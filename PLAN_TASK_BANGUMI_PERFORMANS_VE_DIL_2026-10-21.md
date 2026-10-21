# PLAN / TASK — Bangumi Performansı ve Dil Tercihi (v2.4.339)

**Tarih:** 2026-10-21
**Dal:** `arena/0bb968c6-kitsugi-beta`
**Commit:** `7401010` (taban: `4adf2a7` / v2.4.338)
**Kapsam:** 31 dosya (29 değişti, 2 yeni) · +1642 / −294 satır

---

## 1. Kullanıcı Şikâyeti

1. "Bangumi verileri en az **5 dakika** olmak üzere çok geç geliyor; hesap bağlansın bağlanmasın."
2. "Bangumi **dil seçeneğine uymuyor**; karakter, ekip, ilişki ve öneriler Japonca kalıyor."
3. Karakter detayındaki "Yapımlar" listesinde başlıkların bir kısmı İngilizce, bir kısmı Japonca (tutarsız).
4. "Bangumi her yerde veriler gelmiyor." (Keşfet, Arama, Detay sekmeleri, Profil)

---

## 2. Kök Neden Analizi

| # | Kök neden | Etki |
|---|-----------|------|
| 1 | Eski hız sınırlayıcı istek başına **450 ms rezervasyon** yazıyor, iptal/zaman aşımında da saati ilerletiyordu | Keşfet'te 14 şerit açılınca kuyruk dakikalarca ileri gidiyor; sonraki **tüm** Bangumi istekleri (detay, arama, kütüphane) bu kuyrukta bekliyor |
| 2 | Ortak HTTP istemcisi `RetryInterceptor(2)` | Her başarısız deneme ×3 → tek istek 20 sn × 3 = **60 sn** |
| 3 | Liste başına 20–40 ayrı `/v0/subjects/{id}` infobox çağrısı | Keşfet'te toplam **500+** istek, dakikalarca kilit |
| 4 | `next.bgm.tv/p1` beta host'u bazı ağlarda yanıt vermiyor | Karakter/ekip sekmeleri ve ad çözümü 8 sn × N bekliyor, sonra boş dönüyor |
| 5 | Karakter/kişi adı yalnızca varlık başına infobox'tan geliyor; sonuç diske yazılmıyordu | Aynı seslendirmen her yapımda yeniden çözülüyor |
| 6 | Olumsuz ad önbelleği **10 saat** | Kısa bir ağ kesintisi, seçilen dilin saatlerce yok sayılmasına yol açıyor |
| 7 | Aşamalı boyama yok | Sekme, AniList/Bangumi zincirini bitirene kadar skeleton'da kalıyordu |
| 8 | `getIdentifier` her çağrıda ~41.000 dizelik tabloyu tarıyor | Etiket/görev/rol çipleri toplu işlendiğinde UI kilitleniyor |
| 9 | Türkçe yerelde `lowercase()` → `I` harfi `ı` oluyor | `genre_*` / `tag_*` / `staff_role_*` anahtarları bozuluyor, dil karşılığı bulunamıyordu |
| 10 | `BangumiTitleCache` ana iş parçacığında `SharedPreferences.all` okuyabiliyordu | Liste kartlarında kare düşmesi |

---

## 3. Yapılan İşler

### 3.1 Ağ katmanı
- [x] `KitsugiHttpClient.bangumiClient`: 10/12/12 sn + **14 sn toplam** tavan, **retry yok** (fail-fast).
- [x] `BangumiApiClient`: 450 ms rezervasyon → **5 istek/sn + 5 patlama jeton kovası**; bekleme `delay` ile (iptal edilebilir, kredi harcamaz).
- [x] `BackgroundTraffic` / `runAsBackground`: arka plan işleri kovadan **en az 2 jeton** varken pay alır (etkileşimli istekler aç kalmasın).
- [x] `BangumiAuthStore`: başarısız token yenilemeden sonra **60 sn kısa devre** (14 şerit sıraya girmiyor).
- [x] `p1()`: tek uçuş (`p1InFlight`), **8 sn** çağrı tavanı, yol başına 60 sn geri çekilme, üst üste 4 ağ hatasında **90 sn kısa devre**; token **yalnızca 401/403'te** tekrar denenir.

### 3.2 Başlık çözümleme (`BangumiSubjectTitleResolver`)
- [x] **Liste modu**: yalnızca kalıcı önbellek + tek AniList toplu araması (≤24, 3,5 sn). Per-subject infobox **yok**.
- [x] **Sekme modu**: AniList (≤64, 6 sn) **∥** infobox (≤32, 8 sn) — süreler toplanmıyor.
- [x] Şerit kilidi (stripe lock) kaldırıldı; 14 şerit birbirini sıraya sokmuyor.
- [x] `bangumiCachedTitleOrNull` / `BangumiTitleCache.mergeTitles` ile kısmi güncellemeler eski alanları silmiyor.
- [x] `BangumiTitleCache`: ana iş parçacığında disk okumaz; `warmUp()` IO kapsamında.
- [x] `BangumiLibraryTitleHydrator`: `runAsBackground` + `tabMode`, dilim 16 → **8** (960 satır).

### 3.3 Karakter / kişi adları
- [x] **`bangumi_entity_names_v1`** kalıcı store (`BangumiEntityNameStore`, 4.000 kayıt, yalnız olumlu kayıt).
- [x] **v0 ↔ p1 yarışı**: `/v0/characters|persons/{id}` ile `/p1/...` **paralel**; ilk yanıt kazanır, kaybeden iptal edilir (`loadEntityRoot`, `select`).
- [x] Olumsuz TTL **10 sa → 2 sa**.
- [x] `withStoredName`: p1 liste satırlarında diskteki Latin ad anında uygulanır.
- [x] Sorgu bütçesi 60 → **32**, tek seferlik 3,5 sn → toplu 8 sn; önce **karakterler**, sonra seslendirmenler.
- [x] Latin karşılık **ana `name` alanına** yazılır, özgün ad `nativeName`'de korunur (`withLatinDisplayName`).

### 3.4 Detay sekmelerinde aşamalı boyama
- [x] `ApiResultDetailViewModel` / `MediaEntryDetailViewModel`: ham liste **anında** `Success`, zenginleştirme `enrichBangumiTabInBackground` (20 sn bütçe) ile arka planda.
- [x] Zenginleştirme önbellekten açılan sekmelerde de çalışır (eskiden yalnızca ilk ağ çekiminde çalışıyordu).
- [x] Tek uçuş anahtarı `"sekme|istek"`; eski istek geldiğinde state ezilmiyor (`tabStillCurrent`).
- [x] `enrichNames` / `enrichTitles` = `!isBangumiDetailSource(...)` (8 nokta).

### 3.5 Dil tercihi
- [x] `staffNameLanguage` (`ROMAJI` / `ENGLISH` / `NATIVE`) detay sayfalarına, Karakterler/Ekip sekmelerine ve karakter sayfasına taşındı.
- [x] Seslendirmen adları da `staffNameLanguage`'a uyar (eskiden başlık diline bağlıydı).
- [x] `Locale.ROOT` normalizasyonu (`SearchTranslation`, `KitsugiTranslations`).
- [x] `cachedStringResourceByName`: bulunan **ve bulunmayan** kaynaklar önbelleğe yazılır (getIdentifier fırtınası biter).

### 3.6 Keşfet
- [x] 14 şerit paralel + **şerit başına 15 sn tavan** (`BANGUMI_SHELF_TIMEOUT_MS`); asılı tek istek vitrini kilitlemiyor.

---

## 4. Değişen Dosyalar

### Yeni
| Dosya | Satır | Amaç |
|-------|-------|------|
| `app/.../data/remote/BangumiEntityNameStore.kt` | 117 | Kalıcı karakter/kişi Latin ad önbelleği |
| `release_notes_v2.4.339.md` | 71 | Sürüm notları |
| `PLAN_TASK_BANGUMI_PERFORMANS_VE_DIL_2026-10-21.md` | — | Bu dosya |

### Değişen (29)
| Dosya | +/- |
|-------|-----|
| `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiBangumiDetailClient.kt` | +541 |
| `app/src/main/java/com/kitsugi/animelist/data/remote/BangumiSubjectTitleResolver.kt` | +199 |
| `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiBangumiDetailClientTest.kt` | +3 |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailViewModel.kt` | +137 |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/MediaEntryDetailViewModel.kt` | +163 |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/ExploreViewModel.kt` | +59 |
| `app/src/main/java/com/kitsugi/animelist/data/auth/BangumiApiClient.kt` | +155 |
| `app/src/main/java/com/kitsugi/animelist/data/auth/BangumiAuthStore.kt` | +17 |
| `app/src/main/java/com/kitsugi/animelist/core/network/KitsugiHttpClient.kt` | +20 |
| `app/src/main/java/com/kitsugi/animelist/data/remote/JikanApiClient.kt` | +56 |
| `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiCharacterClient.kt` | +59 |
| `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiStaffClient.kt` | +50 |
| `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiMediaRelationsClient.kt` | +22 |
| `app/src/main/java/com/kitsugi/animelist/data/remote/BangumiLibraryTitleHydrator.kt` | +9 |
| `app/src/main/java/com/kitsugi/animelist/data/remote/BangumiOrgResolver.kt` | +12 |
| `app/src/main/java/com/kitsugi/animelist/data/remote/BangumiTitleCache.kt` | +12 |
| `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiBangumiClient.kt` | +5 |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailPage.kt` | +10 |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/MediaEntryDetailPage.kt` | +6 |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/CharactersTab.kt` | +4 |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/CharacterDetailViewModel.kt` | +4 |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/StaffDetailViewModel.kt` | +4 |
| `app/src/main/java/com/kitsugi/animelist/AppRootDetailPages.kt` | +4 |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/profile/BangumiProfileContent.kt` | +6 |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/notifications/KitsugiNotificationsViewModel.kt` | +5 |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchTranslation.kt` | +18 |
| `app/src/main/java/com/kitsugi/animelist/utils/KitsugiTranslations.kt` | +94 |
| `app/build.gradle.kts` | v2.4.338 → **2.4.339** |
| `RELEASE_NOTES.md` | +72 |

---

## 5. Doğrulama

- [x] `/tmp/ktcheck.py` ile süslü/parantez dengesi: **31 dosya → braces=0, min=0, parens=0**.
- [x] `git diff --check`: boşluk hatası yok.
- [x] İmza uyumu elle doğrulandı (`fetchCharacters`/`fetchStaff`/`fetchRelations`/`fetchRecommendations` parametre sıraları, `FavouritesScreen` çağrısı).
- [x] `parseP1Infobox_readsKeyValueArrays` testine `简体中文名 → ["古河渚"]` assertion'ı eklendi.
- [ ] **Derleme yapılamadı:** sandbox'ta JDK/Gradle yok.
- [ ] **Canlı API testi yapılamadı:** `api.bgm.tv`, `next.bgm.tv`, `graphql.anilist.co` sandbox'tan erişilemiyor (HTTP 000).

### Cihazda test önerisi
1. Keşfet → kaynak **Bangumi**: şeritler ≤15 sn içinde dolmalı, tek şerit boş kalsa bile diğerleri gelmeli.
2. Bir anime detayı → **Karakterler / Ekip / İlişkiler / Öneriler**: önce kanji liste, ~5–10 sn içinde Latin adlarla güncellenmeli.
3. Sekmeyi kapat-aç: önbellekten açılış **anında** olmalı ve adlar yine Latin kalmalı.
4. Dil: `ROMAJI → ENGLISH → NATIVE` geçişlerinde adlar yeniden çekilmeden değişmeli.
5. Uçak modunda detay: boş ekran yerine son önbellek içeriği gelmeli.

---

## 6. Bilinen Sınırlar / Sonraki Adımlar

- `next.bgm.tv` beta host'u tamamen kapalıysa karakter/ekip listeleri v0 yedeğine düşer (isimler aynı, sıralama farklı olabilir).
- AniList kaydı olmayan niş yapımlarda Latin ad yalnızca Bangumi infobox'tan gelir; infobox'ta `英文名`/`罗马字` yoksa özgün ad korunur (uydurma çeviri yapılmaz).
- `FavouritesScreen` imzasında `staffNameLanguage` yok; favori kişi satırları tek `name` alanı taşıdığı için dil değişimi burada etkisiz.
