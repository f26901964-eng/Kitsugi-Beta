package com.kitsugi.animelist.ui.screens.detail

import com.kitsugi.animelist.ui.components.KitsugiGalleryIconButton
import com.kitsugi.animelist.ui.components.HERO_BANNER_FIT_TOLERANCE
import com.kitsugi.animelist.ui.components.HeroArtworkFit
import com.kitsugi.animelist.ui.components.heroArtworkFit
import com.kitsugi.animelist.ui.components.heroImageAlignment
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.data.remote.GalleryCategory
import com.kitsugi.animelist.data.remote.GalleryItem
import com.kitsugi.animelist.data.remote.KitsugiHeroArtworkResolver
import com.kitsugi.animelist.model.MediaType
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.platform.LocalConfiguration

import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.ui.graphics.vector.ImageVector
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.ui.components.KitsugiNsfwImage
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.utils.copyOnDoubleTap
import com.kitsugi.animelist.utils.toFriendlySourceLabel
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KitsugiDetailHero(
    title: String,
    subtitle: String,
    imageUrl: String?,
    logoUrl: String?,
    source: String,
    typeLabel: String, // "ANIME" or "MANGA"
    year: String?,
    isAdult: Boolean,
    onBackClick: () -> Unit,
    onPosterClick: (() -> Unit)? = null,
    statusLabel: String? = null,
    statusColor: Color? = null,
    scoreLabel: String? = null,
    isFavorite: Boolean = false,
    alreadyInList: Boolean = false,
    blurAdultMedia: Boolean = false,
    onShareClick: (() -> Unit)? = null,
    onGalleryClick: (() -> Unit)? = null,
    totalEpisodes: Int? = null,
    nextAiring: String? = null,
    showFavoriteButton: Boolean = false,
    onToggleFavoriteClick: (() -> Unit)? = null,
    /** Yatay arka plan (TMDB/Fanart/kaynak backdrop). Ekran yönü/boyutuna göre poster ile yarışır. */
    backdropUrl: String? = null,
    mediaType: MediaType = MediaType.Anime,
    /** Görsel çözümleyici için kaynak öğe (TMDB + Fanart.tv en kaliteli poster/arka plan). */
    artworkItem: JikanSearchResult? = null,
    /** Galeri görselleri (kaynak + TMDB/Fanart). Dikey/yatay adaylar olarak vitrine eklenir. */
    galleryItems: List<GalleryItem> = emptyList()
) {
    val accentColor = LocalKitsugiAccent.current
    val fallbackPlaceholderColor = statusColor ?: accentColor
    val context = LocalContext.current

    // ── Görsel seçimi (ekran yönü fark etmez) ─────────────────────────────────
    // KURAL: Detay sayfası vitrini her iki yönde de AYNI tasarımdır →
    // en kaliteli **YATAY** görsel (TMDB / Fanart.tv / galeri arka planları) 16:9
    // sinematik bantta **KIRPILMADAN** gösterilir; başlık ve meta veriler bandın altına akar.
    // Dikey poster yalnızca hiçbir yatay görsel yoksa (ör. bazı manga kayıtları) bandın
    // içinde **kesilmeden** gösterilir; vitrin asla tam boy poster paneline dönüşmez.
    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp.toFloat()

    val artworkKey = artworkItem?.let { KitsugiHeroArtworkResolver.cacheKey(it) }
    val artwork by produceState<KitsugiHeroArtworkResolver.HeroArtwork?>(
        initialValue = artworkItem?.let { KitsugiHeroArtworkResolver.getCached(it) },
        key1 = artworkKey
    ) {
        val item = artworkItem ?: return@produceState
        value = runCatching { KitsugiHeroArtworkResolver.resolveArtwork(item) }.getOrNull() ?: value
    }

    // Galeri adaylarını yön ve çözünürlüğe göre sırala (en yüksek çözünürlük önce).
    val galleryVerticalUrls = remember(galleryItems) {
        galleryItems.filter { isVerticalGalleryItem(it) }.sortedByDescending { galleryPixels(it) }.map { it.url }
    }
    val galleryHorizontalUrls = remember(galleryItems) {
        galleryItems.filter { isHorizontalGalleryItem(it) }.sortedByDescending { galleryPixels(it) }.map { it.url }
    }

    // Kaynağın kendi URL'lerini kalite/hizalama kurallarından geçir:
    // poster → tam çözünürlük, dikey kapakların "yatay arka plan" sanılması engellenir.
    val nativePosterUrl = remember(imageUrl) { KitsugiHeroArtworkResolver.upgradeNativePosterUrl(imageUrl) }
    val nativeBackdropUrl = remember(imageUrl, backdropUrl) {
        KitsugiHeroArtworkResolver.sanitizeNativeBackdropUrl(backdropUrl, nativePosterUrl)
    }

    // Yatay adaylar KALİTE SIRASINDA: çözümleyicinin TMDB/Fanart.tv sıralaması
    // (4K > 2K > 1080p, yazısız, oylu) → kaynağın kendi arka planı → galeri yatay görselleri.
    val wideArtworkCandidates = remember(nativeBackdropUrl, artwork, galleryHorizontalUrls) {
        buildList {
            artwork?.backdropCandidates?.let { addAll(it) }
            if (!nativeBackdropUrl.isNullOrBlank()) add(nativeBackdropUrl)
            addAll(galleryHorizontalUrls)
        }.distinct()
    }
    // Dikey adaylar yalnızca YEDEK: hiç yatay görsel yoksa bandın içinde kesilmeden gösterilir.
    val tallArtworkCandidates = remember(nativePosterUrl, artwork, galleryVerticalUrls) {
        buildList {
            artwork?.posterCandidates?.let { addAll(it) }
            if (!nativePosterUrl.isNullOrBlank()) add(nativePosterUrl)
            addAll(galleryVerticalUrls)
        }.distinct()
    }
    val imagePlan = remember(wideArtworkCandidates, tallArtworkCandidates) {
        (wideArtworkCandidates + tallArtworkCandidates).distinct()
    }

    var imageIndex by remember(imagePlan) { mutableIntStateOf(0) }
    val heroImageModel = imagePlan.getOrNull(imageIndex)

    val chosenIsPoster = remember(heroImageModel, wideArtworkCandidates, tallArtworkCandidates) {
        when {
            heroImageModel == null -> false
            heroImageModel in wideArtworkCandidates -> false
            heroImageModel in tallArtworkCandidates -> true
            else -> KitsugiHeroArtworkResolver.isLikelyVerticalUrl(
                url = heroImageModel,
                posterUrl = imageUrl,
                backdropUrl = backdropUrl
            )
        }
    }
    var loadedImageAspect by remember(heroImageModel) { mutableStateOf<Float?>(null) }
    val isVerticalArtwork = loadedImageAspect?.let { it < 1.05f } ?: chosenIsPoster

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 16:9 SİNEMATİK BANT: görsel alanı panel genişliğinden türetilir (telefon, tablet,
        // TV — dikey/yatay hepsi aynı oranı görür) ve ekran yüksekliğinin %62'sini geçmez.
        val bannerHeight = (maxWidth * DETAIL_HERO_BANNER_RATIO)
            .coerceAtMost((screenHeightDp * 0.62f).dp)
        val heroBoxHeight = bannerHeight + DETAIL_HERO_INFO_AREA_HEIGHT

        // 2. ADIM: asıl vitrin alanı (bant + alt bilgi alanı)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroBoxHeight)
        ) {
            val heroBoxWidthDp = maxWidth.value
            val imageAreaModifier = Modifier
                .fillMaxWidth()
                .height(bannerHeight)
                .align(Alignment.TopCenter)
            val imageAreaAspect = if (bannerHeight.value > 0f) heroBoxWidthDp / bannerHeight.value else 1f
            // Yatay görsel banttan belirgin farklı orandaysa KIRPMA yerine
            // "tam görünür + yumuşak dolgu" kullanılır (sıkı eşik: en fazla ~%20 kırpma).
            val useDualLayerContain = heroArtworkFit(
                boxAspect = imageAreaAspect,
                imageAspect = loadedImageAspect,
                tolerance = HERO_BANNER_FIT_TOLERANCE
            ) == HeroArtworkFit.CONTAIN_WITH_FILL
            val heroAlignment = heroImageAlignment(
                chosenIsPoster = isVerticalArtwork,
                heroAspect = imageAreaAspect,
                imageAspect = loadedImageAspect
            )

            if (!heroImageModel.isNullOrBlank()) {
                val imageModifier = if (onPosterClick != null) {
                    imageAreaModifier.tvClickable { onPosterClick() }
                } else {
                    imageAreaModifier
                }
                val onFailed: () -> Unit = {
                    if (imageIndex < imagePlan.lastIndex) imageIndex += 1
                }
                val onAspect: (Float) -> Unit = { ratio -> loadedImageAspect = ratio }
                if (useDualLayerContain) {
                    KitsugiNsfwImage(
                        model = heroImageModel,
                        contentDescription = null,
                        isAdult = isAdult,
                        blurAdultMedia = blurAdultMedia,
                        modifier = imageAreaModifier.alpha(0.30f),
                        contentScale = ContentScale.Crop,
                        alignment = BiasAlignment(0f, -0.20f)
                    )
                    KitsugiNsfwImage(
                        model = heroImageModel,
                        contentDescription = title,
                        isAdult = isAdult,
                        blurAdultMedia = blurAdultMedia,
                        modifier = imageModifier,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.Center,
                        onLoadingFailed = onFailed,
                        onImageAspectResolved = onAspect
                    )
                } else {
                    KitsugiNsfwImage(
                        model = heroImageModel,
                        contentDescription = title,
                        isAdult = isAdult,
                        blurAdultMedia = blurAdultMedia,
                        modifier = imageModifier,
                        contentScale = ContentScale.Crop,
                        alignment = heroAlignment,
                        onLoadingFailed = onFailed,
                        onImageAspectResolved = onAspect
                    )
                }
            } else {
                Box(
                    modifier = imageAreaModifier
                        .background(fallbackPlaceholderColor.copy(alpha = 0.20f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title.take(2).uppercase(),
                        color = fallbackPlaceholderColor,
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                KitsugiColors.Background.copy(alpha = 0.05f),
                                KitsugiColors.Background.copy(alpha = 0.30f),
                                KitsugiColors.Background.copy(alpha = 0.72f),
                                KitsugiColors.Background
                            )
                        )
                    )
            )

            // ── Top Action Bar: Back (left) + Action buttons (right) ──
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(KitsugiColors.Background.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.geri),
                            tint = KitsugiColors.TextPrimary
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Galeri butonu (resimler yüklendiğinde görünür)
                    if (onGalleryClick != null) {
                        val accentColorLocal = LocalKitsugiAccent.current
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(accentColorLocal.copy(alpha = 0.22f))
                                .border(
                                    width = 1.dp,
                                    color = accentColorLocal.copy(alpha = 0.45f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            KitsugiGalleryIconButton(onClick = onGalleryClick, accentColor = accentColorLocal)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(KitsugiColors.Background.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = {
                            if (onShareClick != null) {
                                onShareClick()
                            } else {
                                val mediaType = if (typeLabel.contains("MANGA", ignoreCase = true)) com.kitsugi.animelist.model.MediaType.Manga else com.kitsugi.animelist.model.MediaType.Anime
                                val url = com.kitsugi.animelist.utils.ShareUtils.buildMediaUrl(source, 0, mediaType)
                                com.kitsugi.animelist.utils.ShareUtils.shareText(context, title, url)
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = stringResource(R.string.paylas),
                                tint = KitsugiColors.TextPrimary
                            )
                        }
                    }

                    if (showFavoriteButton && onToggleFavoriteClick != null) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(KitsugiColors.Background.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(onClick = onToggleFavoriteClick) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = if (isFavorite) stringResource(R.string.action_favourite_remove) else "Favori Yap",
                                    tint = if (isFavorite) accentColor else KitsugiColors.TextPrimary
                                )
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp)
            ) {
                val context = LocalContext.current
                val copyTitleGesture = Modifier.copyOnDoubleTap(context, title)

                if (!logoUrl.isNullOrBlank()) {
                    var logoFailed by remember(logoUrl) { mutableStateOf(false) }
                    if (!logoFailed) {
                        AsyncImage(
                            model = logoUrl,
                            contentDescription = title,
                            modifier = Modifier
                                .height(75.dp)
                                .fillMaxWidth(0.85f)
                                .then(copyTitleGesture),
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.CenterStart,
                            onError = { logoFailed = true }
                        )
                    } else {
                        Text(
                            text = title,
                            modifier = copyTitleGesture,
                            color = KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = title,
                        modifier = copyTitleGesture,
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Format
                    val isMangaType = typeLabel.contains("MANGA", ignoreCase = true)
                    val formatIcon = if (isMangaType) Icons.Rounded.Book else Icons.Rounded.Tv
                    val formatText = when {
                        typeLabel.contains("FİLM", ignoreCase = true) || typeLabel.contains("MOVIE", ignoreCase = true) -> stringResource(R.string.media_type_movie)
                        typeLabel.contains("DİZİ", ignoreCase = true) || typeLabel.contains("TVSHOW", ignoreCase = true) -> stringResource(R.string.media_type_tv)
                        isMangaType -> stringResource(R.string.media_type_manga)
                        else -> "TV"
                    }
                    MetadataIconText(icon = formatIcon, text = formatText, tint = accentColor)

                    // 2. Episodes / Chapters count — tek parça yapımlarda (film) bölüm sayacı
                    // gösterilmez; "1 Bölüm" satırı kaynak API'sinin dolgu değeriyle üretiliyordu.
                    if (formatText != "Film") {
                        val countIcon = if (isMangaType) Icons.Rounded.MenuBook else Icons.Rounded.Schedule
                        val countText = if (totalEpisodes != null && totalEpisodes > 0) {
                            if (isMangaType) stringResource(R.string.hero_count_volumes_chapters, totalEpisodes)
                            else stringResource(R.string.hero_count_episodes, totalEpisodes)
                        } else {
                            if (isMangaType) stringResource(R.string.hero_count_no_volumes)
                            else stringResource(R.string.hero_count_no_episodes)
                        }
                        MetadataIconText(icon = countIcon, text = countText, tint = accentColor)
                    }

                    // 3. Status
                    if (statusLabel != null) {
                        val statusIcon = Icons.Rounded.RssFeed
                        MetadataIconText(icon = statusIcon, text = statusLabel, tint = statusColor ?: accentColor)
                    }
                }

                val airingText = rememberAiringCountdownText(nextAiring)
                if (airingText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = stringResource(R.string.cd_airing_soon),
                            tint = KitsugiColors.AccentOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.hero_airing_soon, airingText),
                            color = KitsugiColors.AccentOrange,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailSourcePill(
                        source = source,
                        color = if (source.equals("tmdb", ignoreCase = true)) Color(0xFFFFB800) else accentColor
                    )

                    if (year != null) {
                        HeroDetailPill(
                            text = year,
                            color = KitsugiColors.TextSecondary
                        )
                    }

                    if (scoreLabel != null) {
                        HeroDetailPill(
                            text = scoreLabel,
                            color = accentColor
                        )
                    }

                    if (isFavorite) {
                        HeroDetailPill(
                            text = "★ Favori",
                            color = accentColor
                        )
                    }

                    if (alreadyInList) {
                        HeroDetailPill(
                            text = "Listede",
                            color = KitsugiColors.AccentGreen
                        )
                    }

                    if (isAdult) {
                        HeroDetailPill(
                            text = "+18",
                            color = KitsugiColors.AccentRed
                        )
                    }
                }


            }
        }
    }
}

@Composable
private fun HeroDetailPill(
    text: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MetadataIconText(
    icon: ImageVector,
    text: String,
    tint: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Vitrinin 16:9 sinematik bant (görsel alanı) yüksekliğinin genişliğe oranı.
 * Görsel alanı = panel genişliği * 9/16 → yatay kapak görselleri kırpılmadan görünür.
 */

/**
 * Yatay modda kullanılan sinematik bant oranı (9:16 → 16:9 bandın yükseklik çarpanı).
 * Bant tam 16:9 olduğu için TMDB/Fanart yatay kapakları **hiç kırpılmadan** görünür.
 */
internal const val DETAIL_HERO_BANNER_RATIO = 9f / 16f

/**
 * Yatay modda 16:9 bandın altında başlık/logo, meta satırları ve rozetler için ayrılan alan.
 */
internal val DETAIL_HERO_INFO_AREA_HEIGHT = 220.dp

/** Dikey vitrin adayı: poster kategorisi veya belirgin dikey oran. */
internal fun isVerticalGalleryItem(item: GalleryItem): Boolean {
    if (item.category == GalleryCategory.POSTER) return true
    if (item.category in galleryNonArtCategories) return false
    val w = item.width ?: return false
    val h = item.height ?: return false
    return h > w * 1.2f
}

/** Yatay vitrin adayı: arka plan/banner kategorisi veya belirgin yatay oran. */
internal fun isHorizontalGalleryItem(item: GalleryItem): Boolean {
    if (item.category == GalleryCategory.BACKDROP || item.category == GalleryCategory.BANNER) return true
    if (item.category in galleryNonArtCategories) return false
    val w = item.width ?: return false
    val h = item.height ?: return false
    return w > h * 1.3f
}

private val galleryNonArtCategories = setOf(
    GalleryCategory.LOGO,
    GalleryCategory.CLEARART,
    GalleryCategory.CHARACTER,
    GalleryCategory.PERSON,
    GalleryCategory.THUMBNAIL,
    GalleryCategory.SQUARE
)

internal fun galleryPixels(item: GalleryItem): Long =
    (item.width ?: 0).toLong() * (item.height ?: 0).toLong()
