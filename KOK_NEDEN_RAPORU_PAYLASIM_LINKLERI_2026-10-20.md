# KÖK NEDEN RAPORU — Paylaş Butonları Her Kaynakta Bozuk Link Üretiyor
**Tarih:** 2026-10-20 · **Sürüm:** v2.4.315 · **Kapsam:** AniList · MAL/Jikan · TMDB · Simkl · Kitsu · Shikimori · Bangumi

---

## 1. Şikâyetin teknik karşılığı

Kullanıcı: *"hangi kaynaktan olursa olsun anime/dizi/film/karakter/seslendirmen/ekip paylaş butonları düzgün çalışmıyor, verdiği paylaşım linkleri hep bozuk."*

Bu doğruydu ve **tek bir hata değil, 12 ayrı kök nedenin** birleşimiydi. Tamamı paylaşım URL'sinin
üretildiği tek dosyada (`utils/ShareUtils.kt`) toplanmıştı; ancak her çağrı noktası bu fonksiyonlara
**farklı türde kimlik** veriyordu.

---

## 2. Kök nedenler

### KN-1 — Vitrin (hero) paylaş butonu kimlik olarak sabit `0` gönderiyordu
`KitsugiDetailHero.kt` içindeki yedek yol `buildMediaUrl(source, 0, mediaType)` çağırıyordu.
Sonuç: **her API detay sayfasında** `https://myanimelist.net/anime/0`, `/manga/0`, `anilist.co/anime/0`
gibi var olmayan adresler. `ApiDetailLeftPanel` hiçbir zaman `onShareClick` geçmediği için bu yol
**her zaman** çalışıyordu — yani tüm kaynaklardaki ana paylaş butonu bozuktu.

### KN-2 — Kimlik yerine yerel veritabanı satır numarası paylaşılıyordu
`KitsugiDetailHeroSection.kt` ve `MediaEntryDetailPage.kt` içinde:
```kotlin
val mediaId = entry.malId ?: entry.id   // entry.id = Room satır kimliği!
```
`malId` boş olan her satırda (manual/legacy kayıtlar, bazı Bangumi/Simkl içe aktarımları)
`myanimelist.net/anime/{3}` gibi **başka bir yapımın** sayfası paylaşılıyordu.

### KN-3 — `MediaEntry.malId` alanı "MAL kimliği" değil, **kaynağa özel offset'li kimlik**
Sözleşme (`BangumiIdNamespace`, `MediaIdentity.keys`, `CrossProviderIds`):
| Kaynak | `malId` alanında saklanan |
|---|---|
| MAL/Jikan | gerçek MAL ID (1..99M) |
| AniList | `anilistId + 100M` (veya `idMal` bilindiğinde gerçek MAL ID) |
| Simkl | `simklId + 150M` (bazı yollarda ham `simklId`) |
| Kitsu | `kitsuId + 300M` **veya ham `kitsuId`** (legacy) |
| Shikimori | `shikimoriId + 400M` **veya ham `target_id`** |
| Bangumi | `subjectId + 500M` **veya ham `subject_id`** |

`ShareUtils` yalnızca AniList (`-100M`) ve Kitsu (`-300M`) offset'ini biliyordu. Diğerleri
`else` dalına düşüp **MAL** sayfasına yazılıyordu → `myanimelist.net/anime/500123456` (404).

### KN-4 — Ham Shikimori kimliği MAL kimliği sanılıyordu
Shikimori araması `malId = id` (Shikimori `target_id`) yazar. Eski kod bunu MAL sayfasına çeviriyordu:
`shikimori 20` (One Piece) → `myanimelist.net/anime/20` (Naruto). **Yanlış içerik**, doğru olsa bile
kullanıcı için "bozuk".

### KN-5 — Simkl offset'i çözülmüyordu
`buildExternalMediaUrl("simkl", 150_188_5096, …)` → `simkl.com/anime/1501885096` (404). Doğrusu
`simkl.com/anime/1885096`. Ayrıca Simkl'in **manga bölümü yok**; `/manga/{id}` uydurma bir yoldur.

### KN-6 — Shikimori ve Bangumi için yapım dalı hiç yoktu
`buildMediaUrl` yalnızca `anilist/kitsu/tmdb/simkl` tanıyordu; Shikimori ve Bangumi `else`ye düşüyordu.

### KN-7 — Kitsu'nun legacy ham kimliği MAL'a çevriliyordu
300M penceresi dışındaki her Kitsu değeri "MAL ID" varsayılıyordu. Oysa `MediaIdentity.kitsuId`
legacy Kitsu satırlarını 1..99M aralığında **Kitsu kimliği** olarak kabul eder.

### KN-8 — Karakter / ekip / stüdyo bağlantıları MAL'a sabitlenmişti
```kotlin
fun buildCharacterUrl(source, id) = when(source) { "anilist" -> …; "bangumi" -> …; else -> MAL }
fun buildStaffUrl(source, id)     = when(source) { "anilist" -> …; "bangumi" -> …; else -> MAL }
fun buildStudioUrl(source, id)    = when(source) { else -> "anilist.co/studio/$id" }
```
Sonuç:
* Shikimori karakteri → `myanimelist.net/character/{shikimori_id}` → **yanlış karakter / 404**
* Shikimori seslendirmeni → `myanimelist.net/people/{shikimori_id}` → **404**
* TMDB kişisi → `myanimelist.net/people/{tmdb_person_id}` → **404**
* **Her** stüdyo (MAL, Shikimori, TMDB, Kitsu, Bangumi) → `anilist.co/studio/{yabancı_id}` → **404/yanlış stüdyo**
* Bangumi karakteri stableId taşıyorsa → `bgm.tv/character/500123456` → **404**

### KN-9 — Supheli sınır: Kitsu'nun karakter sayfası yok
`kitsu.app/characters/{id}` **404** döner (doğrulandı). Dolayısıyla Kitsu karakter/ekip/stüdyo
paylaşımında hiçbir Kitsu sayfası üretilemez; eski kod bunun yerine yanlış MAL kimliği yazıyordu.

### KN-10 — Simkl profil adresi yanlıştı
`buildProfileUrl("simkl", x)` → `simkl.com/user/{x}`. Simkl resmî deep-link tablosuna göre profil
`https://simkl.com/{kullanıcı}` şeklindedir.

### KN-11 — Shikimori profil paylaşımı `/users/` yolunu atlıyordu
`ShikimoriProfileContent.kt` doğrudan `https://shikimori.io/{kullanıcı}` üretiyordu → **404**.
Ayrıca Kitsu ve Shikimori profil butonları `ShareUtils`'i atlayıp kendi `Intent`'ini kuruyordu
(başlık yok, tek yerden düzeltilemiyor).

### KN-12 — Alan adı tutarsızlıkları
Aynı dosyada Kitsu için hem `kitsu.io` hem `kitsu.app`; Shikimori için `shikimori.one` yazılıyordu.
Uygulamanın API katmanı `kitsu.app` ve `shikimori.io` kullanıyor; paylaşım da aynı (yaşayan)
alan adlarını kullanmalı.

---

## 3. Doğrulanmış sayfa şemaları (2026-10-20)

| Kaynak | Yapım | Karakter | Ekip | Stüdyo | Profil |
|---|---|---|---|---|---|
| **AniList** | `/anime/{id}`, `/manga/{id}` | `/character/{id}` | `/staff/{id}` | `/studio/{id}` | `/user/{name}` |
| **MAL** | `/anime/{id}`, `/manga/{id}` | `/character/{id}` | `/people/{id}` | `/anime/producer/{id}` | `/profile/{name}` |
| **Bangumi** | `/subject/{id}` | `/character/{id}` | `/person/{id}` | `/person/{id}` (şirket = person) | `/user/{name}` |
| **Shikimori** | `/animes/{id}`, `/mangas/{id}` | `/characters/{id}` | `/people/{id}` | `/animes?studio={id}` | `/users/{name}` |
| **Kitsu** | `/anime/{id}`, `/manga/{id}` | ✗ yok | ✗ yok | ✗ yok | `/users/{name}` |
| **TMDB** | `/movie/{id}`, `/tv/{id}` | `/person/{id}` | `/person/{id}` | `/company/{id}`, `/network/{id}` | `/u/{name}` |
| **Simkl** | `/anime/{id}`, `/movies/{id}`, `/tv/{id}` | ✗ yok | ✗ yok | ✗ yok | `/{name}` |

**Doğrulama yöntemi:** `kitsu.app/characters/1` → 404, `kitsu.app/producers/1` → 404,
`bgm.tv/character/1` → 200 (Lelouch), `myanimelist.net/character.php?q=…` → arama sayfası,
`myanimelist.net/people.php?q=…` → ada göre doğrudan kişi sayfasına yönlenir,
Shikimori kaynak kodunda `resources :studios, only: %i[index]` (ayrı stüdyo sayfası **yok**),
Simkl resmî deep-link tablosu (`/movies|tv|anime/{id}`, profil `/{username}`).

---

## 4. Çözüm

### 4.1 Tek üretici: `utils/ShareLinks.kt`
* `decode(source, storedId, tmdbId, simklId, realMalId)` → `MediaIds` (offset'siz gerçek kimlikler).
  * 500M/400M/300M/150M/100M pencereleri çözülür, **ham değer satırın kaynağına göre** yorumlanır.
  * AniList↔Simkl pencere örtüşmesi kaynak bilgisiyle ayrılır.
* `mediaUrl(...)` → kaynağa özel yapım bağlantısı; kaynak o türü desteklemiyorsa **doğrulanmış**
  sırayla MAL → AniList → TMDB → Bangumi → Shikimori → Kitsu → Simkl denenir.
* `characterUrl`, `staffUrl`, `studioUrl` → her kaynak için kendi şeması; kaynağın sayfası **yoksa**
  MAL arama sayfası (`character.php?q=`, `people.php?q=`, `company?q=`) paylaşılır.
* `profileUrl` → Simkl `simkl.com/{ad}`, Shikimori `shikimori.io/users/{ad}` dahil düzeltildi.
* `isTrustworthy(url)` → `/0`, `http://`, 9+ haneli offset sızıntısı içeren adresler paylaşılmaz.

### 4.2 `utils/ShareUtils.kt`
Artık yalnızca `Intent` kuran ince katman; `url` artık **nullable**. Bağlantı çözülemezse paylaşım
başlıkla yapılır (bozuk adres gönderilmez). `buildMediaUrl(entry)` yeni aşırı yüklemesi
`malId + tmdbId + simklId` alanlarını birlikte değerlendirir.

### 4.3 Çağrı noktaları düzeltildi
| Dosya | Değişiklik |
|---|---|
| `KitsugiDetailHero.kt` | `0` kimliği kaldırıldı; `artworkItem`tan gerçek kimlik çözülüyor |
| `ApiDetailLeftPanel.kt` | `onShareClick` bağlandı (kaynak+kimlik+realMalId) |
| `KitsugiDetailHeroSection.kt` | `entry.id` yerine `buildMediaUrl(entry)` |
| `MediaEntryDetailPage.kt` | Aynı; `buildExternalUrl` tek satıra indi |
| `KitsugiMediaEntryDetailDialog.kt` | Aynı |
| `ApiResultDetailPage.kt` | `realMalId` + `isTrustworthy` akışı |
| `CharacterDetailComponents.kt` (3 buton) | `buildCharacterUrl(source, id, name)` |
| `StaffDetailComponents.kt` (3 buton) | `buildStaffUrl(source, id, name)` |
| `StudioDetailComponents.kt` + `StudioDetailPage.kt` | `buildStudioUrl(source, id, name)` |
| `KitsuProfileContent.kt`, `ShikimoriProfileContent.kt` | Kendi `Intent`'leri kaldırıldı, ortak üretici |

### 4.4 Testler
`app/src/test/java/com/kitsugi/animelist/utils/ShareLinksTest.kt` — 18 test:
offset çözümleme (6 kaynak), kaynak-duyarlı ham kimlik, Simkl manga yokluğu, Kitsu legacy kimliği,
`0`/`null` kimlik reddi, karakter/ekip/stüdyo/profil şemaları ve güvenlik ağı.

---

## 5. Kapsam dışı bırakılanlar (bilinçli)

* **Kitsu karakter/ekip/stüdyo:** Kitsu'nun web arayüzünde bu sayfalar yok; uydurma link yerine
  MAL arama sayfası paylaşılır. Kitsu ileride bu sayfaları eklerse `ShareLinks` içine tek satırla
  gerçek şema yazılabilir.
* **Simkl karakter/ekip/stüdyo:** Simkl bu varlıklar için sayfa yayınlamıyor.
* **Shikimori stüdyo:** Ayrı sayfa yok; `?studio={id}` filtresi kullanılır (aynı işlevi görür).
