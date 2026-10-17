# Kitsugi Beta v2.4.302

## 🇹🇷 Türkçe

### ➕ "Tümünü Gör" Sayfalarında Hızlı Listeye Ekleme Kısayolu (Grid & Liste Görünümü)
- **Kompakt Kart Rozeti (`KitsugiQuickAddButton`):** Posterin sağ üst köşesinde (TopEnd) yer alan 30dp yuvarlak, şık ve yer kaplamayan kısayol butonu.
- **Grid ve Liste Görünümünde Kusursuz Uyum:** `KitsugiExploreMediaCard` (grid — TV/yatay/dikey varyantları), `KitsugiRankingMediaCard` ve `DetailedSeasonalMediaCard` (liste görünümü) bileşenlerinin hepsinde aynı poster köşesinde durur; layout geometrisinde sıfır piksel kayma oluşturur.
- **Durum Duyarlı & Düzenleme Entegrasyonu:** İçerik listede değilse tema vurgulu zemin üzerinde beyaz `+` ikonuyla doğrudan ekleme yapar (Bangumi koleksiyon akışı dahil); içerik zaten kütüphanenizde ise **yeşil zeminli tik** rozeti gösterir ve tıklandığında doğrudan düzenleme panelini açar.
- **Android TV Desteği:** `tvClickable` ile D-pad odağı ve odak çerçevesi tam desteklenir.
- **Başlıkta Toplu Ekleme Butonu:** Grid, liste ve sayfa kaydırılırken beliren yüzen başlıkta filtre butonunun yanına toplu ekleme ikonu (`Icons.Rounded.PlaylistAdd`) eklendi. Tek tıkla sayfadaki tüm sonuçlar listeye eklenir; liste boşken buton otomatik olarak pasifleşir.
- **Birleşik ViewModel Mimarisi & Özet Bildirim:** `AppViewModel` içinde tekil ve toplu ekleme aynı doğrulama çekirdeğini (`insertApiSelection`) paylaşır. Toplu ekleme sonucunda ekran bildirim yağmuruna tutulmaz; tek bir özet bildirim (*"N eklendi • M zaten listede • K eklenemedi"*) sunulur.

### 📌 "Listem" Kaynak Seçimi & Sekme Kalıcılığı Düzeltmesi
- **Açılış Yarış Durumu (Race Condition) Çözüldü:** `loadFilters` çağrısı `LaunchedEffect` gecikmesinden çıkarılarak ilk kompozisyondan önce senkron çalıştırıldı. Listem ekranının pager 0 ("Tümü") değeriyle açılıp kullanıcının kayıtlı seçimini ezmesi sorunu tamamen giderildi.
- **Koşulsuz Sıra Kayması Kaldırıldı:** Eski sürümlerden kalan `migrateLegacyMyListTabIndex` her açılışta koşulsuz çalışarak sekme indeksini bir yana kaydırıyordu; bu göç kodu kaldırılarak kullanıcının son seçtiği kaynak sekmesi (Bangumi, MAL, AniList vb.) tam olarak korunur hâle getirildi.

### 🖼️ Keşfet Vitrini Nuvio Tarzı Yeniden Tasarlandı (`HeroVitrinLayout`)
- **Her Zaman Tam Kaplama (`ContentScale.Crop`):** Vitrin görseli artık kutusuna tam oturur; oran farkından dolayı yanlarda boş kutu veya ince şerit oluşması engellendi.
- **Dikey Telefonda Ayrılmış Bilgi Yuvası:** Üstte sabit oranlı görsel şeridi, altta başlık, logo, alt başlık ve meta satırları için ayrılmış sabit alan sunulur. Başlık ve logo hiçbir zaman görselin üzerine binmez.
- **Bulanık Ambient Arka Plan Katmanı:** Keskin görselin altında kalan alan aynı görselin bulanık kopyasıyla doldurulur (Coil disk önbelleği kullanıldığı için ek ağ trafiği yaratmaz).
- **Nuvio'dan Alınan Scrim'ler:** Nuvio Mobile dikey gradyanı ve Nuvio TV sol yatay gradyanı uygulanarak metin okunabilirliği her arka planda garantiye alındı.
- **Görselden Bağımsız Geometri & Zıplamasız İskelet:** Bant yüksekliği cihaz boyutlarına ve yazı ölçeğine göre türetilir; görsel yüklenirken ekran boyutu değişmez veya zıplamaz.

### 🎬 İzle & Akış (Stream) Eklenti Teşhis İyileştirmeleri
- **Açıklayıcı Hata Mesajları:** CloudStream eklenti yükleme hatalarında genel "DEX/manifest hatası" yerine gerçek kök neden (`NoClassDefFoundError`, `ClassNotFoundException` vb.) doğrudan kartta ve "Yeniden dene" akışında gösterilir.
- **Akış ve Başlık Eşleştirme:** `CsTitleMatcher` başlık ve yıl normalizasyonu, Stremio akış yönlendirmesi (`StremioStreamRouting`, `StremioAddonRoutes`) ve Wikidata IMDB ayrıştırıcı iyileştirmeleri yapıldı.

---

## 🇬🇧 English

### ➕ Quick "Add to List" Shortcut in "See All" Pages (Grid & List Views)
- **Compact Poster Badge (`KitsugiQuickAddButton`):** A sleek, non-intrusive 30dp circular action button placed at the top-right (TopEnd) corner of media posters.
- **Flawless Parity Across Views:** Integrated identically into `KitsugiExploreMediaCard` (grid view — TV, landscape, portrait variants), `KitsugiRankingMediaCard` and `DetailedSeasonalMediaCard` (list view), taking up zero extra layout footprint.
- **State-Aware & Edit Panel Access:** Shows a `+` badge with accent tint to add new items directly (supporting Bangumi flows); transforms into a **green checkmark badge** when already in your list, tapping it immediately opens the edit dialog.
- **Android TV Ready:** Fully navigable via D-pad with focus highlight using `tvClickable`.
- **Batch "Add All to List" Action:** Added a dedicated batch action button (`Icons.Rounded.PlaylistAdd`) next to the filter button in all three headers (grid, list, and floating sticky header). Adds all visible items in a single click and disables automatically when empty.
- **Unified Architecture & Aggregated Notifications:** Single and batch addition share the same robust validation core (`insertApiSelection`) in `AppViewModel`. Displays a single clean summary notification (*"N added • M already in list • K could not be added"*) instead of spamming alerts.

### 📌 "My List" Tab Selection Persistence Fix
- **Race Condition Resolved:** `loadFilters` now executes synchronously prior to first composition, preventing the pager from initializing at 0 ("All") and overwriting user preferences.
- **Eliminated Unconditional Tab Shift:** Removed legacy index migration that shifted the active tab index on every app launch. Your selected library source (Bangumi, MAL, AniList, etc.) is reliably preserved across app restarts.

### 🖼️ Explore Showcase Redesigned (Nuvio-Style Layout - `HeroVitrinLayout`)
- **Full Coverage (`ContentScale.Crop`):** Showcase artworks now consistently fill their frame, eliminating side pillarboxing and collapsed thin strips.
- **Dedicated Information Slot on Phones:** Features a fixed-aspect visual band on top with a dedicated metadata/title zone below, ensuring logos and titles never obscure the artwork.
- **Ambient Blurred Layer:** Spaces below sharp artwork are smoothly filled with a blurred copy using Coil's cache with zero bandwidth penalty.
- **Nuvio Gradient Scrims:** Incorporates Nuvio Mobile's vertical gradient and Nuvio TV's left-hand horizontal gradient for crystal-clear readability.
- **Device-Driven Geometry:** Container height is calculated strictly from screen geometry and font scale; loading skeletons match the final layout to prevent layout jumping.

### 🎬 Stream & Plugin Diagnostic Improvements
- **Transparent Plugin Errors:** CloudStream plugin loader now surfaces the exact root cause (`NoClassDefFoundError`, `ClassNotFoundException`, etc.) on the card and retry flows instead of a generic DEX/manifest failure.
- **Matcher & Routing Enhancements:** Optimized `CsTitleMatcher` title/year heuristics, refined Stremio stream routing (`StremioStreamRouting`, `StremioAddonRoutes`), and improved Wikidata IMDb parsing.
