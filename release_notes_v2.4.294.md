# Kitsugi Beta v2.4.294

## 🇹🇷 Türkçe

### 🌐 Eksiksiz Wiki Arayüzü & Sözlük Yerelleştirmesi (i18n)
- **Kapsamlı Bilgi Kutusu ve Bölüm Çevirisi:** Wiki sekmesindeki bilgi kutusu etiket sütununda ve İçindekiler bölüm başlıklarında daha önce sözlükte bulunmadığı için İngilizce kalan tüm genel anahtarlar Türkçe ve İngilizce string kaynaklarına bağlandı.
- **215 Yeni String Çifti:**
  - **29 Bilgi Kutusu Etiketi:** *Music by, Licensed by, Original network, Webtoon · service, English publisher, Serialization, Imprint…*
  - **12 Medya Grup Başlığı:** Roman, Manga, Webtoon, Anime, Film, OVA/ONA, Video oyunu, Sahne oyunu, Live-action dizisi…
  - **47 İçindekiler & Bölüm Başlığı:** *Accolades, Critical response, Diğer karakterler, Temalar ve analiz, Ev medyası, Gişe hasılatı, İzlenme oranları, İkinci/Özel/Koleksiyon baskısı…*
  - **127 Kısa Değer:** Medeni durumlar (Bekâr, Evli, Dul…), durumlar (Mühürlendi, Dirildi, Reenkarne…), cinsiyet kimlikleri, türler, 75'ten fazla meslek/rol ve ahlaki yönelimler (İyi, Kötü, Tarafsız…).
- **Düzeltilen Eşlemeler:** Daha önce yanlış dallara bağlanan kritik etiketler düzeltildi:
  - *Producer* ➔ Yapımcı (eskiden Stüdyo)
  - *Magazine* ➔ Dergi (eskiden Yayıncı)
  - *Label* ➔ Müzik şirketi (eskiden Yayıncı)
  - *Streaming* ➔ Dijital yayın platformu (eskiden Kanal)
  - *Demographic* ➔ Hedef kitle (eskiden Tür)
- **Genişletilmiş Sözlük Eşleyicileri:** `FandomInfoboxLabels` ve `FandomSectionTitles` içine 400'den fazla yeni anahtar varyantı eklendi.
- **Kusursuz Dil Paritesi:** `values/strings.xml` ve `values-en/strings.xml` arasında birebir 7601=7601 tam anahtar paritesi sağlandı.

---

## 🇬🇧 English

### 🌐 Comprehensive Wiki Interface & Dictionary Localization (i18n)
- **Complete Infobox & TOC Heading Mapping:** Addressed untranslated English labels in infoboxes and table of contents headings by mapping them to dedicated string resources in both English and Turkish.
- **215 New String Resource Pairs:**
  - **29 Infobox Labels:** *Music by, Licensed by, Original network, Webtoon · service, English publisher, Serialization, Imprint…*
  - **12 Media Group Headings:** Novel, Manga, Webtoon, Anime, Film, OVA/ONA, Video game, Stage play, Live-action series…
  - **47 TOC & Section Headings:** *Accolades, Critical response, Other characters, Themes & analysis, Home media, Box office, Ratings, Second/Special/Collector's edition…*
  - **127 Short Values:** Marital statuses, lifecycle states (Sealed, Resurrected, Reincarnated…), gender identities, types, 75+ professions/roles, and moral alignments.
- **Corrected Mappings:** Fixed misclassified dictionary associations:
  - *Producer* ➔ Producer (previously mapped to Studio)
  - *Magazine* ➔ Magazine (previously mapped to Publisher)
  - *Label* ➔ Record label (previously mapped to Publisher)
  - *Streaming* ➔ Streaming service (previously mapped to Network)
  - *Demographic* ➔ Target demographic (previously mapped to Genre)
- **Expanded Matching Rules:** Added over 400 new key variations to `FandomInfoboxLabels` and `FandomSectionTitles`.
- **1:1 Resource Parity:** Guaranteed strict 7601=7601 key parity between `values/strings.xml` and `values-en/strings.xml`.
