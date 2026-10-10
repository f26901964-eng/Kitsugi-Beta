# PLAN · TASK — Galeri "Sil" butonu + GIF paylaşma/indirme + global +18 blur (2026-10-10, v2.4.227)

Ayrıntılı teknik rapor: `KITSUGI_GALERI_SILME_GIF_BLUR_RAPORU_2026-10-10.md`

## Görevler ve durum

| # | Görev | Kök neden | Durum |
|---|---|---|---|
| 1 | Galeride 3. buton: indirileni galeriden sil (dikey + yatay) | Galeri başlığında yalnızca indir + paylaş vardı; silme sadece İndirilenler ekranındaydı | ✅ |
| 2 | Paylaş + İndir GIF'i (ve tüm desteklenen image formatlarını) bozmasın | Yerel `file://`/`content://` kaynaklarda `MalformedURLException`; imzasız/HTML verinin kaydı; HEIF-AVIF-BMP-TIFF-APNG tanınmaması; `ClipData` izni eksik | ✅ |
| 3 | 18+ blur, ayar açıksa TÜM kaynaklar ve tüm sayfalar (Simkl Listem, Keşfet dahil) | Render değil veri sorunu: Simkl satırları `isAdult=false`; `smartImport` bayrağı eziyor; gece senkronu `deleteBySource+insertAll` ile onarılan bayrağı siliyordu | ✅ |

## 1 · Galeri → Sil

- [x] `KitsugiImageGalleryDialog(allowDelete = true, onItemsDeleted = {})` — 24 çağrı noktası isimli parametre, geriye dönük uyumlu.
- [x] Dikey sıra: Bilgi → İndirildi → Paylaş → **Sil** → Kapat (40.dp).
- [x] Yatay sıra (220.dp panel): İndirildi → Paylaş → **Sil** → Kapat (36.dp sabit kare; `KitsugiAnimatedDeleteButton` panele sığmadığı için tercih edilmedi).
- [x] `KitsugiGalleryDeleteButton` (trash+kloş, aktif/pasif + glow), TV'de Dpad/Enter ile gezilebilir.
- [x] İndirilmemiş kopyada tıklama → toast "Bu resim daha önce indirilmemiş — silinecek bir kopya yok."
- [x] `KitsugiConfirmDialog` onayı; "Uzak kaynak silinmez; resmi istersen tekrar indirebilirsin."
- [x] `hasDeletableCopy()` / `describeDeleteTarget()` ile kopya türü tespiti (uygulama kopyası · MediaStore · SAF tree → sistem onayı mesajı).
- [x] `deleteImageForUrl()` → index düşür (`dropIndexRecordForUrl`) → `MediaScannerConnection`/`notifyChange` → `refreshDownloadedUrls()` → `onItemsDeleted(urls)` → sayfa indeksi clamp.
- [x] İndirilenler → Görseller: grup başlığında toplu silme (`KitsugiAnimatedDeleteButton`), galeri oradan açıldığında grup otomatik kapanır + liste yenilenir.

## 2 · GIF / format desteği

- [x] `ImageFormat` + `detectImageFormat()`: GIF (GCE sayımı ile animasyon), PNG (+APNG `acTL` chunk yürüyüşü), JPEG/MPO, WebP (VP8/VP8L/VP8X A biti), AVIF/AVIS, HEIF/HEIC, BMP, TIFF.
- [x] `normalizeBytesForSharing()`: yalnızca `needsTranscode` işaretli formatlar yeniden kodlanır → **GIF/WebP animasyonu ve PNG şeffaflığı bayt bayt korunur**; decode başarısızsa orijinal baytlar gider.
- [x] `fetchImage()`: `http(s)` + `file` + `content` tek bayt kaynağı; `KitsugiHttpClient` (UA + `android-app://` referer); 48 MB üst sınır.
- [x] `hasImageSignature()`: imzasız/HTML veriyi reddetme → "0 KB kapak" sınıfı hata bitti.
- [x] `shareImage()` reuse-first: indirilmiş kopya varsa diskten paylaşır (ağ yok), `ClipData` + read grant + `EXTRA_TITLE`/`EXTRA_TEXT`, metin-link fallback'i.
- [x] `downloadImage()`: orijinal format + uzantı tutarlılığı; `cacheDir/shared_images` 1 saat TTL budama.
- [x] İndirilenler tarayıcı uzantı seti aynı listeye çekildi (`avif/heic/heif/bmp/tiff` eklendi).

## 3 · +18 blur global

- [x] `MediaEntryRepository.withCrossSourceAdultFlags()` — `MediaIdentity.keys()` üzerinden OR, yalnızca `false → true`.
- [x] `smartImport` birleştirmede bayrak koruması (API sahte `false` getirse bile).
- [x] `replaceSourcePreservingAdultFlags(source, entries)` — `KitsugiApplication` ×3 + `AiringNotificationWorker` ×1 delete+insert noktası buna bağlandı.
- [x] `AdultFlagBackfillMigration` (tek seferlik + `requestRescan`) + `TmdbAdultResolver`; Shikimori-only migrasyon kaldırıldı.
- [x] `MyListViewModel` init → göç tetikler; Simkl/Shikimori/Bangumi içe aktarımı sonrası anında tarama.
- [x] Blur baypası kapatıldı: Shikimori + Kitsu profil "kayıtlar" posterleri `KitsugiNsfwImage`'a alındı.

## Testler

- [x] `app/src/test/.../utils/ImageFormatDetectionTest.kt`
- [x] `app/src/test/.../ui/screens/mylist/AdultFlagBackfillMigrationTest.kt`
- [x] `app/src/test/.../data/local/MediaEntryRepositoryAdultPropagationTest.kt`
- [ ] **Kullanıcıda:** `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` — bu sandbox'ta JDK/Android SDK olmadığı için derleme/test koşumu yapılamadı (kod kaynak düzeyinde gözden geçirildi).

## Kapsam dışı bilinçli kararlar

- Uzak/CDN içeriği silinmez; yalnızca yerel kopya.
- Simkl/Bangumi API'lerinde +18 bayrağı yok → kimlik üzerinden dolaylı çözüm; bayrağı hiç olmayan (Bangumi favori, aktivite) kartlar için uydurma veri üretilmedi.
- `RecoverableSecurityException` intent akışı yerine sistem onayı gereken durumda net mesaj.
- Sürüm 2.4.226 → 2.4.227; commit/push kullanıcı isteğine bağlı bırakıldı.
