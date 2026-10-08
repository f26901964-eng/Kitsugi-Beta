# Kitsugi-Beta v2.4.203 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🖼️ Galeri İndirme: Tekrar İndirmeyi Önleme ve İçerik Adına Göre Gruplama
- **İndirilenler Galerisinde Buton Gizleme:** İndirilenler ekranından açılan tam ekran resim galerisinde (yerel `file://` dosyalarında) indirme butonu tamamen gizlendi (`allowDownload = false`).
- **Kalıcı İndirme İndeksi (Deduplication):** İndirilen görseller için kalıcı URL indeksi (`kitsugi_downloaded_images.json`) oluşturuldu. Önceden indirilmiş resimlerde buton yeşil onay simgesine (✓) dönüşür; tekrar basıldığında mükerrer indirme yapılmaz ve "Bu resim zaten indirilmiş" uyarısı verilir. Çift dokunma (double-tap) yarış durumu engellendi. İndirilen resim silindiğinde indeks de temizlenir.
- **İçerik Adına Göre Gruplama:** İndirmeler → Resimler sekmesi artık resimleri ait oldukları anime, dizi veya film adına göre gruplar (Başlık + "N resim" rozeti + o gruba ait resim akışı). Grup içinden açılan galeri sadece o gruba ait resimleri gösterir.
- **Unicode Güvenli Dosya Adlandırma:** Türkçe, Japonca ve özel karakterler içeren yapım adlarının dosya adlarında bozulması engellendi.
- **TMDB, Shikimori ve Fanart.tv Galeri Kontrolü:** 3 kaynağın galeri ve ekran görüntüsü uç noktaları kontrol edildi, sağlıklı çalıştığı doğrulandı.

### 🔍 Arama "Tümü" & Kitsu Detay İyileştirmeleri
- **Arama Nesil (Generation) Koruması:** İptal edilen eski aramaların yeni sorgu sonuçlarını ezmesi (`race condition`) engellendi.
- **20 Saniyelik Dayanıklı Ağ Tavanı:** Düşük hızlı bağlantılarda AniList ve MAL kaynaklarının kaybolması önlendi (`allSourceTimeoutMs = 20_000L`).
- **MyAnimeList Jikan v4 Yedeği:** Resmî MAL API yanıt vermediğinde veya boş döndüğünde Jikan v4 yedeği devreye girer.
- **Kitsu Detay & +18 Çözümü:** `malId` kaybı ve yerel satır numarası karışıklığı giderildi; Kitsu R18/NSFW kayıtlar oturum jetonuyla açılarak "Medya detay bilgisi yüklenemedi" hatası çözüldü.

### 🧾 Ek
- Sürüm `v2.4.203` olarak güncellendi.
- Kullanıcı talimatı doğrultusunda **yalnızca `foss` varyantı** derlendi ve yayınlandı (GMS derlenmedi).

---

## 🇬🇧 English

### 🖼️ Gallery Downloads: Deduplication, Grouping & Safe Local Viewing
- **Hidden Download Button in Offline Gallery:** When viewing downloaded images (`file://`), the download button is completely hidden (`allowDownload = false`).
- **Persistent Download Index & Deduplication:** Added a persistent download index (`kitsugi_downloaded_images.json`). Downloaded images show a green checkmark (✓); re-tapping warns that the image is already downloaded without creating duplicates. Double-tap race conditions are prevented. Deleting an image clears its index entry.
- **Grouped Downloads by Title:** The Downloads → Images tab now groups images by anime/show/movie title (Title header + count badge + image grid). Opening an image from a group constrains the gallery viewer to that specific group.
- **Unicode-Safe Filenames:** Preserved non-ASCII characters (Turkish, Japanese) without filename corruption.
- **API Audit:** Verified TMDB, Shikimori, and Fanart.tv image fetching pipelines are fully functional.

### 🔍 Search "All" & Kitsu Detail Reliability
- **Search Generation Guard:** Stale cancelled search jobs can no longer overwrite active results.
- **20s Resilient Timeout:** Extended timeout to 20s to prevent missing source shelves on slower connections.
- **MAL Jikan v4 Fallback:** Automatic fallback to Jikan v4 ensures the MyAnimeList shelf stays populated.
- **Kitsu Detail & NSFW Resolution:** Fixed identity loss and row ID confusion. Added authenticated Kitsu token handling for R18/NSFW records.

### 🧾 Extras
- Version bumped to `v2.4.203`.
- Built and published exclusively as the `foss` release variant.
