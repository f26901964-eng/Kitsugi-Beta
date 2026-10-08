# Bangumi (bgm.tv) Entegrasyon Planı ve Araştırma Raporu

Durum: **Kod tamamlandı, derleme/test cihazda yapılacak** (bu ortamda JDK/Gradle yok).
Oturum kaydı: `bgm.tv/dev/app` → uygulama **Kitsugi** (App ID `bgm72686ac7eb0e3c858`),
kayıtlı 回调地址 = `kitsugi://bangumi-auth`. Kimlik bilgileri yalnızca **gitignore'daki**
`local.properties`'tedir (`bangumi_client_id` / `bangumi_client_secret`); ASLA commit edilmez.

---

## 1. Aniyomi'nin "Bangumi kaynağı" nedir?

Bir **extension (kaynak) değil, tracker'dır**:

- `app/src/main/java/eu/kanade/tachiyomi/data/track/bangumi/{Bangumi,BangumiApi,BangumiInterceptor}.kt`
- DTO'lar: `BGMCollectionResponse`, `BGMOAuth`, `BGMSearch`, `BGMUser`
- `aniyomiorg/aniyomi-extensions` `repo` branch'indeki `index.min.json`'da **hiç Bangumi/Çince
  extension yoktur** (3 kayıt, hiçbiri Bangumi değil).

API: `https://api.bgm.tv/v0` (46 uç). Auth host'u farklıdır: `https://bgm.tv`.

### Aniyomi'den kopyalanan eşlemeler (biz de aynısını kullanıyoruz)

| Bangumi CollectionType | Kitsugi WatchStatus |
|---|---|
| 1 (Wish / 想看) | Planned |
| 2 (Done / 看过) | Completed |
| 3 (Doing / 在看) | Watching |
| 4 (OnHold / 搁置) | Paused |
| 5 (Dropped / 抛弃) | Dropped |

- Puan: tam sayı 0–10 · `supportsPrivateTracking = true` · marka rengi `#F09199`
- Web URL: `https://bangumi.tv/subject/{id}` · kapak: `images.common`
- Manga aramasında yalnız `platform == null || platform == "漫画"` tutulur (roman/画集 elenir)
- Token süresi: `now/1000 > created_at + expires_in - 3600` → interceptor'da yenile + yeniden kaydet

## 2. Aynı API'yi kullanan diğer açık kaynak projeler

| Proje | Teknoloji | Not |
|---|---|---|
| **Mihon** (`bgm291665acbd06a4c28`) | Kotlin | `mihon://bangumi-auth` |
| **Komikku** (`bgm31586666817a5d03b`) | Kotlin | |
| **Anikku** (`bgm369567dbdfe6c6c51`) | Kotlin | |
| **Yokai** (`bgm291865b0b16054d89`) | Kotlin | |
| **Suwayomi-Server** (`bgm376667faf473119bb`) | Kotlin | redirect: `https://suwayomi.org/tracker-oauth` |
| **Aniyomi** (`bgm293165b66d7e58156`) | Kotlin | `aniyomi://bangumi-auth` |
| **czy0729/Bangumi** | React Native | kullanıcı adı+şifre+captcha kazıma yedeği (kırılgan, önerilmez) |
| **xiaoyvyv/bangumi** | CMP + Ktorfit | token ucu için en iyi Kotlin referansı (`AuthApi.kt`) |
| **open-ani/animeko** | Kotlin | **çıkmaz**: Bangumi'yi kendi sunucusu üzerinden proxy'ler |
| bangumi-data (`bangumi-data`) | CC BY 4.0 | BGM↔MAL/AniList/TMDB ID eşleme haritası (atıf zorunlu) |
| bangumi/Archive | döküm | herkese açık wiki verisinin periyodik SQL dökümü (meşru offline indeks) |

> Her uygulama Bangumi'de **kendi** kaydını yapar; ortak anahtar gömülmez. Token ucu
> (`POST /oauth/access_token`) `client_secret` istediği için gömülü ortak anahtar zaten mümkün değil.

## 3. İzin / yetki gerekiyor mu? → **HAYIR**

- Kayıt: `https://bgm.tv/dev/app` (oturum açıkken) → App ID + App Secret **anında** verilir, onay süreci yok.
- **回调地址 (Redirect URI) boş bırakılırsa** gönderilen her `redirect_uri` kabul edilir
  (forum `group/topic/350623`); **doldurulursa birebir eşleşmek zorundadır**. Biz `kitsugi://bangumi-auth`
  kaydettik → app de tam olarak bunu gönderiyor (token isteğinde de aynı değer tekrarlanır).
- Kurallar:
  1. **User-Agent zorunlu**: geliştirici adı + uygulama adı (+ sürüm/homepage). `BangumiApiClient.USER_AGENT`.
  2. **Hız sınırı**: kullanıcı/IP başına 3000 istek / 10 dk → HTTP 429, ihlalde 1 saat ban.
  3. Herkese açık wiki verisini **cache'lemek serbest** (kurucu Sai, `bangumi/api` #294).
  4. **Kullanıcı verisini üçüncü tarafa vermek yasak** (#295) → hiçbir Bangumi verisi dış servise gönderilmez.
- Scope'lar: sunucuda yalnız `write:collection` ve `write:indices` tanımlı; klasik OAuth token'ları
  (`scope == null`) yazma yetkisine sahip (`Auth.HasScope()`). Dev panelindeki READ/WRITE kutuları geleceğe yönelik.

## 4. Mimari harita (bu depodaki dosyalar)

### Yeni dosyalar
| Dosya | Görev |
|---|---|
| `data/auth/BangumiApiClient.kt` | v0 API'nin tamamı: search/browse/subjects/episodes/collections/characters/persons/indices + OAuth token uçları |
| `data/auth/BangumiAuthStore.kt` | token/refresh/user/client kimliklerini `MyWebViewPrefs`'te saklar; `getValidToken` (mutex'lu yenileme), pending redirect |
| `data/auth/BangumiAuthManager.kt` | authorize URL üretimi + kod→token takası + `GET /v0/me` profil çözümleme |
| `data/auth/BangumiSyncManager.kt` | tek kayıt eşitleme (durum+puan+ bölüm/kitap ilerlemesi), silme (DELETE yok → 抛弃+puan 0) |
| `data/auth/BangumiImportManager.kt` | koleksiyonu içe aktarma (`MediaEntry`'ye çevirme), profil çekme |
| `data/remote/BangumiIdNamespace.kt` | **500M ID ad alanı** (`subject_id + 500_000_000`) + yerel eşleme cache'i |
| `data/remote/KitsugiBangumiClient.kt` | `JikanSearchResult` adaptörü: Keşfet şeritleri, sayfalı arama, karakter/kişi arama |
| `ui/components/KitsugiBangumiLoginDialog.kt` | App ID/Secret + 1-tık deep link + kod yapıştırma akışı |
| `res/drawable/ic_logo_bangumi.xml` | marka rozeti (pembe zemin + beyaz küp) |

### Düzenlenen dosyalar (özet)
`ExternalAuthManager` (AuthState + deep link + emit), `AndroidManifest` (2 intent-filter),
`app/build.gradle.kts` + `local.properties.example` (BuildConfig), `AppSettings` + `SettingsDataStore`
(`sync_enabled_bangumi`, profil alanları), `AuthViewModel` (`loginBangumi`, `importBangumiList`, disconnect),
`ExternalListSyncManager` (otomatik eşitleme + silme), `AppRoot` (girişte otomatik aktarım),
`ExploreModels/ExploreViewModel/AllSourcesExplore*/FullScreenMediaGridPage/AppRootTabPages/KitsugiRankingBottomSheet`
(**Keşfet: 7. kaynak**), `SearchSourceFilters/SearchUiState/SearchViewModel/SearchScreen/SourceEngineSelector/
SourceEngineFilterSheet/SourceSpecificFilterChipsRow/DetailSearchFilterRequest` (**Arama: 8. motor**),
`MyListLibraryGrouping/MyListComponents/MyListScreen/MyListEmptyState` (**Liste: 7. sekme**),
`SettingsScreen*/AccountSettingsSubPages/AppRootSettingsExtras` (Ayarlar), `MediaIdentity` (500M anahtarı),
etiket/renk: `KitsugiHeroSection`, `KitsugiPlatformLogo`, `Character/StaffDetailViewModel`,
`EntryDetailTabContents`, `KitsugiImageGalleryDialog`, `AppDialogHost`.

## 5. API gerçekleri (openapi/v0.yaml'dan doğrulandı)

- **Keşfet**: `GET /v0/subjects?type=&cat=&sort={date|rank}&year=&month=&limit=&offset=` (1. sayfa 24s cache)
- **Arama**: `POST /v0/search/subjects` body `{keyword, sort: match|heat|rank|score, filter:{type,tag,air_date,rating,rank,nsfw,meta_tags}}` → 200 = `Paged_Subject` (tam `Subject` modeli!)
- **Koleksiyon**: `GET /v0/users/{name}/collections[/{subject_id}]` (404 = toplanmamış),
  `POST|PATCH /v0/users/-/collections/{id}` → 204, bölüm: `…/episodes` (`{episode_id:[…],type}`)
- **DELETE YOK** → silme = `type=5 (抛弃)` + `rate=0` (Aniyomi/Mihon da `DeletableAnimeTracker` uygulamaz)
- `ep_status/vol_status` **yalnız kitap**; anime ilerlemesi bölüm uçlarıyla (打格子)
- Enum'lar: SubjectType 1=kitap 2=anime 3=müzik 4=oyun 6=real (5 yok) · Episode.type 0=本篇 1=SP 2=OP 3=ED
- Kapak: `https://lain.bgm.tv/pic/cover/{l,c,m,s,g}/…` (hotlink koruması → `Referer: https://bgm.tv/`)
- Karakter/kişi arama **deneysel** uçlardır (`/v0/search/characters`, `/v0/search/persons`).

## 6. Bilinen kısıtlar

1. (2026-10-09 güncellemesi) Bangumi karakter ve kişi detayları artık uygulama içinde açılır (ham ID ile).
   Shikimori/Kitsu için durum değişmedi.
2. Bangumi'de stüdyo/yayıncı bazlı arama yok → filtre sheet'inde bu alanlar sunulmaz.
3. `aniyomi://bangumi-auth` yedek şeması **kayıtlı redirect ile eşleşmediği için** artık çalışmaz
   (kayıt `kitsugi://bangumi-auth`). Diyaloğun "Alternatif Şema" düğmesi yalnız redirect alanı BOŞ
   bırakılmış hesaplarda işe yarar.
4. Keşfet "Tümü" modunda Bangumi şeritleri 12 adettir; TMDB backdrop zenginleştirmesi ilk 5 hero için yapılır.

## 7. Cihazda test listesi

```
./gradlew :app:testFossDebugUnitTest --tests '*AllSourcesExploreTest'   # 7 kaynağa güncellendi
./gradlew assembleFossDebug
```

1. Ayarlar → Hesap Bağlantıları → Bangumi → bağlan (deep link ile tarayıcı açılmalı, geri dönüşte toast).
2. Giriş sonrası koleksiyon otomatik içe aktarılmalı; Listem → 🎌 Bangumi sekmesinde görünmeli.
3. Keşfet → kaynak çubuğunda 🎌 Bangumi; şeritler dolu gelmeli; "Tümünü Gör" sayfalaması çalışmalı.
4. Arama → 🎌 BGM motoru; boş sorguda filtre taraması (browse fallback) sonuç üretmeli.
5. Bir kaydı "İzliyorum + 3 bölüm + puan 8" yap → bgm.tv profilinde 在看 / ep 3 / ★8 görünmeli.
6. 429 görürsen: `PlatformRateLimiter` devrede; 10 dk'da 3000 istek sınırını aşma.

---

## Durum güncellemesi — 2026-10-09 (profil sekmesi)

- Profil kaynağı seçicisine **Bangumi** eklendi (profil, koleksiyon listesi, tür/durum filtreleri, favori karakter ve kişiler).
- Bildirimler resmi API'de yok; uygulama içi WebView'de `bgm.tv/notify/all` açılıyor (bkz. araştırma notları).
- Karakter ve kişi detayı artık uygulama içinde (`KitsugiBangumiCreditsClient`). Favori ekleme/çıkarma
  (`/collect`) ve konu "Karakterler" / "Ekip" sekmeleri Bangumi için bağlandı; paylaş bağlantısı bgm.tv'ye gidiyor.
- Ayrıntılar ve bilinen eksikler: `docs/audits/BANGUMI_PROFILE_RESEARCH_2026-10-09.md`, "Karakter ve kişi detay sayfaları".
- Ayrıntılı araştırma, lisans kararları ve cihaz test listesi: `docs/audits/BANGUMI_PROFILE_RESEARCH_2026-10-09.md`.
- Derleme ve cihaz testleri bu oturumda çalıştırılmadı.
