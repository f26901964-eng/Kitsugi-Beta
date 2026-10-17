package com.kitsugi.animelist.ui.app

import android.content.Context
import android.content.SharedPreferences
import com.kitsugi.animelist.ui.screens.mylist.MY_LIST_ANILIST_TAB_INDEX
import com.kitsugi.animelist.ui.screens.mylist.MY_LIST_BANGUMI_TAB_INDEX
import com.kitsugi.animelist.ui.screens.mylist.MY_LIST_TAB_COUNT
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Regresyon testi: Listem kütüphane kaynağı seçimi ("Kütüphane Kaynağı Seç")
 * uygulama kapatılıp açıldığında AYNEN geri yüklenmelidir.
 *
 * İki kök neden düzeltilmişti:
 *  1) loadFilters içindeki migrateLegacyMyListTabIndex koşulsuz her açılışta
 *     uygulanıyor ve kayıtlı tab_index değerini bir sıra kaydırıyordu
 *     (Bangumi 6 → 0/Tümü, Tümü 0 → 1/AniList, …).
 *  2) MyList pager'ının settledPage etkisi ilk karede kurulum sayfasını (0)
 *     geri yazarak kayıtlı seçimi siliyordu (açılışta her zaman "Tümü").
 *
 * Bu test, kayıtlı seçimin taşıma yapılmadan ve geri yazılmadan geldiğini sabitler.
 */
class MyListTabRestoreTest {

    private fun buildMocks(): Pair<Context, SharedPreferences> {
        val prefs = mock<SharedPreferences>()
        val context = mock<Context>()
        // ExternalAuthManager.prefs() context.applicationContext üzerinden okuma yapar;
        // mock'ta null NPE olurdu. Aynı mock'a yönlendirerek iki yolu da karşılarız.
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.getSharedPreferences(any(), anyInt())).thenReturn(prefs)
        return context to prefs
    }

    @Test
    fun `stored library source selection is restored as-is without shifting`() {
        val (context, prefs) = buildMocks()
        // Kayıtlı seçim: Bangumi sekmesi (index 6). Eski göç hatasında bu değer
        // koşulsuz migrate edilip 0'a (Tümü) düşüyordu.
        whenever(prefs.getInt(eq("tab_index"), anyInt())).thenReturn(MY_LIST_BANGUMI_TAB_INDEX)

        val viewModel = AppViewModel()
        viewModel.loadFilters(context)

        assertEquals(MY_LIST_BANGUMI_TAB_INDEX, viewModel.myListTabIndex)
        // Geri yükleme kayıtlı değeri değiştirmediyse prefs üzerine YAZMAMALI;
        // aksi halde her açılışta değer bir kez daha taşınıyor demektir.
        verify(prefs, never()).edit()
    }

    @Test
    fun `stored selection is restored for every valid tab index`() {
        val expectedByIndex = mapOf(
            0 to 0, // Tümü
            MY_LIST_ANILIST_TAB_INDEX to MY_LIST_ANILIST_TAB_INDEX,
            2 to 2, // MAL
            3 to 3, // Simkl
            4 to 4, // Kitsu
            5 to 5, // Shikimori
            MY_LIST_BANGUMI_TAB_INDEX to MY_LIST_BANGUMI_TAB_INDEX,
        )
        for ((stored, expected) in expectedByIndex) {
            val (context, prefs) = buildMocks()
            whenever(prefs.getInt(eq("tab_index"), anyInt())).thenReturn(stored)

            val viewModel = AppViewModel()
            viewModel.loadFilters(context)

            assertEquals(
                "tab_index=$stored geri yüklenirken taşındı",
                expected,
                viewModel.myListTabIndex
            )
            verify(prefs, never()).edit()
        }
    }

    @Test
    fun `out of range stored index is clamped into current tab range`() {
        val (context, prefs) = buildMocks()
        whenever(prefs.getInt(eq("tab_index"), anyInt())).thenReturn(99)
        val editor = mock<SharedPreferences.Editor>()
        whenever(prefs.edit()).thenReturn(editor)
        whenever(editor.putInt(any(), anyInt())).thenReturn(editor)

        val viewModel = AppViewModel()
        viewModel.loadFilters(context)

        assertEquals(MY_LIST_TAB_COUNT - 1, viewModel.myListTabIndex)
    }

    @Test
    fun `fresh install without connected accounts defaults to anilist tab`() {
        val (context, prefs) = buildMocks()
        whenever(prefs.getInt(eq("tab_index"), anyInt())).thenReturn(-1)
        // Token anahtarları null → hiçbir hesap bağlı değil.
        val editor = mock<SharedPreferences.Editor>()
        whenever(prefs.edit()).thenReturn(editor)
        whenever(editor.putInt(any(), anyInt())).thenReturn(editor)

        val viewModel = AppViewModel()
        viewModel.loadFilters(context)

        assertEquals(MY_LIST_ANILIST_TAB_INDEX, viewModel.myListTabIndex)
        // Yeni kurulum varsayılanı kalıcı hale getirilir.
        verify(editor).putInt(eq("tab_index"), eq(MY_LIST_ANILIST_TAB_INDEX))
    }
}
