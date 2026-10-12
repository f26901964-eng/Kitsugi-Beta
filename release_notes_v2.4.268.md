# Kitsugi-Beta v2.4.268 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🔄 Çeviri Butonlarında Dönen Halka & Nabız Yüklenme Animasyonu
- **Yeni Ortak Bileşen (`KitsugiTranslateIconButton`):** Galeri butonu (`KitsugiGalleryIconButton`) ile birebir aynı animasyon dili uyarlandı: Çeviri devam ederken ikon nabız gibi yanıp söner (0.30 → 1.0 alpha, 700 ms) ve etrafında tema vurgu renginde dönen bir yüklenme halkası belirir; yükleme sırasında buton dokunulamaz. 28dp, 32dp ve 36dp butonlar için orantılı halka çapları tanımlandı.
- **Kök Sağlayıcı ile Durum Paylaşımı (`LocalKitsugiTranslationLoading`):** Medya, Karakter, Ekip ve Stüdyo detay sayfalarının kök sağlayıcısına `LocalKitsugiTranslationLoading` eklendi. Sayfa içerisindeki tüm çeviri butonları durumu otomatik tüketirken, alt sayfalar ve pencereler (sheet) kendi yerel durumunu geçirerek bağımsız kalır.
- **Akış Uyumlu ViewModel Durumu:** Detay ViewModel'lerine (`ApiResult`, `MediaEntry`, `Character`, `Staff`, `Studio`) `translationLoading: StateFlow<Boolean>` eklendi. Kısmi metin akışlarında halka erken durmaz; metin akarken döner ve çeviri bittiğinde kapanır. Nesil sayacı ile eski iptal edilen isteklerin yeni durumu etkilemesi engellendi.
- **Genişletilmiş Kapsam:** Açıklama kartı, stüdyo hakkında kartı, karakter ve ekip biyografi kartları, spoiler pencereleri, inceleme, konu ve aktivite pencereleri, tüm liste görünümleri, forum yorum kartları ve profil biyografilerindeki çeviri alanlarına entegre edildi.

---

## 🇬🇧 English

### 🔄 Loading Spinner & Pulse Animation for Translation Buttons
- **New Reusable Component (`KitsugiTranslateIconButton`):** Implemented identical animation styling to `KitsugiGalleryIconButton`: While translation is in progress, the icon pulses (0.30 → 1.0 alpha, 700 ms) accompanied by an accent-colored spinning progress ring, and clicks are disabled during translation. Tailored ring dimensions are provided for 28dp, 32dp, and 36dp button variants.
- **Centralized Composition Local (`LocalKitsugiTranslationLoading`):** Added `LocalKitsugiTranslationLoading` to root page providers (Media, Character, Staff, Studio). Descendant translation buttons consume the state automatically, while bottom sheets and dialogs pass their own isolated states to prevent cross-component indicator bleed.
- **Stream-Aware ViewModel State:** Added `translationLoading: StateFlow<Boolean>` to detail ViewModels (`ApiResult`, `MediaEntry`, `Character`, `Staff`, `Studio`). The spinner stays active during partial/streaming text delivery and only stops once the full translation has resolved. Generation counters prevent cancelled requests from clearing newer tasks.
- **Comprehensive Coverage:** Applied to media synopsis cards, studio about cards, character/staff biography cards, spoiler sheets, review/topic/activity detail dialogs and lists, forum comment cards, and profile biography translate buttons.
