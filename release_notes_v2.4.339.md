# Kitsugi Beta v2.4.339

## 🇹🇷 Türkçe

### ⚡ Bangumi Performansı — "Veri 5 Dakika Gelmiyor" Kök Düzeltmesi
- **4,5 dk'lık gecikmenin kaynağı bulundu:** Bangumi isteği başına **450 ms rezervasyon** yapan eski hız sınırlayıcı, iptal edilen/zaman aşımına uğrayan isteklerde de saati ileri taşıyordu. Keşfet'te 14 şerit aynı anda açıldığında kuyruk dakikalarca ileri gidiyor, ardından gelen *her* Bangumi isteği (detay, arama, kütüphane) bu kuyrukta bekliyordu.
  - Yerine **5 istek/sn + 5 patlama** jeton kovası geldi (Bangumi'nin resmî tavanı 3000 istek/10 dk). Bekleme `delay` ile yapılır → iptal edilen istek kredi harcamaz.
  - Arka plan işleri (kütüphane tazeleme, içe aktarma) kovadan **en az 2 jeton varken** pay alır; kullanıcı bir sekme açtığında arka plan taraması kalan jetonları tüketmez.
- **Fail-fast Bangumi HTTP istemcisi:** `bangumiClient` 10/12/12 sn + 14 sn toplam tavan, **yeniden deneme yok**. Eski istemci her denemeyi 3'e katlıyordu (20 sn × 3 = 60 sn/istek).
- **p1 (`next.bgm.tv`) dayanıklılığı:** aynı istek tek uçuşta paylaşılır, 8 sn tavan, başarısız yol 60 sn geri çekilme, üst üste 4 ağ hatasında 90 sn kısa devre; token yalnızca 401/403'te tekrar denenir.
- **İstek seli bitti:** Keşfet/Arama şeritleri ve kütüphane artık **liste modunda** çalışır (kalıcı önbellek + tek AniList toplu araması). Eskiden liste başına 40, Keşfet'te toplamda 500+ `/v0/subjects/{id}` isteği üretiliyordu.

- **Keşfet şeritlerine 15 sn tavan:** asılı kalan tek bir istek tüm Bangumi vitrinini dakikalarca boş bırakamaz.
- **Token yenileme kısa devresi (60 sn):** süresi dolmuş bir oturumda her Bangumi isteği yeniden yenileme denemesi sıraya giriyordu.

### 🌐 Dil Seçeneği Artık Her Yerde Uygulanıyor
- **Sekmelerde aşamalı boyama:** Karakterler, Ekip, Öneriler ve İlişkiler sekmeleri önce **ham veriyi anında** gösterir; Latin adlar/başlıklar arka planda çözülüp aynı sekmeye yazılır. Sekme artık AniList/Bangumi zincirini beklemez.
- **Önbellekten açılan sekmeler de zenginleştirilir:** Eskiden zenginleştirme yalnızca ilk ağ çekiminde çalışıyordu; sekmeye ikinci kez girildiğinde ham (kanji) liste kalıcı olarak öyle kalıyordu. Düzeltildi.
- **AniList + Bangumi infobox artık paralel:** İlişki/öneri/yapım başlıkları için AniList toplu araması ile sınırlı infobox yedeği eşzamanlı çalışır (6 sn + 8 sn sırayla değil). İlişkilerde infobox tavanı 12 → 32.
- **Karakter/kişi adları kalıcı önbellekte:** `bangumi_entity_names_v1` deposu ile bir kez çözülen romaji/İngilizce ad diske yazılır; uygulama yeniden açıldığında veya aynı seslendirmen başka bir yapımda geçtiğinde ağ isteği yapılmaz.
- **p1 erişilemezse v0 yedeği:** Karakter/kişi infobox'ı `api.bgm.tv` üzerinden de okunur.
- **"Personel adı dili" ayarı sekmelere taşındı:** Karakterler/Ekip sekmeleri ve karakter sayfası artık bu ayarı açıkça alır (başlık diliyle karışmaz); seslendirmen adları da aynı ayara uyar.
- **Latin ad ana ada yazılır:** `name` alanı kanji kalıp yalnızca `romanizedName`/`englishName` doluyken arama, çip ve çapraz eşleştirme yolları Japonca gösteriyordu; artık Latin karşılık ana ada da yazılır, özgün ad `nativeName`'de korunur.

- **Karakter/kişi adı çözümü artık yarışta:** `/v0/characters|persons/{id}` ile `/p1/...` **paralel** istenir, ilk yanıt veren kullanılır ve kaybeden istek iptal edilir. `next.bgm.tv` yavaşladığında adlar sırayla 8 sn beklemiyor; v0 genellikle 300-600 ms'de yanıtlar.
- **Olumsuz ad önbelleği 10 saatten 2 saate indirildi:** kısa süreli bir ağ kesintisi, seçilen dilin saatlerce yok sayılmasına (adların Japonca kalmasına) yol açıyordu.
- **Seslendirmen adları da `staffNameLanguage` ayarına uyar** (eskiden başlık diline bağlıydı).
- **Başlık önbelleği ana iş parçacığında disk okumaz:** liste kartlarındaki `get()` çağrıları `SharedPreferences.all` okumasını tetikleyip kare düşürüyordu.

### 🧮 `getIdentifier` Fırtınası Sona Erdi
- Uygulamada ~40.000 `bangumi_*` dizesi var; Android'in `Resources.getIdentifier` çağrısı **her seferinde tüm kaynak tablosunu tarar** ve `Resources` kilidi yüzünden tüm iş parçacıklarını sıraya sokar.
  - Görev adları (`staff_role_*`), tür/etiket çipleri ve kurum adları artık **önbellekli** okunur (bulunmayanlar da önbelleğe yazılır).
  - Türkçe yerelde `I → ı` dönüşümü kaynak anahtarlarını bozuyordu; anahtarlar `Locale.ROOT` ile üretilir.

### 🧪 Doğrulama
- `parseP1Infobox_readsKeyValueArrays` testi alt anahtar (`k`) indekslemesini de doğrular.
- Derleme öncesi statik denge/çağrı yeri taramaları yapıldı (29 dosya değişti, 1 yeni dosya).

---

## 🇬🇧 English

### ⚡ Bangumi Performance — Root Cause of the "Data Takes 5 Minutes" Report
- **Root cause found:** the old limiter reserved a **450 ms slot per Bangumi request** even when the request was cancelled or timed out. Opening 14 Explore rails pushed the reservation clock minutes into the future, so *every* subsequent Bangumi call (detail, search, library) sat in that queue.
  - Replaced with a **5 req/s + burst 5** token bucket (Bangumi's documented cap is 3000 req/10 min); waiting uses `delay`, so cancelled callers never consume credit.
  - Background work (library hydration, imports) only takes a token when **at least 2** are available, keeping interactive tabs responsive.
- **Fail-fast Bangumi HTTP client:** `bangumiClient` (10/12/12 s + 14 s call timeout, **no retries**). The previous client tripled every attempt (20 s × 3 = 60 s per request).
- **p1 (`next.bgm.tv`) hardening:** single-flight per path, 8 s cap, 60 s per-path backoff, 90 s breaker after 4 consecutive network failures; tokens are only retried on 401/403.
- **Request flood removed:** Explore/Search rails and library hydration run in **list mode** (persistent cache + one batched AniList pass). Previously each rail could issue 40 `/v0/subjects/{id}` calls — 500+ on a single Explore load.

- **15 s ceiling per Explore rail:** one hung request can no longer leave the whole Bangumi showcase empty for minutes.
- **60 s token-refresh breaker:** with an expired session, every Bangumi call used to queue another refresh attempt.

### 🌐 Language Preference Now Applied Everywhere
- **Progressive tab painting:** Characters, Staff, Recommendations and Relations render the **raw payload instantly**; Latin names/titles are resolved in the background and pushed into the same tab state.
- **Cached tabs are enriched too:** enrichment previously ran only on the first network fetch, so a tab re-opened from cache stayed Japanese forever. Fixed.
- **AniList + Bangumi infobox run in parallel** (previously 6 s + 8 s sequentially). Tab infobox budget raised from 12 to 32 entries.
- **Persistent character/person name store** (`bangumi_entity_names_v1`): a resolved romaji/English name is written to disk and reused across media and app restarts.
- **v0 fallback for entity infoboxes** when `next.bgm.tv` is unreachable.
- **Staff-name language propagated** to the Characters/Staff tabs and character screen; voice-actor names follow the same preference.
- **Latin names written to the primary `name` field** while the native name is preserved in `nativeName`.

- **Entity-name resolution now races:** `/v0/characters|persons/{id}` and `/p1/...` are requested **in parallel**; the first usable response wins and the loser is cancelled. When `next.bgm.tv` is slow, names no longer wait 8 s each — v0 typically answers in 300-600 ms.
- **Negative name cache reduced from 10 h to 2 h:** a short network outage used to keep the selected language ignored (names stuck in Japanese) for hours.
- **Voice-actor names follow `staffNameLanguage`** (previously tied to the title language).
- **The title cache no longer reads disk on the main thread:** `get()` from list cards triggered a `SharedPreferences.all` read and dropped frames.

### 🧮 `getIdentifier` Storm Eliminated
- The app ships ~40,000 `bangumi_*` string resources and Android's `Resources.getIdentifier` scans the whole table on every call, serialising threads behind a global lock.
  - Staff roles, genre/tag chips and organisation names now use a **cached lookup** (misses are cached too).
  - Resource keys are normalised with `Locale.ROOT` (Turkish `I → ı` used to break keys).
