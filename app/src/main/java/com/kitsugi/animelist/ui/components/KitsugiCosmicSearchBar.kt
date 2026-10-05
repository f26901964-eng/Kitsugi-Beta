package com.kitsugi.animelist.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent

/**
 * Uiverse (Lakshay-art) tasarımından esinlenilmiş, Android dokunmatik ekranlara
 * tam uyarlanmış Cosmic / Cyber Arama Çubuğu (KitsugiCosmicSearchBar).
 *
 * Görsel ve Etkileşim Katmanları:
 * 1. .grid: Arama arkasında hafif siber uzay matris ızgarası.
 * 2. .glow: Sürekli dönen elektrik mavisi (#402FB5) ve neon macenta (#CF30AA) dış aura ışıması.
 * 3. .border & .white: Çift katmanlı dönen konik degrade ışık huzmeleri.
 * 4. #pink-mask: Sol köşede yumuşak neon odak spot ışığı.
 * 5. .input: Derin obsidian siyah (#010201) zemin, zarif tipografi ve rahat dokunmatik alan.
 * 6. #filter-icon: Bağımsız dönen konik çerçeveli (#3D3A4F) şık filtre butonu.
 *
 * Dokunmatik Özellikler:
 * - Fare hover gerektirmez; ambiyans ışık demetleri yumuşakça döner.
 * - Dokunma/Basma anında 0.985f yaylı (spring) basış geri bildirimi.
 * - Klavyeye odaklanıldığında (Focus) ışıma ve neon parlaklık otomatik artar.
 */
@Composable
fun KitsugiCosmicSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClearQuery: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Tüm platformlarda ara...",
    leadingContent: (@Composable () -> Unit)? = null,
    onFilterClick: (() -> Unit)? = null,
    isFilterActive: Boolean = false,
    height: Dp = 56.dp
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Dokunmatik basış yaylı ölçeklenmesi
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "CosmicSearchScale"
    )

    // Odaklanma ve ışıma yoğunluğu
    val glowIntensity by animateFloatAsState(
        targetValue = if (isFocused) 0.75f else 0.35f,
        animationSpec = tween(500),
        label = "CosmicGlowIntensity"
    )

    // Dönen konik degrade açısı (Sürekli ambiyans)
    val infiniteTransition = rememberInfiniteTransition(label = "CosmicRotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "CosmicAngle"
    )

    // Uiverse Lakshay-art renk paleti
    val neonIndigo = Color(0xFF402FB5)
    val neonMagenta = Color(0xFFCF30AA)
    val softLavender = Color(0xFFA099D8)
    val softPink = Color(0xFFDFA2DA)
    val darkPlum = Color(0xFF6E1B60)
    val deepIndigo = Color(0xFF18116A)
    val baseDark = Color(0xFF1C191C)
    val obsidianBlack = Color(0xFF010201)
    val placeholderColor = Color(0xFFC0B9C0)

    val shape = RoundedCornerShape(14.dp)
    val innerShape = RoundedCornerShape(12.5.dp)

    Box(
        modifier = modifier
            .scale(scale)
            .height(height)
            .onFocusChanged { isFocused = it.hasFocus },
        contentAlignment = Alignment.Center
    ) {
        // ── 1. Katman: .glow Dış Radyan Aura Işıması ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .graphicsLayer {
                    alpha = glowIntensity
                }
                .blur(18.dp)
                .clip(shape)
                .background(
                    Brush.sweepGradient(
                        listOf(
                            Color.Transparent,
                            neonIndigo.copy(alpha = 0.8f),
                            Color.Transparent,
                            Color.Transparent,
                            neonMagenta.copy(alpha = 0.8f),
                            Color.Transparent
                        )
                    )
                )
        )

        // ── 2. Katman: .border & .white Dönen Konik Degrade Çerçeve ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape),
            contentAlignment = Alignment.Center
        ) {
            // Dönen devasa konik fırça (Clipped to rounded rect)
            Box(
                modifier = Modifier
                    .size(650.dp)
                    .graphicsLayer { rotationZ = rotationAngle }
                    .background(
                        Brush.sweepGradient(
                            listOf(
                                baseDark,
                                neonIndigo,
                                softLavender,
                                deepIndigo,
                                baseDark,
                                baseDark,
                                softPink,
                                neonMagenta,
                                darkPlum,
                                baseDark,
                                baseDark
                            )
                        )
                    )
            )

            // ── 3. Katman: .input İç Obsidian Siyah Gövde ──
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(1.5.dp)
                    .clip(innerShape)
                    .background(obsidianBlack),
                contentAlignment = Alignment.CenterStart
            ) {
                // ── #pink-mask: Sol Üst Köşe Neon Spot Işığı ──
                Canvas(
                    modifier = Modifier
                        .size(width = 80.dp, height = 40.dp)
                        .align(Alignment.TopStart)
                ) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                neonMagenta.copy(alpha = if (isFocused) 0.65f else 0.35f),
                                Color.Transparent
                            ),
                            center = Offset(15f, 10f),
                            radius = 90f
                        )
                    )
                }

                // ── Arama Çubuğu İçerik Satırı ──
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Sol Alan: Varsa Kaynak Motor Seçici (Pill) veya Siber Arama İkonu
                    if (leadingContent != null) {
                        leadingContent()
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Ara",
                            tint = if (isFocused) neonMagenta else placeholderColor,
                            modifier = Modifier
                                .size(22.dp)
                                .padding(start = 2.dp)
                        )
                    }

                    // Orta Alan: Metin Girişi
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        singleLine = true,
                        cursorBrush = SolidColor(neonMagenta),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Normal,
                            fontSize = 15.sp
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                onSearch()
                                keyboardController?.hide()
                            }
                        ),
                        decorationBox = { innerTextField ->
                            if (query.isEmpty()) {
                                Text(
                                    text = placeholder,
                                    color = placeholderColor,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 14.sp,
                                    maxLines = 1
                                )
                            }
                            innerTextField()
                        }
                    )

                    // Temizle Butonu ("X")
                    AnimatedVisibility(
                        visible = query.isNotEmpty(),
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        IconButton(
                            onClick = {
                                onClearQuery()
                                keyboardController?.hide()
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Temizle",
                                tint = placeholderColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // ── 4. Katman: #filter-icon Bağımsız Dönen Filtre Butonu ──
                    if (onFilterClick != null) {
                        CosmicFilterIconButton(
                            onClick = onFilterClick,
                            isActive = isFilterActive
                        )
                    }
                }
            }
        }
    }
}

/**
 * Uiverse (#filter-icon ve .filterBorder) referanslı, kendi ekseninde dönen konik
 * çerçeveye sahip özel siber filtre butonu.
 */
@Composable
fun CosmicFilterIconButton(
    onClick: () -> Unit,
    isActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val filterInfiniteTransition = rememberInfiniteTransition(label = "FilterBorderRotation")
    val filterAngle by filterInfiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "FilterAngle"
    )

    val filterShape = RoundedCornerShape(10.dp)
    val filterInnerShape = RoundedCornerShape(9.dp)

    Box(
        modifier = modifier
            .size(38.dp)
            .clip(filterShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = Color(0xFFCF30AA)),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // .filterBorder: Dönen konik çerçeve ışığı (#3d3a4f)
        Box(
            modifier = Modifier
                .size(120.dp)
                .graphicsLayer { rotationZ = filterAngle }
                .background(
                    Brush.sweepGradient(
                        listOf(
                            Color.Transparent,
                            if (isActive) Color(0xFFCF30AA) else Color(0xFF3D3A4F),
                            Color.Transparent,
                            Color.Transparent,
                            if (isActive) Color(0xFF402FB5) else Color(0xFF3D3A4F),
                            Color.Transparent
                        )
                    )
                )
        )

        // #filter-icon iç arka plan: linear-gradient(180deg, #161329, black, #1d1b4b)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(1.dp)
                .clip(filterInnerShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF161329),
                            Color.Black,
                            Color(0xFF1D1B4B)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = "Filtreler",
                tint = if (isActive) Color(0xFFCF30AA) else Color(0xFFC0B9C0),
                modifier = Modifier.size(18.dp)
            )

            // Aktif filtre varsa küçük neon nokta
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 4.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFCF30AA))
                )
            }
        }
    }
}

/**
 * Uiverse (Lakshay-art) eşlikçi siber buton: Eklenti Portalı ve araçlar için
 * aynı dönen konik çerçeve estetiğini paylaşan 56dp yüksekliğinde kare/oval buton.
 */
@Composable
fun CosmicCompanionButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    isActive: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "CompanionRotation")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "CompanionAngle"
    )

    val shape = RoundedCornerShape(14.dp)
    val innerShape = RoundedCornerShape(12.5.dp)

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = Color(0xFF402FB5)),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Dönen çerçeve
        Box(
            modifier = Modifier
                .size(160.dp)
                .graphicsLayer { rotationZ = angle }
                .background(
                    Brush.sweepGradient(
                        listOf(
                            Color(0xFF1C191C),
                            Color(0xFF402FB5),
                            Color.Transparent,
                            Color(0xFFCF30AA),
                            Color(0xFF1C191C)
                        )
                    )
                )
        )

        // İç dolgu
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(1.5.dp)
                .clip(innerShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF161329),
                            Color(0xFF010201),
                            Color(0xFF1D1B4B)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
    }
}

/**
 * Uiverse .grid: Arama ekranı başlığının arkasında siber matris ızgarası.
 */
@Composable
fun CyberMatrixGridCanvas(
    modifier: Modifier = Modifier,
    lineSpacing: Dp = 16.dp,
    lineColor: Color = Color(0xFF181628).copy(alpha = 0.45f)
) {
    Canvas(modifier = modifier) {
        val spacingPx = lineSpacing.toPx()
        val width = size.width
        val height = size.height

        var x = 0f
        while (x <= width) {
            drawLine(
                color = lineColor,
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 1f
            )
            x += spacingPx
        }

        var y = 0f
        while (y <= height) {
            drawLine(
                color = lineColor,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
            y += spacingPx
        }
    }
}
