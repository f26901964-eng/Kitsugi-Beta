# Kitsugi Beta v2.4.353

## 🇹🇷 Türkçe

### 📅 Listem Sıralaması Baştan Yazıldı — "Son Eklenen" Artık Gerçekten Son Eklenen
- **Gerçek Eklenme Tarihi & DB v30:** Kayıtlara `addedAt` alanı eklendi (Room Database v30). AniList, Kitsu ve Shikimori'de kütüphanenize eklediğiniz gerçek tarih kullanılır; önceden sıralama, içe aktarım sırasından ibaret olan yerel satır numarasına bakıyordu.
- **Sıralama Artık Kazanıyor:** Bir sıralama seçtiğinizde (Son eklenen, Puan, Ada göre…) liste tek akışta ve **global** sıralanır. İzleniyor/Tamamlandı/Planlandı bölümleri artık seçtiğiniz sırayı ezmez; durum bölümlü gezinme yalnızca "Varsayılan" sıralamada gösterilir.
- **Son Eklenen'e Özel Dönem Başlıkları:** Liste zaman çizelgesiyle açılır — Bugün, Dün, Bu Hafta, Geçen Hafta, Bu Ay, Geçen Ay, Son 3 Ay, Bu Yıl, Geçen Yıl, Daha Eski…
- **Her Seçeneğe Özgü Sıralama:** İlerleme artık tamamlanma oranına (%100, %50…) göre; puansız ve tarihsiz kayıtlar her iki yönde de sonda; "Ada göre" ekranda gördüğünüz başlığı (başlık dili tercihine göre) sıralar; Favoriler içinde en yeni favori üstte.
- **Zaman Birimi & Sağlayıcı Normalizasyonu:** Bangumi'nin milisaniye, diğerlerinin saniye yazdığı güncelleme zamanları normalize edildi; Simkl kayıtları artık son izleme zamanını taşır (`last_watched_at`).
- **Kullanıcı Listesi & TV Uyumu:** Başka kullanıcıların profil listesi ekranına ve komşu sayfa geçişlerine de aynı sıralama sözleşmesi uygulandı.
- **Not:** Güncellemeden sonra bir kez senkronizasyon yapmak eklenme tarihlerini kaynaklardaki gerçek değerlerle netleştirir. Mevcut görünümünüzün aniden bozulmaması için kayıtlı eski "Son eklenen" seçimi bir kerelik "Varsayılan"a alınır; dilediğiniz an Sıralama menüsünden tekrar seçebilirsiniz.

### 🎬 Fragman & OP/ED Video Oynatıcı Aşağı Kayarak Açılma & Yukarı Kapanma Animasyonu
- **Akıcı slide & expand:** `KitsugiDetailReveal` geçişine dikey kayma animasyonu (`slideInVertically(-h/8)` / `slideOutVertically(-h/8)`) eklendi; fragman ve tema kartları artık aniden açılmak yerine aşağı doğru akıcı şekilde genişleyip yukarı doğru büzülerek kapanır.
- **Videolar sekmesi entegrasyonu:** `DetailVideoGroup` içerisindeki oynatıcı alanı `KitsugiDetailReveal(visible = isSelected)` ile sarılarak kapatma butonu ile birlikte animasyona dahil edildi.
- **İçerik boyutu senkronizasyonu:** `KitsugiTrailerCard`, `DetailVideoGroup`, `KitsugiMediaVideosTab`, `KitsugiThemesList` ve `ThemeVideoSection` alanlarına `animateDetailContentSize()` eklenerek oynatıcı açılıp kapanırken alt içeriklerin zıplamadan yumuşakça kayması sağlandı.

---

## 🇬🇧 English

### 📅 My List Sorting Rewritten — "Recently Added" Is Now Truly Accurate
- **Real Add Dates & DB v30:** Added the `addedAt` timestamp to local records (Room Database v30). AniList (`createdAt`), Kitsu, and Shikimori provide the exact date a title was added to your library, replacing the legacy local row ID that reflected raw import order.
- **Sort Priority:** Selecting any sort option (Recently added, Score, Title, etc.) now produces a unified, **globally** sorted list. Status sections (Watching/Completed/Planning) no longer override user sorting; sectioned browsing is retained under the new "Default" sort option.
- **Dedicated Period Headings:** Recently added sort displays intuitive chronological headers: Today, Yesterday, This Week, Last Week, This Month, Last Month, Last 3 Months, This Year, Last Year, and Earlier.
- **Option-Specific Logic:** Progress sorts by completion ratio; unrated and dateless entries sink to the end in both directions; Title sorts by the currently displayed title language; Favorites sorts newest favorites first.
- **Timestamp & Provider Normalization:** Normalized millisecond (Bangumi) and second-based timestamps; Simkl imports now carry `last_watched_at`.
- **User Profile & TV Parity:** Applied the identical global sorting contract to other users' list view screens and horizontal page transitions.
- **Note:** Running a sync after updating backfills exact add dates from supporting providers. Previously saved "Recently added" selections are safely reset to "Default" once to prevent unexpected layout jumps.

### 🎬 Smooth Slide & Expand Animation for Trailer and OP/ED Players
- **Fluid slide & expand:** Added vertical slide transitions (`slideInVertically(-h/8)` / `slideOutVertically(-h/8)`) to `KitsugiDetailReveal`, ensuring inline video players and music themes glide smoothly downward on open and retract upward on close.
- **Videos tab parity:** Wrapped inline players in `DetailVideoGroup` with `KitsugiDetailReveal(visible = isSelected)`, integrating the inline dismiss button into the shared animation.
- **Content size interpolation:** Applied `animateDetailContentSize()` across `KitsugiTrailerCard`, `DetailVideoGroup`, `KitsugiMediaVideosTab`, `KitsugiThemesList`, and `ThemeVideoSection`, preventing layout jumps when players reveal or collapse.
