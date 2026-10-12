# TASK — Çeviri Butonlarında Yüklenme (Spinner) Animasyonu

**Tarih:** 2026-10-12
**Dal:** `arena/d67271b4-kitsugi-beta`
**İstek:** Otomatik Google çevirisinin dokunduğu tüm metin alanlarında (açıklama/synopsis, karakter biyografisi, ekip biyografisi, stüdyo "Hakkında", spoiler, inceleme/tartışma/aktivite ve liste pencereleri) bulunan **çeviri butonlarının etrafında**, detay sayfalarındaki **galeri butonuyla birebir aynı** yuvarlak yüklenme animasyonu görünsün. Böylece otomatik çeviri açıkken metnin gerçekten çevrildiği (ve hâlâ çevrildiği) anlaşılabilsin.

---

## 1. Çözümün Özü

1. **Ortak bileşen:** Galeri butonunun animasyonu (`KitsugiGalleryIconButton`) referans alınarak `KitsugiTranslateIconButton` oluşturuldu:
   - 700 ms'de bir nabız gibi yanıp sönen ikon (`alpha` 0.30 → 1.0, `RepeatMode.Reverse`)
   - İkonun etrafında dönen `CircularProgressIndicator` halkası (accent renkli)
   - Yüklenirken buton dokunulamaz (`enabled = !isLoading`) — galeri butonuyla aynı davranış
   - `iconSize` / `ringSize` ile küçük butonlara (28 dp, 32 dp, 36 dp) da uyum sağlar
2. **Tek kaynaktan durum:** `LocalKitsugiTranslationLoading` (CompositionLocal). Detay sayfalarının kökü mevcut `CompositionLocalProvider` bloğuna ikinci değer olarak eklenir:
   ```kotlin
   CompositionLocalProvider(
       LocalKitsugiGalleryLoading provides galleryLoading,
       LocalKitsugiTranslationLoading provides translationLoading
   ) { ... }
   ```
   Sayfa içindeki tüm çeviri butonları bu değeri kendiliğinden kullanır; pencere (sheet/dialog) içindeki butonlar ise **kendi yerel durumlarını** açıkça geçirerek sayfa durumunu geçersiz kılar (yanlış yerde halka dönmesin).
3. **ViewModel'lerde gerçek "çeviri sürüyor" durumu:** 5 detay ViewModel'ine `translationLoading: StateFlow<Boolean>` eklendi.
   - Çeviri işi başlarken `true`, iş biterken/iptal edilirken `false`.
   - **Kısmi (stream) sonuçlar geldiğinde kapanmaz**: metin parça parça görünürken halka dönmeye devam eder, çeviri tamamen bitince durur.
   - Nesil sayacı (`translationGeneration`) sayesinde eski/iptal edilmiş işlerin yeni işin yüklenme durumunu kapatması engellenir.
   - Auto-translate kapalıyken hiç tetiklenmez → gereksiz halka yanıp sönmesi olmaz.

---

## 2. Değişen Dosyalar (28)

| Dosya | Değişiklik |
|---|---|
| `ui/components/KitsugiTranslateIconButton.kt` | **YENİ** — ortak çeviri butonu + `LocalKitsugiTranslationLoading` |
| `ui/screens/detail/ApiResultDetailViewModel.kt` | `translationLoading` + `startSynopsisTranslation` / `cancelSynopsisTranslation` |
| `ui/screens/detail/MediaEntryDetailViewModel.kt` | `translationLoading` + `applyAndTranslateSynopsis` yeniden düzenlendi |
| `ui/screens/detail/CharacterDetailViewModel.kt` | `translationLoading` + `cancelBioTranslation` |
| `ui/screens/detail/StaffDetailViewModel.kt` | `translationLoading` + `cancelBioTranslation` |
| `ui/screens/detail/StudioDetailViewModel.kt` | `translationLoading` + `cancelAboutTranslation` |
| `ApiResultDetailPage.kt` / `MediaEntryDetailPage.kt` / `CharacterDetailPage.kt` / `StaffDetailPage.kt` / `StudioDetailPage.kt` | Sayfa kökünde `LocalKitsugiTranslationLoading` sağlanıyor |
| `ApiResultDetailComponents.kt` (`ApiSynopsisCard`) | Çeviri butonu ortak bileşene geçti |
| `KitsugiDetailInfoSection.kt` (`DetailSynopsisCard`) | Aynı — medya detayı + stüdyo "Hakkında" kartı |
| `CharacterDetailComponents.kt` / `StaffDetailComponents.kt` | Biyografi kartındaki çeviri butonu |
| `KitsugiMarkdownText.kt` (`InteractiveSpoilerSheet`) | Spoiler çevirisi sürerken halka; kısmi sonuçta animasyon erken durmuyor |
| `KitsugiReviewDetailBottomSheet.kt` | İnceleme penceresi: `isTranslating` düzeltildi + halka |
| `KitsugiTopicDetailBottomSheet.kt` | Konu penceresi: `activeTranslations` sayacı + başlık ve yorum butonları |
| `KitsugiForumCommentCard.kt` | Yorum/alt yorum çeviri butonları (`isTranslating` parametresi) |
| `KitsugiActivityDetailBottomSheet.kt` | Aktivite + yanıt çeviri butonları |
| `KitsugiAllReviewsBottomSheet.kt` / `KitsugiAllTopicsBottomSheet.kt` | Liste pencereleri: `activeTranslations` sayacı + buton |
| `KitsugiAllActivitiesBottomSheet.kt` | Sadece buton (burada uygulama içi çeviri yok, harici çeviri açılır → halka gösterilmez) |
| `AniListInfoTab.kt`, `UserProfileInfoTab.kt`, `KitsuProfileContent.kt`, `ShikimoriProfileContent.kt`, `SimklProfileContent.kt` | Profil "Hakkında/Biyografi" butonları ortak bileşene geçti (profilde uygulama içi otomatik çeviri yok) |

---

## 3. Beklenen Davranış

| Durum | Sonuç |
|---|---|
| Detay sayfası açıldı, otomatik çeviri açık, çeviri yok | Açıklama/biyografi kartındaki çeviri ikonu **halka + nabız** ile döner, çeviri bitince durur |
| Çeviri önbellekte hazır (DetailCache/Room) | Halka görünmez, çevrilmiş metin anında gelir |
| Çeviri uzun metinde parça parça akıyor | Metin hemen görünür, **halka çeviri tamamen bitene kadar dönmeye devam eder** |
| Otomatik çeviri kapalı | Halka hiç görünmez (elle çeviri butonu harici çeviri uygulamasını açar) |
| Ayarlardan otomatik çeviri sayfa açıkken açıldı | Çeviri başlar, halka döner |
| Ayarlardan kapatıldı | Çalışan çeviri iptal edilir, halka hemen durur |

---

## 4. Notlar / Kapsam Dışı

- **Çeviri butonunun tıklama davranışı değiştirilmedi**: butonlar hâlâ "harici çeviri uygulamasını aç" işlevini görüyor (mevcut davranış korundu). Animasyon, sayfanın **uygulama içi otomatik çevirisi** sürerken gösterilir. İstenirse bu butonlar uygulama içi çeviriyi yeniden tetikleyecek şekilde bağlanabilir (`viewModel.translateSynopsis()` vb. hazır).
- Yüklenirken buton dokunulamaz (galeri butonuyla aynı) — çeviri sürerken tekrar tetikleme olmaz.
- `KitsugiAllActivitiesBottomSheet` ve profil sayfalarında uygulama içi çeviri hattı olmadığı için butonlar görsel olarak ortak bileşene taşındı, halka gösterilmez.
- **Bu ortamda derleme yapılamadı** (JDK/Android SDK yok, ağ yalnızca GitHub/npm/PyPI). Değişiklikler statik inceleme + parantez/blok denge denetimiyle doğrulandı; `./gradlew assembleDebug` ile derlenip cihazda denenmesi gerekiyor.
- Sürüm numarası **değiştirilmedi** (release akışında sizin bump commit'inizle artırılmalı).
