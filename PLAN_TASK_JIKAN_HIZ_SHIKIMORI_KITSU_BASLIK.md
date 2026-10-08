# PLAN / TASK — Jikan Hız Sınırı, Shikimori Başlık/Tip Düzeltmesi, Kitsu Paralel Çekim

**Tarih:** 2026-10-08
**Dal:** `arena/04a863a4-kitsugi-beta`
**Commit:** `0e6b3ac`
**Durum:** Kod değişiklikleri TAMAM. Derleme ve canlı API testi yapılmadı (sandbox'ta JDK/Gradle yok ve Jikan/Shikimori/Kitsu'ya erişim yok). Doğrulama kullanıcı tarafında.

---

## 1. Şikâyetler (kullanıcı raporu)

1. **MAL detay sayfası:** Karakterler "Karakter bulunamadı" diyor; Ekip ve İlişkiler sonsuz skeleton'da; Öneriler yüklenmiyor.
2. **Kitsu çok yavaş:** Karakter ve seslendirmen bilgileri geç geliyor. Kitsu'nun bir hız kotası olup olmadığı soruldu.
3. **Shikimori çok yavaş:** Ekip ve karakter bilgileri Kitsu gibi geç geliyor.
4. **Rusça adlar:** Öneri ve ilişki kartlarında adlar Rusça görünüyor. Seçili başlık diline göre gösterilmesi istendi.

---

## 2. Kök Nedenler

| # | Kök neden | Kanıt / etki |
|---|---|---|
| A | `KitsugiApiBase` Jikan isteklerini 450 ms aralıkla gönderiyordu → dakikada ~133 istek. Jikan'ın resmi limiti **dakikada 60 ve saniyede 3** ([docs.api.jikan.moe](https://docs.api.jikan.moe/)). Limit aşılınca 429 geliyor, yeniden deneme yetersiz kalıyor, liste boş dönüyor. | MAL karakter "bulunamadı", ekip/ilişki skeleton |
| B | Shikimori `related` / `similar` yanıtında Rusça `russian` değeri `titleEnglish` alanına yazılıyordu (`titleEnglish = seed.russian`). | İngilizce başlık seçiliyken Rusça adlar |
| C | Shikimori ilişki tipleri (`Sequel`, `Side story`, `Adaptation` …) hiç Türkçeye çevrilmiyordu. | Ekranda İngilizce tip etiketleri |
| D | `toTurkishRelationType` büyük/küçük harfe duyarlıydı (`Side story` ≠ `Side Story`). | Çeviri atlanıyordu |
| E | Jikan ilişki ve önerilerinde AniList toplu sorgusu yalnızca kapak alıyordu; İngilizce/Japonca başlık yoktu. | Başlık dili İngilizce/Japonca iken Romaji'de kalıyordu |
| F | Kitsu karakter sayfaları ve seslendirmen (castings) istekleri **sırayla** çekiliyordu (5 karakter sayfası + 3 castings sayfası, her biri bir gidiş-dönüş). | Kitsu karakter sekmesi çok yavaş |
| G | Seslendirmen eksik olduğunda Shikimori/Kitsu karakter listesi, Jikan/AniList yedeğini **8 sn** bekliyordu. | Karakter ve ekip sekmesi gecikmesi |

**Kitsu kotası hakkında:** Resmi, sayısal bir hız limiti bulunamadı. Uygulamanın genel Kitsu istemcisinde (`KitsuClient`) hız sınırlayıcı yoktur; sınırlayıcı yalnızca kullanıcı hesabı API'sinde (`KitsuApiClient`) bulunur. Yavaşlığın büyük kısmı kaynak kaynaklı değil, sıralı istek zincirinden kaynaklanıyordu (F).

---

## 3. Yapılan Değişiklikler

### 3.1 Hız sınırlayıcı (`data/remote/KitsugiApiBase.kt`)
- Eski tek 450 ms slot yerine **host bazlı bütçe** eklendi:
  - `api.jikan.moe`: aralık **340 ms** (≈2.9/sn, limit 3/sn) + kayan 60 sn penceresinde **en fazla 55** istek (limit 60/dk).
  - `shikimori.io` / `.one` / `.me`: aralık **450 ms** (eski hız korundu) + dakikada **en fazla 80** istek.
- Bekleme artık `performGet` ve `executeGetRequestOrThrow` içinde yapılıyor; böylece yeniden denemeler ve senkron çağrılar da sayılıyor.
- `runWithRateLimit` artık pass-through (çift sayım olmasın diye). Çağıran kodda değişiklik gerekmedi.
- 429 yanıtında host bütçesi `Retry-After` kadar (yoksa 2 sn) geri çekiliyor.
- `executeGetRequestResilient` varsayılan yeniden deneme sayısı **2 → 3**.

### 3.2 Shikimori başlık ve ilişki tipi (`data/remote/KitsugiShikimoriClient.kt`, yeni `data/remote/ShikimoriTitleResolver.kt`)
- `russian` alanı ilişki/öneri kayıtlarından çıkarıldı.
- Yeni `ShikimoriTitleResolver`: Shikimori GraphQL `animes` / `mangas` sorgusu ile `english` ve `japanese` alanları en fazla 50'lik gruplarda toplu çekilir. Dize ve dize dizisi biçimleri desteklenir. Bellek önbelleği vardır. Kimlikler MAL kimliği olarak kullanılmaz.
- `buildRelationList` kapak ve başlık çözümünü `async` ile **eşzamanlı** yapar.
- Başlık alanları: `title` = romaji (`name`), `titleEnglish` = İngilizce, `titleJapanese` = Japonca, `titleRomaji` = romaji.
- İlişki tipi `toTurkishRelationType()` ile Türkçeye çevrilir.

### 3.3 Jikan ilişki ve önerileri (`data/remote/KitsugiMediaRelationsClient.kt`)
- Yeni `fetchAniListBulkInfoByMalIds`: AniList `idMal_in` toplu sorgusu kapak **ve** romaji/İngilizce/Japonca başlıkları döndürür.
- `fetchRelationsFromJikan` ve `fetchRecommendationsFromJikan` bu toplu sorguyu kullanır.

### 3.4 Başlık ayrıştırma yardımcısı (`utils/KitsugiTranslations.kt`)
- `toTurkishRelationType` önce birebir, sonra büyük/küçük harf duyarsız eşleşir.

### 3.5 Kitsu paralel çekim (`data/remote/KitsuClient.kt`)
- `fetchKitsuCharacters`: castings istekleri karakter sayfalarıyla **aynı anda** başlar.
- `fetchCharacterPages`: önce ilk sayfa alınır; sayfa tam doluysa kalan sayfalar paralel çekilir.
- `fetchKitsuCastings`: aynı mantık. Yeni `fetchCastingPage` yardımcısı eklendi.
- Kitsu'ya aynı anda en fazla **3** istek (`Semaphore(3)`).

### 3.6 Seslendirmen birleştirme süresi (`data/remote/KitsugiCharacterClient.kt`)
- `KITSU_VA_MERGE_TIMEOUT_MS` ve `SHIKIMORI_VA_MERGE_TIMEOUT_MS`: **8 000 → 5 000 ms**.

---

## 4. Değişen Dosyalar

| # | Dosya | Bölüm |
|---|---|---|
| 1 | `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiApiBase.kt` | 3.1 |
| 2 | `app/src/main/java/com/kitsugi/animelist/data/remote/ShikimoriTitleResolver.kt` (yeni) | 3.2 |
| 3 | `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiShikimoriClient.kt` | 3.2 |
| 4 | `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiMediaRelationsClient.kt` | 3.3 |
| 5 | `app/src/main/java/com/kitsugi/animelist/utils/KitsugiTranslations.kt` | 3.4 |
| 6 | `app/src/main/java/com/kitsugi/animelist/data/remote/KitsuClient.kt` | 3.5 |
| 7 | `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiCharacterClient.kt` | 3.6 |

---

## 5. Test Notları (kullanıcı tarafı)

- [ ] Derle: `./gradlew :app:assembleDebug` (veya Android Studio). Olası tip uyumsuzlukları için `KitsuClient`, `KitsugiShikimoriClient` ve `ShikimoriTitleResolver` öncelikli kontrol edilmeli.
- [ ] **MAL detay (ör. Frieren):** Karakterler, Ekip, İlişkiler ve Öneriler birkaç saniyede gelmeli; "bulunamadı" görülmemeli.
- [ ] **Başlık dili = İngilizce:** Shikimori kaynaklı ilişki/öneri kartlarında Rusça ad OLMAMALI.
- [ ] **Başlık dili = Japonca:** Adlar Japonca (yoksa romaji) görünmeli.
- [ ] **İlişki etiketleri:** "Sequel" → "Devam", "Side story" → "Yan Hikaye" gibi Türkçe olmalı (cihaz dili Türkçe iken).
- [ ] **Kitsu karakter sekmesi:** Hızlanmalı; seslendirmenler görünmeli.
- [ ] **Shikimori karakter/ekip:** Seslendirmen bekleme süresi azaldı; liste gelmeli.
- [ ] **Log kontrolü:** `adb logcat -s KitsugiApiBase ShikimoriTitles KitsuClient KitsugiCharacterClient`
  - `HTTP 429` mesajları azalmış olmalı.
  - `GraphQL başlık isteği başarısız` görülürse Shikimori GraphQL alanlarını (`english`, `japanese`) yeniden doğrulayın.

---

## 6. Kalan İşler / Notlar

- [ ] Derleme ve canlı test (yukarıdaki bölüm 5).
- [ ] Jikan dakikalık limiti (55/dk) paylaşıldığı için çok sayıda kayıt açıldığında bölüm puanları gibi Jikan çağrıları da yavaşlayabilir. Bu bilinçli bir takastır (429 yerine kontrollü bekleme).
- [ ] `CrossSyncIdentityGuard` ve `PlatformRateLimiter` içindeki `jikan` limiti hâlâ ayrı bir sayaç kullanıyor; isterseniz tek merkeze taşınabilir.
- [ ] Shikimori'nin 5 istek/sn ve dakika limitlerini resmi dokümandan doğrulamak faydalı olur; kodda kullanılan değerler güvenli tarafta seçildi.
- [ ] Kitsu'da 429 alınırsa `KitsuClient` henüz 429'a özel yeniden deneme yapmıyor; loglarda görülürse eklenecek.
