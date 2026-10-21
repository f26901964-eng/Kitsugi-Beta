# Sentez — Ekosistem Neden "Yarım Yamalak"? + Kitsugi'nin Karşılığı (2026-10-21)

## A. Sekiz sistemik neden (araştırma dosyalarındaki kanıtların özeti)

| # | Neden | Kanıt (dosya) | Gerçek hayatta ne yapar? |
|---|---|---|---|
| 1 | **Rate limit resmen yazılmamış / yanlı yazılmış** | AniList doküman 90, canlı 30/dk (01); MAL limiti spec'te yok, ~1/sn (02); Bangumi sayı yok (06); Shikimori ~5/sn topluluk ölçümü (04) | Uygulama büyüyünce 429 → sync'ler sessiz yarım kalır |
| 2 | **API'nin kendisi kapanır / IP bloğu** | AniList 403-disable olayları 2026-09-10 ve 2026-10-21 (01); MAL 2018 süresiz kapatma + 2024 `/v2` bloğu (02); AniList elle IP bloğu | "Uygulama bozuldu" sanılır; o gün hiçbir istemci yazamaz |
| 3 | **Token ömrü/refresh çöküşü** | AniList refresh-yok ~1 yıl (01); Mihon çıkış-giriş döngüsü (07); MAL 2018 account-lock dalgası (02) | Haftalarca "bağlı ama yazmıyor" |
| 4 | **6 ayrı veritabanı, aynı anime farklı** | Cinemeta→MAL bölüm-sırası faciası (07); Nuvio #1526; Taiga sezon-sakıştırmalar | Yanlış bölüme ilerleme yazılır; duplicate kayıtlar |
| 5 | **Tam-silme endpoint'i yok** | Simkl watchlist-silme web-özel, var-olmayan yola 200-null (05); Bangumi silme-memnuniyetsizliği (06); MAL DELETE 404 paterni | **Kullanıcının en çok yandığı şey: sildim, geri geldi** |
| 6 | **Uygulamalar birbirinin verisini bozar** | MALSync wiki "diğer sync aracı bozdu → sil yeniden ekle" (07); Taiga→Kitsu→MAL tarih-ezme (07); Silo #2426 geri-gelen silmeler (07) | İki uygulama kullanana liste savaşı |
| 7 | **Yanıt formatı tuzakları** | AniList 429 → Cloudflare HTML (01); Simkl var-olmayan uç 200-null (05); Simkl custom-list DELETE 200-ama-silmez (05) | İstemci "başarılı" sanır, hiçbir şey olmaz |
| 8 | **Domain rotasyonu / mirror** | Shikimori .me→.one→.io→(.tv) + Rusya engelleri (04); kitsu.app/kitsu.io karmaşası (03); Bangumi mirror 404 bug'ı Kazumi (06) | Ülke-ölçeğinde / sürüm-ölçeğinde tam kopma |

Ek olarak ekosistemin yapısal gerçeği: **AniList'in fiili limiti yıllardır 30/dk ve bilerek degraded** (01) — yani "herkesin sync'i tarihsel olarak yarım", çünkü hiçbir istemci bu değişken zemine güvenle oturamıyor.

## B. Kitsugi'nin bu nedenlere karşı NE YAPTIĞI (round 1-5 + bu tur)

| Neden | Kitsugi koruması | Durum |
|---|---|---|
| 1 Rate limit | `KitsugiApiBase`: host-bütçeleri (450ms/80dk), Jikan ayrı gateway, 429→`Retry-After` global cooldown, 5xx retryAfterMs; AniList'te **30/dk fiili limiti bilerek** kodlanmış (L876) + 1-120sn bekleme | ✓ |
| 2 API kapanışı | HTTP hatası → `SyncResult.errors` → **bekleyen-senkron kuyruğu** (oturumda 5 deneme bütçesi) → API dönünce boşaltılır; offline kapısı (NetworkMonitor) ile "çevrimdışı kaydedildi" ayrımı | ✓ |
| 3 Token | Her sağlayıcıda 401/403 ayrımı, Shikimori scope-hatası (`ShikimoriScopeException`), MAL/AniList/Bangumi ayrı auth store | ✓ |
| 4 ID haritası | `CrossProviderIdentity` + `persistNewlyResolvedIds` (malId/simklId/tmdbId geri-yazımı), `KitsugiIdResolver` (ARM+Shikimori+TMDB), AniList 100M-offset şeması, Bangumi lokal mapping cache | ✓ |
| 5 Silme | MAL `DELETE` (404 sessiz), Shikimori rateId-silme + history temizliği, Simkl `history/remove` + `ratings/remove` (gerçek uçlar!), Bangumi DROPPED+rate0, AniList medya-silme; k14: yerel silmede **stale bekleyen-işler temizlenir** | ✓ / **Simkl watchlist-tam-çıkış = açık madde A1** |
| 6 Çakışan araçlar | Yerel kayıt = tek-otorite (local-first); puan-null→remote-temizleme bilinçli "yerel hakim"; sıralı yazım (Simkl: history→ratings→**status en son** = kullanıcı isteği kazanır) | ✓ |
| 7 Sahte-başarı | `checkResponseAndThrow` + status-kod ayrımı her istemcide; Simkl'de 200-null uçlara güven yok (yalnızca dokümante uçlar); outcomes tablosu: sync/skip/hata ayrık raporlanır | ✓ |
| 8 Domain | Shikimori redirect-header tuzağı elle çözümlü (`executeShikimori`); resmi host kullanımı (Bangumi `api.bgm.tv`, mirror yok) | Kısmi / **açık maddeler A2-A4** |

## C. AÇIK LİSTE — bu araştırmadan çıkan ve bilinçli kalanlar

| # | Açık madde | Gerekçe / karar noktası |
|---|---|---|
| **A1** | (Bu turda UYGULANDI — kodda) Simkl silmeye **puan-temizliği + korumalı `dropped` taşıması** eklendi (`linkedBeforeDelete` hayalet-koruması: id önceden bağlanmamışsa taşımaz). Eski: watchlist'ten tam API-silme yok | Resmi uç yok (web-özel); `history/remove` + `ratings/remove` uygulanıyor, öğe 0-ilerlemeyle eski status'ünde kalabilir. Karar (kullanıcı yetkisiyle): workaround **koruma-koşuluyla** uygulandı — yalnız önceden bağlanmış kayıtlarda `dropped`; tam-silme ucu API'de yok, kalan satır-durumu kapatılır. (05.md §3) |
| **A2** | (Bu turda UYGULANDI — kodda) Shikimori `BASE_URL` hâlâ `.io`; **`.tv` adayı/otomatik domain-fallback yok**; `KitsugiIdResolver` L386 `.io'ya` sabit; `.tv` host-bütçesi yok | `.tv` iddiası kaynak-zayıf (mirror sitesi); uygulama planı hazır (aday-listesi + son-çalışan-domain saklama + reachability'de geçiş) ama tek-gerçek-domain ilan edilmeden uygulanmamalı. Türkiye'de `.io` çalışıyor; risk ağırlıklı Rusya/engelli-ülkeler. |
| **A3** | (Bu turda UYGULANDI — kodda) Kitsu `kitsu.app`↔`kitsu.io` erişim-dönüşümlü fallback eklendi (`executeWithHostFallback`, yalnız erişim-hatalarında) | EverythingBox #330 gibi doğrulamalar var; iki host da bugün çalışıyor olabilir → hangisinin-kanonik olduğu resmi-kaynakla teyit edilmeli, sonra aday-listesi eklenmeli. |
| **A4** | Bangumi DELETE-koleksiyonunun sitedeki "tam silme" ile eşdeğerliği belirsiz (DROPPED'a düşebilir) | v0 semantiki + üçüncü-parti istemcilerde "silme yok" notları (06.md §4) — Bangumi'de "silindi" mesajı "DROPPED'a taşındı" gerçeğini gizliyor olabilir. |
| **A5** | CI derleme/test kapsamı (eski açık madde) | Sandbox'ta java/gradle yok; tree-sitter + grep denetimi. |
| **A6** | (eski) bulk sync kuyrukta değil; deleteAll yerel-only; import-vs-pending çakışmasında kullanıcı niyeti kazanır; MAL clamp delilsiz-atlandı; `CrossSyncEntryFactory` prod-ölü | Round-5 Açık Liste aynen geçerli. |
| **A11** | (Bu turda KARAR VERİLDİ — kasıtlı olarak kod eklenmedi) Bangumi spesifik write-bütçesi yok: topluluk ölçümü ~3 koleksiyon-yazımı/dk + ilerleme ~10/10dk (bangumi.tv/group/417148) → büyük içe-aktarma Bangumi'de 429 yiyebilir. Genel 450ms throttle + 429-kuyruk var; **spesifik yazma-aralığı ölçülmedi/eklenmedi** | Karar: **uydurma sayı kodlanmadı** — temkinli-sayı (ör. 5sn) bile iddiayı karşılamazken (3/dk ≈ 20sn) yavaşlatma-kullanıcı-iyileştirmez; mevcut 450ms throttle + 429/503→kuyruk düzeni korunuyor. Doğru sayı Bangumi'den doğrulanınca A11 kapanır; 09.md §F1 |
| **A12** | Shikimori'de süresi-dolmuş token'ın **401 yerine 403** geldiği cookie varyantı biliniyor (Oca-2026 kulüp raporu) | Kitsugi Bearer-only → düşük risk; `getCurrentUser` 401'i ele alıyor; 403-scope/sessiz-kuyruk davranışı üretimde izlenecek (09.md §D1) |
| **A13** | AniList'te finale kendiliğinden COMPLETED yazımı **kaynak-çelişkili** (2018 changelog vs 2026 geliştiriciler) | Kitsugi yerel-durumu olduğu gibi gönderir (yerel=otorite); otomatik-tamamlama EKLENMEDİ, kullanıcı niyeti bozulmasın (09.md §A8) |
| **A14** | (Bu turda UYGULANDI — kodda) Simkl **10 GET/sn + 1 POST/sn** gönderim-aralık kilidi `awaitSimklRateLimit` (20 çağrı noktası; GET 120ms, POST 1000ms) — eski metin: Simkl saniyelik-limit koruması yok: resmi **10 GET/sn + 1 POST/sn**; POST aşımı token/client_id'i **throtle-bloğuna alar**, tekrar aşım bloğu uzatır; sürekli aşım **uyarı-sız + itiraz-suz askıya alma**. `api.simkl.com` için `KitsugiApiBase` host-bütçesi YOK, SimklApiClient doğrudan `newCall` yapıyor | Kuyruk+bütçe-senaryosu kısmen korur ama **per-second limit doğrudan ele alınmadı** → Somut sertleştirme adayı (min-interval + 429/blocked backoff) |
| **A15** | (Bu turda HAZIRLIK TAMAM → `13_SIMKL_V2_GOC_PLANI.md`) Simkl AUTH V1 → V2 göçü: V1 client_id **~Nisan 2027'de ölecek** (`/oauth2/*` V1'e 401); V2 = yeni kayıt + PKCE S256 + scope + refresh. Kitsugi hâlâ V1 (`oauth/token`, `oauth/pin`) | 8 adımlı göç planı yazıldı (13.md): kayıt=KULLANICI, PKCE şablonu=MAL akışı, premium_only 200+error ayrıştırıcısı, test listesi. Kod = kayıt sonrası; hedef 2026 sonu. Ek: V1 kota ortak havuzu (11.md §A3-4) |
| **A16** | (Bu turda UYGULANDI — kısmi, kasıtlı) Clamp kural seti **Simkl historyBatch + Shikimori + Bangumi**'ye uygulandı: `total` biliniyorsa `min(progress,total)`, bilinmiyorsa dokunulmaz; eski metin: AniList'te "toplam-bilinmeyen/kısa-giriş" clamp kural seti (ani-gui #264: final→toplam, geri-alma-yok, clamp) | Kural: yalnız **kesin-bilinen toplam** varsa sıkıştır (veri kaybı yok), asla-geri-alma zaten round-3'te var. Kitsu 422-on-squeeze + MAL pre-clamp korumalıydı; AniList toleranslı olduğu için ve Bangumi'de toplam-algısız senaryolar sınırlı diye O'LAR dokunulmadı (12.md §A) |

## D. Araştırmanın doğrudan uygulamaya yansıyan EYLEMİ (bu tur) (TUR-4 GÜNCELLEME: aşağıdaki madde 1'e ek olarak — **Shikimori domain-fallback**: `executeShikimori` erişim-hatasında `.io→.one→.tv` adaylarına geçer, çalışan domain'i SharedPreferences'ta saklar, `preferredBaseUrl()` tarayıcı-bağlantılarında/oauth'ta kullanılır, `KitsugiIdResolver` aday döngüsüne geçti, `.tv` host-bütçeye eklendi; **Kitsu** `kitsu.app↔kitsu.io` fallback (14 çağrı noktası); **Simkl** 10GET/1POST-s kilidi (20 nokta). tree-sitter: 5 dosyada baseline ile birebir aynı hata kümesi.)
1. **`api.bgm.tv` host-bütçesi** → `KitsugiApiBase` hostBudgets'a eklendi (Bangumi GET'leri bu sisteme giriyor; Cloudflare-503 sınıfına karşı 450ms/60-dk aralık + 429'da ortak geri çekilme). Gerekçe: 06.md §1-2.
