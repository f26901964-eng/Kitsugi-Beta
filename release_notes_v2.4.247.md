# Kitsugi-Beta v2.4.247 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🌐 1. Kapsamlı İngilizce Dil Desteği & Arayüz Metinlerinin Yerelleştirilmesi
- **Gömülü (Hardcoded) Türkçe Metinlerin Temizlenmesi:** Kod içerisine doğrudan gömülü kalmış olan yüzlerce Türkçe arayüz metni, Android kaynak sistemine (`R.string`) taşındı. İngilizce dil seçildiğinde Türkçe kalan diyaloglar, ayarlar ve profil sekmeleri tamamen giderildi.
- **41 Dosyada Yerelleştirme Entegrasyonu:** `AuthViewModel`, `KitsugiProfileViewModel`, `AccountSettingsSubPages`, `DownloadsScreen`, `MangaExtensionTab`, `MalProfileExtras`, profil sayfaları (`AniList`, `Bangumi`, `MAL`, `Shikimori`, `Kitsu`, `Simkl`), CrossSync tanı raporları ve bildirim bileşenleri arayüz diline duyarlı hale getirildi.
- **Dinamik Tarih & Göreli Zaman Desteği:** Profil geçmişlerindeki "az önce", "5 dk önce", "3 saat önce", "Dün" gibi zaman ifadeleri ve tarih biçimlendirmeleri (`DateTimeFormatter`) kullanıcının seçtiği yerel ayarlara (`KitsugiStrings.locale()`) bağlandı.
- **Dil Dosyaları Paritesi:** `values/strings.xml` ve `values-en/strings.xml` kaynaklarına 381 yeni simetrik anahtar eklendi; dize kaçış karakterleri (`\'`) AAPT uyumlu olarak standardize edildi.

### 🏢 2. Dinamik Bangumi Kurum Çözümleyici (`BangumiOrgResolver.kt`)
- **API Tabanlı Kurum Adı Çözümleme:** Bangumi infobox alanlarından serbest metin olarak gelen şirket adları (`株式会社マッパ`, `集英社`, vb.), Bangumi'nin resmi `search/persons` (type=2 / Şirket) API ucu üzerinden otomatik olarak küresel adına (`MAPPA`, `Shueisha`, vb.) çözülür.
- **Kesintisiz & Süre Korumalı Mimari:** Kurum çözümleme işlemi 2,5 saniye tavan süre ve negatif önbellek desteğiyle arka planda yürütülür; ağ gecikmelerinde veya bilinmeyen kayıtlarda orijinal ad korunur, sayfa açılışı asla takılmaz.
- **Arama Türü Filtresi (`BangumiApiClient.kt`):** `searchPersons` fonksiyonuna `type` (1=kişi, 2=şirket, 3=grup) filtreleme parametresi eklendi.

### 🎭 3. Bangumi Kadro ve Karakter Rolleri Sözlüğü (`BangumiRoleDictionary.kt`)
- **Kadro ve Rol Çevirisi:** Bangumi API'lerinden ham Çince/Japonca gelen görev ve karakter etiketleri (`主角`, `配角`, `原作`, `监督`, `脚本`...) arayüz diline (TR/EN) uygun olarak yerelleştirildi.
- **Simetrik Rol Kaynakları:** `bangumi_role_*` ve `bangumi_org_*` tanımları için 154 yeni kaynak anahtarı dil dosyalarına eklendi (toplam 6.944 dize %100 parite).

### 🖼️ 4. Vitrin (Hero Section) TMDB + Fanart.tv Görsel Çözümleyici & Akıllı Ekran Uyumu
- **Tüm Kaynaklar İçin TMDB + Fanart.tv Çözümleyici (`KitsugiHeroArtworkResolver.kt`):** Bangumi, AniList, MyAnimeList (Jikan), Kitsu, Shikimori, Simkl, TMDB, Tümü modu, TV vitrini ve Eklenti (Addon) vitrinleri dahil her vitrin öğesi için hem TMDB (`images`, `external_ids`, `alternative_titles`) hem de Fanart.tv (`tvposter`/`movieposter`, `showbackground`/`moviebackground`) paralel olarak sorgulanır.
- **En Kaliteli Dikey Poster & Yatay Arka Plan Otomatik Seçimi:**
  - **Dikey Poster:** TMDB (/original 2000x3000 / 1000x1500) ve Fanart.tv (1000x1426) adayları çözünürlük, 2:3 dikey oran, dil tercihi, sezon uyumu ve topluluk oylarına göre puanlanarak en yüksek kaliteli dikey poster seçilir.
  - **Yatay Arka Plan:** TMDB (/original 4K 3840x2160 / 1080p) ve Fanart.tv adayları çözünürlük (4K > 2K > 1080p), 16:9 sinematik oran, yazısız (textless) temizlik ve oylara göre puanlanarak en yüksek kaliteli yatay arka plan seçilir.
- **Kaynak Bazlı Hatalı/Düşük Çözünürlüklü Görsel Düzeltmeleri:**
  - **Bangumi:** API'nin yalnızca dikey kapak döndürmesine rağmen bunun backdropUrl sanılması düzeltildi; tam boy large (`/pic/cover/l/`) kapak birincil yapıldı.
  - **Kitsu & Shikimori & TMDB:** Kitsu'da medium yerine large/original poster ve cover önceliklendirildi; Shikimori vitrin posterleri fullUrl (originalUrl) sürümüne yükseltildi; TMDB /w500 ve /w1280 adresleri vitrinde /original tam çözünürlüğe yükseltildi.
- **Cihaz Boyutu ve Ekran Yönü Uyumu (`KitsugiHeroSection.kt`):**
  - **Dikey Telefon (`aspect < 1.10`, dikey mod):** Vitrinde birincil olarak en kaliteli dikey poster (`posterUrl`), yedek olarak yatay arka plan kullanılır.
  - **Yatay Telefon, Tablet ve TV (`aspect >= 1.10` veya yatay mod):** Vitrinde birincil olarak en kaliteli 16:9 yatay arka plan (`backdropUrl`), yedek olarak dikey poster kullanılır.
- **Akıllı Hizalama & Çift Katmanlı Kesilme Koruması:**
  - Geniş ekranlarda 16:9 arka planlar ve dikey posterler karakterlerin başını kesmeyecek şekilde üst-orta odaklama (`BiasAlignment(0f, -0.32f)` / `BiasAlignment(0f, -0.25f)`) ile konumlandırılır.
  - Geniş vitrin alanında yalnızca dikey görsel bulunan yapımlarda arkada yumuşak atmosferik dolgu (`alpha 0.30`) + önde `ContentScale.Fit` ile kesilmemiş tam boy dikey görsel sergilenir.
- **Berrak Görünüm (Scrim İyileştirmesi):** Üst üste binen 3 katmanlı koyu gradyan kaldırıldı; görselin üst %60'lık alanı tamamen berrak ve açık bırakıldı. Dikey tablette sol yarıyı karartan yatay gradyan kaldırıldı (yalnızca yatay modda metin okunurluğu için hafifletilmiş geçiş uygulanır).

### 🧩 5. CloudStream Eklenti Çalışma Zamanı ve Tanı Düzeltmeleri
- **Dinamik eklenti ABI'si:** Serialization runtime'ı artık CS3 eklentilerinin bağlandığı host API ile aynı classloader parent'ı üzerinden görünür. `kotlinx.serialization-core` doğrudan bağımlılık olarak eklenip R8 tarafından silinmeye/yeniden adlandırılmaya karşı korunur; bu, raporlarda görülen `KSerializer` `NoClassDefFoundError` sorununu hedefler.
- **Yükleme ön kontrolü:** Eklenti çalıştırılmadan önce `MainAPI`, `KSerializer` ve Serialization `Json` sınıflarının plugin classloader'ından görülebildiği doğrulanır; eksikler canlı tanı izine yazılır.
- **Daha doğru hata tanısı:** Linkage/çalışma zamanı uyumsuzluğu (`KTS-CSP-9110`) ve hesap gereksinimi (`KTS-CSP-9109`) ayrı hata kodları ve tanı sayaçları aldı. HTTP 401 ile raporda görülen giriş gereksinimi metinleri artık yanlışlıkla boş arama olarak gösterilmez; genel HTTP 403 ise Cloudflare kanıtı sayılmaz.
- **Parser imzaları:** Yanıttaki bozuk/uyumsuz içerik belirtileri ve HLS başlık hataları parser kategorisine eklenmiştir.
- **Testler:** Error classifier ve plugin diagnostic runner için çalışma zamanı uyumsuzluğu, kimlik doğrulama ve özet sayaç testleri eklendi.

> Alan adı/DNS erişilebilirliği ve sağlayıcı hesabı gereksinimleri uzak sitelerin durumuna bağlıdır; doğrulanmamış eklenti alan adları bu sürümde tahmin edilerek değiştirilmemiştir.

### 💾 6. Depolama Ekranına "İndirilenler" Bölümü & Toplu Silme
- **Görünmez Dosyalar Artık Ölçülüyor:** Ayarlar > Depolama ekranı yalnızca önbellekleri gösteriyordu; indirdiğiniz videolar, görseller ve altyazılar hiçbir yerde görünmüyordu. Yeni **İndirilenler** bölümü üçünü ayrı ayrı boyut ve dosya sayısı olarak listeler:
  - **İndirilen videolar:** `Download/Kitsugi/Video` altındaki bölümler (uygulama içi kopyalar + dışa aktarılan `.mp4` dosyaları) ve eski `cacheDir/Kitsugi_downloads` klasörü.
  - **İndirilen görseller:** Galeriden cihaza kaydettiğiniz resimler (`Download/Kitsugi/Images`, `Pictures/Kitsugi/Images`, uygulama dış klasörü, eski `Download/Kitsugi`).
  - **İndirilen altyazılar:** Video indirmelerinin yanındaki `subs/` klasörleri + `Download/Kitsugi/Subtitles`.
- **Toplu Silme ve Yıkıcı Onay Penceresi:** Her satır ve yeni **"Tüm indirilenleri sil"** seçeneği, geri alınamaz işlem olduğu için detaylı onay penceresi açar (neyin silineceği, hesap/liste/geçmişin korunacağı açıkça belirtilir). İşlem bittiğinde boyutlar anında tazelenir ve bildirim gösterilir.
- **Güvenli Ayrım:** İndirilen dosyalar önbellek listesine dahil edilmemiştir; "Güvenli önbellekleri temizle" düğmesi ve otomatik bakım döngüsü çevrimdışı arşivinize asla dokunmaz.
- **Kalan "Hayalet" Kopyaların Temizlenmesi (`DownloadExportNaming.kt`):** Tek bölüm silindiğinde dışa aktarılan `.mp4` ve altyazı dosyalarının diskte kalarak gizli şişme yaratması engellendi. Video, altyazı kopyaları ve MediaStore kayıtları tek elden eksiksiz temizlenir.

### 📅 7. Bu Hafta Yayında Takvimi — Izgara Kartı Çerçeve Taşması Düzeltmesi
- **İki Satır Esnek Metin:** Izgara görünümündeki kartların alt satırında yer alan tarih/saat bilgisi (`2026-10-11 01:00 • Yayında`), kart genişliğine sığmadığında `Yayı...` şeklinde kesiliyordu. `AiringTimeText` bileşenine `maxLines = 2` ve esnek ağırlık (`weight(1f)`) eklenerek metnin iki satıra kadar düzgün sarılması sağlandı.
- **Sabit Yükseklik & Düzgün Hizalama:** Izgara bilgi satırı sabit `34.dp` yüksekliğe alınarak tüm kartların aynı boyda kalması garanti edildi; saat ikonu üst satırla hizalandı.

---

## 🇬🇧 English

### 🌐 1. Comprehensive English Localization & Hardcoded String Extraction
- **Hardcoded String Elimination:** Migrated hundreds of hardcoded Turkish UI strings across the codebase into standard Android string resources (`R.string`), resolving lingering Turkish text when English is selected.
- **Deep 41-File Integration:** Fully localized dialogs, settings, and profile tabs across `AuthViewModel`, `KitsugiProfileViewModel`, `AccountSettingsSubPages`, `DownloadsScreen`, `MangaExtensionTab`, `MalProfileExtras`, provider profiles (`AniList`, `Bangumi`, `MAL`, `Shikimori`, `Kitsu`, `Simkl`), CrossSync diagnostic summaries, and notification cards.
- **Localized Dates & Relative Timestamps:** Dynamic relative time labels ("just now", "5 min ago", "yesterday") and calendar dates now format using the active user locale (`KitsugiStrings.locale()`).
- **Resource Parity:** Added 381 symmetric string resources across both `values/strings.xml` and `values-en/strings.xml`, ensuring 100% parity and valid AAPT apostrophe escaping.

### 🏢 2. Dynamic Bangumi Organization Resolver (`BangumiOrgResolver.kt`)
- **API-Driven Organization Resolution:** Resolves raw free-text company names from Bangumi infoboxes (e.g. `株式会社マッパ`, `集英社`) to their canonical global names (`MAPPA`, `Shueisha`) via Bangumi's `search/persons` API (`type = 2`, company).
- **Non-Blocking Architecture:** Operates with a strict 2.5-second timeout and negative cache; network failures or unresolvable entries gracefully preserve the original name without blocking page loads.
- **Entity Type Filtering (`BangumiApiClient.kt`):** Added `type` filtering (1=person, 2=company, 3=group) to `searchPersons`.

### 🎭 3. Bangumi Cast & Staff Role Dictionary (`BangumiRoleDictionary.kt`)
- **Role Localization:** Localizes raw Chinese/Japanese role labels from Bangumi endpoints (`主角`, `配角`, `原作`, `监督`, `脚本`...) according to the app UI language.
- **Symmetric Role Resources:** Integrated 154 new `bangumi_role_*` and `bangumi_org_*` string resources (scaling to 6,944 strings with 100% TR/EN symmetry).

### 🖼️ 4. Showcase (Hero Section) TMDB + Fanart.tv Artwork Resolver & Smart Aspect Adaptation
- **Universal TMDB + Fanart.tv Resolver (`KitsugiHeroArtworkResolver.kt`):** Across all providers (Bangumi, AniList, MyAnimeList, Kitsu, Shikimori, Simkl, TMDB, All-in-One mode, TV showcase, and Addon showcases), every hero entry concurrently queries TMDB (`images`, `external_ids`, `alternative_titles`) and Fanart.tv (`tvposter`/`movieposter`, `showbackground`/`moviebackground`).
- **Automated High-Resolution Artwork Ranking:**
  - **Vertical Poster:** Evaluates candidates from TMDB (/original 2000x3000 / 1000x1500) and Fanart.tv (1000x1426) based on resolution, 2:3 ratio, preferred language, season matching, and community votes.
  - **Horizontal Backdrop:** Evaluates candidates from TMDB (4K 3840x2160 / 1080p) and Fanart.tv based on resolution (4K > 2K > 1080p), 16:9 cinematic aspect ratio, textless clarity, and community scores.
- **Provider Resolution Upgrades & Fixes:**
  - **Bangumi:** Resolved issue where vertical covers were treated as horizontal backdrops; prioritized full-resolution large (`/pic/cover/l/`) vertical covers.
  - **Kitsu, Shikimori & TMDB:** Prioritized large/original over medium in Kitsu; upgraded Shikimori showcase posters to fullUrl; upgraded TMDB /w500 and /w1280 assets to /original full fidelity.
- **Orientation & Form-Factor Adaptation (`KitsugiHeroSection.kt`):**
  - **Portrait Phone (`aspect < 1.10`, portrait mode):** Prioritizes vertical poster (`posterUrl`), falling back to backdrop.
  - **Landscape Phone, Tablet & TV (`aspect >= 1.10` or landscape):** Prioritizes cinematic 16:9 backdrop (`backdropUrl`), falling back to vertical poster.
- **Smart Alignment & Dual-Layer Vertical Containment:**
  - Biased crop alignment (`BiasAlignment(0f, -0.32f)` / `BiasAlignment(0f, -0.25f)`) protects character faces and head levels from being cut off on ultra-wide bands.
  - In wide showcase containers with only vertical artwork (e.g. manga covers or titles without backdrops), dual-layer presentation displays an atmospheric background fill with `ContentScale.Fit` uncropped foreground presentation.
- **Crystal-Clear Scrim Presentation:** Removed the 3-layer stacking dark gradients, keeping the top 60% of showcase imagery crisp and bright. Removed the harsh horizontal scrim on portrait tablets.

### 🧩 5. CloudStream Plugin Runtime & Diagnostics Fixes
- **Dynamic plugin ABI:** The serialization runtime is now visible to CS3 plugins through the same host API classloader parent. `kotlinx.serialization-core` is an explicit dependency and is kept from R8 removal/renaming, targeting the reported `KSerializer` `NoClassDefFoundError`.
- **Load preflight:** Before plugin code runs, the loader verifies that `MainAPI`, `KSerializer`, and Serialization `Json` are visible through the plugin classloader; missing classes are recorded in the live diagnostic trace.
- **More accurate classification:** Linkage/runtime incompatibility (`KTS-CSP-9110`) and account requirements (`KTS-CSP-9109`) now have separate codes and diagnostic counters. HTTP 401 and reported login prompts no longer appear as empty searches; a generic HTTP 403 is still not treated as proof of Cloudflare protection.
- **Parser signatures:** Added malformed/unexpected response markers and HLS header errors to the parser category.
- **Tests:** Added classifier and diagnostic-runner coverage for runtime incompatibility, authentication requirements, and summary counts.

> Domain/DNS availability and provider-account requirements depend on remote services. This release does not guess or substitute unverified plugin domains.

### 💾 6. New "Downloads" Section on the Storage Screen & Bulk Deletion
- **Invisible Files Are Now Measured:** Settings > Storage previously only listed caches; downloaded videos, images, and subtitles were unlisted. The new **Downloads** section lists all three with dedicated file size and item counts:
  - **Downloaded videos:** Episode directories under `Download/Kitsugi/Video` (in-app copies + exported `.mp4` files) and legacy `cacheDir/Kitsugi_downloads`.
  - **Downloaded images:** Gallery images saved locally (`Download/Kitsugi/Images`, `Pictures/Kitsugi/Images`, app external folder, legacy `Download/Kitsugi`).
  - **Downloaded subtitles:** `subs/` folders next to video downloads plus `Download/Kitsugi/Subtitles`.
- **Bulk Delete with Destructive Confirmation:** Every download row and the new **"Delete all downloads"** action open an explicit confirmation dialog detailing exactly what will be removed while guaranteeing accounts, lists, and history remain untouched. Sizes refresh dynamically upon completion.
- **Safe Isolation:** Downloaded items are kept strictly separate from the cache lists; "Clear safe caches" and 6-hour automated maintenance routines never touch offline downloads.
- **Fixed "Ghost" Copies (`DownloadExportNaming.kt`):** Single-episode deletion previously left exported `.mp4` files and companion subtitles in public folders, causing silent disk bloat. File naming and lifecycle management are now unified, ensuring video copies, subtitles, and MediaStore records are cleanly expunged together.

### 📅 7. Airing Calendar Grid Card — Frame Overflow & Text Wrap Fix
- **Two-Line Flexible Wrapping:** In the weekly airing calendar's grid layout, the airing date/time stamp (`2026-10-11 01:00 • Airing`) previously clipped with ellipsis (`Air...`) when card widths were narrow. `AiringTimeText` now supports `maxLines = 2` and flexible weighting (`weight(1f)`), wrapping gracefully across two lines without text truncation.
- **Consistent Heights & Alignment:** The metadata info row now uses a fixed `34.dp` height so all grid cards maintain perfectly uniform dimensions; the clock icon aligns with the top text baseline.



