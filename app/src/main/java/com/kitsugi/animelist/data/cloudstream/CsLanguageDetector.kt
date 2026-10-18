package com.kitsugi.animelist.data.cloudstream

import java.util.Locale

/**
 * Altyazı dil kodu tespiti.
 * [CsStreamRunner] içindeki `detectLanguageCode` mantığı buraya taşındı.
 */
internal object CsLanguageDetector {

    /**
     * Verilen dil adı string'inden BCP-47 uyumlu dil kodu döndürür.
     *
     * Örnek: "Turkish" → "tr", "English" → "en", "Türkçe" → "tr"
     *
     * @param lang Altyazı dosyasından gelen dil adı (herhangi bir formatta olabilir)
     * @return BCP-47 dil kodu ("tr", "en" vb.)
     */
    fun detectLanguageCode(lang: String): String {
        // "İ" harfi lowercase sonrası "i" + birleşik nokta (U+0307) olur; "İngilizce" gibi adların
        // eşleşmesi için birleşik noktayı kaldırıyoruz.
        val lower = lang.trim().lowercase(Locale.ROOT).replace("\u0307", "")
        // v2.4.258: Eskiden "contains(\"tr\")" ve bilinmeyen her dil için "tr" dönüyordu; bu yüzden
        // Almanca / Arapça / Orijinal gibi altyazılar Türkçe etiketlenip Türkçe tercihine karışıyordu.
        return when {
            lower == "tr" || lower == "tur" || lower.startsWith("turk") ||
                lower.startsWith("türk") || lower.startsWith("tür") -> "tr"
            lower == "en" || lower == "eng" || lower.startsWith("engl") ||
                lower.startsWith("ingiliz") -> "en"
            // Türk sitelerinde (ör. Anizium) altyazı adları Türkçe yazılır: "İngilizce", "Almanca" vb.
            lower.startsWith("almanca") || lower.startsWith("german") -> "de"
            lower.startsWith("fransız") || lower.startsWith("fransiz") || lower.startsWith("french") -> "fr"
            lower.startsWith("italyan") || lower.startsWith("italian") -> "it"
            lower.startsWith("ispanyol") || lower.startsWith("spanish") -> "es"
            lower.startsWith("rus") || lower.startsWith("russian") -> "ru"
            lower.startsWith("arap") || lower.startsWith("arabic") -> "ar"
            lower.startsWith("japon") || lower.startsWith("japanese") -> "ja"
            lower.startsWith("kore") || lower.startsWith("korean") -> "ko"
            lower.startsWith("çin") || lower.startsWith("cin") || lower.startsWith("chinese") -> "zh"
            // Türk sitelerindeki "Altyazı" / "Dublaj" etiketleri Türkçe kaynaktır
            lower.contains("altyaz") || lower.contains("dublaj") -> "tr"
            lower.isBlank() -> "und"
            else -> "und" // Bilinmeyen dil: Türkçe varsayılmaz; ad (name) yine de gösterilir
        }
    }
}
