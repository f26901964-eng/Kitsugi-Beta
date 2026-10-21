# KÖK NEDEN RAPORU — İndirilenler: Altyazı ve Resim Silme Çalışmıyor
**Sürüm:** v2.4.332 · **Tarih:** 2026-10-21 · **Kapsam:** `DownloadsScreen`, `KitsugiImageDownloadHelper`, `DownloadStorage`

## 1. Şikâyet
İndirilenler ekranında **Videolar** sekmesinde silme çalışıyor; ancak **Altyazılar** ve
**Resimler** sekmelerindeki sil düğmeleri hiçbir şey silmiyor (kart tazelenince geri geliyor,
bazen hiç geri bildirim verilmiyor).

## 2. Kök nedenler (tek tek)

### 2.1. Görsel kartındaki silme `File.delete()` idi
`DownloadsScreen.DownloadedImagesTab` → `DownloadedImageCard.onDelete`:

```kotlin
if (img.file.exists() && img.file.delete()) { ... } else false
```

Görseller **`MediaStore.Downloads` / `MediaStore.Images`** ile
`Download/Kitsugi/Images` (ve yedek olarak `Pictures/Kitsugi/Images`) altına yazılıyor
(`KitsugiImageDownloadHelper.saveImageViaMediaStore`). Android 10+ Scoped Storage'da
MediaStore üzerinden oluşturulan bir dosya için `File.delete()` **istisna fırlatmaz,
sessizce `false` döner**. Sonuç: dosya diskte kalır, `refreshImagesTrigger++` ile liste
yeniden taranınca kart geri gelir. Üstelik `else` dalında **hiçbir kullanıcı bildirimi yoktu**,
yani kullanıcı "düğme hiçbir şey yapmıyor" görüyordu.

Aynı hata grup başlığındaki toplu silmede de vardı (`deleteImageForUrl("file://…")` →
`deleteLocalFile` → `File.delete()` + `DATA` sütununa dayalı MediaStore araması). Android
11+ üzerinde `DATA` sütunuyla sorgulama güvenilir değil; bu yüzden yedek yol da düşüyordu.

### 2.2. Altyazı silme de `File.delete()` idi
`KitsugiPlayerViewModel.downloadSubtitleToStorage` altyazıları **`MediaStore.Downloads`**
ile `Download/Kitsugi/Subtitles` altına yazıyor. Altyazı sekmesindeki silme:

```kotlin
subItem.file.exists() && subItem.file.delete()
```

Aynı Scoped Storage davranışı → dosya silinmiyor; kod bunu "başarısız" sayıp öğeyi listeye
geri ekliyordu (veya sessiz kalıyordu). Ayrıca aynı adı taşıyan **kopyalar** (public
`Kitsugi/Subtitles`, `Kitsugi/Video`, `cacheDir/subtitles`) silinmediği için öğe listede
kalmaya devam ediyordu: `loadAllDownloadedSubtitles` üç ayrı kökü tarıyor.

### 2.3. Sistem onayı (sahiplik) hiç ele alınmıyordu
Uygulama yeniden kurulduğunda (veya dosyalar başka bir UID ile oluşturulduğunda) MediaStore
kayıtları artık uygulamanın sahibi değildir. Bu kayıtlar **yalnızca**
`MediaStore.createDeleteRequest` ile sistem onayı alınarak silinebilir. Kod bu durumda
sadece bir metin gösteriyordu; silme imkânı hiç sunulmuyordu.

## 3. Çözüm

### 3.1. Yeni: `core/storage/MediaStoreFileDeleter.kt`
Tek sorumluluk: paylaşılan depolamadaki bir dosyayı **güvenilir** silmek.
- Kaydı `DISPLAY_NAME` (+ `RELATIVE_PATH`) ile arar (`DATA` kullanılmaz),
- `OWNER_PACKAGE_NAME` ile sahipliği denetler,
- sahibi olduğumuz kaydı `ContentResolver.delete` ile siler,
- sahibi olmadığımız kayıtları `foreignUris` olarak döndürür → arayüz
  `MediaStore.createDeleteRequest` onayını açar,
- `ContentResolver` / galeri bildirimlerini `notifyChange` ile tazeler,
- hiçbir adımda istisna fırlatmaz (`Outcome`).

### 3.2. `KitsugiImageDownloadHelper`
- **Yeni `suspend fun deleteImageFile(context, file)`**: index kaydı (MediaStore URI'si /
  SAF klasörü) → aynı adlı tüm disk kopyaları → gerekirse MediaStore-by-name → sonuç
  doğrulaması. "Sildim" demeden önce dosyanın gerçekten gittiğini kontrol eder.
- `deleteLocalFile` artık `File.delete()` başarısız olduğunda MediaStore kaydını ad +
  göreli klasörle siler; başka pakete aitse `RequiresSystemConsent(uris)` döner.
- `GalleryDeleteOutcome.RequiresSystemConsent` artık `uris` taşıyor.
- `systemDeleteRequestSender(...)` ile Android 11+ sistem onayı üretilir.
- `deleteViaMediaStoreByPath` `DATA` sorgusundan kurtarıldı.
- Toplu silmede (`deleteAllDownloadedImages`) N dosya için N ek sorgu yapılmaması adına
  MediaStore "hayalet satır" temizliği yalnızca tekli silme sonunda bir kez çalışır.

### 3.3. `DownloadStorage`
- **Yeni `suspend fun deleteSubtitle(context, file): SingleDeleteResult`**: aynı adlı tüm
  kopyaları (video kökleri + `Subtitles` + `cacheDir/subtitles`) bulur, her birini önce
  `File.delete()` sonra MediaStore ile siler, sonucu doğrular; `consentUris` döndürür.
- `clean(SUBTITLES)` de aynı sağlam yolu kullanır (Ayarlar → Depolama → "Altyazıları sil").

### 3.4. `DownloadsScreen`
- Görsel silme (tekli kart **ve** grup) tek bir akıştan geçer: `deleteImageFiles`.
  İyimser liste güncellemesi + kesin sonuç bildirimi + her durumda tazeleme.
- Altyazı silme `deleteSubtitleItem` üzerinden `DownloadStorage.deleteSubtitle` çağırır;
  başarısızsa öğe geri eklenir ve **neden** silinemediği toast ile gösterilir.
- Sistem onayı köprüsü: `rememberLauncherForActivityResult(StartIntentSenderForResult)`.
  Onay → liste tazelenir; iptal → "Silme iptal edildi".
- Grup silme artık **onay penceresi** gösteriyor (yanlışlıkla 15-20 resmi silmeyi önler).

### 3.5. `KitsugiImageGalleryDialog`
- Galeriden silmede de sistem onayı akışı eklendi; onay sonrası `onItemsDeleted` çağrılır,
  gereksiz hata toast'ı bastırılır.

## 4. Davranış matrisi (silme sonrası)

| Durum | Sonuç |
|---|---|
| Dosya app-specific klasörde | `File.delete()` → başarı |
| Dosya MediaStore ile yazılmış, sahibi biz | `ContentResolver.delete` → başarı, satır düşer |
| Aynı adlı kopya birden fazla klasörde | Hepsi silinir, liste tamamen temizlenir |
| Kayıt başka UID'ye ait | Sistem onay penceresi → onayda silinir, iptalde bilgi mesajı |
| Gerçekten silinemiyor (salt okunur / kilitli) | Kart geri gelir + **neden** silinemediği toast'ı |

## 5. Notlar
- Hiçbir yerde `MANAGE_EXTERNAL_STORAGE` istenmez; Play politikasına uygun kalınır.
- Ağır disk taraması hâlâ `Dispatchers.IO` üzerinde; ana iş parçacığında dosya işlemi yok.
- Silme düğmeleri eskisi gibi tek dokunuşla çalışır; yalnızca **grup** silmede onay istenir.
