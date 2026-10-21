# "Kendi Kendine bile Kaydedemiyor" — Tek Kaynak-Yazma Hataları Arşivi (2026-10-21)

Kapsam: kaynaklar-arası çapraz senkronu GEÇTİK; uygulamaların/kaynakların **kendi veritabanına**
(durum, puan, bölüm ilerlemesi, tarih) yazamadığı, yazıp göremediği, yanlış yazdığı gerçek vakalar.

## A. AniList — yazma hataları (en yoğun arşiv)

1. **`scoreRaw: null` sonsuz-döngüsü (para "kayıp" yazısı).** `SaveMediaListEntry(scoreRaw: null)` →
   400 "The score raw must be an integer"; alanın **yok (omitted) olması** gerekiyor. Seanime #985:
   hata oluşuyor, **retry önbelleği hep null gönderiyor → yazı ASLA YERLEŞMİYOR, her deneme quota
   yakıyor.** Sıfır-çıkarım: null=temizle SEMANTİĞİ YOK; `score:0` göndermek gerekiyor.
   https://github.com/5rahim/seanime/issues/985
   (Kitsugi: bu kural round-5'te dokümandan doğrulandı — null alan asla eklenmiyor, doğru.)
2. **Puan formatı çift-çevrimi → yanlış puan yazılır.** Okuma `score(format:)` önbellekteki birimle,
   yazma hesabın O anki formatıyla yorumlar: 85/100 okunup 5-yıldızlı hesaba **85 puan** yazıldı
   (Otaku-Reader PR #38: "reads 85/100 and writes 85 as a five-star rating").
   Aynı sınıf: MediaList.score zaten hesabın ölçeğinde döner, bir kez daha çeviren 3/5'i 0'a çevirir
   (animitchures PR #6). Ayrıca anipace PR #4: pull'da `score(raw:true)` GraphQL-validation 400'ü.
   https://github.com/HeartlessVeteran2/Otaku-Reader-Flutter-/pull/38
3. **Alan-korunumu belirsizliği (çapraz-alan EZİLME korkusu).** Resmi doküman YAZMIYOR: yalnızca
   `mediaId + scoreRaw` gönderilen mutation status/progress/notes/dates'i korur mu, siler mi —
   aniduel #3 bunu canlı test etme zorunluluğu hissediyor: "If other fields get cleared, the Import
   must read each entry's current fields and send them back." Aynı repo #40: import'lar önce
   status'u yazabilmeli.
   https://github.com/zennnne/aniduel/issues/3
4. **İlerleme GERİYE sarılır.** SaveMediaListEntry gönderilen her değeri yazar — bitmiş dizide
   bölüm 3 senkronlanınca liste 3'e iner (anime-sh PR #145). Üstelik **429 sonrası başarısız
   liste-oku `{}` önbelleklenirse TÜM_PUSH'lar kör olur** ve gerçek listeyi ezer (PR #162:
   "a failed read cached {} ... rolling the real AniList list back").
   https://github.com/Anime123450/anime-sh/pull/145 ; /pull/162
5. **Yanlış satıra EZME yarışı.** Geç dönen A detay cevabı B'nin ekranında A'nın progress/score/
   dates'ini tutar; kaydet A'nın değerlerini **B'nin id'sine** yazar (unyo-app #4 P1).
   https://github.com/rafaelborges29/unyo-app/issues/4
6. **Nested parametre reddi.** `startedAt/completedAt` iç içe obje olarak gönderilen sarmalayıcılarda
   "Provided object has a nested value!" ile reddedilir → **tarihler hiç yazılamaz**
   (anilist-mcp #13/#14, kısmi-update şeması tüm alanları zorunlu kılınca ekleme yapılamıyordu).
   https://github.com/yuna0x0/anilist-mcp/issues/13
7. **Sunucu tarafı istatistik/kayıt bozuklukları (site kendi DB'sinde tutamıyor):**
   - Profill "Total Anime" 48 saatte bir güncellenmiyor, elle "Update Stats" bile yanlış (Haz-2026:
     "Stats haven't updated every 48 hours for over a month... forced update is currently inaccurate")
     https://anilist.co/forum/thread/90348
   - Advanced scoring sıfırlama çalışmıyor; kaydettiğin puan geri dönünce 0'lanıyor (forum #49020).
   - Kayıtlarda `media: null` bozuk satırlar dönüyor → tüm liste render'ı çöküyor (MALSync PR #4141).
8. **Otomatik-tamamlama çelişkisi (iki kaynak iki şey söylüyor):** 2018 changelog "max episode'a
   ulaşınca status tamamlanıra ayarlandı"yı DÜZELTTİ diyor; 2026'da iki bağımsız geliştirici
   "AniList does not close a series out on its own" (animitchures PR #6, anime-sh) — yani bugünkü
   istemcinin finale kendiliğinden COMPLETED yazması beklenmemeli.
   https://github.com/AniList/ApiV2-GraphQL-Docs/releases ; https://github.com/Mitchures/animitchures/pull/6
9. Alias-lı toplu mutation'ların X-RateLimit-Remaining'i nasıl saydığı bilinmiyor, test ediliyor
   (aniduel #3) — toplu yazarken limit erken bitiyor olabilir.

## B. MyAnimeList — yazma hataları

1. **API olayında site-üstü yazımlar bile görünmüyor.** Ocak-2024 `/v2` bloğunda "changing tracking
   403", arama 503; **elle sitede yapılan değişiklikler bile görünmüyor** (tachiyomiJ2K #1763).
   https://github.com/Jays2Kings/tachiyomiJ2K/issues/1763
2. **Sunucu yazıyı yuttu — veri kaybı:** May-2023 hack sonrası resmi: "any list updates, forum posts,
   edits made ~8.5 hours before the incident **would have to be remade**" (Wikipedia/MAL) — sunucu
   kendi DB'sindeki son ~8.5 saatlik yazımları sildi.
   https://en.wikipedia.org/wiki/MyAnimeList
3. **Tarihler hem TAM yazılır hem PARÇALI okunur:** write `yyyy-MM-dd` zorunlu; XML/oku tarafında
   `0000-00-00`, `2018-07-00` gibi parçalı değerler dolaşımda (gilgi.org parse hataları) — tek taraf
   düzgün bağlamazsa tarihler her yerde kırılır.
4. **Rewatch/alan eksiklikleri:** MALSync "List Sync rewatch'i tanımıyor" (#356); notlar senkronlanmıyor
   (#1468); token issue (#3981). https://github.com/MALSync/MALSync/issues/356
5. Not (önceki tur): favori endpoint'i yok; Jikan salt-okunur; 2018 "süresiz API kapatma".

## C. Kitsu — yazma hataları

1. **Kendi metadata'sı güncellenmiyor:** "Kitsu API does not regularly update episodes (airdate field)"
   (kitsu-notifications, proje bu yüzden terk edilmiş).
   https://github.com/akramsaouri/kitsu-notifications
2. **Tarihler tıklama anı:** Kitsu start/finish tarihleri çoğu zaman "izleme anı değil, tıklama anı"
   (goodwatch #614) — tarih senkronu anlamını yitirir.
   https://github.com/alp82/goodwatch-monorepo/issues/614
3. **Yazma izni/red:** `NotAuthenticatedError: You don't have permission to create this library entry`
   (MALSync #2705); `include=media` ile 500 (Flexget #3483); 422 doğrulama (Kitsugi'de clamp-rewrite
   ile korunuyor — round 1-5).
4. **Alan eskimi:** `rating`→`ratingTwenty`; statü/filtre isimleri depreşmiş (Apiary "DEPRECATED").

## D. Shikimori — yazma hataları

1. **Süresi-dolmuş token 403 olarak geliyor (401 değil).** Kulüp raporu (Oca-2026): cookie+token
   birlikte giden istemcilerde PATCH /api/v2/user_rates/:id **403 Forbidden** alıyor, 401 değil —
   sadece 401'e bakan istemciler reauth'u hiç tetiklemez.
   https://shikimori.one/clubs/950-shikimori-api/topics/215541-obsuzhdenie-kluba
   (Kitsugi: Bearer-only çağrılarda genelde 401; `getCurrentUser` 401'i hemen bitirir + 200-"null"
   tuzağını 3 denemeyle aşar ✓. 403 varyantı → izlenecek, bkz. Açık 12.)
2. **422 tip-tuzakları:** resmi örneklerde bile `"episodes": "4"` string — sayı bekleyen uçlarda
   "Unprocessable entity"; SevereCloud/shikimori #48 çözümü "params values converter".
   https://github.com/SevereCloud/shikimori/issues/48
3. **GraphQL mutation YOK** (Mutation=testField) → tüm yazış REST v2; 401'de refresh yarışı +
   invalid_grant ayrımı detaylı belgelenmiş (anime-db-plugins #42, Ağu-2026).
   https://github.com/anime-db/anime-db-plugins/issues/42
4. **Sayfa-dönme hilesi:** N istek N+1 dönebiliyor (son sayfa işareti yok); sunucu yeni enum değeri
   eklerse tipli istemciler deserialize'da ölür (docs.rs shikimori-api notları).
5. **Eksik-senkron kültürü:** önde gelen proje "yalnız status" senkronluyor (puan/ilerleme değil);
   masaüstü araç "senkronizasyon yalnız status+progress taşıyor" diyor (anime-db-desktop #914);
   AnimeGO transferinde "rewatch/ilerleme/tarih/not taşınmıyor" (shikimori-to-animego v1.0).

## E. Simkl — "yazdım-sandım" hataları

1. **Başarı raporlaması ama hiçbir şey yazılmıyor:** "Simkl still drops anime watches... **full push
   reports success**" (scrob #453, v2.23.0).
   https://github.com/ellite/scrob/issues/453
2. **Final ping'inde completed oluşmuyor** (anisync #16 — 05.md'de de var).
3. **Yaz-oku tutarsızlığı:** history yazıldı, okumada görünmüyor / `simkl_not_found:episodes`
   (CrossWatch #1334, #1316 — yazım başarılı, geri-okuma eksik).
4. **Kimlik düşüşü:** Kitsu id'si yoksa scrobble sessizce düşüyor (NuvioDesktop #11).

## F. Bangumi — yazma hataları (Çince kaynaklardan)

1. **SUNUCU YAZMA LIMITLERİ (en somut bulgu):** "服务端 修改/添加条目收藏 的 rate limit 是 **1 分钟 3 次**，
   更新章节进度是 **10 分钟 10 次**" — koleksiyon ekleme/değiştirme ~**3/dakika**, bölüm ilerlemesi
   ~**10/10 dakika** eşiğinde 429 (iOS istemci geliştiricisi ölçümü, Şub-2025).
   https://bangumi.tv/m/topic/group/417148
   → Toplu yazan her istemci (içe aktarma, senkron toplu) bu duvara çarpıyor.
2. **`updated_at` ALDATICI:** resmi doküman — "修改评分、评价或章节观看状态时，**updated_at 可能不会更新**"
   (bangumi-mcp tasarım notu). updated_at'i su-başı (watermark) yapan delta-senkronlar **puan/ilerleme
   yazımlarını GÖRMEZ** → değişiklik kaybolur sandığı gibi. Ayrıca ep_status kesintili-ilerleme
   sayılır, doğru yazma anahtarı `PATCH .../episodes` (video) / ep_status (kitap).
   https://github.com/akashic-plugins/bangumi-mcp/blob/main/docs/design/collection-list.md
3. **Yanlış verb → 404:** koleksiyon yazımı PATCH ile "subject not collected" 404 veriyor;
   resmi semantik **POST = upsert** (bangumi-manager v0.1.1 düzeltmesi).
   https://github.com/yurif2020/bangumi-manager/releases/tag/v0.1.1
   (Kitsugi: `upsertCollection` POST ✓, video ilerleme `PATCH .../episodes` ✓ — doğru yollar seçilmiş.)
4. **Sitenin "yalnız-izleme-değiştir" kuralı:** web'de yalnız "在看" durumunda ilerleme değiştirilebilir
   (1991 kullanıcılı userscript bunu deliyor); API'de ayrı episodes-ucu var. "未收藏 iken yönetilir
   ama gösterilmez" (garage bileşeni notu).
   https://bangumi.tv/dev/garage
5. **403 ilerleme uçları:** üçüncü-parti dokümanda "修改追番进度… 403 Forbidden" örneği — yetki/scope
   darlığı class'ı. **Koleksiyon-ekleme-tarihi API'den okunamıyor** (yalnız son güncelleme var;
   time-capsule tartışması: "api只能获取最后更新时间，不能获取收藏时间").
   https://bangumi.tv/group/topic/462213
6. **Kaydet-sonra-yanılsama:** kayıt sonrası 5 dakikalık istemci önbelleği eski kategoride gösteriyor
   (kanade #192); kayıt sonrası 10× GET tekrar-okuma dalgası 500ms global aralıkla 4.5 sn kuyruk
   yaratıyor (kanade #185).
7. **Mihon'da Bangumi tracker döngüsü:** "Bangumi Tracker Repeatedly Marks Reading Status" (mihon #1925)
   + Mihon Shikimori URL değiştirmek zorunda kaldı (mihon #3260 — domain kaçağı büyük-uygulamada).
   https://github.com/mihonapp/mihon/issues/3260 ; /issues/1925
8. Sunucu tarafı DB tıkanması tüm v0 API'yi dakikalarca ölü yapabiliyor (bangumi/server #642).

## G. Kitsugi ile anlık eşleme (bu tur denetimi)

| Bulgu | Kitsugi durumu |
|---|---|
| AniList null-scoreRed→400 sonsuz retry | ✓ korunuyor (null alan eklenmez — round-5 doküman notu) |
| AniList puan-formatı | ✓ scoreRaw 0-100 matematiği doğrulandı |
| Shikimori 200-"null" + 401 | ✓ `getCurrentUser` 3-deneme + 401-sondurma |
| Shikimori 422 string-tuzak | ✓ sayısal alanlar kodda Int/Double olarak üretiliyor |
| Bangumi POST-upsert | ✓ `upsertCollection` POST |
| Bangumi video-ilerleme yolu | ✓ `PATCH /collections/{id}/episodes` (ep_status kitaplara) |
| Bangumi 3/dk write-limit | ⚠️ genel 450ms throttle var ama **spesifik write-bütçesi yok** → Açık 11 |
| Bangumi updated_at watermark | ✓ Kitsugi delta-watermark kullanmıyor (tam okuma) — etkilenmiyor |
| Simkl sahte-başarı | ✓ `checkResponseAndThrow` + status ayrımı; 200-null uç yok |
| Kitsu 422 | ✓ clamp-rewrite (round 1-5) |
