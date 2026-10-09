package com.kitsugi.animelist.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Çeviri ayarlarının süreç içi anlık görüntüsü. DataStore akışı her ayar
 * değişiminde [update] çağırır; böylece composable dışındaki (suspend olmayan)
 * çeviri intent'leri ayarlara senkron erişebilir.
 */
object KitsugiTranslatePrefs {
    @Volatile
    var sourceLanguage: String = "auto"
        private set

    @Volatile
    var targetLanguage: String = ""
        private set

    fun update(source: String?, target: String?) {
        sourceLanguage = source?.takeIf { it.isNotBlank() } ?: "auto"
        targetLanguage = target?.takeIf { it.isNotBlank() } ?: ""
    }

    /** Hedef dil: kullanıcı ayarı → yoksa uygulamanın aktif dili. */
    fun resolvedTarget(): String {
        targetLanguage.takeIf { it.isNotBlank() }?.let { return it }
        val tag = com.kitsugi.animelist.LocaleCache.localeTag
        if (tag != com.kitsugi.animelist.LocaleCache.UNSET && tag.isNotBlank()) {
            return tag.substringBefore('-')
        }
        return java.util.Locale.getDefault().language.ifBlank { "tr" }
    }
}

object KitsugiTranslateUtils {

    /**
     * Google Translate'in desteklediği TÜM dillerin kataloğu (kod → Türkçe ad).
     * Hem kaynak ("Otomatik" ayrıca eklenir) hem hedef dil seçiminde kullanılır;
     * böylece çeviri tek bir dile kilitlenmez, tüm diller desteklenir.
     */
    val googleLanguageCatalog: List<Pair<String, String>> = listOf(
        "af" to "Afrikaanca", "sq" to "Arnavutça", "am" to "Amharca", "ar" to "Arapça",
        "hy" to "Ermenice", "as" to "Assamca", "ay" to "Aymaraca", "az" to "Azerbaycanca",
        "bm" to "Bambaraca", "eu" to "Baskça", "be" to "Belarusça", "bn" to "Bengalce",
        "bho" to "Bhojpuri", "bs" to "Boşnakça", "bg" to "Bulgarca", "ca" to "Katalanca",
        "ceb" to "Cebuano", "cs" to "Çekçe", "zh" to "Çince (Basitleştirilmiş)",
        "zh-TW" to "Çince (Geleneksel)", "cv" to "Çuvaşça", "da" to "Danca",
        "dv" to "Divehice", "doi" to "Dogri", "nl" to "Felemenkçe", "en" to "İngilizce",
        "eo" to "Esperanto", "et" to "Estonca", "ee" to "Ewe", "fil" to "Filipince",
        "fi" to "Fince", "fr" to "Fransızca", "fy" to "Frizce", "gl" to "Galiçyaca",
        "ka" to "Gürcüce", "de" to "Almanca", "el" to "Yunanca", "gn" to "Guaranice",
        "gu" to "Guceratça", "ht" to "Haiti Creole", "ha" to "Hausaca", "haw" to "Hawaiice",
        "iw" to "İbranice", "hi" to "Hintçe", "hmn" to "Hmong", "hu" to "Macarca",
        "is" to "İzlandaca", "ig" to "Igbo", "ilo" to "Ilocano", "id" to "Endonezce",
        "ga" to "İrlandaca", "it" to "İtalyanca", "ja" to "Japonca", "jv" to "Cavaca",
        "kn" to "Kannada", "kk" to "Kazakça", "km" to "Khmer", "rw" to "Kinyarwanda",
        "ko" to "Korece", "kri" to "Krio", "ku" to "Kürtçe (Kurmanç)", "ckb" to "Kürtçe (Sorani)",
        "ky" to "Kırgızca", "lo" to "Laoca", "la" to "Latince", "lv" to "Letonca",
        "ln" to "Lingala", "lt" to "Litvanca", "lg" to "Luganda", "lb" to "Lüksemburgca",
        "mk" to "Makedonca", "mai" to "Maithili", "mg" to "Malgaşça", "ms" to "Malayca",
        "ml" to "Malayalam", "mt" to "Maltaca", "mi" to "Maori", "mr" to "Marathi",
        "mni-Mtei" to "Meiteilon (Manipuri)", "lus" to "Mizo", "mn" to "Moğolca",
        "my" to "Myanmar (Burmaca)", "ne" to "Nepalce", "no" to "Norveççe",
        "ny" to "Nyanja (Chichewa)", "or" to "Odiya (Oriya)", "om" to "Oromo",
        "ps" to "Peştuca", "fa" to "Farsça", "pl" to "Lehçe", "pt" to "Portekizce",
        "pa" to "Pencapça", "qu" to "Keçuvaça", "ro" to "Rumence", "ru" to "Rusça",
        "sm" to "Samoaca", "sa" to "Sanskritçe", "gd" to "İskoç Galcesi",
        "nso" to "Sepedi", "sr" to "Sırpça", "st" to "Sesotho", "sn" to "Shona",
        "sd" to "Sintçe", "si" to "Seylanca", "sk" to "Slovakça", "sl" to "Slovence",
        "so" to "Somalice", "es" to "İspanyolca", "su" to "Sundaca", "sw" to "Svahili",
        "sv" to "İsveççe", "tl" to "Tagalog", "tg" to "Tacikçe", "ta" to "Tamilce",
        "tt" to "Tatarca", "te" to "Telugu", "th" to "Tayca", "ti" to "Tigrinya",
        "ts" to "Tsongaca", "tr" to "Türkçe", "tk" to "Türkmence", "ak" to "Twi (Akan)",
        "uk" to "Ukraynaca", "ur" to "Urduca", "ug" to "Uygurca", "uz" to "Özbekçe",
        "vi" to "Vietnamca", "cy" to "Galce", "xh" to "Xhosa", "yi" to "Yidiş",
        "yo" to "Yoruba", "zu" to "Zulu"
    )

    /**
     * Tries to open the text in available translator apps.
     * If a preferredTranslator is specified (GOOGLE, DEEPL, TRANSLATE_YOU), attempts that app first.
     * If preferred fails or is DEFAULT, cycles through all apps in fallback order.
     * Shows a toast if no translator app is found.
     *
     * [sourceLanguage]: metnin yazıldığı dil (ör. incelemenin orijinal dili). Null ise
     * kullanıcı ayarı / otomatik algılama kullanılır — asla tek bir dile sabitlenmez.
     */
    fun Context.openTranslator(
        text: String,
        preferredTranslator: String = "DEFAULT",
        sourceLanguage: String? = null
    ) {
        val sourceLang = sourceLanguage?.takeIf { it.isNotBlank() }
            ?: KitsugiTranslatePrefs.sourceLanguage.ifBlank { "auto" }
        val targetLang = KitsugiTranslatePrefs.resolvedTarget()

        val handled = when (preferredTranslator.uppercase()) {
            "GOOGLE" -> openInGoogleTranslateMini(text) || openInGoogleTranslate(text, sourceLang, targetLang) ||
                openInGoogleTranslateWeb(text, sourceLang, targetLang)
            "DEEPL" -> openInDeepLMini(text) || openInDeepL(text)
            "TRANSLATE_YOU" -> openInTranslateYou(text)
            else -> false
        }

        if (!handled) {
            val fallbackSuccess = openInDeepLMini(text)
                || openInDeepL(text)
                || openInGoogleTranslateMini(text)
                || openInGoogleTranslate(text, sourceLang, targetLang)
                || openInGoogleTranslateWeb(text, sourceLang, targetLang)
                || openInTranslateYou(text)
            if (!fallbackSuccess) {
                Toast.makeText(this, "Çeviri uygulaması bulunamadı", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun Context.openInGoogleTranslate(text: String, sourceLang: String, targetLang: String): Boolean {
        return try {
            Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra("key_text_input", text)
                putExtra("key_text_output", "")
                // Kaynak dil SABİT DEĞİL: incelemenin kendi dili ya da otomatik algılama.
                putExtra("key_language_from", sourceLang.ifBlank { "auto" })
                putExtra("key_language_to", targetLang.ifBlank { "tr" })
                putExtra("key_suggest_translation", "")
                putExtra("key_from_floating_window", false)
                component = ComponentName(
                    "com.google.android.apps.translate",
                    "com.google.android.apps.translate.TranslateActivity"
                )
                startActivity(this)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Google Translate web fallback'u: uygulama kurulu olmasa bile tüm dilleri ve
     * otomatik kaynak algılamayı (sl=auto) destekler.
     */
    private fun Context.openInGoogleTranslateWeb(text: String, sourceLang: String, targetLang: String): Boolean {
        return try {
            val sl = sourceLang.ifBlank { "auto" }
            val tl = targetLang.ifBlank { "tr" }
            val encoded = Uri.encode(text.take(4500))
            val url = "https://translate.google.com/?sl=$sl&tl=$tl&text=$encoded&op=translate"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun Context.openInGoogleTranslateMini(text: String): Boolean {
        return try {
            Intent(Intent.ACTION_PROCESS_TEXT).apply {
                component = ComponentName(
                    "com.google.android.apps.translate",
                    "com.google.android.apps.translate.copydrop.gm3.TapToTranslateActivity"
                )
                type = "text/plain"
                putExtra(Intent.EXTRA_PROCESS_TEXT, text)
                startActivity(this)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun Context.openInDeepL(text: String): Boolean {
        return try {
            // DeepL doesn't support direct text injection, so we copy to clipboard first
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("synopsis", text))
            Intent(Intent.ACTION_VIEW).apply {
                component = ComponentName(
                    "com.deepl.mobiletranslator",
                    "com.deepl.mobiletranslator.MainActivity"
                )
                startActivity(this)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun Context.openInDeepLMini(text: String): Boolean {
        return try {
            Intent(Intent.ACTION_PROCESS_TEXT).apply {
                component = ComponentName(
                    "com.deepl.mobiletranslator",
                    "com.deepl.mobiletranslator.MiniTranslatorActivity"
                )
                type = "text/plain"
                putExtra(Intent.EXTRA_PROCESS_TEXT, text)
                startActivity(this)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun Context.openInTranslateYou(text: String): Boolean {
        return try {
            Intent(Intent.ACTION_PROCESS_TEXT).apply {
                component = ComponentName(
                    "com.bnyro.translate",
                    "com.bnyro.translate.ui.ShareActivity"
                )
                type = "text/plain"
                putExtra(Intent.EXTRA_PROCESS_TEXT, text)
                startActivity(this)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
