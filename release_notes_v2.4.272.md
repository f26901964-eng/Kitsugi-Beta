# Kitsugi-Beta v2.4.272 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe (v2.4.272)

### 🎌 Bangumi "Listem" Artık Başlık Dili Ayarına Uyuyor
- **Sorun:** Bangumi koleksiyonu içe aktarılırken API yalnızca özgün adı (`name`, çoğu zaman Japonca) döndürüyor; İngilizce/Romaji karşılığı **hiç gelmiyordu**. Bu yüzden "Listem → Bangumi" listesi, Ayarlar → Görünüm & Tercihler → **Anime & Manga Başlık Dili** ne seçilirse seçilsin CJK (Japonca/Çince) adla çiziliyordu.
- **Kalıcı Başlık Tamamlama:** Yeni `BangumiLibraryTitleHydrator`, kütüphanedeki eksik Latin adları arka planda tamamlar. Sıra: bellek önbelleği → Bangumi subject infobox'ı (`英文名` / `罗马字` / `别名`) → AniList birebir eşleşmesi. Sonuç veritabanına yazılır, yani **bir sonraki açılışta hiç ağ isteği yapılmaz**.
- **Anında Uygulama:** Latin ad öğrenildiği anda önbellekten okunur; liste, detay sayfası veya keşfet vitrininden hangisi önce yüklenirse yüklensin başlık aynı dile döner.
- **Özgün Ad Kaybolmaz:** Yeni Latin ad ana başlığa yazılırken özgün ad `titleJapanese` alanına taşınır; "Ana Dilde" seçeneği bozulmaz.
- **Bangumi Profil Listesi:** Profildeki koleksiyon satırları da seçilen başlık diline uyar.
- **Ağ/Performans Koruması:** Tamamlama yalnızca Latin adı gerçekten eksik satırlar için ve yalnızca "Listem" açıldığında çalışır; eşzamanlılık sınırlı, sonuçlar önbelleğe yazıldığı için tekrar denenmez.

### ⭐ Harici Puanlar (MDBList) Kartı Yeniden Tasarlandı
- **Uygulamanın Kendi Dili:** IMDb / Rotten Tomatoes / Metacritic / Letterboxd / TMDB / Trakt kutuları artık tam doygun renkli bloklar değil; "İstatistikler" ve "Türler" bloklarıyla aynı zemin, köşe yarıçapı ve tipografiyi kullanan bir kart. Puanlar marka rengine hafifçe boyanmış yastıklarda, büyük ve okunur biçimde.
- **Kesilme Yok:** Puanlar yatay kaydırılan şeride sığmadığı için kesilmiyor; ekran genişliğine eşit dağılan ızgaraya yerleşiyor (5–7 puan iki satıra dengeli biçimde bölünür).
- **"0 Metacritic" Hatası Giderildi:** MDBList bazı kayıtlar için "puan yok" yerine **0** döndürüyordu ve ekranda `0` puanlı anlamsız kutular görünüyordu. Artık 0 değerleri "puan yok" kabul edilir; hiç puan yoksa kart tamamen gizlenir.
- **Yükleme İskeleti:** Kart iskeleti yeni ızgara geometrisine uyarlandı; yükleme bitince düzen zıplamaz.

---

## 🇬🇧 English (v2.4.272)

### 🎌 Bangumi "My List" Now Follows the Title Language Setting
- **Root Cause:** The Bangumi collection API returns only the original name (`name`, usually Japanese) and never provides the English/Romaji counterpart. As a result, the Bangumi tab of My List always rendered CJK titles regardless of **Settings → Appearance & Preferences → Anime & Manga Title Language**.
- **Permanent Title Completion:** A new `BangumiLibraryTitleHydrator` fills in missing Latin titles for library rows in the background. Order: in-memory cache → Bangumi subject infobox (`英文名` / `罗马字` / `别名`) → exact AniList match. Results are written to the database, so **no network request happens on the next launch**.
- **Immediate Application:** Once a Latin title is known, it is served from cache — the list reflects the chosen language no matter which screen (list, detail, or explore) resolved it first.
- **Original Name Preserved:** The original name is moved into the `titleJapanese` field, so the "Native" option keeps working.
- **Bangumi Profile List:** Collection rows on the profile screen now follow the selected title language too.
- **Network/Performance Guard:** Completion only targets rows that actually lack a Latin title and only runs when My List is opened; concurrency is limited and results are cached so nothing is retried.

### ⭐ External Ratings (MDBList) Card Redesigned
- **Native App Language:** IMDb / Rotten Tomatoes / Metacritic / Letterboxd / TMDB / Trakt tiles are no longer fully saturated color blocks. They now use the same surface, corner radius, and typography as the "Statistics" and "Genres" blocks, with large readable scores on brand-tinted tiles.
- **No More Cut-off:** Ratings no longer overflow a horizontally scrolled strip; they are laid out in a grid that spans the screen width (5–7 ratings split across two balanced rows).
- **"0 Metacritic" Fixed:** MDBList returned **0** instead of "no score" for some entries, which rendered meaningless zero boxes. Zero is now treated as "no score", and the card hides entirely when nothing is available.
- **Loading Skeleton:** The skeleton mirrors the new grid geometry so the layout no longer jumps when loading finishes.
