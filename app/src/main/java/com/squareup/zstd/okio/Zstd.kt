@file:JvmName("OkioZstd")

package com.squareup.zstd.okio

import okio.Sink
import okio.Source

/**
 * Stub: `com.squareup.zstd:zstd-kmp-okio` API'si.
 *
 * NEDEN GEREKLI
 * -------------
 * KeiSource (extensionLib 1.6) filtre onbelligini diske zstd ile sikistirilmis yazar:
 *
 *   file.source().zstdDecompress().buffer().use { ... }   // readFilterCacheState
 *   tmpFile.sink().zstdCompress().buffer().use { ... }    // writeFilterCache
 *
 * Kitsugi'da `zstd-kmp-okio` bagimliligi yok -> bu iki extension fonksiyonu
 * cozulemez ve filtre onbellegi kullanan kaynaklarda (supportsFilterFetching=true)
 * NoClassDefFoundError olusur.
 *
 * COZUM
 * -----
 * JVM imzalari upstream ile birebir ayni olan pass-through stub'lar
 * (`zstd-kmp-okio/api/jvm/zstd-kmp-okio.api`):
 *
 *   public static final fun zstdCompress   (Lokio/Sink;)Lokio/Sink;
 *   public static final fun zstdDecompress (Lokio/Source;)Lokio/Source;
 *
 * SIKISTIRMA YAPILMAZ. Bu bilinclidir: hem yazma hem okuma ayni stub'i kullandigi
 * icin onbellek dosyasi sikistirilmamis yazilir ve sikistirilmamis okunur — tutarlidir.
 * Gercek zstd verisiyle (ornegin baska bir uygulamadan tasinmis onbellek) karsisilrsa
 * icerik bozuk okunur; KeiSource bu durumda onbellegi yok sayip yeniden ceker.
 *
 * @file:JvmName zorunludur: eklenti `com.squareup.zstd.okio.OkioZstd.zstdCompress`
 * statik metoduna baglanir.
 */

/** Sıkıştırma yapmadan ayni Sink'i dondurur (bkz. sinif dokumani). */
fun Sink.zstdCompress(): Sink = this

/** Cozme yapmadan ayni Source'i dondurur (bkz. sinif dokumani). */
fun Source.zstdDecompress(): Source = this
