package com.kitsugi.animelist.core.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/** Audio focus belongs to the MPV session, not to the Compose surface. minSdk = 26. */
internal class MpvAudioFocusController(
    context: Context,
    private val isPlaying: () -> Boolean,
    private val pauseForFocus: () -> Unit,
    private val resumeForFocus: () -> Unit,
    private val onDuck: (Boolean) -> Unit,
    private val onNoisy: () -> Unit
) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var registered = false
    private var focused = false
    private var resumeOnGain = false
    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
        .setOnAudioFocusChangeListener({ change ->
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    resumeOnGain = false
                    focused = false
                    pauseForFocus()
                    onDuck(false)
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    resumeOnGain = resumeOnGain || isPlaying()
                    focused = false
                    pauseForFocus()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onDuck(true)
                AudioManager.AUDIOFOCUS_GAIN -> {
                    focused = true
                    onDuck(false)
                    if (resumeOnGain) {
                        resumeOnGain = false
                        resumeForFocus()
                    }
                }
            }
        }, Handler(Looper.getMainLooper()))
        .build()
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                resumeOnGain = false
                onNoisy()
            }
        }
    }

    fun request(): Boolean {
        if (!registered) {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else app.registerReceiver(receiver, filter)
            registered = true
        }
        if (!focused) focused = manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return focused
    }

    fun userPaused() { resumeOnGain = false }

    fun release() {
        resumeOnGain = false
        if (registered) runCatching { app.unregisterReceiver(receiver) }
        registered = false
        manager.abandonAudioFocusRequest(request)
        focused = false
    }
}
