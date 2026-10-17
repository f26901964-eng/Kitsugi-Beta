# PLAN / TASK — "İzle" Popup: Eklenti ve Stremio Akışı Düzeltmeleri

**Tarih:** 2026-10-17
**Dal:** `arena/5e68da08-kitsugi-beta` (temel: `41bd9a2`, commit edilmemiş çalışma ağacı)
**Şikâyet:** "İzle" popup'ında kurulu Türkçe CloudStream eklentilerinin çoğu anında hata veriyor
(sorguya girmeden), bazıları hiç video verisi döndürmüyor. Stremio addon'ları da IMDb ile beslenmiyor.

## 1. Bulgular (kod kanıtı)

| # | Bulgu | Dosya | Durum |
|---|---|---|---|
| F1 | Stremio istek türü, addon'un ilan ettiği türle değil ham içerik türüyle yapılıyordu (ör. yalnızca `anime` ilan eden addon `/stream/series/` ile sorgulanıyordu) | `AddonStreamRepository`, `StreamViewModel`, `StremioStreamRouting` | ✅ Düzeltildi |
| F2 | Tam biçimli `stream` kaynağındaki `types`/`idPrefixes` yok sayılıyordu | `AddonStreamClient.fetchManifest` | ✅ Düzeltildi |
| F3 | Bare `kitsu` öneki (Torrentio tarzı) `kitsu:` ile eşleşmiyordu | `StremioStreamRouting` | ✅ Düzeltildi |
| F4 | Detay sayfasının bildiği IMDb ID'si akış ekranına taşınmıyordu | `ApiResultDetailPage`, `KitsugiStreamActivity`, `KitsugiStreamScreen`, `StreamViewModel` | ✅ Düzeltildi (TV ekranı hariç) |
| F5 | ARM/TMDB IMDb bulamazsa IMDb yoktu | `KitsugiIdResolver` (Wikidata P345 yedeği, yalnızca akış yolunda) | ✅ Eklendi |
| F6 | Yeniden deneme (`rerunCsPlugin`) ham `stableId`'yi MAL olarak CS'ye gönderiyordu ve çözülmüş kimlikleri iletmiyordu | `StreamViewModel` | ✅ Düzeltildi |
| F7 | Numarasız film sorgusunda ("Avatar" 2009) yalnızca devam filmi ("Avatar 2 izle") varsa yanlış film seçilebiliyordu | `CsTitleMatcher` | ✅ Düzeltildi + regresyon testi |
| F8 | Yükleme başarısızlığının gerçek nedeni (`CsPluginLoader.takeLastLoadError`) hiç okunmuyordu; kart jenerik "Eklenti yüklenemedi (DEX/manifest hatası)" gösteriyordu → hata "anında" geliyor görünüyordu | `StreamViewModel` (3 yol) | ✅ Gerçek neden gösteriliyor |
| F9 | Yeniden denemede yükleme başarısızlığı "tür desteklemiyor" mesajına düşüyordu | `StreamViewModel.rerunCsPlugin` | ✅ Düzeltildi |
| F10 | Kullanılmayan eski `supportsStreamResource` kopyası (`StreamHelpers.kt`) | `StreamHelpers.kt` | ✅ Silindi |

## 2. Açık konular (henüz kanıtlanmadı)

- **Hemen hepsinde anında hata veren eklentilerin ortak kök nedeni:** Yükleyici (`CsPluginLoader.loadExtension`)
  `.cs3` dosyasını/DEX'i yükleyemiyor ya da `load()` hata veriyor. Hangi sebep olduğu, kart artık gerçek
  hatayı gösterdiği için cihazdan okunabilecek. Sandbox'ta eklenti çalıştırılamadığından doğrulanamadı.
- **Canlı arama/çıkarma zinciri** (CF/WAF, zaman aşımı 75 sn / loadLinks 25 sn, embed çözümü) cihaz
  üzerinde test edilmedi.
- `CsStreamRunner.kt` satır 2090–4140 tam olarak okunmadı.
- TV stream ekranına (`TvStreamScreen`, `TvStreamArgs`) IMDb geçişi yapılmadı.

## 3. Doğrulama durumu

- **Gradle / Android build: çalıştırılmadı** (sandbox'ta SDK/Maven yok).
- Değişen Kotlin dosyaları: sözdizimi kontrolü (Kotlin 2.4.21 parser) temiz.
- Saf testler (stub'larla, orijinal test dosyaları değiştirilmeden):
  - `StremioStreamRoutingTest`: 18/18 geçti.
  - `CsTitleMatcherTest`: 41/41 geçti (40 eski + 1 yeni: `unnumberedMovieQueryNeverAcceptsOnlySequelWithoutYearProof`).
- `WikidataImdbParseTest` (3 test): yazıldı, `org.json` gerektirdiği için yalnızca Gradle'da çalışır (çalıştırılmadı).
- Realistik başlık senaryoları (19 adet): eşleştirici beklenen şekilde davrandı.

## 4. Sonraki adımlar

1. `./gradlew :app:testDebugUnitTest` ile tüm birim testlerini çalıştır (özellikle yeni 3 test dosyası).
2. Uygulamada bir "izle" denemesi yap; hata veren her eklentinin kartındaki `Eklenti yüklenemedi: ...` metnini not al.
3. Ortak hata metni varsa (ör. `NoClassDefFoundError`, `CS3 host runtime class is not visible`) kök nedeni o
   sınıfa göre düzelt; eklenti başına farklıysa tek tek ele al.
4. Eklenti Tanı ekranındaki CsTrace çıktısını, bir film/dizi adıyla birlikte paylaş.

## 5. Değişen dosyalar

Kaynak: `app/src/main/java/com/kitsugi/animelist/...` (ayrıntı için zip içindeki `git-diff.patch` dosyasına bak).
Yeni dosyalar: `StremioStreamRouting.kt`, `StremioAddonRoutes.kt`, `StremioStreamRoutingTest.kt`,
`WikidataImdbParseTest.kt`. Bu plan dosyası da dahildir.
