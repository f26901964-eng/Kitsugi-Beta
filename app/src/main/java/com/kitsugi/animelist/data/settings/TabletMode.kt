package com.kitsugi.animelist.data.settings

import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

/**
 * V2-F05 – TabletMode
 *
 * Tablet (geniş ekran) düzeni modunu belirler.
 * MoeList TabletMode.kt referans alındı.
 *
 * AUTO:    Ekran genişliği > 600dp ise tablet düzeni aktif
 * ENABLED: Her zaman tablet düzeni kullan (bölünmüş panel)
 * DISABLED: Her zaman telefon düzeni kullan (tek panel)
 */
enum class TabletMode(
    val id: String,
    val label: String,
    val description: String
) {
    AUTO(
        id = "AUTO",
        label = "Otomatik",
        description = KitsugiStrings.get(R.string.ekran_genisligine_gore_karar_verilir_600dp_t)
    ),
    ENABLED(
        id = "ENABLED",
        label = "Her Zaman Tablet",
        description = KitsugiStrings.get(R.string.iki_sutunlu_bolunmus_panel_duzeni_zorunlu_ki)
    ),
    DISABLED(
        id = "DISABLED",
        label = "Her Zaman Telefon",
        description = KitsugiStrings.get(R.string.tek_sutunlu_telefon_duzeni_zorunlu_kilinir)
    );

    companion object {
        fun fromId(id: String): TabletMode =
            entries.find { it.id == id } ?: AUTO

        fun all(): List<TabletMode> = entries.toList()
    }
}

/**
 * Yardımcı extension — verilen ekran genişliğinde tablet modunun aktif olup olmadığını döner.
 * @param screenWidthDp Cihazın mevcut ekran genişliği (dp cinsinden)
 */
fun TabletMode.isTabletActive(screenWidthDp: Int): Boolean = when (this) {
    TabletMode.AUTO     -> screenWidthDp >= 600
    TabletMode.ENABLED  -> true
    TabletMode.DISABLED -> false
}
