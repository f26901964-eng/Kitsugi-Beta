# Kitsugi Beta v2.4.284

## 🇹🇷 Türkçe

### 1. Wiki Okuyucusunda Çoklu Sekme & Tarayıcı Gezinmesi (Browser Tabs)
- **Sekme Çubuğu ve Çoklu Görev:** Uygulama içi wiki okuyucu üst çubuğuna yatay kaydırılabilir modern sekme şeridi eklendi. Kullanıcılar birden fazla wiki makalesini eşzamanlı olarak ayrı sekmelerde açık tutabilir (12 sekmeye kadar).
- **Bağımsız Sekme Geçmişi:** Her sekme kendi ileri/geri sayfa geçmişini bağımsız olarak saklar. Üst bardaki geri (←) ve ileri (→) butonlarıyla sayfalar arasında tarayıcı rahatlığında gezinilebilir.
- **Sekme Yönetimi:** Sekmeler arasında tek dokunuşla geçiş yapılabilir, 'x' butonuyla sekme kapatılabilir veya '+' butonuyla yeni sekme başlatılabilir.

### 2. İçindekiler Satır İçi (In-line) Akordeon Görünümü
- **Sayfa Altı Yığılması Giderildi:** İçindekiler bölümündeki başlıklar tıklandığında, içerik artık sayfanın en altına ayrı kartlar halinde yığılmak yerine doğrudan tıklanan başlığın hemen altında akıcı bir şekilde açılır.
- **Çoklu Bölüm Desteği:** Birden fazla bölüm aynı anda açık kalabilir ve her biri bağımsız olarak katlanabilir.

### 3. Uygulama İçi Tam Ekran Galeri Entegrasyonu
- **Dokunulabilir Görseller:** Wiki okuyucu başlık görseli, yüklenen içerik bölümlerindeki tüm resimler ve ilgili wiki sayfalarının küçük kapakları dokunulabilir hale getirildi.
- **Kitsugi Galeri Deneyimi:** Herhangi bir görsele tıklandığında doğrudan uygulamanın zengin tam ekran görsel galerisi (`KitsugiImageGalleryDialog`) açılır; yakınlaştırma, kaydırma ve yüksek kaliteli görsel inceleme imkanı sunulur.

### 4. Çeviri Kontrolleri ve Orijinal Metne Dönüş (TR/EN & Çevirmen Paritesi)
- **WikiTranslateButton Entegrasyonu:** Mevcut otomatik çeviri ayarları ve tercih edilen çevirmen motoruyla (Google, DeepL, Yandex vb.) tam entegrasyon sağlandı.
- **Başlık ve Bölüm Bazlı Geçiş:** Makale özeti ve her içerik bölümü için çevrilmiş metin ile orijinal metin arasında tek dokunuşla geçiş yapılabilir.
- **Eşzamanlılık Koruması:** Eski veya iptal edilmiş çeviri isteklerinin güncel içeriğin üzerine yazmasını engelleyen nesil/iş-kimliği doğrulaması eklendi.

### 5. Sekme ve Sayfa Bazlı Akıllı Kaydırma Konumu Saklama (Scroll Restoration)
- **Kaldığın Yerden Devam Et:** Sekmeler veya sayfalar arasında geçiş yapıldığında kaydırma konumu (scroll position) kaybolmaz; sekme, wiki ve sayfa bazında bellekte saklanarak geri yüklendiğinde tam kalınan konuma odaklanır.

---

## 🇬🇧 English

### 1. In-App Wiki Reader Multi-Tab Browsing & History Navigation
- **Browser Tab Strip:** Added a horizontal tab bar to the wiki reader top bar, allowing up to 12 simultaneous reader tabs.
- **Independent Tab History:** Each tab maintains its own back and forward page stack. Easily navigate wiki articles using dedicated back (←) and forward (→) buttons.
- **Tab Management:** Seamlessly switch tabs, close individual tabs, or open fresh tabs with a single tap.

### 2. In-Line Table of Contents Accordion View
- **No More Bottom Card Stacking:** Tapping a section in the table of contents now expands and renders the section directly underneath its title instead of appending cards at the bottom of the article.
- **Simultaneous Sections:** Multiple sections can remain expanded simultaneously, each folding and unfolding independently.

### 3. Seamless In-App Fullscreen Gallery Integration
- **Tappable Imagery:** Header banners, inline section photos, and related article thumbnails are fully interactive.
- **Kitsugi Gallery Experience:** Tapping any picture instantly launches the in-app fullscreen viewer (`KitsugiImageGalleryDialog`) with zoom, pan, and swipe-through capabilities.

### 4. Translation Controls & Original Text Toggle
- **Translator Parity:** Deeply integrated with user-configured translation settings and preferred translation engines (Google, DeepL, Yandex, etc.).
- **Instant Toggle:** Quickly switch between translated and original text for both article lead blurbs and individual section contents.
- **Stale Coroutine Guards:** Generational job keys prevent lagging translation responses from overwriting updated text.

### 5. Smart Scroll Position Restoration
- **Preserved Scroll Positions:** Reading positions are remembered across tabs, wikis, and pages. Switching tabs or navigating back automatically restores exact scroll offsets.
