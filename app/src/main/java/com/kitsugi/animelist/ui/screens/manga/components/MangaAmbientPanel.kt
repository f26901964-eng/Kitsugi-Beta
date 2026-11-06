package com.kitsugi.animelist.ui.screens.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.data.music.AmbientSound
import com.kitsugi.animelist.data.music.AtmospherePreset
import com.kitsugi.animelist.data.music.AtmospherePresets
import com.kitsugi.animelist.data.music.ChannelPlayState
import com.kitsugi.animelist.data.music.ChannelState
import com.kitsugi.animelist.data.music.ProceduralSoundCatalog
import com.kitsugi.animelist.data.music.RecommendationResult
import com.kitsugi.animelist.data.music.SoundFamily
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent

/**
 * 🎵 Okuyucu Müzik Paneli — Birleşik Sürüm
 *
 * **Tek kural:** Kaynağı ne olursa olsun (prosedürel, Jamendo, Openverse,
 * Archive, Freesound, Audius) her ses aynı kanal modeline girer ve aynı
 * butonlarla yönetilir:
 *
 *  - Ses seviyesi kaydırıcısı
 *  - Mute (sessize al / aç)
 *  - Kaldır
 *  - Ana ses seviyesi
 *  - Tümünü durdur
 *
 * Böylece "her kaynak her butona mantıklı tepki verir" ilkesi mimari
 * düzeyde garanti edilir; UI'da kaynak türüne göre dallanma YOKTUR.
 *
 * Dört sekme:
 *  - **Senin İçin** → manga türüne göre öneriler
 *  - **Ambiyans**   → doğa/ortam sesleri
 *  - **Müzik**      → enstrümantal sentez
 *  - **Karışım**    → aktif kanal miksajı
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaAmbientPanel(
    recommendations: RecommendationResult,
    isPlaying: Boolean,
    activeTitle: String?,
    activePresetId: String?,
    masterVolume: Float,
    channels: List<ChannelState>,
    maxChannels: Int,
    onMasterVolumeChanged: (Float) -> Unit,
    onPlayPreset: (AtmospherePreset) -> Unit,
    onPlaySound: (AmbientSound) -> Unit,
    onChannelVolumeChanged: (Int, Float) -> Unit,
    onChannelMuteToggle: (Int) -> Unit,
    onChannelRemove: (Int) -> Unit,
    onTogglePlay: () -> Unit,
    onStopAll: () -> Unit,
    onDismiss: () -> Unit,
    // ── Çevrimiçi katalog ──────────────────────────────────────────────
    onlineResults: List<AmbientSound> = emptyList(),
    isSearching: Boolean = false,
    searchError: String? = null,
    searchQuery: String = "",
    sourceStates: List<com.kitsugi.animelist.data.music.MusicSourceState> = emptyList(),
    onSearchQueryChanged: (String) -> Unit = {},
    onSearch: () -> Unit = {},
    onPlayOnline: (AmbientSound) -> Unit = {}
) {
    val accent = LocalKitsugiAccent.current
    var selectedTab by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // ── Başlık ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Ortam Sesi",
                    color = KitsugiColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when {
                        channels.isEmpty() -> "Okuduğun içeriğe uygun ses"
                        channels.size == 1 -> activeTitle ?: channels.first().displayLabel
                        else -> "${channels.size} ses çalıyor · $activeTitle"
                    },
                    color = KitsugiColors.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (channels.isNotEmpty()) {
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Duraklat" else "Çal",
                        tint = accent
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Kapat",
                    tint = KitsugiColors.TextSecondary
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Sekmeler ────────────────────────────────────────────────────
        val tabs = remember {
            listOf("Senin İçin", "Ambiyans", "Müzik", "Çevrimiçi", "Karışım")
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tabs.size) { index ->
                FilterChip(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    label = {
                        Text(
                            text = if (index == 4) "${tabs[index]} (${channels.size})"
                            else tabs[index],
                            fontSize = 12.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accent.copy(alpha = 0.22f),
                        selectedLabelColor = accent,
                        containerColor = KitsugiColors.SurfaceSoft,
                        labelColor = KitsugiColors.TextSecondary
                    )
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // ── İçerik ──────────────────────────────────────────────────────
        when (selectedTab) {
            0 -> ForYouTab(
                modifier = Modifier.weight(1f),
                recommendations = recommendations,
                channels = channels,
                activePresetId = activePresetId,
                accent = accent,
                onPlayPreset = onPlayPreset,
                onPlaySound = onPlaySound
            )
            1 -> SoundListTab(
                modifier = Modifier.weight(1f),
                familyFilter = { it != SoundFamily.INSTRUMENTAL },
                channels = channels,
                onPlaySound = onPlaySound
            )
            2 -> SoundListTab(
                modifier = Modifier.weight(1f),
                familyFilter = { it == SoundFamily.INSTRUMENTAL },
                channels = channels,
                onPlaySound = onPlaySound
            )
            3 -> OnlineTab(
                modifier = Modifier.weight(1f),
                results = onlineResults,
                isSearching = isSearching,
                error = searchError,
                query = searchQuery,
                sourceStates = sourceStates,
                playingIds = channels.map { it.sound.id }.toSet(),
                accent = accent,
                onQueryChanged = onSearchQueryChanged,
                onSearch = onSearch,
                onPlay = onPlayOnline
            )
            else -> MixerTab(
                modifier = Modifier.weight(1f),
                channels = channels,
                maxChannels = maxChannels,
                masterVolume = masterVolume,
                onMasterVolumeChanged = onMasterVolumeChanged,
                onChannelVolumeChanged = onChannelVolumeChanged,
                onChannelMuteToggle = onChannelMuteToggle,
                onChannelRemove = onChannelRemove,
                onStopAll = onStopAll,
                accent = accent
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
//  SEKME 1 — SENİN İÇİN
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun ForYouTab(
    modifier: Modifier = Modifier,
    recommendations: RecommendationResult,
    channels: List<ChannelState>,
    activePresetId: String?,
    accent: Color,
    onPlayPreset: (AtmospherePreset) -> Unit,
    onPlaySound: (AmbientSound) -> Unit
) {
    val playingIds = remember(channels) { channels.map { it.sound.id }.toSet() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        val genreLabel = recommendations.detectedGenres
            .takeIf { it.isNotEmpty() }
            ?.joinToString(", ")
            ?: "Nötr başlangıç"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.10f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$genreLabel türüne göre önerildi",
                color = KitsugiColors.TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(12.dp))

        if (recommendations.presets.isNotEmpty()) {
            Text(
                text = "ATMOSFER PRESETLERİ",
                color = KitsugiColors.TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(recommendations.presets, key = { it.sound.id }) { rec ->
                    val preset = AtmospherePresets.byId(rec.sound.id)
                    if (preset != null) {
                        AtmosphereCard(
                            preset = preset,
                            score = rec.score,
                            isActive = activePresetId == preset.id,
                            accent = accent,
                            onClick = { onPlayPreset(preset) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        if (recommendations.sounds.isNotEmpty()) {
            Text(
                text = "ÖNERİLEN SESLER",
                color = KitsugiColors.TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            recommendations.sounds.forEach { rec ->
                SoundRow(
                    sound = rec.sound,
                    reason = rec.reason,
                    isActive = rec.sound.id in playingIds,
                    accent = accent,
                    onClick = { onPlaySound(rec.sound) }
                )
                Spacer(Modifier.height(6.dp))
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Bir sese dokun → havuza eklenir. Karışım sekmesinden seviyeleri ayarlarsın.",
            color = KitsugiColors.TextMuted,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
//  SEKME 2/3 — SES LİSTESİ
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun SoundListTab(
    modifier: Modifier = Modifier,
    familyFilter: (SoundFamily) -> Boolean,
    channels: List<ChannelState>,
    onPlaySound: (AmbientSound) -> Unit
) {
    val accent = LocalKitsugiAccent.current
    val sounds = remember(familyFilter) {
        ProceduralSoundCatalog.sounds.filter { familyFilter(it.family) }
    }
    val playingIds = remember(channels) { channels.map { it.sound.id }.toSet() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        sounds.forEach { sound ->
            SoundRow(
                sound = sound,
                reason = sound.subtitle,
                isActive = sound.id in playingIds,
                accent = accent,
                onClick = { onPlaySound(sound) }
            )
            Spacer(Modifier.height(6.dp))
        }
        if (sounds.isEmpty()) {
            Text(
                text = "Bu kategoride ses yok",
                color = KitsugiColors.TextMuted,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Tümü cihazında üretilir — internet, hesap veya kota gerekmez.",
            color = KitsugiColors.TextMuted,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
//  SEKME 4 — KARIŞIM (BİRLEŞİK KANAL MİKSAJI)
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun MixerTab(
    modifier: Modifier = Modifier,
    channels: List<ChannelState>,
    maxChannels: Int,
    masterVolume: Float,
    onMasterVolumeChanged: (Float) -> Unit,
    onChannelVolumeChanged: (Int, Float) -> Unit,
    onChannelMuteToggle: (Int) -> Unit,
    onChannelRemove: (Int) -> Unit,
    onStopAll: () -> Unit,
    accent: Color
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 10.dp)
    ) {
        // ── Ana ses ─────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Ana Ses",
                color = KitsugiColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${(masterVolume * 100).toInt()}%",
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = masterVolume,
            onValueChange = onMasterVolumeChanged,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = KitsugiColors.Border
            )
        )

        Spacer(Modifier.height(8.dp))

        // ── Kanal sayacı ────────────────────────────────────────────────
        Text(
            text = "AÇIK KANALLAR (${channels.size}/$maxChannels)",
            color = KitsugiColors.TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))

        if (channels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(KitsugiColors.SurfaceSoft)
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Henüz ses eklenmedi\nDiğer sekmelerden bir sese dokun",
                    color = KitsugiColors.TextMuted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            channels.sortedBy { it.index }.forEach { channel ->
                ChannelRow(
                    channel = channel,
                    accent = accent,
                    onVolumeChanged = { onChannelVolumeChanged(channel.index, it) },
                    onMuteToggle = { onChannelMuteToggle(channel.index) },
                    onRemove = { onChannelRemove(channel.index) }
                )
                Spacer(Modifier.height(6.dp))
            }
        }

        Spacer(Modifier.height(14.dp))

        if (channels.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(KitsugiColors.AccentRed.copy(alpha = 0.12f))
                    .clickable(onClick = onStopAll)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tümünü Durdur",
                    color = KitsugiColors.AccentRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Tek bir kanal satırı — **kaynak türünden bağımsız**.
 *
 * Prosedürel ses de, uzak akış da aynı görsel/etkileşimle yönetilir.
 */
@Composable
private fun ChannelRow(
    channel: ChannelState,
    accent: Color,
    onVolumeChanged: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    onRemove: () -> Unit
) {
    val stateColor = when (channel.state) {
        ChannelPlayState.PLAYING -> Color(0xFF34D399)
        ChannelPlayState.BUFFERING -> Color(0xFFFBBF24)
        ChannelPlayState.ERROR -> KitsugiColors.AccentRed
        ChannelPlayState.PAUSED -> KitsugiColors.TextMuted
        ChannelPlayState.IDLE -> KitsugiColors.TextMuted
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (channel.muted) KitsugiColors.SurfaceSoft.copy(alpha = 0.5f)
                else KitsugiColors.SurfaceSoft
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Durum noktası
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(stateColor)
            )
            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.sound.title,
                    color = if (channel.muted) KitsugiColors.TextMuted
                    else KitsugiColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (channel.sound.subtitle.isNotBlank()) {
                    Text(
                        text = channel.sound.subtitle,
                        color = KitsugiColors.TextMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Mute
            IconButton(onClick = onMuteToggle, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (channel.muted) Icons.Rounded.VolumeOff
                    else Icons.Rounded.VolumeUp,
                    contentDescription = if (channel.muted) "Sesi aç" else "Sessize al",
                    tint = if (channel.muted) KitsugiColors.AccentRed else accent,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Kaldır
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Kaldır",
                    tint = KitsugiColors.TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Ses seviyesi
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = channel.volume,
                onValueChange = onVolumeChanged,
                modifier = Modifier.weight(1f),
                enabled = !channel.muted,
                colors = SliderDefaults.colors(
                    thumbColor = if (channel.muted) KitsugiColors.TextMuted else accent,
                    activeTrackColor = if (channel.muted) KitsugiColors.Border else accent,
                    inactiveTrackColor = KitsugiColors.Border
                )
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (channel.muted) "—" else "${(channel.volume * 100).toInt()}%",
                color = KitsugiColors.TextMuted,
                fontSize = 10.sp,
                modifier = Modifier.width(32.dp)
            )
        }

        // Hata mesajı
        channel.errorMessage?.let { msg ->
            Text(
                text = msg,
                color = KitsugiColors.AccentRed,
                fontSize = 9.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
//  ORTAK BİLEŞENLER
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun SoundRow(
    sound: AmbientSound,
    reason: String,
    isActive: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) accent.copy(alpha = 0.16f) else KitsugiColors.SurfaceSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = sound.title,
                color = if (isActive) accent else KitsugiColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (reason.isNotBlank()) {
                Text(
                    text = reason,
                    color = KitsugiColors.TextMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            imageVector = if (isActive) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isActive) "Durdur" else "Çal",
            tint = accent.copy(alpha = 0.75f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun AtmosphereCard(
    preset: AtmospherePreset,
    score: Float,
    isActive: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(112.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isActive) accent.copy(alpha = 0.22f) else KitsugiColors.SurfaceSoft
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = preset.emoji, fontSize = 26.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            text = preset.name,
            color = if (isActive) accent else KitsugiColors.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "%${(score * 100).toInt()} uyum",
            color = KitsugiColors.TextMuted,
            fontSize = 9.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
//  SEKME 5 — ÇEVRİMİÇİ KATALOG (Kaynak Bazlı)
// ─────────────────────────────────────────────────────────────────────────

/**
 * Çevrimiçi kaynaklardan gelen sesler.
 *
 * Listeleme **kaynak bazlı gruplanır** ve her kaynağın durumu görünür:
 *
 *  🟢 yeşil  → etkin + anahtar geçerli (veya anahtarsız)
 *  🟡 sarı   → etkin ama anahtar girilmemiş
 *  🔴 kırmızı→ anahtar var ama doğrulanamadı
 *  ⚪ gri    → kullanıcı kapattı
 *
 * Sonuç kartları kaynaktan bağımsızdır: hepsi aynı "ekle" davranışıyla
 * kanal havuzuna girer, dolayısıyla mute/volume/kaldır butonları çalışır.
 */
@Composable
private fun OnlineTab(
    modifier: Modifier = Modifier,
    results: List<AmbientSound>,
    isSearching: Boolean,
    error: String?,
    query: String,
    sourceStates: List<com.kitsugi.animelist.data.music.MusicSourceState>,
    playingIds: Set<String>,
    accent: Color,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onPlay: (AmbientSound) -> Unit
) {
    val grouped = remember(results) { results.groupBy { it.sourceId } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        // ── Arama ───────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChanged,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ses ara (yağmur, piyano, orman…)", fontSize = 12.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    unfocusedBorderColor = KitsugiColors.Border,
                    cursorColor = accent,
                    focusedTextColor = KitsugiColors.TextPrimary,
                    unfocusedTextColor = KitsugiColors.TextPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onSearch,
                enabled = !isSearching,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Ara",
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Kaynak durumları ────────────────────────────────────────────
        Text(
            text = "KAYNAKLAR",
            color = KitsugiColors.TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))

        sourceStates.filter { it.def.id != com.kitsugi.animelist.data.music.MusicSourceId.BUILTIN }
            .forEach { state ->
                SourceStatusRow(state = state)
                Spacer(Modifier.height(4.dp))
            }

        // ── Sonuçlar ────────────────────────────────────────────────────
        Spacer(Modifier.height(12.dp))

        when {
            isSearching -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = accent,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Kaynaklar taranıyor…", color = KitsugiColors.TextSecondary, fontSize = 12.sp)
                }
            }
            error != null && results.isEmpty() -> {
                Text(
                    text = error,
                    color = KitsugiColors.TextMuted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp)
                )
            }
            results.isEmpty() -> {
                Text(
                    text = "Yukarıdaki kutudan bir arama yap.\nAnahtarsız kaynaklar (Openverse, Archive) hemen çalışır.",
                    color = KitsugiColors.TextMuted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp)
                )
            }
            else -> {
                grouped.forEach { (sourceId, sounds) ->
                    val def = com.kitsugi.animelist.data.music.MusicSources.byId(sourceId)
                    Text(
                        text = (def?.displayName ?: sourceId.name).uppercase() + " · ${sounds.size}",
                        color = KitsugiColors.TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                    )
                    sounds.forEach { sound ->
                        OnlineSoundRow(
                            sound = sound,
                            isPlaying = sound.id in playingIds,
                            accent = accent,
                            onPlay = { onPlay(sound) }
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

/**
 * Kaynak durumu satırı — renk noktası + ad + kota/anahtar bilgisi.
 */
@Composable
private fun SourceStatusRow(state: com.kitsugi.animelist.data.music.MusicSourceState) {
    val dotColor = when (state.status) {
        com.kitsugi.animelist.data.music.SourceStatus.READY -> Color(0xFF34D399)
        com.kitsugi.animelist.data.music.SourceStatus.UNCHECKED -> Color(0xFF60A5FA)
        com.kitsugi.animelist.data.music.SourceStatus.NO_KEY -> Color(0xFFFBBF24)
        com.kitsugi.animelist.data.music.SourceStatus.INVALID_KEY -> KitsugiColors.AccentRed
        com.kitsugi.animelist.data.music.SourceStatus.DISABLED -> KitsugiColors.TextMuted
    }
    val label = when (state.status) {
        com.kitsugi.animelist.data.music.SourceStatus.READY ->
            if (state.def.keyless) "anahtarsız · hazır" else "hazır"
        com.kitsugi.animelist.data.music.SourceStatus.UNCHECKED -> "anahtar girildi · doğrulanmadı"
        com.kitsugi.animelist.data.music.SourceStatus.NO_KEY -> "anahtar girilmedi"
        com.kitsugi.animelist.data.music.SourceStatus.INVALID_KEY -> "anahtar geçersiz"
        com.kitsugi.animelist.data.music.SourceStatus.DISABLED -> "kapalı"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(KitsugiColors.SurfaceSoft.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = state.def.displayName,
            color = KitsugiColors.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "· $label",
            color = KitsugiColors.TextMuted,
            fontSize = 10.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (state.def.nonCommercialOnly) {
            Text(
                text = "NC",
                color = Color(0xFFFBBF24),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Çevrimiçi bir sesin satırı — atıf bilgisiyle birlikte.
 */
@Composable
private fun OnlineSoundRow(
    sound: AmbientSound,
    isPlaying: Boolean,
    accent: Color,
    onPlay: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isPlaying) accent.copy(alpha = 0.16f) else KitsugiColors.SurfaceSoft)
            .clickable(onClick = onPlay)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sound.title,
                    color = if (isPlaying) accent else KitsugiColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (sound.subtitle.isNotBlank()) {
                    Text(
                        text = sound.subtitle,
                        color = KitsugiColors.TextMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Durdur" else "Çal",
                tint = accent.copy(alpha = 0.75f),
                modifier = Modifier.size(20.dp)
            )
        }
        // Atıf — lisans gereği gösterilir
        sound.attributionLine()?.let { line ->
            Text(
                text = line,
                color = KitsugiColors.TextMuted.copy(alpha = 0.7f),
                fontSize = 8.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}
