# Kitsugi Beta v2.4.298

## 🇹🇷 Türkçe

### 🌐 Wiki Sayfalarında Bağlantı Korumalı Yerinde Otomatik Çeviri
- **Tıklanabilir Bağlantılar ve Hedefler Korunuyor:** Wiki açıklamalarında, paragraflarda ve listelerde yer alan tıklanabilir bağlantı etiketleri (`label`) ve hedefleri (`url`) çeviri servisine gönderilmez, orijinal yerlerinde korunur.
- **Yerinde Parçalı Çeviri:** Bağlantılı cümleleri jetonlarla maskelemek yerine yalnızca bağlantılar arasındaki metin parçaları çevrilir. Böylece jeton bozulması veya yer değiştirme riski tamamen ortadan kalkar.
- **Hata İzolasyonu:** Bir metin parçasının çevirisi başarısız olursa veya boş dönerse yalnızca o parça orijinal kalır; tüm paragraf veya bölüm orijinal metne düşmez.
- **Görsel Altı ve Başlık Bağlantıları:** Görsel açıklamaları (`caption`) ve alt başlıklar (`subtitle`) yerinde çevrilirken içlerindeki tıklanabilir bağlantılar korunur ve tıklanabilir olarak çizilir.
- **Yapı ve Sıralama Bütünlüğü:** Kartların, görsellerin ve listelerin sırası korunur. Çeviri metin uzaması durumunda alanlar doğal olarak genişler; hiçbir blok kaybolmaz veya yer değiştirmez.
- **Önbellek ve Dil Değişimi Koruması:** Otomatik çeviri kapatıldığında veya hedef dil değiştiğinde eski dildeki çeviri anlık görüntülerinin geri belirmesine karşı koruma sağlandı.
- **Aktif Okuyucu Dili:** Okuyucudaki çeviri anahtarı ve hedef dil kontrolü ana yapımın wiki'sinden değil aktif okunan sayfanın wiki'sinden türetilir.

---

## 🇬🇧 English

### 🌐 Link-Preserved In-Place Automatic Wiki Translation
- **Preserved Clickable Links & Targets:** Clickable link labels and destination URLs within wiki descriptions, paragraphs, and lists are never dispatched to translation engines, preserving original links intact.
- **In-Place Segment Translation:** Instead of brittle sentence-level token masks, only plain text segments between links are translated. Eliminates translation token loss or link displacement.
- **Fault-Isolated Fallback:** If translating an individual text segment fails or returns empty, only that specific segment stays in its original text—preventing entire paragraphs or sections from reverting.
- **Captions & Heading Links:** Image captions and card subtitles are translated in-place while keeping their embedded links active and clickable.
- **Structure & Order Integrity:** Block, card, and image ordering remain intact. Cards dynamically expand to accommodate longer translated prose without shifting elements.
- **Language Switch & Cache Guard:** Prevents stale translations from resurfacing when automatic translation is turned off or when switching target languages.
- **Active Reader Wiki Derivation:** Reader translation keys and source language detection now strictly derive from the actively browsed wiki rather than the parent work's root wiki.
