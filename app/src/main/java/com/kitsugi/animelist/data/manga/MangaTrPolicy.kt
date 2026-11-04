package com.kitsugi.animelist.data.manga

/** Restrict the special login flow to the Manga-TR extension, not arbitrary sites. */
internal fun isMangaTrLoginRequired(packageName: String, message: String?): Boolean =
    packageName.endsWith(".mangatr", ignoreCase = true) &&
        message?.contains("üye girişi", ignoreCase = true) == true

/** A guessed mirror may belong to someone else; Manga-TR owns its domain selection. */
internal fun allowsAutomaticMangaMirror(packageName: String): Boolean =
    !packageName.endsWith(".mangatr", ignoreCase = true)

/** Manga-TR's extension handles its own challenge and image transformation. */
fun isMangaTrHost(host: String): Boolean =
    host.equals("manga-tr.com", true) || host.endsWith(".manga-tr.com", true) ||
        host.equals("mngtr.site", true) || host.endsWith(".mngtr.site", true)

/** Keep the complete URL including fragment; transformed images must not reuse old cache entries. */
fun mangaImageCacheKey(packageName: String, imageUrl: String): String =
    if (packageName.endsWith(".mangatr", ignoreCase = true)) "mangatr-descrambled-v1:$imageUrl" else imageUrl

/** Repository metadata must describe the APK itself, not merely its filename. */
fun matchesMangaApkMetadata(expectedPackage: String, expectedVersion: Long?, actualPackage: String?, actualVersion: Long?): Boolean =
    actualPackage == expectedPackage && actualVersion != null &&
        (expectedVersion == null || expectedVersion <= 0 || actualVersion == expectedVersion)
