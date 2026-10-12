package com.kitsugi.animelist.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kimliksiz (isimle) karakter araması için "ad yeterince ayırt edici mi?" kuralı.
 *
 * Kurgusal karakter adlarının büyük bölümü TEK kelimedir ve Latincedir ("Nobita",
 * "Doraemon", "Goku"). Katı kural bu adları tümüyle reddettiği için TMDB/Simkl kaynaklı
 * anime detay sayfalarında karakter hiçbir kaynaktan aranamıyor, hem kartta hem karakter
 * detay sayfasında görsel hiç gelmiyordu. İkinci imza (doğrulanabilir aramalar) bu adları
 * açar; varsayılan davranış DEĞİŞMEZ.
 */
class KitsugiPersonImageAggregatorTest {

    @Test
    fun acceptsMultiTokenNames() {
        assertTrue(KitsugiPersonImageAggregator.isUnambiguousName("Takeshi Gouda"))
        assertTrue(KitsugiPersonImageAggregator.isUnambiguousName("Eris Boreas Greyrat"))
        // İkinci imza çok kelimeli adları her durumda kabul eder.
        assertTrue(KitsugiPersonImageAggregator.isUnambiguousName("Takeshi Gouda", allowSingleTokenLatin = false))
    }

    @Test
    fun defaultRuleRejectsSingleTokenLatinNames() {
        // Eski (katı) davranış korunur: doğrulama yoksa tek kelimeli Latin ad reddedilir.
        assertFalse(KitsugiPersonImageAggregator.isUnambiguousName("Nobita"))
        assertFalse(KitsugiPersonImageAggregator.isUnambiguousName("Doraemon"))
        assertFalse(KitsugiPersonImageAggregator.isUnambiguousName("Suzu"))
    }

    @Test
    fun verifiedSearchesAcceptSingleTokenLatinNames() {
        // Çağıran sonucu (yapım kimliği ya da birebir ad eşleşmesiyle) doğruluyorsa kabul.
        assertTrue(KitsugiPersonImageAggregator.isUnambiguousName("Nobita", allowSingleTokenLatin = true))
        assertTrue(KitsugiPersonImageAggregator.isUnambiguousName("Doraemon", allowSingleTokenLatin = true))
        assertTrue(KitsugiPersonImageAggregator.isUnambiguousName("Goku", allowSingleTokenLatin = true))
        // Çok kısa adlar hâlâ reddedilir ("Lu", "Bo" gibi kırpıntılar eşleşme üretmesin).
        assertFalse(KitsugiPersonImageAggregator.isUnambiguousName("Lu", allowSingleTokenLatin = true))
        assertFalse(KitsugiPersonImageAggregator.isUnambiguousName("ab", allowSingleTokenLatin = true))
    }

    @Test
    fun acceptsCjkSingleTokenNames() {
        assertTrue(KitsugiPersonImageAggregator.isUnambiguousName("森田鈴"))
        assertTrue(KitsugiPersonImageAggregator.isUnambiguousName("森田鈴", allowSingleTokenLatin = false))
        // İki karakterli CJK ad yeterince ayırt edici değil.
        assertFalse(KitsugiPersonImageAggregator.isUnambiguousName("鈴木"))
    }
}
