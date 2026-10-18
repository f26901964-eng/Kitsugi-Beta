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
        val lower = lang.trim().lowercase(Locale.ROOT)
        // v2.4.258: Eskiden "contains(\"tr\")" ve bilinmeyen her dil için "tr" dönüyordu; bu yüzden
        // Almanca / Arapça / Orijinal gibi altyazılar Türkçe etiketlenip Türkçe tercihine karışıyordu.
        return when {
            lower == "tr" || lower == "tur" || lower.startsWith("turk") ||
                lower.startsWith("türk") || lower.startsWith("tür") -> "tr"
            lower == "en" || lower == "eng" || lower.startsWith("engl") -> "en"
            // Türk sitelerindeki "Altyazı" / "Dublaj" etiketleri Türkçe kaynaktır
            lower.contains("altyaz") || lower.contains("dublaj") -> "tr"
            lower.isBlank() -> "und"
            else -> "und" // Bilinmeyen dil: Türkçe varsayılmaz; ad (name) yine de gösterilir
        }
    }
}
