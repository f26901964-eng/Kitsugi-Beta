package com.kitsugi.animelist.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * Ortak taksonomi katmanı — Shikimori (Rusça) + Bangumi (Çince/Japonca) + İngilizce
 * kaynakların **tek kanonik anahtarda** birleşmesi ve ekranda Rusça/Çince görünmemesi.
 *
 * Sözleşme kaynağı: `diag/taxonomy_master/` + `scripts/check_taxonomy_master.py`.
 */
class TaxonomyLabelsTest {

    private val originalLocale = Locale.getDefault()

    @Before
    fun setTurkishLocale() {
        Locale.setDefault(Locale("tr", "TR"))
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    // ── Rusça (Shikimori) türleri ────────────────────────────────────────────

    @Test
    fun `shikimori turleri turkce etikete cevrilir`() {
        assertEquals("Aksiyon", "Экшен".toLocalizedTaxonomyLabel())
        assertEquals("Günlük Yaşam", "Повседневность".toLocalizedTaxonomyLabel())
        assertEquals("Bilim Kurgu", "Фантастика".toLocalizedTaxonomyLabel())
        assertEquals("Sihirli Kız", "Махо-сёдзё".toLocalizedTaxonomyLabel())
        assertEquals("Shōnen", "Сёнен".toLocalizedTaxonomyLabel())
    }

    @Test
    fun `shikimori turleri ingilizce arayuzde ingilizce kalir`() {
        Locale.setDefault(Locale.ENGLISH)
        assertEquals("Action", "Экшен".toLocalizedTaxonomyLabel())
        assertEquals("Slice of Life", "Повседневность".toLocalizedTaxonomyLabel())
        assertEquals("Action", "Aksiyon".toLocalizedTaxonomyLabel())
    }

    @Test
    fun `bilinmeyen kiril etiketi latin harflerine cevrilir`() {
        val label = "НеизвестныйТег".toLocalizedTaxonomyLabel()
        assertFalse("Ekranda Kiril kalmamalı: $label", label.hasCyrillicCharacters())
        assertTrue(label.isNotBlank())
    }

    @Test
    fun `yogusma farklari tolere edilir`() {
        // `ё` ↔ `е`, tırnak ve büyük/küçük harf farkı aynı girişe gitmeli.
        assertEquals("Shōjo", "Сёдзё".toLocalizedTaxonomyLabel())
        assertEquals("Shōjo", "Седзе".toLocalizedTaxonomyLabel())
        assertEquals("Shōjo", "«СЁДЗЁ»".toLocalizedTaxonomyLabel())
    }

    // ── Diller arası kanonik anahtar ─────────────────────────────────────────

    @Test
    fun `ayni anlama gelen farkli diller tek anahtara iner`() {
        val key = "Экшен".taxonomyCanonicalKey()
        assertEquals("action", key)
        assertEquals(key, "Action".taxonomyCanonicalKey())
        assertEquals(key, "Aksiyon".taxonomyCanonicalKey())
        assertEquals(key, "动作".taxonomyCanonicalKey())
        assertEquals(key, "アクション".taxonomyCanonicalKey())
    }

    @Test
    fun `shikimori gunluk yasam ingiliz ve cince yazimlarla ayni anahtar`() {
        val key = "Повседневность".taxonomyCanonicalKey()
        assertEquals("slice_of_life", key)
        assertEquals(key, "Slice of Life".taxonomyCanonicalKey())
        assertEquals(key, "日常".taxonomyCanonicalKey())
    }

    // ── Sözlük girişi ────────────────────────────────────────────────────────

    @Test
    fun `sozluk girisi turkce ve ingilizce karsiligi tasir`() {
        val entry = "Экшен".taxonomyEntryOrNull()
        assertNotNull(entry)
        assertEquals("action", entry!!.key)
        assertEquals("Aksiyon", entry.turkish)
        assertEquals("Action", entry.english)
        assertEquals("russian", entry.origin)
    }

    @Test
    fun `bangumi girisi bangumi kaynagindan gelir`() {
        val entry = "催泪".taxonomyEntryOrNull()
        assertNotNull(entry)
        assertEquals("bangumi", entry!!.origin)
    }

    @Test
    fun `bilinmeyen ingilizce etiket sozlukte yok`() {
        assertNull("uydurma giriş üretilmemeli", "Zzz Not A Tag".taxonomyEntryOrNull())
    }

    // ── Gösterimden dışlama ─────────────────────────────────────────────────

    @Test
    fun `cozulemeyen cince etiket gosterimden cikarilir`() {
        assertTrue(isUnresolvedForeignLabel("非常识的な独自タグzzz"))
        assertFalse(isUnresolvedForeignLabel("Повседневность"))
        assertFalse(isUnresolvedForeignLabel("Action"))
    }

    @Test
    fun `etiket listesi rusca ve cince sizintisi icermez`() {
        val labels = listOf(
            "Экшен",
            "Повседневность",
            "Action",
            "Aksiyon",
            "非常识的な独自タグzzz"
        ).localizedDistinctTags()

        // `Экшен` ≡ `Action` ≡ `Aksiyon` tek çip
        assertEquals(2, labels.size)
        assertEquals("Экшен", labels[0])
        assertEquals("Повседневность", labels[1])

        val displayed = labels.map { it.toLocalizedTaxonomyLabel() }
        assertTrue(displayed.none { it.hasCyrillicCharacters() })
        assertTrue(displayed.none { it.hasCjkCharacters() })
    }

    // ── Kurum adları ────────────────────────────────────────────────────────

    @Test
    fun `rusca kurum adi latin harflerine cevrilir`() {
        val name = "Студия Гибли".toDisplayOrgName()
        assertFalse("Kurum adında Kiril kalmamalı: $name", name.hasCyrillicCharacters())
        assertTrue(name.isNotBlank())
    }

    @Test
    fun `bilinen kurum adi sozlukten gelir`() {
        assertEquals("Kyoto Animation", "京都アニメーション".toDisplayOrgName())
    }

    // ── Resmî kapsam ────────────────────────────────────────────────────────

    @Test
    fun `shikimori resmi turlerinin tamami sozlukte`() {
        val official = listOf(
            "Экшен", "Приключения", "Гонки", "Комедия", "Авангард", "Мифология", "Тайна",
            "Драма", "Этти", "Фэнтези", "Стратегические игры", "Хентай", "Исторический",
            "Ужасы", "Детское", "Магия", "Боевые искусства", "Меха", "Музыка", "Пародия",
            "Самураи", "Романтика", "Школа", "Фантастика", "Сёдзё", "Сёдзё-ай", "Сёнен",
            "Сёнен-ай", "Космос", "Спорт", "Супер сила", "Вампиры", "Яой", "Юри", "Гарем",
            "Повседневность", "Сверхъестественное", "Военное", "Детектив", "Психологическое",
            "Сэйнэн", "Дзёсей", "Смена пола", "Командный спорт", "Видеоигры",
            "Взрослые персонажи", "Жестокость", "Реинкарнация", "Любовный многоугольник",
            "Изобразительное искусство", "Путешествие во времени", "Гэг-юмор",
            "Удостоено наград", "Триллер", "Спортивные единоборства", "CGDCT", "Махо-сёдзё",
            "Реверс-гарем", "Исэкай", "Хулиганы", "Забота о детях", "Магическая смена пола",
            "Шоу-бизнес", "Культура отаку", "Организованная преступность", "Работа",
            "Иясикэй", "Выживание", "Исполнительское искусство", "Антропоморфизм",
            "Кроссдрессинг", "Идолы (Жен.)", "Игра с высокими ставками", "Медицина",
            "Питомцы", "Образовательное", "Идолы (Муж.)", "Романтический подтекст", "Гурман"
        )
        val missing = official.filter { RussianTaxonomyDictionary.entryFor(it) == null }
        assertTrue("Sözlükte eksik resmî tür: $missing", missing.isEmpty())
    }

    @Test
    fun `mal kimligiyle de cozum yapilir`() {
        assertEquals("action", RussianTaxonomyDictionary.entryForMalGenreId(1)?.key)
        assertEquals("slice_of_life", RussianTaxonomyDictionary.entryForMalGenreId(36)?.key)
        assertNull(RussianTaxonomyDictionary.entryForMalGenreId(0))
    }

    // ── Türkçe sızıntı kontrolü ─────────────────────────────────────────────

    @Test
    fun `turkce degerlerde kiril bulunmaz`() {
        val leaking = RussianTaxonomyDictionary.allEntries()
            .filter { it.turkish.hasCyrillicCharacters() || it.english.hasCyrillicCharacters() }
        assertTrue("Kiril sızıntısı: $leaking", leaking.isEmpty())
    }
}
