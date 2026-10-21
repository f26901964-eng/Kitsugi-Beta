# Kitsugi Beta v2.4.330

## 🇹🇷 Türkçe

### ⚡ Otomatik Çeviri Açıkken Kaydırma Kasması Kökten Çözüldü (Akıcı Wiki Deneyimi)
- **Kaydırma Konumu İzolasyonu (`FandomWikiReader.kt`):**
  - Kaydırma konumları Compose snapshot state'inden çıkarılarak invalidasyon üretmeyen düz `HashMap` yapısına taşındı; kaydırma esnasında her karede tüm sayfanın yeniden recomposition'a girmesi tamamen engellendi.
- **Bölüm Düzeyinde Alt Haritalama (`wikiTranslationsFor`):**
  - Canlı çeviri akarken tüm blokları baştan hesaplamak yerine, her bölüm sadece kendi alt çeviri haritasını alır (`wikiTranslationsFor`). Değişmeyen bölümler `remember` önbelleğinden verilir.
- **Tahsisatsız `startsWith` Kontrolü & Memoization:**
  - `hasWikiTranslation` taramalarında her blok için yapılan binlerce geçici `String` birleştirmesi (`baseKey + separator`) kaldırıldı; doğrudan tahsisatsız `startsWith` ve sayfa düzeyi memoization ile bellek tahsisatı (allocation) sıfırlandı.
- **Sabit Bağlantı Açıcı & Önbelleklenmiş Metin Ölçümü:**
  - Bağlantı açıcı lambda kimliği sabitlendi; böylece sayfa kaydırılırken tüm `AnnotatedString` ve metin ölçümlerinin baştan oluşturulması engellendi.
- **İzole Çeviri İkonu Nabız Animasyonu (`KitsugiTranslateIconButton.kt`):**
  - Boşta dururken arka planda sürekli kare tüketen sonsuz animasyon kaldırıldı; nabız animasyonu sadece aktif çeviri (`isLoading`) sürerken ve izole bir alt scope'ta çalıştırılıyor.
- **Kapsam:**
  - Açılır wiki okuyucu (`FandomWikiReader.kt`), Medya wiki sekmesi (`MediaWikiTabContent.kt`), Karakter wiki sekmesi (`CharacterWikiTabContent.kt`), ilgili ViewModel'ler ve çeviri koruma katmanı (`WikiTranslationProtection.kt`).

---

## 🇬🇧 English

### ⚡ Wiki Auto-Translate Scroll Stutter Fix (Buttery 60fps Experience)
- **Non-Invalidating Scroll Position Tracking (`FandomWikiReader.kt`):**
  - Replaced Compose snapshot-backed scroll state with a standard `HashMap`. Eliminates full-page recomposition triggers on every scroll frame.
- **Section-Isolated Translation Sub-Maps (`wikiTranslationsFor`):**
  - Subsections now only subscribe to their own translation slice rather than the entire global translation map. Unmodified sections are resolved instantly via memoized `remember`.
- **Zero-Allocation Translation Key Matching:**
  - Removed recurring string concatenations (`baseKey + separator`) inside `hasWikiTranslation` in favor of allocation-free `startsWith` checks and page-level memoization.
- **Stable Link Click Handlers:**
  - Stabilized the link opener lambda identity to prevent repeated measurement and recreation of full-page `AnnotatedString` blocks during scrolling.
- **Isolated Translate Button Pulse Animation (`KitsugiTranslateIconButton.kt`):**
  - Stopped continuous idle animation frame consumption. Pulsing only runs when actively translating (`isLoading`) within an isolated scope.
- **Full Coverage:**
  - Bottom sheet wiki reader (`FandomWikiReader.kt`), Media Wiki tab (`MediaWikiTabContent.kt`), Character Wiki tab (`CharacterWikiTabContent.kt`), and translation protection utilities.
