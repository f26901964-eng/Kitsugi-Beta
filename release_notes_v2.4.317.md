# Kitsugi Beta v2.4.317

## 🇹🇷 Türkçe

### 🏷️ Bangumi "Listem" Başlık Dili Düzeltmesi (İngilizce/Romaji)

Bu sürüm, başlık dili **İngilizce** seçili olmasına rağmen Bangumi listesinin yeni veya eski fark etmeksizin Japonca/Çince adlarla görünmesi sorununu 3 kök neden üzerinden tamamen çözer. Ayrıntılı analiz: `KOK_NEDEN_RAPORU_BANGUMI_LISTEM_BASLIK_DILI_INGILIZCEYE_UYMUYOR_2026-10-20.md`.

- **Aktarım Sonrası Anında Tamamlama & Dil Değişim Tetikleyicisi (`AuthViewModel`, `MyListScreen`, `MyListViewModel`):**
  - Bangumi içe aktarımı (ve çapraz senkron doğrulama aktarımı) biter bitmez Latin/İngilizce başlık tamamlama taraması otomatik olarak başlatılır (`AuthViewModel`).
  - Önceden tarama yalnızca `MyListViewModel` ilk oluşturulduğunda çalışıyordu; ekran açıkken aktarılan kayıtlar (ör. 717 kayıt) uygulama kapatılıp açılana kadar taranamıyordu.
  - Başlık dili ayarı değiştirildiğinde de tarama otomatik olarak yeniden tetiklenir (`MyListScreen`, `MyListViewModel`).
- **Kuyruk Tıkanması Çözüldü & Kademeli Tamamlama Motoru (`BangumiLibraryTitleHydrator`):**
  - Tamamlama motoru iki kademeli yapıya geçirildi: önce gösterimi Japonca/Çinceye düşüren "Latin eksik" kayıtlar, ardından (yalnızca İngilizce seçiliyse) ayrı İngilizce ad zenginleştirmesi.
  - İşlem 60'lık dilimler halinde listenin **sonuna kadar** (960 satır kapasite) kesintisiz ilerler; listenin başındaki "yalnızca romaji bulunabilen" kayıtların erken çıkışla tüm kuyruğu tıkaması engellendi.
  - Ağsız bir yerel önbellek geçişi (`cacheOnly`) çalışır; başlıklar ekranda aşamalı ve akıcı bir şekilde İngilizceye döner.
- **Çözülmüş Başlıkların CJK ile Ezilmesi Engellendi (`MediaEntryRepository` → `preserveResolvedTitles`):**
  - `smartImport` güncelleme akışına başlık koruma kuralı eklendi. Bangumi koleksiyon ucu yalnızca özgün adı döndürdüğü için, her yeniden senkron tamamlanmış İngilizce/romaji adları Japonca/Çince adla eziyordu; artık başlıklarda yalnızca yükseltmeye izin verilir ve özgün ad (`titleJapanese`) korunur.
- **Aktarım Anında Kalıcı Önbellek Kullanımı (`BangumiImportManager`):**
  - İçe aktarılan her Bangumi kaydı, daha önce çözülmüş Latin adları tutan kalıcı önbellekle anında zenginleştirilir; bilinen yapımlar ilk çizimden itibaren doğru dilde gelir.
- **Gösterim Yedeği Düzeltmesi (`PreferenceHelpers`):**
  - İngilizce seçiliyken romaji yedeği gösteriliyorsa önbellekteki ayrı İngilizce ad öncelik kazanır.

### 🧪 Birim Testleri
- `BangumiLibraryTitleHydratorTest`: Kademe önceliği, dile göre tamamlama tanımı, büyük kütüphanede kırpma olmaması ve yer tutucu başlıklar doğrulandı.
- `MediaEntryRepositoryTitlePreservationTest`: Yeniden aktarımda çözülmüş başlıkların korunması doğrulandı.
- `BangumiImportTitlesTest`: Aktarım anında önbellek başlıklarının atanması doğrulandı.
- `BangumiDisplayTitleLanguageTest`: İngilizce/Romaji/Ana dil gösterim sözleşmesi doğrulandı.

---

## 🇬🇧 English

### 🏷️ Bangumi "My List" Title Language Fix (English / Romaji)

This release addresses the issue where titles in the Bangumi list were displayed in Japanese or Chinese regardless of the preferred title language being set to **English**. Detailed root-cause breakdown in `KOK_NEDEN_RAPORU_BANGUMI_LISTEM_BASLIK_DILI_INGILIZCEYE_UYMUYOR_2026-10-20.md`.

- **Immediate Post-Import Hydration & Preference Change Triggers (`AuthViewModel`, `MyListScreen`, `MyListViewModel`):**
  - Triggered Latin/English title hydration immediately upon Bangumi import completion (and cross-sync verification), eliminating the need to restart the app to see translated titles.
  - Added reactive re-hydration triggers whenever the preferred title language is changed in settings.
- **Unblocked Hydration Pipeline & Tiered Progress (`BangumiLibraryTitleHydrator`):**
  - Structured hydration into two stages: primary Latin fallback resolution followed by English-specific title enrichment.
  - Paginates through 60-item chunks across the entire library (up to 960 rows), preventing romaji-only entries from exhausting the budget and prematurely terminating the hydrator.
  - Runs a fast local cache-only scan first, ensuring progressive, instant UI updates.
- **Preserve Resolved Titles Against CJK Overwrites (`MediaEntryRepository`):**
  - Implemented `preserveResolvedTitles` in `smartImport`. Bangumi collection endpoints return only native names, which previously overwrote resolved English/romaji titles on each sync; updates now retain resolved translations and native variants.
- **Import-Time Persistent Cache Enrichment (`BangumiImportManager`):**
  - Enriched freshly imported Bangumi entries directly with cached Latin titles, ensuring correct titles on initial render.
- **Title Fallback Hierarchy (`PreferenceHelpers`):**
  - Ensured that when English is preferred and a romaji fallback is active, any resolved English title takes precedence.

### 🧪 Unit Tests
- `BangumiLibraryTitleHydratorTest`: Verified tiered stages, language criteria, and large library coverage.
- `MediaEntryRepositoryTitlePreservationTest`: Verified title preservation during repeated sync imports.
- `BangumiImportTitlesTest`: Verified cache application during direct import.
- `BangumiDisplayTitleLanguageTest`: Verified English/Romaji/Native display contracts.
