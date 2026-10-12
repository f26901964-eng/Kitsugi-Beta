# PLAN / TASK — Eklentiler Sayfası: Bozuk Başlık Düzeni + Eklenti Silme (Tek/ Toplu)

**Tarih:** 2026-10-12
**Durum:** Kod değişiklikleri tamamlandı. Android derlemesi bu ortamda Java/JDK bulunmadığı için çalıştırılamadı (`java`, `JAVA_HOME` yok).

## Kullanıcı bildirimi
`Ayarlar > Eklentiler & Kaynaklar > Video Sağlayıcıları > Installed (165)` ekranında:
1. "Yüklü Video Sağlayıcıları" başlığı ve alt açıklaması **harf harf alt alta** yazılıyor (dikey şerit hâlinde), sayfa bozuk görünüyor.
2. Kurulu eklentiler **tek tek de silinemiyor**, **topluca da silinemiyor** (çöp kutusuna basınca hiçbir şey olmuyor).

## Teşhis

### 1) Bozuk başlık düzeni (kök neden: Row içinde ağırlıklı çocuğun sıfıra sıkışması)
`CloudstreamExtensionsTab.kt` içindeki künye satırı şu yapıdaydı:

```
Row(SpaceBetween) {
    Column(Modifier.weight(1f)) { Başlık  +  "165" rozeti  +  Açıklama }   // sol
    Row { "Tümünü Güncelle (43)" , "Eklenti Portalı" }                       // sağ
}
```

Compose `Row`, ağırlıklı (weight) çocuğu **ağırlıksız çocuklar ölçüldükten sonra kalan
genişlikle** ölçer. Büyük yazı tipi ölçeğinde sağdaki iki buton satırın tamamını kapladığı
için kalan genişlik ≈ 0 oluyor ve sol kolon sıfıra sıkışıyordu. `Text` sıfır/simge genişliğinde
ölçülünce her karakter ayrı satıra düşüyor → ekran görüntüsündeki dikey metin şeridi.
(Kullanıcı ekran görüntülerinde bu davranış hem başlıkta hem açıklama metninde görülüyor.)

### 2) Eklenti silinememesi (kök neden: bağlanmamış callback)
`CsInstalledPluginRow` içindeki çöp kutusu `onUninstallCsPlugin(plugin)` çağırıyor; bu callback
`KitsugiAddonsSettingsDialog` → `SettingsAddonsContent` → `AddonSettings` üzerinden geliyor.

`AppRootSettingsExtras.kt` içinde telefon Ayarlar ekranı için `AddonSettings(...)` nesnesi
kurulurken **`onUninstallCsPlugin` hiç geçilmiyordu**. Veri sınıfındaki varsayılan değer
`{}` (no-op) olduğu için tek tek silme **sessizce hiçbir şey yapmıyordu** — ne hata mesajı ne
kayıt silme. (Aynı callback `KitsugiStreamScreen` ve `MangaBrowseScreen` üzerinden açılan
diyaloglarda doğru bağlanmıştı; yalnızca Ayarlar yolu eksikti. Ekran görüntüsündeki başlık
"Eklentiler & Kaynaklar" = Ayarlar alt sayfası olduğu için kullanıcı tam da bu eksik yolu kullanıyordu.)

Ayrıca **toplu silme özelliği hiç yoktu**: kurulu eklenti listesinde yalnızca
"Tümünü Güncelle" ve "Eklenti Portalı" butonları bulunuyordu.

## Uygulama

### A) Başlık düzeni (CloudstreamExtensionTab.kt)
- Künye bloğu yeniden düzenlendi: başlık + sayaç rozeti **tam genişlik** bir satırda, açıklama
  altında, eylem butonları ise **ayrı ve sarılabilir** (`FlowRow`) bir satırda.
- Başlık `Modifier.weight(1f, fill = false)` ile ölçülür: kalan genişliği aşmaz (harf harf
  kaydırma olmaz) ama sayaç rozetini sağa da itmez.
- Butonlar artık dar ekranda/büyük yazı tipinde alt satıra sarılır; hiçbir koşulda başlığı
  sıkıştıramaz.

### B) Tek tek silme (kök neden düzeltmesi)
- `AppRootSettingsExtras.kt`: `onUninstallCsPlugin = { addonViewModel.uninstallCsPlugin(it) }`
  bağlandı (eksik olan satır). Artık çöp kutusuna basınca kayıt + `.cs3` dosyası silinir ve
  snackbar'da "🗑️ Eklenti kaldırıldı: X" görünür.
- `CloudstreamRepoRepository.uninstallCsPlugin` sağlamlaştırıldı ve `Boolean` döndürür:
  DB kaydı olmasa bile dosya artığı (`.cs3`, yarım kalan `.cs3.tmp`) temizlenir, dosya
  silinemezse log'a uyarı yazılır (eskiden `delete()` sonucu hiç kontrol edilmiyordu).
  Ortak temizlik `removeCsPluginArtifacts(pluginId)` içine alındı.

### C) Toplu silme (yeni özellik)
- `CloudstreamExtensionTab`: kurulu liste varsa başlıkta **"Tümünü Sil"** (kırmızı, yıkıcı)
  butonu görünür; basınca `KitsugiConfirmDialog` ile onay ister
  ("%1$d kurulu video sağlayıcı eklentisinin tamamı diskten silinecek…").
- Yeni zincir: `onUninstallAllCsPlugins` → `KitsugiAddonsSettingsDialog` → `AddonSettings`
  → `AddonViewModel.uninstallAllCsPlugins()` → `CloudstreamRepoRepository.uninstallAllCsPlugins()`.
- `uninstallAllCsPlugins()`: bellekten çıkarma + `.cs3`/`.tmp` dosya silme (tek hata diğerlerini
  engellemez), kayıtları tek işlemde silme (`CsPluginDao.deleteAll()`), yetim dosya taraması ve
  otomatik güncelleme "bekleme" kayıtlarının temizliği (`CsAutoUpdateBackoff.clearAll`).
- Silme sonrası "kaç güncelleme var" sayacı ağ isteği olmadan, son bilinen depo verisiyle
  yeniden hesaplanır (`AddonViewModel.refreshAvailableUpdatesCount`); kurulu eklenti yokken
  "güncelleme var" bandı ve "Tümünü Güncelle" butonu gösterilmez (bayat durum görünmez).

### D) Metinler
`values/strings.xml` ve `values-en/strings.xml`:
`eklenti_toplu_sil`, `eklenti_toplu_sil_baslik`, `eklenti_toplu_sil_mesaj`,
`eklenti_toplu_sil_sonuc`, `eklenti_toplu_sil_bos`.

## Değişen dosyalar
- `app/src/main/java/com/kitsugi/animelist/ui/components/addons/CloudstreamExtensionTab.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiAddonsSettingsDialog.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/addons/CsInstalledPluginRow.kt` (değişmedi, çağrı yeri zaten doğruydu)
- `app/src/main/java/com/kitsugi/animelist/ui/screens/settings/SettingsScreenParameters.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/settings/SettingsScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/AppRootSettingsExtras.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/stream/KitsugiStreamScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/manga/MangaBrowseScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/app/AddonViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/data/repository/CloudstreamRepoRepository.kt`
- `app/src/main/java/com/kitsugi/animelist/data/local/CsPluginDao.kt`
- `app/src/main/java/com/kitsugi/animelist/data/cloudstream/CsAutoUpdateBackoff.kt`
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-en/strings.xml`

## Doğrulama
- `git diff --check`: başarılı.
- Değişen Kotlin dosyalarında yorum/metin ayıklanarak ayraç dengesi kontrolü: başarılı.
- `values*/*.xml` dosyaları XML ayrıştırıcıdan geçti: başarılı.
- Room şeması değişmedi (yalnızca yeni `DELETE` sorgusu) → veritabanı sürümü artırılmadı.
- Gradle derlemesi denenemedi: ortamda JDK yok.

## Cihazda kontrol adımları
1. Ayarlar → Eklentiler & Kaynaklar → Video Sağlayıcıları → **Installed**: "Yüklü Video
   Sağlayıcıları" başlığı tek satırda, sayaç rozeti yanında, butonlar alt satırda görünmeli
   (yazı tipi ölçeği en büyükte de harf harf alt alta yazılmamalı).
2. Tek eklenti: satırdaki çöp kutusu → eklenti listeden anında düşmeli, dosya diskten
   silinmeli, "Eklenti kaldırıldı: X" mesajı çıkmalı. Uygulamayı yeniden başlatınca geri gelmemeli.
3. Toplu silme: **Tümünü Sil** → onay diyaloğu → "Evet/Sil" sonrası listedeki tüm eklentiler
   silinmeli, sayaç `0` olmalı, "güncelleme var" bandı kaybolmalı. Depoda "Kurulu" sekmesi
   boş durum kartını göstermeli.
4. Silme sonrası arama/oynatıcı ekranında eklenti listesi (Eklenti Portalı) güncel olmalı.
5. Aynı akış `KitsugiStreamScreen` içindeki "Eklenti & Akış Ayarları" diyaloğunda ve Manga
   ekranındaki diyalogda da çalışmalı (buralarda toplu silme butonu da görünür).

## Bilinen sınır (kapsam dışı)
- **Manga Kaynakları** sekmesinde tek tek silme çalışıyor (onay diyaloğu ile), ancak orada da
  bir "Tümünü Sil" yok. Manga kaynakları arasında Mihon/APK tabanlı ve gömülü kaynaklar
  bulunduğu için toplu silme ayrı bir tasarım gerektiriyor (hangileri silinebilir?).
- Android TV tarafında (`TvRootScreen` / `TvAddonsScreen`) tek tek silme zaten bağlıydı;
  TV için toplu silme eklenmedi.
