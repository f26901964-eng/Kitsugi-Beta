package com.kitsugi.animelist.core.music

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Okuma müziği ayarları.
 *
 * Ana [com.kitsugi.animelist.data.settings.AppSettings] ağacına EKLENMEDİ: o sınıf
 * yüzlerce alanı tek bir DataStore eşlemesinde tutuyor ve müzik özelliği oraya
 * eklenince her ayar yazımında tüm ağaç yeniden serileşiyordu. Buradaki ayrı
 * DataStore yalnızca 8 anahtar taşır; yazma maliyeti sabittir ve okuyucu ekranı
 * her açılışta yalnızca bu küçük akışı toplar.
 */
private val Context.mangaMusicDataStore by preferencesDataStore(name = "kitsugi_manga_music")

data class MangaMusicSettings(
    val enabled: Boolean = false,
    val source: MangaMusicSource = MangaMusicSource.RADIO_BROWSER,
    val mood: String = MangaMusicMood.LO_FI.tag,
    val volume: Float = 0.6f,
    /** Okuyucu açıldığında müzik kendiliğinden başlasın mı? */
    val autoplay: Boolean = false,
    /** Son çalınan istasyon — okuyucu yeniden açıldığında aynı yayına dönmek için. */
    val stationId: String? = null,
    val stationName: String? = null,
    val stationUrl: String? = null
) {
    fun lastStationOrNull(): MusicStation? {
        val url = stationUrl?.trim().orEmpty()
        if (url.isBlank()) return null
        return MusicStation(
            id = stationId.orEmpty().ifBlank { url },
            name = stationName.orEmpty().ifBlank { "Okuma Müziği" },
            streamUrl = url,
            mood = mood,
            providerLabel = ""
        )
    }
}

class MangaMusicSettingsStore(private val context: Context) {

    private object Keys {
        val Enabled = booleanPreferencesKey("manga_music_enabled")
        val Source = stringPreferencesKey("manga_music_source")
        val Mood = stringPreferencesKey("manga_music_mood")
        val Volume = floatPreferencesKey("manga_music_volume")
        val Autoplay = booleanPreferencesKey("manga_music_autoplay")
        val StationId = stringPreferencesKey("manga_music_station_id")
        val StationName = stringPreferencesKey("manga_music_station_name")
        val StationUrl = stringPreferencesKey("manga_music_station_url")
    }

    val settingsFlow: Flow<MangaMusicSettings> = context.mangaMusicDataStore.data.map { prefs ->
        MangaMusicSettings(
            enabled = prefs[Keys.Enabled] ?: false,
            source = MangaMusicSource.fromStorage(prefs[Keys.Source]),
            mood = MangaMusicMood.fromTag(prefs[Keys.Mood]).tag,
            volume = (prefs[Keys.Volume] ?: 0.6f).coerceIn(0f, 1f),
            autoplay = prefs[Keys.Autoplay] ?: false,
            stationId = prefs[Keys.StationId],
            stationName = prefs[Keys.StationName],
            stationUrl = prefs[Keys.StationUrl]
        )
    }

    suspend fun current(): MangaMusicSettings = settingsFlow.first()

    suspend fun setEnabled(enabled: Boolean) = edit { it.copy(enabled = enabled) }

    suspend fun setSource(source: MangaMusicSource) = edit {
        it.copy(
            source = source,
            enabled = if (source == MangaMusicSource.OFF) false else it.enabled
        )
    }

    suspend fun setMood(mood: MangaMusicMood) = edit { it.copy(mood = mood.tag) }

    suspend fun setVolume(volume: Float) = edit { it.copy(volume = volume.coerceIn(0f, 1f)) }

    suspend fun setAutoplay(autoplay: Boolean) = edit { it.copy(autoplay = autoplay) }

    /** Son çalınan istasyonu kalıcılaştırır (kaynak etiketi dahil). */
    suspend fun setLastStation(station: MusicStation?) = edit {
        it.copy(
            stationId = station?.id,
            stationName = station?.name,
            stationUrl = station?.streamUrl
        )
    }

    private suspend fun edit(transform: (MangaMusicSettings) -> MangaMusicSettings) {
        val current = settingsFlow.first()
        val next = transform(current)
        context.mangaMusicDataStore.edit { prefs ->
            prefs[Keys.Enabled] = next.enabled
            prefs[Keys.Source] = next.source.storageValue
            prefs[Keys.Mood] = next.mood
            prefs[Keys.Volume] = next.volume.coerceIn(0f, 1f)
            prefs[Keys.Autoplay] = next.autoplay
            val stationId = next.stationId
            val stationName = next.stationName
            val stationUrl = next.stationUrl
            if (stationId.isNullOrBlank()) prefs.remove(Keys.StationId) else prefs[Keys.StationId] = stationId
            if (stationName.isNullOrBlank()) prefs.remove(Keys.StationName) else prefs[Keys.StationName] = stationName
            if (stationUrl.isNullOrBlank()) prefs.remove(Keys.StationUrl) else prefs[Keys.StationUrl] = stationUrl
        }
    }
}
