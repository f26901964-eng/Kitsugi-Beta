# PLAN & TASK — Paylaşım Bağlantıları Düzeltmesi
**Sürüm:** Kitsugi Beta v2.4.315 · **Tarih:** 2026-10-20 · **Dal:** `arena/6bc78ba5-kitsugi-beta`
**Kök neden raporu:** `KOK_NEDEN_RAPORU_PAYLASIM_LINKLERI_2026-10-20.md`

---

## 1. Amaç

Anime / manga / film / dizi detay sayfalarında ve karakter, seslendirmen (ekip) ve stüdyo
sayfalarında **paylaş** butonlarının ürettiği bağlantıların, **yedi kaynağın tamamında**
(AniList · MAL/Jikan · TMDB · Simkl · Kitsu · Shikimori · Bangumi) doğru ve açılabilir olması.

Kabul ölçütü:
1. Paylaşılan hiçbir URL `404` veya **başka bir yapımın** sayfasına gitmeyecek.
2. URL'de `0`, yerel veritabanı satır kimliği veya offset'li stable ID (`100M+`) bulunmayacak.
3. Kaynağın o varlık için sayfası yoksa (Kitsu karakter/ekip/stüdyo, Simkl manga) uydurma yol
   yazılmayacak; doğrulanmış arama sayfası paylaşılacak veya bağlantısız paylaşım yapılacak.

---

## 2. Görev listesi

| # | Görev | Durum | Dosya |
|---|---|---|---|
| T1 | Kimlik sözleşmesinin haritalanması (offset pencereleri, ham değerler) | ✅ | `BangumiIdNamespace.kt`, `MediaIdentity.kt`, `CrossProviderIdentity.kt` |
| T2 | Kaynak → doğrulanmış URL şeması tablosu | ✅ | `ShareLinks.kt` (KDoc) |
| T3 | `ShareLinks` tek üretici: `decode`, `mediaUrl`, `characterUrl`, `staffUrl`, `studioUrl`, `profileUrl` | ✅ | `utils/ShareLinks.kt` |
| T4 | `ShareUtils` ince Android katmanına indirgeme + nullable URL + `isTrustworthy` | ✅ | `utils/ShareUtils.kt` |
| T5 | Vitrindeki `0` kimliğinin kaldırılması | ✅ | `KitsugiDetailHero.kt` |
| T6 | `entry.malId ?: entry.id` (yerel satır no) kalıbının kaldırılması | ✅ | `KitsugiDetailHeroSection.kt`, `MediaEntryDetailPage.kt`, `KitsugiMediaEntryDetailDialog.kt` |
| T7 | API detay sayfasının gerçek kimlikleri geçmesi | ✅ | `ApiDetailLeftPanel.kt`, `ApiResultDetailPage.kt` |
| T8 | Karakter / ekip / stüdyo butonlarının kaynağa özel bağlanması | ✅ | `CharacterDetailComponents.kt`, `StaffDetailComponents.kt`, `StudioDetailComponents.kt`, `StudioDetailPage.kt` |
| T9 | Profil paylaşımı (Simkl `/{ad}`, Shikimori `/users/{ad}`) | ✅ | `KitsuProfileContent.kt`, `ShikimoriProfileContent.kt`, `ShareLinks.kt` |
| T10 | Regresyon testleri | ✅ | `ShareLinksTest.kt` (18 test) |
| T11 | Sürüm + sürüm notları | ✅ | `app/build.gradle.kts` (2.4.315), `release_notes_v2.4.315.md`, `RELEASE_NOTES.md` |
| T12 | Kök neden raporu | ✅ | `KOK_NEDEN_RAPORU_PAYLASIM_LINKLERI_2026-10-20.md` |

---

## 3. Doğrulanmış sayfa şemaları

| Kaynak | Yapım | Karakter | Ekip | Stüdyo | Profil |
|---|---|---|---|---|---|
| AniList | `/anime/{id}`, `/manga/{id}` | `/character/{id}` | `/staff/{id}` | `/studio/{id}` | `/user/{name}` |
| MAL | `/anime/{id}`, `/manga/{id}` | `/character/{id}` | `/people/{id}` | `/anime/producer/{id}` | `/profile/{name}` |
| Bangumi | `/subject/{id}` | `/character/{id}` | `/person/{id}` | `/person/{id}` | `/user/{name}` |
| Shikimori | `/animes/{id}`, `/mangas/{id}` | `/characters/{id}` | `/people/{id}` | `/animes?studio={id}` | `/users/{name}` |
| Kitsu | `/anime/{id}`, `/manga/{id}` | ✗ (404) | ✗ (404) | ✗ (404) | `/users/{name}` |
| TMDB | `/movie/{id}`, `/tv/{id}` | `/person/{id}` | `/person/{id}` | `/company/{id}`, `/network/{id}` | `/u/{name}` |
| Simkl | `/anime/{id}`, `/movies/{id}`, `/tv/{id}` | ✗ | ✗ | ✗ | `/{name}` |

Doğrulama: `kitsu.app/characters/1` → 404 · `kitsu.app/producers/1` → 404 · `bgm.tv/character/1` → 200 ·
`myanimelist.net/character.php?q=…` ve `people.php?q=…` arama sayfaları · Shikimori `routes.rb`
içinde stüdyo yalnızca `index` · Simkl resmî deep-link tablosu.

---

## 4. Kimlik (offset) sözleşmesi

| Kaynak | `malId` alanında saklanan | Paylaşımda çözüm |
|---|---|---|
| MAL / Jikan | gerçek MAL ID (1..99M) | aynen |
| AniList | `id + 100M` (veya `idMal` varsa ham MAL ID) | `-100M` / MAL |
| Simkl | `simklId + 150M` (bazı yollarda ham) | `-150M` / ham |
| Kitsu | `kitsuId + 300M` **veya** ham (legacy) | `-300M` / ham → Kitsu |
| Shikimori | `shikimoriId + 400M` **veya** ham `target_id` | `-400M` / ham → Shikimori |
| Bangumi | `subjectId + 500M` **veya** ham `subject_id` | `-500M` / ham → Bangumi |

> AniList (100M..299M) ve Simkl (150M..249M) pencereleri örtüşür → ayrım **satırın kaynağıyla** yapılır.

---

## 5. Test kapsamı (`ShareLinksTest`)

* Altı kaynağın offset çözümlemesi + ham (legacy) değerler
* Simkl manga bölümünün olmaması (uydurma `/manga/{id}` yok)
* Kitsu legacy kimliğinin MAL'a çevrilmemesi
* `0` / `null` kimlikle URL üretilmemesi
* Karakter / ekip / stüdyo / profil şemaları (kaynak bazlı)
* Kitsu karakter/ekip/stüdyo → MAL arama sayfasına düşüş
* `isTrustworthy`: `http://`, `/0`, 9+ haneli offset sızıntısı reddi

---

## 6. Riskler ve geri dönüş

| Risk | Etki | Önlem |
|---|---|---|
| Kitsu ileride karakter sayfası açarsa | Arama sayfası paylaşılır (kırık değil) | `ShareLinks.characterUrl` içine tek satır |
| Shikimori alan adı değişimi (`.one` → `.io`) | Bağlantı yönlenmez | `SHIKIMORI` sabiti; domain takip iş akışı (`update_domains.yml`) |
| Bilinmeyen kaynak + ham kimlik | MAL kimliği varsayılır (legacy davranış) | `isTrustworthy` + `null` yolu |
| Geri dönüş | — | `ShareUtils` eski imzaları korundu; tek commit ile geri alınabilir |
