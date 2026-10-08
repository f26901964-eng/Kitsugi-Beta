# Kitsugi — Shikimori Kapak Görselleri "404 not found" Arızası: Teşhis & Düzeltme Planı

**Tarih:** 2026-10-08 · **Dal:** `arena/3f6f9d10-kitsugi-beta` · **Temel commit:** `7f922c0` (main)

---

## 1. Şikayet

Shikimori kaynağından gelen içeriklerin **çoğunda** kapak görseli yerine, Shikimori'nin
"404 not found" tişörtlü anime kızı yer tutucusu görünüyor. Birkaç eski yapımda
(ör. *Norimono Atsumare: Hit Song-shuu*, *Fleur*) kapak normal geliyor; buna karşılık
güncel ve popüler yapımlarda (*Dandadan 2nd Season*, *Silent Witch*, *PetitCure* …)
hep yer tutucu çıkıyor. Sorun Listem / Keşfet / Arama ekranlarının hepsinde görülüyor.

---

## 2. Kök Neden (kanıtlanmış)

**Shikimori'nin REST API'si kapakları artık dolduramıyor; uygulama yer tutucu görseli
geçerli bir URL sanıp indiriyor.**

Shikimori kaynak kodundan (github.com/shikimori/shikimori) kanıtlar:

1. REST serileştiricisi `image` alanını **eski Paperclip eki** üzerinden üretiyor
   (`app/serializers/anime_serializer.rb`):

   ```ruby
   def image
     { original: object.image.url(:original), preview: object.image.url(:preview), … }
   end
   ```

2. Paperclip eki, dosya yoksa **sessizce yer tutucuya** düşüyor
   (`app/models/anime.rb`, aynısı `manga.rb`, `character.rb`, `person.rb` içinde):

   ```ruby
   has_attached_file :image,
     url: '/system/animes/:style/:id.:extension',
     default_url: '/assets/globals/missing_:style.jpg'   # ← "404 not found" görseli
   ```

   Yani REST yanıtı `"image": { "original": "/assets/globals/missing_original.jpg" }`
   döndürüyor — HTTP 404 değil, **200 + yer tutucu görsel**. Bu yüzden hata hiçbir
   log'a düşmüyor, Coil görseli sorunsuz indirip gösteriyor.

3. Shikimori kapakları yıllar önce **yeni `Poster` tablosuna** (Shrine tabanlı,
   `app/models/poster.rb` + `app/services/uploaders/poster_uploader.rb`) taşıdı.
   Yeni eklenen ya da kapağı yeniden yüklenen yapımların **eski Paperclip eki yok**.
   → Eski kayıtlar (80'ler yapımları vb.) hâlâ görünüyor, yeni kayıtların hepsi
   yer tutucuya düşüyor. Ekrandaki "bazısı var, çoğu yok" tablosunun sebebi tam olarak budur.

4. Gerçek kapaklar **yalnızca GraphQL yüzeyinde** var
   (`app/graphql/types/poster_type.rb`, `app/graphql/types/concerns/db_entry_fields.rb`):

   ```graphql
   { animes(ids: "1,2,3", limit: 50, censored: false) { id poster { originalUrl mainUrl } } }
   ```

   `poster` alanı `ImageUrlGenerator#cdn_poster_url` ile **mutlak CDN adresleri**
   döndürüyor (`https://nyaa.shikimori.*/system/posters/…`). GraphQL okuma uçları
   kimlik doğrulaması istemiyor.

### Yan bulgu

`KitsugiShikimoriClient` hâlâ `https://shikimori.one` host'unu kullanıyordu; OAuth
istemcisi (`ShikimoriApiClient`) bir önceki düzeltmede `shikimori.io`'ya geçmişti.
Aynı şekilde `NotificationDiagnostics` de `.one` üzerinden istek atıyordu — host
değiştiren 301 yönlendirmesinde OkHttp `Authorization` başlığını sildiği için
bildirim teşhis ekranı yanlış "izin yok" sonucu üretebiliyordu.

---

## 3. Çözüm

Yeni dosya **`ShikimoriPosterResolver`** (`data/remote`) üç iş yapıyor:

1. **Yer tutucu eleme** — `/assets/globals/missing*` yoluna düşen her görseli `null`'a
   çeviriyor (`absoluteUrl` / `isMissingImage`). Boş string yerine `null` dönmesi önemli:
   kartlar baş harf gösteriyor ve birleşik "Tümü" görünümünde AniList/MAL kaydı
   temsilci seçilebiliyor.
2. **Toplu GraphQL çözümü** — eksik kalan kimlikleri tür başına (anime / manga /
   karakter / kişi) **50'lik gruplar** hâlinde tek istekte çözüyor.
   `characters`/`people` uçları `ids: [ID]`, `animes`/`mangas` uçları `ids: "1,2,3"`
   aldığı için sorgu türe göre üretiliyor; `censored: false` ile hentai kayıtlar da dönüyor.
3. **Önbellek + hız sınırı** — sonuçlar bellekte tutuluyor (negatif sonuç dahil, aynı
   istek tekrarlanmıyor); istekler `PlatformRateLimiter.acquire("shikimori")` ile
   Shikimori'nin 5 istek/sn limitine uyuyor. **Ağ hatası önbelleğe yazılmıyor**, böylece
   geçici kesinti kalıcı "kapaksız" duruma dönüşmüyor.

### Dokunulan akışlar

| Ekran / akış | Dosya | Yapılan |
|---|---|---|
| **Listem** (Shikimori + Tümü) | `ShikimoriApiClient.fetchAllUserRates` | Yer tutucu eleme + anime/manga için toplu GraphQL çözümü |
| **Keşfet** (trend, sezonluk, film, top, yayında) | `KitsugiShikimoriClient.searchMediaAdvanced` | Sayfa başına tek toplu çözüm |
| **Arama** (anime / manga) | `searchAnime`, `searchManga` | Tek toplu çözüm |
| **Arama** (karakter / kişi) | `searchCharacters`, `searchPeople` | `CHARACTER` / `PERSON` türünde toplu çözüm |
| **Detay sayfası** | `fetchDetail` | Eksikse tam boy poster (`originalUrl`) |
| **Detay → Karakterler / Ekip** | `fetchCharacters`, `fetchStaff` | Karakter + seslendirmen + ekip görselleri |
| **Karakter detayı** | `fetchCharacterDetail` | Karakter, seslendirmenler, yer aldığı anime/manga kapakları |
| **Kişi detayı** | `fetchStaffDetail` | Kişi, karakter rolleri ve yapım kapakları |
| **Profil** | `fetchUserFavorites`, `fetchUserHistory`, `fetchUserMessages`, avatar | Yer tutucu eleme + favorilerde toplu çözüm |
| **Ekran görüntüleri** | `fetchScreenshots` | Host düzeltmesi (bu uçta yer tutucu yok) |

### Host birleştirme (`shikimori.one` → `shikimori.io`)

- `KitsugiShikimoriClient.BASE_URL`
- `NotificationDiagnostics` (3 yer — teşhis isteklerinde yönlendirme kaynaklı yanlış sonucu önler)
- `AboutScreen` API doküman linki, `ShikimoriProfileContent` profil linki
- `ApiResultDetailViewModel` / `MediaEntryDetailViewModel`: kaynak adı tespiti artık
  tüm `shikimori.*` alan adlarını tanıyor

`ShikimoriApiClient.LEGACY_BASE_URL` bilinçli olarak `.one` kalmaya devam ediyor
(OAuth token ucu için yedek).

---

## 4. Davranış notları

- **Performans:** 600 kayıtlık bir Shikimori kütüphanesi ≈ 12 toplu GraphQL isteği
  (350 ms aralıkla ~4,5 sn). Sonuçlar önbelleğe alındığı ve `MediaEntry.imageUrl`
  yerel veritabanına yazıldığı için bu bedel yalnızca ilk eşitlemede ödeniyor.
- **Kapak boyutu:** Liste/ızgara kartlarında `mainUrl` (225 px webp — eski REST
  `original` ile aynı ölçü), detay/tam ekranda `originalUrl` kullanılıyor.
- **Gerçekten kapağı olmayan kayıt:** Shikimori'de poster yoksa görsel `null` kalıyor
  ve kart, başlığın baş harfini gösteriyor — artık 600 tane aynı "404" görseli değil.

---

## 5. Değişen / eklenen dosyalar

```
app/src/main/java/com/kitsugi/animelist/data/remote/ShikimoriPosterResolver.kt      (YENİ)
app/src/test/java/com/kitsugi/animelist/data/remote/ShikimoriPosterResolverTest.kt  (YENİ)
app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiShikimoriClient.kt
app/src/main/java/com/kitsugi/animelist/data/auth/ShikimoriApiClient.kt
app/src/main/java/com/kitsugi/animelist/data/notifications/NotificationDiagnostics.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailViewModel.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/detail/MediaEntryDetailViewModel.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/more/AboutScreen.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/profile/ShikimoriProfileContent.kt
```

---

## 6. Testler

`ShikimoriPosterResolverTest` (9 test):

- Paperclip yer tutucusunun tüm türevlerinin (`missing_original`, `missing_preview`,
  `missing_x96`, `missing_avatar/x160`, timestamp'li ve mutlak biçimleri) tanınması
- Yer tutucunun boş string değil **`null`** dönmesi (arayüzün geri çekilebilmesi için)
- Göreli yolların `shikimori.io` üzerinde mutlaklaşması, mutlak ve `//` ile başlayan
  adreslere dokunulmaması
- `animes`/`mangas` için `ids: "1,2,3"` + `censored: false`,
  `characters`/`people` için `ids: [1,2]` sorgu biçimleri
- GraphQL yanıtının ayrıştırılması; `poster: null` → boş sonuç
- Kullanılamaz yanıtın (boş gövde, HTML, yalnızca `errors`) `emptyMap` değil **`null`**
  dönmesi — "kapak yok" ile "bilinmiyor" ayrımı
- Uçtan uca (yerel sahte HTTP sunucusu): tüm kimliklerin **tek** istekte gitmesi ve
  ikinci çağrının önbellekten dönüp ağa çıkmaması
- Ağ hatasının "kapak yok" olarak önbelleğe alınmaması

## 7. Doğrulama

- `git diff --check`: temiz.
- Gradle/JUnit: bu çalışma ortamında Android SDK ve bağımlılık deposu (Maven Central /
  dl.google.com) erişimi olmadığından çalıştırılmadı; derleme ve test doğrulaması
  cihaz tarafında yapılacak.
