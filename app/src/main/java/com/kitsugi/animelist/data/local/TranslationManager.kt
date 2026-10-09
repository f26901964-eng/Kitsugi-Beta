package com.kitsugi.animelist.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    suspend fun translateTo(
        text: String?,
        sourceLang: String,
        targetLang: String,
        onPartial: ((String) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
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

        // 2. If not cached, fetch translation from Google Translate.
        //    Uzun metinler parçalara bölünür; her parça tamamlandıkça [onPartial]
        //    ile çevrilen kısım UI'ya akıtılır (kullanıcı beklemeden okumaya başlar).
        val result = fetchFromGoogle(trimmed, sourceLang, targetLang, onPartial)
        val translated = result.text.cleanShikimoriBbCode()

        // 3. Cache the success response in DB — YALNIZCA tam başarıda.
        //    Kısmî çeviriyi önbelleğe yazmayız; sonraki açılışta tam çeviri yeniden denenir.
        if (result.fullyTranslated && translated.isNotBlank() && translated != trimmed) {
            runCatching {
                dao.insertTranslation(TranslationCacheEntity(hash, translated))
            }
            return@withContext translated
        }

        // Kısmî çeviri: parçaların bir kısmı çevrilemedi. Özgün metni döndür (böylece
        // çağıran taraf bunu "çevrilemedi" sayar ve önbelleğe yazmaz); kısmî ilerleme
        // zaten onPartial ile ekrana yansımış oldu.
        if (!result.fullyTranslated) return@withContext trimmed

        return@withContext translated
    }

    suspend fun translateTo(text: String?, targetLang: String): String = translateTo(text, "auto", targetLang)

    suspend fun translateToTurkish(text: String?, onPartial: ((String) -> Unit)? = null): String =
        translateTo(text, "auto", "tr", onPartial)

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
     * Uzun metinleri Google Translate istek boyutu sınırını (~2500 karakter) aşmayacak parçalara böler.
     *
     * Bölme önceliği:
     *  1. Paragraf (`\n\n`) sınırları,
     *  2. Satır (`\n`) sınırları,
     *  3. CÜMLE SONLARI (nokta, soru, ünlem, üç nokta, CJK noktalaması) —
     *     tek paragraf/satır sınırı aştığında metni bozmamak için cümlelerden bölünür,
     *  4. Kelime sınırları — tek cümle bile sınırı aşıyorsa.
     *
     * NOT: Eski sürüm 3. ve 4. adımları içermiyordu; satır sonu içermeyen 2000+
     * karakterlik tek paragraflar (karakter biyografilerinde sık) TE PARÇA olarak
     * gönderiliyor ve gtx ucu bunu sessizce reddediyordu → metin çevrilmeden kalıyordu.
     */
    private fun splitIntoChunks(text: String, maxChunkSize: Int = 2000): List<String> {
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
                        // Satır tek başına sınırı aşıyor → cümle sonlarından böl
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
     * Sınırı aşan tek satırı cümle sonlarından böler. Cümle sonu sayılması için
     * noktlamadan sonra boşluk veya satır sonu gelmesi şarttır (ondalık sayılar
     * "3.14" ve "Mr." gibi kısaltmalar yanlış bölünmesin diye). Cümle bile
     * sınırdan uzunsa kelimeden, kelime devasaysa sert kesilir.
     */
    private fun splitOversizedLine(line: String, maxChunkSize: Int): List<String> {
        val sentenceEnders = ".!?…。！？；"

        // 1) Satırı cümlelere ayır
        val sentences = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < line.length) {
            val c = line[i]
            sb.append(c)
            if (sentenceEnders.indexOf(c) >= 0) {
                val next = line.getOrNull(i + 1)
                if (next == null || next.isWhitespace()) {
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

        // 2) Sınırı aşan cümleleri kelimeden böl
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
                // Tek kelime bile devasaysa (aralıksız CJK vb.) sert kes
                while (wordChunk.length > maxChunkSize) {
                    pieces.add(wordChunk.substring(0, maxChunkSize))
                    wordChunk.delete(0, maxChunkSize)
                }
            }
            if (wordChunk.isNotEmpty()) pieces.add(wordChunk.toString())
        }
        return pieces
    }

    /**
     * Tek parça çevirisi ister. Başarısızlıkta (HTTP hatası, bozuk JSON) **null** döner —
     * orijinal metni döndürmek yerine null, çağıran tarafın başarısızlığı AYIRT edebilmesini
     * sağlar (eski davranış: orijinal metin dönüyordu → sessiz "çevrilemedi").
     */
    private fun fetchSingleChunk(text: String, sourceLang: String, targetLang: String): String? {
        val formBody = okhttp3.FormBody.Builder()
            .add("q", text)
            .build()

        val urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$sourceLang&tl=$targetLang&dt=t"
        val request = Request.Builder()
            .url(urlStr)
            .post(formBody)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .build()

        return try {
            com.kitsugi.animelist.core.network.KitsugiHttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.w("TranslationManager", "Çeviri isteği başarısız: HTTP ${response.code} (parça uzunluğu=${text.length})")
                    return null
                }
                val responseText = response.body?.string() ?: return null
                val jsonArray = JSONArray(responseText)
                val sentences = jsonArray.optJSONArray(0) ?: return null
                val result = StringBuilder()
                for (i in 0 until sentences.length()) {
                    val sentence = sentences.optJSONArray(i)
                    val translatedPart = sentence?.optString(0)
                    if (translatedPart != null) {
                        result.append(translatedPart)
                    }
                }
                result.toString()
            }
        } catch (e: Exception) {
            android.util.Log.w("TranslationManager", "Çeviri isteği istisna üretti: ${e.message}")
            null
        }
    }

    /**
     * Tek parça için uygulama seviyesinde yeniden deneme. OkHttp RetryInterceptor
     * 429/5xx/ağ hatasında zaten retry eder; buradaki ek denemeler kalıcı hâle gelen
     * başarısızlıklar (ör. uzun istek sonrası geçici ret) için son bir şans daha verir.
     */
    private suspend fun fetchSingleChunkWithRetry(
        text: String,
        sourceLang: String,
        targetLang: String,
        maxAttempts: Int = 3
    ): String? {
        repeat(maxAttempts) { attempt ->
            val result = runCatching { fetchSingleChunk(text, sourceLang, targetLang) }.getOrNull()
            if (!result.isNullOrBlank()) return result
            if (attempt < maxAttempts - 1) {
                delay(600L * (attempt + 1))
            }
        }
        android.util.Log.e("TranslationManager", "Çeviri parçası ${maxAttempts} denemede çevrilemedi (uzunluk=${text.length})")
        return null
    }

    /** Google çevirisi sonucu: birleşik metin + tüm parçaların başarıp başarılmadığı. */
    private class GoogleTranslationResult(val text: String, val fullyTranslated: Boolean)

    private suspend fun fetchFromGoogle(
        text: String,
        sourceLang: String,
        targetLang: String,
        onPartial: ((String) -> Unit)? = null
    ): GoogleTranslationResult {
        return try {
            val (maskedText, unmasker) = maskMarkdown(text)
            val chunks = splitIntoChunks(maskedText, maxChunkSize = 2000)
            val translatedChunks = mutableListOf<String>()
            var allSucceeded = true

            for ((index, chunk) in chunks.withIndex()) {
                val translated = fetchSingleChunkWithRetry(chunk, sourceLang, targetLang)
                if (translated == null) {
                    // Bu parça çevrilemedi → orijinal hâliyle birleştir (karışık dil olabilir)
                    allSucceeded = false
                    translatedChunks.add(chunk)
                } else {
                    translatedChunks.add(translated)
                }
                // Uzun metinlerde ilerleme: çevrilen kısım + henüz çevrilmemiş özgün kuyruk
                // birlikte gösterilir — metin "büyüyüp küçülmez", dil kademeli değişir.
                if (onPartial != null && index < chunks.lastIndex) {
                    val remaining = chunks.subList(index + 1, chunks.size).joinToString("\n\n")
                    runCatching {
                        onPartial(unmasker(translatedChunks.joinToString("\n\n") + "\n\n" + remaining))
                    }
                }
            }

            GoogleTranslationResult(unmasker(translatedChunks.joinToString("\n\n")), allSucceeded)
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
