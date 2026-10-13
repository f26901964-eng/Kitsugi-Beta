# Kitsugi-Beta v2.4.280

## 🇹🇷 Türkçe (v2.4.280)

Fandom Wiki entegrasyonu, zengin karakter sayfaları (örn. Eren Yeager), hikâye yayları (Jujutsu Kaisen, My Hero Academia, Kimetsu no Yaiba), manga/anime tabloları ve infobox API yapılarına uyumlu olarak genişletildi.

### 🌟 Yeni Özellikler ve İyileştirmeler

1. **Uygulama İçi Makale Okuyucusu (Fandom Article Reader)**
   - Evren rehberi çiplerine veya wiki içi arama sonuçlarına tıklandığında harici tarayıcıya yönlendirmek yerine doğrudan uygulama içinde açılan zengin makale okuyucu sayfası (`WikiItemSheet`) eklendi.
   - Makale başlığı, wiki kaynak adı, kısa açıklama, bilgi kutusu (infobox), görseller, açılır ve tembel yüklenen bölümler (lazy sections), spoiler koruması ve harici "Fandom'da aç" bağlantısı entegre edildi.
   - Manga bölümleri, anime bölümleri ve hikâye yaylarındaki içerik tabloları taranarak satır yapısını koruyan okunaklı bloklara dönüştürüldü; gereksiz navigasyon tabloları temizlendi.
   - Bölüm içindeki figür ve galeri görselleri güvenli HTTPS URL'leri ve resim alt yazılarıyla birlikte yakalanarak zengin görsel blokları olarak sunuldu.

2. **Genişletilmiş Evren Rehberi & 50 Başlıklı Grup Taraması**
   - Evren rehberine Karakterler (`Characters`), Hikâye Yayları (`Story Arcs`), Anime Bölümleri (`Episodes`) ve Manga Bölümleri (`Chapters`) kategorileri eklendi.
   - Türkçe ve İngilizce kategori adayları `categoryinfo` sorgularında 50 başlıklı API gruplarına bölünerek (`clean.chunked(MAX_TITLES_PER_REQUEST)`) sınırda kesilme sorunu tamamen giderildi.

3. **Hiyerarşik Spoiler ve Gizli Bölüm Kalıtımı**
   - İçindekiler tablosunda (TOC) alt bölümler artık üst bölümlerin spoiler ve gizlilik durumunu kalıtım yoluyla devralır. Gizli galeri veya navigasyon bölümlerinin alt başlıkları içindekilerde görünmez.
   - İçindekiler isteği başarısız olduğunda kullanıcıya yeniden deneme sunulur; eksik içindekiler önbelleğe alınmadığı için yeniden deneme gerçekten API'ye gider.

---

## 🇬🇧 English (v2.4.280)

Expanded Fandom Wiki integration to cater to diverse article structures, including comprehensive character pages (e.g. Eren Yeager), story arcs, manga/anime chapter/episode tables (Jujutsu Kaisen, My Hero Academia, Demon Slayer), and modern infobox API structures.

### 🌟 New Features & Improvements

1. **In-App Article Reader**
   - Tapping any universe guide chip or search hit opens a complete in-app article sheet (`WikiItemSheet`) instead of forcing an external browser jump.
   - Full support for article title, wiki source attribution, lead summary, infobox card, lazy expandable sections, spoiler gating, and external "Open on Fandom" link.
   - Formatted content tables for chapters, episodes, and story arcs while omitting extraneous navigation navboxes.
   - Extracted figure and gallery images with secure HTTPS URLs and captions displayed as rich inline image blocks.

2. **Expanded Universe Guide & 50-Title Chunked Lookups**
   - Added Characters, Story Arcs, Episodes, and Chapters to the guide categories.
   - Category existence checks are now chunked in 50-title batches (`clean.chunked(MAX_TITLES_PER_REQUEST)`), ensuring category scans never truncate at the API limit.

3. **Hierarchical Spoiler & Hidden Section Inheritance**
   - Subsections in the table of contents now inherit spoiler and hidden flags from ancestor sections. Child sections of hidden galleries or navigation blocks no longer leak into the TOC.
   - Incomplete table of contents requests display a retry button and avoid negative caching so retries actually re-query the MediaWiki API.
