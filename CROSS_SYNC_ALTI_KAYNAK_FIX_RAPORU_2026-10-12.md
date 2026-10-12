# Çapraz Eşitleme — Altı Kaynağın Tamamı (AL · MAL · Simkl · Kitsu · Shikimori · Bangumi)

**Tarih:** 2026-10-12 · **Sürüm:** v2.4.269 · **Etiket:** `cross-sync`

---

## 1. Kullanıcı şikâyeti

> „AniList, MAL, Simkl arası senkronizasyon sorunsuz çalışıyor; birinden eklediğim içerik
> üçüne de otomatik ekleniyor. Ancak **Bangumi, Shikimori, Kitsu** kaynaklarına otomatik
> eşitlenmiyor. Hangi kaynaktan eklersem ekleyeyim diğerlerinde de otomatik eşitlenmeli.”

## 2. Teşhis (kod okuması ile doğrulanmış kök nedenler)

Canlı eşitleme hattı `MediaEntryRepository.insert/update` → `ExternalListSyncManager.syncEntry`
üzerinden çalışıyordu ve altı kaynağın tamamı için *çağrı bloğu mevcuttu*. Sorun blokların
olmaması değil, **kimlik çözümünün üç kaynağı besleyememesi** ve **atlamanın hata sayılmasıydı**:

| # | Kök neden | Etki |
|---|---|---|
| 1 | `ExternalListSyncManager.syncEntry` içindeki `realMalId` zinciri **yalnızca** iki kimliği çözebiliyordu: gerçek MAL ID ve `source == "anilist"` satırlarındaki 100M offset’i (+ Kitsu 300M). Simkl (150M), Shikimori (400M), Bangumi (500M) alanları **hiç çözülemiyordu** | Shikimori (`target_id = MAL ID`) ve Kitsu kataloğa ulaşamıyor → „kimlik bulunamadı” |
| 2 | Bangumi satırları `syncEntry`/`deleteEntry` başında **kısa devre** yapıyordu (`if (source == bangumi) { sadece Bangumi yaz; return }`) | Bangumi’den eklenen içerik diğer beş kaynağa **hiç** gitmiyordu |
| 3 | `KitsuSyncManager.unresolvedMedia` / `BangumiSyncManager.unresolvedSubject` „güvenli atlama” olarak işaretliyordu, ama `ExternalListSyncManager` bunu `errors` listesine atıyordu; `MediaEntryRepository` ise `errors.isEmpty()` şartıyla bakıyordu | Tek bir kaynak eşleşemediğinde **tüm** iş „başarısız” sayılıyor → `PendingSyncDrainer` kuyruğuna giriyor → her açılışta 5 kez tekrar deneniyor, kullanıcıya „📵 Çevrimdışı kaydedildi” görünüyor, gerçek sebep **hiç gösterilmiyordu** |
| 4 | Çözülen gerçek MAL ID yerel satıra geri yazılıyordu (`dao.update(effectiveEntry…)`) | Satırın **kendi kaynak kimliği** (ör. Kitsu stableId 300M+X) siliniyor; yapım o kaynağın liste ekranında görünmez hâle geliyordu |
| 5 | Canlı hat, toplu eşitlemenin (`AuthViewModel.runCrossSync`) kullandığı `PlatformRateLimiter` ve `BangumiLocalMappingCache` adımlarını **hiç** kullanmıyordu | 6 yazma art arda geldiğinde Shikimori/Bangumi 429 alabiliyor; MAL↔Bangumi köprüsü hiç öğrenilmediği için her eşitlemede arama tekrarlanıyordu |
| 6 | Arama/keşif ekranından ekleme, „en az bir hesap bağlı” kontrolünü yalnızca AL/MAL/Simkl ile yapıyordu | Yalnızca Kitsu/Shikimori/Bangumi bağlıysa ekleme butonu „hesap bağla” uyarısıyla dönüyordu |

## 3. Yapılan değişiklik

### 3.1 Yeni katman: `data/auth/CrossProviderIdentity.kt`

* **`CrossProviderIds`** — bir yapımın altı kimliğini tek yerde tutar: `malId`, `aniListId`,
  `kitsuId`, `shikimoriId`, `bangumiSubjectId`, `tmdbId`, `simklId` (+ her birinin hangi
  kaynaktan öğrenildiğini belirten `via` günlüğü).
  `decode()` satırın `malId` alanında saklı **offset’li stableId**’yi doğru alana yerleştirir;
  AniList (100M..299M) ile Simkl (150M..249M) pencereleri örtüştüğü için ayrımı **kaynak alanıyla**
  yapar — `MediaIdentity.keys` sözleşmesiyle birebir aynıdır.
* **`CrossProviderIdentityResolver`** — kademe kademe, güven sırasına göre çözer:
  1. satırın kendi kimliği (decode)
  2. kalıcı `cross_provider_identity_v1` önbelleği (ağ istemeden)
  3. Shikimori numeric ID → `myanimelist_id` (Shikimori API → ARM)
  4. **ARM** kavşağı (`KitsugiIdResolver.resolveIds`): MAL ↔ AniList ↔ Kitsu ↔ TMDB;
     başka hiçbir kimlik yoksa **Bangumi çapraz köprüsü** (`KitsugiBangumiDetailClient.resolveCrossIds`)
  5. Kitsu resmî `mappings` tablosu (MAL → Kitsu, AniList → Kitsu) + kullanıcı önbelleği
  6. Bangumi: MAL↔`subject_id` önbelleği, yoksa **sıkı** başlık araması (yalnızca tek ve net eşleşme)
  7. Simkl kimliği (birebir kimliklerle; başlık çıplak aramayla yazılmaz)
  8. Son çare: Jikan’da **tek ve net** başlık + yıl eşleşmesi
* **`CrossProviderIdentityCache`** — çözülenler kalıcıya yazılır; ilerleme her değiştiğinde
  yeniden ağa çıkılmaz. Çözülemeyen 6 saat sonra bir daha denenir (sonsuz deneme yok).
  Çözülen gerçek MAL kimliğiyle **ikinci bir kayıt** yazılır; böylece aynı yapım başka bir
  kaynaktan geldiğinde eşleme ağ isteği olmadan bulunur.
* **`CrossSyncPolicy`** — hangi kaynağın hangi türü desteklediği, Bangumi satırının yayılıp
  yayılmayacağı ve „atlandı vs hata” kuralı burada, saf Kotlin olarak durur (birim testiyle sınanır).
* **`ConnectedSources`** — bağlı olmayan servis için katalog araması yapılmaz.

### 3.2 `ExternalListSyncManager`

* Kimlik tek yerden gelir; altı bloğun tamamı aynı `identity` ile çalışır.
* **Bangumi satırları artık yayılıyor:** `subject_id` başka kaynakta anlamsız olduğu için
  yayılım, **doğrulanmış MAL/AniList kimliği çözülebildiyse** yapılır; çözülemezse yalnızca
  Bangumi’ye yazılır ve gerekçe kullanıcıya gösterilir (yanlış yapımı başka listeye eklememek).
* **Atlandı ≠ hata:** kimlik çözülemediyse kaynak `skipped` listesine gider; API/ağ/oturum
  hataları `errors` listesinde kalır. `SyncResult` artık `skipped` ve kaynak bazlı `outcomes`
  haritasını taşır.
* **Yerel satırın kimliği korunur:** çözülen MAL ID uzak çağrılarda kullanılır, DB satırına
  yalnızca satırda **hiç** kimlik yoksa yazılır. Kitsu/Shikimori/Bangumi satırları kendi
  stableId’sini kaybetmez → o kaynağın liste ekranında görünmeye devam eder.
* **Hız sınırları ve önbellek öğrenmesi** toplu eşitlemeyle aynı: her yazmadan önce
  `PlatformRateLimiter.acquire(...)`, başarılı Bangumi yazmasından sonra `MAL ↔ subject_id`
  eşlemesi `BangumiLocalMappingCache`'e yazılır.
* **`skipBangumiWrite`**: Bangumi liste ekranlarından yapılan eklemede koleksiyona iki kez
  yazılmaz, diğer beş kaynağa yine yayılır (`MyListViewModel.saveBangumiEntry`, `AppRoot`
  düzenleme diyaloğu, `AppViewModel.addApiSelectionToList`).
* Silme hattı da simetrik: silmede **ağ araması yapılmaz** (yanlış yapıma dokunmamak için),
  yalnızca satırın kendi kimliği ve önceden çözülmüş kalıcı eşlemeler kullanılır.

### 3.3 Kuyruk ve bildirim

* `MediaEntryRepository`: başarı ölçütü `errors.isEmpty()` — salt „atlandı” kuyruğa yazılmaz.
  Başarısızlık bildirimine kaynak bazlı özet eklenir:
  `AniList: eşitlendi · Kitsu: kimlik çözülemedi · Bangumi: hata`.
* `PendingSyncDrainer`: aynı kural (`errors.isEmpty() && (messages veya skipped dolu)`).
* Snackbar artık **hangi kaynağın neden eşitlenmediğini** gösteriyor
  (`account_sync_sources_skipped`, `account_sync_fanout_blocked_bangumi`).

### 3.4 Küçük destekler

* `ShikimoriSyncManager.SyncResult.unresolvedMedia` — kimlik yoksa „atlanmış” olarak işaretlenir.
* `BangumiSyncManager.syncEntryToBangumi/deleteEntryFromBangumi(context, entry, knownSubjectId)` —
  önden çözülmüş `subject_id` verilebilir; verilince tekrar arama yapılmaz.
* `AuthViewModel.resolveRealMalId` (toplu eşitleme) artık önce çapraz kimlik önbelleğine,
  sonra aynı köprüye bakar → Simkl/Shikimori/Bangumi kökenli gruplar da MAL kimliği bulabilir.
* `AppViewModel.addApiSelectionToList`: „en az bir hesap bağlı” kontrolü altı kaynağı kapsar;
  `primarySource` Kitsu/Shikimori/Bangumi seçimini de tanır ve satır kendi kimlik alanında kalır.

## 4. Korunan güvenlik kuralları

1. **Doğrulanmamış kimlikle yeni kayıt eklenmez.** `CrossSyncIdentityGuard` toplu eşitlemedeki
   kararını aynen uygular; canlı hat da yalnızca birebir kimlikleri (MAL/AniList/Kitsu/stableId)
   kullanır, **TMDB kimliği ARM’a verilmez** (film/dizi kimlik uzayı çakışması).
2. **Belirsiz eşleşme yazılmaz.** Bangumi ve Jikan aramasında yalnızca *tek ve net* sonuç kabul
   edilir; aksi hâlde `null` → atlama.
3. **Kitsu ilerleme tavanı** (422 → medya uzunluğuna kırpma) ve **Shikimori upsert** akışı
   değiştirilmedi.
4. **Bangumi’de DELETE ucu yoktur**; silme `抛弃` olarak kalır (bilinen platform kısıtı).

## 5. Doğrulama

* `tree-sitter-kotlin` ile sözdizimi ayrıştırma: değişen 11 Kotlin dosyasının tamamı **hatasız**
  (bu sandbox’ta JDK/Android SDK yok; `gradlew test` çalıştırılamadı).
* XML: `values/strings.xml` ve `values-en/strings.xml` ayrıştırıldı; **7009 = 7009** anahtar,
  yeni iki anahtar iki dilde de mevcut; değiştirilen dosyalardaki tüm `R.string.*` referansları çözümleniyor.
* Parantez/süslü parantez dengesi ve artık (`effectiveEntry`, eski `rawAniListId` zinciri) taraması temiz.
* **Yeni birim testleri:** `app/src/test/java/com/kitsugi/animelist/data/auth/CrossProviderIdentityTest.kt`
  (12 test) — offset çözümlemesi, Simkl/AniList pencere çakışması, film/dizi satırlarının MAL
  kimliği sayılmaması, önbellek anahtarı, birleştirme önceliği, JSON turu, tür limitleri,
  Bangumi yayılım politikası, „atlandı yeniden denenmez / hata denenir”.
  Cihazda/CI’da çalıştırılmalı: `./gradlew testFossDebugUnitTest --tests "*CrossProviderIdentityTest*"`.

## 6. Kalan kısıtlar (bilinçli)

* **Bangumi eşleşmesi** başlık + yıla dayalıdır (resmî MAL/AniList eşleme ucu yoktur).
  Chinese katalogda Latin/romaji başlıkla net eşleşme bulunamazsa kayıt **Bangumi’de atlanır**;
  kullanıcı o yapımı bir kez Bangumi listesinden eklerse köprü önbelleğe alınır ve sonrası çalışır.
* **Kitsu/Shikimori** anime/manga dışı türleri desteklemez → film/dizi yalnızca Simkl/Bangumi’ye gider.
* **Shikimori** hedef kimliği MAL ID’dir; MAL kimliği hiç çözülemezse Shikimori yazımı atlanır.
* Kimlik önbelleği cihazdadır; hesap değiştirince veya „Tümünü Eşitle” çalıştırılınca
  eksikler toplu hatta tamamlanır.

## 7. Cihazda test senaryosu

1. Ayarlar → Hesaplar: altı hesabı da bağla, „Eşitleme kaynakları” anahtarlarının açık olduğunu doğrula.
2. Keşfet/Arama’dan bir animeyi **yalnızca Bangumi veya Kitsu** sonucundan listeye ekle.
3. Snackbar’da `AniList, MyAnimeList, Simkl, Kitsu ve Shikimori başarıyla eşitlendi` satırını bekle;
   eksik kaynak varsa `Atlanan: …` gerekçesini oku.
4. Bölüm ilerlemesini artır → aynı satır tekrar görünmeli, kuyruk şişmemeli
   (Ayarlar → Eşitleme raporu → „bekleyen işlem” sayısı sabit kalmalı).
5. Kaydı sil → beş kaynakta da kalkmalı (Bangumi → `抛弃`).
