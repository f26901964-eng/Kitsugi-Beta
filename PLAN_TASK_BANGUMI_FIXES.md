# Task Plan — Bangumi (v2.4.207) Düzeltmeleri: Liste 404 · Arama boş · Detay sayfası boş

**Tarih:** 2026-10-08
**Taban sürüm:** v2.4.207 (sürüm numarası bu çalışmada artırılmadı)
**Kapsam:** Bangumi (bgm.tv) entegrasyonunda kullanıcının bildirdiği üç sorun.

---

## 1. Kullanıcı bildirimleri (ekran görüntüleri)

1. **Listem → Bangumi:** 1-tık girişten sonra "Listeniz boş" + snackbar
   `Bangumi HTTP 404 — Not Found (user doesn't exist or has been removed)`.
2. **Arama → Bangumi motoru:** "date a live" dahil hiçbir sorgu sonuç vermiyor, hep "Sonuç bulunamadı.".
3. **Detay sayfası (Clannad ~After Story~):** neredeyse boş.
   - Resimler: yalnızca 4 Bangumi posteri (tek kategori, kopyalar var),
   - Karakterler / Ekip / Öneriler / İlişkiler / Grafikler / Yorumlar: boş,
   - Bilgi: yalnızca Durum/Başlangıç/Bitiş/Süre/Yaş Sınırı/İngilizce + bir Kitsu bağlantısı.
   - İstenen: Bangumi diğer platformlarla birlikte çalışsın; fanart.tv, TMDB, Shikimori ve diğer kaynaklardaki
     TÜM görseller Bangumi galerisine de gelsin; her sekme mümkün olduğunca otomatik dolsun; Bilgi tamamlansın.

---

## 2. Kök nedenler

### 2.1 Liste 404
- `AuthViewModel.importBangumiList` → `BangumiImportManager.fetchAllLists(context, token)` kullanıcı adı vermiyordu;
  `effectiveUsername` `"-"` oluyor → `GET /v0/users/-/collections`.
- Sunucuda (`bangumi/server`) `-` takma adı **yalnızca yazma uçlarında** geçerli
  (`POST|PATCH /v0/users/-/collections/{id}`, `…/episodes`). Okuma ucu `user.GetByName("-")` çağırır → **404**
  "user doesn't exist or has been removed".
- Aynı hata `BangumiSyncManager.fetchRemoteStatus` içinde de vardı (varsayılan `"-"` → 404 → sessizce `null`).

### 2.2 Arama
- `searchSubjects` yetişkin içerik açıkken `filter.nsfw = "include"` (**metin**) gönderiyordu. Sunucu alanı
  `null.Bool` olarak çözer → **HTTP 400** → istemci hatayı yutup boş liste döndürüyordu. (OpenAPI: `nsfw` boolean.)
- İstemci sayfa başına 24 kayıt istiyor, sunucu **20'ye kırpıyor**: `offset = (page-1)*24` her sayfada 4 kaydı atlıyor,
  `size >= 24` kontrolü de "sonraki sayfa yok" sonucunu veriyordu.
- `POST /v0/search/subjects` "deneysel" uçtur; hata/boş yanıtta başka yol yoktu. Eski `GET /search/subject/{q}`
  ucu aynı sorguda sonuç veriyor (canlı doğrulandı: "date a live" → 7 sonuç).
- Hatalar (`runCatching … emptyList()` ve "Tümü" rafında `catch → Pair(emptyList(), false)`) kullanıcıya hiç
  gösterilmiyordu; her şey "Sonuç bulunamadı." olarak görünüyordu.

### 2.3 Detay sayfası
- `KitsugiDetailClient.fetchPrimaryDetail` içindeki `when (source)` **"bangumi" dalına sahip değildi** → `null` →
  "Anime Fallback: Kitsu" **başlık araması** (Bilgi'deki 24 dk / PG / İngilizce başlık / Kitsu bağlantısı buradan).
- Sekme istemcilerinin (`KitsugiCharacterClient`, `KitsugiStaffClient`, `KitsugiMediaRelationsClient`,
  `KitsugiMediaSocialClient`, `KitsugiMediaTabsClient`) hepsinde `"bangumi"` dalı yoktu → `else → emptyList()/null`.
- Galeri / bölüm puanı / logo / MDBList, `result.malId` (**Bangumi stableId = subject_id + 500M**) değerini MAL ID gibi
  kullanıyordu → TMDB/Fanart.tv/Shikimori hiç eşleşmiyordu. Galeri ayrıca `lain.bgm.tv`'nin aynı kapağı farklı
  boyut yollarıyla (`/pic/…`, `/r/400/…`, `/r/800/…`) vermesi yüzünden kopya poster gösteriyordu.
- Room önbelleği (`bangumi_anime_{id}`, 24 sa) eski, Kitsu kaynaklı yanlış detayı saklıyordu.

---

## 3. Yapılan değişiklikler

### 3.1 Liste (Bug 1)
- [x] `BangumiAuthStore.resolveUsername(context)`: kayıtlı kullanıcı adı → `GET /v0/me` → sayısal kullanıcı ID'si.
- [x] `BangumiImportManager.fetchAllLists`: `"-"` yerine gerçek kullanıcı adı; çözülemezse anlaşılır 401 mesajı.
- [x] `BangumiSyncManager.fetchRemoteStatus`: gerçek kullanıcı adıyla `GET …/collections/{id}`.
- [x] `BangumiApiClient.getUserCollections/getAllUserCollections/getUserCollection`: `username` artık **zorunlu**
  (`"-"` varsayılanı kaldırıldı; yanlışlıkla tekrar kullanılamaz).

### 3.2 Arama (Bug 2)
- [x] `BangumiApiClient.searchSubjects`: `nsfw` artık `Boolean?`; `false` → yalnız R18 olmayan, `null` → filtre yok.
  Gövde `buildSearchSubjectsPayload` içinde (test edilebilir). `limit` 1..20'ye sabitlendi (`SEARCH_PAGE_SIZE = 20`).
- [x] `BangumiApiClient.searchSubjectsLegacy` + `parseLegacySearchResponse`: eski uç yedeği
  (`air_date "0000-00-00"` → tarihsiz; `http://` görseller `https://`'e yükseltilir — `absoluteImageUrl`).
- [x] `KitsugiBangumiClient.searchWithFallback`: v0 hata verirse **ya da boş dönerse** (süzgeç yoksa) legacy uç denenir;
  ikisi de hata verirse ilk hata fırlatılır. `searchMediaAdvanced` artık hatayı yutmaz (UI gerçek mesajı gösterir),
  sayfa boyutu/offset aynı değerle hesaplanır. Yetişkin kapalıyken `nsfw=false` sunucuda uygulanır.
- [x] `SearchViewModel`: Bangumi `limit` ve "sonraki sayfa var mı" eşikleri 24 → 20 (tek motor, "Tümü" ve eski sekme yolu);
  "Tümü" rafında anime **ve** manga isteği birlikte hata verirse hata artık gösterilir.
- [x] `KitsugiBangumiClient.topRatedAnime`: boş anahtar kelimeli arama hata verirse `rank` sıralamasına düşer.

### 3.3 Detay sayfası (Bug 3) — yeni `data/remote/KitsugiBangumiDetailClient.kt`
**Strateji:** önce Bangumi-yerel veri (v0 + `next.bgm.tv/p1`), eksikler diğer platformlardan; sayfa yerel veriyle HEMEN açılır.

| Alan | Kaynak |
|---|---|
| Bilgi | v0 `/subjects/{id}` → başlık/özgün ad/takma adlar, özet, durum (yayın+bitiş tarihinden), sezon, başlangıç/bitiş, stüdyo (动画制作), yapımcı (製作), kanal, yayın günü, süre, puan/sıra/üye, etiketler, resmi site + `bgm.tv` bağlantısı |
| Bilgi (tamamlama) | `enrich`: AniList araması → MAL kimliği → ARM → TMDB/Kitsu/TVDB/IMDb; MAL/AniList/TMDB detayı ile türler, yaş sınırı, kaynak materyal, açılış/kapanış müzikleri, fragman, harici bağlantılar, ek görseller birleştirilir. Bangumi alanları (başlık, puan, sıra, kapak) korunur |
| Karakterler | p1 `/subjects/{id}/characters` (+ seslendirmenler, dil etiketli); yoksa MAL/AniList/TMDB |
| Ekip | p1 `/subjects/{id}/staffs/persons`; yoksa MAL/TMDB |
| İlişkiler | p1 `/subjects/{id}/relations` (Çince ilişki adları → MAL sözlüğü → Türkçe); yoksa MAL/AniList/TMDB |
| Öneriler | p1 `/subjects/{id}/recs` (sunucu en çok 10 verir); az ise MAL/AniList/TMDB ile tamamlanır |
| Grafikler | v0 yanıtından: koleksiyon durumu, 1-10 puan dağılımı, tüm zamanlar sırası (ek istek yok); boşsa AniList/MAL |
| Yorumlar | p1 `/reviews` (tam metin `/blogs/{id}`) + `/comments` (puanlı); az ise MAL/AniList |
| Bölümler | p1 `/subjects/{id}/episodes?type=0`; TMDB eşlemesiyle küçük resim/ad tamamlanır |
| Karakter / kişi sayfaları | p1 `/characters/{id}`(+`/casts`), `/persons/{id}`(+`/works`, `/casts`) |

- [x] **Çapraz kimlik çözümü** (`resolveCrossIds`): ARM'de Bangumi anahtarı yok → AniList araması (özgün ad + takma ad,
  **başlık + yıl ± 1 + bölüm sayısı + format puanlaması**, eşik 5; yıl farkı ≥ 2 elenir) → Jikan yedeği → ARM.
  Sonuç `SharedPreferences`'ta kalıcı, olumsuz sonuç 20 dk bellekte önbelleklenir. Yanlış eşleşme yerine "eşleşme yok".
  Çözüm, çağıranın değil uygulama kapsamının içinde **tek uçuşta** çalışır (detay, galeri ve sekmeler aynı anda istese de tek çözüm yürür;
  bir çağıran süre aşımıyla iptal olsa bile iş yarım kalmaz, bitince önbelleğe yazılır). Detay zenginleştirme tavanı: 7 sn kimlik + 7 sn MAL/AniList/TMDB
  (ViewModel'in 20 sn zenginleştirme tavanının altında); süre dolarsa Bangumi-yerel detay + o ana kadar çözülen kimliklerle devam edilir.
- [x] `KitsugiDetailClient`: `fetchPrimaryDetail` / `fetchSynopsis` / `enrichDetail` "bangumi" dalları; Room anahtarına `_bgm1`
  eklendi (eski yanlış satırlar okunmaz, eski anahtar da okunmaz).
- [x] `KitsugiCharacterClient`, `KitsugiStaffClient`, `KitsugiMediaRelationsClient`, `KitsugiMediaSocialClient`,
  `KitsugiMediaTabsClient`: "bangumi" dalları (yerel → çapraz kaynak yedeği); `realMalId` olarak gelen stableId (500M+) süzülür.
- [x] `ApiResultDetailViewModel` + `MediaEntryDetailViewModel`: Fanart.tv / TMDB / Shikimori galerisi, bölüm puanları, logo, MDBList
  çözülen çapraz kimliklerle çalışır; Bangumi galerisi detay geldikten sonra **her zaman** bir kez yenilenir;
  `lain.bgm.tv` boyut varyantları tek görsele indirilir (`galleryDedupKey`); TMDB kapalıyken de Bangumi birleştirmesi çalışır.
- [x] Küçük düzeltmeler: `ShareUtils` (bgm.tv bağlantıları), `DetailSharedComponents` (rozet rengi), `StaffDetailViewModel`
  (Bangumi kişi kimliği Jikan `/people/{id}/pictures`'a gitmez), `KitsugiStudiosCard` (kimliksiz Bangumi stüdyo çipleri tıklanamaz).

---

## 4. Doğrulama

| Kontrol | Sonuç |
|---|---|
| Sözdizimi (tree-sitter-kotlin) — değişen tüm dosyalar | ✅ |
| K2 tip denetimi (Kotlin 2.4.21, `android.jar` + stdlib + coroutines; AndroidX/OkHttp/Compose jar'ları yok) — taban çizgisiyle dosya-dosya karşılaştırma | ✅ Yeni/değişen dosyalarda **yeni gerçek hata yok**; taban çizgisine göre yalnızca 3 ek "unresolved reference" (OkHttp `Request`/`build`, Compose `Color`: eksik jar gürültüsü). `KitsugiBangumiDetailClient.kt`: 0 hata |
| Birim testleri (gerçek kaynak kodundan ayıklanıp `org.json` taklidi ile çalıştırıldı) | ✅ 36/36 (`KitsugiBangumiDetailClientTest` 29, `BangumiApiClientSearchTest` 7) |
| `./gradlew :app:testFossDebugUnitTest` / `assembleFossDebug` | ⏳ Bu ortamda Gradle/Android SDK yok → **CI'de ya da cihazda çalıştırılmalı** |

Eklenen testler: `app/src/test/java/com/kitsugi/animelist/data/remote/KitsugiBangumiDetailClientTest.kt`,
`app/src/test/java/com/kitsugi/animelist/data/auth/BangumiApiClientSearchTest.kt`
(gerçek Clannad AS yanıtı, p1 karakter/ekip/ilişki/öneri/bölüm/inceleme biçimleri, `nsfw` JSON boolean, legacy yanıt, kimlik/tarih/durum/sezon, galeri tekilleştirme, birleştirme).

---

## 5. Cihazda test listesi

```
./gradlew :app:testFossDebugUnitTest --tests '*Bangumi*'
./gradlew assembleFossDebug
```
1. Bangumi'ye bağlan → liste otomatik içe aktarılmalı; Listem → 🎌 Bangumi sekmesinde kayıtlar görünmeli (404 snackbar'ı çıkmamalı).
2. Arama → 🎌 BGM → "date a live", "clannad", "进击的巨人", "naruto": sonuç gelmeli; aşağı kaydırınca 2. sayfa (20'şer) gelmeli.
   Ayarlar'da yetişkin içerik AÇIKken de aynı sorgular sonuç vermeli.
3. Clannad ~After Story~ (Bangumi) detayı:
   - Bilgi: Durum/Sezon/Başlangıç/Bitiş/Kaynak/Stüdyo/Yayın/Yaş Sınırı/İngilizce/Japonca/Diğer Adlar dolu; tür, etiket, harici bağlantılar (MAL, AniList, IMDb…) görünmeli.
   - Resimler: Bangumi kapağı **bir kez**; TMDB / Fanart.tv / Shikimori / MAL görselleri de listelenmeli.
   - Karakterler (seslendirmenli), Ekip, Öneriler, İlişkiler, Grafikler, Yorumlar, Bölümler dolu olmalı; karaktere/kişiye dokununca detay sayfası açılmalı.
4. Manga (Bangumi `书籍`) kaydı: Bilgi/Karakter/Ekip/İlişki/Yorum sekmeleri dolu olmalı (galeri yalnızca eserin kendi görselleri).
5. Eşleşme bulunamayan nadir bir kayıt: sayfa yine Bangumi-yerel veriyle (bilgi, karakter, ekip, ilişki, istatistik, yorum, bölüm) dolu açılmalı.

## 6. Bilinen kısıtlar

1. Çapraz eşleme AniList/Jikan başlık araması ile yapılır (Bangumi'de MAL/AniList kimliği veren uç yok). Başlık/yıl/bölüm puanı
   eşiğin altında kalan nadir kayıtlarda TMDB/Fanart.tv/Shikimori görselleri ve MAL/AniList yedek verileri gelmez
   (Bangumi-yerel sekmeler yine dolar). İsteğe bağlı iyileştirme: BangumiExtLinker `anime_map.json` (günlük güncellenen Bangumi→MAL/TMDB haritası) çevrimdışı eşleme.
2. Bangumi karakter/kişi adları Çince/Japonca gösterilir (Bangumi'de Latin harfli ad alanı yok).
3. Bangumi-yerel stüdyo/yapımcı çipleri (infobox'taki ad) tıklanamaz; MAL eşleşmesi varsa MAL stüdyoları (tıklanabilir) kullanılır.
4. Bu ortamdan canlı `POST /v0/search/subjects` denenemedi; arama kök nedeni kod + sunucu kaynağı üzerinden belirlendi. Legacy yedek
   sayesinde v0 ucu hata verse ya da boş dönse de sonuç gelir; ikisi de hata verirse gerçek hata mesajı gösterilir.
