# PLAN / TASK — TMDB & Simkl hayali (kurgusal) karakter görselleri + MAL tek oturum & bildirim devamı

Tarih: 2026-10-13
Sürüm: v2.4.270

## Kullanıcı geri bildirimi (3 başlık)

1. **TMDB veya Simkl detay sayfalarında hayali karakterlerin görselleri gelmiyor** — hem
   karakter listesinde hem de karakter detay sayfası açıldığında.
2. **MAL bildirimlerinin devamı gelmiyor** (kaydırınca eski bildirimler yüklenmiyor).
3. **Normal MAL girişi bildirimleri açmıyor** — bildirim penceresindeki girişi kullanmak
   gerekiyor. İstenen: *uygulama MAL API'si + web MAL API'si tek girişle aktif olsun.*
   ("İki kere hesap girmek zorunda kalmayalım.")

---

## 1) Kurgusal karakter görselleri (TMDB / Simkl)

### Kök neden 1 — "gerçek çekim" kararı yalnızca medya türüne bakıyordu

`KitsugiCharacterClient.fetchCharacters` içinde TMDB ve Simkl dalları:

```kotlin
// TMDB
val isRealMedia = mediaType == MediaType.TvShow || (mediaType == MediaType.Movie && realMalId == null)
// Simkl
val isRealMedia = simklDetail?.type == TvShow || simklDetail?.type == Movie ||
                  mediaType == TvShow || (mediaType == Movie && malId == null)
```

`isRealMedia == true` ise liste **hiç zenginleştirilmeden** döndürülüyordu
(`tmdbChars.map { it.copy(isRealMediaRole = true) }`) → AniList/MAL/Jikan/Kitsu görsel
zinciri hiç çalışmıyor. Oysa TMDB ve Simkl'de **anime kayıtları çoğunlukla `Movie` /
`TvShow` türünde** gelir. Sonuç: anime karakterleri "gerçek kişi" sayıldı ve kartta
yalnızca seslendirmen fotoğrafı (TMDB `character` alanı `(voice)` içeriyorsa **hiçbir şey**)
göründü.

**Çözüm:** karar VERİYLE verilir → yeni `looksAnimatedOrAnime(detail, mediaType)`:
`MediaType.Anime`, `detail.type == Anime`, tür/etiket adında `anim`/`anime` geçmesi,
Doğu Asya kökeni + animasyon biçimi. Sinyal yoksa eski (tür tabanlı) davranış korunur.

### Kök neden 2 — MAL kimliği yokken çapraz kaynak zinciri hiç açılmıyordu

`enrichFromCrossSourceCharacterLists` (Jikan → Shikimori → Kitsu) `realMalId` yoksa ilk
satırda dönüyordu. TMDB/Simkl kayıtlarında MAL kimliği çoğu zaman bilinmez.

**Çözüm:** `resolveMalIdFromTmdb()` — ARM (anime ilişki haritası) üzerinden
TMDB kimliğinden gerçek MAL kimliğini çözer (süre bütçeli, tek deneme).
Ek olarak `fetchAniListMediaCharacters` artık AniList'in başlıktan çözdüğü yapımın
`idMal` değerini de döndürüyor; böylece yalnızca AniList'in bulabildiği yapımlarda da
Jikan/Shikimori/Kitsu yedekleri açılıyor (`AniListEnrichResult.resolvedMalId`).

### Kök neden 3 — tek kelimeli Latin karakter adları hiç aranamıyordu

`KitsugiPersonImageAggregator.isUnambiguousName`: tek kelimelik adı yalnızca CJK ise kabul
ediyordu. Kurgusal karakter adlarının büyük bölümü tek kelime ve Latin: **Nobita,
Doraemon, Goku, Eren, Luffy, Spike…** Bu adlar reddedildiği için
`fetchAniListCharacterByName` (karakter detay sayfası) doğrudan `null` dönüyordu.

**Çözüm:** ikinci imza `isUnambiguousName(value, allowSingleTokenLatin)`. Yalnızca çağıran
sonucu doğrulayabildiğinde açılır:
- detay sayfasında `isVerifiedAniListCharacterNode` **birebir ad eşleşmesi** zorunludur
  (tam ad/alternatif adlar) + +18 yapım elemesi,
- galeri toplayıcısında karakterin MAL/AniList kimliği biliniyorsa açılır.

### Kök neden 4 — doğru sonuç "kimlik uyuşmuyor" diye çöpe atılıyordu

TMDB `Nobita` yazar, AniList `Nobita Nobi`. `CharacterDetailIdentity.matchesExpectedDetail`
yalnızca birebir ad eşleşmesi kabul ettiği için çözülen doğru karakter reddediliyor, sayfa
"Karakter detayları yüklenemedi" hatasına düşüyordu.

**Çözüm:** `KitsugiCharacterDetail.withRoleAlias(cleanCharName)` — TMDB rol adı detayın
`alternativeNames` listesine eklenir → kimlik doğrulanır, sayfa doğru karakterle açılır.
Ayrıca hiçbir kaynak çözülemediğinde artık `null` yerine kurgusal karakterin kimliğini
(adını) koruyan en az alanlı bir detay döner (hata ekranı yerine boş içerikli sayfa).

### Kök neden 5 — karakter adı aramaları ardışıktı

8 karakter sırayla aranıyordu; tek bir yavaş AniList yanıtı bütçeyi bitiriyordu
("listede yalnızca ilk birkaç karakterin resmi geliyor").

**Çözüm:** aramalar paralel (`async`), bütçe 6 sn → 9 sn, sonuçlar tamamlandıkça yazılır
(timeout olsa bile o ana kadarki görseller kaybolmaz).

### Değişen dosyalar
- `data/remote/KitsugiCharacterClient.kt` (yukarıdakilerin tamamı)
- `data/remote/KitsugiPersonImageAggregator.kt` (`isUnambiguousName` 2. imza + `aggregate(allowSingleTokenNames=)`)
- `ui/screens/detail/CharacterDetailViewModel.kt` (galeri: kimlik biliniyorsa tek kelimeli adlara izin)

---

## 2) MAL bildirimlerinin devamı

MAL'ın özel bildirim akışı için belgelenmiş bir sayfalama adresi yok. Eski kod yalnızca
`?page=N` deniyor, MAL aynı sayfayı döndürünce `hasMore = false` yapıp listeyi
kilitliyordu.

**Çözüm:**
- `MalNotificationsClient.fetchMore(cookie, page, knownIds)`: aday adresleri sırayla dener
  (`?page=N`, `?p=N`, `/history?page=N`, `/history?p=N`); **yeni kayıt getiren ilk adres**
  kabul edilir, hiçbiri getirmezse akış biter (uydurma kart üretilmez).
- Yanlış adres tahminleri oturum kararını bozmaz: yalnızca hiçbir aday sayfa döndürmediyse
  ve gerçek bir "oturum yok" yanıtı alındıysa `MalWebSessionRequiredException` yükseltilir.
- `parseHtml` artık yalnızca `historyItems` içeren sayfaları da ayrıştırıyor (eski koşul
  `items` alanını zorunlu sayıyordu; MAL bazı oturumlarda tüm kayıtları `historyItems`
  altında döndürüyor → "devam hiç gelmiyor").
- `KitsugiNotificationsViewModel.loadMalMore` yeni `fetchMore` akışını kullanır; oturum
  düşerse liste korunur ve yeniden giriş kartı gösterilir.

### Testler
- `MalNotificationsClientTest.parsesPageThatCarriesOnlyHistoryItems`
- `MalNotificationsClientTest.offersSeveralUrlCandidatesForOlderNotifications`

---

## 3) MAL tek oturum (uygulama API'si + web API'si)

MAL iki ayrı kimlik kullanır:

| Kimlik | Taşıyıcı | Açtığı özellikler |
|---|---|---|
| OAuth 2 erişim jetonu | `ExternalAuthManager` | liste okuma/yazma, profil, senkron |
| Web oturumu (çerez) | WebView çerez deposu | **bildirimler** (`myanimelist.net/notification`) |

Kullanıcı şikâyeti: normal (OAuth) girişten sonra bildirimler gelmiyor. Kök neden: OAuth
girişi jetonu bağlıyor ama web oturumu ya hiç kurulmuyor ya da **başka bir MAL hesabına**
ait kalıyor; `MalAccountMatch` hesaplar uyuşmadığında bildirimleri bilinçli olarak gizliyor
(başkasının bildirimlerini göstermemek için doğru davranış).

**Çözüm — `MalSessionCoordinator` (yeni, `data/auth`):**
- `probe(context, verifyWeb)`: iki kimliği okur; web oturumunun sahibi **MAL'a sorularak**
  doğrulanır (çerez adına bakılmaz). Durum: `NOT_LINKED / API_ONLY / WEB_ONLY / LINKED /
  MISMATCHED / UNVERIFIED`.
- `verifyAndPersist(context)`: doğrular ve sonucu saklar (`mal_web_username`).
- `clearWebSessionOnly()`: **yalnızca** MAL çerezlerini temizler; bağlı OAuth hesabı
  kesilmez (diğer platformların oturumlarına dokunulmaz).
- `needsWebRelogin(status)`.

**Akış (`AuthViewModel`):**
1. `startMalOAuthWebSession()` / `reconnectMalWebSession()` → uygulama içi OAuth WebView.
2. Giriş penceresi kapanır kapanmaz `finalizeMalUnifiedSession()` çalışır.
3. `LINKED` → "MAL: uygulama API'si + web oturumu tek hesapta bağlı (kullanıcı)".
4. `API_ONLY` / `MISMATCHED` → yalnızca MAL çerezleri temizlenir, aynı akış **bir kez**
   daha açılır (`malWebReloginAttempted` ile döngü koruması) ve kullanıcıya ne olduğu
   yazılır. Bu kez MAL giriş formu görünür; kullanıcı OAuth hesabıyla **aynı** hesaba web
   oturumu da açar → tek hesap, iki kimlik.
5. `ensureMalUnifiedSession()`: bildirimler ekranındaki "MAL oturumunu bağla" ve
   Ayarlar → MyAnimeList sayfasındaki "Tek girişle ikisini de bağla" düğmesi aynı akışı
   kullanır. Artık bildirim penceresinden ayrıca ayarlar sayfasına gitmek gerekmez.
6. MAL bağlantısı kesildiğinde kaydedilen web kimliği de unutulur.
7. Doğrulama yalnızca giriş GERÇEKTEN tamamlandığında yapılır (`noteOAuthSuccess`):
   pencere iptal edilirse hiçbir şey olmaz, pencere kendiliğinden yeniden açılmaz.

**Bildirim listesi kendini tazeler:** `AuthViewModel.malSessionReadyToken` sayacı, tek oturum
kurulduğunda artar; bildirimler ekranı bunu izleyip MAL listesini yeniden çeker, böylece
"yeniden giriş yapın" kartında kilitli kalmaz.

**Arayüz:**
- Ayarlar → MyAnimeList sayfasına "MAL tek oturum" kartı eklendi: API satırı, web satırı,
  özet ve (bağlı değilse) tek dokunuşluk onarım düğmesi.
- Bildirimler ekranındaki MAL yeniden bağlanma kartı: hesap bağlıysa doğrudan tek giriş
  akışını açar (ayarlara yönlendirme yok).

**Yeni TR/EN metinler:** `mal_unified_*` (14 × 2 dil).

---

## Doğrulama

- [x] Yeni saf mantık için birim testleri (`isUnambiguousName` 2. imza,
      `moreUrlCandidates`, yalnızca `historyItems` içeren sayfa).
- [x] TR/EN dize dosyaları XML olarak doğrulandı ve iki dosyada da 14'er yeni kayıt var.
- [ ] **Gradle derlemesi / testler:** bu ortamda JDK bulunmadığından çalıştırılamadı
      (önceki sürümlerde de aynı kısıt).
- [ ] Cihazda uçtan uca:
      1. TMDB kaynaklı bir animasyon film/seri → Karakterler sekmesi: kurgusal karakterlerin
         AniList/MAL görselleri gelmeli; karta dokununca detay doğru karakterle açılmalı.
      2. Simkl kaynaklı bir anime → aynı kontrol + seslendirmen listesi.
      3. Ayarlar → MyAnimeList: "MAL tek oturum" kartı API + web durumunu göstermeli.
      4. Bildirimler → MyAnimeList: aşağı kaydırınca daha eski bildirimler yüklenmeli.
      5. MAL bağlantısını kes → tek giriş → bildirimler ikinci bir giriş olmadan gelmeli.

## Risk / sınırlar

- MAL'ın bildirim sayfası için belgelenmiş sayfalama yok: `fetchMore` aday adresleri
  VERİYLE doğrular, desteklenmiyorsa akış tek denemede kapanır (eski davranışın aynısı,
  ek maliyet yalnızca 3 deneme isteği).
- `looksAnimatedOrAnime` hiçbir sinyal bulamazsa eski davranışa döner; TMDB/Simkl detay
  istemcileri `genres` alanını doldurduğu için anime kayıtlarında sinyal bulunur.
- Tek kelimeli adlarla yapılan AniList araması birebir ad doğrulamasına bağlıdır; aynı
  adlı karakterlerde ilk doğrulanan sonuç kullanılır (yapım kimliği doğrulaması yalnızca
  MAL kimliği bilindiğinde uygulanır).
