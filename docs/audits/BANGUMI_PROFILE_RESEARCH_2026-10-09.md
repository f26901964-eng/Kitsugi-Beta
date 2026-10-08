# Bangumi Profil Sekmesi — Araştırma ve Tasarım Notları (2026-10-09)

## Soru ve mevcut durum

Profil kaynağı seçicisi ("Profil Kaynağı Seç") yalnızca AniList, MyAnimeList, Simkl, Kitsu ve Shikimori'yi
listeliyordu. Bangumi, Keşfet / Arama / Listem / Ayarlar'da vardı ama profil sekmesinde yoktu.
Bu değişiklikle Bangumi profil kaynağı eklendi.

## Resmi API (bangumi/api, open-api/v0.yaml)

Resmi v0 OpenAPI tanımı (`github.com/bangumi/api`, klonlandı ve incelendi) şu uçları içerir:

| Amaç | Uç | Kitsugi'de kullanım |
|------|----|--------------------|
| Kullanıcı | `GET /v0/users/{username}` | Profil başlığı (ad, avatar, imza) — `BangumiApiClient.getUser` |
| Koleksiyon listesi | `GET /v0/users/{username}/collections` | Tüm tür/durum kayıtları (sayfalı) — `getAllUserCollections` |
| Karakter favorileri | `GET /v0/users/{username}/collections/-/characters` | Favori karakterler — `getUserCharacterFavorites` (yeni) |
| Kişi favorileri | `GET /v0/users/{username}/collections/-/persons` | Favori kişiler — `getUserPersonFavorites` (yeni) |
| Konu detayı | `GET /v0/subjects/{id}` | Mevcut Bangumi detay akışı (ApiResultDetail) |
| Karakter / kişi | `GET /v0/characters/{id}`, `GET /v0/persons/{id}` + ilişki uçları | Uygulama içi detay ekranı (`KitsugiBangumiCreditsClient`), bkz. "Karakter ve kişi detay sayfaları" |

**Bildirim (notification) ucu yoktur.** v0 şemasında "notify", "notification", "pm", "inbox" gibi
hiçbir yol bulunmadı. Bu nedenle bildirimler farklı bir kaynaktan gelmek zorunda.

Kullanılan kimlik doğrulama, hız sınırı ve kullanıcı verisi kuralları mevcut plan belgesiyle aynıdır
(`PLAN_BANGUMI_INTEGRATION.md`): 3000 istek / 10 dk, ihlalde 429 ve 1 saatlik yasak; User-Agent zorunlu.

## Açık kaynak referansları

| Proje | Lisans | Ne incelendi | Ne alındı |
|-------|--------|--------------|-----------|
| czy0729/Bangumi (React Native) | MIT | `src/constants/html/index.ts`: `HTML_NOTIFY = ${HOST}/notify/all`, `HTML_NOTIFY_META = ${HOST}/json/notify`; `src/stores/rakuen/fetch.ts` bu uçları kullanır; zaman çizelgesi için `next.bgm.tv/p1/users/{id}/timeline` | Uç noktalar hakkında bilgi. **Kod kopyalanmadı.** |
| xiaoyvyv/bangumi (Compose Multiplatform) | GPL-3.0 | `shared/data/.../BgmWebApi.kt`: `@GET("json/notify")`; `shared/data/.../next/UserApi.kt`: `@GET("p1/notify")`; `features/notification/.../NotificationViewModel.kt`: `URL_BASE_WEB + "notify/all"` | Uç noktaların adları (bilgi). **GPL-3.0 kodu kopyalanmadı** (BSD-3 projeyle uyumsuz). |
| open-ani/animeko, Mihon, Komikku, Anikku, Yokai, Suwayomi, Aniyomi | — | Önceki plan belgesinde değerlendirildi | Profil sayfası yok (animeko Bangumi'yi kendi sunucusu üzerinden proxy'liyor; tracker'lar yalnızca senkronizasyon). |

Not: `p1/notify` ve `json/notify` uçlarının oturum (çerez) gereksinimleri bu ortamdan doğrulanamadı; sandbox
yalnızca github.com, npm, pypi gibi hostlara erişebiliyor, bgm.tv'ye erişemiyor.

## Lisans kararı

- Bu değişiklikte hiçbir üçüncü taraf kaynak kodu kopyalanmadı. Profil, koleksiyon ve favori akışları resmi
  OpenAPI tanımına göre sıfırdan yazıldı.
- czy0729/Bangumi (MIT) ileride kod ödünç alınırsa, MIT koşulu gereği telif ve izin bildirimi korunmalıdır.
- xiaoyvyv/bangumi (GPL-3.0) kodu BSD-3 bir projeye kopyalanamaz; yalnızca davranış/uç nokta referansı olarak
  kullanıldı.
- Kök dizinde bir `LICENSE` dosyası bulunamadı; proje lisansının (BSD-3-Clause olarak belirtilen) repo
  dosyasıyla teyit edilmesi önerilir.

## Uygulanan kapsam

| Özellik | Durum | Dosyalar |
|---------|-------|----------|
| Profil kaynağı seçicisine "Bangumi" | Eklendi | `ProfileSourcePickerSheet.kt` (`ProfilePlatform.BANGUMI`) |
| Profil ekranı sekmesi (indeks 5) | Eklendi | `KitsugiProfileScreen.kt`, `KitsugiProfileViewModel.kt` |
| Profil verisi (kullanıcı + koleksiyon + favoriler) | Eklendi | `data/auth/BangumiProfileManager.kt`, `BangumiApiClient.kt` |
| Bağlan (giriş) | Mevcut Bangumi giriş diyaloğu yeniden kullanıldı | `AppRootTabPages.kt` (`onBangumiAuthSubmit`) |
| Tür ve durum filtreleri, özet, liste | Eklendi | `ui/screens/profile/BangumiProfileContent.kt` |
| Anime / manga öğesi → uygulama içi detay | Mevcut `ApiResultDetail` (source = `bangumi`, kimlik = 500M + konu no) | `BangumiIdNamespace.stableIdFromRaw` |
| Oyun / müzik / dizi-film öğesi → bgm.tv konu sayfası | Eklendi (uygulama içi detay yok) | `BangumiProfileContent.kt` |
| Karakter / kişi favorisi → uygulama içi detay | Eklendi (favori ekleme/çıkarma dahil) | `BangumiProfileContent.kt` → `onFavoriteCharacterClick` / `onFavoriteStaffClick` |
| Bildirimler | bgm.tv/notify/all uygulama içi WebView'de açılır | `KitsugiWebViewDialog` |

## Bildirimler hakkında karar

- Resmi API bildirim sunmadığı için bildirimler web oturumuna dayanır. Uygulamanın WebView çerez deposu
  kullanılır; ilk kullanımda kullanıcının bu pencerede bgm.tv'ye giriş yapması gerekebilir.
- Bu yaklaşım, bildirimleri yerel bir liste olarak göstermez; sayfayı gösterir. Yerel bildirim listesi
  (`p1/notify` veya HTML ayrıştırma) için: oturum çerezinin doğrulanması, ayrıştırıcının yazılması ve
  bgm.tv erişimiyle test edilmesi gerekir. Bu ortamdan yapılamadı.
- Bildirim sayfasının web oturumu gerektirmesi nedeniyle OAuth token'ı tek başına yetmez.

## Karakter ve kişi detay sayfaları (2026-10-09)

Bangumi karakter ve kişi öğeleri artık bgm.tv yerine uygulama içi `CharacterDetailPage` /
`StaffDetailPage` ekranlarında açılır. Kullanılan uçlar (resmî v0 OpenAPI şemasına göre):

| Konu | Karar |
|---|---|
| Karakter detayı | `GET /v0/characters/{id}` + `/subjects` (yer aldığı konular) + `/persons` (seslendirmenler) |
| Kişi detayı | `GET /v0/persons/{id}` + `/subjects` + `/characters` (canlandırdığı karakterler) |
| Konu sekmeleri | `GET /v0/subjects/{id}/characters` ve `/persons` → ApiResultDetail "Karakterler" / "Ekip" sekmeleri (önceden `else` dalı nedeniyle boştu) |
| Kimlik | Karakter ve kişi ID'leri **ham Bangumi ID'si** (arama sonuçlarıyla aynı, `source = "bangumi"`). Medya bağlantıları **stableId** (500M + konu no). |
| Favori durumu | `GET /v0/users/{username}/collections/-/characters/{id}` (ve `persons`); 404 → favori değil. `username` yoksa `-` kullanılır (**doğrulanmadı**). |
| Favori yazımı | `POST` / `DELETE /v0/characters/{id}/collect` (ve `persons`). İyimser güncelleme, hata olursa geri alınır. |
| Paylaş bağlantısı | Bangumi için `bgm.tv/character/{id}` ve `bgm.tv/person/{id}` (önceden MAL bağlantısı üretiyordu). |
| Etiketler | Karakter rolü 主角 / 配角 / 客串 → Ana / Yardımcı / Konuk Karakter. Meslek `career` enum'ları Türkçeye çevrilir. Kadro görevleri (原画, 导演 vb.) ham metin. |
| Lisans | Kod resmî OpenAPI şemasından yeniden yazıldı. czy0729/Bangumi (MIT) ve xiaoyvyv/bangumi (GPL-3.0) kodu kopyalanmadı. |

Bilinen eksikler:
- Bangumi seslendirmenin dilini vermediği için seslendirmen satırında dil "Bilinmiyor" görünür.
- Karakter-kişi rol kartlarında medya kapağı yok (`/persons/{id}/characters` kapak vermiyor).
- Kadro görev etiketleri Çince/Japonca ham metin olarak gösterilir.
- Canlı Bangumi çağrısı bu ortamdan yapılamadı; cihazda doğrulanmalı.

## Bilinen sınırlar ve doğrulanmayanlar

- **Cihazda hiçbir şey test edilmedi.** Derleme ve birim testleri çalıştırılmadı (Java yok).
- Bangumi API'si canlı olarak çağrılmadı (bgm.tv bu sandbox'tan erişilemez).
- Koleksiyon sayfalama: 50'lik sayfalarla en fazla 200 sayfa (10 000 kayıt) çekiliyor; çok büyük
  koleksiyonlarda hız sınırına dikkat edilmeli.
- Favori uçlarının sayfalama parametresi yok; şemaya göre tek yanıtta `data` döner. Çok büyük favori
  listelerinde tek yanıt boyutu kontrol edilmeli.
- Karakter ve kişi detayının eksikleri (dil bilgisi, rol kartı kapakları, kadro etiketleri) için
  "Karakter ve kişi detay sayfaları" bölümüne bakın.
- Oyun, müzik ve dizi/film kayıtları için uygulama içi detay akışı yok.
- Profilde "Bağlı değil" durumunda giriş diyaloğu açılır; çıkış yapıldığında önceki hesap verisi bellekten
  silinir (`BangumiProfileState` sıfırlanır).

## Cihazda test listesi (kullanıcı tarafı)

1. Profil kaynağı seçicisinde "Bangumi" görünüyor mu, bağlı ise `@kullanıcıadı` gösteriliyor mu?
2. Bağlıyken profil yükleniyor mu; başlık, avatar ve imza doğru mu?
3. Filtreler: tür (Anime / Manga-Kitap / Oyun / Müzik / Dizi-Film) ve durum chip'leri koleksiyonu doğru
   süzüyor mu; özet sayıları filtreyle uyumlu mu?
4. Anime ve manga öğesine dokununca uygulama içi detay açılıyor mu?
5. Oyun/müzik öğesi bgm.tv sayfasını tarayıcıda açıyor mu? Favori karakter/kişi uygulama içi detay sayfasını açıyor mu?
6. "Bildirimler" penceresi açılıyor mu; giriş istiyorsa giriş sonrası sayfa görünüyor mu?
7. Çıkış yapıp tekrar bağlanınca eski veri kalmıyor mu?
8. Bangumi bağlıyken karakter/kişi detayındaki kalp düğmesi görünüyor mu; basınca bgm.tv hesabında favori ekleniyor/çıkarılıyor mu?
9. Bir Bangumi anime/manga konusunun "Karakterler" ve "Ekip" sekmeleri dolu geliyor mu; karakter/kişiye dokununca uygulama içi sayfa açılıyor mu?
10. Karakter/kişi detayındaki paylaş düğmesi `bgm.tv/character/…` ya da `bgm.tv/person/…` bağlantısı veriyor mu?
11. Seslendirmen satırında dil "Bilinmiyor" görünmesi beklenen davranış; medya kartına dokununca ApiResultDetail açılıyor mu?
