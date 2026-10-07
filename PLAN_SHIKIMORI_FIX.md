# Kitsugi — Shikimori OAuth Giriş Arızası: Teşhis & Düzeltme Planı (v2.4.190)

**Tarih:** 2026-10-07 · **Dal:** `arena/d0b08c37-kitsugi-beta` · **Commit:** `344ae21`
**Temel commit:** `98faa3b` (v2.4.189, main)

---

## 1. Şikayet

- 1-Tık otomatik giriş çalışmıyor (resim 1: "The requested redirect uri is malformed or doesn't match client redirect URI").
- Manuel kod akışında: kod alınıp yapıştırılmasına rağmen login düşüyor
  (resim 5: "Shikimori oturumu doğrulanamadı: jeton geçersiz görünüyor").
- Aynı kodla tekrar deneme → HTTP 400 `invalid_grant` (resim 6).

## 2. Kök Neden (kanıtlanmış)

**Giriş aslında token elde ediyordu; uygulama geçerli token'ı çöpe atıyordu.**

1. Uygulama API isteklerini `https://shikimori.one/...` adresine gönderiyordu (`ShikimoriApiClient.BASE_URL`).
2. Shikimori production ortamı artık `shikimori.one` gibi domain'leri **301 ile `shikimori.io`'a
   yönlendiriyor**. Kanıt: kullanıcı ekran görüntülerinde uygulama `shikimori.one` açıyor,
   tarayıcı adres çubuğunda aynı query parametreleriyle `shikimori.io` görünüyor.
3. **OkHttp, host değiştiren 301/302 yönlendirmelerinde `Authorization` header'ını güvenlik
   gereği otomatik siler.** `GET /api/users/whoami` bu yüzden token'sız ulaşıyordu.
4. Shikimori sunucu kodu (github.com/shikimori/shikimori, `Api::V1::UsersController#whoami` +
   `morr/devise-doorkeeper` Warden stratejisi) doğrulandı:
   - istek **oturumsuz** ulaşırsa → `whoami` **200 + `null`** döner;
   - token gerçekten geçersizse → **401 + `{"error":"invalid_token"}`** döner (bu durum DEĞİLDİ).
5. Uygulama tek `null`'u "jeton geçersiz" sayıp login'i düşürüyordu — oysa yetki kodu
   **tek kullanımlıktı ve takas sırasında tüketilmişti**. Yeniden deneme → `invalid_grant`.
6. "Artık bozuldu" sebebi: Shikimori tarafındaki domain yönlendirmesi yeni; eski sürümlerde
   istekler doğrudan kanonik host'a ulaşıyordu.

**1-Tık'taki ayrı (kullanıcı tarafı) sorun:** Kullanıcının kendi Shikimori OAuth uygulamasının
redirect listesinde `kitsugi://shikimori-auth` kayıtlı değil → sunucu authorize adımında
"redirect uri doesn't match client redirect URI" hatası veriyor. Uygulama tarafında düzeltilemez;
OAuth uygulamanın ayarına eklenmeli (veya `aniyomi://shikimori-auth` alternatifi kullanılmalı).
Eklenmiş olsa bile eski sürümde akış yine whoami adımında token'ı kaybediyordu.

## 3. Yapılan Değişiklikler

| Dosya | Değişiklik |
|---|---|
| `app/src/main/java/com/kitsugi/animelist/data/auth/ShikimoriApiClient.kt` | `BASE_URL` → `https://shikimori.io` (kanonik); `LEGACY_BASE_URL` yedeği. Yeni `shikimoriHttpClient` (auto-redirect kapalı) + `executeShikimori()`: 301/302/303/307/308 yönlendirmelerini **orijinal Authorization header'ı korunarak** manuel takip eder (max 3 hop, RFC 7231'e uygun POST→GET). Bütün uçlar (token takas, refresh, whoami, profil, rate CRUD, history, favorites) bu yoldan geçer. `getCurrentUser`: 200-null'da 3 deneme (700ms/1500ms geri çekilme), 401'de anında hata. `ShikimoriTokenException` artık HTTP `status` taşır. |
| `app/src/main/java/com/kitsugi/animelist/data/auth/ExternalAuthManager.kt` | `saveShikimoriAuth(..., notify)`: placeholder kayıt Success olayı yayınlamaz (çift list aktarımı önlenir). `exchangeShikimoriCode` (1-tık deep-link): takas sonrası **token derhal saklanır**; whoami başarısız olsa bile login düşmez, kimlik tembel çözülür. `ensureShikimoriUserResolved()`: kimlik 0 iken kayıtlı token ile whoami dener, id/nickname kaydeder. `getOrRefreshShikimoriToken`: sunucu refresh token'ı net reddederse (400/401) oturum temizlenir + `SessionExpired` yayınlanır; ağ/5xx ise eski token'a düşer. |
| `app/src/main/java/com/kitsugi/animelist/ui/app/AuthViewModel.kt` | `loginShikimori` (manuel kod): aynı "token önce, whoami sonra, kimlik tembel" akışı; whoami başarısızsa login başarı sayılır + tek Success (otomatik aktarım başlar, aktarım kimliği tembel çözer). `importShikimoriList` ve cross-sync platform tespiti: `userId == null && token != null` iken önce `ensureShikimoriUserResolved` dener. |
| `app/src/main/java/com/kitsugi/animelist/data/auth/ShikimoriSyncManager.kt` | `syncEntryToShikimori` / `deleteEntryFromShikimori`: kimlik eksikse önce tembel çözüm. |
| `app/src/main/java/com/kitsugi/animelist/ui/app/KitsugiProfileViewModel.kt` | `fetchShikimoriProfile`: token var / kimlik yok → coroutine içinde `ensure` dener, çözümlenirse yeniden dener, çözülmezse "bağlantı bulunamadı". |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/notifications/KitsugiNotificationsViewModel.kt` | `loadShikimori`: aynı tembel kimlik çözümü. |
| `RELEASE_NOTES.md` | v2.4.190 (TR + EN) maddeleri. |

## 4. Doğrulama Notları

- OkHttp'nin host-değişen yönlendirmede Authorization silme davranışı, bu istemciye özel
  auto-redirect kapalı + elle takip ile aşıldı; hangi yönde 301 olursa olsun (one→io veya
  io→one) çalışır.
- Shikimori sunucu tarafı davranışı kendi açık kaynaklarından doğrulandı (whoami 200-null vs
  401 JSON ayrımı, `access_token_expires_in 1.day`, `authorization_code_expires_in 30.minutes`,
  token'lar opak — JWT değil).
- Ortamda JDK bulunmadığından derleme testi yapılamadı; kod, projedeki mevcut deseni izliyor
  (build.gradle / CI derlemesi önerilir).
- `git push origin arena/d0b08c37-kitsugi-beta` iki denemede GitHub'dan "Internal Server Error"
  döndürdü → commit dalda yerel olarak güvende; sonraki turda tekrar push denenmeli.

## 5. Kalan Görevler (öneriler)

- [ ] Yeni APK derle (v2.4.190) ve kullanımdaki **yeni** yetki koduyla giriş dene (eski kod tükendi).
- [ ] Kullanıcı tarafı: Shikimori OAuth uygulamasının Redirect URI listesine
      `kitsugi://shikimori-auth` ekle (1-tık'ın kalıcı çözümü) — yoksa `aniyomi://` alternatifi.
- [ ] Push'u tekrar dene: `git push origin arena/d0b08c37-kitsugi-beta`.
- [ ] İsteğe bağlı: `KitsugiShikimoriClient.kt` (genel arama) ve görsel URL'lerindeki
      `shikimori.one` referanslarını da `shikimori.io`'a taşımak gereksiz 1 ek yönlendirme hop'unu kaldırır
      (şimdilik çalışır — kimlik gerektiren uç değil).
- [ ] İsteğe bağlı: login sonrası otomatik aktarımın çift Success olayı riski için
      `saveShikimoriAuth(notify=...)` akışının regresyon testi.
