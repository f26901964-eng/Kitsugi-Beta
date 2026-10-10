# Galeri → Silme Butonu, GIF Paylaşma/İndirme ve Global +18 Blur (2026-10-10)

Sürüm: **2.4.227** · Kapsam: telefon + Android TV · Durum: kaynak düzeyinde tamamlandı, **bu ortamda derlenmedi** (sandbox'ta JDK/Android SDK yok).

---

## 1) Görsel galerisine üçüncü buton: **Sil**

**İstek:** Galeri nereden açılırsa açılsın (détay, sezon/episode galerileri, İndirilenler…) indirilen içeriği galeriden doğrudan silebileyim; dikey ve yatay yerleşime uyumlu olsun.

**Yapılan:**

- `KitsugiImageGalleryDialog`'a iki yeni param: `allowDelete: Boolean = true`, `onItemsDeleted: (List<String>) -> Unit = {}`. 24 çağrı noktasının tamamı isimli parametre kullandığı için geriye dönük uyumlu.
- Buton sırası:
  - **Dikey:** `Bilgi → İndirildi(başarı) → Paylaş(fly) → Sil → Kapat` (40.dp kare ikonlar).
  - **Yatay:** başlıktaki `Paylaş` satırına eklendi → `İndirildi → Paylaş → Sil → Kapat` (36.dp, 220.dp panel genişliğine sığan sabit kare buton).
- Buton `KitsugiGalleryDeleteButton` (trash/kloş + çan efekti + glow); resim indirilmemişse soluk görünür, tıklanınca toast: *"Bu resim daha önce indirilmemiş — silinecek bir kopya yok."*
- Silme öncesi **onay diyaloğu** (`KitsugiConfirmDialog`, `isDestructive`): sadece uygulamadaki kopyanın silineceği, uzak kaynağa dokunulmadığı belirtilir.
- Silinecek kopya tespiti galerinin kendisinde değil, `KitsugiImageDownloadHelper.hasDeletableCopy()` içinde: index varsa → *uygulama + Galeri/İndirilenler*, MediaStore kaydı varsa → *yalnızca Galeri/İndirilenler*, SAF tree kaydı varsa → *kullanıcı onayı gerekir*.
- Silme sonrası: index kaydı düşürülür (`dropIndexRecordForUrl`) → `MediaScannerConnection.scanFile` / `ContentResolver.notifyChange` → `refreshDownloadedUrls()` → `onItemsDeleted(urls)` → sayfa indeksi `filteredItems.size - 1` sınırına çekilir.
- **Toplu silme:** galeride buton yok (2 butonlu onay diyaloğu 3. aksiyonu taşımıyordu) — İndirilenler ekranındaki grup başlığına `KitsugiAnimatedDeleteButton` olarak kondu ("Bu grubu sil"). Galeri İndirilenler'den açıldığında grup otomatik kapanır ve liste yenilenir.
- Android TV: galeri zaten odak/DPad yönetimli olduğundan Sil butonu yön tuşlarıyla gezilir, Enter ile onay diyaloğuna geçilir.

**Dokunan dosyalar:** `ui/components/KitsugiImageGalleryDialog.kt`, `utils/KitsugiImageDownloadHelper.kt`, `ui/screens/offline/DownloadsScreen.kt`.

---

## 2) GIF (ve diğer formatlar) için paylaşma + indirme

**Durum tespiti:** indirme tarafı GIF'i zaten *olduğu gibi* kaydediyordu (sihirli numara ile format tespiti, ham bayt, doğru MIME/uzantı) ve galeride Coil `AnimatedImageDecoder` ile hareketli oynuyordu. Asıl kırıklar paylaşma ve uç durumlarındaydı:

| Sorun | Düzeltme |
|---|---|
| Yerel (`file://` / `content://`) öğelerde `URL().openConnection()` → `MalformedURLException: Unknown protocol: file` | Tüm bayt kaynakları tek `fetchImage()` üzerinden: `http(s)`, `file`, `content` |
| CDN'lerin UA/Referer'sız istekleri 403lemesi | `KitsugiHttpClient` ortak istemcisi (tarayıcı UA + `android-app://` referer) |
| HTML hata sayfasının görsel kaydedilmesi (0 KB'lık "kapak") | `hasImageSignature()` — JPEG/PNG/GIF/WebP/AVIF/HEIF/BMP/ICO/APNG imzası olmayan veri reddedilir |
| HEIF/AVIF/BMP/TIFF/APNG tanınmıyordu | Magic-number tespiti genişletildi; APNG `acTL` chunk yürüyüşüyle, WebP `VP8X` A bitiyle, GIF GCE sayımıyla **animasyon** ayrımı |
| GIF'in PNG'ye dönüştürülüp hareketin kaybolması riski | Yalnızca `needsTranscode` işaretli formatlar paylaşım öncesi yeniden kodlanır; **GIF/WebP/PNG baytlarına dokunulmaz** |
| Tek seferlik `EXTRA_STREAM` izinlerinde bazı alıcıların erişememesi | `ClipData` + `FLAG_GRANT_READ_URI_PERMISSION` + `EXTRA_TITLE` / `EXTRA_TEXT` (başlık açıklama olarak gider) |
| Büyük GIF'lerde OOM | Sınır: 48 MB (`MAX_TRANSFER_BYTES`), aşımında anlaşılır hata mesajı |
| Dosya adı uzantısı MIME ile çelişebiliyordu | Ad, tespit edilen formatın uzantısıyla üretilir; İndirilenler tarayıcısının uzantı seti de aynı listeye çekildi (`avif/heic/heif/bmp/tiff`) |
| Paylaşım geçici dosyaları birikiyordu | `cacheDir/shared_images` + 1 saat TTL'li otomatik budama |

Sonuç: GIF hem **orijinal hareketiyle indirilir** hem de **bozulmadan paylaşılır**; hedef galeride artık "indirildi" rozeti kalır, dosya silinince rozet düşer ve tekrar indirilebilir.

---

## 3) +18 blur: tüm kaynaklar, tüm sayfalar

**Kök neden (kodda doğrulandı):** Ekranlarda blur kaçak değildi — kartlar zaten `KitsugiNsfwImage` çiziyordu; sorun **verideydi**:
1. Simkl satırları `isAdult = false` kaydediliyor (Simkl API'i +18 bayrağı vermiyor).
2. `smartImport` mevcut satırı komple ezip bayrağı siliyordu.
3. `entriesFlow` entity'yi olduğu gibi yayınlıyordu → aynı yapımın başka kaynakta işaretli olması Simkl kartını etkilemiyordu.
4. Gece otomatik senkronu `deleteBySource + insertAll` yaptığı için onarılan bayraklar ertesi gün kayboluyordu ("bir düzeldi, bozuldu").

**Yapılan:**
- **Kaynaklar arası yayılım:** `MediaEntryRepository` — `withCrossSourceAdultFlags()` satırları `MediaIdentity.keys()` üzerinden birleştirir, bayrağı OR'lar (yalnızca `false → true`).
- **İşaret korunur:** `smartImport` birleştirme adımı + yeni `replaceSourcePreservingAdultFlags(source, entries)` (4 delete+insert çağrı noktası buna alındı: `KitsugiApplication` ×3, `AiringNotificationWorker` ×1).
- **Onarım göçü:** yeni `ui/screens/mylist/AdultFlagBackfillMigration.kt` — bayrağı olmayan satırları kanonik kimlikle tekrar sorar: MAL kimliği → Shikimori GraphQL (50'lik paket), `tmdb` satırı/`tmdbId` → yeni `data/remote/TmdbAdultResolver.kt`. Negatif cevaplar deftere yazılır, tekrar sorulmaz; ağ hatasında 12 saat içinde yeniden deneme; içe aktarım sonrası `requestRescan()`.
- Eski tek kaynaklı `ShikimoriAdultFlagMigration.kt` kaldırıldı (işini yeni göç devraldı).
- **Blur baypası kapatıldı:** Shikimori ve Kitsu profil "kayıtlar" posterleri düz `AsyncImage` çiziyordu → `KitsugiNsfwImage`'a bağlandı. (`ProfileFavoriteItem.isAdult` zaten mevcuttu.)
- TV dahil tüm kart yolları tek bileşenden geçtiği için Keşfet, Listem ("Tümü" sekmesi dahil), Arama, Détay, Yayın takvimi, manga rafları, oynatıcı kartları kapsamlı; API 31 altı cihazlarda bitmap blur fallback korunuyor.

**Bilinçli bırakılanlar:** Simkl/Bangumi API'lerinden doğrudan +18 verisi alınamıyor (kimlik üzerinden dolaylı çözülüyor); Bangumi/aktivite kartlarında `isAdult` alanı hiç yok, uydurulmadı. Uzak içerik silinmez. `RecoverableSecurityException` intent akışı yerine kullanıcıya sistem onayı gerektiren durumda net mesaj gösteriliyor.

---

## 4) Testler

- `app/src/test/.../utils/ImageFormatDetectionTest.kt` — format/animasyon tespiti, imzasız veri reddi, paylaşım normalizasyonu.
- `app/src/test/.../ui/screens/mylist/AdultFlagBackfillMigrationTest.kt` — kaynak/kimlik bazlı çözümleme planı ve kontrol defteri.
- `app/src/test/.../data/local/MediaEntryRepositoryAdultPropagationTest.kt` — bayrak yayılımı, tür ayrımı, "asla geri alınmaz", yeniden tarama koşulları.

**Cihazda doğrulanacaklar:**
1. Bir resmi galeriden indir → galeride "indirildi" rozeti → Sil → onay → dosya Galeri/İndirilenler'den kalksın, rozet düşsün, aynı resim tekrar indirilebilsin.
2. Yatay çevirip aynısını dene; İndirilenler → Görseller sekmesinde grup başlığındaki "Bu grubu sil"i dene.
3. GIF'i Paylaş (WhatsApp/Telegram) → hareket korunmalı; `file://`/`content://` öğelerinde de paylaşma patlamamalı.
4. Blur ayarını açıp Simkl kaynağında Listem + Keşfet'te +18 posterlerinin bulandığını gör; uygulamayı bir kez tamamen aç-kapat (onarım göçü çalışır) ve aynı +18'in AniList kaynağında da işaretli olduğunu doğrula.

## 5) Derleme notu

Bu sandbox'ta JDK/Android SDK/Gradle önbelleği olmadığı için **derleme ve test koşumu yapılamadı**. Değişiklikler dosya dosya göden geçirildi, parantez dengesi ve tüm yeni parametre/isim çağrı eşleşmeleri grep ile doğrulandı. İlk `./gradlew` koşumunda çıkabilecek hatalar yalnızca yazım/uygulama detayı düzeyindedir.
