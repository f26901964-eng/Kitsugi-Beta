# Kitsugi — Shikimori Listem Sayfasında +18 Bulanıklık Uygulanmaması: Teşhis & Düzeltme Planı (v2.4.226)

**Tarih:** 2026-10-09 · **Dal:** `arena/474d5d3f-kitsugi-beta` · **Commit:** `ac6a880`
**Temel commit:** `cb1612d` (v2.4.225, main)

---

## 1. Şikayet

- Shikimori liste sekmesinde (Listem → Shikimori, "Tümü" sekmesi de dahil) +18 içerikler
  görünüyor; **+18 blur ayarı açık olmasına rağmen** afişlere bulanıklık uygulanmıyor.
- Kullanıcı detaylı Ballet/afiş bekliyor: liste sayfasındayken, içerik +18 ise kapak
  maskelenmeli.

## 2. Kök Neden (kanıtlanmış)

Blur zinciri (ayar → `KitsugiNsfwImage` → `entry.isAdult`) sağlam; sorun **veri katmanında**:
Shikimori içe aktarımı `isAdult` bilgisini asla üretemiyordu.

Shikimori sunucu kodu (github.com/shikimori/shikimori) üzerinden doğrulanan iki gerçek:

1. **`GET /api/users/:id/anime_rates|manga_rates`** yanıtı `UserRateFullSerializer` ile
   üretilir; gömülü `anime`/`manga` nesneleri `AnimeSerializer`/`MangaSerializer` kullanır.
   Bu serializer'lar yalnızca `id, name, russian, image, url, kind, score, status, episodes,
   episodes_aired, aired_on, released_on` döndürür — **`rating` ve `genres` YOKTUR**.
   (`kind` değerleri tv/movie/ova/ona/special/tv_special/music/pv/cm olduğu için
   `rateKind == "hentai"` kontrolü de asla eşleşmez.)
2. Eski `fetchAdultMediaIds` fallback'i `GET /api/animes?ids[]=…&censored=false` ile
   sorguluyordu. Shikimori bu sorguda rx kayıtlarını **döndürür**
   (`Animes::Filters::Policy#whitelist_by?`: `ids` varlığı veya `censored=false` hentai
   filtrelemesini devre dışı bırakır — `forbid_filtering?` → true), **ama yanıt gene
   `AnimeSerializer`/`MangaSerializer` ile üretilir — payload'da `rating` alanı yoktur**.
   `isShikimoriAdultContent(null, [])` her zaman false → `adultIds` hep boş küme.

Sonuç: `ShikimoriImportManager.fetchAllLists` tüm kayıtları `isAdult = false` ile üretiyor,
`smartImport` bunu veritabanına yazıyor, `KitsugiMediaEntryCard` → `KitsugiNsfwImage`
zinciri `isAdult=false` görünce blur uygulamıyor. Detay sayfası (`/api/animes/:id` →
`AnimeProfileSerializer`, `rating`+`genres` içerir) bu yüzden çalışıyordu — sorun
yalnızca liste içe aktarım yolundaydı.

## 3. Yapılan Değişiklikler

| Dosya | Değişiklik |
|---|---|
| `app/src/main/java/com/kitsugi/animelist/data/remote/ShikimoriAdultResolver.kt` | **YENİ.** GraphQL tabanlı toplu +18 çözümleyici (50 kimlik/sorgu, `censored: false`, kimlik doğrulaması gerektirmez): `{ animes(ids: "…", limit: 50, censored: false) { id rating genres { name } } }`; manga sorgusu `rating` içermez (şemada `MangaType.rating` yok) — tür üzerinden tespit. `isShikimoriAdultContent` politikası (yalnızca rx/hentai; r/r_plus hariç). Olumlu+olumsuz sonuçlar `BoundedCache`'te önbelleklenir; ağ hatası önbelleğe yazılmaz (yeniden denenir); `PlatformRateLimiter("shikimori")` ile hız sınırına uyulur. `hostOverride` test kancası. |
| `app/src/main/java/com/kitsugi/animelist/data/auth/ShikimoriImportManager.kt` | `fetchAdultMediaIds` çağrıları → `ShikimoriAdultResolver.resolveAdultIds(ANIME/MANGA, …)`. `user_rates` yanıtında çözülemeyen tüm kimlikler GraphQL ile toplu kontrol ediliyor. |
| `app/src/main/java/com/kitsugi/animelist/data/auth/ShikimoriApiClient.kt` | Kırık REST `fetchAdultMediaIds` kaldırıldı; kullanılmayan `KitsugiApiBase` import'u temizlendi; `ageRating` alanı ve +18 yorumları güncellendi (gerçek: gömülü nesne bu alanları taşımaz, çözümleme GraphQL'de). |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/ShikimoriAdultFlagMigration.kt` | **YENİ.** Mevcut veritabanı kayıtları için tek seferlik onarım: `source=shikimori` ve `isAdult=false` olan, `malId` (Shikimori hedef kimliği) taşıyan kayıtlar `ShikimoriAdultResolver` ile yeniden kontrol edilip +18 olanlar `isAdult=true` yapılıyor. Yalnızca false→true yazar (mevcut işaretler korunur); ağ kesilirse tur tamamlanmamış sayılır, 12 saat sonra yeniden denenir; tur başına 600 sorgu bütçesi. |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListViewModel.kt` | `init{}` onarım zincirine `ShikimoriAdultFlagMigration.runIfNeeded(context, dao)` eklendi (KitsuIdentityMigration deseniyle). TV kütüphanesi (`TvLibraryScreen`) aynı ViewModel'i kullandığı için her iki yüzey kapsanır. |
| `app/src/test/java/com/kitsugi/animelist/data/remote/ShikimoriAdultResolverTest.kt` | **YENİ.** Sorgu üretimi (anime `rating`+tür, manga tür-only, `censored: false`, limit 50 üst sınırı), yanıt ayrıştırma (rx/hentai → +18; r/r_plus/pg_13/null → değil), 50'li batch + önbellek (yerel HTTP sunucusu), ağ hatasının önbelleğe yazılmaması, geçersiz kimliklerin elenmesi. |
| `RELEASE_NOTES.md` | v2.4.226 bölümü (TR + EN). |
| `app/build.gradle.kts` | `appVersionName` 2.4.225 → **2.4.226**. |

## 4. Doğrulama Notları

- **Sunucu tarafı davranışları Shikimori'nin açık kaynak kodundan doğrulandı:**
  serializer alan listeleri (`AnimeSerializer`/`MangaSerializer`/`AnimeProfileSerializer`/
  `UserRateFullSerializer`), `Animes::Filters::Policy#whitelist_by?` (ids/censored=false →
  hentai filtrelemesi kapalı), GraphQL şeması (`AnimesQuery`/`MangasQuery` argümanları:
  `ids` String, `censored` Boolean, `limit` max 50; `AnimeType.rating` RatingEnum
  `%i[none g pg pg_13 r r_plus rx]`; `MangaType` rating yok; her ikisinde `genres` ve
  `is_censored` var via `AniMangaFields`).
- **GraphQL limitleri kontrol edildi:** Shikimori şeması `max_depth 5`, `max_complexity 190`
  (graphql 2.3.14). Yeni sorgunun depth'i 3 (poster sorgusuyla aynı), complexity ≈ 5
  (düz liste alanlarında sayfa boyutu çarpımı yok) — sınırlar içinde. Sorgu şekli üretimde
  kanıtlanmış `ShikimoriPosterResolver` ile birebir aynı.
- **Uygulama politikası değişmedi:** yalnızca `rx`/hentai +18 (r/r_plus hariç) — bkz.
  `ShikimoriAdultContentTest` ve v2.4.198 sürüm notları.
- Ortamda JDK bulunmadığından Gradle derlemesi/ birim testleri çalıştırılamadı. Tüm yeni ve
  değişen dosyalar tree-sitter Kotlin parser ile sözdizimi kontrolünden geçirildi (mevcut
  dosyalar kontrol grubu) — hepsi temiz. CI'da `./gradlew testFossDebugUnitTest` ilk fırsatta
  çalıştırılmalı.
- `git push origin arena/474d5d3f-kitsugi-beta` başarılı (commit `ac6a880`).

## 5. Kalan Görevler (öneriler)

- [ ] Yeni APK derle (v2.4.226) ve cihazda doğrula: Listem → Shikimori sekmesinde +18 içeriklerin
      kapakları blur'lü gelmeli (mevcut kayıtlar için onarım arka planda birkaç saniye sürebilir).
- [ ] Shikimori senkronizasyonu (manuel veya çapraz eşitleme) sonrası ilk içe aktarımda
      `ShikimoriAdultResolver` önbelleğinin devreye girdiğini loglardan izle
      (`ShikimoriAdultResolver` / `ShikimoriAdultFlagMigration` TAG'leri).
- [ ] İsteğe bağlı: arama fallback yolundaki (`KitsugiShikimoriClient.searchAnime`, `censored=true`)
      +18 tespiti ayrı değerlendirme — bu işin kapsamı dışı (kullanıcı şikayeti liste sayfası).
