# Kitsugi-Beta v2.4.248 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🖼️ 1. Simkl Vitrin (Hero) Logoları ve Kimlik Ayrıştırması

- **Kök neden:** Simkl liste/şerit kayıtları anime için **yalnızca romaji başlık** taşır (`Sousou no Frieren`, `Re:Zero kara Hajimeru Isekai Seikatsu`). TMDB kayıtları ise İngilizce/Japonca adlandırılır. `KitsugiEpisodeRatingsRepository.getLogoUrl`, `expectedTitles` ile TMDB kaydını başlık üzerinden doğruluyor ve **her iki tür** (film + dizi) eşleşmezse logoyu vermeyi reddediyordu. Romaji başlıklar TMDB başlıklarıyla eşleşmediği için Simkl öğeleri her seferinde bu doğrulamaya takılıyor, vitrinde düz metin başlık görünüyordu (logo ayarı açık olsa bile).
- **Düzeltme (vitrin):** `KitsugiHeroSection.fetchHeroLogo` Simkl dalında, Simkl'ın KENDİ çözdüğü kimlikler (`ids.tmdb` / `ids.mal`) **otorite** kabul edilir ve başlık doğrulaması uygulanmaz; doğrulama yalnızca türetilmiş kimliklerde kalır. Bu kural, `KitsugiHeroArtworkResolver`'daki `canonicalSource == "tmdb" → expectedTitles = emptyList()` yaklaşımıyla aynıdır.
- **Çapraz kimlik yedeği:** Simkl liste yanıtı kimlik taşımıyorsa vitrin artık Simkl `extended=full` kaydından MAL/TMDB/AniList/Kitsu kimliklerini çözer (Bangumi'nin `resolveCrossIds` deseninin Simkl karşılığı). Uydurma/yanlış kimlik koruması: çözülen Simkl kaydının **kendi başlık alanları** öğe başlıklarıyla kesişmiyorsa logo sorulmaz.
- **Kimlik okuma düzeltmesi (`SimklApiClient`):** Keşfet raflarını besleyen `getBestMedia` yanıtlarında Simkl kimliği `ids.simkl_id` **veya** `ids.simkl`, TMDB kimliği `ids.tmdb` **veya** `ids.tmdb_id`, MAL kimliği sayı **veya** metin (`"mal": "52991"`) olabilir. Ayrıştırma tek bir test edilebilir işlevde (`parseBestMediaArray`) toplandı ve tüm anahtar varyantları okunuyor.
- **Detay sayfaları:** Aynı yanlış negatif, Simkl detay sayfalarının başlık logosunu da engelliyordu. `ApiResultDetailViewModel` ve `MediaEntryDetailViewModel` Simkl dalı, vitrindeki kuralla aynı şekilde Simkl kimliklerine başlık doğrulaması uygulamaz. Diğer kaynakların (MAL/AniList/Kitsu/Bangumi/TMDB) mevcut davranışı değişmedi.
- **Testler:** `SimklExploreParsingTest` (6/6 geçti) — `simkl_id`/`simkl`, `tmdb`/`tmdb_id`, metin `mal`, CJK başlığın Latin alternatife çevrilmesi, anime film "Film" ipucu, poster/fanart CDN adresleri ve kimliksiz kayıtta geçici kimliğin MAL/TMDB sanılmaması.

### 🔍 2. Ayarlar Ekranı Canlı Arama ve Hızlı Gezinme (`SettingsSearch.kt`)

- **Akıllı Arama:** Ayarlar ana sayfasına gerçek zamanlı arama alanı eklendi (`SettingsSearchField`). Arama sorgusu Türkçe karakterlere ve aksanlara karşı duyarsızlaştırılır (`ı`/`i`, `ğ`/`g`, `ş`/`s` eşitlenir).
- **Derin Bağlantı (Deep-Linking):** Arama sonucu yalnızca ilgili ayar sayfasına değil, doğrudan ilgili alt sekmeye (ör. *DoH / DNS*, *Manga Okuyucu*, *Altyazı Ayarları*) odaklanarak açar.
- **Mantıksal Kategoriler:** Ayarlar ekranı Hesap, Uygulama, Kaynaklar & Entegrasyonlar, Veri & Depolama ve Destek & Tanılama bölümlerine ayrılarak organize edildi.

### 🎨 3. Ortak Sekme Kapsülleri (`KitsugiSettingsSegmentedTabs`) ve Alt Sayfa Uyumlaması

- Görünüm, Eklentiler, Entegrasyonlar ve Sistem ayarlarındaki sekmeler uygulamanın modern stiline uygun, ikonlu ve vurgulu `KitsugiSettingsSegmentedTabs` bileşeninde birleştirildi.
- Eklenti sekme adları İngilizce ve Türkçe kaynaklardan okunacak şekilde yerelleştirildi.
- Alt sayfalarda sistem gezinme çubuğu (`navigationBarsPadding`) güvenli alanı sağlandı, oynatıcı ayarları başlığı seçili alt ekrana göre dinamikleştirildi.
- Gömülü (embedded) modda gereksiz tekrarlanan "Tamam" altbilgisi kaldırıldı.
- Depolama ekranındaki iç içe geçmiş kart yapısı sadeleştirildi; İndirilenler (Video, Görsel, Altyazı) yönetimi güvenle korundu.

### 📋 4. Platform Hata Kodları Kataloğu (Ayarlar > Geliştirici & Tanılama)

- Ayarlar'daki "Platform Hata Kodları" ekranı (`PlatformErrorCatalogScreen`) artık AniList, MyAnimeList, Kitsu, Shikimori, Bangumi, TMDB, Simkl, Manga/CloudStream ve ortak (`KTS-COM`) hata kodlarının tamamını kapsıyor.
- Platform bazlı filtre çipleri ve anlık arama desteği eklendi. Tüm açıklamalar ve başlıklar TR/EN kaynaklarından çözülür.

### 🌐 5. Anında Dil Değişimi ve Kitsugi Hesabı İyileştirmeleri

- `AndroidManifest.xml` içerisinden Activity'nin dil değişimini yutmasına neden olan `locale|layoutDirection` configChanges girdisi kaldırıldı; dil değiştiğinde arayüz anında yeni dile döner. API 33+ için çift recreate korumalı güvenlik kontrolü eklendi.
- Kitsugi hesabı ekranına 3 bağımsız cihaz yereli anahtar eklendi: "Şifreli hesap yedeği", "Arama geçmişi eşitleme", "Bildirim arşivi eşitleme".
- Kasa açılamazsa şifre bellekte tutulur, tek tıkla yeniden denenebilir. E-posta doğrulama takibi dile bağımsız hale getirildi.

---

## 🇬🇧 English

### 🖼️ 1. Simkl Hero Logos and Identity Parsing

- **Root cause:** Simkl list/shelf records carry **romaji-only** anime titles (`Sousou no Frieren`, `Re:Zero kara Hajimeru Isekai Seikatsu`), while TMDB records are named in English/Japanese. `KitsugiEpisodeRatingsRepository.getLogoUrl` verifies the TMDB record through `expectedTitles` and refuses to return a logo when neither the movie nor the TV record matches. Romaji titles never matched, so Simkl entries always fell back to plain text titles in the hero (even with the logo setting enabled).
- **Fix (hero):** In the Simkl branch of `KitsugiHeroSection.fetchHeroLogo`, identities resolved by Simkl itself (`ids.tmdb` / `ids.mal`) are treated as authoritative and skip the title verification; the check remains for derived identities. This mirrors the `canonicalSource == "tmdb" → expectedTitles = emptyList()` rule in `KitsugiHeroArtworkResolver`.
- **Cross-ID fallback:** When a Simkl shelf record carries no IDs, the hero resolves MAL/TMDB/AniList/Kitsu identities from the Simkl `extended=full` record. A synthetic/wrong-ID guard rejects the record unless its own title fields intersect the item titles.
- **ID parsing fix (`SimklApiClient`):** In `getBestMedia` responses the Simkl ID may be `ids.simkl_id` or `ids.simkl`, the TMDB ID `ids.tmdb` or `ids.tmdb_id`, and the MAL ID a number or a string (`"mal": "52991"`). Parsing now lives in a single testable function (`parseBestMediaArray`) that reads every variant.
- **Detail pages:** The same false negative also blocked the Simkl detail-page title logo. `ApiResultDetailViewModel` and `MediaEntryDetailViewModel` now follow the hero rule for Simkl, while all other sources retain their existing behavior.
- **Tests:** `SimklExploreParsingTest` (6/6 passing) covers `simkl_id`/`simkl`, `tmdb`/`tmdb_id`, string `mal`, CJK-to-Latin title resolution, anime movie "Film" hint, Simkl CDN poster/fanart URLs, and placeholder ID safety.

### 🔍 2. Real-Time Settings Search and Deep-Linking (`SettingsSearch.kt`)

- **Smart Search:** Live search bar added to Settings (`SettingsSearchField`) with accent- and case-insensitive normalization across Turkish and English.
- **Deep Navigation:** Results navigate directly to the specific target tab or subpage (e.g. *DoH / DNS*, *Manga Reader*, *Subtitle Settings*).
- **Logical Organization:** Settings categorized cleanly into Account, App, Sources & Integrations, Data & Storage, and Support & Diagnostics.

### 🎨 3. Unified Segmented Tabs (`KitsugiSettingsSegmentedTabs`) & Subpage Polish

- Preferences, Addons, Integrations, and System tabs unified under the modern, icon-accented `KitsugiSettingsSegmentedTabs` component.
- Addon tab labels localized in English and Turkish.
- Subpages respect navigation bar insets (`navigationBarsPadding`), player subscreen headers dynamically update, and redundant embedded "OK" footers removed.
- Storage card hierarchy simplified while preserving Downloads (Video, Image, Subtitle) management.

### 📋 4. Complete Platform Error Codes Catalog

- The "Platform Error Codes" screen (`PlatformErrorCatalogScreen`) now catalogs AniList, MyAnimeList, Kitsu, Shikimori, Bangumi, TMDB, Simkl, Manga/CloudStream, and common (`KTS-COM`) error codes with filter chips and search in TR/EN.

### 🌐 5. Instant Language Switching & Account UX

- Removed `locale|layoutDirection` from `AndroidManifest.xml` so the activity immediately recreates upon language change. Safety recreate watchdog added for API 33+.
- Kitsugi account screen adds 3 local toggle switches for Encrypted backup, Search history sync, and Notification archive sync.
- In-memory vault password retry and locale-agnostic verification tracking.
