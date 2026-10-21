# Kitsugi Beta v2.4.339

## 🇹🇷 Türkçe

### 🎭 TMDB Karakter Görselleri, ARM v2 & Çoklu Kaynak Yedek Zinciri
- **ARM v2 Uç Noktası & Önbellek Zehirlenmesi (`KitsugiIdResolver.kt`, `KitsugiEpisodeRatingsRepository.kt`):** `arm.haglund.dev` üzerinde `HTTP 400 Bad Request` dönen `/api/v2/ids?source=themoviedb` sorgusu yerine resmî sözleşmeye uygun `/api/v2/themoviedb?id=`, `/api/v2/thetvdb?id=`, `/api/v2/imdb?id=` ve Simkl `POST /api/v2/ids` uç noktalarına geçildi. `MOVIE` ile `TV` kayıtları ayrıştırılarak ID çakışması önlendi; Wikidata (`P4086/P8729/P11495/P12095`) köprüsü eklendi ve önbellek zehirlenmesi giderildi.
- **Seslendirmen Kopya Görsellerinin Temizlenmesi (`TmdbCreditsClient.kt`, `KitsugiCharacterClient.kt`):** TV dizilerinde seslendirmen fotoğraflarının karakter portresi olarak atanması engellendi. Karakterler sekmesi, detay yüklemesinden önce açılsa bile seslendirme sinyalleriyle zenginleştirilerek gerçek kurgusal portrelerle doldurulur.
- **Çoklu Kaynak Yedek Zinciri & Doraemon Seri Filmleri:** AniList kesintilerinde Jikan, hızlı Shikimori `/roles`, Kitsu, Bangumi, Simkl ve Fandom paralel yedek zinciri devreye alınır. Seri filmlerindeki ilk 8 popüler yapım elemesi ve tek kelimeli isimlerin düşürülmesi hatası düzeltildi; karakter detayında yüklenen görseller sekme önbelleğine geri yayılır.

### 🗑️ Aktivite Silme: Açılır Panel Kaldırıldı
- **Tek dokunuş silme (`ProfileActivityComponents.kt`):** Kendi AniList profilindeki aktivite kartlarında üç nokta (⋮) düğmesi ve tek satırlık "Sil" açılır menüsü kaldırıldı. Yerine doğrudan, kartın sağ üstünde duran kırmızı çöp kutusu düğmesi getirildi.
- **Onay korundu:** Düğme, çağrı yerindeki mevcut `KitsugiConfirmDialog` penceresini açar; kazara silme koruması aynen korunur.

### 🌐 Kullanıcı Biyografilerinde Otomatik Çeviri (Wiki Mantığı)
- **Ortak altyapı (`KitsugiAutoTranslation.kt`, `KitsugiHtmlTranslation.kt`):** Wiki çeviri kuralları profillere taşındı: ayar açıksa çeviri uygulama içinde yapılır ve bellekte tutulur. Çeviri ikonu nabız animasyonu gösterir ve kompakt "Orijinal / Çeviri" çipi sunulur.
- **HTML güvenli çeviri:** Biyografi HTML'i yalnızca metin düğümleri üzerinden çevrilir. Etiketler, resimler ve bağlantılar korunur; `<script>`, `<code>` bloklarına dokunulmaz.
- **Kapsam:** AniList, Kitsu, Shikimori, Simkl ve MAL profilleri; WebView yeniden yaratılmadan güncellenir.
- **Takip butonu metni (`profile_following_button`):** Yanlış anlam veren "Takip Ediliyorsun" yerine doğru "Takiptesin" yapıldı.

### 🏷️ Listem Rozetleri: Köşelere Yapışık ve Kompakt
- **Poster köşe rozetleri (`KitsugiMediaEntryCard.kt`):** Durum ve puan rozetleri posterin köşelerine tam yapışık yerleştirildi (`TopEndCornerShape` / `BottomEndCornerShape`).
- **Kompakt stil:** 9sp etiket boyutu, daraltılmış iç boşluk, taşmaları önleyen `widthIn(max = 108.dp)` sınırı.

### 📖 Kompakt Wiki Araç Çubuğu & Oynatıcı Fallback
- **Kompakt Wiki Çubuğu (`WikiPageTools.kt`, `WikiChromeComponents.kt`):** Çeviri ve reklam engeli durumu tek satırlık modern araç çubuğunda gösterilir; sayfa görünümü rahatlatıldı.
- **Satır İçi Video Oynatıcı Fallback (`InlinePlayerSupport.kt`):** Detay/Fragman bölümünde cihaz donanımının çözemediği kodeklerle karşılaşıldığında tam ekran yazılımsal oynatıcıya geçiş kartı sunulur.

---

## 🇬🇧 English

### 🎭 TMDB Character Portraits, ARM v2 & Multi-Source Fallback Chain
- **ARM v2 Endpoint & Cache Poisoning (`KitsugiIdResolver.kt`, `KitsugiEpisodeRatingsRepository.kt`):** Replaced `/api/v2/ids?source=themoviedb` (which returned `HTTP 400 Bad Request`) with the official ARM endpoints `/api/v2/themoviedb?id=`, `/api/v2/thetvdb?id=`, `/api/v2/imdb?id=`, and Simkl `POST /api/v2/ids`. Differentiated `MOVIE` from `TV` records to prevent ID collision; integrated Wikidata (`P4086/P8729/P11495/P12095`) bridge and resolved cache poisoning.
- **Voice Actor Duplicate Image Cleanup (`TmdbCreditsClient.kt`, `KitsugiCharacterClient.kt`):** Prevented actor portrait propagation into character slots for TV series. The characters tab populates genuine fictional character artwork even when opened prior to media detail resolution.
- **Multi-Source Fallback & Franchise Fixes:** Activated parallel fallbacks via Jikan, fast Shikimori `/roles`, Kitsu, Bangumi, Simkl, and Fandom during AniList downtime. Fixed franchise filters dropping single-word character names (e.g. *Doraemon*) beyond the top 8 popular titles; cached character portraits backpropagate to the tab view.

### 🗑️ Activity Delete: Direct Action Button
- **One-tap delete (`ProfileActivityComponents.kt`):** Removed the redundant three-dot menu on personal AniList profile activities in favor of a direct red trash button, preserving the confirmation dialog.

### 🌐 User Bio Automatic Translation
- **Shared infrastructure (`KitsugiAutoTranslation.kt`, `KitsugiHtmlTranslation.kt`):** Brings wiki-style in-memory translation, pulsing indicators, and compact original/translated chips to user biographies across AniList, Kitsu, Shikimori, Simkl, and MAL without destroying WebView instances.
- **Follow button fix:** Corrected Turkish label from "Takip Ediliyorsun" to "Takiptesin" (Following).

### 🏷️ My List Badges: Corner-Glued & Compact
- **Flush corner placement (`KitsugiMediaEntryCard.kt`):** Status and score badges clip tightly to poster corners with compact 9sp typography and single-line constraints.

### 📖 Compact Wiki Toolbar & Player Fallback
- **Streamlined Wiki Bar (`WikiPageTools.kt`, `WikiChromeComponents.kt`):** Unified translation and ad blocking indicators into a clean single-row bar.
- **Inline Video Player Fallback (`InlinePlayerSupport.kt`):** Provides a seamless one-tap card to open high-profile or unsupported video codecs directly in the fullscreen player.
