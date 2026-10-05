package com.kitsugi.animelist.core.p2p

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object P2pSettingsRepository {
    private const val PREFS_NAME = "p2p_settings"
    private const val KEY_P2P_ENABLED = "p2p_enabled"
    private const val KEY_ENABLE_UPLOAD = "enable_upload"
    private const val KEY_UPLOAD_LIMIT_KBPS = "upload_limit_kbps"
    private const val KEY_HIDE_TORRENT_STATS = "hide_torrent_stats"
    private const val KEY_TORRENT_PROFILE = "torrent_profile"
    private const val KEY_CACHE_SIZE = "cache_size"
    private const val KEY_CONSENT_GRANTED = "consent_granted"

    private var prefs: SharedPreferences? = null
    private val _uiState = MutableStateFlow(P2pSettingsUiState())
    val uiState: StateFlow<P2pSettingsUiState> = _uiState.asStateFlow()

    private var hasLoaded = false
    private var p2pEnabled = false
    private var enableUpload = true
    private var uploadLimitKbps = 0L
    private var hideTorrentStats = false
    private var torrentProfile = P2pTorrentProfile.BALANCED
    private var cacheSize = P2pCacheSize.GB_2
    private var consentGranted = false

    fun initialize(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadFromDisk()
        }
    }

    fun ensureLoaded() {
        if (hasLoaded) return
        loadFromDisk()
    }

    fun isP2pEnabled(): Boolean {
        ensureLoaded()
        return p2pEnabled
    }

    fun isConsentGranted(): Boolean {
        ensureLoaded()
        return consentGranted
    }

    fun setP2pEnabled(enabled: Boolean) {
        ensureLoaded()
        if (p2pEnabled == enabled) return
        p2pEnabled = enabled
        prefs?.edit()?.putBoolean(KEY_P2P_ENABLED, enabled)?.apply()
        publish()
    }

    fun setEnableUpload(enabled: Boolean) {
        ensureLoaded()
        if (enableUpload == enabled) return
        enableUpload = enabled
        prefs?.edit()?.putBoolean(KEY_ENABLE_UPLOAD, enabled)?.apply()
        publish()
    }

    fun setUploadLimitKbps(limitKbps: Long) {
        ensureLoaded()
        if (uploadLimitKbps == limitKbps) return
        uploadLimitKbps = limitKbps
        prefs?.edit()?.putLong(KEY_UPLOAD_LIMIT_KBPS, limitKbps)?.apply()
        publish()
    }

    fun setHideTorrentStats(hide: Boolean) {
        ensureLoaded()
        if (hideTorrentStats == hide) return
        hideTorrentStats = hide
        prefs?.edit()?.putBoolean(KEY_HIDE_TORRENT_STATS, hide)?.apply()
        publish()
    }

    fun setTorrentProfile(profile: P2pTorrentProfile) {
        ensureLoaded()
        if (torrentProfile == profile) return
        torrentProfile = profile
        prefs?.edit()?.putString(KEY_TORRENT_PROFILE, profile.name)?.apply()
        publish()
    }

    fun setCacheSize(size: P2pCacheSize) {
        ensureLoaded()
        if (cacheSize == size) return
        cacheSize = size
        prefs?.edit()?.putString(KEY_CACHE_SIZE, size.name)?.apply()
        publish()
    }

    fun setConsentGranted(granted: Boolean) {
        ensureLoaded()
        if (consentGranted == granted) return
        consentGranted = granted
        prefs?.edit()?.putBoolean(KEY_CONSENT_GRANTED, granted)?.apply()
        publish()
    }

    private fun loadFromDisk() {
        hasLoaded = true
        val p = prefs
        p2pEnabled = p?.getBoolean(KEY_P2P_ENABLED, false) ?: false
        enableUpload = p?.getBoolean(KEY_ENABLE_UPLOAD, true) ?: true
        uploadLimitKbps = p?.getLong(KEY_UPLOAD_LIMIT_KBPS, 0L) ?: 0L
        hideTorrentStats = p?.getBoolean(KEY_HIDE_TORRENT_STATS, false) ?: false
        torrentProfile = p?.getString(KEY_TORRENT_PROFILE, null)
            ?.let { stored -> P2pTorrentProfile.entries.firstOrNull { it.name == stored } }
            ?: P2pTorrentProfile.BALANCED
        cacheSize = p?.getString(KEY_CACHE_SIZE, null)
            ?.let { stored -> P2pCacheSize.entries.firstOrNull { it.name == stored } }
            ?: P2pCacheSize.GB_2
        consentGranted = p?.getBoolean(KEY_CONSENT_GRANTED, false) ?: false
        publish()
    }

    private fun publish() {
        _uiState.value = P2pSettingsUiState(
            p2pEnabled = p2pEnabled,
            enableUpload = enableUpload,
            uploadLimitKbps = uploadLimitKbps,
            hideTorrentStats = hideTorrentStats,
            torrentProfile = torrentProfile,
            cacheSize = cacheSize,
            consentGranted = consentGranted,
        )
    }
}
