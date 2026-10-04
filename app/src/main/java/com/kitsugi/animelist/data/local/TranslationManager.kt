package com.kitsugi.animelist.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import okhttp3.Request
import java.security.MessageDigest

import com.kitsugi.animelist.utils.cleanShikimoriBbCode

class TranslationManager(context: Context) {
    private val dbContext = context.applicationContext
    private val db = KitsugiDatabase.getDatabase(dbContext)
    private val dao = db.translationCacheDao()

    /**
     * Verilen metnin büyük olasılıkla Türkçe olup olmadığını kontrol eder.
     * İngilizce kelimeler içeriyorsa kesinlikle Türkçe değildir.
     * Yalnızca Türkçe'ye özgü harfler (ğ, ş, ı) veya belirgin Türkçe kelimeler içeriyorsa true döner.
     */
    private fun isLikelyTurkish(text: String): Boolean {
        if (text.isBlank()) return false
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
            "series", "anime", "manga", "high", "school", "music", "band"
        )
        val englishCount = words.count { it in englishWords }
        if (englishCount >= 2) {
            return false
        }

        // Yalnızca Türkçe'ye özgü karakterler (Almanca/Fransızca ile ortak olan ö, ü, ç HARİÇ)
        val distinctTurkishChars = setOf('ğ', 'Ğ', 'ş', 'Ş', 'ı', 'İ')
        val distinctCharCount = text.count { it in distinctTurkishChars }
        if (distinctCharCount >= 3) return true

        // Belirgin Türkçe kelimeler (Japonca veya Fransızca ile çakışabilecek da, de, ne, ve HARİÇ)
        val turkishWords = setOf(
            "bir", "için", "olan", "sonra", "ancak", "olarak", "tarafından",
            "nedeniyle", "birlikte", "bulunan", "olduğu", "göre", "kendi",
            "çünkü", "böylece", "arasında", "yapılan", "yılında", "bölüm",
            "karakter", "karakteri", "biyografi", "hakkında", "seslendiren",
            "sanatçı", "sanatçısı", "doğdu", "başladı", "yer", "aldı"
        )
        val turkishCount = words.count { it in turkishWords }
        return turkishCount >= 2 && turkishCount > englishCount
    }

    suspend fun translate(text: String?): String = withContext(Dispatchers.IO) {
        if (text.isNullOrBlank()) return@withContext ""
        val settings = com.kitsugi.animelist.data.settings.SettingsDataStore(dbContext).settingsFlow.first()
        val sourceLang = settings.translateSourceLanguage
        val targetLang = settings.translateTargetLanguage
        translateTo(text, sourceLang, targetLang)
    }

    suspend fun translateTo(text: String?, sourceLang: String, targetLang: String): String = withContext(Dispatchers.IO) {
        if (text.isNullOrBlank()) return@withContext ""
        
        val trimmed = text.trim().cleanShikimoriBbCode()

        // Metin zaten Türkçeyse çevirme — gereksiz API çağrısını önle
        if (targetLang == "tr" && isLikelyTurkish(trimmed)) return@withContext trimmed

        val hash = md5(trimmed + "_" + sourceLang + "_" + targetLang)

        // 1. Check local Room database cache
        val cached = runCatching { dao.getTranslation(hash) }.getOrNull()
        if (cached != null) {
            return@withContext cached.cleanShikimoriBbCode()
        }

        // 2. If not cached, fetch translation from Google Translate
        val translated = fetchFromGoogle(trimmed, sourceLang, targetLang).cleanShikimoriBbCode()
        
        // 3. Cache the success response in DB
        if (translated.isNotBlank() && translated != trimmed) {
            runCatching {
                dao.insertTranslation(TranslationCacheEntity(hash, translated))
            }
            return@withContext translated
        }

        return@withContext trimmed
    }

    suspend fun translateTo(text: String?, targetLang: String): String = translateTo(text, "auto", targetLang)

    suspend fun translateToTurkish(text: String?): String = translateTo(text, "tr")

    suspend fun translateToEnglish(text: String?): String = translateTo(text, "en")

    /**
     * Markdown linklerini ve URL'leri Google Translate'in bozmasını önlemek için maskeler.
     */
    private fun maskMarkdown(text: String): Pair<String, (String) -> String> {
        val linkRegex = Regex("""\[([^\]]+)\]\((https?://[^\s)]+)\)""")
        val links = mutableListOf<Pair<String, String>>() // (label, url)
        var masked = linkRegex.replace(text) { match ->
            val idx = links.size
            links.add(Pair(match.groupValues[1].trim(), match.groupValues[2].trim()))
            "⟦L_${idx}_START⟧${match.groupValues[1].trim()}⟦L_${idx}_END⟧"
        }

        // Korunacak bağımsız URL'ler
        val rawUrlRegex = Regex("""(?<![\[\(="'])(https?://[^\s<>"'\)]+)(?![\]\)"'])""")
        val rawUrls = mutableListOf<String>()
        masked = rawUrlRegex.replace(masked) { match ->
            val idx = rawUrls.size
            rawUrls.add(match.groupValues[1].trim())
            "⟦URL_${idx}⟧"
        }

        val unmasker: (String) -> String = { translated ->
            var res = translated
            // Ham URL'leri geri yükle
            rawUrls.forEachIndexed { i, url ->
                res = res.replace("⟦URL_${i}⟧", url)
                    .replace("⟦url_${i}⟧", url)
                    .replace("⟦ URL_${i} ⟧", url)
                    .replace("⟦ URL _ ${i} ⟧", url)
            }
            // Markdown linklerini geri yükle: etiket çevrildi, URL korundu
            links.forEachIndexed { i, (origLabel, url) ->
                val pattern = Regex("""⟦\s*L_${i}_START\s*⟧(.*?)⟦\s*L_${i}_END\s*⟧""", RegexOption.DOT_MATCHES_ALL)
                if (pattern.containsMatchIn(res)) {
                    res = pattern.replace(res) { m ->
                        val translatedLabel = m.groupValues[1].trim().ifBlank { origLabel }
                        "[$translatedLabel]($url)"
                    }
                } else {
                    res = res.replace("⟦L_${i}_START⟧", "[")
                        .replace("⟦L_${i}_END⟧", "]($url)")
                }
            }
            res
        }

        return Pair(masked, unmasker)
    }

    /**
     * Uzun metinleri Google Translate istek boyutu sınırını (~2500 karakter) aşmayacak paragraflara böler.
     */
    private fun splitIntoChunks(text: String, maxChunkSize: Int = 2000): List<String> {
        if (text.length <= maxChunkSize) return listOf(text)
        val paragraphs = text.split("\n\n")
        val chunks = mutableListOf<String>()
        val currentChunk = StringBuilder()

        for (p in paragraphs) {
            if (currentChunk.isNotEmpty() && (currentChunk.length + p.length + 2) > maxChunkSize) {
                chunks.add(currentChunk.toString())
                currentChunk.clear()
            }
            if (p.length > maxChunkSize) {
                // Paragraf tek başına çok uzunsa satırlara böl
                val lines = p.split("\n")
                for (l in lines) {
                    if (currentChunk.isNotEmpty() && (currentChunk.length + l.length + 1) > maxChunkSize) {
                        chunks.add(currentChunk.toString())
                        currentChunk.clear()
                    }
                    if (currentChunk.isNotEmpty()) currentChunk.append("\n")
                    currentChunk.append(l)
                }
            } else {
                if (currentChunk.isNotEmpty()) currentChunk.append("\n\n")
                currentChunk.append(p)
            }
        }
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString())
        }
        return if (chunks.isEmpty()) listOf(text) else chunks
    }

    private fun fetchSingleChunk(text: String, sourceLang: String, targetLang: String): String {
        val formBody = okhttp3.FormBody.Builder()
            .add("q", text)
            .build()

        val urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sourceLang&tl=$targetLang&dt=t"
        val request = Request.Builder()
            .url(urlStr)
            .post(formBody)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .build()

        return com.kitsugi.animelist.core.network.KitsugiHttpClient.client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val responseText = response.body?.string() ?: return text
                val jsonArray = JSONArray(responseText)
                val sentences = jsonArray.optJSONArray(0) ?: return text
                val result = StringBuilder()
                for (i in 0 until sentences.length()) {
                    val sentence = sentences.optJSONArray(i)
                    val translatedPart = sentence?.optString(0)
                    if (translatedPart != null) {
                        result.append(translatedPart)
                    }
                }
                result.toString()
            } else {
                text
            }
        }
    }

    private fun fetchFromGoogle(text: String, sourceLang: String, targetLang: String): String {
        return try {
            val (maskedText, unmasker) = maskMarkdown(text)
            val chunks = splitIntoChunks(maskedText, maxChunkSize = 2000)
            val translatedChunks = chunks.map { chunk ->
                fetchSingleChunk(chunk, sourceLang, targetLang)
            }
            val combined = translatedChunks.joinToString("\n\n")
            unmasker(combined)
        } catch (e: Exception) {
            android.util.Log.e("TranslationManager", "fetchFromGoogle failed: ${e.message}", e)
            text
        }
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
