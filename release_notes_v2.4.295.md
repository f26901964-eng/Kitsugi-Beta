# Kitsugi Beta v2.4.295

## 🇹🇷 Türkçe

### 🌐 Birleşik Wiki Önizleme Kartları ve Yatay Kaydırma (WikiPagePreviewCard)
- **Tek Tip Modern Kart Düzeni Yeniden Aktif:** Karakter ve yapım sayfalarındaki oyuncu kadrosu (cast), seslendirenler (voice actors), ekip üyeleri (staff/crew), müzik listeleri ve ilgili wiki sayfaları yeniden sabit `280 × 144 dp` boyutundaki tek tip `WikiPagePreviewCard` kartlarıyla gösterilir.
- **Bozulan Geniş Kartlar Kaldırıldı:** Sayfa düzenini bozan ve içeriğe göre sınırsız uzayan dikey detay kartları (`FandomInlineProfile`) tamamen temizlendi; yatay sola hizalı ve akıcı snap-scroll (`WikiPreviewStripRow`) düzenine geri dönüldü.
- **Kusursuz i18n Sözlükleri Korundu:** 215 yeni string çifti ve `FandomInfoboxLabels` ile `FandomSectionTitles` üzerindeki 400+ eşleyici kuralı korunarak bilgi kutusu ve İçindekiler başlıklarının tam Türkçe ve İngilizce görüntülenmesi sağlandı.
- **Güvenilir Otomatik Çeviri & Orijinal/Çeviri Geçişi:** Tıklanabilir bağlantıları koruyan akıllı metin ayrıştırma katmanı ve Açıklama kartındaki Orijinal/Çeviri düğmesi korunmaya devam ediyor.

---

## 🇬🇧 English

### 🌐 Unified Wiki Preview Cards & Snap-Scroll Restored (WikiPagePreviewCard)
- **Unified 280 × 144 dp Card Layout Restored:** Cast, voice actors, staff/crew, soundtrack listings, and related wiki pages on both character and production tabs are once again rendered using the consistent, left-aligned `WikiPagePreviewCard` component.
- **Fixed Inconsistent Vertical Card Expansion:** Removed the oversized vertical inline profile layouts that broke the tab flow; restored the left-aligned horizontal strip layout with smooth snap scrolling (`WikiPreviewStripRow`).
- **Comprehensive i18n Dictionaries Preserved:** Retained all 215 new localization string pairs and 400+ mapping rules across `FandomInfoboxLabels` and `FandomSectionTitles` for infoboxes and table of contents headings.
- **Reliable Translation & Toggles:** Kept the fault-tolerant link token protection layer and the in-card Original/Translation toggle integration fully intact.
