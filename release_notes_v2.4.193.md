## 🔍 Arama Deneyimi (UX) Yenilemesi, "Tümünü Gör" Ayrık Sayfa & Shikimori 1-Tık OAuth Düzeltmesi (v2.4.193) (TR)

### Yeni Özellikler & Düzeltmeler

1. **Arama Sayfası Sadeleştirmesi:**
   - Arama çubuğunun altındaki kategori çipleri (Tümü/Anime/Manga vb.), sıralama seçici, "Filtreler" ve "Tüm Seçenekler" ana sayfadan kaldırıldı.
   - Tüm bu ayarlar sağ üstteki filtre butonunun (`🎛️`) açtığı `SourceEngineFilterSheet` içinde toplandı.
   - Varsayılandan farklı filtre/kapsam aktifken tek satırlık kompakt özet çipi ("🎛️ 🌐 Tümü • ⚡ Anime • 2 filtre — Düzenle") görünür.

2. **"Tümünü Gör" Artık Ayrık Kaynak Sayfası (`SourceSearchPage`):**
   - Çoklu arama raflarındaki "Tümünü Gör" butonu artık sekme değiştirmek yerine geri butonu + başlık içeren bağımsız bir sayfayı açıyor.
   - Bu sayfa izole `SearchViewModel` kullanır; buradaki filtre değişiklikleri ana çoklu arama durumunu bozmaz.

3. **Shikimori 1-Tık OAuth Callback Düzeltmesi:**
   - **Kök neden:** Varsayılan 1-tık akışı `kitsugi://shikimori-auth` gönderiyordu; paylaşılan OAuth uygulaması `aniyomi://shikimori-auth` ile kayıtlı.
   - `DEEP_LINK_REDIRECT_URI` → `aniyomi://shikimori-auth` (varsayılan/paylaşılan akış)
   - `FALLBACK_DEEP_LINK_REDIRECT_URI` → `kitsugi://shikimori-auth` (özel kayıtlı uygulama için)
   - Token değişiminde aynı `redirect_uri` gönderilmesi sağlandı.
   - Regresyon birim testleri (`ShikimoriOAuthTest`) başarıyla geçti ✅

---

## 🔍 Search UX Overhaul, Dedicated "See All" Screen & Shikimori 1-Tap OAuth Fix (v2.4.193) (EN)

### New Features & Fixes

1. **Clean Search Screen:**
   - Removed cluttered category chips, sort picker, filter rows and platform info banners from under the search bar.
   - Consolidated all into `SourceEngineFilterSheet` (top-right filter button `🎛️`).
   - Compact 1-line summary chip appears only when non-default filters are active.

2. **Dedicated "See All" Source Screen (`SourceSearchPage`):**
   - "See All" from multi-search shelves opens a full standalone screen instead of switching tabs.
   - Uses an isolated `SearchViewModel` — filter changes here don't affect the main multi-search state.

3. **Shikimori 1-Tap OAuth Callback Fix:**
   - **Root cause:** Default 1-tap flow sent `kitsugi://shikimori-auth`; the shared OAuth app is registered with `aniyomi://shikimori-auth`.
   - `DEEP_LINK_REDIRECT_URI` → `aniyomi://shikimori-auth` (primary, matches shared app registration)
   - `FALLBACK_DEEP_LINK_REDIRECT_URI` → `kitsugi://shikimori-auth` (for custom user-registered apps)
   - Token exchange now sends the same `redirect_uri` as the authorization request.
   - Regression unit tests (`ShikimoriOAuthTest`) all pass ✅
