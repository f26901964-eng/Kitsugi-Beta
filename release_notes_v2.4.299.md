# Kitsugi Beta v2.4.299

## 🇹🇷 Türkçe

### 📖 Wiki Okuyucusunda Eksiksiz Otomatik Çeviri, Mavi Bağlantı Koruması & Okuma Konumu
- **Giriş Bölümü & Madde Gövdesi Çevirisi:** Giriş bölümü kartına yapılandırılmış çeviri haritası bağlanarak wiki maddesinin en büyük bölümünü oluşturan ana gövde artık blok blok eksiksiz çevriliyor.
- **İçindekiler (TOC) Çevirisi:** Yeni `reader-toc` akışı ile bölüm başlıkları `⟪§⟫` satır jetonları ve satır sayısı kanıtıyla tek bir istekte çevrilir; olası tutarsızlıklarda satır satır güvenli yedeğe düşerek başlıkların yanlış bölümlere kayması engellenir.
- **Bellekte Çeviri Koruması:** 'Orijinal' moduna geçildiğinde çeviriler bellekten silinmez; 'Çeviri'ye geri dönüldüğünde yeni bir ağ isteğine gerek kalmadan anında ekrana yansıtılır.
- **Sayfa Geneli Geçiş Şeridi:** Okuyucunun üst kısmına eklenen `ReaderTranslationBar` ile başlık açıklaması, İçindekiler ve açılmış tüm bölümler tek bir dokunuşla Orijinal veya Çeviri görünümüne geçirilebilir.
- **Görsel Altı Yazıları (figcaption):** Görsellerin URL'si, dosya adı, türü ve yerleşimi korunurken yalnızca altındaki açıklama metinleri (`figcaption`) hedef dile çevrilir.
- **Mavi Tıklanabilir Bağlantı Güvencesi:** Bağlantı etiketleri çeviri öncesinde jetonlarla maskelenir; sağlayıcı jetonları bozsa dahi paragraf yalnızca bağlantı harici parçalar halinde çevrilir. Bağlantı adresi, dokunma hedefi ve etiketler hiçbir koşulda bozulmaz.
- **Okuma Konumu Koruması (`WikiReaderLayoutAnchor`):** Çeviri sonrasında metin uzunluğu ve satır sayısı değişse bile kullanıcının o anda okuduğu blok ve bağıl okuma oranı korunur; Orijinal ↔ Çeviri geçişlerinde sayfa asla başa veya rastgele bir paragrafa zıplamaz.
- **Akıllı Hedef Dil Filtresi:** Vikipedi TR gibi zaten hedef dilde olan kaynaklarda gereksiz çeviri istekleri engellenerek kaynak tüketimi ve gereksiz ağ trafiği önlendi.

---

## 🇬🇧 English

### 📖 Full Wiki Reader Automatic Translation, Link Protection & Reading Position Anchor
- **Lead Section & Article Body Translation:** Structured block translation map is now properly passed to the lead section card, fully translating the entire article body block-by-block.
- **Table of Contents (TOC) Translation:** Introduced the dedicated `reader-toc` pipeline that translates section titles in a single batch using `⟪§⟫` line sentinels with count verification, falling back to line-by-line translation to prevent misalignment.
- **Instant In-Memory Translation Toggle:** Switching to 'Original' retains translated content in memory; toggling back to 'Translation' restores the text instantly with zero network overhead.
- **Global Page Translation Bar:** Added `ReaderTranslationBar` at the top of the wiki reader to seamlessly toggle all active surfaces (description, TOC, and expanded sections) between Original and Translated text with a single tap.
- **Image Captions (figcaption):** Image URLs, filenames, kinds, and positions remain completely untouched while their textual descriptions (`figcaption`) are accurately translated.
- **Guaranteed Clickable Link Protection:** Link labels are masked before translation; if a translation provider alters the mask, only non-link segments are translated, ensuring links and tap targets never detach or break.
- **Reading Position Stability (`WikiReaderLayoutAnchor`):** Measures the visible block and relative reading ratio prior to toggling, seamlessly restoring the user's scroll position regardless of text expansion or shrinkage.
- **Target Language Redundancy Guard:** Prevents redundant translation requests for articles already matching the target language (such as Turkish Wikipedia).
