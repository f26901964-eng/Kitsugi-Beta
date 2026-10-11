# Kitsugi-Beta v2.4.248 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🖼️ 1. Simkl Vitrin (Hero) Logoları Artık Görünüyor

- **Kök neden:** Simkl liste/şerit kayıtları anime için **yalnızca romaji başlık** taşır
  (`Sousou no Frieren`, `Re:Zero kara Hajimeru Isekai Seikatsu`). TMDB kayıtları ise
  İngilizce/Japonca adlandırılır. `KitsugiEpisodeRatingsRepository.getLogoUrl`,
  `expectedTitles` ile TMDB kaydını başlık üzerinden doğruluyor ve **her iki tür**
  (film + dizi) eşleşmezse logoyu vermeyi reddediyordu. Romaji başlıklar TMDB
  başlıklarıyla eşleşmediği için Simkl öğeleri her seferinde bu doğrulamaya takılıyor,
  vitrinde düz metin başlık görünüyordu. (Ayarlar açık olsa bile — kural gereği
  ayar kapalıyken de Simkl denenir.)
- **Düzeltme (vitrin):** `KitsugiHeroSection.fetchHeroLogo` Simkl dalında, Simkl'ın
  KENDİ çözdüğü kimlikler (`ids.tmdb` / `ids.mal`) **otorite** kabul edilir ve başlık
  doğrulaması uygulanmaz; doğrulama yalnızca türetilmiş kimliklerde kalır. Bu kural,
  `KitsugiHeroArtworkResolver`'daki `canonicalSource == "tmdb" → expectedTitles = emptyList()`
  yaklaşımıyla aynıdır.
- **Çapraz kimlik yedeği:** Simkl liste yanıtı kimlik taşımıyorsa vitrin artık Simkl
  `extended=full` kaydından MAL/TMDB/AniList/Kitsu kimliklerini çözer (Bangumi'nin
  `resolveCrossIds` deseninin Simkl karşılığı). Uydurma/yanlış kimlik koruması: çözülen
  Simkl kaydının **kendi başlık alanları** öğe başlıklarıyla kesişmiyorsa logo sorulmaz.
- **Kimlik okuma düzeltmesi (`SimklApiClient`):** Keşfet raflarını besleyen
  `getBestMedia` yanıtlarında Simkl kimliği `ids.simkl_id` **veya** `ids.simkl`,
  TMDB kimliği `ids.tmdb` **veya** `ids.tmdb_id`, MAL kimliği sayı **veya** metin
  (`"mal": "52991"`) olabilir. Ayrıştırma tek bir test edilebilir işlevde
  (`parseBestMediaArray`) toplandı ve tüm anahtar varyantları okunuyor. Eksik
  okunan kimlikler Simkl içeriklerinin logosuz kalmasının ikinci nedeniydi.
- **Detay sayfaları:** Aynı yanlış negatif, Simkl detay sayfalarının başlık logosunu da
  engelliyordu. `ApiResultDetailViewModel` ve `MediaEntryDetailViewModel` Simkl dalı,
  vitrindeki kuralla aynı şekilde Simkl kimliklerine başlık doğrulaması uygulamaz.
  Diğer kaynakların (MAL/AniList/Kitsu/Bangumi/TMDB) mevcut davranışı değişmedi.
- **Testler:** `SimklExploreParsingTest` — `simkl_id`/`simkl`, `tmdb`/`tmdb_id`, metin
  `mal`, CJK başlığın Latin alternatife çevrilmesi, anime film "Film" ipucu, poster/fanart
  CDN adresleri ve kimliksiz kayıtta geçici kimliğin MAL/TMDB sanılmaması.

### 📌 Notlar
- Vitrin görseli (poster/arka plan) çözümleyicisi bu düzeltmeden etkilenmez; değişiklik
  yalnızca **logo** yolundadır.
- Derleme/birim testleri bu ortamda çalıştırılamadı (JDK/Android SDK yok); değişiklikler
  statik denetim (parantez/blok dengesi, imza denetimi) ve `git diff --check` ile
  doğrulandı.

---

## 🇬🇧 English

### 🖼️ 1. Simkl Hero Logos Now Render

- **Root cause:** Simkl list/shelf records carry **romaji-only** anime titles
  (`Sousou no Frieren`, `Re:Zero kara Hajimeru Isekai Seikatsu`), while TMDB records are
  named in English/Japanese. `KitsugiEpisodeRatingsRepository.getLogoUrl` verifies the
  TMDB record through `expectedTitles` and refuses to return a logo when **neither** the
  movie nor the TV record matches. Romaji titles never matched, so Simkl entries always
  failed that check and the hero fell back to plain text titles (even with the logo
  setting enabled — Simkl is attempted regardless).
- **Fix (hero):** In the Simkl branch of `KitsugiHeroSection.fetchHeroLogo`, identities
  resolved by Simkl itself (`ids.tmdb` / `ids.mal`) are treated as authoritative and skip
  the title verification; the check remains for derived identities. This mirrors the
  existing `canonicalSource == "tmdb" → expectedTitles = emptyList()` rule in
  `KitsugiHeroArtworkResolver`.
- **Cross-ID fallback:** When a Simkl shelf record carries no IDs, the hero now resolves
  MAL/TMDB/AniList/Kitsu identities from the Simkl `extended=full` record (the Simkl
  counterpart of Bangumi's `resolveCrossIds`). A synthetic/wrong-ID guard only accepts the
  record when its **own title fields** intersect the item titles.
- **ID parsing fix (`SimklApiClient`):** In `getBestMedia` responses the Simkl ID may be
  `ids.simkl_id` **or** `ids.simkl`, the TMDB ID `ids.tmdb` **or** `ids.tmdb_id`, and the
  MAL ID a number **or** a string (`"mal": "52991"`). Parsing now lives in a single
  testable function (`parseBestMediaArray`) and reads every variant. Missing these keys was
  the second reason Simkl entries stayed logo-less.
- **Detail pages:** The same false negative also blocked the Simkl detail-page title logo.
  `ApiResultDetailViewModel` and `MediaEntryDetailViewModel` now follow the hero rule for
  Simkl, while every other source (MAL/AniList/Kitsu/Bangumi/TMDB) keeps its current
  behaviour.
- **Tests:** `SimklExploreParsingTest` covers `simkl_id`/`simkl`, `tmdb`/`tmdb_id`, string
  `mal`, CJK-to-Latin title resolution, the anime-movie "Film" hint, Simkl CDN poster/fanart
  URLs, and that placeholder IDs never turn into MAL/TMDB identities.

### 📌 Notes
- The hero artwork resolver (posters/backdrops) is untouched; this change only affects the
  **logo** path.
- Build/unit tests could not run in this environment (no JDK/Android SDK); changes were
  verified statically (block/brace balance, signature checks) and with `git diff --check`.
