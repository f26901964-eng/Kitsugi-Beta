package com.kitsugi.animelist.core.music

import android.content.Context
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.kitsugi.animelist.data.music.AmbientOrigin
import com.kitsugi.animelist.data.music.AmbientSound
import com.kitsugi.animelist.data.music.AtmospherePreset
import com.kitsugi.animelist.data.music.AtmospherePresets
import com.kitsugi.animelist.data.music.ChannelPlayState
import com.kitsugi.animelist.data.music.ChannelState
import com.kitsugi.animelist.data.music.MAX_AMBIENT_CHANNELS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 🎚️ Kitsugi Birleşik Ortam Sesi Motoru
 *
 * **Tek arayüz, her kaynak.** Prosedürel sesler (`AudioTrack`) ve uzak
 * akışlar (`ExoPlayer`) aynı kanal havuzunda yaşar. Paneldeki her buton
 * (ses seviyesi, mute, kaldır, duraklat) kanalın kaynağı ne olursa olsun
 * aynı şekilde çalışır.
 *
 * ## Neden yeniden yazıldı
 * Eski tasarımda iki sabit kanal vardı (`MUSIC` + `AMBIENT`). Bu yüzden:
 *  - Uzak bir Jamendo parçası ile prosedürel yağmuru aynı anda çalmak imkânsızdı
 *  - Ses seviyesi/mute gibi kontroller yalnızca 2 kanala uygulanabiliyordu
 *  - "Ses başına mute" gibi özellikler altyapı yokluğundan eklenemiyordu
 *
 * Yeni tasarım: **6 yuvalı kanal havuzu** (`MAX_AMBIENT_CHANNELS`).
 * Her yuva bağımsız olarak prosedürel veya uzak ses çalabilir.
 *
 * ## Kanal yaşam döngüsü
 * ```
 * addChannel(sound)  → boş yuva bulunur, yüklenir
 * playChannel(i)     → fade-in, PLAYING
 * setChannelVolume(i)→ fade ile yeni seviyeye geçer
 * toggleMute(i)      → 0'a fade, hafızada ses seviyesi korunur
 * stopChannel(i)     → fade-out, yuva boşalır
 * ```
 *
 * ## Mimari not
 * Prosedürel sesler için her kanal bir `ProceduralSoundPlayer` (AudioTrack,
 * gerçek zamanlı PCM). Uzak sesler için her kanal bir `ExoPlayer`. İkisi de
 * `ChannelVoice` arayüzüyle soyutlanır → motor hangi tür olduğunu bilmez.
 */
@UnstableApi
class AmbientAudioEngine(
    private val context: Context
) {

    companion object {
        private const val TAG = "AmbientAudioEngine"
        private const val FADE_MS = 900L
        private const val FADE_STEP_MS = 40L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // ─────────────────────────────────────────────────────────────────────
    //  Kanal havuzu
    // ─────────────────────────────────────────────────────────────────────

    /** Yuva → aktif ses sesi. `null` = yuva boş. */
    private val voices = arrayOfNulls<ChannelVoice>(MAX_AMBIENT_CHANNELS)

    private val _channels = MutableStateFlow<List<ChannelState>>(emptyList())
    /** Aktif kanalların anlık durumu — UI bunu dinler. */
    val channels: StateFlow<List<ChannelState>> = _channels.asStateFlow()

    // ─────────────────────────────────────────────────────────────────────
    //  Global durum
    // ─────────────────────────────────────────────────────────────────────

    private val _masterVol = MutableStateFlow(0.8f)
    val masterVolume: StateFlow<Float> = _masterVol.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _activeTitle = MutableStateFlow<String?>(null)
    /** Mini player'da gösterilen başlık. */
    val activeTitle: StateFlow<String?> = _activeTitle.asStateFlow()

    private val _activePresetId = MutableStateFlow<String?>(null)
    val activePresetId: StateFlow<String?> = _activePresetId.asStateFlow()

    /** Ekran kapanınca devam etsin mi (foreground service kararı için). */
    var continueInBackground: Boolean = false

    private var presetLayers: MutableList<Pair<Int, Long>> = mutableListOf()

    // ─────────────────────────────────────────────────────────────────────
    //  Kanal ekleme / çalma
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Bir sesi havuza ekler ve çalmaya başlar.
     *
     * Aynı ses zaten çalıyorsa ikinci kopya açılmaz; mevcut kanal
     * kaldırılır (toggle davranışı).
     *
     * @param sound Eklenecek ses (prosedürel veya uzak)
     * @param volume Başlangıç ses seviyesi
     * @return Oluşturulan kanalın yuvası, başarısızsa `-1`
     */
    fun addChannel(sound: AmbientSound, volume: Float = 0.6f): Int {
        // Zaten çalıyorsa → kaldır (toggle)
        val existing = _channels.value.firstOrNull { it.sound.id == sound.id }
        if (existing != null) {
            stopChannel(existing.index)
            return existing.index
        }

        // Boş yuva bul
        val slot = voices.indexOfFirst { it == null }
        if (slot < 0) {
            Log.w(TAG, "Kanal sınırı dolu ($MAX_AMBIENT_CHANNELS)")
            return -1
        }

        val voice = createVoice(sound) ?: return -1
        voices[slot] = voice

        val state = ChannelState(
            index = slot,
            sound = sound,
            volume = volume.coerceIn(0f, 1f),
            state = ChannelPlayState.BUFFERING
        )
        _channels.update { it + state }
        syncActiveFlags()

        // Yükle ve çal
        voice.setVolume(0f)
        val started = voice.start()
        if (!started) {
            voices[slot]?.release()
            voices[slot] = null
            _channels.update { list -> list.filterNot { it.index == slot } }
            syncActiveFlags()
            return -1
        }

        fadeVoice(slot, 0f, state.effectiveVolume(_masterVol.value))
        updateChannel(slot) { it.copy(state = ChannelPlayState.PLAYING) }
        _activeTitle.value = state.displayLabel
        _isPlaying.value = true

        return slot
    }

    /**
     * Bir başka sese geçerken öncekini durdurur (tekli çalma).
     * Preset'ler ve "tek ses" butonları bunu kullanır.
     */
    fun playExclusive(sound: AmbientSound, volume: Float = 0.7f) {
        stopAllChannels()
        addChannel(sound, volume)
    }

    /** Bir yuvayı durdurur ve serbest bırakır. */
    fun stopChannel(index: Int) {
        val voice = voices.getOrNull(index) ?: return
        voices[index] = null
        voice.fadeOutAndRelease(FADE_MS)
        _channels.update { list -> list.filterNot { it.index == index } }
        syncActiveFlags()
        if (_channels.value.isEmpty()) {
            _activeTitle.value = null
            _activePresetId.value = null
        } else {
            _activeTitle.value = _channels.value.firstOrNull()?.displayLabel
        }
    }

    /** Tüm kanalları durdurur. */
    fun stopAllChannels() {
        voices.forEachIndexed { i, v ->
            if (v != null) {
                v.fadeOutAndRelease(FADE_MS)
                voices[i] = null
            }
        }
        _channels.value = emptyList()
        _activeTitle.value = null
        _activePresetId.value = null
        _isPlaying.value = false
        presetLayers.clear()
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Ses seviyesi / mute
    // ─────────────────────────────────────────────────────────────────────

    /** Kanalın ses seviyesini ayarlar (yumuşak geçiş). */
    fun setChannelVolume(index: Int, volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        updateChannel(index) { it.copy(volume = v) }
        val state = _channels.value.firstOrNull { it.index == index } ?: return
        fadeVoice(index, -1f, state.effectiveVolume(_masterVol.value))
    }

    /** Kanalı sessize alır/açar. Ses seviyesi hafızada korunur. */
    fun toggleMute(index: Int) {
        updateChannel(index) { it.copy(muted = !it.muted) }
        val state = _channels.value.firstOrNull { it.index == index } ?: return
        fadeVoice(index, -1f, state.effectiveVolume(_masterVol.value))
    }

    /** Ana ses seviyesi — tüm kanalları etkiler. */
    fun setMasterVolume(value: Float) {
        val v = value.coerceIn(0f, 1f)
        if (v == _masterVol.value) return
        _masterVol.value = v
        _channels.value.forEach { state ->
            fadeVoice(state.index, -1f, state.effectiveVolume(v))
        }
    }

    /** Tek kanalı sessize alır/açar (adlandırılmış yardımcı). */
    fun setChannelMuted(index: Int, muted: Boolean) {
        val current = _channels.value.firstOrNull { it.index == index } ?: return
        if (current.muted == muted) return
        updateChannel(index) { it.copy(muted = muted) }
        val state = _channels.value.firstOrNull { it.index == index } ?: return
        fadeVoice(index, -1f, state.effectiveVolume(_masterVol.value))
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Toplu kontrol
    // ─────────────────────────────────────────────────────────────────────

    fun pause() {
        voices.forEach { it?.pause() }
        _isPlaying.value = false
        _channels.update { list -> list.map { it.copy(state = ChannelPlayState.PAUSED) } }
    }

    fun resume() {
        voices.forEach { it?.resume() }
        _isPlaying.value = true
        _channels.update { list -> list.map { it.copy(state = ChannelPlayState.PLAYING) } }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) pause() else resume()
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Preset
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Bir atmosfer presetini yükler. Katmanlar gecikmeli olarak havuza eklenir.
     */
    fun playPreset(preset: AtmospherePreset) {
        stopAllChannels()
        _activePresetId.value = preset.id
        _activeTitle.value = "${preset.emoji} ${preset.name}"
        _isPlaying.value = true

        val resolved = AtmospherePresets.resolveLayers(preset)
        if (resolved.isEmpty()) {
            Log.w(TAG, "Preset katmanları çözülemedi: ${preset.id}")
            return
        }

        resolved.forEach { (sound, layer) ->
            val job = scope.launch {
                if (layer.delayMs > 0) delay(layer.delayMs)
                if (_activePresetId.value != preset.id) return@launch
                addChannel(sound, layer.volume)
            }
            presetLayers += 0 to job.id
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Serbest bırakma
    // ─────────────────────────────────────────────────────────────────────

    fun release() {
        voices.forEachIndexed { i, v ->
            v?.release()
            voices[i] = null
        }
        _channels.value = emptyList()
        _isPlaying.value = false
        _activeTitle.value = null
        _activePresetId.value = null
        scope.cancel()
    }

    // ═════════════════════════════════════════════════════════════════════
    //  DAHİLİ
    // ═════════════════════════════════════════════════════════════════════

    private fun updateChannel(index: Int, transform: (ChannelState) -> ChannelState) {
        _channels.update { list ->
            list.map { if (it.index == index) transform(it) else it }
        }
    }

    private fun syncActiveFlags() {
        _isPlaying.value = _channels.value.any { it.state == ChannelPlayState.PLAYING }
    }

    /**
     * Ses tanımına göre uygun arka ucu seçer.
     *
     * Prosedürel → [ProceduralVoice] (AudioTrack)
     * Uzak        → [StreamVoice] (ExoPlayer)
     */
    private fun createVoice(sound: AmbientSound): ChannelVoice? = when {
        sound.origin == AmbientOrigin.PROCEDURAL -> ProceduralVoice(context, sound)
        !sound.streamUrl.isNullOrBlank() -> StreamVoice(context, sound, sound.streamUrl)
        else -> {
            Log.w(TAG, "Çalınamaz ses (adres yok): ${sound.id}")
            null
        }
    }

    /** Bir kanalın ses seviyesini kademeli değiştirir. */
    private fun fadeVoice(index: Int, from: Float, to: Float) {
        val voice = voices.getOrNull(index) ?: return
        scope.launch {
            val start = if (from < 0f) voice.currentVolume() else from
            val steps = (FADE_MS / FADE_STEP_MS).coerceAtLeast(1).toInt()
            val delta = (to - start) / steps
            var current = start
            repeat(steps) {
                current += delta
                voice.setVolume(current.coerceIn(0f, 1f))
                delay(FADE_STEP_MS)
            }
            voice.setVolume(to.coerceIn(0f, 1f))
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════
//  KANAL SESİ SOYUTLAMASI
// ═════════════════════════════════════════════════════════════════════════

/**
 * Bir kanalın ses arka ucu. Motor bu arayüz üzerinden konuşur — altta
 * `AudioTrack` mi `ExoPlayer` mı olduğunu bilmez.
 */
internal interface ChannelVoice {
    /** Çalmaya başlar. Başarılıysa `true`. */
    fun start(): Boolean
    fun pause()
    fun resume()
    /** Anlık ses seviyesi (fade başlangıcı için). */
    fun currentVolume(): Float
    fun setVolume(value: Float)
    /** Yumuşak kapanış + kaynak serbest bırakma. */
    fun fadeOutAndRelease(durationMs: Long)
    /** Anında serbest bırakma. */
    fun release()
}

/**
 * Prosedürel ses kanalı — `AudioTrack` üzerinden gerçek zamanlı PCM.
 */
internal class ProceduralVoice(
    private val context: Context,
    private val sound: AmbientSound
) : ChannelVoice {

    private val player = ProceduralSoundPlayer(context, sound.id)
    @Volatile
    private var volume = 0f

    override fun start(): Boolean {
        return runCatching {
            player.start()
            true
        }.getOrElse {
            Log.w("ProceduralVoice", "Başlatılamadı: ${sound.id}", it)
            false
        }
    }

    override fun pause() = player.pause()
    override fun resume() = player.resume()
    override fun currentVolume(): Float = volume

    override fun setVolume(value: Float) {
        volume = value
        player.setVolume(value)
    }

    override fun fadeOutAndRelease(durationMs: Long) {
        player.fadeOutAndStop(durationMs)
    }

    override fun release() = player.stop()
}

/**
 * Uzak akış kanalı — Media3 `ExoPlayer`.
 *
 * Ses tanımında `streamUrl` varsa doğrudan kullanılır. Internet Archive
 * gibi dosya adının çözülmesi gereken kaynaklarda çağıran taraf önce
 * `MusicCatalogClient.resolveArchiveTrackUrl()` ile adresi almalıdır.
 */
@UnstableApi
internal class StreamVoice(
    context: Context,
    private val sound: AmbientSound,
    private val url: String
) : ChannelVoice {

    private val player: ExoPlayer

    @Volatile
    private var volume = 0f

    @Volatile
    private var playing = false

    init {
        val dataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(context)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            // true → kulaklık çıkarılınca otomatik duraklat
            .setAudioAttributes(audioAttributes, true)
            .build()

        player.repeatMode = Player.REPEAT_MODE_ONE
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.w("StreamVoice", "Akış hatası: ${sound.id} — ${error.errorCodeName}", error)
            }
        })
    }

    override fun start(): Boolean = runCatching {
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.volume = 0f
        player.playWhenReady = true
        player.play()
        playing = true
        true
    }.getOrElse {
        Log.w("StreamVoice", "Başlatılamadı: $url", it)
        false
    }

    override fun pause() {
        player.pause()
        playing = false
    }

    override fun resume() {
        player.play()
        playing = true
    }

    override fun currentVolume(): Float = volume

    override fun setVolume(value: Float) {
        volume = value
        runCatching { player.volume = value }
    }

    override fun fadeOutAndRelease(durationMs: Long) {
        // Motor fade'i kendisi yapıyor; burada yalnızca kısa bir bekleme
        // sonrası serbest bırakma yeterli.
        release()
    }

    override fun release() {
        runCatching {
            player.stop()
            player.release()
        }
        playing = false
    }
}
