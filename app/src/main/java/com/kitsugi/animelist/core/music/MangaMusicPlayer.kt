package com.kitsugi.animelist.core.music

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Okuma müziği oynatıcısı (tek örnek).
 *
 * Tasarım kararı: [ExoPlayer] nesnesine **doğrudan** erişilir; `MediaController`
 * üzerinden uzak oturum kullanılmaz. Sebep: okuyucu ekranı ile servis aynı süreçte
 * çalışıyor ve denetleyici (controller) bağlantısı asenkron kurulduğu için ilk
 * "Çal" basışında müzik 1–2 saniye gecikiyor, bağlantı düşünce panel donuyordu.
 * Bildirim/lock-screen denetimi yine [MangaMusicService] içindeki MediaSession
 * üzerinden çalışır — aynı player'ı paylaştığı için iki taraf asla ayrışmaz.
 *
 * İş parçacığı kuralı: tüm `player.*` çağrıları ana iş parçacığından yapılır
 * (ExoPlayer'ın oluşturulduğu iş parçacığı). Compose ve servis zaten ana iş
 * parçacığında olduğu için ek kilit gerekmez; durum yayını [MutableStateFlow]
 * üzerinden atomiktir.
 */
object MangaMusicPlayer {

    private const val TAG = "MangaMusicPlayer"

    /** Servis bağlanması için tanınan süre; sonrasında yerel motor devreye girer. */
    private const val LOCAL_FALLBACK_DELAY_MS = 900L

    private val _state = MutableStateFlow(MusicPlaybackState())
    val state: StateFlow<MusicPlaybackState> = _state.asStateFlow()

    private var player: ExoPlayer? = null
    private var appContext: Context? = null
    private var queue: List<MusicStation> = emptyList()
    private var currentIndex: Int = -1

    /** Servis henüz kurulmadan gelen "çal" isteği burada bekler. */
    private var pendingStation: MusicStation? = null

    /** Canlı yayın düştüğünde otomatik sıradaki istasyona geçme hakkı. */
    private var autoAdvanceBudget: Int = 0

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update {
                it.copy(
                    isLoading = playbackState == Player.STATE_BUFFERING,
                    isPlaying = player?.isPlaying == true
                )
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "Oynatma hatası: ${error.errorCodeName} (${error.errorCode})", error)
            _state.update {
                it.copy(
                    isLoading = false,
                    isPlaying = false,
                    error = error.errorCodeName
                )
            }
            // Canlı radyoda tek istasyonun düşmesi normaldir; sıradaki varsa bir kez dene.
            if (autoAdvanceBudget > 0 && queue.size > 1) {
                autoAdvanceBudget--
                moveBy(1)
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Servis bağlanamadığında (arka planda foreground servis kısıtı, pil optimizasyonu,
     * bazı MIUI/EMUI sürümleri) aynı süreçte çalan yerel motor. Kullanıcı düğmeye
     * bastığında "hiçbir şey olmuyor" durumunu engeller; bildirim olmasa da ses çalar.
     */
    private val localFallback = Runnable {
        if (player != null) return@Runnable
        val ctx = appContext ?: return@Runnable
        if (pendingStation == null) return@Runnable
        val fallbackPlayer = createLocalPlayer(ctx) ?: return@Runnable
        Log.w(TAG, "Servis bağlanamadı; yerel motorla çalınıyor")
        attach(fallbackPlayer)
    }

    /** Servis oluşturulduğunda çağrılır. */
    internal fun attach(exoPlayer: ExoPlayer) {
        mainHandler.removeCallbacks(localFallback)
        val previous = player
        val replacedPlayer = previous != null && previous !== exoPlayer
        if (replacedPlayer) {
            runCatching { previous?.removeListener(listener) }
            runCatching { previous?.release() }
        }
        player = exoPlayer
        exoPlayer.addListener(listener)
        exoPlayer.volume = _state.value.volume
        // Yerel yedek motordan servise geçildiyse çalan istasyonu sürdür.
        val queued = pendingStation ?: if (replacedPlayer) _state.value.station else null
        pendingStation = null
        if (queued != null) startInternal(queued)
    }

    private fun createLocalPlayer(context: Context): ExoPlayer? = try {
        ExoPlayer.Builder(context.applicationContext)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
    } catch (e: Exception) {
        Log.e(TAG, "Yerel oynatıcı oluşturulamadı", e)
        null
    }

    /** Servis yok edildiğinde çağrılır; durum "çalmıyor"a döner. */
    internal fun detach() {
        player?.let { runCatching { it.removeListener(listener) } }
        player = null
        _state.update { it.copy(isPlaying = false, isLoading = false) }
    }

    /** Servis yeniden başlatılsın diye bekleyen istasyon var mı? */
    internal fun hasPending(): Boolean = pendingStation != null

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _state.update { it.copy(volume = clamped) }
        player?.volume = clamped
    }

    /**
     * Bir istasyonu çalar. [stations] verilirse "sonraki/önceki" bu liste içinde
     * dolaşır; verilmezse yalnızca istenen istasyon çalınır.
     */
    fun play(context: Context, station: MusicStation, stations: List<MusicStation> = emptyList()) {
        if (station.streamUrl.isBlank()) {
            _state.update { it.copy(error = "Geçersiz yayın adresi") }
            return
        }
        appContext = context.applicationContext
        val list = (if (stations.isEmpty()) listOf(station) else stations)
            .let { if (it.any { s -> s.id == station.id }) it else it + station }
        queue = list
        val foundIndex = list.indexOfFirst { it.id == station.id }
        currentIndex = if (foundIndex >= 0) foundIndex else 0
        autoAdvanceBudget = 2
        pendingStation = station
        // Servis + bildirim yalnızca gerçek bir çalma isteğiyle başlatılır; aksi hâlde
        // foreground servis 5 saniye içinde startForeground çağırmadığı için ANR'ye düşer.
        MangaMusicService.start(context.applicationContext)
        val running = player
        if (running != null) {
            // Motor zaten ayakta: bekleyen isteği tüket, iki kez başlatma.
            pendingStation = null
            startInternal(station)
        } else {
            // Servis kısa süre içinde bağlanmazsa yerel motora düş.
            mainHandler.removeCallbacks(localFallback)
            mainHandler.postDelayed(localFallback, LOCAL_FALLBACK_DELAY_MS)
        }
    }

    /** Aynı istasyonu duraklat/devam ettir; hiç istasyon yoksa [fallback] ile başlat. */
    fun toggle(context: Context, fallback: MusicStation? = null) {
        val current = player
        if (current != null && current.isPlaying) {
            current.pause()
            return
        }
        if (current != null && current.mediaItemCount > 0) {
            current.play()
            return
        }
        val target = _state.value.station ?: fallback
        if (target != null) play(context, target, queue)
    }

    fun pause() {
        player?.pause()
        _state.update { it.copy(isPlaying = false) }
    }

    fun stop() {
        player?.pause()
        _state.update { it.copy(isPlaying = false, isLoading = false) }
    }

    fun next() {
        moveBy(1)
    }

    fun previous() {
        moveBy(-1)
    }

    private fun moveBy(delta: Int) {
        if (queue.isEmpty()) return
        val size = queue.size
        val nextIndex = ((currentIndex + delta) % size + size) % size
        currentIndex = nextIndex
        val station = queue[nextIndex]
        if (player == null) {
            pendingStation = station
            appContext?.let { MangaMusicService.start(it) }
        } else {
            startInternal(station)
        }
    }

    private fun startInternal(station: MusicStation) {
        val current = player
        if (current == null) {
            pendingStation = station
            appContext?.let { MangaMusicService.start(it) }
            return
        }
        _state.update { it.copy(isLoading = true, error = null, station = station) }
        val item = MediaItem.Builder()
            .setUri(station.streamUrl)
            .setMediaId(station.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(station.name)
                    .setArtist(station.providerLabel.ifBlank { "Okuma Müziği" })
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .build()
            )
            .build()
        try {
            current.setMediaItem(item)
            current.prepare()
            current.play()
        } catch (e: Exception) {
            Log.e(TAG, "İstasyon başlatılamadı: ${station.name}", e)
            _state.update {
                it.copy(
                    isLoading = false,
                    isPlaying = false,
                    error = e.message ?: e.javaClass.simpleName
                )
            }
        }
    }
}
