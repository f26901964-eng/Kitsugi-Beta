package com.kitsugi.animelist.data.cloudstream

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.core.identity.MediaIdentity
import com.kitsugi.animelist.data.local.CsPluginEntity
import com.kitsugi.animelist.data.remote.ResolvedIds
import com.kitsugi.animelist.data.repository.StreamSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Ortak Kaynak Omurgası — CloudStream eklenti fan-out'u (TEK yol).
 *
 * SORUN (2026-10-25 kullanıcı raporu): aynı bölüm için iki ayrı toplama yolu vardı:
 *   1. [com.kitsugi.animelist.ui.screens.stream.StreamViewModel] — tip filtresi, durum
 *      izleyici, önceden çözülmüş ID'ler, tekrar ayıklama vardı.
 *   2. [com.kitsugi.animelist.ui.screens.fullscreen.KitsugiPlayerViewModel.fetchStreamsForEpisode]
 *      (sonraki bölüm / bölüm değiştirme) — bunların HİÇBİRİ yoktu: tip filtresi yok,
 *      engellenen eklenti kontrolü yok, `preResolvedIds` yok (her eklenti için yeniden
 *      ARM/TMDB ağı), cs3 URL modunda **erken çıkış** (tek kaynağa kilitlenme).
 * Sonuç: dizi bittiğinde otomatik geçiş yapılan bölümde kaynak sayısı düşüyor, bazı
 * eklentiler hiç sorulmuyor, altyazı listesi de buna bağlı olarak boş kalıyordu.
 *
 * ÇÖZÜM: Tüm akış toplama işlemleri bu nesneden geçer. Politika tek yerde:
 *   • engellenen (bloklu) eklenti atlanmaz → nedeni raporlanır,
 *   • içerik tipi uyumsuz eklenti sorgulanmaz (ama sessizce gizlenmez, nedeni döner),
 *   • tüm eklentiler aynı kimlik + aynı önceden çözülmüş ID'lerle paralel sorgulanır,
 *   • doğrudan URL (cs3Url) yalnızca **tohum**tur: sonuç varsa gösterilir, tarama sürer.
 */
object CsStreamFanout {

    private const val TAG = "CsStreamFanout"

    /**
     * Tek bir eklentinin sonucu.
     *
     * [streams] boş ve [error] dolu ise satır kullanıcıya hata olarak gösterilir; hiçbir
     * eklenti sessizce "yok" sayılmaz (kaynak kayırmama).
     */
    data class PluginOutcome(
        val pluginId: String,
        val pluginName: String,
        val displayName: String,
        val streams: List<StreamSource> = emptyList(),
        val error: String? = null,
        val errorCode: String? = null,
        val blocked: Boolean = false,
        val unsupported: Boolean = false,
    ) {
        val ok: Boolean get() = error == null
    }

    /** Eklentinin görünen adı: kullanıcıya ve satır eşleştirmesine tek biçim. */
    fun displayNameOf(plugin: CsPluginEntity): String = "⚡ ${plugin.name}"

    /**
     * Tüm etkin eklentileri paralel sorgular.
     *
     * @param seed eklenti adı → doğrudan URL'den gelen hazır kaynaklar (yalnızca tohum).
     * @return her etkin eklenti için tam olarak bir sonuç (sıra korunur).
     */
    suspend fun resolveAll(
        context: Context,
        plugins: List<CsPluginEntity>,
        identity: MediaIdentity,
        resolvedIds: ResolvedIds? = null,
        seed: Map<String, List<StreamSource>> = emptyMap(),
    ): List<PluginOutcome> = coroutineScope {
        plugins.map { plugin ->
            async(Dispatchers.IO) {
                queryPlugin(context, plugin, identity, resolvedIds, seed)
            }
        }.map { it.await() }
    }

    /** Tek eklenti sorgusu (paralel çağrıya uygun; kendi içinde sıralı çalışır). */
    suspend fun queryPlugin(
        context: Context,
        plugin: CsPluginEntity,
        identity: MediaIdentity,
        resolvedIds: ResolvedIds? = null,
        seed: Map<String, List<StreamSource>> = emptyMap(),
    ): PluginOutcome = withContext(Dispatchers.IO) {
        val display = displayNameOf(plugin)

        // 1) Oturum içi blok: eskiden bu kontrol yalnızca akış ekranında vardı.
        if (CsPluginStatusTracker.isBlocked(plugin.id) || CsPluginStatusTracker.isBlocked(plugin.name)) {
            val reason = CsPluginStatusTracker.getErrorMessage(plugin.name)
                ?: CsPluginStatusTracker.getErrorMessage(plugin.id)
                ?: "Tekrarlı hata"
            Log.w(TAG, "[$display] Engellendi: $reason")
            return@withContext PluginOutcome(
                pluginId = plugin.id,
                pluginName = plugin.name,
                displayName = display,
                error = "Engellendi: $reason",
                errorCode = CsPluginStatusTracker.getErrorCode(plugin.name)
                    ?: CsPluginStatusTracker.getErrorCode(plugin.id),
                blocked = true,
            )
        }

        // 2) Yükleme
        val apis = try {
            CsPluginLoader.loadExtension(context, plugin.id)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (e: Throwable) {
            Log.e(TAG, "[$display] loadExtension hatası: ${e.message}", e)
            emptyList()
        }
        if (apis.isEmpty()) {
            return@withContext PluginOutcome(
                pluginId = plugin.id,
                pluginName = plugin.name,
                displayName = display,
                error = CsPluginLoader.takeLastLoadError(plugin.id)
                    ?: "Eklenti yüklenemedi (DEX/manifest hatası)",
            )
        }

        // 3) İçerik tipi uyumu — sessiz eleme YOK, nedeni döner.
        val compatible = apis.filter { api ->
            CsPluginTypeCompatibility.supports(identity.mediaType, api.supportedTypes.map { it.name })
        }
        if (compatible.isEmpty()) {
            val message = identity.mediaType?.let {
                "Bu eklenti ${CsPluginTypeCompatibility.description(it)} içeriklerini desteklemiyor."
            } ?: "Bu eklenti bu içerik türünü desteklemiyor."
            return@withContext PluginOutcome(
                pluginId = plugin.id,
                pluginName = plugin.name,
                displayName = display,
                error = message,
                unsupported = true,
            )
        }

        // 4) Tohum (doğrudan URL) — yalnızca bu eklentiye aitse eklenir.
        val seeded = (seed[plugin.name] ?: seed[plugin.id] ?: emptyList())

        val collected = mutableListOf<StreamSource>()
        collected.addAll(seeded)

        var lastError: String? = null
        for (api in compatible) {
            try {
                collected.addAll(
                    CsStreamRunner.getStreams(
                        api = api,
                        title = identity.title,
                        alternativeTitles = identity.alternativeTitles,
                        year = identity.startYear,
                        season = identity.effectiveSeason,
                        episode = identity.effectiveEpisode,
                        malId = identity.malId,
                        aniListId = identity.aniListId,
                        tmdbId = identity.tmdbId,
                        bangumiId = identity.bangumiId,
                        isMovie = identity.isMovie,
                        mediaType = identity.mediaType,
                        // Aynı ID'ler tüm eklentilere geçer: eklenti başına ARM/TMDB fırtınası yok.
                        preResolvedIds = resolvedIds,
                    )
                )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (e: Throwable) {
                lastError = e.localizedMessage ?: e.javaClass.simpleName
                Log.e(TAG, "[$display] ${api.name} sorgusu başarısız: $lastError", e)
            }
        }

        val deduped = if (collected.size > 1) {
            collected.distinctBy { it.url ?: it.infoHash }
        } else collected

        // ── Ortak altyazı havuzu (kaynak kayırmama) ───────────────────────────
        // Eklentilerin döndürdüğü gömülü altyazılar, kullanıcı o kaynağı SEÇMESE BİLE
        // ortak havuza yazılır. Böylece "A kaynağında altyazı vardı, B'de yok" durumu
        // oluşmaz: tarama anında tüm eklentilerin altyazıları toplanmış olur.
        try {
            val embedded = deduped.flatMap { it.subtitles }
            if (embedded.isNotEmpty()) {
                com.kitsugi.animelist.core.subtitle.SubtitleMemoryStore.initialize(context)
                com.kitsugi.animelist.core.subtitle.SubtitleMemoryStore.rememberProviderSubtitles(
                    keys = identity.subtitleKeyCandidates,
                    addonName = plugin.name,
                    inputs = embedded,
                )
            }
        } catch (e: Throwable) {
            Log.w(TAG, "[$display] gömülü altyazılar havuza yazılamadı: ${e.message}")
        }

        PluginOutcome(
            pluginId = plugin.id,
            pluginName = plugin.name,
            displayName = display,
            streams = deduped,
            // Kaynak bulunduysa hata gösterilmez: kısmi hata listeyi karartmasın.
            error = if (deduped.isEmpty()) lastError else null,
        )
    }

    /**
     * Ortak tekrar ayıklama: aynı video birden fazla eklentiden gelirse **ilk gelen**
     * korunur ve diğer eklenti adları `⚡ A + B` biçiminde birleştirilir. Böylece hiçbir
     * eklenti "görünmez" olmaz (kaynak kayırmama) ama liste şişmez.
     */
    fun mergeAndDedupe(outcomes: List<PluginOutcome>): List<StreamSource> {
        val byKey = LinkedHashMap<String, StreamSource>()
        for (outcome in outcomes) {
            for (stream in outcome.streams) {
                val key = streamKey(stream) ?: continue
                val existing = byKey[key]
                if (existing == null) {
                    byKey[key] = stream
                } else {
                    val merged = existing.addonName.split(" + ")
                        .map { it.trim() }
                        .toMutableList()
                    val name = stream.addonName.trim()
                    if (name.isNotEmpty() && merged.none { it.equals(name, ignoreCase = true) }) {
                        merged.add(if (name.startsWith("⚡")) name else "⚡ $name")
                    }
                    byKey[key] = existing.copy(addonName = merged.joinToString(" + "))
                }
            }
        }
        return byKey.values.toList()
    }

    /** Aynı videoyu işaret eden anahtar (URL veya bilgi karması). */
    fun streamKey(stream: StreamSource): String? {
        val url = stream.url?.trim()
        if (!url.isNullOrBlank()) return "u:" + url.substringBefore("#")
        val hash = stream.infoHash?.trim()
        return if (!hash.isNullOrBlank()) "h:" + hash.lowercase() else null
    }
}
