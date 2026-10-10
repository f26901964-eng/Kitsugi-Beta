package com.kitsugi.animelist.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.ui.theme.KitsugiColors

/**
 * Paylaşımlı shimmer fırçası. DEĞERİ SABİTTİR (animasyon içermez): tüketiciler
 * yalnızca renk paletini buradan alır. Tarama (sweep) hareketi [LocalShimmerOffset]
 * üzerinden ÇİZİM kapsamında uygulanır.
 */
val LocalShimmerBrush: ProvidableCompositionLocal<Brush?> = compositionLocalOf { null }

/**
 * Paylaşımlı shimmer tarama konumu (0..1). BİLİNÇLİ OLARAK `State<Float>`:
 *
 * SORUN (eski davranış): fırça, animasyon değeri okunarak HER KAREDE yeniden
 * üretiliyor ve `CompositionLocalProvider` ile aşağı veriliyordu. Her karede
 * değişen bir CompositionLocal değeri, o değeri okuyan TÜM iskelet bileşenlerini
 * her karede yeniden kompoze ediyor; ayrıca her yeni `Brush` örneği kendi
 * `LinearGradient` shader'ını yeniden üretiyordu (ShaderBrush önbelleği instance
 * başına çalışır). Cihaz ölçümünde bu, saniyede ~500.000 nesne / saniyede ~15 MB
 * çöp üreten sürekli bir GC fırtınası ve %24 janky kare demekti.
 *
 * ÇÖZÜM: animasyon değeri `drawBehind` içinde okunur → yalnızca YENİDEN ÇİZİM
 * (recomposition yok, yeni fırça/shader yok). Bu değer kompozisyonda OKUNMAMALIDIR.
 */
val LocalShimmerOffset: ProvidableCompositionLocal<State<Float>?> = compositionLocalOf { null }

// ─── Merkezi Shimmer Paleti ────────────────────────────────────────────────
// KitsugiMobile HomeSkeletonLoading.kt'den uyarlama:
// 1200ms süren sağa doğru kayan lineer gradyan efekti.
// Palet dosya düzeyinde sabittir: `remember` anahtarının her kompozisyonda
// değişip yeni fırça üretmesini engeller.

private val SHIMMER_GRADIENT_END_PX = 1300f

/** Tarama bandının genişliği (px) — animasyon sırasında canvas bu kadar kaydırılır. */
private const val SHIMMER_SWEEP_PX = 300f

@Composable
private fun kitsugiShimmerColors(): List<Color> = listOf(
    KitsugiColors.Surface,
    KitsugiColors.SurfaceSoft.copy(alpha = 0.6f),
    KitsugiColors.SurfaceStrong.copy(alpha = 0.4f),
    KitsugiColors.SurfaceSoft.copy(alpha = 0.6f),
    KitsugiColors.Surface,
)

/**
 * Statik (animasyonsuz) shimmer fırçası. Tüm çağrılarda AYNI örnek döner —
 * `CompositionLocal` değeri ve `Modifier.background(brush)` girdisi kararlı kalır,
 * dolayısıyla tüketici bileşenler gereksiz yeniden kompoze olmaz.
 */
@Composable
fun rememberKitsugiShimmerBrush(): Brush {
    val colors = kitsugiShimmerColors()
    return remember(colors) {
        Brush.linearGradient(
            colors = colors,
            start = Offset(0f, 0f),
            end = Offset(SHIMMER_GRADIENT_END_PX, 0f),
        )
    }
}

/**
 * Tarama konumunu (0..1) üreten tek animasyon saati. Değer YALNIZCA çizim
 * kapsamında okunmalıdır; kompozisyonda okunursa her karede recomposition olur.
 * Birden fazla iskelet aynı ağaçtaysa [KitsugiShimmerProvider] tek örneği paylaştırır.
 */
@Composable
fun rememberKitsugiShimmerOffsetState(): State<Float> {
    val transition = rememberInfiniteTransition(label = "KitsugiShimmer")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerSweep",
    )
}

// ─── Temel Shimmer Blok ────────────────────────────────────────────────────
// Belirli boyutlarda animasyonlu placeholder blok.
//
// PERF: blok, animasyon değerini çizim kapsamında okur. Bu sayede her karede
// yalnızca yeniden çizim olur; kompozisyon ve modifier zinciri sabit kalır.
// (TV ana sayfasındaki `placeholderCardShimmer` ile aynı yaklaşım.)

@Composable
fun KitsugiShimmerBlock(
    brush: Brush,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
) {
    val provided = LocalShimmerOffset.current
    // Provider yoksa blok kendi saatini kurar (geriye dönük uyumluluk).
    val ownOffset = if (provided == null) rememberKitsugiShimmerOffsetState() else null
    val offset = provided ?: ownOffset
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                if (offset == null) {
                    drawRect(brush = brush)
                } else {
                    // `.value` yalnızca burada okunur → yalnızca yeniden çizim.
                    val sweep = offset.value
                    val shift = sweep * (size.width + SHIMMER_SWEEP_PX) - SHIMMER_SWEEP_PX
                    translate(left = shift) { drawRect(brush = brush) }
                }
            },
    )
}

// ─── Medya Kartı Shimmer ──────────────────────────────────────────────────
// KitsugiHorizontalMediaSection'daki SkeletonMediaCard'ın animasyonlu versiyonu.

@Composable
fun KitsugiShimmerMediaCard(
    brush: Brush = rememberKitsugiShimmerBrush(),
) {
    Column(
        modifier = Modifier
            .width(180.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(KitsugiColors.Surface)
            .padding(14.dp),
    ) {
        // Poster alanı
        KitsugiShimmerBlock(
            brush = brush,
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp),
            cornerRadius = 20.dp,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Başlık satırı
        KitsugiShimmerBlock(
            brush = brush,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(14.dp),
            cornerRadius = 999.dp,
        )

        Spacer(modifier = Modifier.height(7.dp))

        // Alt başlık satırı
        KitsugiShimmerBlock(
            brush = brush,
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .height(11.dp),
            cornerRadius = 999.dp,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Puan / meta satırı
        KitsugiShimmerBlock(
            brush = brush,
            modifier = Modifier
                .fillMaxWidth(0.40f)
                .height(10.dp),
            cornerRadius = 999.dp,
        )
    }
}

// ─── Yatay Shimmer Satırı ─────────────────────────────────────────────────────────────
// ExploreScreen'deki her KitsugiHorizontalMediaSection'ın yükleme durumu.

/**
 * Shimmer içeren birden fazla bileşen aynı kompozisyon ağacında yer alıyorsa
 * bu provider ile tek bir InfiniteTransition paylaşılır — belirgin performans gain'i.
 *
 * [active] false iken sonsuz shimmer animasyonu HİÇ OLUŞTURULMAZ: içerik
 * yüklendikten sonra placeholder kalmadığında bile transition'ın her karede
 * çalışmaya devam etmesi (sayfanın sürekli yeniden kompoze edilmesi ve boşuna
 * pil/CPU tüketimi) önlenir. Pasif durumda statik fırça sağlanır; shimmer
 * tüketicileri yine çizilebilir ama animasyon saatini ayakta tutmaz.
 */
@Composable
fun KitsugiShimmerProvider(
    active: Boolean = true,
    content: @Composable () -> Unit
) {
    // ÖNEMLİ 1: `if` yalnızca ANİMASYON SAATİNİ seçer; içerik (`content`) `if` dalının
    // DIŞINDA kalır. Eski sürümde `if/else` içinde iki ayrı CompositionLocalProvider vardı →
    // `active` her değiştiğinde (yükleme başladı/bitti, pull-to-refresh, arama yükleniyor) tüm
    // içerik ağacı söküp baştan kuruluyordu: hero pager durumu sıfırlanıyor, tüm raflar yeniden
    // kompoze oluyor ve kaydırma sırasında takılma/sıçrama üretiyordu.
    //
    // ÖNEMLİ 2: Aşağı verilen DEĞERLER artık kararlı (sabit fırça örneği + sabit State nesnesi).
    // Eskiden fırça, animasyon değeri okunarak her karede yeniden üretiliyordu; bu hem tüm
    // iskelet bileşenlerini her karede yeniden kompoze ediyor hem de her karede yeni bir
    // `LinearGradient` shader tahsisi yapıyordu (ölçülen GC fırtınasının kaynağı).
    // Tarama hareketi artık `KitsugiShimmerBlock` içinde `drawBehind` ile uygulanır.
    val brush = rememberKitsugiShimmerBrush()
    val offset: State<Float>? = if (active) rememberKitsugiShimmerOffsetState() else null
    CompositionLocalProvider(
        LocalShimmerBrush provides brush,
        LocalShimmerOffset provides offset,
        content = content
    )
}

@Composable
fun KitsugiShimmerMediaRow(
    cardCount: Int = 5,
) {
    // LocalShimmerBrush varsa paylaş, yoksa kendi transition'unu oluştur
    val brush = LocalShimmerBrush.current ?: rememberKitsugiShimmerBrush()
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(cardCount) {
            KitsugiShimmerMediaCard(brush = brush)
        }
    }
}

// ─── Hero Banner Shimmer ──────────────────────────────────────────────────
// ExploreScreen'de hero verileri yüklenirken gösterilir.

@Composable
fun KitsugiShimmerHeroSection(
    heroHeight: Dp = Dp.Unspecified,
    modifier: Modifier = Modifier,
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE || screenWidthDp > screenHeightDp

    val finalHeight = if (heroHeight != Dp.Unspecified) {
        heroHeight
    } else if (isLandscape) {
        (screenHeightDp * 0.60f).coerceIn(210f, 265f).dp
    } else {
        (screenHeightDp * 0.50f).coerceIn(390f, 460f).dp
    }

    // LocalShimmerBrush varsa paylaş, yoksa kendi transition'unu oluştur
    val brush = LocalShimmerBrush.current ?: rememberKitsugiShimmerBrush()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(finalHeight)
            .background(brush),
    ) {
        // Alt köşe içerik placeholder
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            KitsugiShimmerBlock(
                brush = brush,
                modifier = Modifier.width(60.dp).height(10.dp),
                cornerRadius = 999.dp,
            )
            Spacer(modifier = Modifier.height(10.dp))
            KitsugiShimmerBlock(
                brush = brush,
                modifier = Modifier.fillMaxWidth(0.78f).height(28.dp),
                cornerRadius = 8.dp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            KitsugiShimmerBlock(
                brush = brush,
                modifier = Modifier.fillMaxWidth(0.55f).height(16.dp),
                cornerRadius = 8.dp,
            )
            Spacer(modifier = Modifier.height(18.dp))
            // Buton placeholder
            KitsugiShimmerBlock(
                brush = brush,
                modifier = Modifier.width(120.dp).height(42.dp),
                cornerRadius = 999.dp,
            )
            Spacer(modifier = Modifier.height(16.dp))
            // Dot indicator placeholder
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) {
                    KitsugiShimmerBlock(
                        brush = brush,
                        modifier = Modifier.size(8.dp),
                        cornerRadius = 999.dp,
                    )
                }
            }
        }
    }
}

// ─── Detay Sayfası Başlık Shimmer ─────────────────────────────────────────
// ApiResultDetailPage / MediaEntryDetailPage yüklenirken gösterilir.

@Composable
fun KitsugiShimmerDetailHeader(
    modifier: Modifier = Modifier,
) {
    val brush = LocalShimmerBrush.current ?: rememberKitsugiShimmerBrush()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        // Cover image placeholder
        KitsugiShimmerBlock(
            brush = brush,
            modifier = Modifier
                .width(130.dp)
                .height(190.dp),
            cornerRadius = 18.dp,
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Başlık
        KitsugiShimmerBlock(
            brush = brush,
            modifier = Modifier.fillMaxWidth(0.85f).height(22.dp),
            cornerRadius = 8.dp,
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Alt başlık
        KitsugiShimmerBlock(
            brush = brush,
            modifier = Modifier.fillMaxWidth(0.60f).height(14.dp),
            cornerRadius = 999.dp,
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Meta satırı
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KitsugiShimmerBlock(
                brush = brush,
                modifier = Modifier.width(48.dp).height(12.dp),
                cornerRadius = 999.dp,
            )
            KitsugiShimmerBlock(
                brush = brush,
                modifier = Modifier.width(48.dp).height(12.dp),
                cornerRadius = 999.dp,
            )
            KitsugiShimmerBlock(
                brush = brush,
                modifier = Modifier.width(48.dp).height(12.dp),
                cornerRadius = 999.dp,
            )
        }
        Spacer(modifier = Modifier.height(20.dp))

        // Açıklama satırları
        repeat(4) { i ->
            KitsugiShimmerBlock(
                brush = brush,
                modifier = Modifier
                    .fillMaxWidth(if (i == 3) 0.55f else 1f)
                    .height(13.dp),
                cornerRadius = 999.dp,
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

// ─── Karakter / Staff Avatar Satırı Shimmer ───────────────────────────────
// Karakter ve oyuncu bölümleri için yükleme durumu.

@Composable
fun KitsugiShimmerAvatarRow(
    avatarCount: Int = 6,
) {
    val brush = LocalShimmerBrush.current ?: rememberKitsugiShimmerBrush()
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(avatarCount) {
            Column(
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                KitsugiShimmerBlock(
                    brush = brush,
                    modifier = Modifier.size(62.dp),
                    cornerRadius = 999.dp, // daire
                )
                Spacer(modifier = Modifier.height(6.dp))
                KitsugiShimmerBlock(
                    brush = brush,
                    modifier = Modifier.width(56.dp).height(10.dp),
                    cornerRadius = 999.dp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                KitsugiShimmerBlock(
                    brush = brush,
                    modifier = Modifier.width(44.dp).height(8.dp),
                    cornerRadius = 999.dp,
                )
            }
        }
    }
}

// ─── Arama Sonuçları Shimmer Listesi ───────────────────────────────────────
// SearchScreen'deki arama sonuçları yüklenirken dikey liste halinde gösterilir.
@Composable
fun KitsugiShimmerSearchResultList(
    itemCount: Int = 5,
) {
    val brush = LocalShimmerBrush.current ?: rememberKitsugiShimmerBrush()
    val isTv = com.kitsugi.animelist.ui.theme.LocalIsTv.current
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(itemCount) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(if (isTv) com.kitsugi.animelist.ui.theme.KitsugiTvTokens.Shapes.posterCard else RoundedCornerShape(16.dp))
                    .background(KitsugiColors.Surface)
                    .padding(if (isTv) 8.dp else 10.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val thumbWidth = if (isTv) com.kitsugi.animelist.ui.theme.KitsugiTvTokens.Cards.searchThumbWidth else 56.dp
                val thumbHeight = if (isTv) com.kitsugi.animelist.ui.theme.KitsugiTvTokens.Cards.searchThumbHeight else 80.dp
                KitsugiShimmerBlock(
                    brush = brush,
                    modifier = Modifier.size(width = thumbWidth, height = thumbHeight),
                    cornerRadius = if (isTv) 12.dp else 10.dp
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    KitsugiShimmerBlock(
                        brush = brush,
                        modifier = Modifier.fillMaxWidth(0.7f).height(14.dp),
                        cornerRadius = 999.dp
                    )
                    KitsugiShimmerBlock(
                        brush = brush,
                        modifier = Modifier.fillMaxWidth(0.4f).height(10.dp),
                        cornerRadius = 999.dp
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        KitsugiShimmerBlock(
                            brush = brush,
                            modifier = Modifier.width(36.dp).height(10.dp),
                            cornerRadius = 999.dp
                        )
                        KitsugiShimmerBlock(
                            brush = brush,
                            modifier = Modifier.width(36.dp).height(10.dp),
                            cornerRadius = 999.dp
                        )
                        KitsugiShimmerBlock(
                            brush = brush,
                            modifier = Modifier.width(44.dp).height(10.dp),
                            cornerRadius = 999.dp
                        )
                    }
                }
                KitsugiShimmerBlock(
                    brush = brush,
                    modifier = Modifier.size(40.dp),
                    cornerRadius = 12.dp
                )
            }
        }
    }
}

