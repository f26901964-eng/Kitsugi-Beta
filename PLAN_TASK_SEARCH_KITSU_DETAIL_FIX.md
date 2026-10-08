# Task Plan — Arama "Tümü" Eksik Sonuçlar + Kitsu Detay "Yüklenemedi" Hatası (v2.4.201)

**Tarih:** 2026-10-08
**Sürüm:** v2.4.201
**Kapsam:** (1) Arama ekranı "Tümü" kapsamında bazı kaynakların boş/eksik gelmesi, (2) Kitsu kaynaklı kayıtlarda ayrıntı sayfasının "Medya detay bilgisi şu anda yüklenemedi" ekranına düşmesi.

---

## 1. Kullanıcı bildirimleri

1. **Arama → "Tümü":** "Tüm kaynakları kontrol edip eksiksiz sonuç getirmesi gerekiyor; şu an görünmesi gereken bazı şeyler çıkmıyor."
   - Ekran görüntülerinde: AniList/MAL satırları boş ya da eksik, buna karşılık Shikimori/Kitsu satırlarında **önceki sorgunun alakasız sonuçları** görünüyor; bağlantı hızı ~125 KB/s.
2. **Listem → Kitsu kayıtları:** "Medya detay bilgisi şu anda yüklenemedi" hatası. Örnek başlıklar: *Imaria*, *Ero Ishi: Seijun Bishoujo wo Kotoba Takumi ni Hametai Houdai*, *Menkui!*, *Kouhai* — yani çapraz veri eşitleme sonrası listede görünen Kitsu kayıtları.

---

## 2. Kök nedenler

### 2.1 Arama "Tümü" — bayat (stale) çalışma sonuçların üzerine yazıyordu
- Her tuş vuruşunda `searchJob?.cancel()` çağrılıyor, ancak kaynak istekleri `runCatching { ... }.getOrDefault(emptyList())` ile sarmalandığı için **`CancellationException` yutuluyordu**. İptal edilen (eski) çalışma arka planda devam edip kaynak sonuçlarını ve son birleşik listeyi **yeni sorgunun üzerine yazıyordu**.
- İki arama aynı anda yazabildiği için "Tümü" sonucu karışıyordu: bir kaynağın satırı boşalırken bir başkasında önceki sorgunun sonuçları kalıyordu (`onPlatformCompleted` ve `MultiSearchSection` boş listeyi hiç çizmez).
- Tek kaynak zaman tavanı **7 sn** idi; 125 KB/s gibi bağlantılarda AniList/MAL yanıtı bu süreyi aşınca kaynak **tamamen kayboluyordu**.
- Resmî MAL v2 API'si boş/başarısız döndüğünde (anahtar/429/bölgesel engel) `searchMALOnly` doğrudan `emptyList()` dönüyordu → "MyAnimeList" rafı hiç çizilmiyordu.

### 2.2 Kitsu detay — kimlik kaybı + gizlenen kayıtlar
- `KitsuIdentityMigration` çözemediği kayıtta `malId` alanını **`null` yapıyordu**. Alan boş kalınca `MediaEntryDetailViewModel` yerel satır numarasını (`entry.id`, örn. 42) dış kimlik sanıp `fetchPrimaryDetail`'e geçiriyor; sonuç ya **alakasız yapım** ya da **"Medya detay bilgisi şu anda yüklenemedi"** ekranı oluyordu.
- Aynı turda kimliği silinen kayıtlar sonraki onarım turlarında **atlanıyordu** (`if (entity.malId == null) { skipped++; continue }`) → kayıt kalıcı olarak kimliksiz kalıyordu.
- Onarım ağ bütçesi 40 denemeydi ve bütçe nedeniyle ertelenen kayıtlar "başarısız" sayılıp sürüm damgası yazılıyordu.
- Kitsu dalında yedek zinciri zayıftı: kimlik çözülemezse yalnızca `KitsuClient.fetchAnimeDetailByTitle` deneniyor, **film/dizi türlerinde hiç yedek kalmıyordu**; MAL (Jikan) ve TMDB tabanlı yedek yoktu.
- **Kitsu API kuralı:** R18/nsfw kayıtlar **anonim isteklere gizlenir** (resmî doküman). Uygulamanın detay/manga-detay/kimlik-arama çağrıları jeton göndermiyordu → kullanıcının kendi listesindeki +18 kayıtlar "404" gibi davranıp sayfayı düşürüyordu. (Canlı doğrulama: `https://kitsu.io/api/edge/manga?filter[text]=Menkui` → kayıt döner; arama, `filter[nsfw]` filtresini manga uç noktasında **desteklemez** — bu yüzden çözüm jeton + yedek zinciridir.)

---

## 3. Yapılan değişiklikler

### 3.1 Arama (`ui/screens/search/SearchViewModel.kt`)
- [x] `searchGeneration: AtomicInteger` eklendi; her arama **nesil numarası** artırıyor.
- [x] `onPlatformCompleted` yalnızca güncel nesil için yazıyor (bayat çalışma kaynak satırlarını ezemiyor).
- [x] "Tümü" final birleştirmesinde nesil kontrolü: bayat çalışma `Pair(mevcut sonuçlar, false)` dönüyor, hiçbir şey yazmıyor.
- [x] `loadMore` sayfalaması nesil kontrolü ile korunuyor (araya yeni arama girerse sayfalama uygulanmıyor).
- [x] Tüm kaynak dallarında `getOrDefault(emptyList())` → `getOrElse { err -> if (err is CancellationException) throw err; emptyList() }` (iptal artık yutulmuyor).
- [x] Kaynak zaman tavanı `allSourceTimeoutMs = 20_000L` (7 sn → 20 sn), shimmer durumu korunuyor.

### 3.2 MyAnimeList yedeği (`data/remote/JikanSearchClient.kt`)
- [x] `searchMALOnly` resmî API boş/hatalıysa **Jikan v4** yedeğine düşüyor (`searchJikanFallback`): `/{anime|manga}?q=&limit=24&sfw=&order_by=members&sort=desc`.
- [x] Sonuçlar `source = "mal"` ve **gerçek MAL kimlikleriyle** işaretleniyor (AniList kimliği MAL diye gösterilmiyor).

### 3.3 Kitsu detay (2 dosya)
- [x] `ui/screens/detail/MediaEntryDetailViewModel.kt`: yeni `externalIdOf(entry)` — Kitsu kaydı için **asla `entry.id` (yerel satır no) kullanılmaz**; pozitif `malId` yoksa `0` döner ve istemci başlıktan çözer. `fetchDetail` + `loadTab` bu fonksiyonu kullanıyor.
- [x] `data/remote/KitsugiDetailClient.kt`:
  - `fetchPrimaryDetail` artık "kimlik yok" diye erken dönmüyor; Kitsu kaydı için **başlık yeterli** (`hasUsableId` + `extId` önsözü, tüm dallar `extId` kullanıyor).
  - Kitsu yedek zinciri: (a) kanonik stableId, (b) kaydın/belleğin bildiği **gerçek MAL ID → Jikan**, (c) **sıkı başlık eşleşmeli MAL araması → Jikan** (`fetchMalDetailByTitle`, `CrossSyncIdentityGuard.titlesLookRelated(strict = true)`), (d) film/dizi ise **başlıktan TMDB** (`fetchTmdbDetailByTitle`).
  - AnimeThemes zenginleştirmesi yalnızca gerçekten Kitsu verisi geldiğinde çalışıyor.

- [x] `data/remote/KitsuClient.kt` + `data/remote/KitsuExploreClient.kt`: Kitsu detay/manga-detay istekleri artık **kullanıcının jetonuyla** yapılıyor (jeton yoksa/geçersizse anonim isteğe düşülüyor) → +18 (R18/nsfw) kayıtlar da açılıyor.
- [x] `data/auth/KitsuApiClient.kt`: `lookupKitsuId` (kimlik çözümleme araması) aynı şekilde önce jetonlu, sonra anonim arıyor.
- [x] `ui/screens/mylist/KitsuIdentityMigration.kt`:
  - Çözülemeyen kayıtta `malId` **artık silinmiyor** (değer korunur, Kitsu kimliği gibi yorumlanmaz).
  - Eski turun **sildiği** (`malId == null`) kayıtlar atlanmıyor; **başlık üzerinden yeniden çözülüyor** ve kanonik stableId yazılıyor.
  - Ağ bütçesi 40 → **120**; bütçe/oturum nedeniyle denenemeyen kayıtlar "beklemede" sayılıyor (sürüm damgası yazılmaz, 12 saat sonra yeniden denenir).

### 3.4 Sürüm / notlar
- [x] `app/build.gradle.kts`: `appVersionName = "2.4.201"`.
- [x] `RELEASE_NOTES.md`: v2.4.201 TR + EN girişleri.

---

## 4. Doğrulama durumu

- Bu çalışma ortamında **JDK/Android SDK yok** → `./gradlew` derlemesi yapılamadı (bilinçli olarak test yapılmadı).
- Yapılan kontroller: değişen her dosyada süslü/`()`/`[]` dengesi (yorum ve dizeler ayıklanarak) tam; tüm değişiklikler `git diff` ile satır satır gözden geçirildi; çağrı yerleri `grep` ile tarandı (`externalIdOf`, `fetchMalDetailByTitle`, `authTokenOrNull`, `lookupKitsuIdMatching`, `searchJikanFallback`, `allSourceTimeoutMs`).
- Derleme doğrulaması gerekiyorsa: repoda bulunan `🏗️ Android Debug Build` iş akışı (`.github/workflows/android-build.yml`) push'ta debug APK derler; şu an bu dalın push'u ile bir koşu başlatılmış durumda (sonucu derleme hatası olup olmadığını söyler).

---

## 5. Kullanıcı için deneme adımları

1. ZIP içindeki değişen dosyaları repo kök dizinine kopyala (dizin yapısı birebir korunmuştur) **veya** `changes.patch` dosyasını uygula:
   `git apply changes.patch`
2. Derle: `./gradlew assembleFossRelease` (çıktı: `app/build/outputs/apk/foss/release/`).
3. Deneme:
   - **Arama:** Aynı kelimeyi hızlıca yazıp silerek ara; "Tümü"de kaynaklar geldikçe artmalı, karışık/eski sonuç kalmamalı, MyAnimeList rafı boş kalmamalı.
   - **Kitsu detay:** *Imaria*, *Ero Ishi…*, *Menkui!*, *Kouhai* kayıtlarını aç; sayfa artık hata ekranı yerine veri göstermeli (Kitsu → MAL → TMDB zinciri).

---

## 6. Riskler / notlar

- Zip içindeki `.github/workflows/android-build.yml` **sorunun parçası değildir**; yalnızca derleme doğrulaması için geçici olarak eklenmiş CI dosyasıdır, arşive dahil edilmedi.
- Kitsu +18 kayıtları için jetonla istek: jeton yoksa davranış eskisi gibi anonim kalır (gerileme yok).
- Kimlik çözülemeyen kayıtlar artık silinmediği için, çok eski/harici kimlik taşıyan kayıtlarda "alakasız detay" riski `KitsuIdNamespace` + `externalIdOf` korumalarıyla engellenmiştir (gerçek MAL ID, Kitsu kimliği gibi yorumlanmaz).

## 7. Arşiv içeriği

- `PLAN_TASK_SEARCH_KITSU_DETAIL_FIX.md` — bu dosya.
- `DEGISEN_DOSYALAR/` — değişen dosyalar, orijinal repo dizin yapısıyla (8 Kotlin dosyası + `app/build.gradle.kts` + `RELEASE_NOTES.md`).
- `changes.patch` — `8fdfcf9 → v2.4.201` arası uygulanabilir diff.
