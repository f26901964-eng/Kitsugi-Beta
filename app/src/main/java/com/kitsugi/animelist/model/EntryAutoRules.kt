package com.kitsugi.animelist.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Liste ekleme / düzenleme sırasındaki **durum → tarih + ilerleme** otomasyonu.
 *
 * Amaç: kullanıcı bir yapımı "İzliyorum"a (ya da "Tamamlandı"ya) çektiğinde başlangıç
 * (ve gerekiyorsa bitiş) tarihinin günü gününe yazılması. Eskiden bu kural üç ayrı yere
 * dağılmıştı ve yalnızca **kaydetme anında**, kullanıcıya görünmeden uygulanıyordu
 * (`AppRoot.onConfirmEdit`, `AppRoot.incrementEntryProgress`). Artık tek bir yerde:
 *
 * - [KitsugiEditMediaSheet][com.kitsugi.animelist.ui.components.KitsugiEditMediaSheet]
 *   durum hapına basıldığı **anda** tarih satırını doldurur (kullanıcı ne yazılacağını görür),
 * - `AppRoot.onConfirmEdit` kaydetme anında son bir kez doğrular,
 * - `AppViewModel.insertApiSelection` hızlı eklemede (panel açılmadan) uygular.
 *
 * ### Kural tablosu
 *
 * | Durum                        | Başlangıç      | Bitiş         | İlerleme      |
 * |------------------------------|----------------|---------------|---------------|
 * | `Watching` / `Repeating`     | boşsa → bugün  | dokunulmaz    | dokunulmaz    |
 * | `Completed`                  | boşsa → bugün  | boşsa → bugün | `total`a çek  |
 * | `Planned`/`Paused`/`Dropped` | dokunulmaz     | dokunulmaz    | dokunulmaz    |
 *
 * > **Dolu bir tarih asla üzerine yazılmaz.** Kullanıcı "İzliyorum"dan "Planlandı"ya geri
 * > dönerse yazılmış tarih silinmez — geri dönüldüğünde yeniden "İzliyorum" yapıldığında
 * > eski tarih korunur, bugünün tarihiyle ezilmez. Bu bilinçli bir veri kaybı korumasıdır.
 *
 * Motor tamamen saftır (suspend yok, Context yok, Compose yok); böylece hem Compose içinde
 * hem `insertApiSelection` gibi Compose dışı bağlamlarda aynı sonucu üretir.
 */
object EntryAutoRules {

    /** Tarih biçimi: `yyyy-MM-dd` (AniList / MAL / Simkl ortak biçimi). */
    private const val PATTERN = "yyyy-MM-dd"

    /** Başlangıç tarihinin otomatik doldurulduğu durumlar. */
    private val START_DATE_STATUSES = setOf(
        WatchStatus.Watching,
        WatchStatus.Repeating,
        WatchStatus.Completed
    )

    /** Bugünün tarihi: `2026-10-20`. */
    fun today(): String = SimpleDateFormat(PATTERN, Locale.US).format(Date())

    /** Dünün tarihi: hızlı "Dün" çipi için. */
    fun yesterday(): String = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -1)
    }.time.let { SimpleDateFormat(PATTERN, Locale.US).format(it) }

    /** Boş kabul edilen değerler: null, boşluk ve `0000-00-00` benzeri yer tutucular. */
    fun isBlankDate(value: String?): Boolean =
        value.isNullOrBlank() || value.all { it == '0' || it == '-' }

    /**
     * Bir durum değişikliğinde uygulanacak otomatik başlangıç tarihi.
     * Gerekmiyorsa [current] olduğu gibi döner (hiçbir zaman `null`a çekilmez).
     */
    fun autoStartDate(status: WatchStatus, current: String?, today: String = today()): String? =
        if (autoDateEnabled && status in START_DATE_STATUSES && isBlankDate(current)) today else current

    /** Bir durum değişikliğinde uygulanacak otomatik bitiş tarihi. */
    fun autoEndDate(status: WatchStatus, current: String?, today: String = today()): String? =
        if (autoDateEnabled && status == WatchStatus.Completed && isBlankDate(current)) today else current

    /**
     * "Tamamlandı"ya çekilirken ilerlemenin toplam bölüme tamamlanması (AniList paritesi).
     *
     * [statusChanged] bilinçli bir koruma: durum **zaten** `Completed` olan bir kayıt
     * sadece düzenlendiğinde (ör. yalnızca puan değiştirildi) ilerlemeye dokunulmaz.
     * Aksi hâlde kullanıcının bilerek yarım bıraktığı ilerlemesi, görünür bir neden
     * olmadan son bölüme zıplardı.
     */
    fun autoProgress(status: WatchStatus, progress: Int, total: Int?, statusChanged: Boolean): Int {
        if (!autoProgressEnabled) return progress
        if (!statusChanged) return progress
        if (status != WatchStatus.Completed) return progress
        val safeTotal = total?.takeIf { it > 0 } ?: return progress
        return if (progress < safeTotal) safeTotal else progress
    }

    /** Tek seferde çözüm sonucu. */
    data class Resolved(
        val startDate: String?,
        val endDate: String?,
        val progress: Int
    )

    /**
     * Tüm kuralları tek seferde uygular.
     *
     * @param status   hedeflenen yeni durum
     * @param startDate mevcut başlangıç tarihi (boşsa doldurulur)
     * @param endDate   mevcut bitiş tarihi (boşsa doldurulur)
     * @param progress  mevcut ilerleme
     * @param total     toplam bölüm/sayı (bilinmiyorsa `null`)
     * @param statusChanged durum bu çağrıda gerçekten değişti mi? Yalnızca `true` ise
     *                      ilerleme otomatik tamamlanır (bkz. [autoProgress]).
     * @param today     test edilebilirlik için "bugün" enjeksiyonu
     */
    fun resolveOnStatusChange(
        status: WatchStatus,
        startDate: String?,
        endDate: String?,
        progress: Int,
        total: Int?,
        statusChanged: Boolean = true,
        today: String = today()
    ): Resolved = Resolved(
        startDate = autoStartDate(status, startDate, today),
        endDate = autoEndDate(status, endDate, today),
        progress = autoProgress(status, progress, total, statusChanged)
    )

    // ── Ayar köprüsü ────────────────────────────────────────────────────────────
    // Kural motoru saf kalsın diye ayarlar buradan, geç değişken (late-init) olarak
    // okunur: `KitsugiContentPrefs` SettingsDataStore akışı her yayınladığında güncellenir.

    private val autoDateEnabled: Boolean
        get() = com.kitsugi.animelist.data.settings.KitsugiContentPrefs.autoDateOnStatusChange

    private val autoProgressEnabled: Boolean
        get() = com.kitsugi.animelist.data.settings.KitsugiContentPrefs.autoProgressOnComplete
}
