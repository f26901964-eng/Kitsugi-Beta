package com.kitsugi.animelist.ui.screens.detail.wiki

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.remote.GalleryItem

/**
 * Kişi (karakter / ekip / seslendirme sanatçısı) "Wiki" sekmesinin yuvası.
 *
 * Sekme sayfa açılır açılmaz vardır; çözümleme sürerken iskelet, sayfa gelince içerik,
 * bulunamazsa boş durum + "Tekrar dene" gösterilir. Karakter ve ekip sayfaları aynı
 * [FandomCharacterWikiViewModel]'i kullanır; yalnızca boş durum metni değişir.
 */
@Composable
internal fun CharacterWikiTabSlot(
    wikiState: FandomCharacterWikiViewModel.UiState,
    sections: Map<Int, FandomCharacterWikiViewModel.SectionState>,
    translations: Map<String, String>,
    translatingKeys: Set<String>,
    spoilerGuard: Boolean,
    accentColor: Color,
    onLoadSection: (Int) -> Unit,
    onTranslate: (String) -> Unit,
    onShowOriginal: (String) -> Unit,
    onOpenGallery: (List<GalleryItem>, Int) -> Unit,
    onConfirm: () -> Unit,
    onReportWrong: () -> Unit,
    onRetry: () -> Unit,
    onTranslateInfobox: () -> Unit = {},
    unavailableMessageRes: Int = R.string.fandom_unavailable_character
) {
    when (wikiState) {
        is FandomCharacterWikiViewModel.UiState.Ready -> CharacterWikiTabContent(
            page = wikiState.page,
            sections = sections,
            translations = translations,
            translatingKeys = translatingKeys,
            spoilerGuard = spoilerGuard,
            accentColor = accentColor,
            onLoadSection = onLoadSection,
            onTranslate = onTranslate,
            onShowOriginal = onShowOriginal,
            onOpenGallery = onOpenGallery,
            onConfirm = onConfirm,
            onReportWrong = onReportWrong,
            onTranslateInfobox = onTranslateInfobox
        )
        FandomCharacterWikiViewModel.UiState.Unavailable -> WikiUnavailableCard(
            accentColor = accentColor,
            messageRes = unavailableMessageRes,
            onRetry = onRetry
        )
        else -> WikiTabSkeleton(accentColor = accentColor)
    }
}

/**
 * ViewModel'e doğrudan bağlı kısa yol: durumları kendisi toplar, onay/ret geri bildirimlerini
 * Toast ile verir. Ekip sayfası gibi tek noktadan kullanılan yerler için.
 */
@Composable
internal fun CharacterWikiTabSlot(
    viewModel: FandomCharacterWikiViewModel,
    accentColor: Color,
    onOpenGallery: (List<GalleryItem>, Int) -> Unit,
    modifier: Modifier = Modifier,
    unavailableMessageRes: Int = R.string.fandom_unavailable_character
) {
    val context = LocalContext.current
    val wikiState by viewModel.state.collectAsState()
    val sections by viewModel.sections.collectAsState()
    val translations by viewModel.translations.collectAsState()
    val translating by viewModel.translating.collectAsState()
    val prefs by viewModel.prefs.collectAsState()
    androidx.compose.foundation.layout.Box(modifier = modifier) {
        CharacterWikiTabSlot(
            wikiState = wikiState,
            sections = sections,
            translations = translations,
            translatingKeys = translating,
            spoilerGuard = prefs.spoilerGuard,
            accentColor = accentColor,
            onLoadSection = viewModel::loadSection,
            onTranslate = viewModel::translate,
            onShowOriginal = viewModel::showOriginal,
            onOpenGallery = onOpenGallery,
            onConfirm = {
                viewModel.confirmMatch()
                Toast.makeText(context, context.getString(R.string.fandom_confirm_done), Toast.LENGTH_SHORT).show()
            },
            onReportWrong = {
                viewModel.reportWrongMatch()
                Toast.makeText(context, context.getString(R.string.fandom_report_wrong_done), Toast.LENGTH_SHORT).show()
            },
            onRetry = viewModel::retry,
            onTranslateInfobox = viewModel::translateInfobox,
            unavailableMessageRes = unavailableMessageRes
        )
    }
}
