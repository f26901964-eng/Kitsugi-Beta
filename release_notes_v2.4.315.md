# Kitsugi Beta v2.4.315

## 🇹🇷 Türkçe

### 🔗 Paylaşım Bağlantıları — Tüm Kaynaklarda (AniList · MAL · TMDB · Simkl · Kitsu · Shikimori · Bangumi) Kalıcı Düzeltme

Bu sürüm, anime/dizi/film/karakter/seslendirmen/ekip/stüdyo sayfalarındaki **paylaş** butonlarının her kaynakta **bozuk veya yanlış** bağlantı üretmesini 12 kök neden üzerinden çözer. Detaylı analiz: `KOK_NEDEN_RAPORU_PAYLASIM_LINKLERI_2026-10-20.md`.

- **Tek üretici:** Tüm paylaşım adresleri artık `utils/ShareLinks.kt` içinde üretiliyor. Kaynak + kimlik çözümlemesi, offset'ler ve doğrulanmış sayfa şemaları tek yerde.
- **`/anime/0` kabusu bitti:** Vitrin paylaş butonu kimlik yerine sabit `0` gönderiyordu; `myanimelist.net/anime/0` gibi adresler paylaşılıyordu. Artık sayfanın kendi kaydından gerçek kimlik çözülüyor.
- **Yerel satır numarası paylaşılmıyor:** `entry.malId ?: entry.id` kalıbı kaldırıldı; `entry.id` (veritabanı satırı) hiçbir koşulda URL'ye yazılmıyor.
- **Offset'li kimlikler çözülüyor:** AniList `+100M`, Simkl `+150M`, Kitsu `+300M`, Shikimori `+400M`, Bangumi `+500M`. Ham (`<100M`) değerler **satırın kaynağına göre** yorumlanıyor.
- **Shikimori:** Yapım → `shikimori.io/animes|mangas/{id}`, karakter → `/characters/{id}`, ekip → `/people/{id}`, stüdyo → `/animes?studio={id}`, profil → `/users/{ad}`. (Eskiden hepsi MAL sayfasına gidiyordu.)
- **Bangumi:** Yapım → `bgm.tv/subject/{id}`, karakter → `/character/{id}`, ekip/şirket → `/person/{id}`.
- **Kitsu:** Yapım → `kitsu.app/anime|manga/{id}`, profil → `/users/{ad}`. Kitsu'nun karakter/ekip/stüdyo sayfası **olmadığı** için (404) artık uydurma MAL kimliği yazılmıyor; MAL arama sayfası paylaşılıyor.
- **Simkl:** Yapım → `/anime|movies|tv/{id}`, profil → `simkl.com/{ad}` (önceden `/user/{ad}` → 404). Simkl'de manga bölümü olmadığı için `/manga/{id}` üretilmiyor.
- **TMDB:** Film/dizi ayrımı, kişi → `/person/{id}`, şirket → `/company/{id}`, kanal → `/network/{id}`, profil → `/u/{ad}`.
- **MAL:** Yapım/karakter/kişi/stüdyo (`/anime/producer/{id}`) doğru şemalarla.
- **Güvenlik ağı:** `isTrustworthy` ile `http://`, `/0` ve 9+ haneli offset sızıntısı içeren adresler paylaşılmıyor. Bağlantı çözülemezse paylaşım **bağlantısız** (yalnızca başlık) yapılır — kırık link gönderilmez.
- **Alan adları:** Paylaşımda uygulamanın kullandığı yaşayan alan adları (`kitsu.app`, `shikimori.io`) kullanılıyor.

### 🧪 Birim Testleri
- `ShareLinksTest`: 18 test — altı kaynağın offset çözümlemesi, kaynak-duyarlı ham kimlik, Simkl manga yokluğu, Kitsu legacy kimliği, `0`/`null` reddi, karakter/ekip/stüdyo/profil şemaları ve güvenlik ağı.

---

## 🇬🇧 English

### 🔗 Share Links — Permanent Fix Across All Sources (AniList · MAL · TMDB · Simkl · Kitsu · Shikimori · Bangumi)

This release fixes the **share** buttons on anime/TV/movie/character/voice-actor/staff/studio pages producing **broken or wrong** links for every source, traced to 12 root causes. Full analysis: `KOK_NEDEN_RAPORU_PAYLASIM_LINKLERI_2026-10-20.md`.

- **Single producer:** Every share URL is now built by `utils/ShareLinks.kt` — source-aware ID decoding, offsets and verified page schemas in one place.
- **No more `/anime/0`:** The hero share button sent a hard-coded `0` ID. It now resolves the real ID from the page's own record.
- **No local row IDs:** `entry.malId ?: entry.id` is gone; the database row id can never leak into a URL.
- **Offsets decoded:** AniList `+100M`, Simkl `+150M`, Kitsu `+300M`, Shikimori `+400M`, Bangumi `+500M`. Raw (`<100M`) values are interpreted **per row source**.
- **Shikimori:** media → `/animes|mangas/{id}`, character → `/characters/{id}`, staff → `/people/{id}`, studio → `/animes?studio={id}`, profile → `/users/{name}`.
- **Bangumi:** media → `/subject/{id}`, character → `/character/{id}`, staff/company → `/person/{id}`.
- **Kitsu:** media → `kitsu.app/anime|manga/{id}`, profile → `/users/{name}`. Kitsu has **no** character/staff/studio pages (404), so instead of fabricating a MAL ID we share the MAL search page.
- **Simkl:** media → `/anime|movies|tv/{id}`, profile → `simkl.com/{name}` (was `/user/{name}` → 404). No `/manga/{id}` (Simkl has no manga section).
- **TMDB:** movie/TV split, person → `/person/{id}`, company → `/company/{id}`, network → `/network/{id}`, profile → `/u/{name}`.
- **Safety net:** `isTrustworthy` blocks `http://`, `/0` and 9+ digit offset leaks. When no link can be resolved the share sheet opens with the title only — a broken link is never sent.
- **Domains:** share links use the same live domains as the app (`kitsu.app`, `shikimori.io`).

### 🧪 Unit tests
- `ShareLinksTest`: 18 tests covering six-source offset decoding, source-aware raw IDs, Simkl manga absence, legacy Kitsu IDs, `0`/`null` rejection, character/staff/studio/profile schemas and the URL safety net.
