# Düzeltme: Yorum Sayfası Profil Tıklamaları (MAL/Kitsu/Simkl) + Sayfa Korunumu

**Sürüm:** v2.4.258 tabanı · **Etkilenen alan:** Detay sayfası "Yorumlar / Tartışma / Aktivite"
sekmeleri, "Tüm ... Konuları" alt sayfaları, inceleme/konu/aktivite detay sayfaları.

Kullanıcı raporu: "MAL kullanıcıya tıklayınca kullanıcı profili mal tarayıcı üzerinden açıyor,
profilleri bir ton şey ayarladık uygulama içinden açılsın", "niye kitsu ve simkl profillerini
anilist den açmaya çalışıyor", "profillere tıklayıp geri dönersem bu yorumlar sayfası bozuluyor
… gelen verilerin hepsi gitmiş oluyor".

---

## Kök Nedenler

1. **Profiller webde / yanlış platformda açılıyordu.**
   `ReviewsTab.handleUserProfileClick` yorumu hangi platformdan geldiğine (Kitsu/AniList karışık
   birleştirme verisindeki `source` alanı) bakmıyordu:
   - `userId != null` olan HER kullanıcı doğrudan uygulama içi **AniList** profiline gidiyordu
     (Kitsu'nun sayısal kullanıcı kimlikleriyle AniList profili isteniyordu → "kitsu ve simkl
     profillerini anilist den açmaya çalışıyor").
   - `userId == null` olan herkes `ShareUtils.buildProfileUrl` üzerinden **MAL web sitesine**
     gönderiliyordu; fonksiyonun bilinen platform dalları yoktu, `else` dalı
     `myanimelist.net/profile/` üretiyordu ("mal tarayıcı üzerinden açıyor").

2. **Geri dönüşte yorum sayfası sıfırlanıyordu.**
   Sayfa birleşimi yalnızca aktif detay sayfasını çiziyor; `ReviewsTab` ve alt sayfaların
   listeleri/sayıfa durumu/açık-alt-sayfa bayrakları düz `remember` ile tutuluyordu. Profil
   ekranına gidildiğinde yorumlar sayfası birleşimden çıkıyor, geri dönüşte her şey yeniden
   yükleniyor ve tüm veriler kayboluyordu.

---

## Yapılan Değişiklikler

### 1) Platforma göre yönlendirme (Hata 1 + 2)

- `ReviewsTab.handleUserProfileClick` artık 4 argümanlı çağrılıyor
  `(userId, username, avatarUrl, source)` ve `canonicalMediaSourceId(source)` ile yönlendiriyor:
  | Yorum kaynağı | Davranış |
  |---|---|
  | `anilist` | Uygulama içi `KitsugiUserProfileScreen` (mevcut AniList profili; `userId` yoksa anilist.co web) |
  | `mal` / `jikan` | **Uygulama içi MAL profili** |
  | `kitsu` | **Uygulama içi Kitsu profili** (kullanıcının kendi yorumundaki kimlikle) |
  | `simkl` | **Uygulama içi Simkl profili** |
  | `tmdb` / `shikimori` / `bangumi` / diğer | Doğru platformun web profili (`ShareUtils.buildProfileUrl`) |
- `ShareUtils.buildProfileUrl` tüm platformlar için doğru URL'leri üretiyor
  (anilist.co/user, myanimelist.net/profile, kitsu.app/users, simkl.com/user, shikimori.one/users,
  bangumi.tv/user, themoviedb.org/u).
- Yeni **`OtherUserProfileRepository`**: başkalarının profillerini yükler
  - MAL: Jikan `users/{u}/full|friends|history` (JikanGateway → cihaz üzeri MAL HTML → miribyou → Tenrai).
    Kendi profildeki `applyMalFullProfile` ayrıştırmasını birebir yeniden kullanır.
  - Kitsu: `users/{id}` + `stats` + `favorites` + `library-entries` (herkese açık).
  - Simkl: `api.simkl.com/users/{id}` genel profil; başarısızsa yorum kartındaki ad/avatar ile
    eksiksiz ama sade bir profil (asla boş kalmaz).
  - Dönen tipler `MalProfileState` / `KitsuProfileState` / `SimklProfileState` ile aynı; mevcut
  `MalProfileContent` / `KitsuProfileContent` / `SimklProfileContent` ekranları aynen kullanılır.
- Yeni **`PlatformUserProfileScreen`**: uygulama içi MAL/Kitsu/Simkl profil ekranı; kendi
  profil altyapısını kullanır (stat kartları, favoriler tam ekran sheet, galeri, arkadaşlar,
  geçmiş, "Web'de aç"), üst bar'da platform logosu + `OpenInNew`.
- `DetailScreen.UserProfile`'e `source` alanı eklendi; `AppStateKey.UserProfile` state anahtarı
  `source` + `username` içerir (MAL'de `userId=0` olan kullanıcılar karışmaz).
- `AppRootDetailPages` profil dalı: `mal/jikan/kitsu/simkl` → `PlatformUserProfileScreen`,
  aksi halde → mevcut `KitsugiUserProfileScreen`. Tüm sosyal kartlar (inceleme kartı,
  tartışma kartı, aktivite kartı, forum yorumları ve çocuk yorumları, tümü-sheet'leri,
  inceleme/konu/aktivite detay sayfaları) yorumun **kendi** `source` alanını iletir.
- AniList'e özel ekranlar (`KitsugiProfileScreen`, `KitsugiUserProfileScreen`,
  `AniListProfileContent`, `AniListSocialTab`, `UserProfileSocialTab`, bildirimler) 3 argümanlı
  davranışını korur; yalnızca sosyal ailedeki lambdalar 4 argümana geçirildi.

### 2) Yorum sayfası geri dönüşte korunuyor (Hata 3)

- Yeni **`SocialFeedCache`**: medya başına (`source|id|type|tmdbId|malId` anahtarı) bellek içi
  önbellek: önizleme konuları/aktiviteleri, "Tüm Konular / Tüm İncelemeler / Tüm Aktiviteler"
  sayfa listeleri (`items, page, hasMore`), beğeni güncellemeleri.
- `ReviewsTab`: önizleme listeleri önbellekten doldurulur, geri dönüşte `LaunchedEffect`
  ağa tekrar düşmez; tüm-alt-sayfa bayrakları `rememberSaveable` ile korunur (sheet açık kalır);
  beğeni işlemleri önbelleği de günceller.
- Tüm sheet'ler (`KitsugiAllTopics/AllReviews/AllActivitiesBottomSheet`): listeler + `page` +
  `hasMore` + `isLoading` önbellekten; ilk yükleme ve sayfalama önbelleği yazar; filtreler
  (`dil`, `kaynak`) `rememberSaveable`.
- Detay sayfası içi iç içe sayfalar (inceleme/konu detayları, aktivite detay kimliği) da
  `rememberSaveable` kimlik anahtarıyla korunur; nesneler geri yüklendiği listeden bulunur.
- `rememberLazyListState` kaydırma konumu `SaveableStateProvider` (derinlik anahtarı) altında
  otomatik korunur; `AppStateKey.UserProfile` anahtarı `source` içerdiği için farklı
  platformdaki kullanıcılar birbirinin durumunu ezmez.

---

## Notlar / Sınırlar

- Kitsu/Simkl için yorum yazma yok; yalnızca okuma uçları kullanılır (mevcut kısıt).
- Veri katmanında `source="simkl"` inceleme üretilmiyor (Simkl yorum göndermiyor); yönlendirme
  her kartın kendi `source` alanına göre çalışır.
- Simkl genel profil uçları resmi olarak yetersizdir; karta sığan ad/avatar ile temel profil
  gösterilir, "Web'de aç" ile tam profil açılır.
- TMDB/Shikimori/Bangumi kullanıcı profilleri web'de açılır (uygulama içi profil ekranı yok).
- **Bu ortamda JDK/Android SDK bulunmadığından derleme (build) yapılmamıştır**; değişiklikler
  kod incelemesiyle doğrulanmıştır.
