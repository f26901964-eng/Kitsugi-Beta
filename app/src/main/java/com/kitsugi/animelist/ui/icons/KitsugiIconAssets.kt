package com.kitsugi.animelist.ui.icons

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R

/**
 * Kitsugi 3B ikon seti (ayarlar satırları, sekmeler ve gezinme çubuğu).
 *
 * Neden var?
 * ----------
 * Ekranlarda tek renkli Material vektör ikonları ("◯", "✓", "•" gibi görünen ince
 * çizgiler) hem küçük boyutlarda okunmuyor hem de "bu satır ne yapıyor?" sorusunu
 * yanıtlamıyordu. Bu set, her satırın **işlevini doğrudan anlatan** renkli, 3B
 * render edilmiş, saydam arka planlı (arka plansız) görseller kullanır.
 *
 * Kaynak / lisans
 * ---------------
 * Görseller Microsoft **Fluent Emoji (3D)** setinden alınmıştır:
 * https://github.com/microsoft/fluentui-emoji — MIT License
 * (Copyright (c) Microsoft Corporation). Ayrıntı: docs/IKON_SETI.md
 *
 * Teknik notlar
 * -------------
 * - Varlıklar `res/drawable-nodpi/ic_kitsugi_*.png` (192×192, saydam) olarak durur;
 *   hepsi aynı optik kutuya normalize edilmiştir (tool/iconpack/normalize.js).
 * - [fromVector] Material ikon adından otomatik eşleme yapar; böylece 150+ çağrı
 *   yerini tek tek değiştirmek gerekmez. Yanlış eşleşme riski olan yerlerde
 *   `art = KitsugiArt.X` ile açıkça belirtilir.
 */
enum class KitsugiArt(
    @DrawableRes val resId: Int,
    /** Otomatik eşlemede kullanılan Material ikon adları (ör. "PlayCircle"). */
    internal val vectorNames: List<String> = emptyList()
) {
    // ── Hesap ────────────────────────────────────────────────────────────
    Account(R.drawable.ic_kitsugi_account, listOf("AccountCircle", "AccountBox", "ManageAccounts", "Badge")),
    Connections(R.drawable.ic_kitsugi_connections, listOf("Link", "LinkOff", "Share", "LinkRounded")),

    // ── Görünüm / oynatıcı ───────────────────────────────────────────────
    Palette(
        R.drawable.ic_kitsugi_palette,
        listOf("Palette", "ColorLens", "Brush", "Gradient", "FormatPaint", "Style", "InvertColors")
    ),
    Player(R.drawable.ic_kitsugi_player, listOf("PlayCircle", "PlayCircleFilled", "Movie", "Theaters", "Videocam")),
    Play(R.drawable.ic_kitsugi_play, listOf("PlayArrow", "PlayCircleOutline")),

    // ── Kaynaklar / entegrasyonlar ───────────────────────────────────────
    Extensions(R.drawable.ic_kitsugi_extensions, listOf("Extension", "Widgets")),
    Integrations(R.drawable.ic_kitsugi_integrations, listOf("Hub", "DeviceHub", "Api")),
    Network(
        R.drawable.ic_kitsugi_network,
        listOf("NetworkCheck", "Wifi", "WifiOff", "SettingsEthernet", "Dns", "Lan", "Cloud", "CastConnected")
    ),
    Sync(R.drawable.ic_kitsugi_sync, listOf("Sync", "SyncAlt", "SyncProblem", "CloudSync", "Cached", "Autorenew")),

    // ── Veri / depolama ──────────────────────────────────────────────────
    Database(R.drawable.ic_kitsugi_database, listOf("Storage", "Backup", "Save", "CloudUpload")),
    Storage(R.drawable.ic_kitsugi_storage, listOf("CleaningServices", "DeleteSweep", "FolderDelete")),

    // ── İndirmeler / geçmiş ──────────────────────────────────────────────
    Tune(R.drawable.ic_kitsugi_tune, listOf("Tune", "SettingsApplications", "Equalizer", "GraphicEq")),
    Download(
        R.drawable.ic_kitsugi_download,
        listOf("Download", "DownloadForOffline", "FileDownload", "SaveAlt", "DownloadDone")
    ),
    History(R.drawable.ic_kitsugi_history, listOf("History", "Restore", "Update", "WatchLater", "Replay")),

    // ── Destek / tanılama ────────────────────────────────────────────────
    Info(R.drawable.ic_kitsugi_info, listOf("Info", "InfoOutline")),
    Feedback(R.drawable.ic_kitsugi_feedback, listOf("Feedback", "RateReview", "Campaign", "Announcement")),
    Warning(R.drawable.ic_kitsugi_warning, listOf("BugReport", "Warning", "WarningAmber", "ErrorOutline", "Report")),
    Help(R.drawable.ic_kitsugi_help, listOf("Help", "HelpOutline", "QuestionMark")),
    Memo(R.drawable.ic_kitsugi_memo, listOf("Notes", "Description", "Article", "EditNote", "Book", "MenuBook", "Title", "TextFields", "Translate")),

    // ── Oynatıcı ayarları sekmeleri ──────────────────────────────────────
    Gestures(
        R.drawable.ic_kitsugi_gestures,
        listOf("Swipe", "SwipeLeft", "SwipeRight", "SwipeUp", "SwipeDown", "Gesture", "TouchApp", "PanTool")
    ),
    Decoder(R.drawable.ic_kitsugi_decoder, listOf("Memory", "DeveloperBoard", "Hd", "HighQuality")),
    Subtitles(R.drawable.ic_kitsugi_subtitles, listOf("Subtitles", "SubtitlesOff", "ClosedCaption", "SubtitlesOutlined")),
    Audio(
        R.drawable.ic_kitsugi_audio,
        listOf("VolumeUp", "VolumeDown", "VolumeMute", "VolumeOff", "Audiotrack", "MusicNote", "Hearing")
    ),
    Buttons(R.drawable.ic_kitsugi_buttons, listOf("SmartButton", "SportsEsports", "Gamepad", "Vibration")),
    Code(R.drawable.ic_kitsugi_code, listOf("Code", "CodeOff", "Terminal", "DataObject")),
    Advanced(
        R.drawable.ic_kitsugi_advanced,
        listOf("DeveloperMode", "SettingsSuggest", "Construction")
    ),

    // ── Yardımcı ─────────────────────────────────────────────────────────
    Speed(R.drawable.ic_kitsugi_speed, listOf("Speed", "Bolt", "FlashOn", "RocketLaunch", "SlowMotionVideo")),
    Timer(R.drawable.ic_kitsugi_timer, listOf("Timer", "HourglassEmpty", "HourglassFull", "AccessTime", "Schedule")),
    Sun(R.drawable.ic_kitsugi_sun, listOf("BrightnessMedium", "Brightness7", "BrightnessLow", "WbSunny", "LightMode")),
    Next(R.drawable.ic_kitsugi_next, listOf("SkipNext", "FastForward", "Forward10", "Forward30", "Forward5", "Redo")),
    Tv(R.drawable.ic_kitsugi_tv, listOf("Tv", "ConnectedTv", "LiveTv", "SmartDisplay")),
    Keyboard(R.drawable.ic_kitsugi_keyboard, listOf("Keyboard", "KeyboardAlt")),
    Wrench(R.drawable.ic_kitsugi_wrench, listOf("Handyman", "HomeRepairService")),
    Key(R.drawable.ic_kitsugi_key, listOf("Key", "KeyOff", "VpnKey", "Password")),
    Star(R.drawable.ic_kitsugi_star, listOf("Star", "StarBorder", "StarHalf", "Grade", "Favorite")),
    Picture(R.drawable.ic_kitsugi_image, listOf("Image", "Photo", "InsertPhoto", "Wallpaper", "ImageSearch")),
    Delete(R.drawable.ic_kitsugi_delete, listOf("Delete", "DeleteOutline", "RemoveCircle", "DeleteForever")),
    Eye(R.drawable.ic_kitsugi_eye, listOf("Visibility", "VisibilityOff", "RemoveRedEye")),
    Public(R.drawable.ic_kitsugi_integrations, listOf("Public", "Language", "TravelExplore")),

    // ── Gezinme çubuğu ───────────────────────────────────────────────────
    NavExplore(R.drawable.ic_kitsugi_nav_explore, listOf("Explore", "TravelExplore")),
    NavList(R.drawable.ic_kitsugi_nav_list, listOf("ListAlt", "Bookmarks", "CollectionsBookmark", "FormatListBulleted")),
    NavSearch(R.drawable.ic_kitsugi_nav_search, listOf("Search", "ManageSearch")),
    NavProfile(R.drawable.ic_kitsugi_nav_profile, listOf("Person", "Person2", "PersonOutline")),
    NavSettings(R.drawable.ic_kitsugi_advanced, listOf("Settings", "Build"));

    companion object {
        /** Material ikon adı -> 3B ikon. Ad "Rounded.PlayCircle" ise son parça kullanılır. */
        private val byVectorName: Map<String, KitsugiArt> by lazy {
            val map = HashMap<String, KitsugiArt>()
            values().forEach { art ->
                art.vectorNames.forEach { name -> map[name] = art }
            }
            map
        }

        /**
         * Verilen Material [icon] için 3B karşılığını döner; eşleşme yoksa `null`.
         * `null` dönerse çağıran taraf eski tek renkli glife düşer.
         */
        fun fromVector(icon: ImageVector): KitsugiArt? {
            val rawName = icon.name
            if (rawName.isEmpty()) return null
            byVectorName[rawName]?.let { return it }
            return byVectorName[rawName.substringAfterLast('.')]
        }
    }
}

/** Tek bir 3B ikonu çizer (saydam arka plan, kutu doldurma). */
@Composable
fun KitsugiArtIcon(
    art: KitsugiArt,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: Dp = 30.dp,
    alpha: Float = 1f
) {
    Image(
        painter = painterResource(art.resId),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
        alpha = alpha
    )
}

/**
 * Ayar satırı ikonu: 3B görsel varsa onu, yoksa satır rengiyle boyanmış tek renkli
 * glifi (arka plan dairesi OLMADAN) çizer. Slot her iki durumda da aynı genişlikte
 * olduğu için başlık hizası kaymaz.
 */
@Composable
fun KitsugiRowIcon(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    art: KitsugiArt? = null,
    artSize: Dp = 30.dp,
    glyphSize: Dp = 24.dp
) {
    val resolved = art ?: KitsugiArt.fromVector(icon)
    Box(
        modifier = modifier.size(32.dp),
        contentAlignment = Alignment.Center
    ) {
        if (resolved != null) {
            KitsugiArtIcon(art = resolved, size = artSize)
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(glyphSize)
            )
        }
    }
}
