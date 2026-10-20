# Kitsugi Beta v2.4.315

## 🇹🇷 Türkçe

### 🏷️ Bangumi "Listem" Başlık Dili Düzeltmesi (İngilizce/Romaji)

Bu sürüm, başlık dili **İngilizce** seçili olmasına rağmen Bangumi listesinin yeni/eski fark etmeksizin Japonca/Çince adlarla görünmesi sorununu 3 kök neden üzerinden çözer. Ayrıntılı analiz: `KOK_NEDEN_RAPORU_BANGUMI_LISTEM_BASLIK_DILI_INGILIZCEYE_UYMUYOR_2026-10-20.md`.

- **Aktarım Sonrası Anında Tamamlama:** Bangumi içe aktarımı (ve çapraz senkron doğrulama aktarımı) biter bitmez Latin/İngilizce başlık tamamlama taraması başlar (`AuthViewModel.kt`). Eskiden tarama yalnızca Listem ekranı ilk yaratıldığında çalışıyordu; Listem açıkken aktarılan kayıtlar (örn. 717 kayıt) uygulama kapanıp açılana dek Japonca/Çince kalıyordu. Başlık dili ayarı değiştiğinde de tarama yeniden tetiklenir (`MyListScreen.kt`, `MyListViewModel.kt`).
- **Tıkanmayan Tamamlama Motoru:** `BangumiLibraryTitleHydrator` baştan yazıldı. Satırlar artık iki kademede işlenir: önce gösterimi Japonca/Çinceye düşüren "Latin eksik" kayıtlar, sonra (yalnızca İngilizce seçiliyse) ayrı İngilizce ad zenginleştirmesi. İşlem 60'lık dilimler halinde listenin **sonuna kadar** ilerler; eskiden listenin başındaki "yalnızca romaji bulunabilen" kayıtlar tüm bütçeyi tüketip değişim üretmediğinde tarama yarıda bırakılıyor, yüzlerce kayıt hiç denenmeden kalıyordu. Önce ağsız bir önbellek geçişi çalışır; başlıklar ekranda aşamalı olarak seçili dile döner.
- **Çözülmüş Başlıklar Artık Ezilmez:** `smartImport` güncelleme koluna başlık koruma kuralı eklendi (`MediaEntryRepository.kt` → `preserveResolvedTitles`). Bangumi koleksiyon ucu yalnızca özgün adı döndürdüğü için, her yeniden senkron tamamlanmış İngilizce/romaji adları Japonca/Çince adla eziyordu; artık başlıklarda yalnızca yükseltmeye izin verilir, özgün ad da kaybolmaz.
- **Aktarım Anında Önbellek:** İçe aktarılan her Bangumi satırı, daha önce çözülmüş Latin adları tutan kalıcı önbellekle anında zenginleştirilir (`BangumiImportManager.kt`); bilinen kayıtlar ilk çizimden itibaren doğru dilde gelir.
- **Gösterim Yedeği Düzeltmesi:** İngilizce seçiliyken romaji yedeği gösteriliyorsa önbellekteki ayrı İngilizce ad artık kazanır (`PreferenceHelpers.kt`).

### 🧪 Birim Testleri

- `BangumiLibraryTitleHydratorTest` (genişletildi): kademe önceliği, dile göre tamamlama tanımı, büyük kütüphanede kırpma olmaması, yer tutucu başlıklar.
- `MediaEntryRepositoryTitlePreservationTest` (yeni): yeniden aktarımda çözülmüş başlıkların korunması.
- `BangumiImportTitlesTest` (yeni): aktarım anında önbellek başlıkları.
- `BangumiDisplayTitleLanguageTest` (yeni): İng/Romaji/Ana dil gösterim sözleşmesi.

---

## 🇬🇧 English

### 🏷️ Bangumi "My List" Title-Language Fix (English/Romaji)

This release fixes Bangumi library entries showing Japanese/Chinese titles despite the title language being set to **English** — for both newly imported and existing rows — by addressing 3 root causes. Full analysis: `KOK_NEDEN_RAPORU_BANGUMI_LISTEM_BASLIK_DILI_INGILIZCEYE_UYMUYOR_2026-10-20.md`.

- **Immediate Post-Import Completion:** Latin/English title completion now starts as soon as a Bangumi import (or cross-sync verify import) finishes (`AuthViewModel.kt`). Previously it only ran when the My List view model was created, so rows imported while My List was already open (e.g. 717 entries) stayed in Japanese/Chinese until the app restarted. Changing the title-language setting also re-triggers completion (`MyListScreen.kt`, `MyListViewModel.kt`).
- **Non-Stalling Completion Engine:** `BangumiLibraryTitleHydrator` was rewritten with two tiers — rows missing any Latin title first, then optional distinct-English enrichment (English language only) — processed in batches of 60 through the **entire** pending list. Previously, "romaji-only" rows at the head of the queue consumed every pass budget and a no-change round aborted the whole scan, so hundreds of rows were never attempted. A cache-only pass runs first; titles progressively flip to the selected language on screen.
- **Resolved Titles Are Never Clobbered:** `smartImport` updates now preserve resolved titles (`MediaEntryRepository.kt` → `preserveResolvedTitles`). Since Bangumi's collection endpoint only returns the native name, every re-sync used to overwrite completed English/romaji titles with the CJK name; imports may now only upgrade titles, and the native name is never lost.
- **Import-Time Cache Enrichment:** Each imported Bangumi row is enriched from the persistent Latin-title cache (`BangumiImportManager.kt`), so previously resolved entries are correct from the first frame.
- **Display Fallback Fix:** When English is selected and only romaji is shown, a distinct English title from the cache now wins (`PreferenceHelpers.kt`).

### 🧪 Unit Tests

- `BangumiLibraryTitleHydratorTest` (expanded): tier priority, per-language completion definition, no truncation on large libraries, placeholder titles.
- `MediaEntryRepositoryTitlePreservationTest` (new): resolved-title preservation across re-imports.
- `BangumiImportTitlesTest` (new): import-time cache titles.
- `BangumiDisplayTitleLanguageTest` (new): English/Romaji/Native display contract.
