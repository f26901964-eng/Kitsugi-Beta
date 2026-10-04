package com.kitsugi.animelist.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogo
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent

/**
 * Desteklenen 5 Profil Kaynak Platformu.
 */
enum class ProfilePlatform(
    val id: String,
    val label: String,
    val description: String
) {
    ANILIST(
        id = "anilist",
        label = "AniList",
        description = "Aktiviteler, takipçiler, anime/manga istatistikleri, favoriler ve detaylı grafikler"
    ),
    MAL(
        id = "mal",
        label = "MyAnimeList",
        description = "Klasik MAL profil görünümü, anime/manga durumları, puanlama ve kulüpler"
    ),
    SIMKL(
        id = "simkl",
        label = "Simkl",
        description = "TV dizileri, filmler, anime takibi, izleme geçmişi ve hesap detayları"
    ),
    KITSU(
        id = "kitsu",
        label = "Kitsu",
        description = "Kütüphane kayıtları, biyografi, takipçi istatistikleri ve favori karakterler"
    ),
    SHIKIMORI(
        id = "shikimori",
        label = "Shikimori",
        description = "Anime ve manga listeleri, bölüm sayıları, puan istatistikleri ve kullanıcı oranları"
    )
}

/**
 * Arama ve Keşfet Sayfalarındaki gibi Profil Kaynak Motoru Seçici Hap Buton.
 * Orijinal logoyu gösterir, emoji içermez.
 */
@Composable
fun ProfileSourceSelectorPill(
    selectedPlatform: ProfilePlatform,
    isConnected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = LocalKitsugiAccent.current

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(KitsugiColors.SurfaceElevated)
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.35f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        KitsugiPlatformLogo(
            platformId = selectedPlatform.id,
            size = 20.dp
        )
        Text(
            text = selectedPlatform.label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = KitsugiColors.TextPrimary
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (isConnected) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(KitsugiColors.AccentGreen)
            )
        }
        Icon(
            imageVector = Icons.Rounded.ArrowDropDown,
            contentDescription = "Profil Kaynağı Seç",
            tint = accentColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * 5 Platformlu Profil Kaynak Seçim Bottom Sheet Diyalogu.
 * Arama ve Keşfet sayfalarındaki seçim paneli tarzında:
 * AniList, MyAnimeList, Simkl, Kitsu ve Shikimori'yi
 * orijinal logoları, bağlantı durumları ve özellik açıklamalarıyla listeler.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSourcePickerSheet(
    selectedPlatformIndex: Int,
    isAniListConnected: Boolean,
    isMalConnected: Boolean,
    isSimklConnected: Boolean,
    isKitsuConnected: Boolean,
    isShikimoriConnected: Boolean,
    aniListUsername: String? = null,
    malUsername: String? = null,
    simklUsername: String? = null,
    kitsuUsername: String? = null,
    shikimoriUsername: String? = null,
    onSelectPlatform: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accentColor = LocalKitsugiAccent.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = KitsugiColors.Surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(KitsugiColors.TextMuted.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Profil Kaynağı Seç",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = KitsugiColors.TextPrimary
                )
            )
            Text(
                text = "Görüntülemek ve yönetmek istediğiniz profil kaynağını seçin",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = KitsugiColors.TextMuted
                ),
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            ProfilePlatform.entries.forEachIndexed { index, platform ->
                val isSelected = index == selectedPlatformIndex
                val isConnected = when (platform) {
                    ProfilePlatform.ANILIST -> isAniListConnected
                    ProfilePlatform.MAL -> isMalConnected
                    ProfilePlatform.SIMKL -> isSimklConnected
                    ProfilePlatform.KITSU -> isKitsuConnected
                    ProfilePlatform.SHIKIMORI -> isShikimoriConnected
                }
                val username = when (platform) {
                    ProfilePlatform.ANILIST -> aniListUsername
                    ProfilePlatform.MAL -> malUsername
                    ProfilePlatform.SIMKL -> simklUsername
                    ProfilePlatform.KITSU -> kitsuUsername
                    ProfilePlatform.SHIKIMORI -> shikimoriUsername
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isSelected) accentColor.copy(alpha = 0.12f)
                            else KitsugiColors.SurfaceElevated.copy(alpha = 0.5f)
                        )
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) accentColor else Color.Transparent,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable {
                            onSelectPlatform(index)
                            onDismiss()
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    KitsugiPlatformLogo(
                        platformId = platform.id,
                        size = 32.dp,
                        modifier = Modifier.padding(end = 14.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = platform.label,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) accentColor else KitsugiColors.TextPrimary
                                )
                            )
                            if (isConnected) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(KitsugiColors.AccentGreen.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(KitsugiColors.AccentGreen)
                                    )
                                    Text(
                                        text = if (!username.isNullOrBlank()) "@$username" else "Bağlı",
                                        color = KitsugiColors.AccentGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(KitsugiColors.SurfaceStrong)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Bağlı Değil",
                                        color = KitsugiColors.TextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = platform.description,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = KitsugiColors.TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        )
                    }

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(accentColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Seçili",
                                tint = KitsugiColors.Background,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
