# Kitsugi Beta v2.4.341

## 🇹🇷 Türkçe

### 🖼 İndirilen Resimler — Ekrana Göre Otomatik Izgara
- **Sabit 4'lü satır kaldırıldı:** Resim kutucukları artık ekran genişliğine göre hesaplanan sütunlarda gösteriliyor. Dikey telefonda 2–3, yatay telefonda 4–6, tablet/katlanabilir/geniş ekranda 6–10 sütun otomatik çıkıyor; ekran döndürüldüğünde ızgara anında yeniden akıyor, sağda boş alan kalmıyor.
- **Kutucuklar hücreye oturuyor:** Kart genişliği sabit 160 dp değil, ızgara hücresi kadar; küçük resim alanı 4:3 oranını koruyarak ölçekleniyor.
- **Kutucuk boyutu seçimi:** Otomatik / Kompakt / Geniş seçenekleri eklendi, seçim kalıcı (ekran kapanıp açılınca korunur).
- **Sıralama:** Önce en yeni, önce en eski, isme göre, boyuta göre.
- **Gruplama:** İçeriğe göre başlıklı gruplar (varsayılan) veya tüm resimler tek kronolojik listede (Tek liste).
- **Çoklu seçim:** Bir resme uzun basınca seçim modu açılır; tümünü seç / seçimi temizle / seçilenleri sil tek dokunuşla. Silme öncesi onay penceresi çıkar.
- **Daha fazla bilgi:** Her kutucukta dosya boyutu ve çözünürlük (ör. 1920×1080) rozeti; galeri de artık resmin piksel boyutunu önceden biliyor.

### 🎛 İndirilenler Ekranı — Özet Kartı ve Canlı İlerleme
- **Cihazdaki indirmeler özeti:** Video / altyazı / resim adetleri ve her türün kapladığı alan ile toplam boyut tek kartta.
- **Canlı ilerleme:** Devam eden indirmelerin birleşik ilerleme çubuğu, yüzde, **anlık toplam hız (MB/sn)** ve **tahmini kalan süre**.
- **Toplu işlem:** "Tümünü duraklat" ve "Tümünü sürdür" düğmeleri.
- **Satır bazlı ayrıntı:** Her indirme satırında hız, kalan süre, indirilen/toplam boyut, parça sayısı, eklenme tarihi ve tamamlananların dosya boyutu.

### 🔎 Arama ve Filtreler
- **Tüm sekmelerde tek arama:** Başlık, kaynak, yayın adı, altyazı adı ve dil üzerinden arama; sonuç yoksa açıklayıcı boş durum.
- **Videolar:** Tümü / İndiriliyor / Duraklatıldı / Tamamlandı / Hatalı hızlı filtreleri (adetlerle birlikte, devam edenler her zaman üstte).
- **Altyazılar:** Sıralama düğmeleri (en yeni / en eski / isim / boyut).

---

## 🇬🇧 English

### 🖼 Downloaded Images — Screen-Aware Automatic Grid
- **Fixed 4-per-row layout removed:** Image tiles now use a column count derived from the available width. Portrait phones show 2–3, landscape phones 4–6, tablets/foldables/large screens 6–10 columns automatically; rotating the device reflows the grid instantly and no empty gutter remains on the right.
- **Tiles fill their cell:** Card width follows the grid cell instead of a fixed 160 dp, and the thumbnail scales while keeping a 4:3 ratio.
- **Tile size choices:** Auto / Compact / Large, persisted across sessions.
- **Sorting:** Newest, oldest, by name, by size.
- **Layout:** Grouped by title (default) or a single chronological list.
- **Multi-select:** Long-press a tile to enter selection mode; select all / clear / delete selected in one tap, with a confirmation dialog.
- **More detail:** File size and resolution (e.g. 1920×1080) badges on every tile; the gallery now knows pixel dimensions up front.

### 🎛 Downloads Screen — Overview Card and Live Progress
- **On-device summary:** Video / subtitle / image counts plus per-type storage usage and total size in one card.
- **Live progress:** Combined progress bar, percentage, **aggregate speed (MB/s)** and **estimated time remaining**.
- **Bulk actions:** "Pause all" and "Resume all".
- **Per-row detail:** Speed, ETA, downloaded/total size, segment count, added date and completed file size.

### 🔎 Search and Filters
- **One search for every tab:** Matches titles, sources, stream names, subtitle names and languages, with a clear empty state when nothing matches.
- **Videos:** Quick All / Downloading / Paused / Completed / Failed filters with counts (active downloads always on top).
- **Subtitles:** Sort buttons (newest / oldest / name / size).
