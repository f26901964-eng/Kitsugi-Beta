package com.kitsugi.animelist.data.local

import android.content.Context
import com.kitsugi.animelist.core.network.KitsugiHttpClient
import com.kitsugi.animelist.utils.KitsugiTranslatePrefs
import com.kitsugi.animelist.utils.cleanShikimoriBbCode
import com.kitsugi.animelist.utils.isTechnicalTranslationValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TranslationManager(context: Context) {
    companion object {
        /**
         * Eşzamanlı çeviri kapısı. Arka plan isim/yorum çevirilerinin aktif detay
         * sayfası açıklamasını/biyografisini kilitlememesi için 4 izin kullanılır.
         */
        private val requestGate = Semaphore(4)

        /**
         * Çeviri istekleri için özel OkHttp istemcisi:
         * Genel [KitsugiHttpClient.client] içindeki RetryInterceptor bir uç nokta 429
         * döndüğünde aynı URL'yi 3 kez bekleyerek tekrar deniyordu. Burada interceptor
         * zinciri temizlenerek uç noktalar arası anında yedekli geçiş (fallback) sağlanır.
         */
        private val translationHttpClient: OkHttpClient by lazy {
            KitsugiHttpClient.client.newBuilder()
                .apply { interceptors().clear() }
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .writeTimeout(8, TimeUnit.SECONDS)
                .build()
        }

        private const val MAX_CHUNK_CHARS = 1400

        /**
         * Web sayfası (DOM) çevirisinde kullanılan parça boyutu.
         *
         * Google Translate'in tek istek sınırı 5000 karakterdir; genel çeviri yolu
         * (açıklama/biyografi) kısmî akış grenliliği için 1400'de kalırken sayfa
         * çevirisi işaretli paketleri tek istekte gönderebilmek için 3600 kullanır:
         * aynı metin için yaklaşık 2,5 kat daha az ağ turu. Paket boyutu
         * (bkz. `WikiDomTranslationBatch`) bu sınırın altında tutulur.
         */
        private const val WEB_MAX_CHUNK_CHARS = 3600

        /**
         * Süreç içi LRU önbelleği (Room'un ÖNÜNDE).
         *
         * Sayfa çevirisinde yüzlerce metin ard arda çevrilir; her biri için SQLite
         * sorgusu çalıştırmak görünür gecikme üretiyordu. Bu katman aynı oturumda
         * tekrarlanan metinleri (sayfaya geri dönüş, aynı paragraftan birden çok
         * düğüm, parça tekrarları) disk erişimi olmadan döndürür. Anahtarlar Room
         * ile birebir aynı özetlerdir; kalıcı katman değişmez.
         */
        private const val MEMORY_CACHE_MAX_ENTRIES = 600
        private val memoryCache: MutableMap<String, String> = java.util.Collections.synchronizedMap(
            object : LinkedHashMap<String, String>(128, 0.75f, true) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<String, String>?
                ): Boolean = size > MEMORY_CACHE_MAX_ENTRIES
            }
        )

        private fun memoryGet(hash: String): String? = memoryCache[hash]?.takeIf { it.isNotBlank() }

        private fun memoryPut(hash: String, value: String) {
            if (value.isNotBlank()) memoryCache[hash] = value
        }

        // Maskeleme desenleri tek kez derlenir. Eskiden her çeviri çağrısında
        // (~sayfa çevirisinde yüzlerce kez) yeniden Regex derleniyordu.
        private val MEDIA_EMBED_REGEX = Regex(
            """(?:!\[[^\]]*\]\((https?://[^\s)]+)\)|(?:img\d*|webm|youtube)\s*\(\s*([^\s)]+)\s*\))""",
            RegexOption.IGNORE_CASE
        )
        private val LINK_REGEX = Regex("""\[([^\]]+)\]\((https?://[^\s)]+)\)""")
        private val RAW_URL_REGEX = Regex("""https?://[^\s<>"'\)\]]+""")
        private val WIKI_TOKEN_REGEX = Regex(
            """\[\[[^\]]+\]\]|\{\{[^}]+\}\}|\b(?:File|Image|Category|Template|Dosya|Resim|Kategori|Şablon)\s*:\s*[^\s|]+|\b[\w.+-]+@[\w-]+\.[A-Za-z]{2,}\b""",
            RegexOption.IGNORE_CASE
        )
        private val WIKI_NAMESPACE_WORDS = listOf(
            "file", "image", "category", "template", "dosya", "resim", "kategori", "şablon"
        )
    }

    private val dbContext = context.applicationContext
    private val db = KitsugiDatabase.getDatabase(dbContext)
    private val dao = db.translationCacheDao()

    private suspend fun ensurePrefsInitialized() {
        if (!KitsugiTranslatePrefs.isInitialized) {
            runCatching {
                com.kitsugi.animelist.data.settings.SettingsDataStore(dbContext).settingsFlow.first()
            }
        }
    }

    /**
     * Verilen metnin büyük olasılıkla Türkçe olup olmadığını kontrol eder.
     * CJK / Kiril karakterleri veya İngilizce kelimeler içeriyorsa kesinlikle Türkçe değildir.
     */
    private fun isLikelyTurkish(text: String): Boolean {
        if (text.isBlank()) return false

        // CJK (Çince/Japonca/Korece) veya Kiril harfleri varsa Türkçe değildir
        var nonLatinScriptCount = 0
        var letterCount = 0
        for (ch in text) {
            if (ch.isLetter()) {
                letterCount++
                val code = ch.code
                if (code in 0x0400..0x04FF || // Cyrillic
                    code in 0x3040..0x30FF || // Hiragana & Katakana
                    code in 0x3400..0x4DBF || // CJK Ext A
                    code in 0x4E00..0x9FFF || // CJK Unified
                    code in 0xAC00..0xD7AF    // Hangul
                ) {
                    nonLatinScriptCount++
                }
            }
        }
        if (letterCount == 0) return false
        if (nonLatinScriptCount > 0 && (nonLatinScriptCount >= 3 || nonLatinScriptCount * 10 >= letterCount)) {
            return false
        }

        val words = text.lowercase().split(Regex("""[\s\p{Punct}]+""")).filter { it.isNotBlank() }
        if (words.isEmpty()) return false

        // Yaygın İngilizce kelimeler — eğer bunlar varsa metin kesinlikle İngilizce/yabancıdır
        val englishWords = setOf(
            "the", "be", "to", "of", "and", "a", "in", "that", "have", "i",
            "it", "for", "not", "on", "with", "he", "as", "you", "do", "at",
            "this", "but", "his", "by", "from", "they", "we", "say", "her",
            "she", "or", "an", "will", "my", "one", "all", "would", "there",
            "their", "what", "so", "up", "out", "if", "about", "who", "get",
            "which", "go", "me", "when", "make", "can", "like", "time", "no",
            "just", "him", "know", "take", "into", "year", "your", "good",
            "some", "could", "them", "see", "other", "than", "then", "now",
            "only", "its", "over", "also", "after", "two", "how", "work",
            "first", "even", "new", "because", "born", "voiced", "role",
            "series", "anime", "manga", "high", "school", "music", "band",
            "studio", "company", "produced", "written", "directed", "story",
            "character", "characters", "world", "life", "years", "was", "were",
            "is", "are", "has", "had", "been", "into", "through", "during"
        )
        val englishCount = words.count { it in englishWords }
        if (englishCount >= 2) {
            return false
        }

        // Belirgin Türkçe kelimeler
        val turkishWords = setOf(
            "bir", "için", "olan", "sonra", "ancak", "olarak", "tarafından",
            "nedeniyle", "birlikte", "bulunan", "olduğu", "göre", "kendi",
            "çünkü", "böylece", "arasında", "yapılan", "yılında", "bölüm",
            "karakter", "karakteri", "biyografi", "hakkında", "seslendiren",
            "sanatçı", "sanatçısı", "doğdu", "başladı", "aldı", "ilgili",
            "üzerine", "karşı", "kadar", "daha", "çok", "गöre", "ise", "ile"
        )
        val turkishCount = words.count { it in turkishWords }

        // Yalnızca Türkçe'ye özgü karakterler
        val distinctTurkishChars = setOf('ğ', 'Ğ', 'ş', 'Ş', 'ı', 'İ')
        val distinctCharCount = text.count { it in distinctTurkishChars }

        if (distinctCharCount >= 4 && englishCount == 0 && (turkishCount >= 1 || words.size <= 12)) {
            return true
        }
        return turkishCount >= 2 && turkishCount > englishCount * 2
    }

    suspend fun translate(text: String?): String = withContext(Dispatchers.IO) {
        if (text.isNullOrBlank()) return@withContext ""
        ensurePrefsInitialized()
        val sourceLang = KitsugiTranslatePrefs.sourceLanguage.ifBlank { "auto" }
        val targetLang = KitsugiTranslatePrefs.resolvedTarget()
        translateTo(text, sourceLang, targetLang)
    }

    suspend fun translateTo(
        text: String?,
        sourceLang: String,
        targetLang: String,
        onPartial: ((String) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        if (text.isNullOrBlank()) return@withContext ""

        val resolvedSource = sourceLang.trim().ifBlank { "auto" }
        val resolvedTarget = targetLang.trim().ifBlank { "tr" }
        val trimmed = text.trim().cleanShikimoriBbCode()
        if (trimmed.isBlank()) return@withContext ""

        // Teknik metin (yalnızca URL, dosya adı, `File:` ad alanı değeri…) çevrilmez.
        // Google Translate bu değerlere boşluk ekler / yol parçasını çevirir ve bağlantı
        // erişilemez hâle gelir; hiç istek yapmadan orijinali döndürmek en güvenli yol.
        if (isTechnicalTranslationValue(trimmed)) return@withContext trimmed

        // Metin zaten Türkçeyse çevirme — gereksiz API çağrısını önle
        if (resolvedTarget.equals("tr", ignoreCase = true) && isLikelyTurkish(trimmed)) {
            return@withContext trimmed
        }

        val hash = md5("${trimmed}_${resolvedSource}_${resolvedTarget}")

        // 1a. Bellek önbelleği (Room'dan önce; değerler temizlenmiş hâliyle saklanır)
        memoryGet(hash)?.let { memoryHit ->
            onPartial?.let { runCatching { it(memoryHit) } }
            return@withContext memoryHit
        }

        // 1. Check local Room database cache
        val cached = runCatching { dao.getTranslation(hash) }.getOrNull()
        if (!cached.isNullOrBlank()) {
            val cleanedCached = cached.cleanShikimoriBbCode()
            memoryPut(hash, cleanedCached)
            onPartial?.let { runCatching { it(cleanedCached) } }
            return@withContext cleanedCached
        }

        // 2. If not cached, fetch translation from Google Translate.
        //    Uzun metinler parçalara bölünür; her parça tamamlandıkça [onPartial]
        //    ile çevrilen kısım UI'ya akıtılır (kullanıcı beklemeden okumaya başlar).
        val result = fetchFromGoogle(trimmed, resolvedSource, resolvedTarget, onPartial)
        val translated = result.text.cleanShikimoriBbCode()

        // 3. Cache the success response in DB — YALNIZCA tam başarıda.
        //    Kısmî çeviriyi tam metin anahtarına yazmayız; sonraki açılışta kalan parçalar tamamlanır.
        if (result.fullyTranslated && translated.isNotBlank() && translated != trimmed) {
            memoryPut(hash, translated)
            runCatching {
                dao.insertTranslation(TranslationCacheEntity(hash, translated))
            }
            return@withContext translated
        }

        return@withContext if (translated.isBlank()) trimmed else translated
    }

    /** DOM translation needs an explicit failure signal, and must not run BBCode cleanup
     * on literal web text. Uses the same Google transport, request gate and Room cache.
     * External translator-app preferences are intents, not background service providers.
     */
    suspend fun translateWebPageText(text: String, sourceLang: String, targetLang: String): String? =
        withContext(Dispatchers.IO) {
            val original = text.trim()
            if (original.isEmpty()) return@withContext original
            val source = sourceLang.trim().ifBlank { "auto" }
            val target = targetLang.trim().ifBlank { "tr" }
            if (source.equals(target, true) || isTechnicalTranslationValue(original)) return@withContext original
            val hash = md5("webdom-v1_${source}_${target}_$original")
            memoryGet(hash)?.let { return@withContext it }
            val cached = try {
                dao.getTranslation(hash)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (!cached.isNullOrBlank()) {
                memoryPut(hash, cached)
                return@withContext cached
            }
            // Mask within each chunk: token integrity must not require a URL token
            // belonging to another chunk of a long DOM text node.
            // Parçalar birbirinden bağımsızdır: eşzamanlılık kapısı (requestGate)
            // ağı zaten sınırladığı için parçalar paralel istenir; sıra korunur.
            val chunks = splitIntoChunks(original, WEB_MAX_CHUNK_CHARS)
            val results = if (chunks.size == 1) {
                listOf(fetchFromGoogle(chunks[0], source, target, null, WEB_MAX_CHUNK_CHARS))
            } else {
                coroutineScope {
                    chunks.map { chunk ->
                        async { fetchFromGoogle(chunk, source, target, null, WEB_MAX_CHUNK_CHARS) }
                    }.awaitAll()
                }
            }
            if (results.any { !it.fullyTranslated || it.text.isBlank() }) return@withContext null
            val translated = results.joinToString("\n\n") { it.text }
            if (translated.length > minOf(40_000, original.length * 6 + 256)) return@withContext null
            memoryPut(hash, translated)
            try {
                dao.insertTranslation(TranslationCacheEntity(hash, translated))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Cache failure must not discard a valid translation.
            }
            translated
        }

    /**
     * Tek bir sayfa metni için AĞSIZ önbellek sorgusu (bellek → Room).
     *
     * Sayfa çevirisi paketleri metin metin önbelleğe bakarak yalnızca eksikleri
     * ağa gönderir; böylece paketin dizilişi değişse de (farklı kaydırma konumu,
     * sayfaya dönüş) daha önce çevrilmiş paragraflar yeniden çevrilmez.
     * `null` = önbellekte yok. Teknik metin / aynı dil durumunda çevrilmiş hâliyle
     * aynı olan özgün metin döner (ağa gitmeye gerek yoktur).
     */
    suspend fun cachedWebPageText(text: String, sourceLang: String, targetLang: String): String? =
        withContext(Dispatchers.IO) {
            val original = text.trim()
            if (original.isEmpty()) return@withContext original
            val source = sourceLang.trim().ifBlank { "auto" }
            val target = targetLang.trim().ifBlank { "tr" }
            if (source.equals(target, true) || isTechnicalTranslationValue(original)) return@withContext original
            val hash = md5("webdom-v1_${source}_${target}_$original")
            memoryGet(hash)?.let { return@withContext it }
            val cached = try {
                dao.getTranslation(hash)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (!cached.isNullOrBlank()) memoryPut(hash, cached)
            cached?.takeIf { it.isNotBlank() }
        }

    /**
     * Paket hâlinde çevrilmiş sayfa metinlerini TEK TEK önbelleğe yazar.
     *
     * Anahtarlar [translateWebPageText] / [cachedWebPageText] ile aynıdır; toplu
     * yazım tek Room işleminde yapılır. Yazım başarısızlığı çeviriyi etkilemez.
     */
    suspend fun storeWebPageTexts(
        pairs: List<Pair<String, String>>,
        sourceLang: String,
        targetLang: String
    ) {
        if (pairs.isEmpty()) return
        withContext(Dispatchers.IO) {
            val source = sourceLang.trim().ifBlank { "auto" }
            val target = targetLang.trim().ifBlank { "tr" }
            val entities = pairs.mapNotNull { (originalText, translatedText) ->
                val original = originalText.trim()
                val translated = translatedText.trim()
                if (original.isEmpty() || translated.isEmpty()) return@mapNotNull null
                val hash = md5("webdom-v1_${source}_${target}_$original")
                memoryPut(hash, translated)
                TranslationCacheEntity(hash, translated)
            }.distinctBy { it.originalHash }
            if (entities.isEmpty()) return@withContext
            try {
                dao.insertTranslations(entities)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Cache failure must not discard valid translations.
            }
        }
    }

    suspend fun translateTo(text: String?, targetLang: String): String = translateTo(text, "auto", targetLang)

    /**
     * Metni KULLANICININ ayarladığı hedef dile çevirir (Ayarlar → Çeviri → Hedef dil;
     * ayar yoksa uygulama dili).
     */
    suspend fun translateToTarget(text: String?, onPartial: ((String) -> Unit)? = null): String {
        ensurePrefsInitialized()
        val sourceLang = KitsugiTranslatePrefs.sourceLanguage.ifBlank { "auto" }
        val targetLang = KitsugiTranslatePrefs.resolvedTarget()
        return translateTo(text, sourceLang, targetLang, onPartial)
    }

    suspend fun translateToEnglish(text: String?): String = translateTo(text, "en")

    /**
     * Maskelenmiş metin + çeviri sonrası geri kurma ve bütünlük denetimi.
     *
     * [isIntact] çevirinin korunan jetonları **kaybetmediğini** doğrular. Google bir jetonu
     * düşürürse korunan URL/dosya adı metinden tamamen silinirdi; bu durumda çeviri
     * reddedilir ve o parça orijinal hâliyle gösterilir (kopuk bağlantı gösterilmez).
     */
    private class MaskedMarkdown(
        val masked: String,
        val unmask: (String) -> String,
        val isIntact: (String) -> Boolean
    )

    /**
     * Maskelenmesi gereken hiçbir kalıp içermeyen düz metinler için hızlı karar.
     *
     * Korunan her kalıbın zorunlu tetikleyicisi aranır: bağlantı/gömme köşeli
     * parantez veya `http`, şablon için `{{`, e-posta için `@`, spoiler için `~`,
     * ad alanı için kelimenin kendisi + `:`. Hiçbiri yoksa maskeleme çıktısı
     * girdinin aynısıdır ve jeton listeleri boştur; regex taramaları atlanabilir.
     * Şüphe hâlinde (tetikleyici VARSA) her zamanki tam maskeleme çalışır —
     * koruma davranışı değişmez.
     */
    private fun needsMasking(text: String): Boolean {
        if (text.indexOfAny(charArrayOf('[', '{', '@', '~')) >= 0) return true
        if (text.contains("http", ignoreCase = true)) return true
        if ('(' in text && (text.contains("img", ignoreCase = true) ||
                text.contains("webm", ignoreCase = true) ||
                text.contains("youtube", ignoreCase = true))
        ) return true
        if (':' in text && WIKI_NAMESPACE_WORDS.any { text.contains(it, ignoreCase = true) }) return true
        return false
    }

    /**
     * Markdown linklerini, medya etiketlerini (img/webm/youtube), spoiler işaretlerini
     * ve URL'leri Google Translate'in bozmasını önlemek için harf içermeyen sembolik
     * yer tutucularla maskeler (böylece Google Translate yer tutucu içindeki kelimeleri çeviremez).
     */
    private fun maskMarkdown(text: String): MaskedMarkdown {
        if (!needsMasking(text)) return MaskedMarkdown(text, { it }, { true })
        var masked = text

        // 1. AniList / Markdown görsel ve video gömmeleri: img220(https://...), webm(https://...), ![](https://...)
        val rawTokens = mutableListOf<String>()
        masked = MEDIA_EMBED_REGEX.replace(masked) { match ->
            val idx = rawTokens.size
            rawTokens.add(match.value)
            "⟪@$idx⟫"
        }

        // 2. Markdown linkleri: [Etiket](https://...)
        val links = mutableListOf<Pair<String, String>>() // (label, url)
        masked = LINK_REGEX.replace(masked) { match ->
            val idx = links.size
            val label = match.groupValues[1].trim()
            val url = match.groupValues[2].trim()
            links.add(Pair(label, url))
            "⟪#$idx⟫$label⟪/$idx⟫"
        }

        // 3. Korunacak bağımsız URL'ler
        masked = RAW_URL_REGEX.replace(masked) { match ->
            val idx = rawTokens.size
            rawTokens.add(match.value.trim())
            "⟪@$idx⟫"
        }

        // 3.5 Wiki'ye özgü korunacak jetonlar: [[Sayfa|Etiket]], {{şablon}}, File:/Image:/
        //     Category: ad alanı değerleri ve e-posta adresleri. Bunlar çevrilirse bağlantı
        //     hedefi ya da şablon adı bozulur; kullanıcı yanlış sayfaya düşer.
        masked = WIKI_TOKEN_REGEX.replace(masked) { match ->
            val idx = rawTokens.size
            rawTokens.add(match.value.trim())
            "⟪@$idx⟫"
        }

        // 4. AniList spoiler sınırları (~!...!~): iç metin çevrilsin ama ~! ve !~ bozulmasın
        val spoilerOpenToken = "⟪!9990⟫"
        val spoilerCloseToken = "⟪!9991⟫"
        val hasAniListSpoilers = masked.contains("~!") && masked.contains("!~")
        if (hasAniListSpoilers) {
            masked = masked
                .replace("~!", spoilerOpenToken)
                .replace("!~", spoilerCloseToken)
        }

        val unmasker: (String) -> String = { translated ->
            var res = translated

            if (hasAniListSpoilers) {
                res = res
                    .replace(Regex("""⟪\s*!\s*9990\s*⟫"""), "~!")
                    .replace(Regex("""⟪\s*!\s*9991\s*⟫"""), "!~")
                    .replace(Regex("""~\s+!"""), "~!")
                    .replace(Regex("""!\s+~"""), "!~")
            }

            // Ham URL ve medya gömmelerini geri yükle
            rawTokens.forEachIndexed { i, token ->
                res = res.replace(Regex("""⟪\s*@\s*${i}\s*⟫"""), Regex.escapeReplacement(token))
            }

            // Markdown linklerini geri yükle: etiket çevrildi, URL korundu
            links.forEachIndexed { i, (origLabel, url) ->
                val pattern = Regex("""⟪\s*#\s*${i}\s*⟫(.*?)⟪\s*/\s*${i}\s*⟫""", RegexOption.DOT_MATCHES_ALL)
                if (pattern.containsMatchIn(res)) {
                    res = pattern.replace(res) { m ->
                        val translatedLabel = m.groupValues[1].trim().ifBlank { origLabel }
                        "[$translatedLabel]($url)"
                    }
                } else {
                    val openRegex = Regex("""⟪\s*#\s*${i}\s*⟫""")
                    val closeRegex = Regex("""⟪\s*/\s*${i}\s*⟫""")
                    if (openRegex.containsMatchIn(res) && closeRegex.containsMatchIn(res)) {
                        res = res.replace(openRegex, "[").replace(closeRegex, "]($url)")
                    } else {
                        res = res.replace(openRegex, "").replace(closeRegex, " ($url)")
                    }
                }
            }

            // Eski önbellekten veya bozulmuş yanıttan kalabilecek artık yer tutucuları temizle
            res = res
                .replace(Regex("""⟪\s*[#@/!]?\s*\d+\s*⟫"""), "")
                .replace(Regex("""⟦\s*L_\d+_(?:START|END|BAŞLANGIÇ|SON)\s*⟧""", RegexOption.IGNORE_CASE), "")
            res
        }

        // Bütünlük denetimi: korunan jetonlardan biri çeviri yanıtında kaybolduysa
        // URL/dosya adı metinden tamamen silinmiş demektir → çeviri reddedilir.
        val isIntact: (String) -> Boolean = { translated ->
            rawTokens.indices.all { index ->
                Regex("⟪\\s*@\\s*$index\\s*⟫").containsMatchIn(translated)
            } && links.indices.all { index ->
                Regex("⟪\\s*#\\s*$index\\s*⟫").containsMatchIn(translated)
            }
        }

        return MaskedMarkdown(masked, unmasker, isIntact)
    }

    /**
     * Uzun metinleri Google Translate istek boyutu sınırını aşmayacak parçalara böler.
     */
    private fun splitIntoChunks(text: String, maxChunkSize: Int = MAX_CHUNK_CHARS): List<String> {
        if (text.length <= maxChunkSize) return listOf(text)
        val chunks = mutableListOf<String>()
        var currentChunk = StringBuilder()

        fun flush() {
            if (currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toString())
                currentChunk = StringBuilder()
            }
        }

        fun addPiece(piece: String, separator: String) {
            if (piece.isEmpty()) return
            if (currentChunk.isNotEmpty() && currentChunk.length + separator.length + piece.length > maxChunkSize) {
                flush()
            }
            if (currentChunk.isNotEmpty()) currentChunk.append(separator)
            currentChunk.append(piece)
        }

        for (paragraph in text.split("\n\n")) {
            if (paragraph.length > maxChunkSize) {
                for (line in paragraph.split("\n")) {
                    if (line.length > maxChunkSize) {
                        for (sentence in splitOversizedLine(line, maxChunkSize)) {
                            addPiece(sentence, " ")
                        }
                    } else {
                        addPiece(line, "\n")
                    }
                }
            } else {
                addPiece(paragraph, "\n\n")
            }
        }
        flush()

        return if (chunks.isEmpty()) listOf(text) else chunks
    }

    /**
     * Sınırı aşan tek satırı cümle sonlarından böler.
     * CJK noktalamaları (。！？；…) sonrasında boşluk olmasa bile cümle sonu sayılır;
     * Latin noktalamalarında (.!?) ise "3.14" veya "Mr." bölünmesin diye boşluk/parantez/tırnak aranır.
     */
    private fun splitOversizedLine(line: String, maxChunkSize: Int): List<String> {
        val cjkEnders = "…۔۔。！？；"
        val asciiEnders = ".!?"

        val sentences = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < line.length) {
            val c = line[i]
            sb.append(c)
            val isCjkEnder = cjkEnders.indexOf(c) >= 0
            val isAsciiEnder = asciiEnders.indexOf(c) >= 0
            if (isCjkEnder || isAsciiEnder) {
                val next = line.getOrNull(i + 1)
                val isBoundary = if (isCjkEnder) {
                    true
                } else {
                    next == null || next.isWhitespace() || next == '"' || next == '\'' || next == ')' || next == ']' || next == '”'
                }
                if (isBoundary) {
                    val s = sb.toString().trim()
                    if (s.isNotEmpty()) sentences.add(s)
                    sb.setLength(0)
                }
            }
            i++
        }
        if (sb.isNotBlank()) sentences.add(sb.toString().trim())
        sentences.removeAll { it.isBlank() }
        if (sentences.isEmpty()) return listOf(line)

        val pieces = mutableListOf<String>()
        for (sentence in sentences) {
            if (sentence.length <= maxChunkSize) {
                pieces.add(sentence)
                continue
            }
            var wordChunk = StringBuilder()
            for (word in sentence.split(" ")) {
                if (wordChunk.isNotEmpty() && wordChunk.length + 1 + word.length > maxChunkSize) {
                    pieces.add(wordChunk.toString())
                    wordChunk = StringBuilder()
                }
                if (wordChunk.isNotEmpty()) wordChunk.append(' ')
                wordChunk.append(word)
                while (wordChunk.length > maxChunkSize) {
                    pieces.add(wordChunk.substring(0, maxChunkSize))
                    wordChunk.delete(0, maxChunkSize)
                }
            }
            if (wordChunk.isNotEmpty()) pieces.add(wordChunk.toString())
        }
        return pieces
    }

    private suspend fun Call.executeCancellable(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation {
            runCatching { cancel() }
        }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) {
                    cont.resume(response)
                } else {
                    response.close()
                }
            }
        })
    }

    /**
     * Google Translate'in farklı uç noktalarından dönen JSON yanıtlarını (hem `dj=1`
     * nesne biçimi hem klasik iç içe dizi biçimi) ayrıştırır.
     */
    private fun parseTranslationResponse(responseText: String): String? {
        val trimmed = responseText.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("<")) return null

        if (trimmed.startsWith("{")) {
            val obj = JSONObject(trimmed)
            val sentences = obj.optJSONArray("sentences")
            if (sentences != null && sentences.length() > 0) {
                val sb = StringBuilder()
                for (i in 0 until sentences.length()) {
                    val item = sentences.optJSONObject(i) ?: continue
                    val trans = item.optString("trans", "")
                    if (trans.isNotEmpty()) sb.append(trans)
                }
                if (sb.isNotEmpty()) return sb.toString()
            }
        }

        if (trimmed.startsWith("[")) {
            val jsonArray = JSONArray(trimmed)
            val firstElem = jsonArray.opt(0)
            if (firstElem is JSONArray) {
                val sb = StringBuilder()
                for (i in 0 until firstElem.length()) {
                    val item = firstElem.opt(i)
                    when (item) {
                        is JSONArray -> {
                            val part = item.optString(0, "")
                            if (part.isNotEmpty()) sb.append(part)
                        }
                        is String -> {
                            if (i == 0 && item.isNotEmpty()) sb.append(item)
                        }
                    }
                }
                if (sb.isNotEmpty()) return sb.toString()
            } else if (firstElem is String && firstElem.isNotEmpty()) {
                return firstElem
            }
        }

        return null
    }

    private fun buildEndpointRequest(
        endpointIndex: Int,
        text: String,
        sourceLang: String,
        targetLang: String
    ): Request {
        val formBody = FormBody.Builder()
            .add("q", text)
            .build()

        return when (endpointIndex % 4) {
            0 -> Request.Builder()
                .url("https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sourceLang&tl=$targetLang&dt=t&dj=1")
                .post(formBody)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .build()
            1 -> Request.Builder()
                .url("https://translate.google.com/translate_a/single?client=at&sl=$sourceLang&tl=$targetLang&dt=t&dj=1")
                .post(formBody)
                .header("User-Agent", "GoogleTranslate/6.29.0.05.421483610 (Linux; U; Android 13; Pixel 7)")
                .build()
            2 -> Request.Builder()
                .url("https://translate.googleapis.com/translate_a/single?client=dict-chrome-ex&sl=$sourceLang&tl=$targetLang&dt=t&dj=1")
                .post(formBody)
                .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .build()
            else -> {
                val encoded = URLEncoder.encode(text, "UTF-8")
                if (encoded.length <= 3600) {
                    Request.Builder()
                        .url("https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sourceLang&tl=$targetLang&dt=t&q=$encoded")
                        .get()
                        .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15")
                        .build()
                } else {
                    Request.Builder()
                        .url("https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=$sourceLang&tl=$targetLang&dt=t")
                        .post(formBody)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                        .build()
                }
            }
        }
    }

    /**
     * Tek parça çevirisi ister. Birincil uç nokta 429/403/5xx döndürürse beklemeden
     * yedek Google Translate uç noktalarına geçer.
     */
    private suspend fun fetchSingleChunk(
        text: String,
        sourceLang: String,
        targetLang: String,
        startEndpointIndex: Int = 0
    ): String? {
        for (offset in 0 until 4) {
            val endpointIdx = (startEndpointIndex + offset) % 4
            val request = buildEndpointRequest(endpointIdx, text, sourceLang, targetLang)
            try {
                val parsed = translationHttpClient.newCall(request).executeCancellable().use { response ->
                    if (!response.isSuccessful) {
                        android.util.Log.w(
                            "TranslationManager",
                            "Çeviri uç noktası #$endpointIdx başarısız: HTTP ${response.code} (uzunluk=${text.length})"
                        )
                        null
                    } else {
                        val body = response.body?.string()
                        if (body.isNullOrBlank()) null else parseTranslationResponse(body)
                    }
                }
                if (!parsed.isNullOrBlank()) {
                    return parsed
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                android.util.Log.w("TranslationManager", "Çeviri uç noktası #$endpointIdx istisna: ${e.message}")
            }
        }
        return null
    }

    /**
     * Tek parça için yeniden deneme + parça bazlı Room önbelleği.
     * Başarılı olan her parça kendi özet anahtarıyla da önbelleğe yazılır; böylece
     * çok parçalı uzun biyografilerde/incelemelerde yarıda kesilme olsa bile
     * çevrilmiş parçalar kaybolmaz.
     */
    private suspend fun fetchSingleChunkWithRetry(
        text: String,
        sourceLang: String,
        targetLang: String,
        maxAttempts: Int = 3
    ): String? {
        val chunkHash = md5("chunk_${text}_${sourceLang}_${targetLang}")
        memoryGet(chunkHash)?.let { return it }
        val cachedChunk = runCatching { dao.getTranslation(chunkHash) }.getOrNull()
        if (!cachedChunk.isNullOrBlank()) {
            memoryPut(chunkHash, cachedChunk)
            return cachedChunk
        }

        repeat(maxAttempts) { attempt ->
            val result = requestGate.withPermit {
                fetchSingleChunk(text, sourceLang, targetLang, startEndpointIndex = attempt)
            }
            if (!result.isNullOrBlank()) {
                if (result != text) {
                    memoryPut(chunkHash, result)
                    runCatching { dao.insertTranslation(TranslationCacheEntity(chunkHash, result)) }
                }
                return result
            }
            if (attempt < maxAttempts - 1) {
                delay(600L * (attempt + 1))
            }
        }
        android.util.Log.e("TranslationManager", "Çeviri parçası $maxAttempts denemede çevrilemedi (uzunluk=${text.length})")
        return null
    }

    /** Google çevirisi sonucu: birleşik metin + tüm parçaların başarıp başarılmadığı. */
    private class GoogleTranslationResult(val text: String, val fullyTranslated: Boolean)

    private suspend fun fetchFromGoogle(
        text: String,
        sourceLang: String,
        targetLang: String,
        onPartial: ((String) -> Unit)? = null,
        chunkChars: Int = MAX_CHUNK_CHARS
    ): GoogleTranslationResult {
        return try {
            val mask = maskMarkdown(text)
            val chunks = splitIntoChunks(mask.masked, maxChunkSize = chunkChars)
            val translatedChunks = mutableListOf<String>()
            var allSucceeded = true

            for ((index, chunk) in chunks.withIndex()) {
                val raw = fetchSingleChunkWithRetry(chunk, sourceLang, targetLang)
                // Korumalı jeton (URL/dosya adı/bağlantı) kaybı varsa çeviri reddedilir:
                // bozuk bağlantı göstermektense kaynak metin korunur.
                val translated = if (raw == null || !mask.isIntact(raw)) {
                    if (raw != null) {
                        android.util.Log.w(
                            "TranslationManager",
                            "Çeviri korunan bağlantı jetonunu kaybetti; parça orijinal bırakıldı (uzunluk=${chunk.length})"
                        )
                    }
                    allSucceeded = false
                    chunk
                } else {
                    raw
                }
                translatedChunks.add(translated)
                if (onPartial != null && index < chunks.lastIndex) {
                    val remaining = chunks.subList(index + 1, chunks.size).joinToString("\n\n")
                    runCatching {
                        onPartial(mask.unmask(translatedChunks.joinToString("\n\n") + "\n\n" + remaining))
                    }
                }
            }

            GoogleTranslationResult(mask.unmask(translatedChunks.joinToString("\n\n")), allSucceeded)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            android.util.Log.e("TranslationManager", "fetchFromGoogle failed: ${e.message}", e)
            GoogleTranslationResult(text, false)
        }
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
