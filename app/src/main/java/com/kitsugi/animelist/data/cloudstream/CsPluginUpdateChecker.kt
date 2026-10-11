package com.kitsugi.animelist.data.cloudstream

import android.content.Context
import com.kitsugi.animelist.data.local.CsPluginEntity
import com.kitsugi.animelist.data.remote.CsPlugin

/**
 * "Bu eklenti için güncelleme var mı?" sorusunun TEK karar mercii.
 *
 * Önceki durumda bu karar 6 ayrı yerde kopyalanmış `latest.version > installed.version`
 * karşılaştırmasıyla veriliyordu (ViewModel, kurulu eklenti satırı, depo kartı, eklenti
 * satırı, otomatik güncelleme, toplu güncelleme). İki sonucu vardı:
 *
 *  1. `version` alanı artırılmadan yeniden derlenen eklentiler (depo manifestinde `fileHash`
 *     değişir, `version` aynı kalır) hiçbir yerde "güncellenebilir" görünmüyordu.
 *  2. Kararlar zamanla birbirinden ayrışıyordu (bir ekran güncelleme gösterirken diğeri
 *     göstermiyordu).
 *
 * Artık karar [decide] içinde toplanır: sürüm **veya** içerik parmak izi farkı güncelleme
 * sayılır. [CsInstalledFingerprint] kurulu dosyanın SHA-256'sını sağlar, depo manifestindeki
 * `fileHash` ise depodaki dosyanın SHA-256'sıdır.
 */
object CsPluginUpdateChecker {

    enum class Reason {
        /** Güncelleme yok. */
        NONE,

        /** Depodaki sürüm numarası kuruludan yüksek. */
        VERSION,

        /** Sürüm aynı ama dosya içeriği farklı (yeniden derlenmiş eklenti). */
        CONTENT
    }

    /** Stremio/HTTP addon manifestleri `.cs3` gibi sürümlenmez; güncelleme denetimine girmez. */
    fun isManifestAddon(url: String?): Boolean =
        url?.trim()?.endsWith("/manifest.json", ignoreCase = true) == true

    /**
     * Depo manifestindeki `fileHash` alanını karşılaştırılabilir biçime indirger.
     *
     * Kabul edilen biçimler: `sha256-<64 hex>`, `SHA256:<64 hex>`, doğrudan `<hex>`.
     * Anlaşılmayan/eksik değerler `null` döner ve karar yalnızca sürüm numarasına dayanır.
     */
    fun normalizeHash(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val candidate = raw.trim()
            .substringAfterLast('-')
            .substringAfterLast(':')
            .lowercase()
            .trim()
        if (candidate.length !in 32..64) return null
        if (!candidate.all { it in '0'..'9' || it in 'a'..'f' }) return null
        return candidate
    }

    /**
     * Saf karar fonksiyonu (Android bağımlılığı yok, birim testlerle doğrulanır).
     *
     * @param installedHash Kurulu `.cs3` dosyasının SHA-256'sı (yoksa `null`).
     * @param latestHash Depo manifestindeki dosyanın SHA-256'sı (yoksa `null`).
     */
    fun decide(
        installedVersion: Int,
        installedHash: String?,
        latestVersion: Int,
        latestHash: String?
    ): Reason {
        // Depodaki sürüm daha yüksek → klasik güncelleme.
        if (latestVersion > installedVersion) return Reason.VERSION
        // Kurulu sürüm depodan yeni → geri alma (downgrade) yapılmaz.
        if (latestVersion < installedVersion) return Reason.NONE
        // Sürümler eşit: dosya değişmiş mi?
        if (installedHash != null && latestHash != null && !installedHash.equals(latestHash, ignoreCase = true)) {
            return Reason.CONTENT
        }
        return Reason.NONE
    }

    /**
     * Kurulu bir eklenti için güncelleme gerekip gerekmediği.
     *
     * Performans notu: içerik parmak izi YALNIZCA sürüm numaraları eşit olduğunda hesaplanır;
     * sürüm karşılaştırması tek başına karar verdiğinde diske hiç dokunulmaz.
     */
    fun reason(context: Context, installed: CsPluginEntity, latest: CsPlugin?): Reason {
        val repoPlugin = latest ?: return Reason.NONE
        if (isManifestAddon(repoPlugin.url)) return Reason.NONE
        // Yedek kaynaktan (Codeberg aynası / APK içine gömülü bayat liste) gelen kayıtların
        // sürüm numaraları canlı depoyla aynı seriyi paylaşmaz. Bunlara dayanarak güncelleme
        // önermek, kullanıcıyı ESKİ bir derlemeye düşürebilir; bu yüzden karar verilmez.
        if (repoPlugin.fromOfflineFallback) return Reason.NONE

        if (repoPlugin.version > installed.version) return Reason.VERSION
        if (repoPlugin.version < installed.version) return Reason.NONE

        val latestHash = normalizeHash(repoPlugin.fileHash) ?: return Reason.NONE
        val installedHash = CsInstalledFingerprint.sha256(context, installed.id) ?: return Reason.NONE
        return decide(installed.version, installedHash, repoPlugin.version, latestHash)
    }

    fun hasUpdate(context: Context, installed: CsPluginEntity, latest: CsPlugin?): Boolean =
        reason(context, installed, latest) != Reason.NONE

    /**
     * Kurulu eklentiler arasından güncellenebilir olanları seçer.
     *
     * @param latestById Depo manifestlerinden derlenen `internalName → en yeni kayıt` haritası.
     */
    fun pendingUpdates(
        context: Context,
        installed: List<CsPluginEntity>,
        latestById: Map<String, CsPlugin>
    ): List<Pair<CsPluginEntity, CsPlugin>> = installed.mapNotNull { entity ->
        if (isManifestAddon(entity.downloadUrl)) return@mapNotNull null
        val latest = latestById[entity.id] ?: return@mapNotNull null
        if (hasUpdate(context, entity, latest)) entity to latest else null
    }
}
