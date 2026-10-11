package com.kitsugi.animelist.ui.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.settings.SettingsDataStore
import kotlinx.coroutines.launch
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

class PlayerSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val settingsDataStore = SettingsDataStore(context)

    // Callback to show messages on UI
    var onShowMessage: ((String) -> Unit)? = null

    fun updatePlayerPreference(pref: String) {
        viewModelScope.launch {
            settingsDataStore.setPlayerPreference(pref)
            onShowMessage?.invoke(KitsugiStrings.get(R.string.varsayilan_oynatici_guncellendi))
        }
    }

    fun updateAutoplayEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setAutoplayEnabled(enabled)
            onShowMessage?.invoke(if (enabled) "Otomatik oynatma etkin" else KitsugiStrings.get(R.string.otomatik_oynatma_devre_disi))
        }
    }

    fun updateSkipIntroDurationSec(sec: Int) {
        viewModelScope.launch {
            settingsDataStore.setSkipIntroDurationSec(sec)
            onShowMessage?.invoke(KitsugiStrings.get(R.string.intro_atlama_suresi_guncellendi))
        }
    }

    fun updateDefaultSubtitleSize(size: Int) {
        viewModelScope.launch {
            settingsDataStore.setDefaultSubtitleSize(size)
            onShowMessage?.invoke(KitsugiStrings.get(R.string.varsayilan_altyazi_boyutu_guncellendi))
        }
    }

    fun updateDefaultSubtitleColor(color: Int) {
        viewModelScope.launch {
            settingsDataStore.setDefaultSubtitleColor(color)
            onShowMessage?.invoke(KitsugiStrings.get(R.string.varsayilan_altyazi_rengi_guncellendi))
        }
    }

    fun updateSubtitleBold(bold: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setSubtitleBold(bold)
            onShowMessage?.invoke(if (bold) KitsugiStrings.get(R.string.kalin_altyazi_etkin) else KitsugiStrings.get(R.string.kalin_altyazi_devre_disi))
        }
    }

    fun updateSubtitleOutline(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setSubtitleOutlineEnabled(enabled)
            onShowMessage?.invoke(if (enabled) KitsugiStrings.get(R.string.altyazi_kenarligi_etkin) else KitsugiStrings.get(R.string.altyazi_kenarligi_devre_disi))
        }
    }

    fun updateDefaultAudioBoost(boost: Float) {
        viewModelScope.launch {
            settingsDataStore.setDefaultAudioBoost(boost)
            onShowMessage?.invoke(KitsugiStrings.get(R.string.varsayilan_ses_guclendirmesi_guncellendi))
        }
    }

    fun updateDefaultAudioDelayMs(delayMs: Long) {
        viewModelScope.launch {
            settingsDataStore.setDefaultAudioDelayMs(delayMs)
            onShowMessage?.invoke(KitsugiStrings.get(R.string.varsayilan_ses_gecikmesi_guncellendi))
        }
    }

    fun updateBufferSettings(
        min: Int,
        max: Int,
        playback: Int,
        rebuffer: Int,
        back: Int
    ) {
        // T1-15: min≤max ve playback≤max kuralı; geçersiz değer gelirse clamp uygula
        val clampedMax = max.coerceAtLeast(1000)
        val clampedMin = min.coerceIn(0, clampedMax)
        val clampedPlayback = playback.coerceIn(0, clampedMax)
        val clampedRebuffer = rebuffer.coerceIn(0, clampedMax)
        val clampedBack = back.coerceAtLeast(0)
        viewModelScope.launch {
            settingsDataStore.setBufferSettings(clampedMin, clampedMax, clampedPlayback, clampedRebuffer, clampedBack)
            onShowMessage?.invoke(KitsugiStrings.get(R.string.arabellek_buffer_ayarlari_guncellendi))
        }
    }

    fun updateDv7HandlingMode(mode: com.kitsugi.animelist.data.settings.Dv7HandlingMode) {
        viewModelScope.launch {
            settingsDataStore.setDv7HandlingMode(mode)
            onShowMessage?.invoke(KitsugiStrings.get(R.string.dolby_vision_isleme_modu_guncellendi))
        }
    }

    fun updateStripHdr10PlusSei(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setStripHdr10PlusSei(enabled)
            onShowMessage?.invoke(if (enabled) KitsugiStrings.get(R.string.hdr10_sei_cikarma_etkin) else KitsugiStrings.get(R.string.hdr10_sei_cikarma_devre_disi))
        }
    }

    fun updatePreferredSubtitleLanguages(langs: String) {
        viewModelScope.launch {
            settingsDataStore.setPreferredSubtitleLanguages(langs)
            onShowMessage?.invoke(KitsugiStrings.get(R.string.tercih_edilen_altyazi_dilleri_guncellendi))
        }
    }

    fun updateAddonSubtitleStartupMode(mode: String) {
        viewModelScope.launch {
            settingsDataStore.setAddonSubtitleStartupMode(mode)
            onShowMessage?.invoke(KitsugiStrings.get(R.string.altyazi_yukleme_modu_guncellendi))
        }
    }

    fun updateQualityProfileJson(json: String) {
        viewModelScope.launch {
            settingsDataStore.setQualityProfileJson(json)
            onShowMessage?.invoke(KitsugiStrings.get(R.string.kalite_profili_guncellendi))
        }
    }

    // ─── T1.9 – Paralel Aralık İndirme ──────────────────────────────────────
    fun updateParallelRangeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setParallelRangeEnabled(enabled)
            onShowMessage?.invoke(
                if (enabled) "Paralel indirme etkin (MKV/MP4)" else KitsugiStrings.get(R.string.paralel_indirme_devre_disi)
            )
        }
    }

    // ─── T1.4 – Frame Rate & Resolution Matching ────────────────────────────
    fun updateFrameRateMatchingMode(mode: com.kitsugi.animelist.data.settings.FrameRateMatchingMode) {
        viewModelScope.launch {
            settingsDataStore.setFrameRateMatchingMode(mode)
            onShowMessage?.invoke(KitsugiStrings.get(R.string.kare_hizi_esleme_modu_guncellendi_x, mode.name))
        }
    }

    fun updateResolutionMatchingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setResolutionMatchingEnabled(enabled)
            onShowMessage?.invoke(
                if (enabled) KitsugiStrings.get(R.string.cozunurluk_esleme_etkin) else KitsugiStrings.get(R.string.cozunurluk_esleme_devre_disi)
            )
        }
    }

    // ─── T2.1 + T2.7 – Gesture Ayarları ────────────────────────────────────
    fun updateGestureVolumeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setGestureVolumeEnabled(enabled)
            onShowMessage?.invoke(if (enabled) "Ses jesti etkin" else KitsugiStrings.get(R.string.ses_jesti_devre_disi))
        }
    }

    fun updateGestureBrightnessEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setGestureBrightnessEnabled(enabled)
            onShowMessage?.invoke(if (enabled) KitsugiStrings.get(R.string.parlaklik_jesti_etkin) else KitsugiStrings.get(R.string.parlaklik_jesti_devre_disi))
        }
    }

    fun updateGestureZoomEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setGestureZoomEnabled(enabled)
            onShowMessage?.invoke(if (enabled) "Zoom jesti etkin" else KitsugiStrings.get(R.string.zoom_jesti_devre_disi))
        }
    }

    fun updateDoubleTapSeekSeconds(seconds: Int) {
        viewModelScope.launch {
            settingsDataStore.setDoubleTapSeekSeconds(seconds)
            onShowMessage?.invoke(KitsugiStrings.get(R.string.cift_dokunus_ileri_geri_suresi_xs, seconds))
        }
    }

    fun updateHoldSpeedMultiplier(multiplier: Float) {
        viewModelScope.launch {
            settingsDataStore.setHoldSpeedMultiplier(multiplier)
            onShowMessage?.invoke(KitsugiStrings.get(R.string.basili_tutma_hiz_carpani_xx, multiplier))
        }
    }

    // TASK_050 — Swipe hassasiyeti
    fun updateGestureScrollSensitivity(sensitivity: Float) {
        viewModelScope.launch {
            settingsDataStore.setGestureScrollSensitivity(sensitivity)
            val label = when {
                sensitivity <= 0.6f -> KitsugiStrings.get(R.string.player_scroll_speed_slow, sensitivity)
                sensitivity >= 1.6f -> KitsugiStrings.get(R.string.player_scroll_speed_fast, sensitivity)
                else                -> KitsugiStrings.get(R.string.player_scroll_speed_normal, sensitivity)
            }
            onShowMessage?.invoke(KitsugiStrings.get(R.string.kaydirma_hassasiyeti_x, label))
        }
    }

    // ─── T2.2 – Önizleme Seekbar ───────────────────────────────────────────
    fun updatePreviewSeekbarEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setPreviewSeekbarEnabled(enabled)
            onShowMessage?.invoke(
                if (enabled) KitsugiStrings.get(R.string.onizleme_seekbari_etkin) else KitsugiStrings.get(R.string.onizleme_seekbari_devre_disi)
            )
        }
    }

    // ─── T2.5 – Harici Oynatıcı Tercihi ────────────────────────────────────
    fun updatePreferredExternalPlayerPackage(packageName: String) {
        viewModelScope.launch {
            settingsDataStore.setPreferredExternalPlayerPackage(packageName)
            onShowMessage?.invoke(KitsugiStrings.get(R.string.tercih_edilen_harici_oynatici_guncellendi))
        }
    }

    // ─── T2.6 – Oynatıcı Başlık / Medya Bilgisi Görünürlüğü ───────────────
    fun updateShowPlayerTitle(show: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setShowPlayerTitle(show)
        }
    }

    fun updateShowPlayerResolution(show: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setShowPlayerResolution(show)
        }
    }

    fun updateShowMediaInfo(show: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setShowMediaInfo(show)
        }
    }

    fun updateTitleLimitType(limitType: String) {
        viewModelScope.launch {
            settingsDataStore.setTitleLimitType(limitType)
        }
    }

    // ─── T1.1 – Görüntü Oranı ───────────────────────────────────────────────
    fun updateAspectMode(mode: com.kitsugi.animelist.core.player.PlayerAspectMode) {
        viewModelScope.launch {
            settingsDataStore.setAspectMode(mode.name)
            onShowMessage?.invoke(KitsugiStrings.get(R.string.goruntu_orani_guncellendi_x, mode.name))
        }
    }

    fun updateLiveHelperEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setLiveHelperEnabled(enabled)
            onShowMessage?.invoke(if (enabled) KitsugiStrings.get(R.string.canli_yardimci_etkin) else KitsugiStrings.get(R.string.canli_yardimci_devre_disi))
        }
    }

    fun updateEnableAssExtractor(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setEnableAssExtractor(enabled)
            onShowMessage?.invoke(if (enabled) KitsugiStrings.get(R.string.ass_altyazi_ayiklayici_etkin) else KitsugiStrings.get(R.string.ass_altyazi_ayiklayici_devre_disi))
        }
    }

    // ─── T1-01 – StillWatching + PostPlayMode + AutoplaySessionLimit ──────────
    fun updateStillWatchingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setStillWatchingEnabled(enabled)
            onShowMessage?.invoke(if (enabled) "Hâlâ izliyor musun? sorusu etkin" else KitsugiStrings.get(R.string.hala_izliyor_musun_sorusu_devre_disi))
        }
    }

    fun updateStillWatchingThresholdMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsDataStore.setStillWatchingThresholdMinutes(minutes)
            onShowMessage?.invoke(KitsugiStrings.get(R.string.hareketsizlik_esigi_x_dakika, minutes))
        }
    }

    fun updatePostPlayMode(mode: String) {
        viewModelScope.launch {
            settingsDataStore.setPostPlayMode(mode)
            onShowMessage?.invoke(KitsugiStrings.get(R.string.sonraki_bolum_modu_guncellendi))
        }
    }

    fun updateAutoplaySessionLimit(limit: Int) {
        viewModelScope.launch {
            settingsDataStore.setAutoplaySessionLimit(limit)
            onShowMessage?.invoke(if (limit == 0) KitsugiStrings.get(R.string.otomatik_oynatma_limiti_sinirsiz) else KitsugiStrings.get(R.string.otomatik_oynatma_limiti_x_bolum, limit))
        }
    }

    // ─── T1-03 – Ses Gelişmiş: GainBoost + SubtitleDelay ──────────────────
    fun updateGainBoostDb(db: Float) {
        viewModelScope.launch {
            settingsDataStore.setGainBoostDb(db)
            onShowMessage?.invoke(KitsugiStrings.get(R.string.ses_guclendirme_xdb, db))
        }
    }

    fun updateSubtitleDelayMs(delayMs: Long) {
        viewModelScope.launch {
            settingsDataStore.setSubtitleDelayMs(delayMs)
            onShowMessage?.invoke(KitsugiStrings.get(R.string.altyazi_gecikmesi_xms, delayMs))
        }
    }

    // ─── T1-04 – Dekoder Önceliği (Telefon) ──────────────────────────────
    fun updateDecoderPriority(priority: Int) {
        viewModelScope.launch {
            settingsDataStore.setDecoderPriority(priority)
            val label = when (priority) {
                1 -> KitsugiStrings.get(R.string.yazilim_fallback)
                2 -> "Yazılım Öncelikli"
                else -> "Donanım Öncelikli"
            }
            onShowMessage?.invoke(KitsugiStrings.get(R.string.dekoder_onceligi_x, label))
        }
    }
}
