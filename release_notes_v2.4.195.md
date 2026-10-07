## ⚡ Keşfet Kaynak Alanları, Listem İyileştirmeleri & Akış Doğruluk Düzeltmesi (v2.4.195) (TR)

### 🌐 Keşfet "Tümü" — Kaynak Bazlı Keşif Alanları & Kategori Gruplaması
1. **Kaynaklara Özel Bağımsız Alanlar:** AniList, MyAnimeList, TMDB, Simkl, Kitsu ve Shikimori platformları birbirine karıştırılmadan kendi logolu, renk vurgulu bağımsız başlıklarına kavuştu.
2. **Kategori Ayrımı:** Trendler, en iyiler, popülerler, sezon içerikleri ve ilgili platformun desteklediği kategoriler doğrudan kendi kaynağının altında listelenir.
3. **Sabit Kaynak & Kategori Çubuğu:** Ekranın üstündeki sabit çubukla doğrudan istenilen platforma (AniList, MAL, TMDB vb.) veya kategori şeridine anında atlanabilir; uzun sayfalarda sayfa başına dönüş düğmesi eklendi.
4. **Bağımsız Daraltma & Dayanıklı Durum:** Her kaynak alanı ayrı ayrı daraltılıp genişletilebilir; daraltma durumları ekran döndürmelerinde veya sayfa değişimlerinde korunur.
5. **Akıllı "Tümünü Gör":** Çoklu görünümdeki "Tümünü Gör" butonu artık doğru kaynağın ve kategorinin devam sayfasını açar.
6. **İzole Hata Yönetimi & Yeniden Deneme:** Bir kaynak yüklenemezse veya hata verirse diğer kaynaklar görüntülenmeye devam eder; yalnızca sorunlu kaynak tek dokunuşla yeniden denenebilir.
7. **Büyük Ekran & TV Uyumu:** Ortak kaynak alanları mobil, tablet ve Android TV D-pad gezinmesiyle tam uyumlu hale getirildi.

### 📋 Listem "Tümü" Sekmesi, Temsilci Kaynak & Simkl TMDB Detay Geçişi
1. **Sola Kaydırma & Sekme Sırası Düzeltmesi:** "Tümü" sekmesi gerçekten ilk sıraya (`index 0`) taşındı. Pager ile üst buton dizilimi eşitlenerek Tümü sayfasından sola kaydıramama sorunu giderildi.
2. **Öncelikli Temsilci Kaynak:** Birleşik kütüphanede aynı yapımın farklı servislerdeki kayıtları birleştirilirken öncelik zinciri uygulandı: `AniList > MAL > Kitsu > Shikimori > Simkl > TMDB`. Animelerde otomatik olarak AniList öne çıkarken, dizi ve filmlerde doğal olarak Simkl/TMDB temsilci kalır.
3. **Hızlı Simkl Detayları (TMDB Entegrasyonu):** Simkl kayıtlarının ayrıntı sayfaları doğrudan TMDB üzerinden açılır (`tmdbId`, ARM ID çözücü veya arama). Simkl'in yavaş API yanıtı detay ekranını geciktirmez.
4. **Varsayılan Manuel Kayıt:** Tümü sekmesinde manuel kayıt eklerken varsayılan hedef AniList olarak ayarlandı.

### 🎬 Akış ve Kalite Bilgi Doğruluğu (StreamInfoFix)
1. **"400p" Kök Neden Düzeltmesi:** CloudStream eklentilerinin bilinmeyen kalite döndürdüğünde (`Qualities.Unknown = 400`) ekrana "400p" basması sorunu giderildi.
2. **Sahte Dil ve Rozet Çıkarmalarının Kaldırılması:** "Subaru" kelimesinden "Altyazılı", "Dubai" kelimesinden "Dublaj" basan hatalı alt dize filtreleri kaldırıldı. "Türkçe eklenti ⇒ Altyazılı" varsayımı ve kanıtsız "🎬 Standart" etiketi temizlendi.
3. **Gerçek Ölçüm ve HLS Çözümleme (`StreamProbe`):** HLS master playlist'i üzerinden `#EXT-X-STREAM-INF` (çözünürlük) ve `#EXT-X-MEDIA` (ses/altyazı dilleri) etiketleri incelenerek gerçek veriler rozetlere yansıtılır. Yüklenen altyazılar `CC TR` / `CC EN` olarak gösterilir.
4. **Arayüz ve Filtre Uyumu:** Kart rozetleri, alt sayfa akış seçici, filtre çipleri ve TV arayüzü yeni `StreamInfoResolver` verisiyle senkronize edildi.

### 🔐 Shikimori Yetkilendirme & Gradle Bellek Optimizasyonu
1. **Shikimori 401 Otomatik Yenileme:** Sunucu tarafında geçersiz kılınan token'larda liste çekimi sırasında otomatik refresh yapılarak kullanıcı oturumunun kopması önlenir.
2. **Otomatik RAM Temizliği:** `gradle.properties` içine 60 saniyelik daemon zaman aşımı eklendi; derleme bittikten sonra arkada asılı kalan Gradle/JDK süreçlerinin bellek işgali önlendi.

---

## ⚡ Explore Source Sections, My List Improvements & Stream Info Accuracy (v2.4.195) (EN)

### 🌐 Explore "All" — Dedicated Source Sections & Grouped Categories
1. **Dedicated Platform Sections:** AniList, MyAnimeList, TMDB, Simkl, Kitsu, and Shikimori each receive dedicated branded headers with custom accent colors and descriptions instead of intermixed rails.
2. **Source-Scoped Rails:** Trending, top-rated, popular, seasonal, upcoming, and other platform categories stay strictly grouped under their respective provider section.
3. **Sticky Platform Navigation:** Sticky header bar allows immediate jumping directly to any provider (AniList, MAL, TMDB, etc.) or category rail, with a quick return-to-top shortcut.
4. **Collapsible Sections & Saved State:** Each provider section can be independently collapsed or expanded, preserving state across screen rotations and navigation.
5. **Accurate "See All" Routing:** Tapping "See All" navigates to the dedicated category view matching the exact source and filter.
6. **Isolated Provider Failures & Retry:** If a single provider fails to load or times out, all other providers remain intact; users can retry individual failed providers independently.
7. **TV & Large Screen Support:** Unified source sections fully support Android TV D-pad focus, navigation, and tablet landscape layouts.

### 📋 My List "All" Tab, Representative Hierarchy & Fast Simkl Details
1. **Tab Ordering & Swiping Fix:** The unified "All" tab is now index 0. Synchronized pager and pill tabs eliminate the gesture lock where users could not swipe left from the "All" page.
2. **Smart Representative Provider Hierarchy:** Multi-provider entries deduplicate using prioritized representative selection: `AniList > MAL > Kitsu > Shikimori > Simkl > TMDB`. Anime defaults to AniList, while TV shows and movies naturally maintain Simkl/TMDB representation.
3. **Lightning Fast Simkl Details via TMDB:** Simkl entries now route details through TMDB (`tmdbId`, ARM resolver, or fallback search), removing slow Simkl API bottlenecks.
4. **Default Manual Entry:** Manual additions on the "All" tab default to AniList.

### 🎬 Accurate Stream Information (StreamInfoFix)
1. **"400p" False Badge Root Fix:** Resolved issue where `Qualities.Unknown = 400` in CloudStream plugins resulted in a "400p" label.
2. **Removed Heuristic Audio/Subtitle Inferences:** Fixed substring matching bugs (e.g., "Subaru" triggering Subtitle, "Dubai" triggering Dubbed). Eliminated automatic "Turkish Plugin ⇒ Subtitled" assumption and baseless "🎬 Standard" labels.
3. **Real Stream Probing (`StreamProbe`):** Analyzes HLS playlists for actual `#EXT-X-STREAM-INF` resolutions and `#EXT-X-MEDIA` audio/subtitle tracks. Verified subtitle files display as `CC TR` / `CC EN`.
4. **Synchronized UI & Filters:** Stream cards, bottom sheets, filter chips, and TV screens are fully wired to the accurate `StreamInfoResolver` output.

### 🔐 Shikimori 401 Silent Token Refresh & Memory Optimizations
1. **Shikimori 401 Auto-Recovery:** Automatic token refresh on 401 errors during rate syncing prevents unneeded session expirations.
2. **Gradle/JDK Daemon Idle Cleanup:** Added 60s idle timeout to `gradle.properties` and post-build cleanup scripts to stop lingering Java daemons from holding system RAM.
