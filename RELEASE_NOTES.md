# Kitsugi-Beta — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe (v2.4.229)

### 📖 1. Manga KeiSource & OkHttp 5 Uyumluluk Onarımı (72/77 TR Eklentisi Kurtarıldı)
- **3 Zorunlu KeiSource Interceptor'ı Eklendi:** Keiyoushi `KeiSource` tabanlı eklentilerin host istemcisinden sınıf adına göre şart koştuğu `UncaughtExceptionInterceptor`, `UserAgentInterceptor` ve `CloudflareInterceptor` sınıfları `NetworkHelper` varsayılan zincirine bağlandı. İsimsiz lambda veya sınıf uyuşmazlığı nedeniyle oluşan `IllegalStateException` ve arama/bölüm listesinin boş kalması sorunu giderildi (`NetworkHelper.kt`, `UncaughtExceptionInterceptor.kt`, `UserAgentInterceptor.kt`).
- **OkHttp 5.4 & Brotli/Zstd Uyumluluğu:** Keiyoushi lib 1.6 eklentilerinin talep ettiği `CompressionInterceptor(Brotli, Gzip, Zstd)` ve `zstd-kmp-okio` çağrıları için eksik Brotli / Zstd köprüleri bağlandı; `okhttp-android:5.3.2` ile ikili çakışmalar giderildi.
- **ProGuard Keep Kuralları:** R8 derlemesi sırasında interceptor sınıf adlarının ve OkHttp 5 köprülerinin korunması sağlandı (`proguard-rules.pro`).
- **Bölüm Listesi Hata Şeffaflığı:** `MihonSourceWrapper.fetchChapterList` içerisindeki hatalar artık sessizce yutulup boş liste döndürülmüyor; hata açıkça yüzeye çıkarılarak hatalı kaynak istatistikleri ve sahte "bölüm yok" durumları engellendi (`MihonSourceWrapper.kt`).
- **Kaynak Sağlık Sorgusu Çeşitlendirmesi:** `SourceHealthService` içinde sabit tek sorgu ("one piece") yerine dile göre 3 aday arama sorgusu tanımlandı (`SourceHealthService.kt`).
- **Uçtan Uca Doğrulama & Envanter Raporu:** 1431 eklenti, 2419 kaynak ve 93 Türkçe kaynağın incelendiği teknik rapor ve otomatik tarama script'i eklendi (`docs/audits/MANGA_KAYNAK_UCTAN_UCA_DOGRULAMA_2026-10-10.md`, `scripts/audit_manga_sources.py`).

### 🎬 2. Video Eklenti Kurtarma, Güvenli Arama & Doğru CF Tespiti
- **Otomatik Eklenti Devre Dışı Bırakma Kaldırıldı:** Tanı aracının ağ veya sağlayıcı hatası alan eklentileri veritabanında otomatik kapatarak akış aramasının tamamen boş dönmesine yol açan mekanizma kaldırıldı (`CsPluginDiagnosticRunner.kt`).
- **Tüm Eklentilerin Kapalı Olması Durumu:** Kurulu eklentilerin tamamı kapalıysa akış ekranında net bilgilendirme gösteriliyor; eklenti ayarlarına tüm devre dışı eklentileri tek dokunuşla geri açma seçeneği eklendi (`CloudstreamExtensionTab.kt`, `KitsugiStreamScreen.kt`).
- **Kesintisiz Arama (Failover Search):** Doğrudan seçilen bir eklentinin URL'sinden kaynak çıkmazsa arama orada kesilmiyor; aynı başlık, sezon ve bölüm bilgileriyle etkin tüm eklentilerde aramaya devam ediliyor (`StreamViewModel.kt`).
- **Daraltılmış ve Doğru Cloudflare Tespiti:** Arama sonucu boş olan her eklenti artık CF/WAF engeli sayılmıyor. Yalnızca doğrulanmış bot/WAF koruma imzası saptandığında "Doğrula" butonu gösteriliyor; genel 403/503, DNS veya zaman aşımı hataları ayrı sınıflandırılıyor (`CsProtectionClassifier.kt`).
- **Sunucu Cooldown Süreleri Korunuyor:** Tanı aracı artık yeniden deneme bekleme sürelerini zorla devre dışı bırakmıyor; sunuculara aşırı istek yüklenmesi önlendi.
- **Güvenli Sezon Seçimi:** Sezon aramasında başlıkla eşleşen sonuç bulunamazsa listedeki ilk ilgisiz adayın yüklenmesi engellendi (`CsStreamRunner.kt`).

### 🔐 3. Kullanıcı Hesap Güvenliği ve Veri Erişim Yetkileri
- **Kimliği Doğrulanmış Kullanıcı Veri Erişimi:** Supabase `user_data` tablosuna kimliği doğrulanmış kullanıcılar için CRUD yetkileri ve RLS izinleri tanımlandı (`20261010_user_data_authenticated_grants.sql`).
- **Yedekleme ve Vault CAS Güvenliği:** Hesap senkronizasyonunda Compare-and-Swap güvenlik kontrolleri ve kullanıcı dostu hata mesajı biçimlendiricisi eklendi (`AccountErrorFormatter.kt`, `KitsugiAccountContent.kt`).

---

## 🇬🇧 English (v2.4.229)

### 📖 1. Manga KeiSource & OkHttp 5 Compatibility Fixes (Unblocked 72/77 TR Extensions)
- **Added 3 Required KeiSource Interceptors:** Keiyoushi `KeiSource` extensions enforce strict class-name checks on the host client for `UncaughtExceptionInterceptor`, `UserAgentInterceptor`, and `CloudflareInterceptor`. All three are now properly provided by `NetworkHelper`, eliminating `IllegalStateException` crashes that caused silent empty search and chapter results (`NetworkHelper.kt`, `UncaughtExceptionInterceptor.kt`, `UserAgentInterceptor.kt`).
- **OkHttp 5.4 & Brotli/Zstd Compatibility:** Provided bridge stubs for `CompressionInterceptor(Brotli, Gzip, Zstd)` and `zstd-kmp-okio` APIs expected by extensionLib 1.6; resolved duplicate binary symbols against `okhttp-android:5.3.2`.
- **ProGuard Keep Rules:** Added ProGuard keep entries ensuring interceptor class names and OkHttp 5 bridge classes are preserved during R8 optimization (`proguard-rules.pro`).
- **Chapter Fetch Error Transparency:** `MihonSourceWrapper.fetchChapterList` no longer swallows exceptions into an empty list, ensuring genuine error surfacing and accurate source health metrics (`MihonSourceWrapper.kt`).
- **Localized Health Check Queries:** Enhanced `SourceHealthService` with language-specific candidate query pools instead of a hardcoded single query (`SourceHealthService.kt`).
- **End-to-End Audit & Inventory Report:** Added comprehensive analysis and audit tooling covering 1,431 extensions, 2,419 sources, and 93 Turkish sources (`docs/audits/MANGA_KAYNAK_UCTAN_UCA_DOGRULAMA_2026-10-10.md`, `scripts/audit_manga_sources.py`).

### 🎬 2. Video Plugin Source Recovery & Accurate Protection Classification
- **Removed Auto-Disabling of Plugins:** Diagnostic test runs no longer auto-disable plugins upon network or provider errors, which previously caused all stream sources to go silent (`CsPluginDiagnosticRunner.kt`).
- **Disabled Plugins Safeguard:** Stream screen clearly notifies when all installed plugins are disabled; added a one-tap action in extension settings to re-enable all installed providers (`CloudstreamExtensionTab.kt`, `KitsugiStreamScreen.kt`).
- **Failover Search Across Active Plugins:** If a directly selected plugin URL returns no streams, search continues across all active plugins for the matching title, season, and episode (`StreamViewModel.kt`).
- **Narrowed Cloudflare Classification:** Empty search results are no longer misclassified as Cloudflare/WAF blocks. Verification triggers only on verified bot/WAF challenge signatures; generic 403/503, DNS, and timeouts are reported accurately (`CsProtectionClassifier.kt`).
- **Respected Server Cooldowns:** Diagnostic tool no longer overrides cooldown timers (`ignoreCooldowns = false`), preventing server-side bans.
- **Safe Season Matching:** Prevents loading arbitrary non-matching candidates when season title matching yields no hits (`CsStreamRunner.kt`).

### 🔐 3. Authenticated User Data Access & Account Vault Security
- **User Data Table Permissions:** Added explicit CRUD grants and RLS policies for authenticated users on the `user_data` table (`20261010_user_data_authenticated_grants.sql`).
- **Account Backup Vault CAS:** Integrated compare-and-swap checks and user-friendly error formatting for account sync and backup (`AccountErrorFormatter.kt`, `KitsugiAccountContent.kt`).

---

## 🇹🇷 Türkçe (v2.4.228)

### ⚡ 1. Keşfet & Arama Sayfaları Performans İyileştirmeleri
- **Scroll Geri Besleme Döngüsü Çözüldü (Keşfet & Listem):** Keşfet ve Listem ekranlarında kaydırma yapılırken her pikselde `AppViewModel` üzerindeki durum değişkenlerine (`mutableIntStateOf`) yazılması tüm sekmenin baştan aşağı recomposition'a girmesine ve 100+ öğelik listenin her karede yeniden kurulmasına yol açıyordu. Pozisyonlar düz değişkenlere (`var exploreScrollIndex: Int`) dönüştürüldü; sekmeden ayrılıp geri dönüldüğünde scroll konumu korunurken kaydırma esnasındaki kasma ve takılmalar tamamen ortadan kaldırıldı (`AppViewModel.kt`, `ExploreScreen.kt`).
- **Sonsuz Shimmer Animasyonu Optimizasyonu:** `KitsugiShimmerProvider` bileşenine `active` kontrolü eklendi. Sayfada veri yüklemesi tamamlandığında ve içerik geldiğinde sonsuz animasyon saati (`InfiniteTransition`) tamamen kapatılıyor; statik fırça sağlanarak boşta dururken bile arka planda çalışan ve CPU/pil tüketen gereksiz recomposition döngüleri durduruldu (`KitsugiShimmer.kt`, `ExploreScreen.kt`, `TvAllSourcesHomeContent.kt`).
- **Arama Sayfası Shimmer & Filtreleme Hızlandırması:**
  - "Tümü" aramasında 7 platform aynı anda sorgulanırken her platform rafının kendi sonsuz shimmer animasyonunu kurması yerine sayfa geneli tek paylaşımlı `KitsugiShimmerProvider(active = anySearchLoading)` bağlandı.
  - `filteredResults` (yetişkin içerik ve listemdekiler süzgeci) LazyColumn her çizildiğinde yüzlerce sonucu baştan filtrelemek yerine `remember(...)` ile önbelleğe alındı (`SearchScreen.kt`).
- **Teşhis Raporu:** Keşfet performans analizi ve optimizasyon detayları belgelendi (`KESFET_PERFORMANS_FIX_RAPORU_2026-10-10.md`).

### 📖 2. Manga Hattı Düzeltmeleri & Kompakt Arayüz İyileştirmeleri
- **Kompakt "Oku" Butonu:** Detay sayfasındaki "Oku" butonu, "İzle" butonu gibi kompakt yapıya kavuşturuldu; ekranın tüm satırını kaplaması engellendi (`ApiDetailLeftPanel.kt`, `KitsugiDetailInfoSection.kt`).
- **Siyah Okuyucu Ekranı Giderildi:** Görsel hazır olduğunda ekranın bunu anında gözlemleyebilmesi sağlandı; durum güncelleme (state observation) gecikmelerinden kaynaklanan siyah ekran sorunu çözüldü (`MangaReaderViewModel.kt`, `MangaBrowseViewModel.kt`, `MangaBrowseScreen.kt`).
- **Mihon Görsel İndirme Hattı:** Mihon görselleri artık eklentinin kendi resim isteği (image request), HTTP istemcisi ve oturum çerezleriyle doğrudan indiriliyor (`MihonSourceWrapper.kt`).
- **Kotatsu Önizleme ve Hata İyileştirmeleri:** Kotatsu parser'larında önizleme görsellerinin gerçek sayfa sanılması engellendi, kaybolan bölüm metadataları, bölüm sıralaması ve arka planda gizlenen hatalar giderildi (`KotatsuExtensionAdapter.kt`, `MangaModels.kt`).
- **Doğrudan Kaynak Eşleşmesi:** Manga arama sonuçları artık doğrudan kendi kaynak kimliğiyle açılıyor; aynı isimdeki eklentiler arasında hatalı tahmin ve yanlış kaynak ataması yapılmıyor (`MangaSourceRepository.kt`).
- **HTML Temizliği ve Önbellek Koruması:** Bozuk veya HTML hata yanıtlarının resim olarak önbelleğe alınması engellendi; manga açıklamalarındaki ham HTML etiketleri (`<p>`, `<b>`, `&nbsp;` vb.) arayüzde temizlendi (`MangaCache.kt`, `MangaPageLoaderV2.kt`).
- **121 Türkçe Parser Denetimi & Envanteri:** 121 Türkçe parser statik envantere çıkarıldı ve durum raporu belgelendi (`docs/MANGA_PIPELINE_AUDIT_2026-10-10.md`, `docs/MANGA_TR_PARSER_INVENTORY.md`, `scripts/audit_manga_parsers.py`).
- **Test Kapsamı:** Manga sayfa durumu ve önbellek mekanizmaları için birim testleri entegre edildi (`MangaPageStateTest.kt`, `MangaCacheTest.kt`).

### 🌐 3. Kapsamlı Dil Altyapısı (i18n) Genişletmesi & 3.745 Çift Dilli Kaynak (`strings.xml`, `values-en/strings.xml`, `values-en/arrays.xml`)
- **3.745 Anahtarlık %100 TR / EN Simetrisi:** Kaynak sayısı 2.195'ten 3.745 anahtara çıkarıldı; Türkçe ve İngilizce dil kaynakları arasında birebir anahtar, sıra ve `%1$s` biçim yer tutucusu (placeholder) eşitliği sağlandı.
- **AAPT XML Kaçış & Standardizasyon Onarımları:**
  - 665 adet geçersiz `xml:space="preserve"` attribute'u temizlendi (Android AAPT derleyicisinin `CantBindXML` hatası çözüldü).
  - Metin içi kaçışsız apostroflar (`zoom'u`, `provider's` vb.) Android standartlarına uygun `\'` haline getirildi.
  - Yüzde karakterleri içeren metinler için `formatted="false"` etiketleri düzenlendi.
  - Bangumi etiket senkronizasyon sözlüğü (`sync_bangumi_tag_strings.py`) bozulmadan korundu.

### 📱 4. 14 Yeni Ekran & Diyalog Kaynaklara Bağlandı
- **Sistem ve İndirme Ayarları (`KitsugiSystemSettingsDialog.kt`):**
  - İndirme hız limitleri ("Sınırsız", "50 KB/s", "10 MB/s" vb.), eşzamanlı indirme sayıları.
  - Okuma sonrası otomatik silme ("Devre Dışı", "Son okunan bölüm", "Son 1-10 bölüm").
  - Okurken/İzlerken otomatik ön indirme ("Sonraki 1-10 bölüm / video").
  - Kategori filtreleme & hariç tutma diyalogları ("CURRENT", "PLANNING", "COMPLETED", "ON_HOLD", "DROPPED").
  - DNS sağlayıcıları (Sistem Varsayılanı, Google, Cloudflare, AdGuard, DNS.WATCH, Quad9, DNS.SB, Canadian Shield açıklamaları).
  - Harici indirici tercihleri (Dahili, 1DM, ADM algılama ve durum metinleri).
- **Gelişmiş Arama Filtreleme (`SourceEngineFilterSheet.kt`):** Arama motoru, kaynak filtreleri ve sıralama seçeneklerinde 49 metin yerelleştirildi.
- **Akış ve Video Sayfası (`StreamScreenContent.kt`):** Akış kaynakları, oynatıcı seçimi, hata durumları ve çözünürlük etiketlerinde 24 metin dinamik kaynaklara bağlandı.
- **Eklenti ve Geliştirici Günlükleri (`CsPluginDiagnosticScreen.kt`, `DeveloperLogsDialog.kt`):** Eklenti tanılama raporları, test adımları, log filtreleme ve panoya kopyalama butonları yerelleştirildi.
- **Geri Bildirim (`FeedbackDialog.kt`):** E-posta gönderimi, şablon metinleri ve hata uyarıları.
- **Android TV Ekranları:**
  - TV Aktivite Detayı (`TvActivityDetailDialog.kt`), İnceleme Detayı (`TvReviewDetailDialog.kt`), Manga Eklentileri (`TvMangaExtensionScreen.kt`), QR Giriş Ekranı (`TvQrLoginScreen.kt`), TV Akış Ekranı (`TvStreamScreen.kt`), TV Eklenti Yöneticisi (`TvAddonsScreen.kt`), TV Yardımcı Eşleşme Onayı (`TvCompanionApprovalDialog.kt`) ve QR Kod Ekranı (`TvCompanionQrScreen.kt`).

### 🛠️ 5. Yeni Yerelleştirme Denetim Araçları (`scripts/`)
- **`scripts/audit_hardcoded_turkish.py`:** Kod tabanında kalan Türkçe karakterli hardcoded literal metinleri tarayıp dosya bazında raporlayan denetim aracı eklendi.
- **`scripts/check_localization_resource_parity.py`:** `values` ve `values-en` arasındaki string ve string-array anahtarlarını, format yer tutucularını (`%1$s`) otomatik doğrulayan CI aracı entegre edildi.

---

## 🇬🇧 English (v2.4.228)

### ⚡ 1. Explore & Search Performance Optimizations
- **Resolved Scroll Recomposition Loop (Explore & My List):** Eliminated the feedback loop where updating `mutableIntStateOf` during scrolling triggered full recompositions of the entire tab and rebuilt 100+ items per frame. Replaced scroll positions with plain Kotlin properties (`var exploreScrollIndex: Int`), preserving scroll state upon navigation while delivering stutter-free scrolling (`AppViewModel.kt`, `ExploreScreen.kt`).
- **Infinite Shimmer Animation Optimization:** Added an `active` control parameter to `KitsugiShimmerProvider`. Once content is loaded and placeholders are replaced, the infinite transition clock is completely disabled, substituting a static gradient brush and eliminating wasteful CPU/battery drain (`KitsugiShimmer.kt`, `ExploreScreen.kt`, `TvAllSourcesHomeContent.kt`).
- **Search Screen Shimmer & Filter Caching:**
  - Consolidated multi-platform shimmer instances: wrapped search results with a single shared `KitsugiShimmerProvider(active = anySearchLoading)` across all 7 platform shelves instead of 8 separate infinite transitions.
  - Cached `filteredResults` (adult filter and "in my list" filter) using `remember(...)` to avoid recalculating hundreds of items on every layout pass (`SearchScreen.kt`).
- **Diagnostic Report:** Documented comprehensive analysis and profiling findings (`KESFET_PERFORMANS_FIX_RAPORU_2026-10-10.md`).

### 📖 2. Manga Pipeline Fixes & Compact UI Enhancements
- **Compact "Read" Button:** The "Read" action button on the anime/manga detail screen is now compact like the "Watch" button instead of spanning the entire line (`ApiDetailLeftPanel.kt`, `KitsugiDetailInfoSection.kt`).
- **Resolved Black Reader Screen:** Fixed state update / observation race condition where page states failed to trigger UI rendering upon image readiness (`MangaReaderViewModel.kt`, `MangaBrowseViewModel.kt`, `MangaBrowseScreen.kt`).
- **Mihon Image Pipeline:** Manga page images from Mihon extensions are now fetched using the extension's dedicated image requests, HTTP client, and session headers (`MihonSourceWrapper.kt`).
- **Kotatsu Improvements:** Prevented preview thumbnails from being mistaken as chapter pages, restored missing metadata, fixed chapter ordering, and surfaced previously suppressed error details (`KotatsuExtensionAdapter.kt`, `MangaModels.kt`).
- **Direct Source Mapping:** Manga search results now open strictly with their originating source provider, preventing ambiguous name-based guesses (`MangaSourceRepository.kt`).
- **HTML Stripping & Cache Protection:** Blocked corrupted or HTML error responses from being cached as images; stripped raw HTML tags (`<p>`, `<b>`, `&nbsp;` vb.) from manga synopsis text (`MangaCache.kt`, `MangaPageLoaderV2.kt`).
- **121 Turkish Parser Inventory:** Conducted a comprehensive audit and static inventory of all 121 Turkish manga parsers (`docs/MANGA_PIPELINE_AUDIT_2026-10-10.md`, `docs/MANGA_TR_PARSER_INVENTORY.md`, `scripts/audit_manga_parsers.py`).
- **Unit & Regression Testing:** Added tests for manga page states and cache integrity (`MangaPageStateTest.kt`, `MangaCacheTest.kt`).

### 🌐 3. Comprehensive i18n Expansion & 3,745 Bilingual Resources (`strings.xml`, `values-en/strings.xml`, `values-en/arrays.xml`)
- **Full 100% TR / EN Symmetry with 3,745 Keys:** Resource collection expanded from 2,195 to 3,745 keys, maintaining perfect 1-to-1 key parity and matching `%1$s` positional format tokens across English and Turkish.
- **AAPT XML Escape & Standardization Fixes:**
  - Stripped 665 invalid `xml:space="preserve"` attributes to resolve AAPT compiler XMLStreamException (`CantBindXML`).
  - Escaped raw single quotes (`\'`) for proper Android string compilation (`zoom\'u`, `provider\'s`).
  - Applied `formatted="false"` attributes to strings containing unescaped percentage symbols.
  - Fully maintained Bangumi dictionary generator parity (`sync_bangumi_tag_strings.py --check`).

### 📱 4. Migration of 14 UI Screens & Dialogs to Localized Resources
- **System & Download Settings (`KitsugiSystemSettingsDialog.kt`):**
  - Download speed limits ("Unlimited", "50 KB/s", "10 MB/s", etc.) and concurrent download counts.
  - Auto-removal options ("Disabled", "Last read chapter", "Last 1-10 chapters").
  - Pre-download while watching/reading options.
  - Category inclusion/exclusion dialogs ("Current", "Planning", "Completed", "On Hold", "Dropped").
  - DoH DNS providers and descriptions (System Default, Google, Cloudflare, AdGuard, DNS.WATCH, Quad9, DNS.SB, Canadian Shield).
  - External downloader preferences (Internal, 1DM, ADM detection).
- **Search Engine Filters (`SourceEngineFilterSheet.kt`):** 49 string resources wired for source provider and filter settings.
- **Streaming Screen (`StreamScreenContent.kt`):** 24 string resources wired for stream lists, server states, and resolution tags.
- **Diagnostics & Logs (`CsPluginDiagnosticScreen.kt`, `DeveloperLogsDialog.kt`):** Diagnostic test suites, log filtering, and clipboard actions.
- **Feedback Dialog (`FeedbackDialog.kt`):** Feedback form, email triggers, and templates.
- **Android TV Screens:**
  - TV Activity Detail (`TvActivityDetailDialog.kt`), Review Detail (`TvReviewDetailDialog.kt`), Manga Extensions (`TvMangaExtensionScreen.kt`), TV QR Login (`TvQrLoginScreen.kt`), TV Stream (`TvStreamScreen.kt`), TV Addons (`TvAddonsScreen.kt`), Companion Approval (`TvCompanionApprovalDialog.kt`), and Companion QR (`TvCompanionQrScreen.kt`).

### 🛠️ 5. Localization Verification & Audit Tools (`scripts/`)
- **`scripts/audit_hardcoded_turkish.py`:** Automated scanner identifying remaining hardcoded strings across Kotlin files.
- **`scripts/check_localization_resource_parity.py`:** Automated validator verifying structural, key, and format token parity between locale resource files.

---

## 🇹🇷 Türkçe (v2.4.227)

### 🎯 1. Kaynak Bazlı Arama & Bölüm Mekaniği: Film ve Dizi için Ayrı Boru Hatları (`CsStreamRunner.kt`, `CsTitleMatcher.kt`)
- **Temel İlke:** Tüm kaynaklarda içerik araması artık YALNIZCA çıplak eser adıyla yapılır — sezon/bölüm bilgisi arama sorgusuna ASLA karışmaz. Hiçbir site (Türkçe/yabancı) "X 1. Sezon 1. Bölüm" sorgusunu çözemez; doğru akış oturtuldu: başlık ara → içerik sayfasına gir → (dizi ise) sezon+bölüm sayfasına in → video verilerini çek.
- **Film Mekaniği (ayrı boru hattı):** Filmler sezon mantığıyla depolanmaz; eşleşen içerik sayfası doğrudan video sayfası olarak kullanılır (dataUrl/url → loadLinks), bölüm eşleştirme yapılmaz.
- **Dizi/Anime Mekaniği (ayrı boru hattı):** Yüklenen sayfanın bölüm listesinden hedef S+E seçilir; eklenti bölüm listesi doldurmuyorsa (sayfa-tipli plugin) sayfa URL'si bölüm kapsayıcısı olarak kullanılır.
- **Sezon Sayfası Gezinmesi:** Bölüm listesi dolu ama hedef sezon sayfada yoksa site sezonu ayrı tutuyordur; eklentinin KENDİ search+load akışıyla sezon sayfasına inilir — kaynak-bağımsız, tüm eklentilerde ortak genel adım.
- **Eski Hata Düzeltildi:** Bölüm listesi dolu olup eşleşme yoksa loadLinks'e DİZİ sayfası URL'si veriliyordu — 25 sn'lik link çıkarma bütçesi çöpe gidiyor, kart "akış bulunamadı" diyordu. Artık gerçek sebep tracker'a yazılır ve sezon gezinmesi denenir.
- **Sezon Girdisi Araması (güvenlik ağı):** Sezon ekli sorgular ("X 2. Sezon") asla birincil arama değildir; yalnızca çıplak başlık araması sıfır sonuç döndürürse (sezonları ayrı indeksleyen siteler için) denenir.
- **Sayfa Tipine Göre Boru Hattı:** Yanlış sınıflandırma durumunda (film bayrağıyla gelip kaynak "tek bölüm" dizi girdisi döndürürse — Türkçe sitelerde çok yaygın) boru hattı yüklenen sayfanın gerçek tipine göre seçilir; mekanik bayrağa değil kaynağa bakar.

### 🛡️ 2. Cloudflare WebView Bellek Sızıntısı ve Çökme Riski (`CloudflareInterceptor.kt`, `KitsugiApplication.kt`, `CsCfWarmupManager.kt`)
- **Başarılı Challenge Sonrası WebView Sızıntısı Giderildi:** `cf_clearance` bulunduğunda bekleme sayacı tamamlanıyor, fakat eski timeout kolu yalnızca sayaç hâlâ açıkken `destroy()` çağırıyordu. Başarılı çözüm yolunda WebView açık kalıyor; sonraki challenge'larda native Chromium/RenderThread kaynaklarının birikmesine yol açabiliyordu. Başarı, zaman aşımı ve iptal artık ana thread'de tek seferlik temizliğe gidiyor.
- **Başlangıçta Toplu WebView Açılması Kaldırıldı:** Açılışta 14 adede kadar gizli WebView ile Cloudflare warmup yapılmıyor. Challenge çözümü yalnızca ihtiyaç duyulan host için tembel (lazy) başlatılıyor.
- **Güvenli Teardown:** WebView yok edilmeden hemen önce `about:blank` yüklenmesi kaldırıldı; view hiyerarşisinden sökme, yükü durdurma, client referanslarını kesme ve tek `destroy()` sırası kullanılıyor.

### 🌐 3. Kaynak Bazlı Liste Eşleştirme & Yetişkin İçerik Bulanıklığı (`KitsugiModels.kt`, `AppRoot.kt`, `AppRootDetailPages.kt`)
- **Kaynak Bazlı Ayrım (`matchesInSource`, `firstMatchingInSource`):** Liste aidiyeti ve detay sayfası kontrolleri artık platform kaynağına duyarlıdır. Bangumi (`bgm`, `bgm.tv`) kayıtları Bangumi düzenleyicisine yönlendirilir; Simkl veya AniList eşleşmeleri Bangumi öğelerinin listede görünmesine sebep olmaz.
- **Profil Favorilerinde Başa Dön Butonu:** Profil favorilerindeki "başa dön" butonu, kaydırma öncesinde ve sonrasında paylaşılan alt çubuk görünürlüğünü sıfırlar.
- **Yetişkin İçerik Bulanıklığı Taşıması:** AniList profil favorilerindeki yetişkin medya durumu detay navigasyonuna taşınır; sinematik yükleme posterleri ilk kareden itibaren bulanıklık ayarına uyar.

### 🏷️ 4. Ortak Küresel Etiket ve Tür Çevirileri (`BangumiTagDictionary.kt`, `SearchTranslation.kt`, `GenreLabelFormatter.kt`, `KitsugiTranslations.kt`)
- **Tüm Kaynaklarda Birlik:** AniList, MAL, Kitsu, Shikimori ve Bangumi için etiket ve tür çevirileri tek ortak BangumiTagDictionary ve SearchTranslation tablosu üzerinden hizalandı.
- **Çevrilen ve İyileştirilen Etiketler:** Heteroseksüel, Erkek/Kadın Baş Karakter, Ters Harem, Ağırlıklı Kadro etiketleri, Klon/Klonlar, Uzaylılar, Poliamori, Kuudere, Çıplaklık, Ansambl Kadro, Fantastik, Günlük Yaşam, Romantik, Isekai, Cyberpunk, Kıyamet Sonrası, Orta Çağ ve diğer tüm jargonsal karşılıklar standartlaştırıldı.

### 🗑️ 5. Görsel Galerisinde Üçüncü Buton: Sil (`KitsugiImageGalleryDialog.kt`, `KitsugiImageDownloadHelper.kt`, `DownloadsScreen.kt`)
- **Silme Butonu Eklendi:** Galeri eylem sırası artık **İndir → Paylaş → Sil → Kapat** şeklindedir; kardeşleriyle aynı cam kutu dilinde kırmızı uyarı tonuyla çizilir ve Android TV kumandalarında odaklanabilir.
- **Dikey ve Yatay Eşitliği:** Dikey başlıkta 40.dp, yatay yan panelde 36.dp — boyut/köşe parametreleriyle yönetilen tek `KitsugiGalleryDeleteButton` bileşeni kullanılır.
- **Gerçek Silme:** MediaStore kaydını (`content://`), kullanıcının seçtiği SAF klasöründeki dosyayı ve `Download/Kitsugi/Images` altındaki kopyaları kaldırır; ardından harici galerilerin ölü kareler göstermemesi için `MediaScanner`'ı bilgilendirir.
- **İndirilenler Ekranı Entegrasyonu:** İndirilen resimler galerisinden silme işlemi kareyi anında kaldırır ve listeyi yeniden tarar; yeni grup düzeyinde silme butonu bir başlığın indirilen tüm resimlerini tek işlemde temizler.

### 🖼️ 6. GIF ve Çoklu Format Desteği (`KitsugiImageDownloadHelper.kt`)
- **Yerel Kaynaklar Paylaşılabilir:** `file://` ve `content://` öğeleri doğrudan FileProvider üzerinden iletilir; MediaStore/SAF satırları çözücü üzerinden okunur.
- **Genişletilmiş Format Tespiti:** GIF87a/89a, PNG + APNG, JPEG/MPO, WebP (VP8X), AVIF/AVIS, HEIF/HEIC, BMP ve TIFF formatları tanınır ve hareketli içerikler bozulmadan saklanır.

### 🔞 7. +18 Blur Zorunluluğu Tüm Kaynaklarda (`AdultFlagBackfillMigration.kt`, `ShikimoriAdultResolver.kt`, `TmdbAdultResolver.kt`, `MediaEntryRepository.kt`, `KitsugiApplication.kt`, `AiringNotificationWorker.kt`)
- **Simkl Listesi Onarımı:** Simkl senkronizasyonunun eksik bıraktığı yetişkin bayrağı kanonik kimlik üzerinden `AdultFlagBackfillMigration` ile tamamlanır.
- **Kaynaklar Arası +18 Yayılımı:** `MediaEntryRepository.entriesFlow` satırları `MediaIdentity.keys()` üzerinden eşleştirir; aynı yapımın AniList satırı +18 işaretliyse Simkl/Shikimori/"Tümü" sekmesindeki satırı da bulanıklaşır.
- **İşaretler Asla Silinmez:** `replaceSourcePreservingAdultFlags()` sayesinde otomatik senkron ve bildirim işçisi işaretli bayrakları korur.

### 🧪 8. Testler
- `ImageFormatDetectionTest`, `AdultFlagBackfillMigrationTest`, `MediaEntryRepositoryAdultPropagationTest`.

### 🎨 9. Tam Renk Seçici, Gradyan Vurgu Mekaniği & Otomatik Siyah-Beyaz Kontrast (`KitsugiAccentColorPickerDialog.kt`, `KitsugiAccentSupport.kt`, `Theme.kt`, `KitsugiColors.kt`, `AppSettings.kt`, `SettingsDataStore.kt`)
- **"+" Butonu Tam Panel Açıyor:** Eski "hex kodu girin" dialoğu kaldırıldı; yerine 3 sekmeli zengin panel geldi:
  - **Palet:** 19 renk ailesi × 10 ton (50→900) + siyah/beyaz = **192 renk**.
  - **Özel:** Tam **HSV doygunluk–değer karesi + ton şeridi** (sınırsız renk, isteğe bağlı hex alanı).
  - **Gradyan:** İki renkli açılı lineer gradyan: açı slider'ı + 8 yön ön ayarı (→ ↘ ↓ ↙ ← ↖ ↑ ↗), canlı şerit önizleme, başlangıç/bitiş renk seçimi.
- **Kalıcı Gradyan Mekaniği:** `customAccentColor2` (bitiş rengi) ve `customAccentGradientAngle` (açı) kalıcı olarak saklanır; tüm vurgu zeminlerinde iki renk arasında seçilen açıyla lineer akış uygulanır.
- **Otomatik Siyah ↔ Beyaz Kontrast (`onAccentColor`):** Vurgu zeminindeki TÜM yazı ve ikonlar, seçilen rengin koyuluğuna göre yumuşak (smoothstep) geçişle otomatik siyah veya beyaz kontrast alır (ayar ikonları, aksiyon butonları, oynatıcı overlayleri, FAB'lar vb.).

### 🔄 10. Bangumi Çapraz Eşitleme (Cross-Sync) Tam Entegrasyonu (`AuthViewModel.kt`, `BangumiSyncManager.kt`, `PlatformRateLimiter.kt`, `CrossSyncDisclaimer.kt`)
- **6/6 Platform Desteği:** AniList, MyAnimeList, Simkl, Kitsu, Shikimori ve Bangumi artık tam çapraz senkronizasyon havuzunda birlikte çalışır.
- **Hız Sınırı & Aday İndeksi:** Bangumi API için 400ms hız sınırı tanımlandı; ad eşleştirme ve çoklu başlık aramasında Japonca/orijinal ad desteği eklendi.
- **Akıllı İçe Aktarma:** Senkronizasyon sonrası Bangumi verisi `smartImport` ile yerel veritabanıyla doğrulanır.

### 🈯 11. TMDB/Simkl Kadro Adlarında Yanlış Alfabe Düzeltmesi & Hayali Karakter Görsel Zinciri (`PreferenceHelpers.kt`, `TmdbCreditsClient.kt`, `KitsugiCharacterClient.kt`)
- **Yanlış Alfabe Kökten Çözüldü:** `pickLatinAlias()` artık CJK-olmayan her alfabeyi Latin sanmak yerine `PreferenceHelpers.isLatinText()` ile harflerin gerçekten Latin (Türkçe harfler dahil) olduğunu doğrular; Arapça veya Tayca çeviriler elenerek doğru Latin/Romaji adlar seçilir.
- **Çapraz Kaynak Görsel Zinciri:** TMDB/Simkl animasyon içeriklerinde AniList'in görsel bulamadığı karakterler için sırasıyla MAL/Jikan → Shikimori → Kitsu kaynaklarından doğrulanmış gerçek MAL ID ile görsel çekilir.

### 🏢 12. Stüdyo & Yapımcı Yapım Listesi Sonsuz Sayfalama & Limit Düzeltmesi (`KitsugiStudioClient.kt`, `JikanGateway.kt`, `StudioDetailViewModel.kt`, `StudioDetailPage.kt`, `KitsugiModels.kt`)
- **Tenrai 50 İstek Limiti Koruması:** Tenrai `limit > 50` isteklerinde 400 hatası döndürüyordu; `TENRAI_MAX_LIMIT = 50` ile `JikanGateway.tenraiRest` içinde otomatik sınırlandırılarak yapım listesinin boş gelmesi önlendi.
- **Tüm Kaynaklarda Sayfalama Desteği:** 50'den fazla yapımı olan stüdyolar için MAL/Jikan (`has_next_page`), Shikimori (`page` ve `limit=50`), AniList (`pageInfo.hasNextPage`), TMDB (`movie` ve `tv` discover için `total_pages`) sayfalama desteği entegre edildi.
- **Akıcı Sonsuz Kaydırma & Hata Kurtarma:** Liste ve grid görünümünde sona yaklaşıldığında sonraki sayfa otomatik çekilir; yükleme anında alt ortada minimal yükleme çemberi, ağ hatasında "Devamı yüklenemedi · Tekrar dene" butonu sunulur.

### 🎬 13. Fragman (Ön İzleme) Satır Kalıcılığı & Oynatıcı Kontrolü (`KitsugiDetailThemesTrailerComponents.kt`)
- **Kalıcı Satır Kartı:** Ön İzleme kartında oynatıcı açıldığında "Fragmanı İzle" satırının kaybolması sorunu giderildi; açılış/kapanış müziklerindeki gibi oynatıcının üzerinde görünür kalır.
- **Aktif Durum & Toggle:** Oynatıcı açıkken daire içinde yukarı ok (chevron) ikonu, vurgulu arka plan ve renk gösterilir; satıra tekrar dokunulduğunda oynatıcı kapanır (veya altındaki "Kapat" düğmesi kullanılabilir).
- **Tüm Detay Sayfalarında Geçerli:** Ortak `KitsugiTrailerCard` bileşeni üzerinden hem kütüphaneden açılan medya detayında hem de arama/API detay sayfalarında (anime ve manga) aktif.

---

## 🇬🇧 English (v2.4.227)

### 🎯 1. Per-Source Search & Episode Mechanics: Separate Pipelines for Movies vs Series (`CsStreamRunner.kt`, `CsTitleMatcher.kt`)
- **Core principle:** Content search across ALL sources now uses ONLY the bare work title — season/episode info never enters the search query. No site (Turkish or foreign) can resolve "X Season 1 Episode 1"; the correct flow is established: search title → enter content page → (series) descend to the season+episode page → extract video data.
- **Movie pipeline (separate):** Movies carry no season logic; the matched content page is used directly as the video page (dataUrl/url → loadLinks), with no episode matching.
- **Series/anime pipeline (separate):** The target S/E is picked from the loaded page's episode list; when a plugin fills no episode list (page-style plugin), the page URL is used as the episode container.
- **Season page navigation:** When the episode list is populated but the target season is absent, the site keeps seasons on separate entries; the plugin's OWN search+load flow navigates to the season page — a generic, source-agnostic step applied for every addon.
- **Old bug fixed:** When episodes existed but none matched, the SERIES page URL was fed to loadLinks — the 25s link-extraction budget was wasted and the card showed "no streams found". The real reason is now recorded and season navigation is attempted instead.
- **Season-entry search (safety net):** Season-suffixed queries ("X Season 2") are never primary searches; they run only when the bare-title search returns zero results (sites that index seasons as separate entries).
- **Page-type-driven pipeline:** On misclassification (movie flag but the source returns a "single episode" series entry — very common on Turkish sites), the pipeline is chosen by the loaded page's real type; the mechanics follow the source, not the flag.

### 🛡️ 2. Cloudflare WebView Leak & Crash-Risk Fix (`CloudflareInterceptor.kt`, `KitsugiApplication.kt`, `CsCfWarmupManager.kt`)
- **WebView Leak After Successful Challenges:** The old timeout destroyed the WebView only while the latch was still pending. Once `cf_clearance` succeeded, the latch completed and the timeout skipped destruction, potentially allowing Chromium/RenderThread resources to accumulate. Success, timeout, and cancellation now share one main-thread, exactly-once teardown.
- **No Batch of Startup WebViews:** Startup no longer launches up to 14 hidden WebViews to pre-warm Cloudflare cookies. Challenges are solved lazily for the host that actually needs them.
- **Safer Teardown:** Removed the immediate `about:blank` navigation before destruction; teardown removes the view, stops loading, clears client references, and destroys the WebView once.

### 🌐 3. Source-Scoped List Membership & Adult Content Blur (`KitsugiModels.kt`, `AppRoot.kt`, `AppRootDetailPages.kt`)
- **Source-Scoped Membership (`matchesInSource`, `firstMatchingInSource`):** List membership and detail lookups are scoped by platform source. Bangumi entries open Bangumi's editor, and Simkl or AniList records do not falsely mark Bangumi items as listed.
- **Scroll-to-Top Bottom Bar Reset:** Profile favorites scroll-to-top button now resets the shared bottom-bar visibility before and after scrolling.
- **Adult Content Blur Propagation:** Adult status from AniList profile favorites now reaches detail navigation so cinematic loading animation posters blur properly from the first frame.

### 🏷️ 4. Unified Global Tag & Genre Translations (`BangumiTagDictionary.kt`, `SearchTranslation.kt`, `GenreLabelFormatter.kt`, `KitsugiTranslations.kt`)
- **Consistency Across Providers:** Unified tag and genre definitions for AniList, MAL, Kitsu, Shikimori, and Bangumi across dictionaries, XML string resources, and search maps.
- **Standardized Terms:** Aligned translations for Heterosexual, Protagonist variants, Reverse Harem, Cast types, Kuudere, Nudity, Ensemble Cast, Fantasy, Slice of Life, Romance, Isekai, Cyberpunk, Post-Apocalyptic, and Medieval.

### 🗑️ 5. Third Gallery Button: Delete Downloaded Media (`KitsugiImageGalleryDialog.kt`, `KitsugiImageDownloadHelper.kt`, `DownloadsScreen.kt`)
- **Delete Button Added:** Action row now includes delete with two-step confirmation, MediaStore/SAF cleanup, and index synchronization.
- **Downloads Screen Integration:** Seamless tile removal and group-level bulk deletion.

### 🖼️ 6. Full GIF / Image Format Support (`KitsugiImageDownloadHelper.kt`)
- **Local File Sharing:** FileProvider sharing for local files and raw preservation for animated GIFs, animated WebP, and APNG.

### 🔞 7. Universal +18 Blur Enforcement (`AdultFlagBackfillMigration.kt`, `MediaEntryRepository.kt`, etc.)
- **Cross-Source Propagation:** Canonical identity matching propagates adult flags across all providers and ensures flags are never dropped during sync.

### 🧪 8. Tests
- `ImageFormatDetectionTest`, `AdultFlagBackfillMigrationTest`, `MediaEntryRepositoryAdultPropagationTest`.

### 🎨 9. Full Color Picker, Gradient Accent Mechanics & Auto Black-White Contrast (`KitsugiAccentColorPickerDialog.kt`, `KitsugiAccentSupport.kt`, `Theme.kt`, `KitsugiColors.kt`, `AppSettings.kt`, `SettingsDataStore.kt`)
- **"+" Button Opens Full Panel:** Replaced plain hex dialog with a 3-tab color picker:
  - **Palette:** 19 color families × 10 shades + black/white = **192 colors**.
  - **Custom:** Full **HSV saturation-value square + hue slider** (infinite colors, optional hex input).
  - **Gradient:** Two-color angled linear gradient with slider + 8 direction presets, live preview, start/end color targets.
- **Persistent Gradient Mechanics:** Persisted `customAccentColor2` and `customAccentGradientAngle`; renders gradient flows across buttons, setting icon circles, badges, and previews.
- **Automatic Black ↔ White Contrast (`onAccentColor`):** Contrast on accent backgrounds automatically transitions smoothly between black and white based on luminance.

### 🔄 10. Bangumi Full Cross-Sync Integration (`AuthViewModel.kt`, `BangumiSyncManager.kt`, `PlatformRateLimiter.kt`, `CrossSyncDisclaimer.kt`)
- **6/6 Connected Platform Support:** Full parity across AniList, MyAnimeList, Simkl, Kitsu, Shikimori, and Bangumi.
- **Rate Limiting & Japanese Title Matching:** 400ms rate limiter and candidate title resolution for Bangumi subjects.
- **Post-Sync Verification:** Automated `smartImport` verification for Bangumi library after sync.

### 🈯 11. Fixed Wrong-Script Cast Names & Character Image Fallback Chain (`PreferenceHelpers.kt`, `TmdbCreditsClient.kt`, `KitsugiCharacterClient.kt`)
- **Latin Script Validation:** Replaced naive non-CJK check with `PreferenceHelpers.isLatinText()` to prevent Thai/Arabic aliases from overriding English/Romaji names.
- **Cross-Source Character Image Chain:** For TMDB/Simkl anime entries missing AniList images, resolves portraits in sequence across MAL/Jikan → Shikimori → Kitsu using verified MAL IDs and name tokens.

### 🏢 12. Studio & Producer Works Infinite Pagination & Limit Fix (`KitsugiStudioClient.kt`, `JikanGateway.kt`, `StudioDetailViewModel.kt`, `StudioDetailPage.kt`, `KitsugiModels.kt`)
- **Tenrai 50 Request Limit Safeguard:** Tenrai returned HTTP 400 for `limit > 50`; capped automatically via `TENRAI_MAX_LIMIT = 50` in `JikanGateway.tenraiRest`.
- **Full Cross-Source Works Pagination:** Added continuous pagination for studios with >50 works across MAL/Jikan (`has_next_page`), Shikimori (`page` and `limit=50`), AniList (`pageInfo.hasNextPage`), and TMDB (`total_pages` across movie and tv discover).
- **Infinite Scrolling & Failure Recovery:** Seamless pagination as the user scrolls near the end of grid/list layouts, with a minimal loading indicator and retry button on network failures.

### 🎬 13. Trailer Preview Row Persistence & Player Toggle (`KitsugiDetailThemesTrailerComponents.kt`)
- **Persistent Header Row:** The "Watch Trailer" row remains visible above the expanded player, mimicking the opening/ending theme music player layout.
- **Active State & Tap Toggle:** Features an upward chevron icon and accent highlighting when active. Tapping the row toggles the player closed, alongside the bottom close button.
- **Universal Scope:** Powered by the shared `KitsugiTrailerCard`, functioning seamlessly across both library entries and API search details for anime and manga.

---

## 🇹🇷 Türkçe (v2.4.226)

### 📝 1. Liste Düzenleme Ekranı & Platform Eşitlemesi (`KitsugiEditMediaSheet.kt`, `BangumiSyncManager.kt`, `KitsuSyncManager.kt`, `ShikimoriSyncManager.kt`)
- **Bangumi Platform Entegrasyonu:** Bangumi kayıtları artık "Yerel/Manuel" kitaplık yerine gerçek Bangumi platform şablonuyla açılır; gereksiz manuel başlık ve +18 alanları yerine "Gizli" ve "Etiketler" alanları gösterilir. Notlar ve etiketler Bangumi API'sine senkronize edilir.
- **Kitsu Gizlilik ve Tarih Senkronizasyonu:** Kitsu düzenleme sayfasına "Gizli" seçeneği eklendi. Başlangıç/bitiş tarihleri, kişisel notlar ve gizlilik ayarları hem import edilir hem de Kitsu API'sine gönderilir. Kitsu'da bulunmayan manga cilt satırı gizlendi.
- **Shikimori "Yeniden İzleniyor" ve Tekrar Sayacı:** Shikimori için "Yeniden İzleniyor" durumu ve tekrar sayısı kontrolü eklendi; kişisel metin notu, tekrar sayısı ve cilt bilgileri hem içe aktarılır hem de uzaktaki Shikimori profiline iletilir.
- **Platform Destek Rozetleri & Güvenli Güncelleme:** İlgili platform API'sinin desteklemediği alanlar için düzenleme sayfasında şeffaf bilgilendirme rozetleri gösterilir (örn. Simkl'de not/tarih/favori, MAL'de favori). Yalnızca doldurulan alanlar gönderilir, boş bırakılan alanlar uzaktaki veriyi ezmez.

### 🚀 2. Eklenti Arama & Yayın Sağlayıcı İyileştirmeleri (`CsStreamRunner.kt`, `CsRuntimeInit.kt`)
- **Canlı Domain Koruması & Tablo Güvenlik Ağı:** Yerleşik ve uzak domain tablolarının, eklentinin kendi sağlıklı canlı adresini veya çalışma zamanında bulduğu aynayı (örn. FilmMakinesi, Dizipal, SezonlukDizi) ezmesi engellendi. Eski tablolar artık yalnızca boş, zehirli veya bilinen ölü adresleri kurtarmak için devreye girer; sağlıklı domain'e yalnızca açılışta güncellenen uzak tablo geri-alma korumasıyla dokunabilir.
- **Title-Search Fallback Koruması:** `getLoadUrl()` boş veya tanımsız döndüğünde arama sürecinin kilitlenmesi önlendi; boş dönen native sonuçlar otomatik olarak başlık bazlı aramaya yönlendirilir.
- **Scraper Disk Önbelleği Kaldırıldı:** 50 MB'lık OkHttp disk önbelleği kaldırıldı. Bazı sitelerin arama sayfalarında döndürdüğü `max-age` başlıkları nedeniyle Cloudflare engeline veya boş sonuca takılan aramaların diske kalıcı olarak yazılıp süresiz "0 sonuç" dönmesi sorunu çözüldü (Resmi CloudStream mimarisine hizalandı).
- **Arama Bütçesi & Eşzamanlılık Artışı:** Global `searchSemaphore` bütçesi 12'den 24'e çıkarıldı. 30+ eklenti paralel sorgulanırken sıradaki eklentilerin 20 saniyelik zaman aşımına uğraması engellendi.

### 🎬 3. Kitsu Film Ayrıştırması & TMDB Çakışma Önlemi (`KitsugiEpisodeRatingsRepository.kt`, `ApiResultDetailViewModel.kt`, `MediaEntryDetailViewModel.kt`)
- **Film ve Dizi Kimlik Çakışması Giderildi:** TMDB üzerinde aynı kimlik numarasını paylaşan film ve dizi kayıtları (örneğin *Howl's Moving Castle* filmi ile *Roar* dizisi) birbirinden tamamen ayrıldı. Film kayıtları yalnızca film uç noktalarını sorgular ve TV/SeriesGraph/TVDB yollarına düşmez.
- **Kitsu Alt Tür Tespiti:** Kitsu listelerinde tür bilgisi bulunmayan kayıtlar için Kitsu API üzerinden `subtype` ("movie") sorgusu yapılıp kimlik başına önbelleklenir. Hero, detay logosu ve galeriler doğru türü kullanır.
- **Temiz Medya Galerisi:** Film kayıtlarının Fanart galerilerine TV dizilerinin afiş ve ekran görüntülerinin karışması engellendi.

### 🔔 4. Profil ve Listem Bildirim Butonları & Keşfet Zarı (`AppRootTabPages.kt`, `ExploreScreen.kt`, `MyListComponents.kt`, `MyListScreen.kt`, `KitsugiProfileScreen.kt`)
- **Profil ve Listem Bildirim Entegrasyonu:** AniList, MAL veya Simkl hesaplarından biri bağlı olduğunda, Profil ekranında istatistik butonunun soluna ve Listem ekranında zar butonunun soluna bildirim butonu eklendi.
- **Keşfet Zarı Bağımsızlaştırıldı:** Keşfet ekranında bildirim butonu açıkken de rastgele keşfet (🎲) zarı görünür kalır ve bildirimin solunda konumlandırılır.

### 🌐 5. Bangumi "Diğer Adlar" & CJK Varyantları (`BangumiLocalizedName.kt`, `KitsugiSimklDetailClient.kt`)
- **Japonca, Çince ve Korece Alternatif Adlar Geri Getirildi:** "Diğer Adlar" / eşanlamlılar listesindeki Latin-harf zorunluluğu kaldırılarak Japonca, Çince ve Korece özgün varyantlar yeniden görünür hale getirildi. Yalnızca ana başlıkla birebir aynı olan tekrarlar elenir.

### 🏷️ 6. Bangumi Etiket & Tür Sözlüğü (`BangumiTagDictionary.kt`, `KitsugiTranslations.kt`, `strings.xml`)
- **%100 Resmî Meta Etiket Kapsamı:** Bangumi'nin 158 resmî meta etiketi (kaynak, tip, bölge, tema, hedef kitle, oyun türü, platform, kitap, müzik) eksiksiz olarak Türkçe ve İngilizceye çevrildi.
- **817 Kullanıcı Etiketi Desteği:** En popüler 817 kullanıcı etiketi Türkçe ve İngilizce karşılıklarıyla sözlüğe eklendi. Yazım varyantları (ör. `催泪` / `催涙` / `Duygusal`) tekilleştirildi.
- **Okunabilirlik Sıralaması:** Çevrilmemiş CJK etiketler listenin sonuna ötelenerek önce kullanıcının dilindeki etiketlerin görünmesi sağlandı.

### 🎨 7. Bangumi ve Simkl Vitrin Logoları & Çok Kaynaklı Zengin Galeriler (`HeroLogoPolicy.kt`, `MediaGalleryIdentity.kt`, `KitsugiPersonImageAggregator.kt`)
- **Vitrin Görsel Logoları:** Bangumi ve Simkl vitrinlerinde grafik logo varsa metin başlık yerine logo gösterilir.
- **Güvenli Çapraz Kimlik Eşleştirme:** Bangumi subject ID'si asla MAL ID olarak kullanılmaz; Simkl sonuçlarında TMDB kimliği korunur.
- **Karakter, Ekip ve Seslendirmen Galerileri:** MAL ID yoksa ad ve alternatif adlarla Jikan eşleşmesi yapılarak zengin galeriler oluşturulur.

### 🌟 8. Keşfet: "Yakında Yayında" Şeridi & Mantıksal Kaynak Rafları (`ExploreScreen.kt`, `AllSourcesExplore.kt`, `ExploreCategories.kt`)
- **Şerit Konumlandırma Standardı:** "Yakında Yayında" geri sayımlı şeridi, tüm kaynak sayfalarında kategori kısayollarının hemen altına taşındı.
- **Mantıksal Raf Hiyerarşisi:** Anime Rafları → Manga Rafları → Kaynağa Özgü Raflar (Manhwa, Novel, Simkl) düzenine kavuşturuldu.
- **Eksik Kısayollar:** "En Yüksek Puanlı Animeler" ve "En Yüksek Puanlı Mangalar" kısayolları eklendi.

### 🗓️ 9. Tüm Kaynaklarda Birleşik Yayın Tarihi ve Geri Sayım Mantığı (`NextAiringFormat.kt`, `DetailSharedComponents.kt`, `NextAiringChip.kt`)
- **Tek Ortak Biçim Katmanı (`NextAiringFormat`):** AniList, MAL/Jikan, TMDB, Shikimori, Bangumi, Simkl ve Kitsu kaynaklarında yayın tarihi ve geri sayım tek standart formata getirildi.
- **Detay Sayfaları:** *"Yaklaşan Yayın: Bölüm 2, 2026-10-15 tarihinde yayında (6 gün sonra)"* şeklinde hem tarih hem geri sayım gösterilir.

### 📋 10. Kullanıcı Profili Anime/Manga Listesi ve "Listem" Tam Paritesi (`KitsugiUserMediaListScreen.kt`, `MyListFilterHelpers.kt`)
- **Tasarım ve Üst Kontrol Paritesi:** Başka bir kullanıcının listesi ana Listem ekranıyla aynı başlık, görünüm değiştirici, arama ve filtre kontrollerine kavuştu.
- **Birleşik Anime & Manga Veri Kümesi:** Anime ve Manga listeleri tek havuzda birleştirilerek filtreleme seçenekleri zenginleştirildi.
- **Gelişmiş Filtreler:** Durum, medya türü, puan, yıl ve özel filtreler eklendi; poster önizleme ve rastgele seçim desteklendi.

### 🔐 11. Kalıcı Kasa Yedekleme, Çoklu Cihaz Çakışma Yönetimi & Android Keystore (`VaultBackupWorker.kt`, `VaultMerge.kt`, `LocalVaultKeyStore.kt`)
- **WorkManager Kalıcı Kuyruk:** Ağ kesintilerinde ve süreç kapanmalarında kasa yedekleri kaybolmaz.
- **3 Yönlü Akıllı Birleştirme:** Farklı servis token'ları çakışmasız birleşir; aynı servis token çakışmalarında kullanıcıya seçim sunulur.
- **Supabase CAS Koruması:** `kitsugi_vault_cas` veritabanı fonksiyonu ile yarış durumları engellenir.
- **Kesintiye Dayanıklı Şifre Değiştirme & Keystore Şifreleme:** Kasa anahtarı işlem yarıda kalsa dahi korunur ve yerel anahtar Android Keystore ile şifrelenir.
- **22 Taşınabilir Ayar:** Tema, liste düzeni ve dil ayarları şifreli yedek kapsamına alındı.

### 🧩 12. Eklenti Keşfet Portalı Ana Sayfa Paritesi & Stüdyo Detay İyileştirmeleri (`AddonExploreDialog.kt`, `AddonFullScreenGridPage.kt`, `AddonHomeParity.kt`, `StudioDetailPage.kt`, `StudioDetailComponents.kt`, `StudioDetailViewModel.kt`)
- **Eklenti Vitrin ve Raf Paritesi:** Eklenti portalı içerisindeki eklenti keşfet sayfaları ana sayfa keşfet sayfasıyla birebir aynı görsel dile ve etkileşim standartlarına kavuştu (`KitsugiHeroSection` öne çıkan vitrini, otomatik kayan carousel, çizgi-nokta göstergeleri, neon çerçeveli `KitsugiExploreMediaCard`, snap-fling kaydırma ve "Tümünü Gör" sözleşmesi).
- **"Tümünü Gör" Grid ve Liste Paritesi:** Eklenti keşfet ekranındaki "Tümünü Gör" açılır sayfası (`AddonFullScreenGridPage`), ana stüdyo ve keşfet grid sayfalarıyla aynı tasarım diline geçirildi: emojili tür filtre çipleri (✨ Tümü / 🎌 Anime / 🎥 Film / 📺 Dizi), Filtre+Sıralama alt sayfası (`KitsugiStudioFilterBottomSheet`), grid ↔ liste görünüm geçişi (kalıcı tercih hafızasıyla), içerik sayacı, kaydırınca beliren üst şerit ve hızlı yukarı FAB.
- **CS3 Ortak Model Köprüsü & Temiz Eklenti Rozetleri:** CloudStream 3 arama sonuçları ortak `JikanSearchResult` modeline dönüştürülerek bilinmeyen kaynaklarda sahte `#id` gösterimi kaldırıldı; genel eklenti rozeti (`Icons.Default.Extension`) ve eklenti çipi ile vitrin çipinin boş kalması önlendi.
- **Stüdyo Kuruluş Tarihi Formatı:** Stüdyo detay sayfasındaki ham ISO formatlı kuruluş tarihi (`1998-10-01T00:00:00+00:00`) `KitsugiDateUtils` ile *"Kuruluş: 1 Ekim 1998"* biçiminde okunabilir Türkçe tarihe dönüştürüldü (hem vitrin başlığında hem sol bilgi panelinde).
- **Stüdyo "Hakkında" Kartı Tam Detay Paritesi:** Stüdyo sayfası "Hakkında" kartı medya detay sayfasındaki `DetailSynopsisCard` ile tam pariteye kavuşturuldu: otomatik Türkçe çeviri desteği (`autoTranslateEnabled` veya Rusça metin tespiti), `DetailCache` önbellekleme, uygulama içi çeviri ve 3. parti çevirmen butonu, tek dokunuşla panoya kopyalama ("Panoya kopyalandı") ve uzun metinlerde "Daha fazla / Daha az" genişletme kontrolü.

### 🎯 13. "Listeye Ekle" & "Zaten Listede" Çelişkisi ve Arama Başlık Eşleşmesi (`KitsugiModels.kt`, `AppViewModel.kt`, `ApiResultDetailPage.kt`, `MyListFilterHelpers.kt`, `MyListLibraryGrouping.kt`, `MyListScreen.kt`)
- **Buton ve Kontrol Tutarlılığı:** Detay sayfasındaki "Listeye Ekle" butonu ile ekleme akışındaki mükerrer kontrolü aynı eşleme sonucuna bağlandı (`findExistingEntry`). Kayıt kütüphanede zaten mevcutsa buton dinamik olarak **"✎ Düzenle"** durumuna geçer ve tıklandığında mevcut kaydı doğrudan açar.
- **Açıklayıcı ve Hedef Gösteren Uyarı Mesajı:** Ekleme akışındaki uyarı mesajı artık gelen sorgu adını değil, listede bulunan asıl kaydın adını ve bulunduğu sekmeyi bildirir (*"\"Clannad: After Story\" zaten listende var (AniList sekmesi)."*).
- **Çoklu Başlık Varyantı Eşleşmesi:** `MediaEntry.matches` kontrolü tüm başlık türevlerini (özgün ad, İngilizce, Japonca, Romaji) `MediaIdentity.normalizedTitle` standardıyla karşılaştırarak dil tercihlerinden kaynaklanan eşleşme kayıplarını giderir.
- **Listem Gelişmiş Arama:** Listem ekranında arama yapılırken alternatif İngilizce/Japonca başlıklar ve noktalama/boşluk duyarsız normalleştirilmiş sorgular taranır ("clannad after story", "CLANNAD 〜AFTER STORY〜", "クラナド" gibi tüm yazım varyantları doğru kaydı bulur).
- **Çözülmüş Kimliklerle Güvenlik Ağı:** Erken tıklama durumlarında çift kayıt oluşmaması için kimlik çözümleme sonrasında ikinci bir kontrol eklenerek veri tabanı bütünlüğü korundu (6 yeni birim testiyle doğrulandı).

### 🏢 14. Stüdyo & Yapımcı Kaynak Kimliği Ayrıştırması & Şirket Arama Yönlendirmesi (`KitsugiStudioClient.kt`, `StudioSourceSupport.kt`, `JikanSearchClient.kt`, `SearchScreen.kt`, `SearchResultRow.kt`, `StudioDetailPage.kt`, `StudioDetailViewModel.kt`)
- **Sağlayıcıya Özgü Stüdyo Kimlik Ayrıştırması:** Stüdyo ve yapımcı kimliklerinin farklı platformlarda çakışması (örneğin MAL üzerindeki Aniplex'in AniList ID çakışması nedeniyle Bones olarak açılması) engellendi; her stüdyo ve yapımcı öncelikle kendi kaynak kimliğiyle (`source`) sorgulanır.
- **İsim Doğrulaması & Akıllı Yeniden Arama:** Jikan/MAL üzerinde dönen stüdyo adı tıklanan isimle uyuşmazsa şirket adıyla otomatik doğrulama araması yapılır; eşleşme teyit edilemeyen yanıltıcı stüdyo kayıtları önlenir.
- **Çoklu Kaynak Desteği:** TMDB, Shikimori ve Bangumi stüdyo yolları normalize edildi; Bangumi için yalnızca şirket türündeki (`type = 2`) kayıtlar stüdyo olarak eşleştirilir.
- **Doğrudan Stüdyo Detayına Yönlendirme:** Arama ekranında "Stüdyo" filtresiyle aranan kayıtlar anime/medya detayına değil, doğrudan ilgili sağlayıcının `StudioDetailPage` sayfasına yönlendirilir; stüdyo arama satırında yanıltıcı "listeye ekle" butonu gizlenir.
- **Kesintisiz Yükleme Deneyimi & Görsel Aktarımı:** Stüdyo logosu ve adı navigasyon rotası üzerinden yükleme ekranına taşınarak beyaz/boş ekran titremesi engellenir.

### 📱 15. Profil Favorilerinde Alt Bar (BottomBar) ile Uyumlu Kategori Butonu Hizalaması (`AniListProfileContent.kt`, `KitsugiProfileScreen.kt`, `AppRootTabPages.kt`)
- **Dinamik Alt Gezinme Çubuğu Hizalaması:** Profil ekranı "Favoriler" sekmesinde yer alan yüzen kategori ("Anime" / `☰`) filtre butonu ve hızlı yukarı çıkma FAB'ı, alt gezinme çubuğunun (BottomBar) açık/kapalı durumuna göre dinamik olarak konumlandırılır (`isBottomBarVisible` etkinken `96.dp + insets`, kapalıyken veya yatay modda `16.dp / 20.dp + insets`).
- **Görsel Çakışma ve Dokunma Engeli Giderildi:** Kategori butonunun "Keşfet" sekmesi üzerine binmesi, menü öğelerinin birbirini perdelemesi ve listenin son satırının alt barın altında kalması tamamen çözüldü (`Spacer(140.dp)`).

### 📐 16. Dikey Mod Kenar Taşması (Peek) Düzeltmesi (`DetailPageScaffold.kt`)
- **Kenar Boşluğu Temizliği:** Dikey mod detay sayfalarında (`DetailPageScaffold`), `HorizontalPager` üzerindeki `contentPadding = PaddingValues(horizontal = 16.dp)` kaldırıldı ve dolgu doğrudan sayfa `Box` bileşeni içine taşındı (`padding(horizontal = 16.dp)`).
- **Komşu Sekmelerin Ekrana Taşması Engellendi:** Ekran kenarlarından komşu sekmelerin köşelerinin görünmesi ("peek" sorunu) tamamen giderildi; sekmeler ekran genişliğini tam kaplayarak kenarlardan taşma yapmaz.

### 🌐 17. Cümle Sınırlarından Bölmeli ve Kademeli Uzun Biyografi & Özet Çevirisi (`TranslationManager.kt`, `CharacterDetailViewModel.kt`, `StaffDetailViewModel.kt`, `ApiResultDetailViewModel.kt`, `MediaEntryDetailViewModel.kt`)
- **4 Kademeli Akıllı Metin Bölme:** 2000+ karakterlik uzun metinler (özellikle HTML'den gelen ve satır sonu içermeyen dev tek paragraf karakter/personel biyografileri) sırasıyla paragraf (`\n\n`) → satır (`\n`) → cümle (`. ! ? … 。！？；`) → kelime sınırlarından bölünür. Noktalama sonrası boşluk şartı aranarak `3.14` ve `Mr.` gibi kısaltmalar korunur.
- **Kademeli Çeviri Akışı (Streaming UI):** Uzun metinlerde her parça çevrildikçe ekrana "çevrilen kısım + henüz çevrilmemiş orijinal kuyruk" anında yansıtılır; metin boyutu sıçramaz, dil kullanıcının gözü önünde akıcı şekilde Türkçeye dönüşür.
- **Uygulama Seviyesinde Retry & Sessiz Hata Koruması:** Her parça için 3 denemeli üstel geri çekilme (`delay`) eklendi; HTTP durum kodları loglanır ve `fetchSingleChunk` hata durumunda `null` dönerek başarısızlıkları açıkça bildirir.
- **Önbellek Hijyeni:** Room ve `DetailCache`'e yalnızca %100 eksiksiz tamamlanan çeviriler yazılır; yarım çeviriler önbelleğe alınmaz.

### 🛡️ 18. Medya Detay Sosyal Eşleşme Güvenliği & Keşfet Vitrin Backdrop Önceliği (`KitsugiMediaSocialClient.kt`, `DetailCache.kt`, `KitsugiHeroSection.kt`, `TmdbDiscoverClient.kt`, `ExploreViewModel.kt`)
- **Kaynak ve Tür Bazlı Sosyal Eşleşme:** Detay sayfalarındaki inceleme, forum ve aktivite akışlarında yabancı platform kimlikleri (TMDB, Simkl vb.) doğrudan MAL veya AniList ID'si olarak kabul edilmez. Yalnızca açık ve doğrulanmış çapraz kimlikler kullanılır.
- **Aktivite ve İnceleme Filtresi:** Anime/Manga olmayan içeriklerde Jikan/AniList sosyal sorguları yapılmaz. Aktiviteler hedef AniList ID'si ve türüyle birebir eşleşmek zorundadır; medyasız serbest metin aktiviteleri filtrelenir.
- **Review Cache Tür İzolasyonu:** `DetailCache.getMediaReviews` artık `source + ID + mediaType` üçlüsüyle izole edilir; dizi/anime incelemeleri birbirine taşmaz.
- **Keşfet Vitrininde Yatay Backdrop Önceliği:** Anime, TV ve filmlerde dikey ekranlarda da mevcut yatay backdrop görselleri posterden önce denenir. TMDB movie/TV kayıtları exact ID ile, diğerleri tür ve yıl uyumlu başlık aramasıyla çözülür (Manga oran mantığını korur).

### 🖼️ 19. Fanart API Galeri Detayları, Kalıcı Bildirim Arşivi ve Bangumi Bildirim Kaynağı (`FanartApiClient.kt`, `GalleryItem.kt`, `KitsugiImageGalleryDialog.kt`, `NotificationArchiveStore.kt`, `KitsugiNotificationsViewModel.kt`, `NotificationDiagnostics.kt`)
- **Fanart API Detayları Galeride:** Fanart.tv v3 API'sinden gelen `name` ve `iMDb` alanları galeride "Ad" ve "IMDb ID" satırları olarak sunulur. Yatay modda sağ panelde yer alırken, dikey modda üst bara eklenen ℹ️ "Detaylar" butonuyla açılan alt levhada gösterilir.
- **Kalıcı Bildirim Arşivi (`NotificationArchiveStore`):** MAL, Simkl, Kitsu ve Bangumi gibi API'sinde kişisel bildirim ucu bulunmayan kaynaklar için üretilen yayın takvimi ve izleme listesi bildirimleri yerel `notifications_archive.json` dosyasında (kaynak başına 300 kayıt) ve Kitsugi hesabı bulut yedeğinde (`user_data.notifications`) kalıcı olarak saklanır; 7 günlük takvim penceresinden düşen bildirimler kaybolmaz.
- **Bangumi Bildirim Kaynağı:** Bildirim ekranı ve seçici paneline 6. kaynak olarak Bangumi eklendi. İzleme listesi (在看/DOING) + önbellekli MAL çapraz çözümü + yayın takvimi eşleşmesiyle bildirimler üretilir; hesap bağlı olmadığında yerel kayıtlar gösterilir. Bildirim Teşhis paneli 6 kaynağı canlı olarak test eder.

### 🔞 20. Shikimori Liste Kartları +18 (Adult) Blur Düzeltmesi & GraphQL Çözümlemesi (`ShikimoriAdultResolver.kt`, `ShikimoriImportManager.kt`, `ShikimoriAdultFlagMigration.kt`)
- **Kök Neden Tespiti:** Shikimori'nin `user_rates` REST uç noktası gömülü anime/manga verilerini `AnimeSerializer` ve `MangaSerializer` ile döndürür; bu serializer'lar `rating` ve `genres` alanlarını hiç taşımaz. Eski `fetchAdultMediaIds` fonksiyonu da aynı kısıttan ötürü her zaman boş dönüyor ve Shikimori kayıtları `isAdult = false` olarak içe aktarılıyordu (blur ayarı açık olsa bile liste kartlarında uygulanmıyordu).
- **Yeni `ShikimoriAdultResolver` (GraphQL):** 50'lik gruplar halinde `{ animes(ids: "...", limit: 50, censored: false) { id rating genres { name } } }` sorgusu koşturulur (`censored: false` ile hentai filtresi aşılır, manga için şemada `rating` bulunmadığından tür listesi kullanılır). Sonuçlar önbelleğe alınır ve hız sınırlarına uyulur.
- **Mevcut Veritabanı Kayıtlarının Onarımı (`ShikimoriAdultFlagMigration`):** Listem ekranı açıldığında yerel veritabanında `isAdult = false` kalmış Shikimori kayıtları GraphQL üzerinden taranır ve +18 olanlar (rx/hentai) arka planda otomatik olarak `isAdult = true` yapılır.

### 📦 21. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.226-foss.apk`).

---

## 🇬🇧 English (v2.4.226)

### 📝 1. Media Entry Editor & Provider Parity (`KitsugiEditMediaSheet.kt`, `BangumiSyncManager.kt`, `KitsuSyncManager.kt`, `ShikimoriSyncManager.kt`)
- **Bangumi Platform Alignment:** Bangumi entries open with genuine Bangumi platform templates rather than generic "local library" fields (title, +18, total counts); introduces "Private" and "Tags" fields with bidirectional sync to Bangumi API.
- **Kitsu Privacy & Date Sync:** Added "Private" toggle to Kitsu editor. Start/finish dates, personal notes, and privacy settings are imported and synced to Kitsu; unsupported manga volume counter is hidden.
- **Shikimori Rewatching & Counters:** Added "Rewatching" status and rewatch count field; personal notes, rewatch counts, and manga volume progress are fully synced bidirectionally.
- **Platform Capability Indicators:** Explicit informational labels indicate fields unsupported by remote APIs (e.g. notes/dates on Simkl, favorites on MAL). Only populated values are synced to prevent remote data erasure.

### 🚀 2. Extension Search & Stream Runner Overhaul (`CsStreamRunner.kt`, `CsRuntimeInit.kt`)
- **Live Domain Protection & Table Safety Net:** Hardcoded and remote domain tables no longer overwrite healthy plugin domains or runtime-discovered mirrors (e.g., FilmMakinesi, Dizipal, SezonlukDizi). Stale tables are now only used to rescue empty, poisoned, or dead URLs.
- **Title-Search Fallback Preservation:** Empty native `getLoadUrl()` resolution results no longer swallow the search pipeline; they cleanly fall back to title-based search.
- **Scraper Disk Cache Removed:** Removed the 50 MB OkHttp disk cache from the scraper client. Websites serving `max-age` headers on search queries previously caused Cloudflare challenges or 0-result pages to be cached indefinitely on disk.
- **Concurrency & Budget Increase:** Increased global `searchSemaphore` from 12 to 24 permits, preventing queued plugins from exhausting their 20-second timeout budgets during 30+ plugin parallel searches.

### 🎬 3. Kitsu Movie Subtype Disambiguation & TMDB Conflict Prevention (`KitsugiEpisodeRatingsRepository.kt`, `ApiResultDetailViewModel.kt`, `MediaEntryDetailViewModel.kt`)
- **Movie vs. TV ID Collision Prevention:** Media sharing identical TMDB IDs between movies and TV shows (e.g. *Howl's Moving Castle* film vs *Roar* series) are strictly isolated. Movie entries query only movie endpoints and avoid TV/SeriesGraph fallbacks.
- **Kitsu Subtype Caching:** Queries and caches the Kitsu API `subtype` attribute per entry, allowing hero, detail logos, and galleries to use movie endpoints.
- **Clean Media Galleries:** Prevents TV series artwork from polluting anime movie Fanart galleries.

### 🔔 4. Profile & My List Notification Buttons & Explore Dice Button (`AppRootTabPages.kt`, `ExploreScreen.kt`, `MyListComponents.kt`, `MyListScreen.kt`, `KitsugiProfileScreen.kt`)
- **Notification Navigation in Profile & My List:** When AniList, MAL, or Simkl is connected, a notification icon button appears in Profile (left of stats) and My List (left of the dice button).
- **Independent Explore Dice:** The random picker dice button (🎲) in Explore remains visible even when notifications are active.

### 🌐 5. Bangumi Alternate Titles & CJK Variants Restored (`BangumiLocalizedName.kt`, `KitsugiSimklDetailClient.kt`)
- **CJK Alternate Titles Restored:** Lifted the Latin-only restriction on alternative titles in Bangumi and Simkl, re-enabling Japanese, Chinese, and Korean title variants in the "Other Names" section while discarding exact duplicates of the main title.

### 🏷️ 6. Bangumi Tag & Genre Dictionary (`BangumiTagDictionary.kt`, `KitsugiTranslations.kt`, `strings.xml`)
- **100% Official Meta Tag Coverage:** All 158 official Bangumi meta tags (sources, types, genres, themes, platforms, etc.) are localized in Turkish and English.
- **817 User Tags Dictionary:** 817 popular user tags localized with spelling variant deduplication (e.g. `催泪` / `催涙` / `Tearjerker`).
- **Readability Tag Sorting:** Unmapped CJK tags sort to the end of the tag cloud, prioritizing readable tags.

### 🎨 7. Hero Logos & Multi-Source Galleries (`HeroLogoPolicy.kt`, `MediaGalleryIdentity.kt`, `KitsugiPersonImageAggregator.kt`)
- **Hero Graphical Logos:** Displays graphic logos in the Hero spotlight for Bangumi and Simkl items when available.
- **Cross-Source ID Segregation:** Bangumi subject IDs are never used as MAL IDs; Simkl entries retain valid TMDB IDs.
- **Character, Staff & Voice Actor Galleries:** Resolves people missing MAL IDs using strict name matching against Jikan.

### 🌟 8. Explore: Airing Soon Shelf & Ordered Categories (`ExploreScreen.kt`, `AllSourcesExplore.kt`, `ExploreCategories.kt`)
- **Repositioned Airing Soon Banner:** Relocated immediately below category shortcuts across all views.
- **Logical Shelf Ordering:** Standardized order: Anime shelves → Manga shelves → Source-specific shelves.
- **Category Shortcuts:** Added "Top Rated Anime" and "Top Rated Manga" shortcuts.

### 🗓️ 9. Universal Airing Dates & Countdowns (`NextAiringFormat.kt`, `DetailSharedComponents.kt`, `NextAiringChip.kt`)
- **Unified Formatting Layer:** Standardized air dates and countdowns across AniList, MAL, TMDB, Shikimori, Bangumi, Simkl, and Kitsu.
- **Detail Screens:** Unified label: *"Yaklaşan Yayın: Bölüm 2, 2026-10-15 tarihinde yayında (6 gün sonra)"*.

### 📋 10. User Profile List & "My List" Full Parity (`KitsugiUserMediaListScreen.kt`, `MyListFilterHelpers.kt`)
- **Full Header & Filter Parity:** Viewing another user's list now provides the complete header, view modes, search, and filtering capabilities of the main "My List" screen.
- **Combined Anime & Manga Pool:** Both media lists are loaded concurrently and filterable on the fly.

### 🔐 11. Hardened Account Backup, Multi-Device CAS & Keystore (`VaultBackupWorker.kt`, `VaultMerge.kt`, `LocalVaultKeyStore.kt`)
- **WorkManager Persistent Queue:** Protects pending backups against network drops, process death, and reboots.
- **3-Way Merge & CAS:** Automatic merging for distinct accounts and server-side compare-and-swap on Supabase.
- **Keystore Encryption & Interruption-Proof Password Change:** Hardware-backed encryption and dual-wrap transitions.
- **22 Portable Settings:** Encrypted alongside tokens.

### 🧩 12. Extension Explore Portal Home Parity & Studio Detail Polish (`AddonExploreDialog.kt`, `AddonFullScreenGridPage.kt`, `AddonHomeParity.kt`, `StudioDetailPage.kt`, `StudioDetailComponents.kt`, `StudioDetailViewModel.kt`)
- **Extension Explore Home Screen Parity:** Extension explore pages within the Addon Portal now match the primary Home Explore screen in aesthetics and interaction (`KitsugiHeroSection` featured hero carousel, auto-advancing slides, line-dot indicators, neon-framed `KitsugiExploreMediaCard`, snap-fling physics, and standard "See All" contracts).
- **"See All" Fullscreen Grid & List Parity:** The "See All" destination (`AddonFullScreenGridPage`) adopts the studio/explore grid architecture: emoji-annotated type filter chips (✨ All / 🎌 Anime / 🎥 Movie / 📺 Series), Filter & Sort bottom sheet (`KitsugiStudioFilterBottomSheet`), grid ↔ list view toggle (with persistent layout preference), item count indicator, sticky header on scroll, and scroll-to-top FAB.
- **CS3 Unified Model Bridge & Clean Addon Badges:** CloudStream 3 search results map to standard `JikanSearchResult` models; redundant fake `#id` labels are removed from addon cards, and fallback extension badges/pills (`Icons.Default.Extension`) prevent blank source pills in hero headers.
- **Studio Foundation Date Formatting:** Replaced raw ISO timestamps (`1998-10-01T00:00:00+00:00`) with localized date formatting (*"Founded: 1 October 1998"* via `KitsugiDateUtils`) across both hero and side panels.
- **Studio "About" Synopsis Card Full Parity:** The studio "About" section now matches the media detail `DetailSynopsisCard`: automatic Turkish translation (`autoTranslateEnabled` or Cyrillic auto-detection) backed by `DetailCache`, in-app translation toggle with 3rd-party translator fallback, one-tap copy to clipboard toast, and expandable "Read more / Read less" controls.

### 🎯 13. "Add to List" & "Already in List" Discrepancy & Search Title Matching (`KitsugiModels.kt`, `AppViewModel.kt`, `ApiResultDetailPage.kt`, `MyListFilterHelpers.kt`, `MyListLibraryGrouping.kt`, `MyListScreen.kt`)
- **Button and Action Parity:** The detail page "Add to List" button now checks the exact same identity/duplicate match (`findExistingEntry`) as the add action. If the media entry already exists in the library, the button seamlessly turns into **"✎ Edit"** and opens the existing entry directly upon click.
- **Descriptive Duplicate Notification:** The duplicate snackbar notification now references the existing library entry's title and its tab (*"\"Clannad: After Story\" already in your list (AniList tab)."*), eliminating confusion when names differ across providers.
- **Multi-Variant Title Matching:** `MediaEntry.matches` checks all title variants (canonical, English, Japanese, Romaji) using `MediaIdentity.normalizedTitle` to prevent mismatches caused by display language choices.
- **Robust "My List" Search:** Searching in "My List" scans English and Japanese alternative titles as well as punctuation- and spacing-insensitive normalized strings (e.g. queries for "clannad after story", "CLANNAD 〜AFTER STORY〜", and "クラナド" all find the target entry).
- **Resolved ID Double-Check Safety Net:** Early clicks before cross-platform ID resolution completes are captured by a second check using resolved IDs, preventing duplicate entries (verified by 6 unit tests).

### 🏢 14. Studio & Producer Provider Identity Isolation & Search Routing (`KitsugiStudioClient.kt`, `StudioSourceSupport.kt`, `JikanSearchClient.kt`, `SearchScreen.kt`, `SearchResultRow.kt`, `StudioDetailPage.kt`, `StudioDetailViewModel.kt`)
- **Provider-Scoped Studio ID Resolution:** Cross-provider studio ID collisions (such as Aniplex opening Bones due to mismatched MAL and AniList numeric ID namespaces) are completely resolved; each studio is queried using its origin provider namespace.
- **Name Verification & Fallback Search:** When Jikan returns a company name differing from the clicked label, an automatic name search verification is triggered, preventing incorrect company data from being shown.
- **Multi-Source Support:** Normalized studio endpoints across TMDB, Shikimori, and Bangumi (filtering strictly for company records with `type = 2` on Bangumi).
- **Direct Studio Search Routing:** Searching under the "Studio" scope routes directly to `StudioDetailPage` rather than media details, with "Add to List" buttons disabled for company entries.
- **Seamless Loading Transition:** Studio logo and name are carried across navigation into the loading screen to prevent blank screen flicker.

### 📱 15. Profile Favorites Bottom Navigation Bar Adaptive Alignment (`AniListProfileContent.kt`, `KitsugiProfileScreen.kt`, `AppRootTabPages.kt`)
- **Bottom Bar Adaptive Floating Controls:** The floating category filter button ("Anime" / `☰`) and scroll-to-top FAB on the Profile "Favorites" tab now adapt dynamically to bottom navigation bar visibility (`isBottomBarVisible` sets `96.dp + insets` when visible, animating to `16.dp / 20.dp + insets` when hidden or in landscape).
- **Eliminated Overlap & Click Blocking:** Resolves the visual collision where the floating button sat directly over the "Keşfet" tab, ensuring clean separation and sufficient list bottom padding (`Spacer(140.dp)`).

### 📐 16. Portrait Mode Horizontal Tab Peek Elimination (`DetailPageScaffold.kt`)
- **Clean Margins Without Leakage:** Removed `contentPadding = PaddingValues(horizontal = 16.dp)` on portrait `HorizontalPager` and applied 16.dp padding directly inside the page `Box`. Adjacent tabs no longer peek from the left/right screen edges.

### 🌐 17. Long Biography & Synopsis Progressive Translation (`TranslationManager.kt`, `CharacterDetailViewModel.kt`, `StaffDetailViewModel.kt`, `ApiResultDetailViewModel.kt`, `MediaEntryDetailViewModel.kt`)
- **4-Stage Text Chunking:** Texts exceeding 2000 characters are progressively split along paragraph (`\n\n`) → newline (`\n`) → sentence (`. ! ? … 。！？；`) → word boundaries, with punctuation-whitespace validation protecting numbers (e.g. `3.14`) and titles (e.g. `Mr.`).
- **Progressive Streaming UI:** Translations stream chunk-by-chunk to the UI ("translated head + original tail"), avoiding layout jumps and translating smoothly before the user's eyes.
- **Application-Level Retries & Error Visibility:** 3 attempts with exponential backoff on HTTP/translation failures; `null` return on failure avoids silent degradation, and only 100% complete translations are cached in Room and `DetailCache`.

### 🛡️ 18. Media Detail Social Matching Safety & Explore Backdrop Prioritization (`KitsugiMediaSocialClient.kt`, `DetailCache.kt`, `KitsugiHeroSection.kt`, `TmdbDiscoverClient.kt`, `ExploreViewModel.kt`)
- **Source & Type Isolated Social Mapping:** Forum topics, activities, and reviews no longer conflate third-party IDs (TMDB, Simkl) with MAL or AniList IDs; only verified cross-platform mappings are accepted.
- **Strict Activity Verification:** Social queries are disabled for non-anime/manga media. Activities strictly match the destination AniList ID and media type; unlinked text posts are discarded.
- **Review Cache Type Isolation:** `DetailCache.getMediaReviews` is strictly scoped by `source + ID + mediaType`, preventing cross-medium cache leakage.
- **Explore Hero Backdrop Prioritization:** For anime, TV, and movies, horizontal backdrop artwork is prioritized over posters even on portrait screens. TMDB titles query exact TMDB IDs, while other sources use type- and year-aware matching (Manga preserves aspect-ratio priority).

### 🖼️ 19. Gallery Fanart API Details, Persistent Notification Archive & Bangumi Notification Source (`FanartApiClient.kt`, `GalleryItem.kt`, `KitsugiImageGalleryDialog.kt`, `NotificationArchiveStore.kt`, `KitsugiNotificationsViewModel.kt`, `NotificationDiagnostics.kt`)
- **Fanart API Metadata in Gallery:** Displays `name` and `iMDb` fields from the Fanart.tv v3 API as "Name" and "IMDb ID" metadata. Integrated into the landscape right inspector panel and accessible via a new ℹ️ "Details" top-bar button on portrait screens.
- **Persistent Notification Archive (`NotificationArchiveStore`):** Airing and watchlist notifications generated for platforms without dedicated personal notification endpoints (MAL, Simkl, Kitsu, Bangumi) are stored persistently in `notifications_archive.json` (up to 300 items per provider) and backed up to Supabase (`user_data.notifications`). Notifications no longer vanish once they slide out of the 7-day airing window.
- **Bangumi Notification Provider:** Added Bangumi as the 6th notification source with custom icon, login status, and username pill. Queries the user's DOING (在看) collection, resolves cross-platform IDs against the airing schedule, and displays local entries with helpful notes when unauthenticated. Diagnostics panel upgraded to verify all 6 providers live.

### 🔞 20. Shikimori List Card +18 (Adult) Blur Fix & GraphQL Resolution (`ShikimoriAdultResolver.kt`, `ShikimoriImportManager.kt`, `ShikimoriAdultFlagMigration.kt`)
- **Root Cause Resolution:** Shikimori's `user_rates` endpoint serializes embedded anime and manga models without `rating` or `genres` fields, causing all imported entries to save with `isAdult = false` and bypassing the adult blur setting on library cards.
- **New `ShikimoriAdultResolver` (GraphQL):** Batches up to 50 items per query using `{ animes(ids: "...", limit: 50, censored: false) { id rating genres { name } } }` (`censored: false` lifts the hentai filter; manga resolves via genre tags). In-memory caching, rate-limit awareness, and automatic retries ensure fast, resilient resolution.
- **Database Backfill Migration (`ShikimoriAdultFlagMigration`):** Automatically migrates and flags existing library entries in the background upon opening "My List", immediately applying cover blur to adult media without requiring a manual re-sync.

### 📦 21. Packaging
- Built exclusively as **FOSS** release (`assembleFossRelease`) (`Kitsugi-Beta-v2.4.226-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.225)

### 🌸 1. Bangumi Başlık Dili & Latin Ad Zenginleştirmesi (`KitsugiBangumiClient.kt`, `BangumiLocalizedName.kt`)
- **Liste Kartlarında Latin Ad Desteği:** Bangumi liste ve arama uçları (`/v0/search`, `/v0/subjects`, sıralamalar vb.) infobox dönmediğinden Korece ve Çince dizi/anime başlıkları (ör. "김비서가 왜 그럴까", "눈물의 여왕") yerel dilde görünüyordu. Başlığı CJK olan kayıtlar için `withLatinBangumiNames` mekanizması devreye girerek arka planda (Semaphore 4 sınırlayıcı ile) subject detayını tek seferlik çeker, İngilizce/Romaji adları `BangumiTitleCache`'e yazar ve kart başlıklarını uygulama dil tercihine göre günceller.
- **Tüm Bangumi Keşfet & Arama Listeleri:** En İyi Animeler/Mangalar, Popüler Listeler, Sezonluk Keşif, Real/Dizi Şeritleri ve Normal Arama dahil 15 farklı liste çağrısına uygulandı.
- **Alternatif Adlar (Diğer İsimler) Filtresi:** Detay sayfalarındaki "Diğer Adlar" alanında Japonca, Çince ve Korece karakterli varyantlar gizlendi; kullanıcıya yalnızca temiz Latin harfli adlar sunulur.

### 👤 2. Karakter & Kişi Detay Sayfası İyileştirmeleri (`CharacterDetailPage.kt`, `StaffDetailPage.kt`)
- **Seçilen Dile Bağlı Japonca Alt Satır:** Karakter ve ekip detay sayfalarında adın altındaki Japonca yerel ad satırı, artık yalnızca kullanıcı uygulama dilini "Japonca/Yerel" seçtiğinde gösterilir. Diğer dillerde (İngilizce/Romaji) gereksiz tekrar engellendi.

### 📱 3. Profil & Favoriler UI Düzeltmeleri (`AniListProfileContent.kt`, `ProfileFavoritesListemStyle.kt`)
- **AniList Profil Kategori Butonu Hizalaması:** AniList profil favorilerindeki kategori filtre butonu (`bottomOffset = 96.dp`), kullanıcı profiliyle uyumlu olacak şekilde `20.dp`'ye indirilerek ekranın alt kenarıyla hizalandı.
- **Hızlı Yukarı Kaydırma Butonu:** Favoriler listesinde yukarı dön butonu, kullanıcının 3 öğe beklemesine gerek kalmadan ilk öğeden çıkıldığı anda (~240px kaydırmada) anında görünür hale getirildi.

### 📦 4. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.225-foss.apk`).

---

## 🇬🇧 English (v2.4.225)

### 🌸 1. Bangumi Title Localization & Latin Name Enrichment (`KitsugiBangumiClient.kt`, `BangumiLocalizedName.kt`)
- **Latin Name Resolution for List Cards:** Bangumi list and search endpoints do not return infoboxes, previously causing Korean/CJK titles (e.g. "김비서가 왜 그럴까", "눈물의 여왕") to render in native script. The new `withLatinBangumiNames` pipeline queries subject details for CJK titles (rate-limited via Semaphore 4), caches English/Romaji names in `BangumiTitleCache`, and updates list cards to match user language preferences.
- **Universal Coverage:** Covers Top Anime/Manga, Trending, Seasonal Browse, Real Drama/Movies, and Search lists (15 endpoints).
- **Clean Alternative Names:** Filtered non-Latin scripts from "Other Names" on detail pages; CJK variants are excluded from alternative aliases.

### 👤 2. Character & Staff Detail Localization (`CharacterDetailPage.kt`, `StaffDetailPage.kt`)
- **Language-Aware Native Subtitle:** The Japanese native name subtitle below character and staff headings is now only shown when the user has selected "Native / Japanese" as their display language.

### 📱 3. Profile & Favorites Layout Polish (`AniListProfileContent.kt`, `ProfileFavoritesListemStyle.kt`)
- **AniList Profile Category Button Position:** Adjusted `bottomOffset` from `96.dp` to `20.dp` on the AniList profile tab, matching the user profile layout.
- **Responsive Scroll-to-Top:** The floating scroll-to-top button now activates immediately after passing the first item (~240px) rather than waiting for item 4.

### 📦 4. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.225-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.224)

### 🔐 1. Kitsugi Hesap Senkronizasyonu & Güvenli Kasa (`LinkedAccountVault.kt`, `KitsugiAccountRepository.kt`, `KitsugiAccountContent.kt`)
- **Uçtan Uca Şifreli Bağlı Hesap Yedekleme:** AniList, MyAnimeList, Kitsu, Shikimori, Bangumi ve Simkl bağlantı token'ları (erişim ve yenileme anahtarları ile kullanıcı ID'leri) cihazdan çıkmadan önce istemci tarafında AES-256-GCM ile şifrelenir.
- **Sıfır Bilgi Güvenlik Modeli:** Supabase ve sunucu tarafı yalnızca şifreli veriyi (`CipherBlob`) görür; düz metin token'lar sunucuya asla iletilmez.
- **Şifreden Türetilen Kasa Anahtarı:** Rastgele 256-bit kasa anahtarı üretilir ve kullanıcının Kitsugi hesap şifresinden PBKDF2-SHA256 (210.000 iterasyon) ile türetilen anahtarla sarılarak saklanır (`vault_meta`). Kitsugi şifresi hiçbir yerde saklanmaz.
- **Tek Girişle Otomatik Geri Yükleme:** Yeni bir cihazda veya yeniden kurulumda Kitsugi hesabına giriş yapıldığında, bağlı tüm servis hesapları otomatik olarak çözülüp geri yüklenir; servislere tek tek tekrar giriş yapma ihtiyacı ortadan kalkar.
- **Otomatik Canlı Yedekleme:** Bağlı bir serviste token yenilendiğinde (`refresh_token` rotasyonu dahil), `LinkedAccountVault` değişikliği algılar ve 3 saniyelik güvenli gecikmeyle bulut yedeğini otomatik günceller.
- **Geçici/Hassas Alan İzolasyonu:** OAuth geçici PKCE doğrulayıcıları (`*_code_verifier`, `*_pending_redirect_uri`) ve hesap şifreleri yedekleme kapsamı dışında tutulur.

### 🔍 2. Bulut Arama Geçmişi & Kompakt Arama Çipleri (`SearchHistoryRepository.kt`, `SearchHistorySection.kt`)
- **Cihazlar Arası Geçmiş Senkronizasyonu:** Arama yapıldığında, silindiğinde veya temizlendiğinde arama geçmişi otomatik olarak Kitsugi hesabına aktarılır (`user_data` tablosu `search_history` anahtarı).
- **Zaman Damgalı İki Yönlü Birleştirme:** Giriş yapıldığında yerel ve bulut geçmişi zaman damgalarına göre akıllıca harmanlanır (`pullAndMergeSearchHistory`).
- **Kompakt Arama Geçmişi Tasarımı:** Arama ekranındaki geçmiş çipleri modernize edildi; tek harfli/gürültülü sorgular filtrelenir, ilk 8 çip kompakt blokta gösterilir ve fazla sorgular "+N / Daha az" butonuyla açılıp kapanabilir.

### ⚙️ 3. Ayarlar Menüsü Entegrasyonu (`SettingsScreen.kt`)
- **"Kitsugi Hesabı" Menü Sayfası:** Ayarlar > Hesap altına "Kitsugi Hesabı" alt sayfası eklendi.
- **Hesap Yönetimi:** E-posta ve şifre ile doğrudan kayıt olma, giriş yapma, anlık eşitleme ("Şimdi eşitle") ve güvenli çıkış yapma kontrolleri sağlandı.

### 🖼️ 4. Orijinal Formatında Medya İndirme & Paylaşma (`KitsugiImageDownloadHelper.kt`)
- **Magic Number Format Tespiti:** İndirilen görsellerin gerçek MIME ve formatı bayt başlıklarından (magic number) tespit edilir.
- **Hareketli GIF & Şeffaf PNG Koruması:** Animasyonlu GIF'lerin düz JPEG'e dönüştürülmesi önlendi; GIF'ler `.gif`, PNG'ler `.png`, WebP'ler `.webp` olarak orijinal kalitesinde kaydedilir ve paylaşılır.

### 📦 5. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.224-foss.apk`).

---

## 🇬🇧 English (v2.4.224)

### 🔐 1. Kitsugi Account Synchronization & Secure Vault (`LinkedAccountVault.kt`, `KitsugiAccountRepository.kt`, `KitsugiAccountContent.kt`)
- **End-to-End Encrypted Linked Account Backup:** Access/refresh tokens and user IDs for connected platforms (AniList, MyAnimeList, Kitsu, Shikimori, Bangumi, Simkl) are encrypted on-device via AES-256-GCM before syncing to the cloud.
- **Zero-Knowledge Security Architecture:** Supabase stores only ciphertext blobs; raw authentication tokens are never exposed to the server.
- **Key Wrapping with PBKDF2:** A random 256-bit vault key encrypts token data and is wrapped using a key derived from the user's Kitsugi account password via PBKDF2-SHA256 (210,000 iterations). Passwords are never stored.
- **Seamless Single-Sign-On Restoration:** Logging into a Kitsugi account on a new device automatically unwraps the vault and restores all connected service integrations without manual re-authentication.
- **Real-Time Auto-Backup:** When service tokens refresh, changes trigger an automatic debounced backup (3-second window) to keep cloud vaults up-to-date.
- **Transient Field Isolation:** Ephemeral OAuth data (`*_code_verifier`, `*_pending_redirect_uri`) and user passwords are strictly excluded from backups.

### 🔍 2. Cloud Search History & Compact History Chips (`SearchHistoryRepository.kt`, `SearchHistorySection.kt`)
- **Cross-Device Search Synchronization:** Search additions, deletions, and purges automatically sync with cloud storage under `search_history`.
- **Timestamped Bidirectional Merge:** Account sign-in performs a smart conflict-free merge of local and remote search entries (`pullAndMergeSearchHistory`).
- **Compact Search UI Redesign:** Replaced full-size chips with streamlined, responsive chips. Single-character queries are omitted, queries collapse to 8 items with "+N / Show less" expansion.

### ⚙️ 3. Settings Interface Integration (`SettingsScreen.kt`)
- **"Kitsugi Account" Section:** Added a dedicated "Kitsugi Account" settings destination under Settings > Account.
- **Account Actions:** Sign-up, sign-in, manual forced synchronization ("Sync now"), and secure sign-out.

### 🖼️ 4. True Format Media Preservation (`KitsugiImageDownloadHelper.kt`)
- **Magic Number File Type Detection:** Inspects binary headers to accurately detect image MIME types and extensions.
- **Animated GIF & PNG Preservation:** Prevents animated GIFs and transparent PNGs from being flattened into JPEGs; saves and shares media in its native format (.gif, .png, .webp).

### 📦 5. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.224-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.223)

### 👤 1. Profil Favorileri & Listem Görünüm Uyarlaması (`ProfileFavoritesListemStyle.kt`, `AniListFavoritesTab.kt`, `KitsugiUserMediaListScreen.kt`)
- **Listem Kart Düzenleri:** Kendi profilindeki ve diğer kullanıcıların profillerindeki favori kartları (Anime, Manga, Karakter, Kişi, Stüdyo) artık Listem ekranının kart düzenlerini (`compact`, `comfortable`, `minimalist`, `large`, `grid_2col`) ve kullanıcının Listem ayarlarını kullanır.
- **Otomatik Sayfalama (Sonsuz Kaydırma):** Eski "Daha Fazla Yükle" butonu kaldırıldı; sayfa aşağı kaydırıldıkça sonraki sayfalar akıcı bir şekilde otomatik yüklenir.
- **Dinamik Alt Kontroller:** Sol altta kategori seçici çipi (Anime, Manga, Karakterler, Ekip, Stüdyolar) ve sağ altta yukarı kaydırma butonu eklendi; aşağı kaydırırken kontroller otomatik gizlenir.
- **Diğer Kullanıcıların Medya Listesi:** Diğer kullanıcıların anime/manga listeleri Listem'in yerel kart ve durum başlığı bileşenlerine geçirildi; kart düzeni Listem tercihlerine bağlandı.
- **Stüdyo Görsel Yedekleri:** Görseli bulunmayan yapımcı/stüdyo kartlarında boş kutular yerine stüdyo adının baş harflerini içeren avatar rozetleri gösterilir.

### 🌊 2. Cloudstream Akış Boru Hattı & Kısmi Sonuç Kurtarma (`CsStreamRunner.kt`, `StreamViewModel.kt`)
- **Kısmi Sonuç Kurtarma (Partial Sink):** Eklentiler video linklerini bulmuş olsa bile işlem süresi zaman aşımına (timeout) uğradığında bulunan tüm verilerin silinmesi sorunu kökten çözüldü. Artık zaman aşımı gerçekleşse bile o ana kadar elde edilen kaynaklar (`partialSink`) kurtarılarak kullanıcıya sunulur.
- **Paralel HEAD Canlılık Doğrulaması:** Medya linklerinin canlılık kontrolü eski sıralı (8 sn × N) yapıdan kurtarılarak 6 eşzamanlı worker ve 5 sn zaman aşımıyla paralel hale getirildi. 5 ölü CDN'in 40 saniye boyunca tüm aramayı kilitleyip bütçeyi patlatması engellendi.
- **Zaman Kutulu & Paralel Varyant Araması:** 18 arama varyantı tek tek taranmak yerine 3'lü gruplar halinde paralel taranır. Katı zaman kutusu (normalde 20 sn, Cloudflare korumalı sitelerde 40 sn) uygulanarak bütçenin kalan kısmı `load` ve `loadLinks` (video çıkarma) aşamalarına saklanır.
- **Üst Bütçe Uyarlaması:** Tek sağlayıcı zaman aşımı 40 sn'den 75 sn'ye, Cloudflare korumalı eklentiler için 90 sn'den 120 sn'ye çıkarıldı. Eklentiler paralel çalıştığı için kullanıcı fazladan bekletilmez.
- **Doğrulama Sonrası Gerçek Temizleme:** `CsPluginStatusTracker` kayıtları `api.name` ile tutulduğundan, Cloudflare WebView doğrulamasından sonra sadece `plugin.id` değil; eklenti id'si, eklenti adı ve yüklenen tüm alt API adları temizlenerek eklentinin tekrar denemede bloklu kalması önlendi.
- **Şeffaf Durum Bildirimi:** Arama başarısızlıklarında jenerik "akış bulunamadı" yerine gerçek nedenler kartlara yansıtılır (ör. *"Zaman aşımı — 3 kaynak kurtarıldı"*, *"S1E1 bölümü bulunamadı"*, *"Arama sonuç vermedi (site erişilemiyor veya CF korumalı)"*).

### 🖼️ 3. Tüm Kaynaklardan Birleşik Karakter & Kişi Galerisi (`KitsugiPersonImageAggregator.kt`, `CharacterDetailViewModel.kt`, `StaffDetailViewModel.kt`)
- **Evrensel Galeri Toplayıcı:** Karakter ve seslendirmen/personel detay sayfalarındaki galeri tek kaynak sınırından kurtarıldı.
- **Katkı Sağlayan Kaynaklar:** MAL (Jikan `/pictures` çoklu görsel), AniList, Shikimori, Kitsu (karakterler), Bangumi ve TMDB (kişiler — 12'ye kadar profil fotoğrafı). Her görsel kendi kaynak rozetiyle galeri modalında gösterilir.
- **Sıkı Kimlik & İsim Doğrulaması:** İsim bazlı çapraz aramalarda katı eşleşme (normalize eşitlik veya Doğu/Batı ad sırası jeton-kümesi) aranır; yanlış kişi/karakter görseli riski engellendi.
- **Paralel & Dayanıklı:** Tüm kaynaklar eşzamanlı ~9 sn bütçeyle sorgulanır; süre dolarsa kısmi sonuçlar gösterilir.

### 📅 4. "Yakında Yayında" Her Yerde & TMDB Haftalık Takvim Düzeltmesi (`KitsugiAiringCalendarClient.kt`, `ExploreViewModel.kt`, `AllSourcesExploreContent.kt`)
- **TMDB Günlük Yayın Düzeltmesi:** Uzun soluklu dizilerin ilk yayın yıllarının gününe düşmesi hatası giderildi; `discover/tv?air_date` filtresi haftanın her günü için ayrı sorgulanarak yapımlar gerçek yayın günlerine yerleştirildi. Bilinmeyen bölüm numaraları için "Dizi" etiketi gösterilir.
- **Ortak Geri Sayımlı Şerit (Tümü Keşfet Sayfası):** Keşfet ana sayfasında ("Tümü" modu) tüm kaynakların en üstünde tek ortak "Yakında Yayında" geri sayımlı şerit gösterilir.
- **Tüm Platformlarda Kesintisiz Geri Sayım:** Kitsu, Shikimori, Bangumi ve Simkl sekmelerine de gerçek bölüm saatli takvim verisi entegre edildi. Şerit oku doğrudan haftalık yayın takvimini açar.

### 📦 5. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.223-foss.apk`).

---

## 🇬🇧 English (v2.4.223)

### 👤 1. Profile Favorites & "My List" Layout Parity (`ProfileFavoritesListemStyle.kt`, `AniListFavoritesTab.kt`, `KitsugiUserMediaListScreen.kt`)
- **"My List" Card Layouts:** Profile favorites (Anime, Manga, Characters, Staff, Studios) for both personal and public user profiles now mirror "My List" layout styles (`compact`, `comfortable`, `minimalist`, `large`, `grid_2col`) driven by the user's persistent display preferences.
- **Infinite Scrolling:** Removed legacy "Load More" pagination buttons in favor of smooth automatic infinite scroll.
- **Dynamic Floating Controls:** Added bottom floating category selector (Anime, Manga, Characters, Staff, Studios) and quick scroll-to-top button with scroll-aware auto-hiding.
- **Public User Media Lists:** Third-party user anime/manga lists adopted "My List" native card layouts and section headers.
- **Studio Avatar Fallbacks:** Studios lacking banner artwork display stylized monogram initials rather than blank tiles.

### 🌊 2. Cloudstream Stream Pipeline & Partial Result Rescue (`CsStreamRunner.kt`, `StreamViewModel.kt`)
- **Partial Result Rescue (Partial Sink):** Fixed a critical pipeline issue where found media streams were discarded if provider execution exceeded timeout. When a timeout occurs, any streams extracted up to that point (`partialSink`) are preserved and delivered to the player/user.
- **Parallel HEAD Liveness Verification:** Replaced sequential HTTP HEAD checks (8s × N) with a 6-worker parallel pool (5s timeout). Prevents dead CDNs from burning the provider budget and triggering false negatives.
- **Time-Boxed Parallel Search:** Query variants are now dispatched concurrently in batches of 3 within a strict time-box (20s standard, 40s CF). Early pruning preserves remaining execution budget for page loading and stream extraction.
- **Extended Provider Budget:** Adjusted provider execution budget to 75s (standard) and 120s (Cloudflare-protected). As providers execute concurrently, overall user latency is unaffected.
- **Complete Verification Reset:** Properly evicts `api.name`, `plugin.name`, and `plugin.id` from `CsPluginStatusTracker` following successful Cloudflare WebView verification, preventing false "blocked" state loops on retries.
- **Diagnostic UI Badging:** Cards now articulate the exact underlying cause instead of generic "no streams found" (e.g., *"Timeout — 3 streams rescued"*, *"Episode S1E1 not found"*, *"Search returned 0 results (unreachable or CF/WAF)"*).

### 🖼️ 3. Multi-Source Aggregated Character & Person Gallery (`KitsugiPersonImageAggregator.kt`, `CharacterDetailViewModel.kt`, `StaffDetailViewModel.kt`)
- **Universal Image Aggregator:** Character and staff/voice actor gallery drawers aggregate photos across all connected databases.
- **Integrated Sources:** MAL (Jikan `/pictures`), AniList, Shikimori, Kitsu (characters), Bangumi, and TMDB (persons — up to 12 profile stills). Every image features its respective platform badge.
- **Strict Identity Matching:** Strict normalization and Japanese naming order token-set matching prevent cross-entity image misattribution.
- **Concurrent & Resilient:** All sources query concurrently with a ~9s budget; partial results are gracefully rendered upon deadline.

### 📅 4. "Airing Soon" Everywhere & TMDB Weekly Schedule Fix (`KitsugiAiringCalendarClient.kt`, `ExploreViewModel.kt`, `AllSourcesExploreContent.kt`)
- **TMDB Per-Day Schedule Fix:** Fixed long-running series falling onto old historical premiere dates by querying `discover/tv?air_date` day-by-day for the current week. Unknown episode numbers clearly display "TV" badge.
- **Unified Countdown Shelf on All Explore:** A shared, authoritative "Airing Soon" countdown shelf is rendered directly above sources on the "All" explore tab.
- **Cross-Platform Parity:** Kitsu, Shikimori, Bangumi, and Simkl now feature genuine countdown airing data. Shelf header navigates directly to the weekly calendar view.

### 📦 5. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.223-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.222)

### 🌟 1. Evrensel Sosyal Birleştirme & Platform Rozetleri (`KitsugiMediaSocialClient.kt`, `ReviewsTab.kt`, `KitsugiReviewCard.kt`)
- **Evrensel İncelemeler & Konular:** Hangi kaynakta olunursa olunsun (AniList, MAL, TMDB, SIMKL, Kitsu, Shikimori, Bangumi), çapraz kimlikler çözülerek (ARM / KitsugiIdResolver / Bangumi cross-id) ilgili tüm platformlardaki incelemeler, forum konuları ve aktiviteler tek havuzda birleştirilir.
- **Platform Rozetleri:** Yorum kartları, forum konuları ve aktivitelerde kullanıcı adının yanında platform logosu (`KitsugiPlatformLogo`) gösterilir.
- **Akıllı Beğeni & Profil Yönlendirmesi:** AniList kullanıcıları doğrudan uygulama içi profile yönlendirilir; diğer platform kullanıcıları dış web profillerine açılır. Beğeni desteği AniList kaynaklı gönderiler için sorunsuz çalışır.
- **Çok Dilli İncelemeler:** TMDB incelemelerindeki dil kilidi (`language=tr-TR`) kaldırıldı; tüm dillerdeki incelemeler orijinal dil rozetiyle (TR, EN, JA, DE...) gelir, sayfalama ve çeviri butonu entegre çalışır.

### 🎬 2. Stüdyo & Yapımcı Sayfalarında Keşfet Paritesi (`StudioDetailPage.kt`, `StudioDetailComponents.kt`)
- **Keşfet Tasarım Dili:** Stüdyo detay sayfaları ana keşfet açılır sayfalarıyla birebir görsel ve işlevsel pariteye kavuşturuldu.
- **Grid ↔ Liste Modu & Kalıcı Tercih:** Keşfet poster kartları ile degrade şeritli ızgara modu ve detaylı liste modu arasında geçiş desteği.
- **Duyarlı Kolon Düzeni:** Dikey ve yatay ekran modlarında genişliğe göre akıllı 3–5 kolon hesabı.
- **Gelişmiş Filtreleme:** Üst çip şeridi (Tümü / Anime / Manga / Film / Dizi) ve "Filtre ve Sıralama" bottom sheet penceresi.

### 🎭 3. TMDB Kişi Adlarında Romaji & "Yönetmenlik" Çevirisi (`TmdbCreditsClient.kt`, `strings.xml`)
- **Romaji Önceliği:** TMDB'den Japonca (CJK) gelen yönetmen, oyuncu ve ekip adları için `also_known_as` listesinden Latin/romaji isimler otomatik seçilir (`romanizedName`).
- **Sınırlı Bellek Önbelleği:** Kişi sorguları `BoundedCache` (LRU 500) ile tutulur, yanıt başına en fazla 12 sorgu yapılarak ağ yükü korunur.
- **Rol Çevirisi:** "Directing" görevi için `staff_role_directing` ("Yönetmenlik") çevirisi eklendi.

### 🧩 4. Kitsu Karakter Detayı & MAL İlişki Kapak İyileştirmeleri (`KitsugiCharacterClient.kt`, `KitsugiMediaRelationsClient.kt`)
- **Kitsu Karakter Kurtarma:** Kitsu API'sinden 404/429 dönen veya eşleşmesi eksik yeni yapımdaki karakterlerde kart görseli, AniList isim araması ve Jikan yedekleri devreye girerek "Detaylar yüklenemedi" hatası engellendi.
- **MAL İlişki Kapakları:** Jikan ilişkilerindeki eksik kapak görselleri AniList üzerinden toplu sorguyla çekilerek gri placeholder'lar kaldırıldı ve afişler eklendi.

### 🏮 5. Bangumi Etiket Sözlüğü, Başlık Önbelleği & Tıklanabilir Stüdyolar (`BangumiTagDictionary.kt`, `BangumiTitleCache.kt`, `KitsugiBangumiDetailClient.kt`)
- **Kapsamlı Etiket Sözlüğü:** 523 grup ve 2182 yazım içeren otoriter sözlük; takvim etiketleri regex ile çözülür, arayüz diline göre gösterilir.
- **Kalıcı Latin Başlık Önbelleği:** Keşfet şeritleri ve ilişkilerdeki CJK başlıklar için kalıcı Latin önbellek (`BangumiTitleCache`, LRU 1500).
- **Tıklanabilir Kurumlar & Kanonik Adlar:** Bangumi yapımcı/stüdyoları kişi arama API'siyle eşlenip tıklanabilir hale getirildi; tanınan stüdyolar için 117 kanonik Latin yazım eşlemesi uygulandı.

### 🛡️ 6. Bellek Sızıntısı & Şişme Koruması (`BoundedCache.kt`, `KitsugiMemoryGuard.kt`)
- **LRU Sınırlı Önbellekler:** 18 dosyadaki 30'dan fazla sınırsız harita LRU sınırlı `BoundedCache`'e geçirildi.
- **Bellek Bekçisi:** Android'in `onTrimMemory`/`onLowMemory` geri çağrıları dinlenir; 15 saniyelik bekçi %80 heap doluluğunda %50, %90 doluluğunda %100 tahliye uygular.
- **Negatif Fragman Önbelleği & Çökme Raporu:** Boş fragman sonuçlarının önbelleklenmesi düzeltildi; çökme raporlarına önbellek doluluk metrikleri eklendi.

### ⚡ 7. Derleme Performansı & Gradle İyileştirmeleri (`gradle.properties`)
- **Kotlin Incremental Build:** `kotlin.incremental=true` ve `kotlin.incremental.useClasspathSnapshot=true` aktif edildi.
- **Paralel Görevler:** `kotlin.parallel.tasks.in.project=true` ve `android.r8.optimizedResourceShrinking=true` eklendi.

### 📦 8. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.222-foss.apk`).

---

## 🇬🇧 English (v2.4.222)

### 🌟 1. Universal Social Merge & Platform Badges (`KitsugiMediaSocialClient.kt`, `ReviewsTab.kt`, `KitsugiReviewCard.kt`)
- **Cross-Platform Reviews & Topics:** Regardless of current media source (AniList, MAL, TMDB, SIMKL, Kitsu, Shikimori, Bangumi), cross-ID resolution (ARM / KitsugiIdResolver / Bangumi cross-id) aggregates reviews, discussion topics, and activities from all connected databases into a unified feed.
- **Platform Badges:** Display platform logos (`KitsugiPlatformLogo`) beside author usernames in review cards, discussion topics, and activity feeds.
- **Intelligent Likes & Profile Navigation:** In-app navigation for AniList profiles; external browser intent for third-party platforms. Native liking enabled for AniList-sourced items.
- **Multilingual Reviews:** Unlocked TMDB review language restrictions; reviews in all languages load with original language badges (TR, EN, JA, DE...) and integrated pagination/translation.

### 🎬 2. Studio & Producer Explore Page Parity (`StudioDetailPage.kt`, `StudioDetailComponents.kt`)
- **Explore Design Parity:** Studio detail screens upgraded to mirror explore category sheets with consistent typography and padding.
- **Grid ↔ List View Persistence:** Toggle between poster gradient cards and detailed list rows with persistent user preferences.
- **Responsive Layout:** Dynamic 3-5 column layout calculation for portrait and landscape orientations.
- **Filters & Sorting:** Top chip filter row (All / Anime / Manga / Movie / TV) plus comprehensive sorting bottom sheet.

### 🎭 3. TMDB Romaji Person Names & Directing Translation (`TmdbCreditsClient.kt`, `strings.xml`)
- **Romaji Priority:** Automatic Latin/romaji name selection from `also_known_as` for CJK cast and crew in TMDB (`romanizedName`).
- **Memory-Bounded Cache:** Cached in `BoundedCache` (LRU 500), limited to 12 lookups per credits response.
- **Directing Role:** Added `staff_role_directing` translation ("Yönetmenlik").

### 🧩 4. Kitsu Character Detail & MAL Relations Covers (`KitsugiCharacterClient.kt`, `KitsugiMediaRelationsClient.kt`)
- **Kitsu Character Resiliency:** Fallback to card artwork, AniList name search, and Jikan search on Kitsu 404/429 errors.
- **MAL Relations Cover Art:** Bulk AniList lookup for Jikan relation items to replace grey placeholders with posters.

### 🏮 5. Bangumi Tag Dictionary & Title Cache (`BangumiTagDictionary.kt`, `BangumiTitleCache.kt`)
- **Tag Dictionary:** Authoritative dictionary of 523 groups / 2182 variants with calendar regex translation.
- **Persistent Latin Title Cache:** High-speed LRU-1500 caching for titles across explore and relations.
- **Interactive Studios:** Resolution of Bangumi company entities into clickable studio profiles with 117 canonical name mappings.

### 🛡️ 6. Memory Guard & Leak Protections (`BoundedCache.kt`, `KitsugiMemoryGuard.kt`)
- **Bounded LRU Caches:** Replaced 30+ unbounded maps across 18 files with `BoundedCache`.
- **System Memory Hooks:** Active hooks into `onTrimMemory`/`onLowMemory` with 15s heap watchdog.

### ⚡ 7. Build Performance & Gradle Tuning (`gradle.properties`)
- **Kotlin Incremental Build:** Enabled `kotlin.incremental=true` and `kotlin.incremental.useClasspathSnapshot=true`.
- **Parallel Tasks:** Added `kotlin.parallel.tasks.in.project=true` and `android.r8.optimizedResourceShrinking=true`.

### 📦 8. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.223-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.221)

### 🌐 1. Çok Dilli İncelemeler & Orijinal Dil Rozetleri (`TmdbCreditsClient.kt`, `KitsugiReviewCard.kt`)
- **Tüm Dillerde İncelemeler:** TMDB inceleme isteklerindeki sabit `language=tr-TR` filtresi kaldırıldı. İncelemeler hangi dilde yazıldıysa o dilde eksiksiz listelenir.
- **TMDB İnceleme Sayfalaması:** "Tümünü Gör" ekranında TMDB incelemeleri için sayfalama desteği (`page`) eklendi.
- **Orijinal Dil Rozeti:** İnceleme kartlarına orijinal dil kodu rozeti (EN, JA, DE vb.) eklendi ve çeviri motoruna kaynak dil olarak aktarıldı.

### 💬 2. Evrensel Tartışma Konuları & Aktiviteler (`KitsugiMediaSocialClient.kt`, `ReviewsTab.kt`)
- **TMDB & SIMKL Kısıtlaması Kaldırıldı:** Kaynak fark etmeksizin (TMDB, SIMKL, Kitsu, Shikimori, Bangumi, MAL) gerçek MAL/AniList kimlik eşlemesi çözülebildiğinde forum konuları ve aktiviteler dinamik olarak yüklenir; çözülemezse bölüm gizlenir ("varsa görünür" kuralı).

### 🌍 3. Akıllı Çok Dilli Çeviri Motoru (`KitsugiTranslateUtils.kt`, `SettingsDataStore.kt`)
- **Dinamik Çeviri:** Sabit `en -> tr` kuralı kaldırıldı; kaynak dil = otomatik algılama veya incelemenin dili, hedef dil = kullanıcının seçtiği hedef dil veya uygulamanın aktif dili.
- **Evrensel Web Fallback:** Google Translate uygulaması kurulu olmadığında tüm dilleri destekleyen `translate.google.com/?sl=auto&tl=...` web arayüzü açılır.
- **130+ Dil Desteği:** Ayarlar ekranındaki dil listeleri Google Translate'in 130+ diline genişletildi (kaynakta "Otomatik (Algıla)" seçeneği dahil).

### 📚 4. Keşfet: Yeni Yerel Ek Tür Rafları (`NativeKindExploreSections.kt`, `ExploreViewModel.kt`)
- **MAL:** "Manhwa & Manhua" ve "Noveller & Light Novel" rafları (Jikan filtreli).
- **Shikimori:** "Manhwa & Manhua" (`kinds=manhwa,manhua`) ve "Noveller & Light Novel" (`kinds=light_novel,novel`) rafları.
- **AniList:** "Noveller & Light Novel" (`format: NOVEL`) rafı.
- Desteklemeyen kaynaklarda (Kitsu) raflar temiz bir şekilde gizlenir. Hem "Tümü" hem tek kaynak modunda "Tümünü Gör" sayfalaması ve vitrin puanlaması entegre edildi.

### 🎨 5. Stüdyo & Yapımcı Sayfası Keşfet Paritesi (`StudioDetailPage.kt`, `StudioDetailComponents.kt`)
- **Keşfet Sayfası Uyumlu Düzen:** Keşfet açılır sayfalarıyla ("Shikimori · En İyi Animeler") birebir aynı kolon hesabı, dikey/yatay mod duyarlılığı ve akıcı sayfa yapısı.
- **Gelişmiş Filtreleme & Görünüm:** Emojili tür filtre çipleri (Tümü, Anime, Manga, Film, Dizi), Filtre ve Sıralama bottom sheet'i, içerik sayacı, yukarı kaydırma FAB'ı, kalıcı Grid ↔ Liste geçişi ve stüdyo açıklaması ("Hakkında") desteği.

### 🏷️ 6. Ortak Kaynak Rozetleri & Logo Gösterimi (`KitsugiSourceBadge.kt`, `DetailSharedComponents.kt`)
- **Logo + Doğru İsim Rozeti:** Vitrin (hero), detay açılışındaki yükleme ekranı ve detay sayfası başlığında ham metinler yerine logo + okunabilir isim gösteren `KitsugiSourceNamePill` ve `DetailSourcePill` entegrasyonu (AniList, MAL, TMDB, Kitsu, Shikimori, Simkl, Bangumi).

### 🧪 7. Birim Testleri
- `AllSourcesExploreTest.kt` ile yerel ek tür raflarının filtreleme ve görünürlük mantığı doğrulandı.

### 📦 8. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.221-foss.apk`).

---

## 🇬🇧 English (v2.4.221)

### 🌐 1. Multilingual Reviews & Original Language Badges (`TmdbCreditsClient.kt`, `KitsugiReviewCard.kt`)
- **Unrestricted Reviews:** Removed language lock (`language=tr-TR`) from TMDB review requests so reviews in all languages load intact.
- **TMDB Review Pagination:** Added pagination (`page`) support for TMDB reviews in the "See all" sheet.
- **Language Badge:** Display original language tag (EN, JA, DE, etc.) on review cards, passed as source language to translators.

### 💬 2. Universal Forum Topics & Activities (`KitsugiMediaSocialClient.kt`, `ReviewsTab.kt`)
- **Broadened Social Tabs:** Lifted TMDB/SIMKL restrictions; whenever MAL/AniList cross-mappings resolve, discussions and activities appear seamlessly across all sources.

### 🌍 3. Smart Multilingual Translation Engine (`KitsugiTranslateUtils.kt`, `SettingsDataStore.kt`)
- **Dynamic Language Pairs:** Replaced fixed `en -> tr` with dynamic auto-detection / original review language to user-selected target (or active app locale).
- **Web Fallback:** Added graceful fallback to `translate.google.com/?sl=auto&tl=...` when Google Translate app is absent.
- **130+ Languages:** Expanded language settings catalog to over 130 Google Translate locales.

### 📚 4. Explore: Native Media Subcategory Shelves (`NativeKindExploreSections.kt`, `ExploreViewModel.kt`)
- **MAL:** "Manhwa & Manhua" and "Novels & Light Novels" shelves.
- **Shikimori:** "Manhwa & Manhua" and "Novels & Light Novels" shelves.
- **AniList:** "Novels & Light Novels" shelf.
- Automatically hidden on unsupported catalogs (Kitsu). Full support for "See all" pagination and hero scoring.

### 🎨 5. Studio Detail Page Explore Parity (`StudioDetailPage.kt`, `StudioDetailComponents.kt`)
- **Unified Explore Layout:** Matches explore category grid responsive columns, portrait/landscape continuity, and layout flow.
- **Filtering & Views:** Added emoji chips (All, Anime, Manga, Movies, Series), Filter/Sort sheet, count indicator, scroll-to-top FAB, persistent Grid ↔ List toggle, and studio "About" section.

### 🏷️ 6. Unified Source Badges & Logos (`KitsugiSourceBadge.kt`, `DetailSharedComponents.kt`)
- **Logo + Friendly Name:** Hero showcase, loading screens, and detail headers now render unified `KitsugiSourceNamePill` and `DetailSourcePill` with crisp logos and standardized labels.

### 🧪 7. Unit Tests
- Verified with `AllSourcesExploreTest.kt`.

### 📦 8. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.221-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.220)

### 🧹 1. Kitsu Bozuk Özet (Synopsis) Tespiti ve Temizliği (`KitsuSynopsisValidator.kt`, `KitsugiDetailClient.kt`)
- **Akıllı Bozuk Özet Tespiti:** Kitsu veritabanında vandalize edilmiş veya hatalı birleştirilmiş kayıtlar (örneğin 2004 yapımı anime olan "ROAR" kaydında 1997 yapımı Fox TV dizisi "Roar"ın özetinin bulunması) tespit edilir:
  - Anime veya film türündeki kayıtlarda Amerikan/İngiliz canlı-aksiyon TV dizisi tarif eden ifadeler (`is an American television show`, `aired on the Fox network` vb.) şüpheli olarak yakalanır.
  - Özet yayın tarihi bağlamında ("aired/premiered/released in 19XX") kayıt yılından 2 yıldan fazla sapan tarihler şüpheli kabul edilir.
  - Canlı-aksiyon diziler (`TvShow`), Kore dizileri ve hikaye içi geçen geçmiş yıllar yanlış-pozitif üretmeyecek şekilde korunur.
- **Doğrulanmış Kaynak İle Değiştirme:** Şüpheli Kitsu özetleri MAL/Jikan üzerinden doğrulanmış temiz özetle (`CrossSyncIdentityGuard` sıkı başlık kontrolüyle) otomatik değiştirilir; doğrulanamazsa özet boşaltılır ("Açıklama bulunamadı" gösterilir, alakasız özet engellenir).
- **Önbellek Sürümleme & Çeviri Temizliği:** Kitsu detay önbellek anahtarı `_ks1` olarak sürümlendi; eski bozuk önbellekler geçersiz kılındı. Bozuk özetlerin çevirileri `DetailCache.removeTranslation` ile bellekten temizlenir.
- **Yedek Zinciri Koruması:** AniList/MAL → Kitsu arama zinciri ve `fetchSynopsis` fonksiyonuna (listeye ekleme penceresi, hero önizleme) Kitsu özet doğrulama mantığı entegre edildi.

### 🌐 2. MyAnimeList & Jikan Çoklu Host Zinciri (`JikanGateway.kt`, `JikanSearchClient.kt`)
- **Yeni Kaynak Sıralaması:** Jikan genel API kesintileri ve kapanışına karşı arama ve keşfet zinciri optimize edildi:
  1. Resmi MAL v2 (`api.myanimelist.net`)
  2. miribyou (`https://miribyou-topaz.vercel.app` / `MIRIBYOU_BASE_URL`)
  3. Tenrai (`https://api.tenrai.org/v1`)
  4. AniList (son çare yedek)
- **Çoklu Host Desteği & Devre Kesici:** `JikanGateway` üzerinden host bazında bağımsız hız sınırlaması, 429 soğuması ve 3 ardışık hatada devre kesici (circuit breaker) eklendi.
- **Tenrai Uyumluluğu:** Tenrai API sorgu parametreleri (`sfw=true` → `sfw`) otomatik dönüştürülür.
- **İstek Tekilleştirme & Özel İstemci:** `KitsugiHttpClient.jikanClient` ile harici `RetryInterceptor` devre dışı bırakılarak gereksiz kota tüketimi ve 429 tetiklemeleri engellendi.
- **Detay ve Görsel Entegrasyonu:** `KitsugiMalDetailClient`, `CharacterDetailViewModel` ve `StaffDetailViewModel` `/pictures` istekleri merkezi `JikanGateway` üzerinden yönlendirildi.

### 🧪 3. Birim Testleri
- `KitsuSynopsisValidatorTest.kt` ile 15 birim testi tamamlandı ve başarıyla doğrulandı.

### 📦 4. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.220-foss.apk`).

---

## 🇬🇧 English (v2.4.220)

### 🧹 1. Kitsu Corrupted Synopsis Detection & Sanitization (`KitsuSynopsisValidator.kt`, `KitsugiDetailClient.kt`)
- **Smart Anomaly Detection:** Identifies vandalized or incorrectly merged Kitsu catalog entries (e.g., 2004 anime "ROAR" containing synopsis of the 1997 Fox TV live-action show "Roar"):
  - Flags western live-action TV show phrasing (`is an American television show`, `aired on the Fox network`, etc.) for Anime and Movie media types.
  - Flags broadcasting year discrepancies exceeding 2 years in release verbs (`aired/premiered/released in 19XX`).
  - Safe against false-positives for legitimate `TvShow` entries, K-dramas, and in-universe fictional dates.
- **Verified Replacement Fallback:** Suspicious synopses are replaced by verified summaries fetched from MAL/Jikan guarded by `CrossSyncIdentityGuard.titlesLookRelated`; unverified synopses are safely cleared to avoid misleading descriptions.
- **Cache Versioning & Translation Eviction:** Kitsu detail cache keyed with `_ks1` to bypass contaminated cache entries. Stale synopsis translations are evicted via `DetailCache.removeTranslation`.
- **Fallback Chain Protection:** Integrated into the AniList/MAL → Kitsu fallback chain (15s timeout) and `fetchSynopsis` (add-to-list dialog, hero preview).

### 🌐 2. MyAnimeList & Jikan Multi-Host Chain (`JikanGateway.kt`, `JikanSearchClient.kt`)
- **Optimized Fallback Hierarchy:** Resilient routing against public Jikan downtime:
  1. Official MAL v2 (`api.myanimelist.net`)
  2. miribyou (`https://miribyou-topaz.vercel.app` / `MIRIBYOU_BASE_URL`)
  3. Tenrai (`https://api.tenrai.org/v1`)
  4. AniList (ultimate fallback)
- **Multi-Host Gateway & Circuit Breakers:** `JikanGateway` now coordinates host-level rate limiting, 429 cool-down, and circuit breakers (tripping on 3 consecutive server/network failures).
- **Tenrai Compatibility:** Seamless parameter translation (`sfw=true` → `sfw`).
- **Quota Leak Fix:** Introduced `KitsugiHttpClient.jikanClient` without `RetryInterceptor` to prevent quota burning outside the gateway.
- **Detail & Asset Gateway Routing:** `KitsugiMalDetailClient`, `CharacterDetailViewModel`, and `StaffDetailViewModel` `/pictures` requests now traverse `JikanGateway`.

### 🧪 3. Unit Tests
- 15 unit tests in `KitsuSynopsisValidatorTest.kt` passed successfully.

### 📦 4. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.220-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.219)

### 🔑 1. Harici API Entegrasyonları Doğrulama & Yönetim (`KitsugiIntegrationsSettingsDialog.kt`)
- **Canlı Anahtar Doğrulama:** TMDB ve Fanart.tv kişisel API anahtarları için anlık servis bağlantı testi eklendi; yalnızca doğrulanmış anahtarlar kaydedilir.
- **Güvenli Temizleme:** Alan temizlendiğinde uygulamanın yerleşik ortak anahtarına geri dönüş sağlanır.
- **MDBList Anahtar Desteği:** Giriş sırasında boşluk kırpma ve doğrulama eklendi.
- **Şifre / Maske Görünürlük Kontrolü:** API anahtarlarını gizleme ve gösterme kontrolleri entegre edildi.

### 📊 2. MDBList TMDB ID Yedek Araması & Akıllı Ayrıştırma (`MdbListClient.kt`)
- **Otomatik TMDB Yedeği:** IMDb kimliği bulunmayan içeriklerde pozitif TMDB ID ile otomatik puan sorgulama desteği eklendi.
- **Genişletilmiş Yanıt Formatı:** Hem yeni dizi hem de eski üst-seviye alan formatları eksiksiz ayrıştırılır ve önbelleğe alınır.
- `ApiResultDetailViewModel` ve `MediaEntryDetailViewModel` üzerinde IMDb/TMDB puan çağrıları bu yedek mekanizmasına bağlandı.

### 🎬 3. TMDB Özellik Kapıları & Televizyon Ağları (`TmdbMediaDetailClient.kt`)
- **Granüler İstek Kapıları:** Ayrıntı, afiş/arka plan, fragman, yapım şirketi, ağlar, oyuncu/ekip ve koleksiyon ayar kapıları API isteklerine ve model alanlarına bağlandı.
- **Yayın Ağları (Networks):** TV içeriklerinde yapımcı televizyon kanalları ve yayın ağları detay kartlarına eklendi.
- **Dinamik Keşfet Senkronizasyonu:** Keşfet sayfasındaki modern TMDB içerik rafları ayar değişikliğinde anında güncellenir (`tmdbModernHomeEnabled`).

### 🛡️ 4. Hassas URL Maskeleme & İstek Güvenliği (`SensitiveUrlRedactor.kt`, `KitsugiApiBase.kt`)
- **Günlük Güvenliği:** Ağ isteklerinde ve hata kayıtlarında API anahtarları, token'lar ve hassas parametreler otomatik olarak maskelenir (`***`).

### 🧪 5. Test Kapsamı
- `MdbListClientTest.kt` ve `TmdbMediaDetailClientTest.kt` ile MDBList URL/ayrıştırma ve TMDB özellik filtreleme mantığı için kapsamlı birim testleri eklendi.

### 📦 6. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.219-foss.apk`).

---

## 🇬🇧 English (v2.4.219)

### 🔑 1. External API Integration Validation & Management (`KitsugiIntegrationsSettingsDialog.kt`)
- **Live Key Validation:** Added in-place connection verification for personal TMDB and Fanart.tv API keys; unverified keys are never saved.
- **Safe Key Reset:** Clearing key fields safely falls back to the app's built-in shared credentials.
- **MDBList Trimming & Checks:** Key whitespace trimmed and sanitized automatically.
- **Masking Controls:** Added show/hide toggle buttons for sensitive key fields.

### 📊 2. MDBList TMDB ID Fallback & Smart Response Parsing (`MdbListClient.kt`)
- **Automatic TMDB Fallback:** When IMDb identifiers are absent, ratings are queried via positive TMDB IDs (`/ratings/tmdb/...`).
- **Unified Schema Support:** Smoothly parses both modern list schemas and legacy top-level rating structures with caching.
- Integrated into `ApiResultDetailViewModel` and `MediaEntryDetailViewModel`.

### 🎬 3. TMDB Feature Gating & TV Networks (`TmdbMediaDetailClient.kt`)
- **Granular Feature Toggles:** Respects user toggles for basic info, release dates, artwork, trailers, productions, networks, credits, more-like-this, and collections.
- **Broadcast Networks:** Added TV broadcast networks to detail metadata cards.
- **Instant Explore Sync:** Modern TMDB rails in Explore react immediately to setting changes (`tmdbModernHomeEnabled`).

### 🛡️ 4. Sensitive URL Redaction & Logging Safety (`SensitiveUrlRedactor.kt`, `KitsugiApiBase.kt`)
- **Log Sanitization:** Automatically masks API keys, secrets, and authorization tokens in URLs across logs and exceptions (`***`).

### 🧪 5. Test Suite
- Added regression unit tests in `MdbListClientTest.kt` and `TmdbMediaDetailClientTest.kt`.

### 📦 6. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.219-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.218)

### 🔍 1. Cloudstream Canlı İzleme & Tanı Raporlama Sistemi (`CsTrace`)
- **Otomatik Canlı Kayıt (`CsTrace.kt`, `CsTraceCore.kt`):** Eklenti yükleme, arama, başlık varyantları, eşleşme, bölüm bulma, doğrudan link ve embed çıkarma, `loadLinks` aşamaları `filesDir/cs_trace/` altında otomatik olarak kalıcı günlüğe yazılır.
- **Eşleşme Reddi Şeffaflığı:** Hiçbir adayın eşleşme eşiğini geçemediği durumlarda hedef başlık, sezon/bölüm, yıl ve en iyi 5 adayın benzerlik skorları kaydedilir ("neden 0 sonuç" durumu netleşti).
- **Hata & Çökme Takibi:** `loadLinks` zaman aşımları, DEX yükleme hataları ve kritik istisnalar izleme sistemine aktarılır.
- **Yanıltıcı Durum Mesajı Düzeltildi:** `domain_fixes.json` engelli listesindeki eklentiler denenirken tracker'a yazılan yanıltıcı "skip" yerine durum yalnızca izleme günlüğüne yazılır.
- **Canlı Rapor Paylaşımı:** Ayarlar > Eklenti Tanı ekranına **"Canlı İzleme Raporunu Paylaş (.md)"** düğmesi eklendi; oluşturulan Markdown raporu Android paylaşım penceresiyle doğrudan iletilebilir.

### 🎬 2. Kapsamlı Fragman Zinciri (`DetailTrailerFallback.kt`)
- **Çok Katmanlı Yedek Mekanizması:** Kaynak kendi YouTube fragmanını sağlamadığında arka planda sırayla **TMDB → Diğer Metadata Kaynakları (AniList, Jikan, Kitsu) → Cloudstream Eklentileri** (arama ve load yanıtındaki fragmanlar) taranır.
- **Dinamik Ön İzleme Kartı:** Bulunan fragman detay durumuna ve önbelleğe yazılır; `ApiResultDetailViewModel` ve `MediaEntryDetailViewModel` üzerinden "Ön izleme" kartı otomatik olarak belirir.

### 🔘 3. İzle Butonu Tasarım Hizalaması (`KitsugiUiverseGlowButton.kt`)
- **Tek Tip Eylem Butonları:** `KitsugiDetailActionButton` artık `ApiActionButton` ve `ActionButton` ile tam uyumlu: 999dp hap form, 14×10dp dolgu, `labelMedium` tipografi ve 16dp ikon ile görsel bütünlük sağlandı.

### ❌ 4. Açılır Sayfa ve Oynatıcı Kapatma Butonu Düzenlemeleri
- **Player Dışındaki Yüzen Çarpılar Temizlendi (`KitsugiSheetOrDialog.kt`):** v2.4.215'te eklenen evrensel yüzen çarpı kaldırıldı; sayfaların kendi doğal başlık yapıları korundu.
- **Player İçi Açılır Sayfalara Çarpı Butonu:** `PlayerSheet` sürükleme kolu satırına sağa hizalı kapatma çarpısı eklendi (Altyazılar, Ses, Kalite/Kaynak, Bölümler, Hız, Ekran Görüntüsü vb.).
- `EpisodeListDialog` ve `QualityProfileDialog` için tek dokunuşluk kapatma butonları eklendi.

### 🔙 5. Oynatıcı Geri Tuşu ve Jest Gezinme Zinciri (`KitsugiFullscreenPlayerActivity.kt`)
- **Deterministik Çıkış Zinciri:** Jest gezinme, 3 tuşlu çubuk ve donanım geri tuşlarının tamamında çalışan `OnBackPressedDispatcher` callback'i kuruldu:
  - `dialog` → `panel` → `sheet` → `kontrolleri gizle` → `oynatıcıdan çık`.

### 📦 6. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.218-foss.apk`).

---

## 🇬🇧 English (v2.4.218)

### 🔍 1. Cloudstream Live Trace & Diagnostic Reporting (`CsTrace`)
- **Automated Live Tracing (`CsTrace.kt`, `CsTraceCore.kt`):** Plugin loading, search query variants, title matching, episode extraction, direct links, embed scrapers, and `loadLinks` phases are persisted to `filesDir/cs_trace/`.
- **Match Rejection Transparency:** When candidate items fail matching thresholds, the target title, season, episode, year, and similarity scores of top 5 candidates are logged to reveal the exact reason behind empty results.
- **Fail-Safe Exception Recording:** `loadLinks` timeouts, DEX loading exceptions, and critical errors are automatically captured.
- **Accurate Status Attribution:** Suppressed misleading "skip" tracker entries for plugins on domain blocklists that are still actively probed.
- **One-Tap Report Sharing:** Added **"Share Live Trace Report (.md)"** button in Settings > Plugin Diagnostics to export markdown traces via Android share sheet.

### 🎬 2. Universal Trailer Fallback Pipeline (`DetailTrailerFallback.kt`)
- **Multi-Source Fallback Chain:** If the primary source lacks a YouTube trailer, a fallback ladder queries **TMDB → Metadata Sources (AniList, Jikan, Kitsu) → Cloudstream Plugins**.
- **Dynamic Preview Card:** Resolved trailers are written to detail state and cached, automatically displaying the "Preview" card across `ApiResultDetailViewModel` and `MediaEntryDetailViewModel`.

### 🔘 3. Action Button Visual Alignment (`KitsugiUiverseGlowButton.kt`)
- **Unified Action Styling:** `KitsugiDetailActionButton` now aligns with `ApiActionButton` and `ActionButton`: 999dp pill form factor, 14×10dp padding, `labelMedium` typography, and 16dp icon.

### ❌ 4. Sheet & Player Close Button Refinements
- **Removed Extraneous Floating Close Buttons (`KitsugiSheetOrDialog.kt`):** Reverted universal floating close button outside the player, preserving native sheet headers.
- **Player Sheet Close Buttons:** Added unified close buttons to the `PlayerSheet` header bar across all bottom sheets (Subtitles, Audio Tracks, Quality/Sources, Episodes, Playback Speed, Screenshot).
- Added close buttons to `EpisodeListDialog` and `QualityProfileDialog`.

### 🔙 5. Player Back Gesture & Navigation Dispatcher Chain (`KitsugiFullscreenPlayerActivity.kt`)
- **Deterministic Back Key Handling:** Integrated an `OnBackPressedDispatcher` callback supporting gesture navigation, 3-button navigation, and hardware back keys:
  - `dialog` → `panel` → `sheet` → `hide controls` → `exit player`.

### 📦 6. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.218-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.217)

### 🚪 1. Merkezi Jikan Gateway (`JikanGateway.kt`) & MAL Trafik Optimizasyonu
- **Merkezi Kota ve Hız Sınırı (Rate Limiting):** Tüm Jikan trafiği tek bir kapıdan yönetilir; saniyede 3 ve dakikada 55 istek sınırları aşılmaz.
- **Toplu 429 Koruması:** Jikan'dan 429 (Too Many Requests) yanıtı geldiğinde tüm Jikan istekleri birlikte beklemeye geçer ve `Retry-After` başlığına uyulur.
- **Akıllı Bellek Önbelleği (In-Memory Cache):** Detay uçları 6 saat, liste ve arama uçları 30 dakika boyunca önbelleğe alınır; gereksiz ağ istekleri önlenir.
- **Eşzamanlı İstek Birleştirme (Single-Flight Deduplication):** Aynı URL'ye aynı anda gelen istekler tek bir ağ çağrısını paylaşır; sekmelerin eşzamanlı yüklenmesinde sunucuya tek istek gider.
- **Öncelikli Trafik Yönetimi:** Arka plan görevleri (profil senkronizasyonu, kimlik eşleştirme) kullanıcının aktif gezindiği ekranların kotasını tüketmez.
- **Keşfet & Arama MAL Önceliği:** MAL için Jikan önce denenir, resmi MAL API ikinci, AniList ise son yedek olarak çalışır. Karakter, ekip, ilişki, öneri ve bölüm istekleri gateway üzerinden güvenle akar.

### 🎯 2. AniList Manuel Kayıtlarında Yanlış Öneri ve Kimlik Çakışması Düzeltmesi
- **Kök Neden Giderildi (`MediaEntryDetailViewModel.kt`):** Manuel eklenen AniList kayıtlarında `malId` boş kaldığında yerel veritabanı satır numarasının (`entry.id`) dış servis kimliği gibi okunması ve başka bir yapımın MAL ID'siyle çakışarak alakasız öneriler getirmesi (ör. Rent-a-Girlfriend sayfasında farklı animelerin önerilerinin çıkması) engellendi.
- **Yalnızca Gerçek Kimlik Kullanımı:** AniList kaynaklı kayıtlarda yerel satır numarası (`entry.id`) dış kimlik olarak kabul edilmez; yalnızca geçerli bir `malId` varsa dış ID olarak kullanılır.
- **Başlık Tabanlı Arama Fallback'i (`KitsugiMediaRelationsClient.kt`):** Dış kimliği bulunmayan AniList kayıtlarında temizlenmiş başlık üzerinden doğrudan AniList API'sinde arama yapılarak (`fetchRecommendationsFromAniListBySearch`) doğru öneriler getirilir.

### 🖼️ 3. Vitrin Kaplama & Responsive Görsel Sunumu (`KitsugiHeroSection.kt`)
- **Tam Kaplayan Vitrin (Full Bleed Cover/Crop):** Bulanık arka plan dolgusu ve sığdırma (fit) yapısı kaldırılarak resmin tüm vitrin alanını kaplaması sağlandı (`ContentScale.Crop`).
- **Responsive Kaynak Seçimi (`heroImageCandidates`):**
  - Geniş vitrin bandında (en/boy $\ge$ 1.1, yatay ekran veya tablet): yatay dikdörtgen fanart/backdrop öncelikli.
  - Dikey telefon modunda (en/boy < 1.1): dikey poster öncelikli.
- **Akıllı Hizalama (`BiasAlignment(0f, -0.2f)`):** Yatay arka planlar üst gövde ve yüzleri koruyacak şekilde yukarıdan hafif odaklı hizalanır; dikey posterler ise üstten kırpılır.
- **Yedek Görsel Zinciri:** Birincil görsel yüklenemediğinde otomatik olarak alternatif adaya (backdrop $\leftrightarrow$ poster) geçilir.

### 🌸 4. Bangumi Keşfet, REAL Diziler/Filmler ve Yerelleştirme Güncellemeleri
- **Canlı Çekim (REAL) Rafları (`BangumiRealExploreSections.kt`):** Bangumi TV dizileri/dramaları ve filmleri için Keşfet bölümüne özel raflar eklendi.
- **Filtreleme & Sayfalama:** Yetişkin içerik filtreleme, vitrin ve rastgele yapım seçimi, kaynak belirtimi ve "Tümünü Gör" sayfalaması entegre edildi.
- **Genişletilmiş İsim ve Çeviri Seçimi:** Karakter, seslendirmen ve ekip adları AniList'te eşleşmediğinde doğrudan Bangumi infobox yedeklerine başvurulur; AniList köprüsü 2. sayfayı da denetler.

### 🌐 5. Türkçe Akış & Başlık Temizleme Düzeltmeleri
- **Site Gürültüsü Temizleme (`stripTurkishSiteNoise`):** Türkçe sitelerin başlıklara eklediği gürültü kalıpları 16 özel regex ile ayıklandı.
- **Genişletilmiş Sorgular:** Arama sorguları 12'den 18'e çıkarıldı, geniş arama fallback'i ve Türkçe CDN/Range optimizasyonları korundu.

### 📦 6. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.217-foss.apk`).

---

## 🇬🇧 English (v2.4.217)

### 🚪 1. Centralized Jikan Gateway (`JikanGateway.kt`) & MAL Traffic Pipeline
- **Unified Rate Limiting:** All Jikan traffic routes through a single gateway strictly bounded by 3 req/sec and 55 req/min token-bucket limits.
- **Coordinated 429 Backoff:** Upon receiving HTTP 429 (Too Many Requests), all queued and concurrent Jikan requests hold together, respecting `Retry-After`.
- **In-Memory Caching:** Detail endpoints cached for 6 hours; list and search endpoints cached for 30 minutes.
- **Single-Flight Request Deduplication:** Concurrent identical URL requests share a single underlying network call, eliminating duplicate fetches during multi-tab screen initialization.
- **Priority-Aware Traffic Budgeting:** Background workloads (profile sync, identity mapping) do not exhaust quota allocated for active user UI navigation.
- **Explore & Search MAL Priority:** Jikan is tried first for MAL, with the official MAL API and AniList serving as reliable fallbacks.

### 🎯 2. AniList Manual Entry False ID & Recommendation Mismatch Fix
- **Resolved Database Row ID Leak (`MediaEntryDetailViewModel.kt`):** Fixed an issue where manual AniList entries without a `malId` mistakenly defaulted to using their local database row index (`entry.id`) as an external API identifier, causing erroneous recommendation lookups when clashing with other titles' MAL IDs.
- **Strict External ID Resolution:** Explicitly decoupled internal database row IDs from external identifier logic for AniList sources; only valid `malId` values are passed.
- **Title-Based Recommendation Fallback (`KitsugiMediaRelationsClient.kt`):** When external IDs are absent for AniList entries, recommendations are safely retrieved by title query (`fetchRecommendationsFromAniListBySearch`) instead of leaking erroneous IDs.

### 🖼️ 3. Full Bleed Hero Cover & Responsive Presentation (`KitsugiHeroSection.kt`)
- **Full Bleed Cover/Crop:** Replaced blur-filled letterboxing with edge-to-edge `ContentScale.Crop` presentation.
- **Responsive Media Selection (`heroImageCandidates`):**
  - Wide hero banners (aspect ratio $\ge$ 1.1, landscape & tablet): landscape backdrop/fanart first.
  - Portrait mobile phone viewports: high-res vertical poster first.
- **Smart Subject Framing (`BiasAlignment(0f, -0.2f)`):** Horizontal backdrops align with a slight upward bias to keep heads and focal subjects in frame; portrait posters align to top.
- **Seamless Fallback Chain:** If the primary candidate fails to render, the hero automatically cascades to the alternate candidate (backdrop $\leftrightarrow$ poster).

### 🌸 4. Bangumi Explore REAL Shelves & Localization Updates
- **Live-Action REAL Sections (`BangumiRealExploreSections.kt`):** Introduced dedicated Explore shelves for Bangumi TV dramas and films.
- **Filtering & Pagination:** Integrated adult content filters, hero & random selection, source attribution, and "See all" pagination.
- **Expanded Infobox & Fallbacks:** Direct Bangumi fallback for character, voice-actor, and staff names when AniList lacks a match; AniList bridge checks second-page search results.

### 🌐 5. Turkish Stream Scraper & Noise Cleaning
- **Noise Stripping (`stripTurkishSiteNoise`):** Strips 16 regex noise patterns commonly found on Turkish streaming websites.
- **Broad Search & Query Expansion:** Query variants expanded to 18 with intelligent fallback matching and CDN handling.

### 📦 6. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.217-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.216)

### 🖼️ 1. Vitrin Cover / Baştan Sona Kaplama & Akıllı Görsel Seçimi
- **Tam Ekran Kaplayan Vitrin (`ContentScale.Crop`):** Önceki "Fit + bulanık dolgu" katmanları tamamen kaldırıldı. Vitrin görseli artık ekranı baştan sona kaplar, kenarlarda boşluk veya bulanık zemin kalmaz.
- **Ekran Boyutu ve Oranına Göre Akıllı Seçim (`heroImageCandidates`):**
  - Geniş vitrin bandı (yatay telefon, tablet, TV — en/boy $\ge$ 1.1) $\rightarrow$ yatay dikdörtgen fanart / backdrop öncelikli.
  - Dikey telefon vitrini (kareye yakın kutu) $\rightarrow$ dikey poster öncelikli.
- **Dinamik Hata Yedekleme Zinciri (`onLoadingFailed`):** Birincil görsel yüklenemezse anında sıradaki adaya (backdrop $\leftrightarrow$ poster) geçilir.
- **Kırpma Odak Hizalaması (`heroImageAlignment`):** Geniş vitrinde poster kullanılmak zorunda kalındığında karakter yüzü ve başlık bandının korunması için hafif yukarı bias (`BiasAlignment(0f, -0.2f)`), backdrop görsellerde merkez hizalama uygulandı.
- **Çok Yönlü TMDB Backdrop Zenginleştirmesi:** Sadece yatay mod değil, dikey tablet ve geniş vitrin bantları da TMDB fanartlarından otomatik yararlanır.
- **Yumuşatılmış Gradyanlar:** Metin okunabilirliğini korurken görselin merkezini ve detaylarını kapatmayan yumuşak renk geçişleri.
- **HeroImageSelectionTest:** 13 birim testi %100 başarıyla doğrulandı.

### 🇹🇷 2. Türkçe Akış & Eklenti Pipeline İyileştirmeleri
- **Site Başlık Kirliliği Temizleme (`stripTurkishSiteNoise`):** Türkçe dizi/film sitelerinin başlıklara eklediği reklam ve gürültü metinleri ("Türkçe Dublaj 1080p izle" vb.) 16 regex kuralı ile filtrelenerek temiz başlık eşleştirmesi sağlandı.
- **Genişletilmiş Dil & Arama Varyantları:** Başlık varyantları 8'den 12'ye, arama sorguları 12'den 18'e çıkarıldı; Türkçe karakter transliterasyonu (ğ $\rightarrow$ g, ş $\rightarrow$ s, ı $\rightarrow$ i) eklendi.
- **Geniş Arama Fallback'i (`buildBroadSearchQueries`):** Tüm varyantlar boş döndüğünde anlamlı ilk 2-3 kelime üzerinden genişletilmiş arama yapılarak farklı Türkçe adlandırmalar yakalanır.
- **45+ Yeni Türkçe CDN Tanıma:** AlionsPlayer, VidMoly, TRsTX, CloseLoad, Pichive, Rapidrame, VidGuard, TurboVid, HDVid, PlayTube, VidHide, Embedy gibi 45+ sağlayıcı `isEmbedUrl` kapsamına eklendi.
- **Gelişmiş Stream Canlılık Doğrulaması (`tryGetRangeValidation`):** HEAD 400 hatası veren akışlarda doğrudan başarısız saymak yerine GET Range header ile doğrulama yapılır; zaman aşımı süresi 6s $\rightarrow$ 8s'ye yükseltildi.
- **Türkçe Başlık Model Alanı (`titleTurkish`):** TMDB üzerinden Türkçe başlıklar çekilerek modele ve arama motoruna entegre edildi.

### 📦 3. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.216-foss.apk`).

---

## 🇬🇧 English (v2.4.216)

### 🖼️ 1. Vitrin Cover Presentation & Responsive Hero Asset Selection
- **Full Bleed Cover (`ContentScale.Crop`):** Completely removed letterboxed "Fit + ambient blur" layers. Hero artwork now stretches end-to-end across the banner container without blurred sidebars or margins.
- **Aspect-Aware Asset Prioritization (`heroImageCandidates`):**
  - Wide hero containers (landscape, tablets, TV — aspect $\ge$ 1.1) prioritize horizontal fanart / backdrop artwork.
  - Portrait mobile phone boxes prioritize vertical posters.
- **Resilient Fallback Chain (`onLoadingFailed`):** Automatically cascades to the alternative candidate (backdrop $\leftrightarrow$ poster) upon asset load failure.
- **Focal Alignment Preservation (`heroImageAlignment`):** Applies slight top-vertical bias (`BiasAlignment(0f, -0.2f)`) when vertical posters are cropped in widescreen containers to prevent cropping faces and title text.
- **Omni-Directional TMDB Backdrop Enrichment:** TMDB backdrop enrichment now activates across all orientations, including portrait tablets.
- **Softened Gradients:** Refined horizontal and vertical gradient scrims to preserve artwork visibility while ensuring text readability.
- **Unit Tests (`HeroImageSelectionTest`):** 13 unit tests passed with 100% success.

### 🇹🇷 2. Turkish Stream & Extension Scraper Pipeline Optimization
- **Stripped Turkish Title Noise (`stripTurkishSiteNoise`):** Cleans up title noise and metadata suffixes ("Türkçe Dublaj 1080p izle", etc.) via 16 regex patterns for accurate content matching.
- **Expanded Query & Language Variants:** Increased title variants from 8 to 12 and search queries from 12 to 18 with ASCII transliteration (ğ $\rightarrow$ g, ş $\rightarrow$ s, ı $\rightarrow$ i).
- **Broad Search Fallback (`buildBroadSearchQueries`):** Secondary search pass utilizing the first 2-3 significant title tokens when exact matches return empty.
- **45+ Turkish Embed & CDN Patterns:** Integrated detection for AlionsPlayer, VidMoly, TRsTX, CloseLoad, Pichive, Rapidrame, VidGuard, TurboVid, HDVid, PlayTube, VidHide, Embedy, and others.
- **Enhanced Stream Probing (`tryGetRangeValidation`):** Mitigates HEAD 400 rejections by falling back to GET requests with HTTP Range headers; increased probe timeout from 6s to 8s.
- **Turkish Title Model Field (`titleTurkish`):** Added `titleTurkish` to `KitsugiMediaDetail` populated directly from TMDB localized payloads.

### 📦 3. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.216-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.215)

### 🛡️ 1. Sessiz Çökme Kök Neden Çözümü (RenderThread SIGSEGV & WebView Teardown)
- **Çifte Destroy ve Use-After-Free Giderildi (`CsCfWarmupManager.kt`):** Çökme raporlarındaki `cr_AwContents: Application attempted to call on a destroyed WebView` uyarıları ve ardından `RenderThread` display-list oynatımı sırasında oluşan `SIGSEGV` (null+0x20) hatası tamamen çözüldü.
- **Güvenli Tek Seferlik Teardown (`releaseWebView`):** `AtomicBoolean` korumasıyla WebView yalnızca bir kez sökülür, durdurulur, `about:blank` yüklenir ve güvenle yok edilir.
- **Açılış Rahatlatması (`KitsugiApplication.kt`):** Proaktif Cloudflare warmup işlemi ilk açılışta arayüzle yarışıp frame atlamalarına (`Skipped 35 frames`) ve `DequeueBuffer` zaman aşımlarına yol açmaması için 12 saniye gecikmeli başlatıldı.

### 🎬 2. Oynatıcı, Altyazı ve PiP İyileştirmeleri
- **Dahili Türkçe Altyazı Seçim Döngüsü (`Media3PlayerEngine.kt`):** Harici altyazılar bağlanmadan önce ilk `onTracksChanged` olayında seçimin sonlanması engellendi. Hedef Türkçe parça tespit edilene kadar (maksimum 8 deneme) otomatik seçim politikası çalışır. Kullanıcının elle yaptığı altyazı seçimi korunur (`userSelectedTextTrack`).
- **Altyazı Taşma Koruması:** Dikey konum kaydırıldığında altyazının ekran dışına veya alt çubuğun altına kaçması engellendi; 4dp kenar payı ile görünür alana sabitlendi.
- **PiP Kapatıldığında Sesin Devam Etmesi Engellendi (`KitsugiFullscreenPlayerActivity.kt`):** Mini pencere kapatıldığında veya odak kaybolduğunda sesin arkadan çalmaya devam etmesi çözüldü; `invokePipAction` yedek mekanizmasıyla doğrudan ViewModel'e iletildi, `KeepAliveService.stop()` ve temiz Activity sonlandırması (`finish()`) sağlandı.
- **Kalıcı Tam Ekran (Immersive Mode):** MIUI ve HyperOS gibi arayüzlerde PiP veya odak değişimi sonrası alt gezinme çubuğunun ekranda asılı kalması giderildi (`onResume` ve `onWindowFocusChanged` üzerinde dinamik yenileme).

### 📑 3. Açılır Sayfalar (Sheet/Dialog) ve Çarpı Butonu
- **Kaydırma Kilidi Kaldırıldı (`KitsugiSheetOrDialog.kt`):** Açılır sayfalarda içerik aşağı kaydırıldığında kapatma hareketinin kilitlenmesi sorunu giderildi.
- **Evrensel Kapatma Çarpısı (`KitsugiSheetCloseButton`):** Tüm açılır sayfalara sağ üstte şık ve her koşulda çalışan kapatma çarpısı eklendi.

### 🌐 4. Bangumi Kapsamlı Türkçe Çeviri ve Metadata Zenginleştirmesi
- **95+ Tür ve Etiket Çevirisi:** Çince ve Japonca tür/etiketler (恋爱 → Romantik, 日常 → Günlük Yaşam, 京阿尼 → Kyoto Animation, 神作 → Başyapıt vb.) Türkçe'ye çevrildi (`strings.xml` ve `values-en/strings.xml`). Tür etiketine tıklanarak arama yapıldığında İngilizce API terimine dönüştürme eklendi.
- **Stüdyo Adları:** Çince/Japonca stüdyo adları Latin karşılıklarına dönüştürüldü (京都アニメーション → Kyoto Animation vb.).
- **AniList Kişi & Karakter Köprüsü (`KitsugiAniListPersonBridge`):** Bangumi'de kanji kalan karakter ve seslendirmen isimleri AniList köprüsü üzerinden otomatik olarak Romaji ve İngilizce isimlerle eşleştirildi.
- **Kadro Rolleri Çevirisi:** Episode Direction → Bölüm Yönetmeni, Animation Direction → Animasyon Yönetmeni, 監督 → Yönetmen vb. kadro rolleri eksiksiz Türkçeleştirildi.
- **İlişki ve Öneri Başlıkları:** Önerilen ve ilişkili yapımların Japonca başlıkları İngilizce/Romaji karşılıklarıyla zenginleştirildi.

### 📋 5. Çökme Raporu Dosya (.txt) Paylaşımı
- **FileProvider ile Doğrudan Dosya Paylaşımı (`KitsugiCrashRecoveryDialog.kt`):** Çökme ekranındaki "Paylaş" butonu artık düz metin yerine doğrudan `crash_log.txt` dosyasını paylaştırır.

### 📦 6. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.215-foss.apk`).

---

## 🇬🇧 English (v2.4.215)

### 🛡️ 1. Silent Crash Resolution (RenderThread SIGSEGV & WebView Teardown)
- **Resolved Double-Destroy Use-After-Free (`CsCfWarmupManager.kt`):** Fixed the root cause of `cr_AwContents: Application attempted to call on a destroyed WebView` warnings and subsequent `RenderThread` display-list recursive traversal crashes (`SIGSEGV` at `fault_addr=0x20`).
- **One-Shot Teardown Routine (`releaseWebView`):** Enforced atomic, single teardown guarded by `AtomicBoolean` ensuring WebView is unattached, halted, pointed to `about:blank`, and cleanly destroyed without race conditions.
- **Warmup Startup Throttle (`KitsugiApplication.kt`):** Deferred proactive Cloudflare warmup by 12 seconds to prevent resource contention during initial frame rendering, eliminating frame drops and buffer timeouts.

### 🎬 2. Player, Subtitle & PiP Architecture Fixes
- **Embedded Subtitle Selection Convergence (`Media3PlayerEngine.kt`):** Resolved race condition where initial `onTracksChanged` fired before sideloaded/embedded Turkish subtitle tracks arrived. Retries up to 8 times until preferred language is settled; preserves manual user track overrides (`userSelectedTextTrack`).
- **Subtitle Bounds Clamping:** Prevented vertical subtitle offset adjustments from translating text offscreen or underneath navigation chrome (clamped with 4dp margins).
- **PiP Background Audio Leak Elimination (`KitsugiFullscreenPlayerActivity.kt`):** Fixed issue where closing the mini-player left playback audio active. Replaced dangling Compose callbacks with `invokePipAction` ViewModel fallbacks, stopped `KeepAliveService`, and terminated ghost player activities.
- **Persistent Immersive Mode:** Re-applied system bar hiding on `onResume` and `onWindowFocusChanged` to fix persistent navigation bar overlays on MIUI/HyperOS devices.

### 📑 3. Sheets & Universal Close Button
- **Fixed Sheet Drag Lock (`KitsugiSheetOrDialog.kt`):** Removed bottom-sheet dismissal locking when scrolling content down.
- **Universal Close Button (`KitsugiSheetCloseButton`):** Added persistent top-right close buttons across all sheets and dialogs.

### 🌐 4. Bangumi Full Turkish Localization & Metadata Enrichment
- **95+ Tag & Genre Translations:** Localized Chinese/Japanese Bangumi genres and tags into Turkish and English with fallback dictionaries and search query translation.
- **Studio Name Latinization:** Converted Kanji studio names into recognized Latin branding (e.g. Kyoto Animation, Pony Canyon).
- **AniList Person & Character Bridge (`KitsugiAniListPersonBridge`):** Automatically matches and hydrates Kanji character and voice actor names with Romaji/English names via AniList.
- **Staff Role Translations:** Fully translated production credits (Episode Director, Animation Director, Script, etc.) into Turkish.
- **Relation & Recommendation Title Hydration:** Hydrates Japanese titles in related and recommended lists with English/Romaji metadata.

### 📋 5. Direct Crash Log File Sharing
- **Crash Log File Provider (`KitsugiCrashRecoveryDialog.kt`):** "Share" action on the crash recovery dialog now shares the actual `crash_log.txt` file via FileProvider instead of raw text.

### 📦 6. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.215-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.214)

### 🌟 1. Vitrin v2: Akıllı Seçim ve Skorlama Motoru (`HeroSelection.kt`)
- **Metrik Tabanlı Puanlama:** Vitrin (Hero Section) içerik seçimi rastgelelikten çıkarılıp ağırlıklı metrik algoritmasına geçirildi:
  $$\text{Skor} = (\text{Puan} \times 10) + (\log_{10}(\text{Üye} + 1) \times 8) + (\log_{10}(\text{Favori} + 1) \times 6) + \text{Rank} + \text{Trend (15)} + \text{Sezon (10)} + \text{Manga (8)}$$
- **Katalog ve Format Çeşitliliği:** Vitrinin tek bir türe veya kaynağa boğulması engellendi; en fazla 2 manga, en az 5 anime/dizi/film ve kaynak başına tavan sınırı (en fazla 3) ile dengeli bir vitrin akışı sağlandı.
- **Kaynaklar Arası Metrik Normalizasyonu:** TMDB gibi üye/favori bilgisi içermeyen kaynaklarda oy sayısı (`voteCount`) ve oy ortalaması logaritmik olarak dengelenerek haksız puan kaybı önlendi.
- **Görsel Kalite Filtresi:** Arka planı (backdrop) veya yüksek çözünürlüklü yatay görseli bulunmayan içerikler vitrin önceliğinde elenir.

### 🖼️ 2. Fit + Bulanık Dolgu Katmanlı Görsel Mimarisi (Kırpma Düzeltmesi)
- **Görsel Kırpma / Kesilme Sorunu Çözüldü (`KitsugiHeroSection.kt`):** Eski zorunlu kırpma (`ContentScale.Crop`) nedeniyle dikey afişlerin ve karakter yüzlerinin kesilme problemi tamamen ortadan kaldırıldı.
- **Çift Katmanlı Sunum:**
  - **Ön Plan:** Orijinal en-boy oranını tam koruyan (`ContentScale.Fit`) net ve keskin ana görsel.
  - **Arka Plan:** Ekranı boşluk bırakmadan dolduran, ortam rengini yansıtan bulanık dolgu katmanı (`HeroAmbientBackground` — Crop + Blur + Karartma).
- **Yumuşatılmış Gradyanlar:** Başlık ve meta metinlerinin okunabilirliğini artırırken görseli boğmayan yumuşak alt ve üst geçiş katmanları uygulandı.

### 🔄 3. TMDB Arka Plan Entegrasyonu ve Birleştirme (`ExploreViewModel.kt`)
- **Eksik Arka Planları Tamamlama (`enrichHeroBackdrops`):** MAL, Bangumi veya diğer kaynaklardan gelen popüler animelerde yatay backdrop bulunmadığında, TMDB üzerinden otomatik olarak yatay afiş temin edilir ve `heroBackdropOverrides` ile vitrinde kullanılır.
- **Tüm Bölümlerden Havuz Oluşturma (`ExploreScreen.kt` & `AllSourcesExplore.kt`):** Trend, Popüler, Sezonluk ve Manga listelerinin tamamından tekilleştirilmiş zengin havuz toplanarak yeni vitrin motoruna teslim edildi.

### 📊 4. Zengin Meta ve İstatistik Satırı (`HeroSectionComponents.kt`)
- **Kompakt Metrik Bilgileri:** Vitrin kartlarının altındaki meta satırına "1.2M üye • 45.3K favori" şeklinde biçimlendirilmiş topluluk metrikleri eklendi.

### 🧪 5. Testler ve Doğrulama (`HeroSelectionTest.kt`)
- Vitrin seçim ve skorlama motoru için 10 adet birim testi eklendi; kaynak çeşitliliği, skor hesaplamaları, TMDB normalizasyonu ve fallback senaryoları %100 başarıyla doğrulandı.

### 📦 6. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.214-foss.apk`).

---

## 🇬🇧 English (v2.4.214)

### 🌟 1. Vitrin / Hero Section v2: Intelligent Selection Engine (`HeroSelection.kt`)
- **Weighted Metric Scoring:** Vitrin items are now selected through a multi-factor ranking algorithm:
  $$\text{Score} = (\text{Score} \times 10) + (\log_{10}(\text{Members} + 1) \times 8) + (\log_{10}(\text{Favorites} + 1) \times 6) + \text{Rank} + \text{Trending Bonus (15)} + \text{Seasonal Bonus (10)} + \text{Manga Bonus (8)}$$
- **Format & Source Diversity:** Guaranteed balanced representation across the carousel: max 2 manga, min 5 anime/shows, and provider caps (max 3 per provider) preventing single-source domination.
- **Cross-Source Metric Normalization:** Sources lacking member/favorite counts (e.g. TMDB) are normalized using vote counts and ratings to maintain fair scoring.
- **Backdrop Quality Gate:** Automatically prioritizes titles with high-resolution backdrops or landscape banners over raw vertical posters.

### 🖼️ 2. Layered Fit + Ambient Blur Presentation (Cropping Fix)
- **Eliminated Poster Cropping (`KitsugiHeroSection.kt`):** Fixed the aggressive `ContentScale.Crop` behavior that cropped character heads and vertical poster artwork.
- **Dual-Layer Rendering:**
  - **Foreground:** Pristine uncropped asset rendered with `ContentScale.Fit`, preserving the original aspect ratio.
  - **Background:** Ambient blur fill (`HeroAmbientBackground` — Crop + Blur + Dim) that seamlessly expands to fill widescreen or ultra-wide viewport edges.
- **Refined Gradients:** Softened multi-stop linear gradients ensuring text readability without obscuring artwork.

### 🔄 3. TMDB Backdrop Enrichment (`ExploreViewModel.kt`)
- **Backdrop Fallback Hydration (`enrichHeroBackdrops`):** When popular entries from MAL or Bangumi lack native horizontal backdrops, the ViewModel dynamically queries TMDB for matching fanart/backdrops, merging them via `heroBackdropOverrides`.
- **Multi-Shelf Pool Aggregation (`ExploreScreen.kt` & `AllSourcesExplore.kt`):** Merged items across Trending, Popular, Seasonal, and Manga shelves into an enriched pool fed into the selection engine.

### 📊 4. Compact Community Statistics (`HeroSectionComponents.kt`)
- **Rich Meta Display:** Added formatted community engagement badges (e.g. "1.2M members • 45.3K favorites") to the hero meta bar.

### 🧪 5. Testing & Validation (`HeroSelectionTest.kt`)
- Added 10 unit tests covering selection diversity, score calculation, TMDB fallback normalization, and edge cases with 100% pass rate.

### 📦 6. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.214-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.213)

### 🔍 1. Arama Ekranı: Yükleme Animasyonu Tutarlılığı (Shimmer Fix)
- **Kopya Shimmer Kaldırıldı:** "Tümü" sekmesinde verisi gelmiş rafların üstüne binen 5 sabit shimmer rafı tamamen kaldırıldı. Artık raf başına tek görsel durum var: Verisi gelmeyen raf kendi shimmer'ını gösterir, verisi gelen raf anında karta dönüşür.
- **Titreme Giderildi:** Arama başladığında yükleme bayrakları kapsama göre anında yazılır; ara "boş + yüklenmiyor" karesi ve rafların kaybolup geri gelmesi (titreme) engellendi.
- **Sayfalama Shimmer Çakışması:** Diğer sekmelerde shimmer yalnızca henüz hiç sonuç yokken çizilir; sayfalama ve yenilemede mevcut sonuçların üstüne binmez.

### 🎯 2. Veri Doğruluğu, Kapsam Süzme ve Alakalılık (`SearchRelevance`)
- **Doğru Kapsam Planlaması (`planAllSources`):**
  - Manhwa / Manga / Manhua / Light Novel kapsamında TMDB ve Simkl'e istek atılmaz; çizgi roman aramalarında alakasız live-action filmler çıkmaz.
  - Diziler / Filmler / K-Drama aramaları anime veritabanlarına düşürülmez; yalnızca TMDB ve Simkl sorgulanır.
  - Karakter / Personel araması "Tümü" motorunda medya raflarını tetiklemez.
- **Türe Duyarlı Tekilleştirme:** Aynı ID'ye sahip anime ve manga kayıtlarının birbirini ezmesi (`kaynak_tur_kimlik`) önlendi; Manhwa ve Manga kayıtları anime altında kaybolmaz (dönüşümlü `interleave` birleştirme).
- **Yeni Alakalılık Motoru (`SearchRelevance`):** Sorguyla doğrudan eşleşen başlıklar en öne alınır, kayıt silinmez. Türkçe karakter ve aksan indirgeme ("İstanbul" ↔ "istanbul") ve yabancı takma ad koruması eklendi.
- **Jikan & AniList İyileştirmesi:** Arama sonuçlarını bozan zorunlu popülerlik sıralaması kaldırıldı, metin alakalılığı ön plana çıkarıldı.

### ⌨️ 3. Onaysız Arama Kaldırıldı (Onaylı & Butonlu Arama)
- **Yazarken Kendiliğinden Arama Engellendi:** Her tuş vuruşunda sorgu atan debounce kaldırıldı. Arama artık yalnızca klavyedeki "Ara" onay tuşuna veya arama butonuna basıldığında tetiklenir.
- **Arama Onay Butonu:** Arama çubuğuna metin yazıldığında görünen arama butonu eklendi.
- **Kompakt Motor Hapı:** Küçük ekranlarda metin alanının daralmaması için yazma esnasında motor seçici kompakt moda geçer.

### 📦 4. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.213-foss.apk`).

---

## 🇬🇧 English (v2.4.213)

### 🔍 1. Search Screen: Shelf Shimmer Consistency (Shimmer Fix)
- **Eliminated Duplicate Shimmers:** Removed the redundant 5-shelf shimmer overlay on the "All" tab. Each provider shelf now owns its visual state: displaying shimmer until its data arrives, then immediately transforming into content.
- **Flicker Elimination:** Loading flags are populated immediately upon search initiation, removing the intermediate "empty/idle" flicker frame.
- **Pagination Shimmer Overlap:** Shimmer placeholders in single-engine tabs only render when the result set is completely empty, preventing overlay during pagination.

### 🎯 2. Data Accuracy, Scope Routing & Relevance (`SearchRelevance`)
- **Scoped Provider Planning (`planAllSources`):**
  - Manga / Manhwa / Manhua / Light Novel scopes bypass TMDB and Simkl completely, eliminating live-action film bleed into print searches.
  - TV Shows / Movies / K-Drama queries route exclusively to TMDB and Simkl without querying anime databases.
  - Character / Staff scopes never trigger media shelves.
- **Type-Aware Deduplication:** Multi-engine keys now include media type (`source_type_id`), preventing legitimate manga records from being dropped when sharing an ID with an anime. Anime and manga are interleaved in mixed searches.
- **Search Relevance Engine (`SearchRelevance`):** Query matches are ranked to the top without pruning non-exact matches (preserving synonyms, romaji, and alternate titles). Added Turkish diacritic folding ("İstanbul" ↔ "istanbul").
- **Jikan & AniList Ranking Fix:** Stripped artificial popularity overrides on search queries in favor of pure text relevance.

### ⌨️ 3. Explicit Search Triggering (Debounce Removed)
- **No Keystroke Auto-Search:** Eliminated automatic debounced searching while typing. Searches only execute upon tapping the keyboard action button or the search icon.
- **Search Action Button:** Added an explicit search trigger button in the search bar when query text is present.
- **Compact Engine Selector:** Engine selector switches to compact mode while typing on small screens to preserve input field width.

### 📦 4. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.213-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.212)

### 📥 1. Android 10 (API 29) ve Üzeri İndirme / Kaydetme Düzeltmesi (ENOENT Fix)
- **Kök Neden Giderildi:** Galeri ve detay ekranlarından görsel indirirken Android 10 ve üzeri Scoped Storage kısıtlamaları nedeniyle meydana gelen `open failed: ENOENT (No such file or directory)` hatası çözüldü.
- **Doğru MediaStore Koleksiyonu:** `MediaStore.Images` yerine Android 10 standartlarına uygun olarak `MediaStore.Downloads.EXTERNAL_CONTENT_URI` ile `Download/Kitsugi/Images` yolu kullanıldı; alternatif olarak `Pictures/Kitsugi/Images` desteği eklendi.
- **Kademeli Güvenli Kayıt (Fallback):** Scoped Storage veya özel ROM kısıtlaması olan cihazlarda doğrudan dosya yazımı engellense dahi `getExternalFilesDir` yedekleme mekanizmasıyla görsellerin her koşulda sıfır hatayla kaydedilmesi sağlandı.
- **Depolama İzinleri & Bayraklar:** `AndroidManifest.xml` içerisine `requestLegacyExternalStorage="true"` eklendi ve `WRITE_EXTERNAL_STORAGE` izni Android 10 (`maxSdkVersion=29`) için de geçerli kılındı.
- **Galeri Otomatik Tarama:** İndirilen resimler `MediaScannerConnection` ile anında taranarak cihazın yerel Galeri ve Fotoğraflar uygulamalarında hemen görünür hale getirildi.

### 📦 2. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.212-foss.apk`).

---

## 🇬🇧 English (v2.4.212)

### 📥 1. Android 10+ (API 29) Image Download & Storage Fix (ENOENT Fix)
- **Root Cause Resolved:** Fixed `open failed: ENOENT (No such file or directory)` occurring when downloading gallery artwork on Android 10+ due to Scoped Storage directory restrictions.
- **Proper MediaStore Target:** Switched from `MediaStore.Images` (which rejects `DIRECTORY_DOWNLOADS` on Android 10) to `MediaStore.Downloads.EXTERNAL_CONTENT_URI` for `Download/Kitsugi/Images`, with `Pictures/Kitsugi/Images` fallback.
- **Graceful Multi-Tier Fallback:** Implemented seamless fallback to app-specific external storage (`getExternalFilesDir`) if public storage creation is restricted on customized OEM ROMs, guaranteeing zero download failures.
- **Manifest Storage Compatibility:** Added `requestLegacyExternalStorage="true"` and extended `WRITE_EXTERNAL_STORAGE` to `maxSdkVersion=29`.
- **Immediate Gallery Indexing:** Invoked `MediaScannerConnection` after write so downloaded media shows up immediately in system Gallery and Photos apps.

### 📦 2. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.212-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.211)

### 🎌 1. Kapsamlı Bangumi Başlık ve İsim Yerelleştirmesi (Bangumi Localization)
- **Merkezi İsim Yerelleştirici (`BangumiNameLocalizer`):** Bangumi'den gelen İngilizce (English), Romaji, Japonca/Özgün (Native), Çince ve alternatif adları (`alias`) koruyan merkezi yerelleştirme katmanı eklendi.
- **Kullanıcı Başlık Dili Tercihi:** Başlık dili tercihi (İngilizce / Romaji / Japonca-Özgün) Bangumi için de tam olarak uygulanır; eksik alanlarda güvenli geri dönüş zinciri çalışır.
- **Çince Önceliği Kaldırıldı:** Bölüm isimlerinde ve varsayılan konu başlıklarında Çince ismin zorunlu önceliği kaldırıldı; orijinal ve İngilizce başlıklar ön plana çıkarılır.
- **Bağımsız İngilizce İsim Desteği:** Karakter ve yapım ekibi modellerinde İngilizce ve Romaji adlar birbirinden ayrıldı.
- **Tüm Ekranlarda Aktif:** Arama, keşfet, medya detayları, karakterler, seslendirmenler, ekip, ilişkiler, öneriler, profil/favoriler, içe aktarma ve Android TV ekranlarının tamamında yerelleştirilmiş isimler uygulandı.

### 🎮 2. Oynatıcı Arayüzü, Tema Uyumu ve Media3 ↔ MPV Paritesi
- **Sabit Gösterge Konumları:** Parlaklık göstergesi ekranın sağında, ses göstergesi solunda sabitlendi (kaydırma jest bölgeleri kullanıcının tercihine göre çalışmaya devam eder).
- **Yenilenen Altyazı Stil Paneli:** Yatay modda ekran dışına taşma sorunu giderildi; panel ekran sınırlarına sabit, kaydırılabilir ve çentik/sistem çubuklarını (safe-area) dikkate alacak şekilde yeniden yapılandırıldı.
- **Uygulama Teması ve Vurgu Rengi (`PlayerAccentTheme`):** Oynatıcı artık seçili tema rengini ve AMOLED modunu alıyor; diyalog, panel, kaydırıcı, buton ve sekmelerdeki sabit renkler seçili tema vurgu rengine uyarlandı.
- **Dahili Oynatıcı Yedekleme Zinciri:** Ana oynatıcıda (Media3) oynatma hatası meydana geldiğinde doğrudan ikinci dahili motor olan MPV'ye geçilir (harici uygulamaya düşme kaldırıldı). Motor geçişinde video sıfırdan başlamaz, kalınan saniyeden devam eder.
- **Ortak Altyazı Öncelik Politikası (`SubtitleSelectionPolicy`):** Hem Media3 hem MPV için tek kural: Gömülü Türkçe > Harici Türkçe > Tercih edilen diğer diller. `tr-TR` ve `tr_TR` gibi bölgesel etiketler Türkçe olarak tanınır.
- **Altyazı ve Ses Düzeltmeleri:** Önbellekteki harici altyazıların bozuk `file://$sub.url` yolu düzeltildi; harici altyazılar benzersiz ID ile eşleştirilir. MPV motoruna ayrı ses akışı (`audioUrl`) harici parça olarak eklendi.

### 🌐 3. CloudStream & Akış İyileştirmeleri
- `domain_fixes.json` blocked listesi temizlendi; Türk eklentileri üzerindeki sessiz atlama kapıları kaldırılarak gerçek hata durumu kartlarda gösterildi.

### 📦 4. Dağıtım
- Kullanıcı talimatı doğrultusunda **yalnızca FOSS varyantı** (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.211-foss.apk`). GMS kesinlikle hariç tutuldu.

---

## 🇬🇧 English (v2.4.211)

### 🎌 1. Comprehensive Bangumi Title and Name Localization
- **Central Name Localizer (`BangumiNameLocalizer`):** Dedicated localization engine preserving Bangumi English, Romaji, native/Japanese, Chinese, and alias records.
- **User Language Preference:** The global title language preference (English, Romaji, Native/Japanese) is now fully applied across all Bangumi surfaces with graceful fallback logic.
- **Removed Chinese Default Bias:** Forced Chinese title precedence on episode titles and main subjects has been removed in favor of user preferences.
- **Distinct English Names:** Added dedicated English naming fields for character and staff models so English and Romaji are no longer conflated.
- **Omnipresent UI Support:** Applied across search, explore, media details, characters, voice actors, staff credits, relations, recommendations, profiles/favorites, imports, and Android TV screens.

### 🎮 2. Player UI, Theme Accent & Media3 ↔ MPV Parity
- **Fixed Indicator Placement:** Brightness indicator is fixed on the right screen edge, volume indicator on the left (swipe gesture regions remain configurable).
- **Redesigned Subtitle Style Panel:** Eliminated horizontal overflow in landscape mode; panel is now bounded, scrollable, and aware of notches and system bars.
- **Dynamic Theme Accent (`PlayerAccentTheme`):** Player now inherits the active app theme color and AMOLED settings; hardcoded colors in sheets, dialogs, sliders, and buttons replaced with the chosen accent color.
- **Internal Engine Fallback Chain:** Playback failures on the primary engine (Media3) automatically switch to the secondary internal engine (MPV) rather than dropping to an external player. Playback resumes from the exact failure position.
- **Shared Subtitle Selection Policy (`SubtitleSelectionPolicy`):** Unified policy across both Media3 and MPV: Embedded Turkish > External Turkish > Other preferred languages. Regional language tags (e.g. `tr-TR`, `tr_TR`) properly recognized.
- **Track & Path Fixes:** Resolved broken `file://$sub.url` paths for cached external subtitles; external tracks identified by unique IDs. Added separate audio stream (`audioUrl`) loading support to MPV.

### 🌐 3. CloudStream & Streaming Refinements
- Cleared broken provider blocklists in `domain_fixes.json`, removed silent skip gates, and surfaced diagnostic error messages.

### 📦 4. Distribution
- Strictly built and released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.211-foss.apk`). GMS is completely excluded.

---

## 🇹🇷 Türkçe (v2.4.210)

### 🎌 1. Bangumi Profil Sekmesi ve Koleksiyon Yönetimi
- **Profil Seçicisine Eklendi:** Profil kaynağı seçicisine (Profile Source Picker) "Bangumi" seçeneği eklendi. Hesap bağlıysa `@kullanıcıadı` görüntülenir.
- **Kapsamlı Profil Başlığı:** Kullanıcı avatarı, takma adı, kullanıcı adı, kişisel imzası (`sign`) ve tek tıkla açılan "bgm.tv profili" butonu.
- **Koleksiyon Listesi ve Filtreler:** Tür (Anime, Kitap/Manga, Oyun, Müzik, Dizi/Film) ve izleme durumu filtreleri, özet koleksiyon sayaçları, bölüm/cilt ilerleme göstergesi ve kullanıcı puanı.
- **Favori Karakterler ve Kişiler:** Kullanıcının Bangumi'de favorilediği karakterler ve kişiler profil ekranında listelenir.
- **Doğrudan Detay Ekranı:** Anime ve manga koleksiyon kartlarına dokunulduğunda doğrudan uygulama içi detay ekranı açılır.
- **Bildirimler:** Resmi Bangumi API'sinde bildirim ucu bulunmadığı için uygulama içi WebView ile doğrudan ve güvenli `bgm.tv/notify/all` sayfası açılır.

### 👤 2. Uygulama İçi Karakter ve Kişi Detayları & Favori Eşitleme
- **Uygulama İçi Detay Sayfaları:** Bangumi karakter ve kişi (yapım ekibi/seiyuu) sayfaları artık tarayıcıya yönlendirilmeden doğrudan uygulama içinde açılır (`KitsugiBangumiCreditsClient` ve `KitsugiBangumiDetailClient`).
- **Bangumi Favori (Kalp) Entegrasyonu:** Bangumi hesabı bağlıyken karakter veya kişi sayfasındaki favori durumu canlı olarak Bangumi sunucusuyla eşitlenir; ekleme ve çıkarma anında yazılır, ağ hatasında güvenli bir şekilde eski haline döner.
- **Dolu Karakter ve Ekip Sekmeleri:** Bangumi konularında "Karakterler" ve "Ekip" sekmeleri v0 OpenAPI ve p1 web uçlarıyla eksiksiz doldurulur.
- **Doğru Paylaşım Bağlantıları:** Karakter ve kişi paylaş butonları artık `bgm.tv/character/{id}` ve `bgm.tv/person/{id}` bağlantılarını üretir.

### 🎮 3. Oynatıcı Jest Bölgeleri, PiP Butonları ve Ses Yönetimi
- **Kaydırıcı ve Dokunma Hizalaması:** Ekrandaki kaydırıcı yerleşimiyle dokunma bölgeleri eşitlendi: Sol yarı parlaklık, sağ yarı ses (varsayılan). Ayar menüsündeki yön metinleri düzeltildi.
- **Çalışan PiP Tuşları:** Mini oynatıcı (PiP) penceresindeki Oynat, Duraklat ve Sonraki Bölüm tuşları Compose oynatıcı köprüsüne (`PipPlayerCallback`) bağlandı.
- **Arka Plan Ses Kesilmesi:** PiP kapatıldığında veya uygulama arka plana alındığında sesin çalmaya devam etmesi `onStop` ve `onDestroy` yaşam döngüsü kontrolleriyle engellendi.

### 🌐 4. CloudStream Kaynak Akışı ve Domain İyileştirmeleri
- **Sessiz Atlama Kapıları Kaldırıldı:** `KNOWN_BROKEN_DOMAINS` ve `domain_fixes.json` blocked listesi eşleştiğinde eklentiler artık peşinen atlanıp sıfır sonuca düşürülmüyor; her zaman denenir ve gerçek hata (DNS/Cloudflare/timeout vb.) UI kartında kullanıcıya bildirilir (`CsPluginStatusTracker.recordSkip`).
- **Domain Listesi Çelişkileri Çözüldü:** `4kfilmizlesene.nl` ve `666filmizle.site` ölü domainler listesinden çıkarıldı.
- **Temizlenen Blocked Listesi:** `domain_fixes.json` dosyasındaki gereksiz yere TR eklentilerini öldüren blocked listesi boşaltıldı (+18 filtreleme uygulamada `ADULT_PLUGINS` ile ayrı olarak yönetilir).
- **Gerçek Hata Gösterimi:** Boş akış döndüğünde "Bu anime için akış bulunamadı" yerine `CsPluginStatusTracker`'dan gelen gerçek sebep gösterilir.
- **Taze Domain Önceliği:** Dinamik güncel domain tablosundan gelen taze domainler, sabit `KNOWN_BROKEN_DOMAINS` listesi tarafından engellenmez.
- **Revert Kararlılığı:** Özgün domaine dönüldükten sonra aynı oturumda yerleşik tablonun tekrar zorlanması engellendi.
- **Güncelleme Geri Çekilme (Backoff):** 404 veren eklentiler için 12 saatlik bekleme süresi uygulandı (`CsAutoUpdateBackoff`).

### 🛠️ 5. Gelişmiş Çökme Teşhisi ve Oturum Denetimi (Crash Diagnostics Overhaul)
- **Native İz Genişletmesi:** Native çökme yığın izi sınırı 48'den 96 çerçeveye çıkarıldı; PC (Program Counter) ve LR (Link Register) bilgileri eklendi.
- **Bellek Haritası Desteği (`/proc/self/maps`):** Çökme anında bellek haritası kaydedilerek native adreslerin ilgili `.so` dosya adına ve ofsetine çözülebilmesi sağlandı.
- **Doğru Süreç ve Ekran Tespiti:** Teşhis raporundaki "Ölüm Sebebi" artık sadece çöken oturumun PID'sine odaklanır; "Son Ekran" bilgisi eylem izinden doğrulanarak gösterilir.
- **Genişletilmiş Kanıt Penceresi:** Sessiz kapanmalarda native iz okuma penceresi 30 dakikadan 7 güne çıkarıldı.

### 📦 6. Dağıtım
- Kullanıcı talimatı doğrultusunda **yalnızca FOSS varyantı** (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.210-foss.apk`). GMS kesinlikle hariç tutuldu.

---

## 🇬🇧 English (v2.4.210)

### 🎌 1. Bangumi Profile Tab & Collection Management
- **Added to Profile Picker:** Bangumi is now available in the profile source selector, displaying `@username` when connected.
- **Full Profile Header:** Displays user avatar, nickname, username, personal signature (`sign`), and a direct link to open the bgm.tv web profile.
- **Collections & Filters:** Category (Anime, Manga/Book, Game, Music, Real/Drama) and status filters, collection counters, episode/volume progress, and user ratings.
- **Favorite Characters & People:** Highlights the user's favorited Bangumi characters and staff members directly on the profile screen.
- **In-App Navigation:** Tapping anime or manga entries seamlessly opens the existing in-app media detail page.
- **Notifications:** Since Bangumi does not provide a public notification API endpoint, notifications open via an in-app WebView targeting `bgm.tv/notify/all`.

### 👤 2. In-App Character & Staff Detail Pages & Bangumi Favorites
- **Native In-App Views:** Bangumi character and person/staff pages now render natively in-app (`KitsugiBangumiCreditsClient` & `KitsugiBangumiDetailClient`) rather than opening externally.
- **Live Favorite Toggle:** When logged in with Bangumi, tapping the favorite heart on character/person screens syncs directly to Bangumi with rollback on error.
- **Populated Credits Tabs:** Subjects on Bangumi now populate both "Characters" and "Staff" tabs via official v0 OpenAPI with p1 fallbacks.
- **Accurate Share URLs:** Sharing characters or staff members now correctly generates `bgm.tv/character/{id}` and `bgm.tv/person/{id}` links.

### 🎮 3. Player Gesture Zones, PiP Controls & Audio Lifecycle
- **Slider & Touch Zone Alignment:** Touch regions now match on-screen sliders: Left half controls brightness, right half controls volume (default). Settings labels aligned.
- **Functional PiP Actions:** Mini player (PiP) Play, Pause, and Skip Next buttons are wired to the player engine via `PipPlayerCallback`.
- **Background Audio Stop:** Playback reliably pauses upon closing PiP or backgrounding the activity via `onStop` and `onDestroy`.

### 🌐 4. CloudStream Stream & Domain Enhancements
- **Eliminated Silent Skip Gates:** Extensions matching `KNOWN_BROKEN_DOMAINS` or `domain_fixes.json` blocked list are no longer prematurely skipped; all extensions are executed and real errors (DNS, Cloudflare, timeouts) are surfaced directly on stream cards (`CsPluginStatusTracker.recordSkip`).
- **Domain Contradictions Resolved:** `4kfilmizlesene.nl` and `666filmizle.site` removed from broken domain blocklist.
- **Cleared Blocked List:** Cleared `domain_fixes.json` blocked list that previously incapacitated legitimate Turkish streaming providers (+18 filtering is handled strictly via `ADULT_PLUGINS`).
- **Real Diagnostic Error Display:** When stream discovery yields empty results, the actual root cause from `CsPluginStatusTracker` is displayed instead of a generic "No streams found" placeholder.
- **Fresh Domain Priority:** Fresh remote domains override the hardcoded `KNOWN_BROKEN_DOMAINS` blocklist.
- **Stable Revert Protection:** Restored domains are protected against immediate builtin overrides in the same session.
- **Update Backoff:** 12-hour retry backoff on 404/missing extensions (`CsAutoUpdateBackoff`).

### 🛠️ 5. Enhanced Crash Diagnostics & Session Supervisor
- **Extended Native Trace:** Increased native stack frames limit from 48 to 96, capturing PC and LR registers.
- **Memory Maps Dump (`/proc/self/maps`):** Recorded address maps allow translating raw crash addresses to `.so` library names and offsets.
- **Precise PID & Last Screen Tracking:** Root cause attribution strictly targets the crashing PID session; the last visited screen is inferred from the action breadcrumbs.
- **Extended Evidence Window:** Silent crash native trace retention window prolonged to 7 days.

### 📦 6. Distribution
- Strictly built and released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.210-foss.apk`). GMS is completely excluded.

---

## 🇹🇷 Türkçe (v2.4.209)

### 🎌 1. Bangumi Liste İçe Aktarma ve Eşitleme 404 Kök Neden Çözümü
- **Kullanıcı Adı Çözümlemesi:** Bangumi sunucusunda `"-"` takma adının yalnızca yazma uçlarında geçerli olması ve okuma ucunda (`GET /v0/users/-/collections`) 404 vermesi sorunu çözüldü. Artık saklanan kullanıcı adı, yoksa `/v0/me`, o da yoksa sayısal ID dinamik olarak çözümlenerek gerçek kullanıcı adına istek atılıyor.
- **Koleksiyon Uçları Güvenliği:** `getUserCollections`, `getAllUserCollections` ve `getUserCollection` fonksiyonlarındaki hatalı `"-"` varsayılan değeri kaldırılarak yanlışlıkla 404 hatasına düşülmesi engellendi.
- **Durum Kontrolü:** `BangumiSyncManager.fetchRemoteStatus` içerisindeki aynı hata giderilerek uzaktaki izleme/okuma durumu güvenle alınır hale getirildi.

### 🔍 2. Bangumi Arama Motoru İyileştirmeleri ve Yedek Uç
- **Yetişkin İçerik (NSFW) Filtresi:** Yetişkin içerik açıkken sunucuya metin (`"include"`) olarak gidip 400 hatası oluşturan `filter.nsfw` parametresi, sunucunun beklediği JSON boolean formatına dönüştürüldü (`false` veya filtresiz).
- **Sayfa Boyutu ve Atlama Düzeltmesi:** Sunucu limiti 20 olduğu halde istemcinin 24 kayıt istemesi ve offset hesaplamasında her sayfada 4 kaydın atlanması sorunu giderildi; tüm Bangumi aramalarında sayfa boyutu 20'ye sabitlendi.
- **Eski Uç Yedeği (Legacy Search Fallback):** v0 deneysel arama ucu hata verirse veya boş dönerse `GET /search/subject/{q}` eski arama ucu otomatik devreye girer.
- **Şeffaf Hata Bildirimi:** Arama hataları artık "Sonuç bulunamadı" ardına gizlenmez; sunucudan dönen gerçek hata mesajı ekranda gösterilir ("Tümü" karma rafında da anime ve manga hataları yüzeye çıkarılır).

### 🎬 3. Kapsamlı Bangumi Detay Sayfası ve Çapraz Kimlik Eşleştirme (`KitsugiBangumiDetailClient`)
- **Doğrudan Yerel Veri Doldurma:** Bilgi, karakterler (seslendirmenli), yapım ekibi, ilişkiler, öneriler, puan dağılım grafikleri, incelemeler/yorumlar ve bölümler doğrudan Bangumi'nin kendi verisinden (v0 ve p1 uçları) anında doldurulur.
- **Karakter ve Kişi Detay Sayfaları:** Bangumi kaynaklı karakter ve yapım ekibi detay sayfaları p1 uçları üzerinden eksiksiz çalışır hale getirildi.
- **Akıllı Çapraz Kimlik Eşleme:** Bangumi kaydı başlık, yıl, bölüm sayısı ve format puanlamasıyla AniList, MAL ve ARM üzerinden TMDB/Kitsu/TVDB/IMDb ile güvenli şekilde eşleştirilir.
- **Zenginleştirilmiş Galeri:** Resimler sekmesine Fanart.tv, TMDB, Shikimori, MAL ve AniList görselleri dahil edildi. `lain.bgm.tv` kaynaklı aynı görselin farklı boyut kopyaları kanonik anahtar ile tekil görsele indirildi.
- **Room Önbellek Sürümlemesi:** Önceki sürümlerin eksik/hatalı Kitsu aramasıyla önbelleğe aldığı satırların okunmasını engellemek için önbellek anahtarına `_bgm1` sürüm eki getirildi.
- **Bölüm Puanları ve Logolar:** MDBList, logo ve bölüm puanları eşleşen çapraz kimlikler üzerinden kusursuz çalışır.

### 📦 4. Dağıtım
- Kullanıcı talimatı doğrultusunda **yalnızca `foss` varyantı** derlendi ve GitHub'a yüklendi (`Kitsugi-Beta-v2.4.209-foss.apk`). GMS kesinlikle hariç tutuldu.

---

## 🇬🇧 English (v2.4.209)

### 🎌 1. Bangumi Collection Import & Sync 404 Root Cause Fix
- **Dynamic Username Resolution:** Resolved the issue where Bangumi rejected the `"-"` alias on read endpoints (`GET /v0/users/-/collections`) with 404 Not Found. Real username is now dynamically determined via stored name, `/v0/me`, or numeric ID.
- **Safe Collection Endpoints:** Removed the fallback `"-"` default in `getUserCollections`, `getAllUserCollections`, and `getUserCollection` to prevent accidental regressions.
- **Sync Status Audit:** Fixed identical issue in `BangumiSyncManager.fetchRemoteStatus` so remote progress sync works reliably.

### 🔍 2. Bangumi Search Improvements & Legacy Fallback
- **NSFW Boolean Filtering:** Fixed `filter.nsfw` sending string `"include"` which caused HTTP 400 Bad Request; now correctly sends JSON boolean or omits when inactive.
- **Page Size Alignment:** Standardized page size and pagination offsets to 20 to match Bangumi's hard limit, preventing 4-item skipping and false "no more pages" triggers.
- **Legacy Fallback Endpoint:** If experimental v0 search fails or returns empty, fallback to legacy `GET /search/subject/{q}` seamlessly engages.
- **Surfaced Error Messages:** API errors are no longer silently masked as "No results found"; descriptive server messages are displayed across both isolated and mixed search views.

### 🎬 3. Full Native Bangumi Media Details & Cross-ID Enrichment (`KitsugiBangumiDetailClient`)
- **Native Details & Tabs:** Information, characters with voice actors, staff, relations, recommendations, score distributions, reviews/comments, and episodes load immediately using native Bangumi APIs (v0 and p1).
- **Character & Staff Pages:** Native Bangumi character and person profile pages are fully supported via p1 endpoints.
- **Cross-ID Resolution:** Bangumi subjects are matched with AniList, MAL, and ARM (TMDB/Kitsu/TVDB/IMDb) using strict title, year, episode count, and format scoring.
- **Enhanced Gallery & Dedup:** Gallery tabs are enriched with fanart.tv, TMDB, Shikimori, MAL, and AniList images. Duplicate covers from `lain.bgm.tv` across various size buckets are deduplicated into single items.
- **Room Cache Busting:** Added `_bgm1` suffix to Bangumi detail cache keys, preventing stale/erroneous legacy Kitsu cache entries from loading.
- **Episode Ratings & Logos:** MDBList, clearlogos, and episode rating curves now correctly resolve using the linked cross-IDs.

### 📦 4. Distribution
- Built and published strictly as a **FOSS release** (`Kitsugi-Beta-v2.4.209-foss.apk`). GMS flavor is omitted.

---

## 🇹🇷 Türkçe (v2.4.208)

### 🏷️ 1. AniList İsim Dili (Romaji / İngilizce / Japonca) Yerelleştirmesi
- **Karakter, Seslendirmen & Yapım Ekibi (Staff) İsimleri:** AniList'ten gelen kişi ve karakter adları artık kullanıcının tercih ettiği isim diline (`ROMAJI`, `ENGLISH`, `NATIVE`, `JAPANESE_STAFF`) göre dinamik olarak gösteriliyor.
- **Latin & Orijinal Ad Uyumu (`AniListPersonName.kt`):** AniList sorgularında `userPreferred` yerine `full`, `native` ve alternatif adlar çekilerek Romaji/İngilizce tercihinde Latin harfli isimler önceliklendirildi. Latin harfli karşılık bulunamadığında orijinal ad korunur.
- **Temiz Başlık & Alt Başlık:** Japonca/Yerel ad seçildiğinde alt başlıktaki mükerrer isimler temizlenir ("Karakter" veya doğrudan rol açıklaması gösterilir).
- **Kapsam:** Arama sonuçları, yapım detay sekmeleri (`CharactersTab`), API detay sayfası, yerel kütüphane detay sayfası, karakter detayı ve yapım ekibi detay sayfalarının tümü güncellendi.

### 📜 2. Akıllı Kaydırma Konumu Koruma (Scroll Retention)
- **Detaydan Geri Dönüş Güvencesi:** Bir listenin ortasından detay sayfasına girilip geri dönüldüğünde ekranın başa sarması ve kaydırma konumunun sıfırlanması tamamen önlendi.
- **Listem (My List):** 7 kütüphane sekmesinin (Tümü, İzlediklerim, İzleyeceklerim, Bıraktıklarım vb. ve 🎌 Bangumi) her biri için kaydırma konumu ekran kapansa dahi bağımsız saklanır (`savedTabIndices`, `savedTabOffsets`, `restoredTabs`).
- **Arama & Çoklu Platform Rafları:** Arama sayfası ve kaynağa özel arama (`SourceSearchPage`) için `rememberRetainedLazyListState` entegre edildi. Sonuçlar henüz yüklenirken veya yenilenirken listenin 0'a sıfırlanması engellendi. `SourceSearchOwner` ile izole ViewModel durumu geri dönüşte korunur.
- **Keşfet & Tam Ekran Izgaralar:** `FullScreenMediaGridPage` ve `AddonFullScreenGridPage` `SaveableStateProvider` ve derinlik takibi (`openingStackDepth`) ile korunarak detaydan dönüşte tam kaldığı noktaya döner.

### ✨ 3. Kartlarda Neon Degrade Çerçeve ve Parıltı (List Grid Glow)
- **Kare Izgara Kartları (`KitsugiMediaEntryCard`):** Kare poster görünümündeki kartlara zarif degrade çerçeve ve neon parıltı efekti (`kitsugiNeonGlow`) uygulandı. Listem sekmelerine ve Favorilerim ekranına modern, canlı bir görünüm kazandırıldı.
- **Kullanıcı Listeleri (`UserMediaListCards`):** Kaynak kullanıcı profil listelerindeki hem kare (grid) hem yatay satır (row) kartlarına degrade çerçeve ve parıltı stili dahil edildi.

### 🛠️ 4. MyAnimeList / Jikan Detay Sekmeleri Kök Neden Çözümü (`MalJikanMediaSupport`)
- **Sekme Donması ve Boş Kalma Çözümü:** MAL kaynaklı detay sayfalarında Karakterler, Ekip, Öneriler, İlişkiler, İstatistikler ve Yorumlar sekmelerindeki iskelet takılması ve boş kalma sorunu giderildi.
- **Tek Ortak Kural:** `mal`, `jikan`, `myanimelist` kimlik uzayları `MalJikanMediaSupport.canonicalSource` altında birleştirildi; `Movie` ve `TvShow` tiplerinin Jikan uç noktalarında (`anime`) veya AniList tipinde (`ANIME`) yanlışlıkla `MANGA`ya düşmesi engellendi.
- **Akıllı Hiyerarşi:** Jikan birincil kaynak yapıldı; AniList'ten toplu zenginleştirme için 25 saniye bekleme kaldırıldı (veriler anında ekrana gelir). Jikan boş döndüğünde ise karmaşık ARM araması öncesi doğrudan aynı MAL ID'si ile AniList denenerek anında sonuç üretilir.

### 🎬 5. Çökme ve Kurtarma Pencerelerinde Canlı Animasyon (`KitsugiCrashAnimation`)
- **Canlı Çökme Görseli:** Çökme ekranı (`KitsugiCrashActivity`) ve başlangıç kurtarma penceresinde (`KitsugiCrashRecoveryDialog`) statik hata ikonu yerine `res/raw/crash_animation.mp4` döngüsel animasyonu eklendi.
- **Sıfır Ek Yük ve Güvenlik:** Android'in yerel `TextureView` ve `MediaPlayer` altyapısı kullanılarak sessiz ve kesintisiz döngü sağlandı; donanım düzeyinde hata koruması sayesinde çökme ekranının kendisinin hata vermesi tamamen engellendi.

### 📦 6. Dağıtım
- Kullanıcı talimatı doğrultusunda **yalnızca `foss` varyantı** derlendi ve GitHub'a yüklendi (`Kitsugi-Beta-v2.4.208-foss.apk`). GMS kesinlikle hariç tutuldu.

---

## 🇬🇧 English (v2.4.208)

### 🏷️ 1. AniList Person Name Localization (Romaji / English / Native)
- **Characters, Voice Actors & Staff:** AniList person and character names now dynamically conform to the user's selected name/title language preference (`ROMAJI`, `ENGLISH`, `NATIVE`, `JAPANESE_STAFF`).
- **Latin & Native Fallback (`AniListPersonName.kt`):** Queries now request `full`, `native`, and alternate names instead of solely relying on `userPreferred`. When Romaji or English is selected, Latinized names take precedence; if no Latin name exists, the original name is safely preserved.
- **Clean Subtitles:** Redundant name prefixes in subtitles when native names are active are cleanly stripped (displaying "Character" or role details).
- **Universal Coverage:** Applied across Search results, Media Detail tabs (`CharactersTab`), API and Local library detail pages, Character Detail, and Staff Detail pages.

### 📜 2. Smart Scroll Retention Across All Lists
- **Seamless Navigation Return:** Returning from a detail screen to any list or search view now accurately preserves scroll position instead of snapping to top.
- **My List Tabs:** Each of the 7 library tabs (including 🎌 Bangumi) retains its scroll index and pixel offset independently across tab switching and detail navigation (`savedTabIndices`, `savedTabOffsets`, `restoredTabs`).
- **Search & Source Search:** Integrated `rememberRetainedLazyListState` to prevent intermediate empty/loading states from clamping scroll offsets to 0. `SourceSearchPage` leverages `SourceSearchOwner` to keep isolated ViewModel state alive during nested navigation.
- **Explore & Full-Screen Grids:** Wrapped `FullScreenMediaGridPage` and `AddonFullScreenGridPage` in `SaveableStateProvider` with depth tracking (`openingStackDepth`) for accurate back-stack restoration.

### ✨ 3. Neon Gradient Border & Glow on Media Cards
- **Grid Cards (`KitsugiMediaEntryCard`):** Added subtle neon glow and gradient border accents (`kitsugiNeonGlow`) to poster grid media cards across My List and Favorites.
- **User Profile Lists (`UserMediaListCards`):** Extended neon gradient styling to both grid and row cards within remote user profile lists.

### 🛠️ 4. MyAnimeList / Jikan Detail Tabs Unification (`MalJikanMediaSupport`)
- **Stall & Empty Tab Resolution:** Fixed root causes where Characters, Staff, Recommendations, Relations, Stats, and Reviews tabs on MAL-sourced items remained stuck on skeletons or empty.
- **Unified Identity Space:** Normalized `mal`, `jikan`, and `myanimelist` under `MalJikanMediaSupport.canonicalSource`. Corrected `Movie` and `TvShow` mapping to prevent accidental `MANGA` endpoints.
- **Instant Response Flow:** Made Jikan the primary responsive source without waiting 25s for AniList batch cover enrichment. If Jikan is empty, AniList is queried with the exact MAL ID before escalating to ARM/Shikimori resolvers.

### 🎬 5. Smooth Looping Animation for Crash & Recovery Screens (`KitsugiCrashAnimation`)
- **Dynamic Error Graphic:** Replaced static bug icons in `KitsugiCrashActivity` and `KitsugiCrashRecoveryDialog` with a sleek looping video animation (`res/raw/crash_animation.mp4`).
- **Zero Overhead & Robust Fallbacks:** Built on Android's native `TextureView` and `MediaPlayer` for seamless looping, zero audio intrusion, and complete crash immunity.

### 📦 6. Distribution
- Per user specification, **only the `foss` release APK** was built and released (`Kitsugi-Beta-v2.4.208-foss.apk`). GMS is strictly excluded.

---

## 🇹🇷 Türkçe (v2.4.207)

### 🎌 1. Bangumi (bgm.tv) Tam Platform Entegrasyonu (7. Kaynak)
- **OAuth 2.0 & Kimlik Doğrulama:** `kitsugi://bangumi-auth` ve `aniyomi://bangumi-auth` yönlendirme şemalarıyla modern yetkilendirme altyapısı kuruldu. `BangumiAuthStore` ve `BangumiAuthManager` ile "1-Tık Otomatik Giriş" ve manuel yetki kodu girme seçenekleri sağlandı.
- **Kütüphane & İçe Aktarma:** Bangumi koleksiyon durumları (想看/在看/看过/搁置/抛弃 - İzlemek İstiyorum / İzliyorum / İzledim / Beklemede / Bıraktım), 0-10 puanlama sistemi ve bölüm bazlı ilerleme (打格子) Kitsugi kütüphanesine içe aktarma (`BangumiImportManager`) ve iki yönlü senkronizasyon (`BangumiSyncManager`) ile tam entegre edildi.
- **Listem Sekmesi (7. Sekme):** Listem ekranına 7. sekme olarak 🎌 Bangumi kütüphanesi eklendi (`MyListLibraryGrouping`, `MyListScreen`, `MyListComponents`, `MyListEmptyState`). Özel marka rengi (`#F09199`), rozetler, sayaçlar ve doğrudan sekme içinden açılan hesap bağlama diyaloğu eklendi.
- **Keşfet & Arama Motoru:** Keşfet ekranında 7. platform olarak Bangumi desteği (`AllSourcesExplore`, `ExplorePlatformToggle`, `ExploreViewModel`), Arama motorlarında Bangumi motoru (`KitsugiBangumiClient`, `SearchViewModel`), anime/manga gelişmiş filtreleme, sıralama, karakter ve personel arama kabiliyeti kazandırıldı.
- **Görsel Rozet & Marka:** Gerçek Bangumi vektör logosu (`ic_logo_bangumi.xml`), Hero rozetleri, detay sayfası başlıkları ve galeri rozetleri güncellendi.

### ⚠️ 2. Çapraz Eşitleme (Cross-Sync) Güvenlik & Hukuki Sorumluluk Reddi (Disclaimer)
- **Merkezi Sorumluluk Reddi (`CrossSyncDisclaimer.kt`):** Çapraz eşitlemenin deneysel (beta) bir özellik olduğunu, eşleştirmelerin otomatik yapıldığını ve hiçbir eşleştirmenin %100 doğruluk garantisi taşımadığını; yanlış eşleşme, liste karışması veya platform yaptırımlarından (kısıtlama, askıya alma, hesap yasağı vb.) sorumluluk kabul edilmediğini belirten açık ve kapsamlı uyarı metinleri eklendi.
- **Görünür Uyarılar:**
  - Hesap bağlantıları Cross-Sync satırına "Deneysel ·" etiketi ve kısa uyarı eklendi.
  - Çok Yönlü Eşitleme ve Cross-Sync ayar sayfasına bilgi kartı içine ayrıntılı uyarı, Hızlı İşlem bölümü altına kısa not eklendi.
  - Eşitleme penceresinde (`KitsugiCrossSyncDialog`) güvenlik notunun altına ayrıntılı uyarı yerleştirildi.
- **Çoklu Aday Grup Akrabalık Koruması (`AuthViewModel`):** Birden fazla aday grup bulunduğunda, tekil ortak kimlikle seçilen grup için yıl uyumu (`CrossSyncIdentityGuard.yearsCompatible`) ve başlık akrabalığı kontrolü eklendi; uyuşmayan kayıtların yanlış birleştirilmesi ve başka hesaplardaki doğru kayıtların bozulması engellendi (`identityReviewRequired = true`).
- **Rapor Teşhis Etiketleri:** Eşitleme teşhisinde iç kimlik alanları (`300M+` Kitsu, `100M+` AniList) açıkça etiketlenerek (`describeMalIdField`) MAL kimliği sanılması önlendi.

### ⚡ 3. Jikan Hız Limiti, Shikimori Başlık Dili & Kitsu Paralel İstek İyileştirmeleri
- **Jikan Hız Limiti & 429 Koruması (`KitsugiApiBase`):** Jikan için saniyede 3 ve dakikada 55 istek (kayan pencere) limiti getirildi. 429 yanıtı alındığında host geçici olarak bekletilir ve yeniden deneme sayısı 3'e çıkarılarak sekme/karakter boş kalma sorunları giderildi.
- **Shikimori Rusça Başlık Çözümü (`ShikimoriTitleResolver`):** Shikimori GraphQL API üzerinden tek istekte İngilizce ve Japonca başlıklar çekilerek, kullanıcının ayarladığı tercih diline (Romaji / İngilizce / Japonca) göre gösterilmesi sağlandı.
- **İlişki Tipi Çevirileri (`KitsugiTranslations`):** Shikimori ve Jikan ilişkilerindeki "Sequel", "Side story", "Adaptation", "Prequel" gibi ilişki türleri Türkçeye çevrildi. Jikan ilişkilerine AniList'ten İngilizce/Japonca başlık desteği eklendi.
- **Kitsu Karakter & Seslendirmen Paralel İstekleri (`KitsuClient`):** Karakter ve seslendirmen sayfaları sıralı yerine en fazla 3 eşzamanlı istekle paralel çekilerek sayfa açılış hızı büyük oranda artırıldı.
- **Seslendirmen Birleştirme Zaman Aşımı Optimizasyonu (`KitsugiCharacterClient`):** Kitsu ve Shikimori için seslendirmen bekleme süresi 8 saniyeden 5 saniyeye indirilerek sayfa kilitlenmeleri önlendi.

### 🔍 4. Arama & "Tümünü Gör" Devamlılığı
- `SearchViewModel` ve `SourceSearchPage` arasındaki `openSourceSearch` köprüsü Bangumi motorunu da kapsayacak şekilde yenilendi; raflardan "Tümünü Gör"e tıklandığında sorgu, kapsam ve görünen sonuçlar eksiksiz aktarılır.

### 📦 5. Dağıtım
- Kullanıcı talimatı doğrultusunda **yalnızca `foss` varyantı** derlendi ve yayınlandı (`Kitsugi-Beta-v2.4.207-foss.apk`). GMS kesinlikle hariç tutuldu.

---

## 🇬🇧 English (v2.4.207)

### 🎌 1. Full Bangumi (bgm.tv) Platform Integration (7th Source)
- **OAuth 2.0 & Authentication:** Implemented redirect schemes `kitsugi://bangumi-auth` and `aniyomi://bangumi-auth` with `BangumiAuthStore` and `BangumiAuthManager`, enabling 1-click automatic browser login and manual authorization code entry.
- **Library & Smart Sync:** Full support for Bangumi collections (想看/在看/看过/搁置/抛弃 - Wish/Do/Collect/On Hold/Dropped), 0-10 rating scale, and episode progress grid via `BangumiImportManager` and `BangumiSyncManager`.
- **My List 7th Tab:** Added dedicated 🎌 Bangumi tab to My List (`MyListLibraryGrouping`, `MyListScreen`, `MyListComponents`), complete with signature brand color (`#F09199`), item counters, status chips, and in-tab account connection prompt.
- **Explore & Multi-Engine Search:** Integrated Bangumi as the 7th platform in All Sources Explore (`AllSourcesExplore`, `ExploreViewModel`) and Global Search (`SearchViewModel`, `KitsugiBangumiClient`) with anime/manga advanced filtering, tags, years, and character/staff lookups.
- **Brand Assets & Polish:** Added official vector logo (`ic_logo_bangumi.xml`), hero badges, detail screen headers, and gallery chips.

### ⚠️ 2. Cross-Sync Safety Guards & Legal Disclaimers
- **Centralized Disclaimer (`CrossSyncDisclaimer.kt`):** Clear legal notice that Cross-Sync is an experimental beta feature with no 100% match guarantee, disclaiming liability for mismatched lists, progress desyncs, or upstream platform sanctions (bans, suspensions).
- **Surface Visibility:**
  - Added "Experimental ·" tag and summary disclaimer to Account Connections.
  - Injected full disclaimer inside the Cross-Sync settings info card and quick actions section.
  - Embedded disclaimer directly into the synchronization dialog (`KitsugiCrossSyncDialog`).
- **Multi-Candidate Guard (`AuthViewModel`):** Added year compatibility (`yearsCompatible`) and title kinship checks for single-evidence selections when multiple candidate groups exist, preventing erroneous mergers and cross-account data overwrite (`identityReviewRequired = true`).
- **Diagnostic Clarity:** Explicitly labeled synthetic IDs (`300M+` for Kitsu, `100M+` for AniList) in diagnostics to prevent confusion with real MAL IDs.

### ⚡ 3. Jikan Rate Limiting, Shikimori Titles & Kitsu Concurrency
- **Jikan 429 Prevention (`KitsugiApiBase`):** Enforced a rate limit of 3 req/sec and 55 req/min (sliding window) for Jikan API, with automatic backoff cooldown and 3 retries on HTTP 429.
- **Shikimori English/Japanese Titles (`ShikimoriTitleResolver`):** Resolves English and Japanese titles via Shikimori's GraphQL API instead of defaulting to Russian titles, strictly adhering to user's title language preference.
- **Relation Type Translations (`KitsugiTranslations`):** Translated relation types (Sequel, Side story, Adaptation, etc.) to Turkish. Added AniList title fallback for Jikan relations.
- **Kitsu Concurrency (`KitsuClient`):** Characters and voice actors are now fetched concurrently (up to 3 parallel requests), significantly accelerating page loads.
- **Voice Actor Timeout Optimization (`KitsugiCharacterClient`):** Reduced VA fallback timeout from 8s to 5s for Kitsu and Shikimori to prevent UI stalls.

### 🔍 4. Search Shelf Continuity
- Extended `openSourceSearch` handoff between `SearchViewModel` and `SourceSearchPage` to seamlessly support Bangumi without dropping active query, scope, or cached shelf results.

### 📦 5. Release Distribution
- Built strictly as **FOSS Release** (`Kitsugi-Beta-v2.4.207-foss.apk`) per user instruction. No GMS variant generated.

---

## 🇹🇷 Türkçe (v2.4.206)

### 🛡️ 1. Native & Java Çökme Teşhisi, ANR/OOM Raporlama & Cockroach Çözümü
- **Native Çökme Yakalayıcı (C++ - libkitsugi_crash_handler.so):** Java `UncaughtExceptionHandler`'ın yakalayamadığı native çökmeler (SIGSEGV, SIGABRT, SIGBUS, SIGILL, SIGFPE, SIGTRAP) `_Unwind_Backtrace` ile asenkron sinyal güvenliğinde yakalanır; ham geri iz `native_crash.txt` dosyasına yazılır.
- **Sessiz Ölüm Dedektörü (KitsugiSessionSupervisor):** Uygulama temiz çıkış yapamadan kapandığında (LMKD bellek öldürmesi, ANR sonrası sonlandırma, SIGKILL), bir sonraki açılışta logcat taranarak `unclean_exit.txt` raporu üretilir ve kullanıcıya "Beklenmedik Kapanma" penceresi gösterilir.
- **Eylem İzi (Breadcrumbs):** Son 40 kullanıcı hareketi ve son bulunulan ekran anlık kaydedilir; çökme raporlarında hatanın hangi ekranda ve hangi eylem sırasında gerçekleştiği açıkça görülür.
- **Bağımsız Görevde Açılan Çökme Ekranı:** `Thread.sleep(800)` ana thread bloklaması kaldırıldı. Çökme ekranı (`KitsugiCrashActivity`) ayrı görev (`taskAffinity`) ve süreçte açılarak Android görev temizliğinin çökme ekranını yok etmesi engellendi.
- **Cockroach Sonsuz CPU Döngüsü & Kör Hata Yutma Düzeltildi:** `com.lagradost.cloudstream3` hatalarını körlemesine yutup arayüzü kilitli bırakan mantık düzeltildi; `Looper.loop()` sonlandığında oluşan %100 CPU kilitlenmesi (ANR donmaları) giderildi.
- **Erken Kurulum:** Çökme yakalayıcı `attachBaseContext()` aşamasına çekilerek açılış ve provider çökmeleri de kapsama alındı.
- **Genişletilmiş Teşhis Paylaşımı:** "Geliştiriciye Gönder", tüm teşhis kütüklerini (`crash_log`, `crash_history`, `unclean_exit`, `native_crash`, `breadcrumbs` vb.) tek pakette paylaşır.

### 📋 2. Duplicate Lazy Key Liste Çökmeleri Düzeltildi
- `IllegalArgumentException: Key ... was already used` hatasına yol açabilen 7 arayüz listesi düzeltildi: Ana sayfa ızgarası, modern ana sayfa satırları, yayın takvimi (liste ve ızgara), genişletilmiş medya ızgarası, manga eklenti kaynak listesi, özel liste düzenleyici ve oynatıcı hız seçim sayfası.

### 🖼️ 3. Resim İndirme & Bellek Güvenliği (OOM Önleme)
- **Bellek Koruma (Downsampling):** İndirilen görsellerin bildirim önizlemesi tam boy yerine `inSampleSize` ile küçültülerek decode edilir; 48 MB üzeri devasa görseller filtrelenir.
- **Android 10+ MediaStore Kaydı:** Görseller doğrudan `İndirilenler/Kitsugi/Images` altına MediaStore API ile yazılır (Android 11+ dosya yazma engeli aşıldı).
- Galeri ve indirme adımları eylem izine (breadcrumbs) dahil edildi.

### 🎭 4. Karakter Görselleri & Oyuncu Fotoğrafları İyileştirmeleri
- **Akıllı Başlık Adayları:** TMDB içeriklerinde tüm başlık varyantları (Türkçe, Romaji, İngilizce, Japonca, eşanlamlılar) taranarak doğru anime eşleşmesi bulunur.
- **AniList Karakter Araması Yedeği:** Yapım düzeyinde eşleşmeyen karakterler için doğrudan AniList karakter araması yapılır (`realMalId` doğrulamasıyla).
- **Canlı Çekim Kadrolarında Oyuncu Fotoğrafları Korundu:** Live-action yapımlarda oyuncu fotoğrafları silinmez; `isRealMediaRole` bayrağı ile doğrudan TMDB kişi profili açılır.
- **Detay Sayfasında Anında Görsel (Hint Image):** Karakter sayfasına girildiğinde karttaki görsel anında galeriye ve arka plana alınır, sayfa resimsiz kalmaz.
- **Jikan /pictures Optimizasyonu:** Galeri resim isteği yalnızca MyAnimeList kimlik uzayında çalıştırılır (TMDB 404 çağrıları bitti).

### ⚡ 5. Shikimori Detay Sekmeleri & Hız Optimizasyonları
- **Yerel İlişkiler ve Öneriler:** Shikimori `/similar` ve `/related` uç noktalarıyla tek istekte saniyeler içinde yüklenir; kapaklar toplu çözülür.
- **Ekip Sekmesi Boş Kalma Koruması:** Shikimori personel kaydı bulunmayan yapımlarda gerçek MAL ID'si üzerinden Jikan personel listesi devreye girer.
- **Sonsuz Skeleton Koruması (25 sn Zaman Aşımı):** Sekme yüklemelerine 25 saniye (bölümler için 45 saniye) zaman aşımı ve tek-uçuş (single-flight) koruması eklendi.
- **ID İzolasyonu:** Shikimori ID'sinin MAL ID sanılması engellendi; gerçek MAL ID'si önbellekli çözülür.

### 🎬 6. Detay Sayfası Tasarım & "Bilgiler" Kopyalama Butonları Birleştirmesi
- **Ortak Bilgiler Bileşeni (`DetailInfoValueRow`):** Kütüphane girdisi ve API detay ekranlarındaki "Bilgiler" kartı ortak bileşene kavuşturuldu; her iki ekranda da tüm satırlara (Durum, Başlangıç, Bitiş, Stüdyo, Süre, Yayın, Yaş Sınırı, İngilizce, Japonca vb.) kopyalama butonları eklendi.
- **"Diğer Adlar" Tekil Etiketler & Bağımsız Kopyalama:** "Diğer Adlar" satırında tüm isimleri tek metin halinde birleştirip kopyalama sorunu giderildi. Artık her isim kendi etiketinde (chip) ve bağımsız kopyalama butonuyla yer alır; virgül içeren adlar bozulmaz, boş ve tekrar edenler filtrelenir.
- **Kompakt İzle Butonu:** Sol panelde ve özet bilgi alanında tüm genişliği kaplayan devasa buton yerine içerikle uyumlu (`wrapContent`) şık ve kompakt bir "İzle" butonu tasarlandı.

### 🔍 7. Arama & "Tümünü Gör" Raf Tutarlılığı
- **Raf ile Kaynak Sayfası Uyumu:** Arama ekranında bir raftan "Tümünü Gör"e tıklandığında, raftaki sorgu, arama kapsamı (`SearchScope`) ve mevcut sonuçlar doğrudan `SourceSearchPage`'e aktarılır; sonuçlar kaybolmaz.
- **Tümü Rafından Açıldığında Çift Kapsam (Anime + Manga):** "Tümü" rafında hem anime hem manga listelendiği için, kaynak sayfası açıldığında her iki kapsam da birlikte taranır; rafta görünen içerikler sayfada silinmez.
- **Yenileme Başarısız Olsa Bile İçerik Koruma:** Ağ hatasında raftaki mevcut sonuçlar temizlenmez.
- **MAL Alakasız Popülerler Düşüşü Kaldırıldı:** MAL kaynağında spesifik sorgu sonuçsuz kalınca kullanıcıyı yanıltan genel popülerler listesine düşme engellendi.

### 🧾 Ek
- Sürüm adı `v2.4.206` olarak güncellendi.
- Kullanıcı talimatı doğrultusunda **yalnızca `foss` varyantı** derlendi ve yayınlandı (GMS derlenmedi).

---

## 🇬🇧 English (v2.4.206)

### 🛡️ 1. Crash Diagnostics & Reporting Overhaul (No More Silent Deaths)
- **Native Crash Handler (C++ - libkitsugi_crash_handler.so):** Catches SIGSEGV, SIGABRT, SIGBUS, SIGILL, SIGFPE, SIGTRAP using `_Unwind_Backtrace` in an async-signal-safe manner, writing raw backtraces to `native_crash.txt`.
- **Silent-Death Detector (KitsugiSessionSupervisor):** Discovers unhandled terminations (LMKD, post-ANR kills, SIGKILL) by scraping logcat on the subsequent launch, generating `unclean_exit.txt` and alerting the user with an "Unexpected Shutdown" dialog.
- **Action Breadcrumbs:** Tracks the last 40 user interactions and active screen keys to pinpoint the exact failure location.
- **Isolated Crash Screen:** Removed `Thread.sleep(800)` UI freezes. The crash screen (`KitsugiCrashActivity`) opens with its own `taskAffinity` in a dedicated process, protecting it from OS task teardown.
- **Cockroach Loop & Error Swallowing Fix:** Fixed unchecked swallowing of Cloudstream exceptions and eliminated the 100% CPU spinning loop on `Looper.loop()` exit.
- **Early Setup:** Initialized in `attachBaseContext()` to catch startup and ContentProvider faults.
- **Comprehensive Sharing:** "Send to Developer" bundles all diagnostic artifacts (`crash_log`, `crash_history`, `unclean_exit`, `native_crash`, `breadcrumbs`).

### 📋 2. Duplicate Lazy Key List Crashes Fixed
- Resolved `IllegalArgumentException: Key ... was already used` across 7 lists: Home grid, modern home rows, airing calendar (list & grid), expanded media grid dialog, manga extensions, custom list editor, and playback speed picker.

### 🖼️ 3. Image Downloads & Memory Hardening (OOM Prevention)
- **Sampled Decoding:** Notification icons downsample byte streams using `inSampleSize` instead of full-size bitmap decoding; capped downloads at 48 MB.
- **Android 10+ MediaStore Integration:** Saves downloaded images via MediaStore to `Downloads/Kitsugi/Images`, bypassing scoped storage issues on Android 11+.
- Logged gallery and download interactions into breadcrumbs.

### 🎭 4. Character Images & Live-Action Cast Photos
- **Candidate Title Resolver:** Resolves character images across all title variants (Turkish, Romaji, English, Japanese, synonyms).
- **Direct AniList Character Search Fallback:** Unmatched character records fall back to AniList character search with strict `realMalId` verification.
- **Preserved Cast Photos in Live-Action:** Actor photos are preserved, and live-action characters route directly to TMDB person profiles via `isRealMediaRole`.
- **Instant Detail Image:** Re-uses the list card thumbnail as an immediate placeholder on the detail page.
- **MAL-Only Jikan /pictures:** Prevents wasteful 404 network requests with TMDB IDs.

### ⚡ 5. Shikimori Tabs & Speed Optimization
- **Native Relations & Recommendations:** High-speed single-request fetching with batch cover resolution via Shikimori `/similar` and `/related`.
- **Staff Shelf Fallback:** Automatically queries Jikan staff data when Shikimori returns empty staff records.
- **25s Timeout & Single-Flight Protection:** Eliminates perpetual loading skeletons on unstable networks.
- **ID Resolution Safety:** Distinct caching and resolution between Shikimori IDs and MyAnimeList IDs.

### 🎬 6. Detail Screen Polish & Unified Info Row Copy Buttons
- **Unified Info Component (`DetailInfoValueRow`):** Both library entry and API result info cards now share the same row component with individual copy buttons on every row (Status, Start Date, End Date, Studio, Duration, Broadcast, Rating, English, Japanese).
- **Synonyms Chips with Individual Copy:** "Other Names" are displayed as individual chips, each with its own copy button. Prevents comma concatenation issues and ignores null/blank duplicates.
- **Compact Watch Button:** Replaced full-width watch buttons with clean, content-fitting buttons.

### 🔍 7. Search & "See All" Shelf Consistency
- **Handoff from Shelf to Source Page:** Clicking "See All" preserves the shelf's query, search scope (`SearchScope`), and pre-rendered results directly into `SourceSearchPage`.
- **Dual Scope for "All" Shelves:** Searching from "All" carries over both Anime and Manga scopes so items found on the shelf are never wiped upon opening the full source page.
- **Failure Resilience:** Network refresh failures preserve previously visible shelf results.
- **Removed Irrelevant MAL Popular Fallbacks:** Fixed MAL queries returning unrelated popular anime lists when search yielded zero matches.

### 🧾 Extras
- Version bumped to `v2.4.206`.
- Built and published exclusively as the `foss` release variant.

---

## 🇹🇷 Türkçe (v2.4.200)

### 🌍 TMDB & Simkl Başlık Dili Düzeltmesi (Türkçe → İngilizce → Romaji)

**Sorun:** TMDB sekmesindeki "Trend Animeler", "En Yüksek Puanlı Animeler" ve "Yakında Yayında" şeritlerinde, Türkçe başlık dili seçili olmasına rağmen Türkçesi bulunmayan içerikler Japonca/Çince (CJK) başlıklarla görünüyordu.

**Kök neden:** İngilizce yedek başlık isteği üretilirken `language=` parametresi `Regex("language=[^&]+")` ile değiştiriliyordu. Bu regex `with_original_language=ja` parametresinin içindeki `language=ja` kısmını da yakalayıp `with_original_language=en-US` yapıyordu. Sonuç: anime şeritlerinin (hepsi `with_original_language=ja` kullanır) İngilizce yedek isteği **tamamen farklı bir içerik listesi** döndürüyor, ID eşleşmesi tutmuyor ve Japonca başlıklar ekranda kalıyordu.

**Çözüm:**

- **Güvenli URL üretimi (`TmdbUrlUtils`):** `language` parametresi artık yalnızca tam parametre olarak değiştiriliyor; `with_original_language` ve diğer filtreler korunuyor. Yedek istek aynı içerik listesini döndürdüğü için ID eşleşmesi birebir tutuyor.
- **Merkezi başlık çözümleyici (`MediaTitleResolver`):** Türkçe → İngilizce → Romaji zinciri tek yerde tanımlandı. CJK başlıklar ilk üç adımda asla kabul edilmiyor; yalnızca hiçbir Latin alternatif yoksa (son çare) gösteriliyor.
- **Akıllı yedek istek (`TmdbTitleFallback`):** TMDB istenen dilde başlık bulamazsa orijinal (Japonca) başlığı döndürür. Uygulama bunu "yerelleştirilmiş başlık == orijinal başlık" karşılaştırmasıyla ve CJK taramasıyla yakalıyor; yalnızca gerektiğinde tek bir `en-US` isteği yapıp başlıkları birleştiriyor. Gereksiz ağ trafiği yok.
- **Kapsam:** Keşfet şeritleri (trend/popüler/en yüksek puanlı/yakında yayında + "Tümü" ekranları), arama sonuçları, medya detay sayfası (TMDB `alternative_titles` desteğiyle TR/US/JP alternatifleri), bölüm adları, yayın takvimi, yapım şirketi sayfaları, öneriler/ilişkiler ve oyuncu-ekip çalışmaları.
- **Simkl:** Simkl kayıtlarında önce TMDB üzerinden Türkçe başlık denenir; yoksa Simkl'in İngilizce/romaji alanlarına inilir. Simkl `title` alanı CJK geldiğinde ekrana düşmez.
- **Gösterim güvencesi:** TMDB/Simkl kaynaklı öğeler için kart ve liste başlıkları, seçilen başlık dilinden bağımsız olarak daima Latin alfabesinde bir alternatif bulur (Japonca seçilmediği sürece).
- **Önbellek tazeleme:** Bellek içi ve kalıcı keşfet önbelleği anahtarları artık dil + başlık çözümleme sürümü içeriyor. Böylece eski sürümden kalan hatalı (Japonca) kayıtlar servis edilmiyor, düzeltme anında görünür oluyor.

### 🧾 Ek
- Sürüm adı `2.4.200` olarak güncellendi.

---

## 🇬🇧 English (v2.4.200)

### 🌍 TMDB & Simkl Title Language Fix (Turkish → English → Romaji)

**Problem:** In the TMDB tab, shelves such as "Trend Animeler", "En Yüksek Puanlı Animeler" and "Yakında Yayında" showed Japanese/Chinese (CJK) titles for entries that had no Turkish translation, even though Turkish was the selected title language.

**Root cause:** When building the English fallback request, the `language=` parameter was rewritten with `Regex("language=[^&]+")`. That regex also matched the `language=ja` fragment inside `with_original_language=ja` and turned it into `with_original_language=en-US`. As a result, the English fallback request for anime shelves (all of which use `with_original_language=ja`) returned a **completely different result set**, ID matching failed, and raw CJK titles stayed on screen.

**Fix:**

- **Safe URL building (`TmdbUrlUtils`):** the `language` parameter is now replaced only as a standalone parameter; `with_original_language` and other filters are preserved, so the fallback request returns the same list and IDs match one-to-one.
- **Single central resolver (`MediaTitleResolver`):** the Turkish → English → Romaji chain is defined in one place. CJK titles are never accepted in the first three steps and appear only as a last resort when no Latin alternative exists.
- **Smart fallback fetch (`TmdbTitleFallback`):** TMDB returns the original (Japanese) title when it has no translation for the requested language. The app detects this by comparing localised vs. original titles and scanning for CJK, then performs a single `en-US` request only when needed.
- **Coverage:** explore shelves (trending/popular/top-rated/upcoming + "see all" screens), search results, media detail pages (via TMDB `alternative_titles`, TR/US/JP), episode names, airing calendar, production company pages, recommendations/relations and person credits.
- **Simkl:** Simkl entries try a TMDB lookup first for a Turkish title, then fall back to Simkl's English/romaji fields; CJK `title` values never reach the UI.
- **Display guarantee:** for TMDB/Simkl items, card and list titles always resolve to a Latin alternative regardless of the selected title language (unless Japanese is explicitly selected).
- **Cache refresh:** in-memory and persistent explore cache keys now include the language plus the title-resolution version, so stale (Japanese) entries from previous builds are ignored and the fix is visible immediately.

---

## 🇹🇷 Türkçe (v2.4.198)
### 🔞 +18 Bulanıklık & Yetişkin İçerik Güvenliği (Her Koşulda Tam Koruma)
- **46 Arayüz Yüzeyinde Eksiksiz Uygulama:** Ana sayfa, Keşfet şeritleri, Arama sonuçları, Karakter/Personel detayları, Galeri pencereleri ve Android TV arayüzlerinde +18 afiş ve görseller ayara tam uyumlu olarak maskelenir.
- **Eski Android Sürümleri İçin Yedek Motor (API < 31 Fallback):** Android 12 altındaki cihazlarda sistem düzeyindeki `Modifier.blur` sessizce devre dışı kaldığı için afişlerin açık kalması sorunu, Coil tabanlı `BlurTransformation` ve akıllı görsel boru hattı (`KitsugiNsfwImage`) ile çözüldü. Artık her cihazda bulanıklık garantilidir.
- **Platform Başına Doğrulanmış +18 Kuralları:**
  - **Kitsu:** R18 derecelendirmesi, `nsfw=true` veya hentai alt türü (`KitsuAdultFlags`).
  - **Shikimori:** Yalnızca `rx`/`hentai` yapımlar (`r`/`r_plus` ayrıldı).
  - **Simkl:** `adult=true` bayrağı ve resmi sertifikalar (`NC-17`, `XXX`, `18+`...) (`SimklAdultFlags`).
  - **AniList & TMDB:** `isAdult` ve `adult` alanları keşif takvimleri ve vitrinlere eksiksiz aktarılır.
  - **Manga Eklentileri:** API bayrağı olmayan kaynaklar için etiket ve tür analizi (`hentai`, `doujin`, `xxx`... ecchi hariç tutularak).
- **Kişisel Liste İçe Aktarımı:** Kitsu, Shikimori ve Simkl içe aktarımları `isAdult` bilgisini ilk andan itibaren kaydeder.

### 📌 Kaynak Seçimi Kalıcılığı & Kitsu Kimlik İzolasyonu
- **Keşfet:** Son seçili kaynak (Tümü / AniList / MAL / TMDB / Simkl / Kitsu / Shikimori) `SettingsDataStore` üzerinde saklanır ve uygulama yeniden açıldığında otomatik geri yüklenir. Otomatik kaynak düşüşleri seçim sayılmaz, kaydedilmez.
- **Arama:** Kaynak motoru + kapsam (sekme, platform, medya türü) birlikte hatırlanır; sekme çipleri ile motor seçicisi senkron kalır ve açılışta gereksiz arama tetiklenmez.
- **Listem:** Kaynak sekmesi `tab_index` ile kalıcıdır; Keşfet/Arama ile aynı davranış sergilemesi korunur.
- **Açılış maliyeti:** Son seçim TMDB/Tümü dışında bir kaynaksa splash sırasındaki 11 istekli TMDB önbellek ön-yüklemesi atlanır.
- **Kitsu Alakasız Ayrıntı Düzeltmesi:** Kitsu içe aktarmasında ham MAL ID'lerinin yazılmasından kaynaklanan sayısal çakışma (ör. Keep Your Hands Off Eizouken!) çözüldü. `KitsuIdNamespace` (`300M+1..399M+1`), stableId çözümleyici ve `KitsuIdentityMigration` eklendi.

### ♾️ Simkl Gerçek Sayfalama & Akış İyileştirmeleri (20'şer Sonsuz Kaydırma)
- **100 Sınırı Kaldırıldı:** Simkl artık statik bir sınırda durmak yerine, diğer kaynaklar gibi aşağı kaydırdıkça 20'şer 20'şer yeni içerik getirmeye devam eder.
- **Resmî Simkl API Endpoint Mimarisi:**
  - **Tür & Popülerlik Listeleri:** `genres/...` endpoint'leri üzerinden `page` ve `limit` parametreleriyle 20 sayfaya kadar (~1200 içerik) gerçek sayfalama.
  - **Yakında Yayında:** `anime/premieres/soon` ve `premieres/new` ile 20'şer içeriklik sayfalı akış.
  - **Trend Listeleri:** Sayfa parametresi bulunmayan Simkl trend JSON'ları için 500'lük Top snapshot (`today_500.json`, `week_500.json`) 1 saat boyunca önbelleğe alınır ve 20'şer dilimler halinde kaydırılır.
  - **Yayında Olanlar (Airing):** Takvim endpoint'inden tek seferde eksiksiz liste çekilir.
- **Arama Tarafında Sayfalama:** Simkl arama ve gelişmiş arama (`search`, `searchAdvanced`) `page` desteğine kavuştu. Hatalı path parçaları dokümandaki `all` standartlarına getirildi.
- **Zorunlu API Parametreleri & User-Agent:** Tüm isteklere `client_id`, `app-name=kitsugi`, `app-version` ve `KitsugiApp/... (Android)` User-Agent başlığı eklendi.
- **Kişisel Listeler:** "İzlemeye Devam" ve "Planladıklarım" listeleri tek seferde yüklendiği için gereksiz yere tekrar çekilmez.

---

## 🇹🇷 Türkçe (v2.4.196)
### 🛡️ Yanlış İçerik Eklenmesine Karşı Kimlik Güvencesi (CrossSyncIdentityGuard)
- **6 Sızıntı Vektörünün Kapatılması:**
  - **V1 (TMDB - ARM):** ARM servisine TMDB kimliği aktarımı kaldırıldı (film/dizi ID çakışması ve tüm franchise'ı kapsayan dizi ID'lerinin yanlış anime MAL ID'si üretmesi engellendi; yalnızca birebir MAL/AniList/Kitsu kimlikleri kullanılır).
  - **V2 (Simkl Anime - TMDB):** Simkl'a anime kayıtları için TMDB kimliği gönderilmesi tamamen engellendi. Simkl ID varsa yalnızca o gönderilir; dizi/film çakışmaları önlendi.
  - **V3 (Simkl Başlık Eşleme):** Güvenli kimliği (Simkl/MAL) olmayan animeler için Simkl'da başlık eşleştirmesi kapatıldı (`titleMatchingAllowed = false`). Benzer adlı film veya dizilerin listeye sızması engellendi.
  - **V4 (Kitsu Başlık Eşleme):** Kitsu başlık aramasında kısmi eşleşmeler (ör. "Oni Chichi" ⊂ "Oni Chichi 2") kapatıldı; birebir alias ve uyumlu yıl şartı getirildi.
  - **V5 & V6 (Tek Kaynak Kimlik Doğrulama):** Yeni `CrossSyncIdentityGuard` ile en az iki bağımsız kaynakça doğrulanmayan veya tek bir sağlayıcı eşlemesinden (Simkl `ids.mal`, Kitsu `mappings`, AniList `idMal`) gelen kimlikler için resmi MAL API / Jikan kataloğundan başlık ve yıl doğrulaması yapılır.
- **"Doğrulanamayan Kimlikle Asla Yeni Kayıt Eklenmez" Kuralı:** Başlık akrabalığı ve yıl kontrolünden geçemeyen şüpheli kimliklerle hiçbir platforma (AniList, MAL, Kitsu, Shikimori, Simkl) yeni kayıt eklenmez; atlanır ve raporda gerekçesiyle gösterilir. Mevcut kayıtların güncellenmesi platformun kendi kimliğiyle yapıldığı için bu kısıtlamadan etkilenmez.

### 📊 Platform Bazlı Eşitleme ve Hata Sayımı Düzeltmeleri
- **Simkl Sayım ve Makbuz İyileştirmesi:** 35'lik grupta tek bir eşleşmeyen öğe yüzünden tüm grubun "200 hata" olarak sayılması engellendi; kayıt bazlı makbuz (`Receipt.unmatched`) ile başarılı olanlar "eklendi", Simkl'de bulunamayanlar "atlandı" olarak işlenir. TV dizi ilerlemesi sınırı ve kısmi puan/geçmiş uyarıları artık grup hatası değil; eşitleme sonunda başlık listesiyle tek bir özet uyarı olarak raporlanır. 400M+ Shikimori kimliklerinin yanlışlıkla Kitsu ID olarak Simkl'e gönderilmesi düzeltildi (`300M..400M` aralık kontrolü).
- **Kitsu Resmi Mappings & 429 Yönetimi:** Resmi Kitsu `/mappings` API uç noktası (MAL ve AniList eşlemeleri) entegre edildi; manga ve ARM'da olmayan animeler için ID çözümleme oranı artırıldı. Kitsu istekleri için merkezi limiter ve 429 durumlarında 3 tekrarlı yeniden deneme mekanizması eklendi; API hata gövdeleri (`KitsuWriteResult`) okunarak ayrıntılı teşhis sağlandı. Kitsu içe aktarımına yayın yılı (`startYear`) eklendi.
- **AniList Kota ve Hız Limiti Optimizasyonu:** İstek aralığı 2100 ms'ye ayarlanarak (~28 req/dk) AniList'in fiili 30/dk hız sınırına tam uyum sağlandı. HTTP 429 durumlarında `Retry-After` başlığına duyarlı en fazla 3 kez otomatik yeniden deneme eklendi. İçe aktarımda zaten var olan `idMal` için her kayıtta atılan gereksiz sorgu kaldırılarak kota tasarrufu yapıldı.
- **MyAnimeList & Shikimori Özet Raporlama:** Çözülemeyen her kayıt için tek tek uyarı basılması yerine platform başına tek satırlık özet uyarı eklendi. Shikimori içe aktarımına `aired_on`/`released_on` yayın yılı ve İngilizce başlık eklendi. AniList içe aktarımında `seasonYear` boşsa `startDate.year` yedeği devreye alındı.

### 🧩 Kimlik Eşleştirme ve Çakışma Yönetimi
- **Aynı İsimli Yapımlar & Çelişen Kimlikler:** Berserk (1997/2016), Hunter x Hunter (1999/2011), Golden Time gibi aynı başlığa sahip yapımların tamamen izole edilip hiçbir yere yazılmaması sorunu çözüldü; artık ayrı gruplar halinde kendi kimlikleriyle yazılır.
- **Sahte "Kimlik Doğrulaması Gerekli" Gevşetmesi:** Farklı platformlardaki alias uyumsuzlukları yalnızca her iki tarafta da yıl biliniyor ve yıl farkı 1'den büyükse incelemeye düşer; aksi halde güvenle birleştirilir.
- **Durum ve İlerleme Doğruluğu:** Simkl'in toplam bölüm (`total`) sayısından "Tamamlandı" çıkarımı yapılması engellendi. Tek kaynaklı gruplarda gereksiz güncellemeler engellendi.
- **Temiz Rapor:** "Eşitleme kısmen tamamlandı" satırı hata yerine bilgi formatına dönüştürüldü.

### 📱 Yenilenen Tam Ekran Çapraz Eşitleme Paneli
- **Tam Ekran Dialog & Çentik Uyumu:** Panel artık `fullScreen = true` olarak açılır; durum çubuğu ve ekran çentikleri (`displayCutoutPadding`) ile kusursuz uyum sağlar.
- **Dikey ve Yatay Yönlendirme:** Dikey modda tek sütunlu akıcı hiyerarşi; yatay modda sol panelde ilerleme/hesaplar/eylemler, sağ panelde filtreler ve canlı günlük gösterilir.
- **Filtre Çipleri & Kompakt Hesap Kartları:** Hata ve uyarı sayıları filtre çiplerine rozet olarak eklendi; platform logolu kartlar ve 40 dp optimize butonlarla daha kompakt bir görünüm sunar.

---

## 🇹🇷 Türkçe (v2.4.195)
### 🌐 Keşfet "Tümü" — Kaynak Bazlı Keşif Alanları & Kategori Gruplaması
- **Kaynaklara Özel Bağımsız Alanlar:** AniList, MyAnimeList, TMDB, Simkl, Kitsu ve Shikimori platformları birbirine karıştırılmadan kendi logolu, renk vurgulu bağımsız başlıklarına kavuştu.
- **Kategori Ayrımı:** Trendler, en iyiler, popülerler, sezon içerikleri ve ilgili platformun desteklediği kategoriler doğrudan kendi kaynağının altında listelenir.
- **Sabit Kaynak & Kategori Çubuğu:** Ekranın üstündeki sabit çubukla doğrudan istenilen platforma veya kategori şeridine anında atlanabilir; uzun sayfalarda sayfa başına dönüş düğmesi eklendi.
- **Bağımsız Daraltma & Dayanıklı Durum:** Her kaynak alanı ayrı ayrı daraltılıp genişletilebilir; daraltma durumları ekran döndürmelerinde veya gezinmede korunur.
- **Akıllı "Tümünü Gör":** Çoklu görünümdeki "Tümünü Gör" butonu doğru kaynağın ve kategorinin devam sayfasını açar.
- **İzole Hata Yönetimi & Yeniden Deneme:** Bir kaynak yüklenemezse veya hata verirse diğer kaynaklar görüntülenmeye devam eder; yalnızca sorunlu kaynak tek dokunuşla yeniden denenebilir.
- **Büyük Ekran & TV Uyumu:** Ortak kaynak alanları mobil, tablet ve Android TV D-pad gezinmesiyle tam uyumlu hale getirildi.

### 📋 Listem "Tümü" Sekmesi, Temsilci Kaynak & Simkl TMDB Detay Geçişi
- **Sola Kaydırma & Sekme Sırası Düzeltmesi:** "Tümü" sekmesi gerçekten ilk sıraya (`index 0`) taşındı. Pager ile üst buton dizilimi eşitlenerek Tümü sayfasından sola kaydıramama sorunu giderildi.
- **Öncelikli Temsilci Kaynak:** Birleşik kütüphanede aynı yapımın farklı servislerdeki kayıtları birleştirilirken öncelik zinciri uygulandı: `AniList > MAL > Kitsu > Shikimori > Simkl > TMDB`. Animelerde otomatik olarak AniList öne çıkarken, dizi ve filmlerde doğal olarak Simkl/TMDB temsilci kalır.
- **Hızlı Simkl Detayları (TMDB Entegrasyonu):** Simkl kayıtlarının ayrıntı sayfaları doğrudan TMDB üzerinden açılır (`tmdbId`, ARM ID çözücü veya arama). Simkl'in yavaş API yanıtı detay ekranını geciktirmez.
- **Varsayılan Manuel Kayıt:** Tümü sekmesinde manuel kayıt eklerken varsayılan hedef AniList olarak ayarlandı.

### 🎬 Akış ve Kalite Bilgi Doğruluğu (StreamInfoFix)
- **"400p" Kök Neden Düzeltmesi:** CloudStream eklentilerinin bilinmeyen kalite döndürdüğünde (`Qualities.Unknown = 400`) ekrana "400p" basması sorunu giderildi.
- **Sahte Dil ve Rozet Çıkarmalarının Kaldırılması:** "Subaru" kelimesinden "Altyazılı", "Dubai" kelimesinden "Dublaj" basan hatalı alt dize filtreleri kaldırıldı. "Türkçe eklenti ⇒ Altyazılı" varsayımı ve kanıtsız "🎬 Standart" etiketi temizlendi.
- **Gerçek Ölçüm ve HLS Çözümleme (`StreamProbe`):** HLS master playlist'i üzerinden `#EXT-X-STREAM-INF` (çözünürlük) ve `#EXT-X-MEDIA` (ses/altyazı dilleri) etiketleri incelenerek gerçek veriler rozetlere yansıtılır. Yüklenen altyazılar `CC TR` / `CC EN` olarak gösterilir.
- **Arayüz ve Filtre Uyumu:** Kart rozetleri, alt sayfa akış seçici, filtre çipleri ve TV arayüzü yeni `StreamInfoResolver` verisiyle senkronize edildi.

### 🔐 Shikimori Yetkilendirme & Gradle Bellek Optimizasyonu
- **Shikimori 401 Otomatik Yenileme:** Sunucu tarafında geçersiz kılınan token'larda liste çekimi sırasında otomatik refresh yapılarak kullanıcı oturumunun kopması önlenir.
- **Otomatik RAM Temizliği:** `gradle.properties` içine 60 saniyelik daemon zaman aşımı eklendi; derleme bittikten sonra arkada asılı kalan Gradle/JDK süreçlerinin bellek işgali önlendi.

---

## 🇬🇧 English (v2.4.195)
### 🌐 Explore "All" — Dedicated Source Sections & Grouped Categories
- **Dedicated Platform Sections:** AniList, MyAnimeList, TMDB, Simkl, Kitsu, and Shikimori each receive dedicated branded headers with custom accent colors and descriptions instead of intermixed rails.
- **Source-Scoped Rails:** Trending, top-rated, popular, seasonal, upcoming, and other platform categories stay strictly grouped under their respective provider section.
- **Sticky Platform Navigation:** Sticky header bar allows immediate jumping directly to any provider (AniList, MAL, TMDB, etc.) or category rail, with a quick return-to-top shortcut.
- **Collapsible Sections & Saved State:** Each provider section can be independently collapsed or expanded, preserving state across screen rotations and navigation.
- **Accurate "See All" Routing:** Tapping "See All" navigates to the dedicated category view matching the exact source and filter.
- **Isolated Provider Failures & Retry:** If a single provider fails to load or times out, all other providers remain intact; users can retry individual failed providers independently.
- **TV & Large Screen Support:** Unified source sections fully support Android TV D-pad focus, navigation, and tablet landscape layouts.

### 📋 My List "All" Tab, Representative Hierarchy & Fast Simkl Details
- **Tab Ordering & Swiping Fix:** The unified "All" tab is now index 0. Synchronized pager and pill tabs eliminate the gesture lock where users could not swipe left from the "All" page.
- **Smart Representative Provider Hierarchy:** Multi-provider entries deduplicate using prioritized representative selection: `AniList > MAL > Kitsu > Shikimori > Simkl > TMDB`. Anime defaults to AniList, while TV shows and movies naturally maintain Simkl/TMDB representation.
- **Lightning Fast Simkl Details via TMDB:** Simkl entries now route details through TMDB (`tmdbId`, ARM resolver, or fallback search), removing slow Simkl API bottlenecks.
- **Default Manual Entry:** Manual additions on the "All" tab default to AniList.

### 🎬 Accurate Stream Information (StreamInfoFix)
- **"400p" False Badge Root Fix:** Resolved issue where `Qualities.Unknown = 400` in CloudStream plugins resulted in a "400p" label.
- **Removed Heuristic Audio/Subtitle Inferences:** Fixed substring matching bugs (e.g., "Subaru" triggering Subtitle, "Dubai" triggering Dubbed). Eliminated automatic "Turkish Plugin ⇒ Subtitled" assumption and baseless "🎬 Standard" labels.
- **Real Stream Probing (`StreamProbe`):** Analyzes HLS playlists for actual `#EXT-X-STREAM-INF` resolutions and `#EXT-X-MEDIA` audio/subtitle tracks. Verified subtitle files display as `CC TR` / `CC EN`.
- **Synchronized UI & Filters:** Stream cards, bottom sheets, filter chips, and TV screens are fully wired to the accurate `StreamInfoResolver` output.

### 🔐 Shikimori 401 Silent Token Refresh & Memory Optimizations
- **Shikimori 401 Auto-Recovery:** Automatic token refresh on 401 errors during rate syncing prevents unneeded session expirations.
- **Gradle/JDK Daemon Idle Cleanup:** Added 60s idle timeout to `gradle.properties` and post-build cleanup scripts to stop lingering Java daemons from holding system RAM.

---

## 🇹🇷 Türkçe (v2.4.194)
### ⚡ Çapraz Eşitleme (Cross-Sync) %0 Takılma Düzeltmesi & Gelişmiş Tanılama
- **Aday İndeksleme ve Performans Optimizasyonu:** Eşleştirme aşamasında her yeni içerik için tüm birleştirilmiş kayıtların baştan sona taranması ($O(N^2)$) kaldırıldı. Kimlik ve başlık indeksleriyle (`CrossSyncCandidateIndex`) aday arama daraltıldı; binlerce kayıtta donma ve takılmalar engellendi.
- **Canlı İlerleme Sayacı:** Eşleştirme aşamasında sayaç her 25 kaynak kaydında güncellenir ve coroutine iptal kontrolleri düzenli çalışır.
- **Dinamik Durum ve Duraklama Uyarıları:** Ağ beklerken sahte %0 yerine belirsiz (indeterminate) ilerleme göstergesi sunulur. Ekrana geçen süre, tahmini kalan süre ve uzun süren duraklamalarda uyarı eklendi.
- **Gelişmiş Günlük & Rapor Paylaşımı:** Günlükte Tümü / Sorunlar / Hatalar / Uyarılar filtreleri ve genişletilebilir teknik ayrıntılar yer alır. Eşitlemeyi onaylayarak durdurabilir, sürerken kısmi raporu paylaşabilir veya tamamlanınca raporu dışa aktarabilirsiniz.
- **Otomatik Rapor ve Güvenlik:** Raporlar otomatik olarak `Downloads/Kitsugi/CrossSyncReports` dizinine kaydedilir; cihaz ve ortam bilgileri eklenir, Bearer ve API anahtarları maskelenir (`[REDACTED]`). Hesaplardan içerik silinmez.
- **İstem Dışı Eşitleme Koruması:** "Son Eşitleme Raporu"na dokunulduğunda yanlışlıkla yeni bir eşitleme başlatılması engellendi.

### 🌐 Listem Sayfası Birleşik "Tümü" Görünümü & Dinamik Platform Rozetleri
- **Tüm Kaynakları Tek Ekranda Birleştirme:** Listem sekmesine AniList, MyAnimeList, Simkl, Kitsu ve Shikimori kütüphanelerini tek ekranda toplayan "Tümü" seçeneği eklendi.
- **Tekil Gösterim & Yinelenme Koruması:** Farklı servislerde bulunan aynı yapım yinelenmek yerine tek kartta birleştirilir (`groupMyListEntries`).
- **Dinamik Kaynak Rozetleri:** Kartların altında yapımın hangi platformlarda bulunduğunu gösteren orijinal platform logoları (`FlowRow` ile dar kartlarda taşmadan) sergilenir.
- **Tüm Düzenlerle Uyumlu:** Kompakt, rahat, ayrıntılı, 2 sütunlu grid ve minimalist kart düzenleriyle tam uyumludur.
- **Tek Dokunuşla Yenileme:** Birleşik görünümde aşağı çekip yenileme yapıldığında bağlı tüm aktif sağlayıcılar sırayla eşitlenir.

---

## 🇬🇧 English (v2.4.194)
### ⚡ Cross-Sync 0% Stall Fix & Advanced Diagnostics
- **Candidate Indexing & Performance:** Eliminated $O(N^2)$ quadratic scanning during cross-platform media grouping. Added identity & title candidate index (`CrossSyncCandidateIndex`), speeding up grouping on multi-thousand entry libraries.
- **Live Progress Reporting:** Progress counters update every 25 source records during grouping, with periodic cooperative coroutine cancellation checks.
- **Indeterminate Progress & Stall Warnings:** Displays indeterminate progress indicators during network/verification steps instead of a false 0% determinate bar. Added elapsed time, estimated time, and long-stall warnings.
- **Advanced Log Filtering & Report Sharing:** Logs feature All / Issues / Errors / Warnings filters and expandable technical stacktraces. Allows confirmed cancellation, sharing partial reports while sync is active, and exporting final reports.
- **Auto Report Storage & Credential Redaction:** Diagnostic reports are automatically saved to `Downloads/Kitsugi/CrossSyncReports/` with environment details and redacted credentials (`[REDACTED]`). No items are deleted from upstream accounts.
- **Accidental Sync Trigger Prevention:** Viewing the "Latest Sync Report" no longer accidentally launches a new synchronization.

### 🌐 My List Unified "All" View & Dynamic Platform Badges
- **Unified Library Tab:** Added an "All" option to the My List screen consolidating libraries across AniList, MyAnimeList, Simkl, Kitsu, and Shikimori in one view.
- **Deduplicated Media Grouping:** Identical media titles across multiple connected providers are unified into a single card representation.
- **Dynamic Platform Badges:** Cards display responsive badges (`FlowRow`) indicating all connected platforms hosting the entry.
- **Layout Compatibility:** Fully supported across compact, comfortable, large, 2-column grid, and minimalist card designs.
- **Unified Pull-to-Refresh:** Pulling to refresh in the combined view sequentially syncs all active connected providers.

---

## 🇹🇷 Türkçe (v2.4.193)
### 🔍 Arama Deneyimi (UX) Yenilemesi & "Tümünü Gör" Ayrık Kaynak Sayfası

- **Arama Sayfası Sadeleştirmesi:** Arama çubuğunun altındaki kalabalık kategori çipleri, sıralama seçici, filtreler ve platform bilgi satırı ana sayfadan kaldırılarak sağ üstteki filtre butonunun açtığı `SourceEngineFilterSheet` altında toplandı. Sayfa temizlendi.
- **Kompakt Filtre Özeti:** Varsayılandan farklı bir filtre/kapsam seçildiğinde tek satırlık kompakt özet çipi görüntülenir ve tıklandığında ayar panelini açar.
- **"Tümünü Gör" Artık Ayrık Sayfa (`SourceSearchPage`):** Çoklu arama raflarındaki "Tümünü Gör" artık aynı sayfada sekme değiştirmek yerine geri butonu, başlık ve dikey sonuç listesi içeren bağımsız bir sayfada açılır.
- **İzole ViewModel:** `SourceSearchPage` kendi izole `SearchViewModel` örneğini kullanır; burada yapılan filtre ve arama değişiklikleri ana çoklu arama durumunu etkilemez.
- **Shikimori 1-Tık OAuth Callback Düzeltmesi (`redirect_uri` Uyuşmazlığı):** Shikimori OAuth yetkilendirme isteğindeki callback adresinin uyuşmaması ("The requested redirect uri is malformed or doesn't match client redirect URI") sorunu giderildi. Varsayılan 1-tık girişi resmî `aniyomi://shikimori-auth` callback'ine bağlandı; özel kayıtlı uygulamalar için `kitsugi://shikimori-auth` alternatifi korundu ve regresyon birim testleri eklendi.

---

## 🇬🇧 English (v2.4.193)
### 🔍 Search UX Overhaul, Dedicated "See All" Screen & Shikimori 1-Tap Fix

- **Clean Search Screen:** Cluttered chips, sorting dropdowns, and info banners removed from under the search bar and unified into the top-right `SourceEngineFilterSheet`.
- **Compact Active Filter Summary:** An unobtrusive summary chip appears only when non-default filters or scopes are active.
- **Dedicated "See All" Screen (`SourceSearchPage`):** Tapping "See All" opens a full standalone screen with back navigation, search bar, filters, and vertical results.
- **Isolated ViewModel:** Uses an independent `SearchViewModel` instance so altering filters on source search does not corrupt multi-search state.
- **Shikimori 1-Tap OAuth Callback Fix (`redirect_uri` Mismatch):** Fixed the OAuth authorization rejection ("The requested redirect uri is malformed or doesn't match client redirect URI"). Default 1-tap flow now uses the official registered `aniyomi://shikimori-auth` callback with `kitsugi://shikimori-auth` preserved for custom apps, verified via unit tests.

---

## 🇹🇷 Türkçe (v2.4.194)
### 🔔 Bildirim Altyapısı Denetimi — 5 Kaynak (AniList · MAL · Simkl/TMDB · Kitsu · Shikimori)

- **Bildirim Teşhisi (yeni):** Bildirimler ekranının sağ üstüne 🪲 butonu eklendi. Tek dokunuşla 5 kaynağa **gerçek istek** atılır; hesap bağlı mı, hangi uç nokta kullanılıyor, **HTTP durum kodu**, dönen kayıt sayısı, süre ve varsa hata + çözüm ipucu gösterilir. Artık "geliyor mu gelmiyor mu" tahmine kalmaz.
- **AniList — sessiz hata yutma kaldırıldı:** İstek başarısız olduğunda istemci boş liste döndürüp "bildirim yok" gösteriyordu; artık GraphQL hataları gerçek mesajla gösterilir. Okunmamış bildirim sayacı eklendi ve sekmede rozet olarak görünür.
- **Shikimori — gerçek bildirim akışı:** Uygulama bugüne kadar **izleme geçmişini** bildirim gibi gösteriyordu. Resmî uçlar (`GET /api/users/:id/messages?type=notifications|news` ve `/unread_messages`) eklendi; `messages` izni yoksa ekran bunu ve nasıl verileceğini açıklar, geçmiş listesine güvenli düşer. OAuth izni artık client_id'ye göre seçilir (kendi uygulamanızı girerseniz `user_rates messages`).
- **Simkl — deprecated takvim yolu düzeltildi (kritik):** Eski kod, **1 Şubat 2027'de güncellenmeyi bırakacak** olan v2 olmayan dosyaları okuyor ve sonucu **ilk 50 öğeyle** kesiyordu; eşleşme bu yüzden neredeyse hiç tutmuyordu. Yeni `SimklCalendarClient` resmî `data.simkl.in/calendar/v2/…` dosyalarını (`{calendar, metadata}`) ve kullanıcının `/sync/all-items/*/watching` listesini kullanır (simkl/tmdb/mal ID eşleşmesi, ±7 gün penceresi).
- **MyAnimeList — takvim haftası tuzağı:** Akış, içinde bulunulan takvim haftasına bağlı olduğu için hafta başında boşalıyor ve `id`'ye göre sıralanıyordu. Artık kayan **son 7 gün** penceresi kullanılır, doğru sıralanır ve listenin ne olduğu (MAL API'sinin kişisel bildirim sunmadığı) bilgi bandında yazılır.
- **Kitsu — sonraki bölüm tarihleri:** Kitsu'nun genel API'sinde bildirim ucu yoktur; buna karşılık anime kaynağındaki `nextRelease` alanı okunup "Bölüm N • yayınlandı/yaklaşan" akışı üretilir.
- **Arka plan işçisi güçlendirildi:** Simkl'de bölüm başına ayrı bildirim (eskiden dizi başına 1), yalnızca son 24 saatte yayınlananlar; MAL için hafta sınırına takılmayan pencere; **Kitsu ve Shikimori için yeni arka plan desteği** ve Ayarlar > Bildirimler'de iki yeni anahtar.
- **Ayrıntılı rapor:** `docs/audits/NOTIFICATION_INFRASTRUCTURE_AUDIT_2026-10-07.md`

---

## 🇬🇧 English (v2.4.194)

### 🔔 Notification Infrastructure Audit — All 5 Sources (AniList · MAL · Simkl/TMDB · Kitsu · Shikimori)

- **Notification Diagnostics (new):** a 🪲 action in the Notifications screen fires **real requests** at all five sources and reports connection state, endpoint, **HTTP status**, item count, duration, plus an error and a fix hint. No more guessing whether notifications actually arrive.
- **AniList — silent errors removed:** on failure the client returned an empty list (looking like "no notifications"); GraphQL errors are now surfaced with their real message. The unread notification count is fetched and shown as a tab badge.
- **Shikimori — real notification feed:** the app used to present the **watch history** as notifications. Official endpoints (`GET /api/users/:id/messages?type=notifications|news`, `/unread_messages`) are now used, with a clear in-app explanation and a safe history fallback when the `messages` scope is missing. The OAuth scope is now chosen per client id (use your own app to get `user_rates messages`).
- **Simkl — deprecated calendar path fixed (critical):** the old code read the pre-v2 files that **stop updating on 1 February 2027** and truncated results to the **first 50 items**, which is why matches almost never appeared. The new `SimklCalendarClient` reads the official `data.simkl.in/calendar/v2/…` files (`{calendar, metadata}`) and joins them with the user's `/sync/all-items/*/watching` list (simkl/tmdb/mal ID match, ±7 day window).
- **MyAnimeList — calendar-week trap:** the feed was bound to the current calendar week (empty at the start of a week) and sorted by string id. It now uses a rolling **last 7 days** window, correct ordering, and an info banner explaining that the MAL API provides no personal notifications.
- **Kitsu — next episode dates:** Kitsu's public API has no notification endpoint, but the anime resource's `nextRelease` field is now parsed into an "Episode N • aired/upcoming" feed.
- **Hardened background worker:** per-episode Simkl notifications (previously one per show), last-24-hours only; a week-boundary-free window for MAL; **new background support for Kitsu and Shikimori** with two new toggles in Settings > Notifications.
- **Full report:** `docs/audits/NOTIFICATION_INFRASTRUCTURE_AUDIT_2026-10-07.md`

---

## 🇹🇷 Türkçe (v2.4.194)

### 🛡️ Kitsu Senkronizasyon & Çoklu Kayıt Çökme Koruması (Crash Fix)

- **Kitsu ve Platform Eşitleme Çökmesi Giderildi (`IllegalArgumentException: Belirsiz kitsu eşlemesi...`):** `MediaEntryRepository.smartImport` içerisinde yer alan katı `require(matches.size <= 1)` koşulu kaldırıldı. Veritabanında aynı yapıma ait birden fazla yerel kayıt veya benzer başlık bulunduğunda uygulamanın `KitsugiCrashActivity` ile çökmesi engellendi; ID, ana başlık ve güncellik sırasına göre en uygun kaydı seçip güvenle güncelleyen akıllı çözümleyici uygulandı.
- **Franchise & Çoklu Film Başlık İzolasyonu:** `MediaIdentity.sameMedia` fonksiyonu güçlendirildi. `Date A Bullet: Dead or Bullet` ve `Date A Bullet: Nightmare or Queen` gibi aynı seriye ait yapımların yalnızca genel Japonca franchise adı (`デート・ア・バレット`) paylaşıldığı için yanlışlıkla aynı yapım sanılması engellendi.
- **Yedek Geri Yükleme ve Çapraz Eşitleme Koruması:** `MediaEntryBackup.mergeAndSyncEntries` ve `AuthViewModel.clusterEntry` adımlarındaki tüm fırlatıcı `require` kontrolleri güvenli eşleme mekanizmasıyla değiştirildi.
- **Hata Yakalama & İzolasyon Güvencesi:** `AuthViewModel` içerisindeki tüm harici platform import ve sunucu doğrulama adımları `runCatching` kalkanına alındı; bir platformda beklenmeyen bir durum oluşsa dahi diğer platformlar ve kullanıcı arayüzü kesintiye uğramadan çalışmaya devam ediyor.

### 🚀 Shikimori OAuth Girişi Kök Neden Düzeltmesi (Yönlendirme + Authorization Header Kaybı)

- **Giriş Başarısızlığının Kök Nedeni Çözüldü:** Shikimori production ortamında diğer domain'ler (örn. `shikimori.one`) `shikimori.io` adresine 301 ile yönlendiriliyor. OkHttp, host değiştiren yönlendirmelerde güvenlik nedeniyle `Authorization` header'ını otomatik SİLİYORDU. Bu yüzden token takası başarılı olup geçerli jeton elde edilmesine rağmen, hemen ardından yapılan `whoami` doğrulaması "oturumsuz" istek olarak ulaşıp `200 + null` döndürüyor ve uygulama **geçerli jetonu çöpe atarak** "jeton geçersiz görünüyor" hatasıyla kullanıcıyı yanıltıyordu (kullanıcı aynı kodla yeniden denediğinde kod tükendiği için `invalid_grant` alıyordu).
- **Başlıklar Korunan Yönlendirme Takibi:** Shikimori istemcisine (`ShikimoriApiClient`) özel bir HTTP istemci eklendi; 301/302/303/307/308 yönlendirmeleri, orijinal `Authorization` header'ı korunarak manuel takip ediliyor (en fazla 3 atlayış, RFC 7231'e uygun POST→GET dönüşümüyle). Artık hangi Shikimori domain'ine istek atılırsa atılsın oturum bilgisi kaybolmuyor.
- **Kanonik Host'a Doğrudan İstek:** Tüm API/token uçları artık production'un resmi host'u olan `shikimori.io` adresine doğrudan gider (`shikimori.one` yedek olarak korunur). Gereksiz yönlendirme atlayışı ortadan kalktı.
- **whoami Artık Girişi Düşürmüyor:** Giriş akışında (hem 1-tık deep-link hem manuel kod) yetki kodu takas edilir edilmez token derhal saklanıyor; profil bakımı (whoami, 3 denemeli) geçici olarak başarısız olsa bile oturum korunuyor ve kullanıcı kimliği ilk senkronizasyon/profil/aktarım işleminde tembel olarak çözümleniyor (`ensureShikimoriUserResolved`). Tekrar tekrar "yeniden giriş" döngüsü ve tüketilmiş kod hataları bitti.
- **Ölü Refresh Token Temizliği:** Sunucu refresh token'ı net biçimde reddediyorsa (400/401 `invalid_grant`/`invalid_client`) oturum sessizce saklanıp 401 döngüsü yerine temizleniyor ve kullanıcı yeniden girişe yönlendiriliyor.

### 📊 Kapsamlı Çapraz Eşitleme Hata Raporu ve Eşleştirme Güvenliği

- **Tam Eşitleme ve Hata Raporlama:** Eşitleme penceresindeki 500 satırlık canlı görünüm sınırından bağımsız olarak, tüm oturum boyunca gerçekleşen işlemler, API hataları, eşleştirme kararları ve platform istatistikleri eksiksiz bir rapora kaydedilir.
- **Otomatik Rapor Dosyası ve Dışa Aktarma:** Android 10+ cihazlarda raporlar otomatik olarak `İndirilenler/Kitsugi/CrossSyncReports/` altına `.txt` formatında kaydedilir. Ayrıca eşitleme penceresine eklenen "Raporu Kaydet" düğmesiyle rapor istenen herhangi bir konuma dışa aktarılabilir.
- **Sağlayıcı Kimlik Çatışması Koruması:** Aynı tür ve başlık için farklı servislerden gelen kimlikler birbiriyle çelişiyorsa (`MediaIdentity.conflictingIdentityKeys`) veya belirsiz birden fazla aday grup çözülemiyorsa kayıt diğer platformlara yazılmadan güvenlik için atlanır ve nedeni detaylarıyla rapora yazılır.
- **Hassas Veri İzolasyonu:** Oluşturulan raporda Bearer token, access/refresh token ve client secret gibi tüm hassas kimlik doğrulama anahtarları otomatik olarak maskelenir (`[REDACTED]`).

---

## 🇬🇧 English (v2.4.194)

### 🛡️ Kitsu Sync & Disambiguation Crash Fix

- **Resolved Fatal Crash in Kitsu & Multi-Platform Sync (`IllegalArgumentException: Belirsiz kitsu eşlemesi...`):** Replaced hard-failing `require(matches.size <= 1)` check in `MediaEntryRepository.smartImport` with graceful disambiguation. If multiple local records exist for an entry (or during complex imports), the system prioritizes exact provider key, exact primary title, English title, and latest update timestamp without throwing unhandled exceptions.
- **Franchise Japanese Title Bleed Prevention:** Enhanced `MediaIdentity.sameMedia` comparison so franchise movies sharing generic Japanese series titles (e.g. `Date A Bullet: Dead or Bullet` vs `Nightmare or Queen`) are not conflated as the same media when primary titles differ.
- **Safe Backup Merge & Cross-Sync Clustering:** Replaced assertion throws in `MediaEntryBackup.mergeAndSyncEntries` and `AuthViewModel.clusterEntry` with deterministic matching fallbacks.
- **Fault-Tolerant Coroutine Safety:** Guarded all platform import invocations in `AuthViewModel` with `runCatching` to prevent background coroutine cancellations from bringing down the UI or interrupting remaining platforms.

### 🚀 Shikimori OAuth Login Root-Cause Fix (Redirect + Authorization Header Loss)

- **Login Failure Root Cause Resolved:** In Shikimori production, other domains (e.g. `shikimori.one`) 301-redirect to `shikimori.io`. OkHttp automatically STRIPS the `Authorization` header on host-changing redirects for security. As a result, the token exchange succeeded and produced a valid token, but the immediately-following `whoami` verification arrived as an unauthenticated request, received `200 + null`, and the app **discarded the valid token** with a misleading "token appears invalid" error (retries with the same code then failed with `invalid_grant` because the code was already consumed).
- **Header-Preserving Redirect Following:** The Shikimori client (`ShikimoriApiClient`) now uses a dedicated HTTP client that manually follows 301/302/303/307/308 redirects while preserving the original `Authorization` header (max 3 hops, RFC 7231-compliant POST→GET conversion). The session no longer gets lost regardless of which Shikimori domain the request lands on.
- **Direct Requests to the Canonical Host:** All API/token endpoints now hit `shikimori.io` (the production canonical host) directly; `shikimori.one` is kept as a fallback. Unnecessary redirect hops are eliminated.
- **whoami No Longer Kills Login:** In both the 1-tap deep-link and manual code flows, the token is saved immediately after the authorization-code exchange. If the profile lookup (whoami, with 3 retries) temporarily fails, the session is preserved and the user identity is resolved lazily on first sync/profile/import (`ensureShikimoriUserResolved`). The repeated re-login loop and consumed-code errors are gone.
- **Dead Refresh Token Cleanup:** When the server explicitly rejects the refresh token (400/401 `invalid_grant`/`invalid_client`), the session is cleared and the user is prompted to reconnect instead of silently looping on 401s.

### 📊 Comprehensive Cross-Sync Diagnostic Report & Matching Safety

- **Full Sync & Error Reporting:** Independent of the live dialog's 500-entry limit, complete session operations, API error chains, disambiguation decisions, and platform statistics are written to an exhaustive diagnostic report.
- **Automatic Report Storage & Export:** On Android 10+, reports are automatically persisted to `Downloads/Kitsugi/CrossSyncReports/` as `.txt` files. A "Save Report" button in the cross-sync dialog also allows saving the report to any user-selected location.
- **Provider Conflict Prevention:** If entries of the same type share titles but have conflicting provider IDs (`MediaIdentity.conflictingIdentityKeys`) or cannot be safely disambiguated across multiple candidates, writing to target platforms is skipped for safety and logged to the report.
- **Credential Redaction:** Bearer headers, access/refresh tokens, and client secrets are automatically redacted (`[REDACTED]`) before reports reach disk.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 MyAnimeList Çapraz Senkronizasyon & Shikimori OAuth Doorkeeper Düzeltmesi

- **MyAnimeList Çapraz Eşitleme URL Hatası Çözüldü (`Illegal character in query at index 193`):** MAL API v2 kullanıcı listesi (`/animelist` ve `/mangalist`) isteklerindeki `fields` parametresi içinde yer alan iç içe alan seçicisi (`list_status{...}`) süslü parantezleri (`%7B` ve `%7D`) olarak URL encode edildi. Böylece `java.net.URI`'nin parantezleri reddedip çapraz eşitlemeyi %0'da durdurması tamamen engellendi.
- **Shikimori Doorkeeper Token İstek Düzeltmesi:** Token takas isteğine (`POST /oauth/token`) `redirect_uri` parametresi geri eklendi; `400 Missing required parameter: redirect_uri` hatası tamamen giderildi.
- **Dinamik Pending Redirect URI Eşleştirmesi:** Tarayıcının açıldığı yönlendirme adresi (`kitsugi://`, `urn:ietf:wg:oauth:2.0:oob` veya `aniyomi://`) hafızaya alınarak token takası sırasında sunucuya birebir aynı adres gönderilir.
- **Ham JSON Hatası Engellendi:** Shikimori'nin geçersiz oturumlarda döndürdüğü `null` yanıtının parse edilmesiyle oluşan ham `A JSONObject text must begin with '{'` hatası giderildi; tüm yanıtlar null/HTML kontrolleriyle korumaya alındı.
- **Gelişmiş Giriş Diyaloğu & Alternatif Şema:** Giriş penceresine "Shikimori OAuth Uygulamalarım" yönetim bağlantısı ve eski şemayı kullananlar için "Alternatif Tek Tık (aniyomi://)" butonu eklendi.

---

## 🇬🇧 English (v2.4.194)

### 🚀 MyAnimeList Cross-Sync Query Encoding & Shikimori OAuth Doorkeeper Fix

- **MyAnimeList Cross-Sync Query Parsing Fix (`Illegal character in query at index 193`):** Percent-encoded nested field selector braces (`%7B` and `%7D`) in MAL `/animelist` and `/mangalist` endpoints. Fixed `java.net.URI` query syntax crash that aborted cross-account sync at 0%.
- **Shikimori Doorkeeper Token Payload Fix:** Restored `redirect_uri` parameter in token exchange requests, eliminating `400 Missing required parameter: redirect_uri`.
- **Dynamic Pending Redirect URI Matching:** Pairs the authorization redirect URI (`kitsugi://`, `urn:ietf:wg:oauth:2.0:oob`, or `aniyomi://`) with the token exchange step as required by Doorkeeper OAuth.
- **Guarded JSON Parsing:** Eliminated raw `A JSONObject text must begin with '{'` exceptions when encountering `null` or non-JSON payloads from expired session endpoints.
- **Enhanced Login Dialog & Fallback Flows:** Added direct link to Shikimori OAuth management dashboard and an "Alternative 1-Tap (aniyomi://)" button in the login dialog.

---

---

- **Nuvio Libtorrent P2P Motor Entegrasyonu (libtorrent + Boost.Asio):** Nuvio'nun yüksek performanslı C++ libtorrent ve Boost.Asio çekirdeği (`lib-nuvio-engine-android-0.1.2.aar`) Kitsugi'ye entegre edildi. Artık Debrid üyeliği (RealDebrid, AllDebrid vb.) olmadan da Torrentio ve diğer torrent tabanlı Stremio eklentileri doğrudan cihaz üzerinden eşler arası (P2P) ve yüksek hızla oynatılabilir.
- **Gelişmiş Seeding & Yükleme (Upload) Kontrolleri:** Orijinal Nuvio uygulamasında bulunmayan, arka planda sınırsız çalışan gönderme (seeding) davranışı tamamen kullanıcı denetimine açıldı. Stremio eklentileri ayarlarından seed modu tek tıkla kapatılabilir ya da internet kotasını korumak için 256 KB/s, 512 KB/s, 1 MB/s, 2 MB/s hız sınırları atanabilir.
- **P2P Gizlilik & Swarm Güvenlik Bilgilendirmesi:** P2P motoru ilk kez aktif edildiğinde veya Debrid olmadan bir torrent bağlantısına tıklandığında açılan tek seferlik güvenlik diyaloğu eklendi. IP adresinin torrent swarm havuzunda görünebileceği, telif hakları sorumluluğu ve VPN tavsiyeleri net biçimde sunuldu.
- **Tam Ekran Oynatıcı Canlı Torrent Paneli (TorrentOverlay):** Video oynatıcıya bağlanan canlı gösterge paneli sayesinde anlık indirme hızı, yükleme hızı, bağlı eşler (peers), seed sayısı ve önbellek tampon durumu gerçek zamanlı takip edilebilir. Oynatıcı kapatıldığında veya bölüm değiştiğinde P2P motoru gereksiz arka plan trafiğini önlemek için otomatik sonlandırılır.
- **Simkl Toplu Eşitleme (Batch Sync) & 429 Hız Sınırı Koruması:** Simkl API'sinin saniyede 1 istek kuralına ve `title`/`year` zorunluluklarına tam uyum sağlandı. 35'lik akıllı paketler ve 1.1 saniye gecikme ile yüzlerce animenin tek seferde sorunsuz senkronize edilmesi sağlandı; senkronizasyon sonrası Room DB'ye gerçek sunucu ID'leri işlenerek öğelerin sayfa yenilenince kaybolması engellendi.
- **Simkl Medya Detay Sayfası & 404 Koruması:** Simkl kütüphanesindeki öğelerin üzerine tıklandığında oluşan "Veri Yok" ve 404 hataları giderildi; kimlik çözümleyici Simkl ID'lerini doğru yöne kanalize ederek Jikan ve Simkl arama fallback mekanizmalarıyla güçlendirildi.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Nuvio Libtorrent P2P Streaming Engine, Full Seeding & Upload Controls, Simkl Batch Sync & Media Detail Fixes

- **Nuvio Libtorrent P2P Engine Integration (libtorrent + Boost.Asio):** Embedded Nuvio's high-performance C++ libtorrent and Boost.Asio core (`lib-nuvio-engine-android-0.1.2.aar`) directly into Kitsugi. Users can now stream torrent-based Stremio add-ons (such as Torrentio) entirely for free via peer-to-peer swarms without requiring a Debrid subscription.
- **Full Seeding & Upload Bandwidth Controls:** Addressed a critical privacy and bandwidth shortcoming in the original Nuvio app (which seeded indefinitely with no UI controls). Users can now toggle seeding on/off or limit upload bandwidth to 256 KB/s, 512 KB/s, 1 MB/s, or 2 MB/s directly from the Stremio settings tab.
- **P2P Privacy & Swarm Consent Dialog:** Implemented a one-time transparent consent dialog detailing public swarm IP visibility, copyright compliance responsibilities, and VPN usage recommendations before initiating any P2P torrent stream.
- **Real-Time Video Player Torrent Overlay:** Connected fullscreen player metrics to live engine telemetry, displaying instant download speeds, upload speeds, seeders, peers, and buffering progress, with automatic stream teardown on player exit.
- **Simkl Batch Synchronization & 429 Rate-Limit Prevention:** Adhered strictly to Simkl's 1 request/second policy and mandatory title/year metadata requirements. Synchronizes large libraries in 35-item batches with 1.1s throttling, followed by server-verified Room DB persistence to eliminate phantom entries and list reverts.
- **Simkl Media Detail Navigation & 404 Fallback:** Resolved missing detail screens and 404 errors when opening Simkl entries by prioritizing Simkl external IDs and adding Jikan & Simkl title search fallback strategies.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 İzole Süreç Çökme Ekranı (:crash), Canlı Sistem Teşhisi, Çapraz Senkronizasyon & Kararlılık Güncellemeleri

- **İzole Süreç Tabanlı Çökme & Teşhis Ekranı (Isolated :crash Process):** AndroidManifest'e `:crash` ayrılmış süreci entegre edildi. Ana uygulama süreci beklenmedik bir kritik hatayla veya bellek yetersizliğiyle karşılaşsa dahi, çökme ekranı (`KitsugiCrashActivity`) tamamen izole ve temiz bir süreçte anında açılır. Uygulamanın aniden sessizce kapanması ("pat diye kapanma") önlendi.
- **Hakkında Ekranından Doğrudan Teşhis & Rapor Erişimi:** Ayarlar altındaki Hakkında (About) ekranına yeni "Çökme & Hata Teşhis Raporu" butonu eklendi. Kullanıcılar ve test ekibi istedikleri an geçmiş çökme kayıtlarını, logcat çıktılarını ve cihaz donanım teşhisini inceleyebilir veya tek tıkla panoya kopyalayabilir.
- **Çoklu Platform Çapraz Senkronizasyon (Cross-Sync) Güçlendirmesi:** AniList, MyAnimeList, Simkl, Kitsu ve Shikimori platformları arasındaki kütüphane ve izleme geçmişi senkronizasyonu geliştirildi; diyalog akışı ve durum göstergeleri optimize edildi.
- **Kitsu Bildirim & Detay Entegrasyonu:** Kitsu bildirimlerine dokunulduğunda anime ve manga detay sayfalarının kesintisiz, doğru kimlik çözümlemesiyle ve önbellekten anında kapak aktarımıyla açılması garantilendi.
- **Shikimori 1-Tık Giriş ve Güvenlik Sertleştirmesi:** Shikimori OAuth2 bağlantısında yaşanan 403 ve token çözümleme problemleri kalıcı olarak giderildi.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Isolated :crash Process Architecture, Live System Diagnostics, Cross-Sync & Core Stability

- **Isolated Process Crash & Diagnostics Screen (:crash Process):** Dedicated isolated process architecture (`:crash`) wired into AndroidManifest. Even if the primary application process experiences an unhandled fatal error or OutOfMemory condition, `KitsugiCrashActivity` launches reliably within a clean sandbox without hangs or freeze loops, preventing abrupt app disappearance.
- **Direct Diagnostics & Crash Hub Access:** Added a dedicated "Crash & Diagnostics Report" button to the About screen. Users and testers can review past crash logs, live logcat output, and device hardware diagnostics on demand, or copy full reports to clipboard in one tap.
- **Multi-Platform Cross-Sync Enhancements:** Streamlined library cross-synchronization across AniList, MyAnimeList, Simkl, Kitsu, and Shikimori, complete with dialog feedback and state reconciliation.
- **Kitsu Notification & Media Detail Navigation:** Guaranteed smooth detail page transitions when launching from Kitsu push notifications, handling both raw and offset entity identifiers with instant poster caching.
- **Shikimori 1-Tap OAuth & Authorization Polish:** Strengthened Shikimori authentication flow against 403 Forbidden edge cases and token renewal hiccups.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 Shikimori 1-Tık Otomatik Giriş Çözümü, Kitsu Bildirim Detay Sayfası Düzeltmesi & Kararlılık Güncellemeleri

- **Shikimori 1-Tık Otomatik Giriş ve Deep Link Desteği:** Shikimori OAuth girişinde yaşanan 403 erişim engelleri ve yetkisiz istemci (invalid_client) sorunları kalıcı olarak çözüldü. Resmi `aniyomi://shikimori-auth` deep link yönlendirmesi entegre edildi. Kullanıcı tarayıcıda tek dokunuşla izin verdiğinde uygulama otomatik açılır ve oturumu başlatır.
- **Alternatif Manuel Giriş ve Otomatik Sıfırlama:** Tarayıcıda doğrulama kodunu doğrudan görüntüleyen `urn:ietf:wg:oauth:2.0:oob` protokolü korundu. Gelişmiş ayarlar menüsüne "Varsayılan Anahtarları Geri Yükle" butonu eklenerek cihaz hafızasında kalmış eski/hatalı anahtarların tek tıkla temizlenmesi sağlandı.
- **Kitsu Bildirimlerinden Medya Detayına Geçiş Sorunu Giderildi:** Bildirimler ekranındaki Kitsu bildirimlerine dokunulduğunda oluşan boş ekran veya çökme problemi düzeltildi. `KitsuExploreClient` ve `KitsugiDetailClient` içerisindeki kimlik çözümleme motoru güncellenerek hem ham Kitsu ID'leri hem de ofsetli (`+300,000,000`) ID'ler eksiksiz tanınır hale getirildi.
- **Görsel Bildirim Geçişleri:** Bildirimlerden detay sayfasına giderken anime/manga başlığı ve kapak görseli önbellekten anında aktarılarak sayfa açılışında görsel süreklilik ve akıcı bir deneyim sunuldu.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Shikimori 1-Tap OAuth & Deep Link Fix, Kitsu Notification Detail Resolution & Stability Improvements

- **Shikimori 1-Tap OAuth & Deep Link Integration:** Fixed 403 Forbidden and invalid_client issues during Shikimori authentication. Configured official `aniyomi://shikimori-auth` deep link callback scheme; tapping "Allow" in browser immediately returns to Kitsugi and authenticates seamlessly.
- **Alternative Out-of-Band Auth & Credential Reset:** Preserved manual code generation via `urn:ietf:wg:oauth:2.0:oob`. Added a "Reset to Default Keys" action in advanced settings to purge any corrupted client keys from app storage.
- **Kitsu Notification Media Detail Page Fix:** Resolved an issue where tapping on Kitsu notifications resulted in empty detail screens or failed navigation. Harmonized ID offset mapping in `KitsuExploreClient` and `KitsugiDetailClient` to reliably resolve both raw numeric IDs and offset stable IDs (`+300,000,000`).
- **Seamless Notification Visual Transition:** Titles and cover posters from incoming notifications are now forwarded directly to detail screens, ensuring instantaneous visual feedback without flicker.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 Gelişmiş Arama Filtre Paneli, İzole Arama Motorları, Resmi MAL API Entegrasyonu, Shikimori 1-Tık Giriş & Modern Çökme Raporlama

- **Yeni Nesil Arama Kaynak ve Filtre Seçici Paneli (Source Engine Filter Sheet):** Arama sayfasında motorların durumunu, aktifliklerini ve sonuç sayılarını gösteren, kategorilere (Anime, Dizi, Film, Manga, Roman vb.) göre filtrelenebilen yepyeni bir modal alt panel (`SourceEngineFilterSheet`) eklendi.
- **İzole Edilmiş ve Bağımsız Arama Sağlayıcıları:** Çoklu arama motorları birbirinden tamamen izole edildi; bir sağlayıcıda gecikme veya hata olduğunda diğer motorların (AniList, Kitsu, Shikimori, TMDB, Simkl vb.) arama sonuçları kesintisiz olarak anında ekrana gelir.
- **Resmi MyAnimeList (MAL) API Entegrasyonu:** Eski veya kararsız Jikan uç noktaları yerine MyAnimeList'in resmi OAuth2 API altyapısına geçildi. Arama hızı ve kütüphane güvenilirliği en üst seviyeye çıkarıldı.
- **Shikimori 1-Tık Giriş & 403 Forbidden Çözümü:** Shikimori OAuth girişinde yaşanan 403 erişim engeli ve token sorunları giderildi. Tek tıkla tarayıcı yetkilendirmesiyle sorunsuz profil bağlantısı sağlandı.
- **Kitsu & Shikimori Kapsamlı Profil ve Liste Yönetimi:** Profil ekranında Kitsu ve Shikimori hesapları için kütüphane durumları (İzleniyor/Okunuyor, Tamamlandı, Beklemede, Bırakıldı, Planlandı), puan dağılımı, favoriler ve biyografi eksiksiz görüntülenebilir ve güncellenebilir hale getirildi.
- **Gelişmiş Teşhis ve Çökme Raporlama Ekranı (KitsugiCrashActivity & Logger):** Uygulama beklenmeyen bir hatayla karşılaştığında kapanmak yerine modern bir arayüzle hatanın detayını, cihaz donanım bilgilerini ve bellek durumunu gösterir; logları tek tıkla kopyalama veya uygulamayı güvenle yeniden başlatma imkanı sunar.
- **Ayarlar Ekranı Modüler Hesap Sayfaları:** Ayarlar menüsündeki hesap yönetimi ayrıştırılarak AniList, MAL, Simkl, Kitsu ve Shikimori için özel alt sayfalar (`AccountSettingsSubPages`) oluşturuldu.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Advanced Search Filter Sheet, Isolated Search Engines, Official MAL API, Shikimori 1-Tap OAuth & Modern Crash Activity

- **Next-Gen Source Engine Filter Bottom Sheet:** Introduced a comprehensive modal bottom sheet (`SourceEngineFilterSheet`) on the Search screen with per-source toggles, status indicators, and categories (Anime, Movies, TV Series, Manga, Light Novels).
- **Isolated Multi-Search Provider Pipeline:** Search engines are now completely isolated; network timeouts or throttles on one provider no longer affect parallel search results from others (AniList, Kitsu, Shikimori, TMDB, Simkl).
- **Official MyAnimeList (MAL) API Migration:** Fully migrated search and library operations to official MyAnimeList OAuth2 endpoints, eliminating third-party rate limits and improving result speed.
- **Shikimori 1-Tap OAuth & 403 Forbidden Fix:** Fixed 403 Forbidden errors during Shikimori authorization, enabling an effortless 1-tap browser auth flow.
- **Kitsu & Shikimori Full Profile & Library Management:** Enriched profile screens with full status tracking (Watching/Reading, Completed, On Hold, Dropped, Plan to Watch/Read), score charts, favorites, and profile updates.
- **Advanced Diagnostics & Modern Crash Screen (KitsugiCrashActivity & Logger):** Replaced default unhandled exception crashes with a modern diagnostics screen showing breadcrumbs, system specifications, memory status, stacktrace copying, and 1-tap safe app restart.
- **Modular Account Settings Subpages:** Restructured account management into modular subpages (`AccountSettingsSubPages`) for AniList, MAL, Simkl, Kitsu, and Shikimori with clear connection status and sync actions.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 Zenginleştirilmiş Sistem Bildirimleri, Kitsu & Shikimori Profilleri, Profil Kaynak Seçici Paneli ve Orijinal Logolar

- **Zengin ve Görsel Sistem Bildirimleri (Drawer & Status Bar):** Android bildirim çekmecesi bildirimleri artık çok daha ayrıntılı ve yüksek çözünürlüklü kapak/avatar görselleriyle (`LargeIcon` & `BigPictureStyle`) donatıldı. Bildirimler artık hangi kaynaktan geldiğini açıkça belirtir (`[AniList]`, `[MyAnimeList]`, `[Simkl]`). Sistem çekmecesi bildirimleri ile uygulama içi bildirim ekranı metin ve biçimlendirme açısından birebir senkronize edildi.
- **Profil Sayfası Açılır Kaynak Seçim Paneli (Profile Source Picker Sheet):** Arama ve Keşfet sayfalarında olduğu gibi profil sayfasına da açılır seçim paneli (`ProfileSourcePickerSheet`) ve orijinal logolu hap butonu eklendi. Tek dokunuşla tüm bağlı/bağlantısız profiller arasında geçiş yapılabilir.
- **Kitsu ve Shikimori Profil Desteği Tamamlandı:** Profil sayfasına eksik olan Kitsu ve Shikimori sekmeleri eklendi. Kullanıcı biyografisi, waifu/husbando, takipçi verileri, anime/manga durum istatistikleri ve kütüphane kayıtları eksiksiz görüntülenir.
- **Orijinal Platform Logoları (Sıfır Emoji Kuralı):** Tüm sayfalardaki kaynak butonları, seçim panelleri ve sekmelerdeki emojiler veya boş kutular kaldırılarak yerini yüksek kaliteli orijinal platform logoları (`KitsugiPlatformLogo`) aldı.
- **Yeni GitHub Hesabı & Depoları Entegrasyonu:** Kod tabanı yeni `f26901964-eng/Kitsugi-Beta` ve `f26901964-eng/Kitsugi-Plugins` depolarına uyarlandı. Eklenti deposundan tüm +18 içerikler temizlenerek güvenli havuz devreye alındı.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Rich System Notifications, Kitsu & Shikimori Profiles, Source Picker Sheet & Original Logos

- **Rich Visual System Drawer Notifications:** Android status bar and drawer notifications now include high-resolution poster artwork and avatars via `LargeIcon` and `BigPictureStyle`. Every notification clearly highlights its origin source (`[AniList]`, `[MyAnimeList]`, `[Simkl]`), completely harmonized with the in-app notification center.
- **Profile Source Picker Bottom Sheet:** Added a modal selection sheet and original logo pill button to the Profile screen, matching the Search and Explore user experience.
- **Kitsu & Shikimori Full Profile Integration:** The Profile screen now fully supports Kitsu and Shikimori tabs, displaying bio, social metrics, anime/manga status breakdown charts, score distributions, and library records.
- **Original Platform Logos (Strictly No Emojis):** Replaced all emoji badges and generic colored boxes across buttons, sheets, and tabs with authentic, high-res platform vector logos (`KitsugiPlatformLogo`).
- **New GitHub Ecosystem Integration:** Fully wired the application and plugin repositories to the new `f26901964-eng` GitHub account, with an adult-free safe plugins pool and updated in-app update mechanisms.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 Kitsu Düzeltmeleri, Grid/Liste Hatırlama, Yapışkan Bar Filtresi, TMDB İngilizce Fallback, Simkl Dizi/Film Keşfeti ve Çökme Korumaları

- **Grid / Liste Görünümü Kalıcı Olarak Hatırlanıyor:** Popüler, Trend vb. tam ekran medya sayfalarındaki liste veya grid görünüm seçimi artık kaydedilir; sayfadan çıkıp tekrar girildiğinde kullanıcının son seçtiği görünüm modu korunur.
- **Yapışkan Üst Çubukta Filtre Butonu Düzeltildi:** Sayfa aşağı kaydırıldığında beliren kompakt üst çubukta filtre butonunun kaybolması sorunu çözüldü; filtreleme ve sıralama butonu artık her kategoride ve kaydırma durumunda görünür durumdadır.
- **TMDB Keşfet'te Türkçe Başlığı Olmayan İçerikler İçin Otomatik İngilizce Desteği:** TMDB'den çekilen içeriklerde Türkçe başlık bulunmadığında Japonca/Kanji başlık yerine otomatik olarak İngilizce başlık (örn. "Souryo to Majiwaru Shikiyoku no Yoru ni...", "Puella Magi Madoka Magica...") gösterilir.
- **Simkl Keşfet'e Dizi ve Film Desteği Eklendi:** Simkl platformu artık sadece animelerle kısıtlı kalmayıp TMDB gibi Trend Diziler, Trend Filmler, Popüler Diziler, Popüler Filmler ve en yüksek puanlı içerikleri eksiksiz gösterir.
- **Kitsu Anime Ekleme / Düzenleme Sayfası Düzeltildi:** Kitsu üzerinden açılan animelerde "Listeye Ekle / Düzenle" alt sayfasının MyAnimeList olarak açılması ve MAL alanlarının görünmesi sorunu çözüldü. Artık Kitsu renkleri (turuncu), Kitsu kimlik etiketi ve Kitsu'ya özgü liste durumu seçenekleri açılır.
- **Kitsu Karakterler Sekmesi Takılma / Yüklenmeme Sorunu Giderildi:** Anime detay sayfasında "Resimler" sekmesi eklendikten sonra meydana gelen indeks kayması giderildi; "Karakterler" sekmesine tıklandığında artık iskelet ekranda takılı kalmadan tüm karakterler eksiksiz yüklenir.
- **Kitsu Kişi Bilgisi ve Galeri Kaynak Çelişkisi Düzeltildi:** Kitsu'dan açılan yönetmen/seslendirmen/ekip detayında "ANILIST" yerine doğru platform ("Kitsu") etiketi gösterilir; profil fotoğrafı tam ekran açıldığında da "Jikan" ve "🎭 Karakter" yerine doğru kaynak ve "👤 Kişi" kategorisi kullanılır. Kitsu ID ile kişi adı önce doğrudan Kitsu API'den çözümlenerek doğru eşleştirme sağlanır.
- **Rastgele Uygulama Çökmeleri Engellendi (Crash Shield & Looper Koruması):** Sayfa geçişlerinde, video izlerken veya eklentilerden veri gelmesini beklerken arka plan iş parçacıklarında, ağ zaman aşımlarında ya da harici eklenti kodlarında oluşan beklenmedik istisnaların uygulamayı aniden kapatması engellendi. Ana iş parçacığı döngü koruyucusu (Cockroach Looper Recovery) ve `SupervisorJob` takviyesiyle donatıldı.
- **Eklenti Deposu (Kitsugi Plugins) Sorunsuz Yükleme Garantisi:** "Kitsugi Eklentileri (Önerilen)" deposu eklenirken yaşanan ISS engelleri ve ağ zaman aşımları tamamen çözüldü. Hem doğrudan Codeberg bağlantısı hem REST API hem de dahili çevrimdışı katalog yedeği devreye alındı.
- **Shikimori Giriş Akışı Düzeltildi:** "The requested redirect uri is malformed" hatası giderilerek RFC 6749 uyumlu URL-encode OAuth2 giriş akışı sağlandı.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Grid Persistence, Sticky Filter Button, TMDB English Fallback, Simkl TV/Movies & Crash Shield

- **Grid / List Mode Persistent Memory:** The layout toggle preference on full-screen media grid pages is now saved and remembered across visits.
- **Sticky Top Bar Filter Button Fix:** Fixed the filter and sorting button disappearing from the compact sticky header upon scrolling down; it is now always visible and positioned properly next to the layout toggle.
- **TMDB Explore English Title Fallback:** When Turkish titles are not localized on TMDB, titles automatically fall back to their English/Romaji counterparts instead of Japanese Kanji/Kana.
- **Simkl Explore TV & Movie Expansion:** Simkl explore is no longer limited to anime; it now features full TV show and movie sections (trending, popular, top-rated) matching the TMDB experience.
- **Kitsu Media Editor Sheet Fix:** Fixed Kitsu anime entries opening the edit sheet with "MyAnimeList" branding and MAL status fields. Proper Kitsu branding, theme color, and sync properties are now respected.
- **Kitsu Characters Tab Loading Fix:** Resolved tab index offset bug causing the Characters tab to freeze with skeleton loaders indefinitely; characters now load immediately.
- **Person / Staff Detail & Gallery Attribution Fix:** Resolved source attribution conflicts when opening staff from Kitsu (showing "ANILIST" badge or "Jikan / Character" in fullscreen gallery). Now correctly attributes Kitsu and "Person" category, with pre-resolution against Kitsu's `/people` API.
- **Random Crash Shield & Main Looper Protection:** Prevented random crashes during navigation, video playback, and extension scraping. Installed a resilient main Looper recovery loop and `SupervisorJob` scopes across extension loaders and prefetch coroutines to isolate non-fatal exceptions.
- **Plugin Repository Bulletproof Loading:** Resolved repository manifest loading failures caused by ISP blocks with Codeberg API fallback and bundled 174-plugin offline catalog.
- **Shikimori OAuth Redirect URI Fix:** Fixed "The requested redirect uri is malformed" error with proper RFC 6749 URL encoding and streamlined OAuth flow.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 Keşfet Arayüzü, "Tümü" Butonu, Kitsu & Shikimori Girişleri, Bildirim Önizleme ve Çapraz Eşitleme

- **Keşfet Sayfasında Çift Bar Fazlalığı Giderildi:** Keşfet sayfasında hem açılır seçim menüsü hem de alttaki yatay çip çubuğunun oluşturduğu karmaşa giderildi; gereksiz yatay çip barı kaldırılarak tekil, şık ve fonksiyonel kaynak seçici korundu.
- **Listem Sayfası "Tümü" Butonu Alt Bar ile Uyumlu Hale Getirildi:** "Tümü" kategori seçim butonu artık alt gezinti çubuğunun (Bottom Navigation Bar) üzerinde taşma veya örtüşme yapmaz. Diğer sayfalardaki FAB butonlarıyla birebir aynı biçimde, alt çubuğun tam üzerinde hizalanır ve kaydırma hareketleriyle pürüzsüzce animasyonlanır.
- **Kitsu Giriş Hatası ("Client authentication failed") Çözüldü:** Kitsu API OAuth2 token uç noktasına yönelik istemci anahtarları ve OAuth2 standart form-urlencoded yapısı güncellendi. Kullanıcılar artık e-posta/kullanıcı adı ve şifreleriyle doğrudan ve hatasız giriş yapabilir.
- **Shikimori Giriş Akışı Sadeleştirildi:** Kullanıcıdan karmaşık Client ID, Client Secret oluşturması isteme zorunluluğu kaldırıldı. Dahili kimlik bilgileriyle tek dokunuşla tarayıcıda Shikimori onay sayfası açılır, ekrandaki kod yapıştırılarak anında bağlantı kurulur.
- **AniList Bildirimlerine Tıklayınca Doğrudan Aktivite/Beğeni Detayı Açma:** Beğeni veya yorum bildirimlerine tıklandığında yalnızca körü körüne kullanıcı profiline gitmek yerine, tam olarak hangi paylaşımın/içeriğin beğenildiği veya yorumlandığını gösteren detay sayfası ve ilgili anime/dizi bağlantıları açılır.
- **Tüm Hesapları Birbiriyle Eşitle (5 Platform Çapraz Senkronizasyon) Düzeltildi:** AniList, MyAnimeList, Simkl, Kitsu ve Shikimori hesapları arasında anime, dizi ve film içerikleri eksiksiz olarak birbirine eşitlenir; her bağlı platformun kütüphanesindeki eksik içerikler tespit edilip hem yerel listelere hem de ilgili servislere sorunsuz aktarılır.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Explore UI, "All" FAB, Kitsu & Shikimori Auth, Activity Notifications & Cross-Sync Matrix

- **Explore Top Bar Deduplication:** Removed the redundant horizontal chip row below the platform selector pill on the Explore screen, keeping a clean and unified interface.
- **MyList "All" FAB Bottom Bar Alignment:** The category button now sits properly above the Material3 navigation bar without overlapping, animating smoothly on scroll just like floating action buttons on other screens.
- **Kitsu Authentication Fix:** Resolved the "Client authentication failed" error by adopting official Kitsu public OAuth2 credentials and urlencoded form specifications.
- **Simplified Shikimori OAuth Flow:** Users no longer need to create developer applications or enter client credentials. A 1-tap browser authorization with built-in keys allows pasting the code directly.
- **Notification Activity Preview:** Clicking activity or like notifications now opens the exact activity/post modal showing what was liked/commented with direct media links, instead of merely navigating to the user profile.
- **Full Cross-Platform Synchronization Matrix:** The "Sync All Accounts" feature now equalizes anime, TV shows, and movies across all connected accounts (AniList, MAL, Simkl, Kitsu, Shikimori), properly updating local tabs and cross-syncing missing library items.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 Eklenti Deposu ve Eklenti Yükleme Sorunları Tamamen Giderildi

- **Codeberg Depo ve Eklenti Yüklenememe Sorunu Çözüldü:** Repo URL doğrulayıcı (`RepoVerifier`), `codeberg.org` alan adını artık varsayılan olarak güvenilir listeye aldı. Ek olarak `.git`, depo kök dizini veya eski GitHub bağlantıları otomatik olarak optimize edilmiş Codeberg manifestine yönlendirildi.
- **"Eklentiler Yüklenemedi" Hatası & Otomatik Yenileme:** Ağ gecikmesi veya ilk açılışta başarısız olan depo isteklerinde eklenti listesinin takılı kalması engellendi; otomatik yeniden deneme ve akıllı önbellek yenileme mekanizması devreye alındı.
- **Ağ Zaman Aşımı & Proxy Desteği:** Yavaş bağlantılarda ve ISS kısıtlamalarında eklenti ve repo indirmelerinin zaman aşımına uğramaması için okuma/bağlantı süreleri 30 saniyeye çıkarıldı, jsDelivr CDN yedeği güçlendirildi.
- **Eski ve Bozuk Depoların Temizliği:** Veritabanındaki eski/çift veya hatalı depo adresleri açılışta otomatik olarak temizlenerek resmi `Kitsugi Plugins (Önerilen)` deposu altında birleştirildi.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Extension Repository & Plugin Loading Fixes

- **Codeberg Domain Trust & Repo Normalization:** `codeberg.org` is now unconditionally trusted in `RepoVerifier`. All repository formats (root URLs, `.git`, legacy GitHub links) now seamlessly normalize to canonical Codeberg endpoints.
- **Automatic Retry for Extension Listings:** Fixed an issue where temporary network failures left repo cards stuck on "Eklentiler yüklenemedi". Repos now auto-retry upon opening or refreshing.
- **Extended Timeouts & CDN Fallback:** Connect and read timeouts increased to 30s to prevent dropouts on throttled networks.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 Eklenti Kurulum Hatası Giderildi & Codeberg Eklenti Havuzu Canlıya Alındı

- **Eklenti Kurulumu ve İndirme Hatası Tamamen Çözüldü:** Yeni Codeberg `KitsugiPlugins` deposundaki tüm 174 eklenti (DiziPal, RecTV, FilmMakinesi, InatBox, TurkAnime vb.) artık sorunsuz, doğrudan ve tek tıkla kurulmaktadır. Dosya bütünlüğü ZIP doğrulamasıyla garanti altına alınmıştır.
- **Doğrudan Codeberg URL Koruması:** Eklenti indirme ve repo yenileme sırasında Codeberg URL'lerinin bozulması engellenmiş, eski GitHub bağlantıları otomatik olarak yeni depoya yönlendirilmiştir.
- **Sürüm Güncellemesi:** Önceki derlemelerdeki önbellek çakışmalarını gidermek amacıyla v2.4.194 olarak paketlenmiştir.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Plugin Installation Error Fixed & Codeberg Plugin Pool Live

- **Extension Download & Installation Completely Fixed:** All 174 plugins (DiziPal, RecTV, FilmMakinesi, InatBox, TurkAnime, etc.) in the new Codeberg `KitsugiPlugins` repository now install seamlessly with 1-click installation.
- **Direct Codeberg URL Preservation:** Plugin download URLs are safely preserved and legacy GitHub endpoints are automatically routed to the new repository.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 Codeberg Entegrasyonu & Eklenti Havuzu Güvenliği (+18 Temizlendi)

- **Codeberg Otomatik Güncelleme Entegrasyonu:** Uygulama içi otomatik güncelleyici (`KitsugiUpdateRepository`) artık birincil güncelleme kaynağı olarak Codeberg Releases API'sini (`BlackDamage/Kitsugi-Beta`) kullanır. Olası bir durumda GitHub API'sine yedek (fallback) olarak bağlanır.
- **Güvenli ve Temiz Eklenti Deposu (+18 / NSFW Kaldırıldı):** Eklenti deposu GitHub'dan Codeberg'e (`BlackDamage/Kitsugi-Plugins`) taşındı. Topluluk ve mağaza kurallarına tam uyum için tüm yetişkin (+18/NSFW), ifşa ve deepfake eklentileri havuzdan kalıcı olarak temizlendi; 170+ popüler dizi, film, anime ve belgesel eklentisi (DiziPal, RecTV, FilmMakinesi, InatBox, TurkAnime vb.) optimize edilerek korundu.
- **Eski Depo URL'lerini Otomatik Yönlendirme:** Uygulama açılışında ve eklenti taramasında eski GitHub bağlantıları (`gameras1010-afk`, `KitsugiBeta-dev`, `Kekik-cloudstream`) otomatik olarak yeni Codeberg deposuna yönlendirilir.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Codeberg Migration & Clean Plugin Repository (+18 Excluded)

- **Codeberg In-App Auto-Updates:** Primary update checking and release asset downloading has been migrated to the Codeberg Releases API (`BlackDamage/Kitsugi-Beta`) with GitHub fallback.
- **Clean & Safe Plugin Repository (+18 Excluded):** Migrated cloudstream plugins to Codeberg (`BlackDamage/Kitsugi-Plugins`), completely removing 116+ NSFW / adult / deepfake plugins to guarantee longevity and platform TOS compliance. All 170+ mainstream movie, anime, TV, and documentary sources remain fully available and updated.
- **Automatic Legacy URL Redirection:** Legacy GitHub URLs for plugins are seamlessly redirected to the new Codeberg repository.

---

## 🇹🇷 Türkçe (v2.4.194)

### 📚 Türkçe Manga & Webtoon Altyapısı Kökten Yenilendi (Keiyoushi V2, 77 TR Kaynağı, MangaDex 3.450 TR Başlık, Hotlink Çözümü)

- **Keiyoushi V2 Repo Şeması & Decoy Koruması:** Keiyoushi'nin modern `index.json` V2 şeması (`extensionList.extensions[]`) tam olarak desteklendi. 2 sahte kayıtlı decoy `index.min.json` otomatik bypass edilerek doğrudan `index.json` üzerinden tüm eklentiler listelenir hale getirildi. 404 veren ölü katalog URL'leri temizlendi.
- **2026 Keiyoushi İmza Anahtarı:** Yeni SHA-256 parmak izi (`9add655a...`) `TrustManager`'a eklendi ve repodan gelen eklentiler için dinamik güven zinciri entegre edildi.
- **Modern Eklenti API'sine Geçiş (extensionLib 1.6+):** 77 Türkçe eklentinin 71'inde detay ve bölüm listelerinin boş gelmesine sebep olan eski deprecated API çağrıları kaldırıldı; modern `getMangaUpdate` suspend API'sine geçildi.
- **MangaDex & Global Katalog Aramaya Dahil Edildi (3.450 TR Başlık):** `MangaSearchCoordinator` filtresindeki mantıksal engel kaldırılarak MangaDex gibi çok dilli global kaynaklar arama adaylarına eklendi. TR kaynaklarda bulunamayan mangalar MangaDex'teki 3.450 Türkçe çeviri üzerinden anında bulunabilir hale geldi.
- **Sayfa Görsellerinde Hotlink Koruması Çözüldü:** Manga-TR, TRManga, WebtoonHattı gibi hotlink korumalı CDN'lerin 403 Forbidden dönmesi engellendi. `MangaPageLoaderV2`, görselleri kaynağın kendi `Referer`, özel User-Agent ve Cloudflare interceptor'ları ile indirip doğrudan disk önbelleğine yazar ve Telephoto okuyucuya sunar.
- **Arama Eşzamanlılık Kapısı (Semaphore 6) & Akıllı Zaman Aşımı:** Aynı anda 70+ kaynağa istek atıp 429 veya bağlantı kopması yaşanmasını engellemek için 6'lı eşzamanlılık kapısı eklendi. Yeni nesil Keiyoushi motoru tanınarak zaman aşımı 25 saniyeye optimize edildi.
- **İki Geçişli Arama & Doujinshi Filtresi:** Arama sonuçlarının ilk turda gereksiz doujinshi veya spin-off'larla dolması engellendi. Önce sıkı skorlama yapılır, sonuç yoksa gevşek skorlama devreye girerek doğru manga ilk sıraya yerleştirilir.
- **Kaynak Sağlık Sınıflandırması İyileştirildi:** Arama teriminin bulunamaması (404) veya geçici DNS hataları ("unable to resolve host") artık kaynakları 2 saatlik "Broken" cezasına sokmaz; "Degraded" seviyesinde tutularak kaynakların kaybolması engellendi.
- **Eksik Host Shim Sınıfları Eklendi:** Eklentilerin ihtiyaç duyduğu `AppInfo`, `UnmeteredSource`, `RateLimitInterceptor` gibi sınıflar shim katmanına eklenerek sınıf yükleme çökmeleri giderildi.

---

## 🇬🇧 English (v2.4.194)

### 📚 Turkish Manga & Webtoon Engine Complete Overhaul (Keiyoushi V2, 77 TR Sources, MangaDex 3,450 TR Titles, Hotlink Bypass)

- **Keiyoushi V2 Schema & Decoy Bypass:** Added full support for the modern Keiyoushi V2 `index.json` schema (`extensionList.extensions[]`). Automatically bypasses the decoy 2-entry `index.min.json` and loads extensions directly from `index.json`. Removed dead 404 catalog URLs.
- **2026 Signing Key Trust:** Added the latest Keiyoushi SHA-256 fingerprint (`9add655a...`) to `TrustManager` and established dynamic signature trust for repository extensions.
- **Modern Extension API (extensionLib 1.6+):** Migrated from deprecated `fetchMangaDetails`/`fetchChapterList` stub methods to the modern `getMangaUpdate` suspend API, restoring detail and chapter lists across 71+ Turkish extensions.
- **MangaDex & Global Catalogs in Search (3,450 TR Titles):** Fixed a boolean logic barrier in `MangaSearchCoordinator`, enabling MangaDex and trusted global catalogs to participate in manga searches alongside Turkish sources.
- **Hotlink Protection Bypass in Image Loader:** Resolved 403 Forbidden errors on hotlink-protected CDNs (Manga-TR, TRManga, WebtoonHattı). `MangaPageLoaderV2` now prioritizes the extension's native `source.getImage()` pipeline with correct `Referer`, User-Agent, and Cloudflare cookies directly into disk cache for the Telephoto reader.
- **Concurrency Gate (Semaphore 6) & Smart Timeouts:** Prevented network congestion and 429 rate limits by bounding concurrent searches to 6 parallel requests. Added modern `KEI_SOURCE` engine detection and increased timeout to 25s for stable scraping on mobile networks.
- **Two-Pass Search Scoring & Doujinshi Guard:** Searches now execute a strict first-pass match before relaxing filters, preventing doujinshi and loose spinoffs from polluting main search results.
- **Softened Health Classifier:** Network DNS errors ("unable to resolve host") and search 404s now mark sources as Degraded rather than Broken, preventing false 2-hour cooldown lockouts.
- **Missing Extension Host Shims:** Added missing `AppInfo`, `UnmeteredSource`, and `RateLimitInterceptor` host shims to prevent ClassNotFound exceptions when loading extensions.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🎭 TMDB Kurgusal Karakter Detayları, Seslendirmen Ayrımı, AL Arama ve Kesintisiz Keşfet (Kitsu Fallback)

- **TMDB Kurgusal Karakter Detay Sayfası Düzeltildi:** TMDB yapımlarındaki anime/kurgusal karakterlere (ör. Nobita Nobi, Doraemon, Rudeus Greyrat) tıklandığında seslendirmenin (ör. Megumi Oohara, Wasabi Mizuta) biyografi ve doğum günü yerine doğrudan karakterin kurgusal biyografisi, anime rolleri ve tüm seslendirmenlerinin listelendiği sayfa açılacak şekilde düzeltildi.
- **Karakter Görsel Karışıklığı Kökten Çözüldü:** Mushoku Tensei gibi aynı soyadını ("Greyrat") taşıyan karakterlerin (Rudeus, Former Self, Eris) ve rol varyantlarının yanlış token eşleşmesiyle aynı görseli alması engellendi. Öz isim doğrulaması (given-name guard) ve varyant filtreleri eklendi.
- **AniList (AL) Arama Sorunu Giderildi:** AniList GraphQL sorgusunda eksik olan `$isLicensed: Boolean` tanımı eklenerek sunucudan HTTP 400 Bad Request dönmesi ve aramalarda AL satırının kaybolması düzeltildi.
- **AniList Keşfet Sayfası & Kitsu Otomatik Fallback:** AniList keşfet sayfasındaki genel erişim iyileştirildi; sunucu hatası, hız sınırı veya boş liste durumlarında Kitsu keşfet motorunun otomatik devreye girerek listeleri eksiksiz doldurması sağlandı.

---

## 🇬🇧 English (v2.4.194)

### 🎭 TMDB Fictional Character Details, Voice Actor Separation, AL Search & Seamless Kitsu Fallback

- **Fictional Character Resolution in TMDB:** Tapping anime characters in TMDB entries now reliably opens the character's fictional profile (biography, appearances, and voice actors) via AniList/Jikan rather than displaying the real voice actor's personal biography and birthday.
- **Character Image Pollution Fixed:** Resolved an issue where characters with shared family surnames (e.g., Greyrat in Mushoku Tensei) or role variants ("Former Self") mistakenly received the same image. A robust given-name guard and variant modifier matching algorithm ensures exact individual character mapping.
- **AniList Search Restored:** Fixed an HTTP 400 Bad Request caused by a missing `$isLicensed: Boolean` definition in the AniList GraphQL search query. AniList results now reliably appear in both "All" and "Anime" search tabs.
- **AniList Explore & Seamless Kitsu Fallback:** Restored public AniList explore browsing without token requirements. Whenever AniList returns empty data or encounters issues, Kitsu automatically and seamlessly populates the explore carousels.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🎬 TMDB Film/Dizi ID Eşleme Düzeltmesi & Yorumlardaki Resim Büyüme Sorunu Çözüldü

- **TMDB Anime Filmleri Diziyle Karışma Sorunu Düzeltildi:** TMDB keşfet sayfalarında "Howl's Moving Castle" (Yürüyen Şato) gibi anime filmlerine tıklandığında film yerine aynı TMDB ID numarasına sahip "Roar" adlı TV dizisinin açılması sorunu giderildi. TMDB anime filmlerinin türü doğru şekilde `MediaType.Movie` olarak belirlendi, başlık doğrulaması eklendi ve önbellek film/dizi ayrımıyla güçlendirildi.
- **Yorumlar & Tartışmalardaki Resimlerin Yavaşça Büyümesi Düzeltildi:** Konu detayı ve yorumlarda paylaşılan görsellerin ve GIF'lerin sayfa açıldıkça veya render edildikçe yavaşça kendi kendine büyüyüp genişlemesi (Compose `animateContentSize` döngüsü) durduruldu. Görseller artık anında sabit orantıyla yüklenir, maksimum yükseklik sınırı (240dp) ile kart düzenini bozmaz ve tıklandığında tam ekran galeri açılır.
- **İndirme Ayarları Veri Yönetiminden Ayrıldı:** Veri & Yedekleme içindeki "İndirmeler" sekmesi oradan çıkarıldı; Ayarlar menüsünde doğrudan "İndirme Ayarları" bağımsız bir sayfa olarak yer aldı. Veri & Yedekleme ekranı sadeleştirildi.
- **İndirmeler Ekranı Sağa/Sola Kaydırılabilir Yapıldı:** İndirmeler sayfasındaki Videolar, Altyazılar ve Resimler sekmeleri arasında parmakla sağa ve sola kaydırarak (swipe gesture) geçiş desteği eklendi.
- **Arama Sayfası Yukarı Kaydırma Butonu & Alt Bar Senkronizasyonu:** Arama sayfasında aşağı kaydırırken alt bar ile yukarı kaydırma butonunun (FAB) üst üste binmesi sorunu giderildi. Buton alt barın durumuna göre dinamik olarak barın üzerinde konumlanır, butona tıklandığında alt bar anında geri gelir ve sayfa tepeye ulaştığında alt barın görünür kalması sağlandı.

---

## 🇬🇧 English (v2.4.194)

### 🎬 TMDB Movie/TV ID Mapping Fix, Comment Image Auto-Expansion Resolved & Downloads Navigation

- **TMDB Anime Movie/TV Confusion Resolved:** Fixed an issue where clicking anime movies on TMDB explore (such as *Howl's Moving Castle*) opened a TV show (*Roar*) with the same TMDB ID. Anime movies are now accurately typed as `MediaType.Movie`, verified by title matching, and differentiated in Room cache.
- **Forum & Comment Image Auto-Growing Fixed:** Fixed the issue where inline images and GIFs in discussion threads and comments slowly expanded on their own due to Compose `animateContentSize` layout passes. Inline images now display immediately with fixed bounds (max 240dp height), preserving clean comment layouts.
- **Dedicated Download Settings Subpage:** Removed the download settings tab from inside "Data & Backup". Created a clean, standalone "Download Settings" item in the Settings menu for quick access.
- **Horizontal Swipe for Downloads Screen:** The Downloads screen (Videos, Subtitles, Images) is now horizontally swipeable with smooth paging gestures.
- **Search Screen Scroll-to-Top & Bottom Bar Sync:** Fixed the overlap between the scroll-to-top floating button and the bottom navigation bar on the Search screen. The button dynamically floats above the bottom bar, and tapping it immediately restores the bottom bar while scrolling to the top.
