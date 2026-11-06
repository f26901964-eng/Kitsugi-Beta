package com.kitsugi.animelist.core.music

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Manga okuyucu "Okuma Müziği" ön plan servisi.
 *
 * Görevleri:
 *  - [ExoPlayer]'ı (ses akışı) uygulama yaşam döngüsünden bağımsız çalıştırmak,
 *  - MediaSession ile **bildirim paneli / kilit ekranı** denetimlerini sağlamak,
 *  - ses odağı (audio focus) ve "kulaklık çıkarıldı" davranışını yönetmek.
 *
 * Önceden bu özellik ekran içindeki bir `MediaPlayer` ile yapılmaya çalışıldığında
 * okuyucudan çıkınca ses kesiliyor, bildirim panelinde denetim görünmüyor ve
 * panel donuyordu. Servis + MediaSession bu üç sorunu da kökten çözer.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MangaMusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Okuma müziği servisi oluşturuldu")
        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        mediaSession = MediaSession.Builder(this, exoPlayer).build()
        // Pending "çal" isteği burada tüketilir: kullanıcı düğmeye bastığı anda
        // servis henüz ayakta değildi; istek kaybolmaz, ekran donmaz.
        MangaMusicPlayer.attach(exoPlayer)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        // Çalma isteği yoksa (pending tüketildi ve playWhenReady false) servis
        // foreground'a geçemez; 5 saniyelik ANR penceresine girmeden kapanır.
        if (mediaSession?.player?.playWhenReady != true) {
            stopSelf()
        }
        return result
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            // Uygulama görevden silindi ve müzik çalmıyorsa servisi kapat; aksi hâlde
            // bildirim panelinde "hayalet" bir oynatıcı kalıyordu.
            stopSelf()
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Okuma müziği servisi durduruldu")
        MangaMusicPlayer.detach()
        mediaSession?.run {
            runCatching { player.release() }
            runCatching { release() }
        }
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MangaMusicService"

        /**
         * Servisi başlatır. Yalnızca gerçek bir çalma isteğiyle çağrılmalıdır:
         * çalma başlamazsa MediaSessionService 5 saniye içinde foreground'a
         * geçemeyeceği için sistem süreci öldürür (ANR).
         */
        fun start(context: Context) {
            val intent = Intent(context, MangaMusicService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // Arka planda foreground servis başlatma kısıtı (Android 12+) veya
                // pil optimizasyonu: müzik sessizce ölmesin, en azından günlüğe düşsün.
                Log.e(TAG, "Okuma müziği servisi başlatılamadı", e)
            }
        }

        /** Servis zaten ayaktaysa yeniden başlatmayı engelleyen yardımcı. */
        fun startIfNeeded(context: Context) {
            if (!MangaMusicPlayer.hasPending()) return
            start(context)
        }
    }
}
