package com.kitsugi.animelist.ui.screens.fullscreen.runtime

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kitsugi.animelist.core.player.PlayerAutoplaySessionRules
import com.kitsugi.animelist.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * T1.6 – PlayerStillWatchingController
 *
 * StillWatching prompt ve binge session kurallarını yönetir.
 *
 * @param scope ViewModel'in viewModelScope'u
 */
class PlayerStillWatchingController(
    private val scope: CoroutineScope,
    /** Geri sayım sıfıra indiğinde çağrılır; oynatma duraklatılır. */
    private val onPromptTimedOut: () -> Unit = {}
) {
    private val TAG = "StillWatchingCtrl"

    // ── Compose state (VM'den observe edilir) ─────────────────────────────────
    var showPrompt by mutableStateOf(false)
        private set

    var countdownSec by mutableStateOf<Int?>(null)
        private set

    // ── Internal ──────────────────────────────────────────────────────────────
    private var thresholdMinutes = 90
    private var promptSeconds = 30

    private var lastInteractionMs = System.currentTimeMillis()
    private var sessionStartMs = System.currentTimeMillis()

    private var sessionRules = PlayerAutoplaySessionRules(sessionLimit = 0)
    private var countdownJob: Job? = null
    private var timeoutNotified: Boolean = false

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Oynatma tick'i — ekran tarafından her ~1s aralıkla çağrılır.
     */
    fun onPlaybackTick(positionMs: Long, isPlaying: Boolean) {
        if (!isPlaying) return
        if (showPrompt) return

        val elapsedSinceInteractionMs = System.currentTimeMillis() - lastInteractionMs
        val thresholdMs = thresholdMinutes * 60_000L

        if (elapsedSinceInteractionMs >= thresholdMs) {
            showPrompt = true
            countdownSec = promptSeconds
            startCountdown()
            Log.d(TAG, "Playback tick: inactivity threshold reached ($thresholdMinutes min) — prompt triggered")
        }
    }

    /**
     * Prompt'u dışarıdan (autoplay kuralları, UI vb.) hemen açar.
     * Eski kod yalnızca [onEpisodeCompleted] yolundan açıyordu ve AutoplayController bu
     * metodu hiç çağırmıyordu; sonuç: "hâlâ izliyor musunuz?" uyarısı hiç görünmüyordu.
     */
    fun showPromptNow(seconds: Int = promptSeconds) {
        promptSeconds = seconds.coerceAtLeast(5)
        timeoutNotified = false
        showPrompt = true
        countdownSec = promptSeconds
        startCountdown()
        Log.d(TAG, "Prompt triggered externally — countdown=${promptSeconds}s")
    }

    /**
     * Bölüm tamamlandı. Session kuralları kontrol edilir.
     * @return true → limit aşıldı, prompt gösterilmelidir
     */
    fun onEpisodeCompleted(settings: AppSettings): Boolean {
        applySettings(settings)
        val limitReached = sessionRules.onEpisodeCompleted()
        if (limitReached && settings.stillWatchingEnabled) {
            showPrompt = true
            countdownSec = promptSeconds // Fix: initialize warning countdown to promptSeconds (30s), not thresholdMinutes!
            startCountdown()
            Log.d(TAG, "Session limit reached (${sessionRules.watchedCount()} eps) — prompt triggered")
        }
        return limitReached
    }

    /**
     * Kullanıcı "Evet, hâlâ izliyorum" dedi.
     */
    fun onConfirmed() {
        countdownJob?.cancel()
        timeoutNotified = false
        resetGating()
        sessionRules.resetSession()
        showPrompt = false
        countdownSec = null
        Log.d(TAG, "Confirmed — continuing playback")
    }

    /**
     * Kullanıcı "Hayır, duraksın" dedi.
     */
    fun onDismissed() {
        countdownJob?.cancel()
        timeoutNotified = false
        showPrompt = false
        countdownSec = null
        Log.d(TAG, "Dismissed — playback should pause")
    }

    /**
     * Kullanıcı girişi algılandı (dokunma, seek vb.).
     */
    fun onUserInteraction() {
        lastInteractionMs = System.currentTimeMillis()
        if (showPrompt) {
            onConfirmed()
        }
    }

    /**
     * Settings değiştiğinde config'i güncelle.
     */
    fun applySettings(settings: AppSettings) {
        thresholdMinutes = settings.stillWatchingThresholdMinutes
        sessionRules = PlayerAutoplaySessionRules(sessionLimit = settings.autoplaySessionLimit)
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun resetGating() {
        showPrompt = false
        countdownSec = null
        lastInteractionMs = System.currentTimeMillis()
        sessionStartMs = System.currentTimeMillis()
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            while (showPrompt) {
                delay(1_000L)
                val current = countdownSec
                if (current == null) {
                    break
                } else if (current <= 1) {
                    countdownSec = 0
                    showPrompt = true // prompt still visible until user acts
                    if (!timeoutNotified) {
                        timeoutNotified = true
                        Log.d(TAG, "Countdown expired — pausing playback")
                        onPromptTimedOut()
                    }
                    break
                } else {
                    countdownSec = current - 1
                }
            }
        }
    }
}
