# Kitsugi-Beta — Sürüm Notları / Release Notes

_Sürüm: v2.4.270_

## 🇹🇷 Türkçe

### 🧩 TMDB & Simkl: hayali (kurgusal) karakter görselleri artık geliyor
- **Kök neden bulundu:** TMDB/Simkl'de anime kayıtları çoğunlukla "Film/Dizi" türünde
  geldiği için kurgusal karakterler "gerçek kişi" sayılıyor ve AniList/MAL/Jikan/Kitsu
  görsel zinciri **hiç çalışmıyordu**. Artık karar medya türüyle değil VERİYLE veriliyor
  (animasyon/anime türü, etiket, köken).
- **MAL kimliği olmadan da çalışır:** TMDB kimliğinden anime ilişki haritası (ARM) ile gerçek
  MAL kimliği çözülüyor; AniList'in başlıktan bulduğu yapımın MAL kimliği de kullanılıyor.
- **Tek kelimeli karakter adları artık aranıyor:** "Nobita", "Doraemon", "Goku" gibi adlar
  eskiden "yeterince ayırt edici değil" diye tümüyle reddediliyordu. Adın birebir
  doğrulanabildiği aramalarda bu adlar artık kabul ediliyor.
- **Karakter detay sayfası açılıyor:** TMDB "Nobita", AniList "Nobita Nobi" yazdığı için
  doğru sonuç "kimlik uyuşmuyor" diye çöpe atılıyor ve sayfa hata veriyordu. Rol adı artık
  kimliğe ekleniyor; hiçbir kaynak bulamazsa da hata ekranı yerine karakterin kimliğini
  koruyan bir sayfa gösteriliyor.
- **Daha hızlı:** karakter adı aramaları ardışık değil paralel çalışıyor (süre bütçesi
  6 sn → 9 sn), böylece listede yalnızca ilk birkaç karakterin değil tüm karakterlerin
  görseli geliyor.

### 🔔 MyAnimeList: bildirimlerin devamı geliyor
- MAL'ın bildirim akışı için belgelenmiş bir sayfalama yoktu; tek adres (`?page=N`)
  denendiği için liste "devam yok" diye kilitleniyordu. Artık birden fazla aday adres
  sırayla deneniyor ve **gerçekten yeni kayıt getiren** adres kabul ediliyor.
- MAL bazı oturumlarda tüm kayıtları `historyItems` altında döndürüyor; bu sayfalar artık
  "veri yok" sayılmıyor.
- Oturum düşerse liste korunur ve yeniden giriş kartı gösterilir (sessizce kilitlenmez).

### 🔐 MyAnimeList: tek giriş → hem uygulama API'si hem web API'si
- **Yeni "MAL tek oturum" koordinatörü:** OAuth jetonu (liste/senkron) ile web oturumu
  (bildirimler) aynı hesapta mı, MAL'a sorularak doğrulanır.
- **Normal giriş artık yeterli:** girişten hemen sonra iki kimlik doğrulanır. Web oturumu
  yoksa ya da FARKLI bir hesaba aitse yalnızca MAL web çerezleri temizlenir ve aynı giriş
  akışı bir kez daha açılır — bağlı hesap kesilmez, ikinci bir "hesap girme" gerekmez.
- **Ayarlar → MyAnimeList** sayfasına durum kartı eklendi: uygulama API'si satırı, web
  oturumu satırı, özet ve tek dokunuşla onarım düğmesi ("Tek girişle ikisini de bağla").
- **Bildirimler ekranındaki** "MAL oturumunu bağla" artık sizi ayarlar sayfasına
  göndermiyor; hesap bağlıysa tek giriş akışını doğrudan açıyor.

---

## 🇬🇧 English

### 🧩 TMDB & Simkl: fictional character images now load
- **Root cause found:** anime entries on TMDB/Simkl are usually typed as Movie/TvShow, so
  their fictional characters were treated as "real people" and the AniList/MAL/Jikan/Kitsu
  image pipeline never ran. The decision is now made from DATA (animation/anime genre,
  tags, country of origin) instead of the media type.
- **Works without a MAL id:** the real MAL id is resolved from the TMDB id through the anime
  relations map (ARM); the id AniList resolves from the title is used as well.
- **Single-token character names are searchable again:** names like "Nobita", "Doraemon",
  "Goku" used to be rejected outright as "not distinctive enough". They are now accepted
  wherever the result can be verified by an exact name match.
- **Character detail pages open:** TMDB writes "Nobita" while AniList writes "Nobita Nobi",
  so the correct result was discarded as an identity mismatch and the page failed. The role
  name is now part of the identity; if nothing resolves, the page keeps the character's
  identity instead of showing an error.
- **Faster:** per-character name lookups run in parallel instead of sequentially
  (budget 6 s → 9 s), so the whole list gets images — not just the first few rows.

### 🔔 MyAnimeList: notifications keep loading
- MAL documents no pagination for its notification feed, so the list locked itself after one
  page. Several candidate URLs are now tried in order and only the one that actually returns
  NEW entries is accepted.
- Pages that carry all entries under `historyItems` are no longer treated as "no data".
- If the session expires the list is preserved and the re-login card is shown instead of
  silently dead-ending.

### 🔐 MyAnimeList: one login → both the app API and the web API
- **New "MAL single sign-in" coordinator:** verifies against MAL whether the OAuth token
  (list/sync) and the web session (notifications) belong to the same account.
- **The normal login is now enough:** both identities are verified right after login. If the
  web session is missing or belongs to a DIFFERENT account, only MAL's web cookies are
  cleared and the same login flow opens once more — the linked account is never removed and
  there is no second "account sign-in".
- **Settings → MyAnimeList** shows a status card: app API row, web session row, summary and
  a one-tap repair button ("Link both with one login").
- The **notification screen's** "connect MAL session" action no longer redirects to
  settings; when an account is linked it opens the single sign-in flow directly.
