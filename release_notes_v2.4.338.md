# Kitsugi Beta v2.4.338

## 🇹🇷 Türkçe

### 🏛️ Bangumi Vault Tam Entegrasyonu & 4 Modüllü Kaynak Mimarisi
- **Sıfır XML Kimlik Artışı (+0 XML ID) ile Modüler Kaynak Ayrımı:**
  - Bangumi çeviri ve meta-etiket sözlüğü devasa boyutu nedeniyle 4 bağımsız Gradle modülüne bölündü (`bangumi-vault-resources-1`, `bangumi-vault-resources-2`, `bangumi-vault-resources-3` ve ana `:app` modülü).
  - Android 65.535 tekil DEX / kaynak kimlik sınırı korunarak tüm CJK kanon, organizasyon ve etiket sözlükleri hatasız ve hafif şekilde entegre edildi.
  - 9 adet Python doğrulayıcısı ve 72 adet JUnit testi eksiksiz başarıyla geçti.

### 🎨 Detay, İnceleme ve Liste Arayüz İyileştirmeleri (UI/UX)
- **Tüm İncelemeler Penceresi Anında Yükleme (`KitsugiAllReviewsBottomSheet` & `ReviewsTab`):**
  - Detay sayfasında önceden yüklenmiş olan inceleme verileri (`reviewsList`) yeni `initialReviews` parametresiyle pencereye aktarılır.
  - Pencere ilk açıldığında ağ veya kaynak/kimlik çözümleme gecikmesi olsa bile boş görünmez; önizleme incelemeleri doğrudan ekrana yansıtılır.
- **Listem Kare Görünümü Dar Ekran Uyumu (`MyListComponents`):**
  - Dar dikey telefon ekranlarında (`screenWidthDp < 600`) satır başına kart sayısı 3'ten 2'ye düşürüldü; kartların aşırı sıkışması önlendi. Tablet (4 sütun) ve yatay geniş ekran düzenleri (5–6 sütun) korundu.
- **Detay Sayfası Dikey Poster Tercih Eşiği (`DetailHeroGallerySelection`):**
  - Dikey cihazlar için poster tercih oranı eşiği `0.55`'ten `0.75`'e yükseltildi. Artık çok daha geniş bir dikey ekran en-boy oranında yatay arka plan yerine dikey posterler öncelikli olarak seçilir.

---

## 🇬🇧 English

### 🏛️ Full Bangumi Vault Architecture & 4-Module Resource Split
- **Modular Resource Split with Zero XML ID Bloat (+0 XML IDs):**
  - Large-scale Bangumi translation and meta-tag dictionary partitioned across 4 distinct Gradle modules (`bangumi-vault-resources-1`, `bangumi-vault-resources-2`, `bangumi-vault-resources-3`, and main `:app`).
  - Preserves Android 65,535 single DEX/resource ID limits while integrating comprehensive CJK canon, studio/org, and tag databases.
  - Verified with 9 Python validators and 72 passing JUnit tests.

### 🎨 Detail, Reviews, and MyList UI/UX Enhancements
- **Instant Reviews Bottom Sheet Seed (`KitsugiAllReviewsBottomSheet` & `ReviewsTab`):**
  - Pre-loaded reviews from the detail screen tab are now seeded into the "All Reviews" bottom sheet via `initialReviews`.
  - Eliminates empty-screen flicker on open, ensuring instant visibility even while remote/alias ID feeds resolve in the background.
- **MyList Card Density on Narrow Screens (`MyListComponents`):**
  - Reduced column count from 3 to 2 on narrow portrait mobile screens (`< 600dp`) to prevent crowded cards. Tablet (4 cols) and landscape (5–6 cols) layouts remain preserved.
- **Hero Artwork Portrait Poster Ratio Threshold (`DetailHeroGallerySelection`):**
  - Raised portrait tall ratio threshold from `0.55` to `0.75`, reliably prioritizing vertical posters over landscape artwork across a wider variety of phone aspect ratios.
