package com.kitsugi.animelist.core.music

/**
 * Manga okuyucusunun "Okuma Müziği" (ambiyans) alt sisteminin ortak modelleri.
 *
 * Neden ayrı paket: okuyucu ekranı, entegrasyon ayarları ve ön plan servisi aynı
 * durumu paylaşmak zorunda. Tek bir kaynak (bu dosya) sayesinde kaynak (source)
 * bilgisi, istasyon modeli ve oynatma durumu her yerde aynı şekilde okunur.
 */

/** Müziğin nereden çekileceği. */
enum class MangaMusicSource(val storageValue: String) {
    /** radio-browser.info açık API'si (anahtar gerektirmez). */
    RADIO_BROWSER("radio_browser"),

    /** Uygulamaya gömülü, ağ gerektirmeyen acil durum listesi. */
    BUILT_IN("built_in"),

    /** Okuma müziği tamamen kapalı. */
    OFF("off");

    companion object {
        fun fromStorage(value: String?): MangaMusicSource =
            entries.firstOrNull { it.storageValue.equals(value?.trim(), ignoreCase = true) }
                ?: RADIO_BROWSER
    }
}

/** Hazır ambiyans etiketleri. `tag`, API tarafında arama etiketidir. */
enum class MangaMusicMood(val tag: String) {
    LO_FI("lofi"),
    CLASSICAL("classical"),
    JAZZ("jazz"),
    AMBIENT("ambient");

    companion object {
        fun fromTag(value: String?): MangaMusicMood =
            entries.firstOrNull { it.tag.equals(value?.trim(), ignoreCase = true) } ?: LO_FI
    }
}

/**
 * Tek bir yayın istasyonu.
 *
 * [streamUrl] her zaman doğrudan çalınabilir bir akış adresi olmalıdır
 * (`https://…/stream.mp3` gibi). Liste sayfası / HTML adresi kabul edilmez.
 */
data class MusicStation(
    val id: String,
    val name: String,
    val streamUrl: String,
    val mood: String = "",
    val bitrate: Int = 0,
    val country: String = "",
    /** "Radio Browser", "SomaFM", "NPO" gibi kaynak etiketi — UI'da gösterilir. */
    val providerLabel: String = ""
)

/** Oynatıcının tek doğruluk kaynağı. */
data class MusicPlaybackState(
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val station: MusicStation? = null,
    val error: String? = null,
    val volume: Float = 0.6f
)

/** İstasyon listesi yüklemesinin sonucu — hata ile boş listeyi ayırt eder. */
data class MusicStationLoadResult(
    /** UI'da gösterilecek istasyonlar (hata olsa da yerleşik liste döner). */
    val stations: List<MusicStation> = emptyList(),
    /** true ise ağ/API başarısız olduğu için yerleşik yedek liste kullanıldı. */
    val usedFallback: Boolean = false,
    /** Kullanıcıya gösterilecek kısa hata metni (yalnızca gerçek hata varsa). */
    val error: String? = null
)
