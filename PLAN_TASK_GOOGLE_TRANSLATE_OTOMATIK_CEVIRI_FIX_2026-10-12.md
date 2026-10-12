# PLAN / TASK — Google Translate Otomatik Çeviri Hattı Kapsamlı Düzeltmesi

**Tarih:** 2026-10-12
**Dal:** `arena/388beb11-kitsugi-beta`
**Şikâyet:** Uygulama içi Google otomatik çeviri (`autoTranslateEnabled`) açık olduğunda detay sayfası açıklaması (synopsis), karakter biyografisi, ekip biyografisi, stüdyo açıklaması, spoiler metinleri ve inceleme/tartışma/aktivite pencereleri gibi otomatik çevirinin etkilediği tüm alanlarda bazen çevirip bazen hiç çevirmemesi veya arka planda sessizce başarısız olması.

---

## 1. Kök Nedenler

| # | Kök Neden | Belirti |
|---|---|---|
| KN-1 | **Tek uç nokta (`client=gtx`) + `RetryInterceptor` tıkanması:** `TranslationManager` yalnızca tek bir Google Translate uç noktası kullanıyordu. Hız sınırına (HTTP 429/403) takıldığında `RetryInterceptor` aynı engelli URL'yi bekleyerek tekrar deniyor, yedek uç noktaya geçemiyordu. | Uzun veya art arda açılan sayfalarda çevirinin sessizce durması |
| KN-2 | **Bloklayıcı `.execute()` ve `CancellationException` yutulması:** İptal edilen coroutine'ler (`withTimeoutOrNull(3000)` vb.) `requestGate` semafor iznini serbest bırakmıyor, sonraki çevirileri kilitliyordu. | Bir sayfa/Shikimori isteği zaman aşımına uğradıktan sonra diğer sayfaların çevrilmemesi |
| KN-3 | **Jikan/MAL karakter ve ekip sayfalarında koşulsuz Shikimori çağrısı:** Jikan biyografiyi zaten getirdiği halde her seferinde `KitsugiShikimoriClient` çağrılıp 25+ adet Rusça isim ve biyografi Google Translate'e gönderiliyor, ardından `mergeWith` içinde atılıyordu. | Karakter/ekip sayfası açılır açılmaz Google Translate'in 429 hız sınırına girmesi |
| KN-4 | **Çevirinin galeri ve ikincil ağ işlerinin (10–30 sn) arkasına bekletilmesi:** `CharacterDetailViewModel` ve `StaffDetailViewModel` 5 kaynaktan görsel toplayan `buildCharacterGallery`/`buildStaffGallery` bitmeden çeviriyi başlatmıyordu. `ApiResultDetailViewModel` ise `resolveHeroArtwork` ve `enrichDetail` bitmeden çeviriyi başlatmıyordu. | Sayfa açıldığı halde açıklamanın/biyografinin uzun süre veya hiç çevrilmemesi |
| KN-5 | **CJK (Çince/Japonca) cümle bölme hatası (`splitOversizedLine`):** `。！？；…` karakterlerinden sonra boşluk (`next.isWhitespace()`) şartı arandığı için boşluksuz CJK metinleri cümlelerden bölünemiyor, devasa bloklar halinde gönderiliyordu. | Bangumi ve CJK kaynaklı uzun açıklamaların çevrilememesi |
| KN-6 | **Markdown yer tutucularının (`START`/`END`) çevrilmesi ve spoiler (`~!...!~`) bozulması:** `⟦L_0_START⟧` içindeki İngilizce kelimeler Google Translate tarafından `BAŞLANGIÇ`/`SON` olarak çevriliyor, `unmasker` eşleşemiyordu. `~!...!~` arasına boşluk (`~ !`) girdiği için spoiler yapısı kırılıyordu. | Çevrilen biyografi/incelemelerde bozuk yer tutucu metinler, kırık linkler ve çalışmayan spoiler'lar |
| KN-7 | **`isLikelyTurkish` yanlış pozitifleri:** CJK/Kiril metinlerde İngilizce kelime sayısı `0` olduğu için başına Türkçe etiket (`"Merkez: ..."`, `"Ülke: ..."`, Türkçe meslek adı) eklenen metinler Türkçe sanılıp çevrilmeden atlanıyordu. | Bangumi ve TMDB stüdyo/kişi açıklamalarının çevrilmeden kalması |
| KN-8 | **TMDB kişi/karakter/ekip biyografisinde `en-US` yedeğinin olmaması:** `TmdbCreditsClient` yalnızca `language=tr-TR` ile istek atıyor, Türkçe biyografi boş döndüğünde İngilizce biyografiye düşmüyordu. | TMDB oyuncu/karakter/ekip sayfalarının %95'inde biyografinin boş görünmesi |
| KN-9 | **AniList ve Shikimori stüdyolarında açıklamanın (`about`) `null` olması:** Stüdyo açıklaması Jikan/MAL üzerinden tamamlanmadığı için çevrilecek metin hiç gelmiyordu. | Stüdyo detay sayfasında açıklama kartının hiç çıkmaması |
| KN-10 | **`MediaEntryDetailViewModel` yarış durumu & sayfa içi ayar tepkisizliği:** `fetchDetail` ve `fetchSynopsis` birbirinin çevirisini eziyor, `onPartial` UI'ya yansımıyordu; ayrıca detay sayfasındayken otomatik çeviri ayarı değiştirildiğinde ViewModel'ler `settingsFlow`'u dinlemiyordu. | Kütüphane detay sayfasında ve ayar aç/kapa sonrasında çevirinin çalışmaması |

---

## 2. Tamamlanan Görevler

- [x] **T1 — `TranslationManager` Çoklu Uç Nokta & İptal Edilebilir Ağ Katmanı:**
  - `RetryInterceptor`'dan arındırılmış özel `translationHttpClient` ve 4 kademeli yedekli uç nokta zinciri (`client=gtx&dj=1`, `translate.google.com/.../client=at&dj=1`, `client=dict-chrome-ex&dj=1`, `clients5.google.com/translate_a/t`) eklendi.
  - `executeCancellable()` (`suspendCancellableCoroutine`) ile iptal anında HTTP çağrısının ve semafor izninin (`Semaphore(4)`) serbest bırakılması sağlandı.
  - Parça bazlı (`chunkHash`) Room önbelleği eklendi; yarıda kesilen uzun çevirilerde çevrilmiş parçalar korunuyor.
- [x] **T2 — CJK Bölme, Sembolik Markdown/Spoiler Maskeleme ve `isLikelyTurkish` Düzeltmesi:**
  - `splitOversizedLine` içinde CJK noktalama işaretlerinin boşluk gerektirmeden cümle sonu sayılması sağlandı (`MAX_CHUNK_CHARS = 1400`).
  - Harf içermeyen sembolik yer tutucular (`⟪#0⟫`, `⟪@0⟫`, `⟪!9990⟫`) ile Markdown linkleri, `img(...)` medya etiketleri ve `~!...!~` spoiler sınırları korundu.
  - `isLikelyTurkish` içine CJK/Kiril alfabe kontrolü eklendi.
- [x] **T3 — Karakter, Ekip, Stüdyo ve TMDB Veri İstemcileri:**
  - `KitsugiCharacterClient` ve `KitsugiStaffClient` içinde Jikan başarılıysa gereksiz Shikimori çağrısı atlandı; AniList biyografisi boşsa Jikan yedeği eklendi.
  - `KitsugiShikimoriClient.translateCyrillicNames` içinde Kiril isimler 12'lik gruplar halinde tek istekte toplu çevrilecek şekilde optimize edildi.
  - `TmdbCreditsClient.fetchPersonCharacterDetail` ve `fetchPersonStaffDetail` içine `language=tr-TR` boş döndüğünde `language=en-US` biyografi yedeği eklendi.
  - `KitsugiStudioClient` içine `enrichStudioMetaFromJikan` eklendi; `KitsugiDetailClient.fetchSynopsis` içine `"tmdb"` dalı eklendi.
- [x] **T4 — 5 Detay ViewModel'i ve `DetailCache` Senkronizasyonu:**
  - `ApiResultDetailViewModel`, `MediaEntryDetailViewModel`, `CharacterDetailViewModel`, `StaffDetailViewModel`, `StudioDetailViewModel` içinde çeviriler ikincil ağ/galeri işlerini beklemeden **hemen** başlatılıyor ve `settingsDataStore.settingsFlow` dinlenerek ayar değişimine anında tepki veriliyor.
  - `DetailCache` çeviri anahtarlarına hedef dil (`targetLang`) ve `identity` ayrıştırıcısı eklendi.
- [x] **T5 — Spoiler, İnceleme, Tartışma, Aktivite ve Önizleme Pencereleri:**
  - `InteractiveSpoilerSheet`, `KitsugiReviewDetailBottomSheet`, `KitsugiTopicDetailBottomSheet`, `KitsugiActivityDetailBottomSheet`, TV diyalogları ve 3 önizleme diyaloğu (`KitsugiApiSearchDialog`, `KitsugiHeroPreviewDialog`, `KitsugiMediaEntryDetailDialog`) otomatik çeviri ve parçalı akış (`onPartial`) ile uyumlu hale getirildi.

---

## 3. Değişen Dosyalar (25 Dosya)

| Dosya | Açıklama |
|---|---|
| `app/src/main/java/com/kitsugi/animelist/data/local/TranslationManager.kt` | 4 uç noktalı yedekli çeviri, `executeCancellable`, parça bazlı Room önbelleği, CJK cümle bölme, harfsiz Markdown/spoiler maskeleme, `isLikelyTurkish` düzeltmesi |
| `app/src/main/java/com/kitsugi/animelist/utils/KitsugiTranslateUtils.kt` | `KitsugiTranslatePrefs.isInitialized` ve varsayılan hedef dil (`"tr"`) güvencesi |
| `app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt` | `defaultTranslateTarget(appLanguage)` varsayılan hedef dil çözümlemesi |
| `app/src/main/java/com/kitsugi/animelist/data/remote/DetailCache.kt` | Hedef dil (`targetLang`) ve stüdyo/kişi `identity` duyarlı önbellek anahtarları |
| `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiCharacterClient.kt` | Gereksiz Shikimori çağrısının önlenmesi + AniList/TMDB karakterleri için Jikan biyografi yedeği |
| `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiStaffClient.kt` | Gereksiz Shikimori çağrısının önlenmesi + AniList/TMDB ekip üyeleri için biyografi yedeği |
| `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiShikimoriClient.kt` | `translateCyrillicNames` toplu (batch) çeviri optimizasyonu |
| `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiStudioClient.kt` | `enrichStudioMetaFromJikan` ile eksik stüdyo açıklamalarının tamamlanması + Bangumi `about` düzeltmesi |
| `app/src/main/java/com/kitsugi/animelist/data/remote/TmdbCreditsClient.kt` | TMDB kişi/karakter/ekip biyografilerinde `en-US` yedek sorgusu |
| `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiDetailClient.kt` | `fetchSynopsis` içine `"tmdb"` dalı eklenmesi |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailViewModel.kt` | Aşama 1'de anında çeviri başlatma + `settingsFlow` dinleyicisi |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/MediaEntryDetailViewModel.kt` | `applyAndTranslateSynopsis` ile yarış durumu düzeltmesi, `onPartial` akışı + `settingsFlow` dinleyicisi |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/CharacterDetailViewModel.kt` | Galeri beklenmeden anında biyografi çevirisi + `settingsFlow` dinleyicisi |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/StaffDetailViewModel.kt` | Galeri beklenmeden anında biyografi çevirisi + `settingsFlow` dinleyicisi |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/StudioDetailViewModel.kt` | `triggerAboutTranslationIfNeeded`, `onPartial` akışı, `studioCacheIdentity` + `settingsFlow` dinleyicisi |
| `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiMarkdownText.kt` | `InteractiveSpoilerSheet` içinde `autoTranslateEnabled` desteği ve `onPartial` akışı |
| `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiReviewDetailBottomSheet.kt` | Otomatik çeviri sekmesi, `onPartial` akışı ve hatalı çeviri kilidi düzeltmesi |
| `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiTopicDetailBottomSheet.kt` | Otomatik çeviri sekmesi, `onPartial` akışı ve hatalı çeviri kilidi düzeltmesi |
| `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiActivityDetailBottomSheet.kt` | Otomatik çeviri sekmesi, `onPartial` akışı ve hatalı çeviri kilidi düzeltmesi |
| `app/src/main/java/com/kitsugi/animelist/ui/tv/detail/TvReviewDetailDialog.kt` | TV inceleme diyaloğu otomatik çeviri + `onPartial` akışı |
| `app/src/main/java/com/kitsugi/animelist/ui/tv/detail/TvTopicDetailDialog.kt` | TV tartışma diyaloğu otomatik çeviri + alt yorum (recursive) çevirisi + `onPartial` akışı |
| `app/src/main/java/com/kitsugi/animelist/ui/tv/detail/TvActivityDetailDialog.kt` | TV aktivite diyaloğu otomatik çeviri + `onPartial` akışı |
| `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiApiSearchDialog.kt` | Arama önizleme diyaloğunda otomatik özet çevirisi |
| `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiHeroPreviewDialog.kt` | Vitrin önizleme diyaloğunda otomatik özet çevirisi |
| `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiMediaEntryDetailDialog.kt` | Kütüphane önizleme diyaloğunda otomatik özet çevirisi |
