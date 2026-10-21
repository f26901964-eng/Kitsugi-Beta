# Çapraz Eşitleme (Cross-Sync) Derin Denetimi — 2026-10-21 (tur-5)

> Kapsam: "yanlış eşleşmeler + tarih/puan/bölüm birbirlerinin üzerine yazıyor" şikâyetinin
> kod içindeki karşılığı. Klasördeki 00–13 no'lu araştırmaların bu özelliğe NE kazandırdığı
> bu dosyada. Kod denetimi: `ExternalListSyncManager.kt`, `CrossSyncIdentityGuard.kt`,
> `CrossProviderIdentity.kt` (CrossSyncPolicy + Resolver), `CrossSyncCandidateIndex.kt`,
> `AuthViewModel.kt` (dialog akışı), altı `*SyncManager.kt`.

## A. Özelliğin gerçek akışı (kod haritası)

| Faz | Nerede | Ne yapar |
|---|---|---|
| **FAZ 1 — çek** | `AuthViewModel` (crossSyncJob, ~L859+) | Altı kütüphanenin TAMAMINI çeker (`*ImportManager.fetchAllLists` ×6); her platformun `initialCount`'u raporlanır. **Yani çapraz eşitleme önce uzakları taze okur — eski veriyle üste yazma senaryosunun önü buradan kesilir.** |
| **FAZ 2 — birleşik eşleştirme** | `AuthViewModel` ~L916+ | `CrossSyncCandidateIndex` (kimlik-anahtarı + başlık-alias + Japonca-başlık indeksi) adayları daraltır; nihai karar `MediaIdentity.sameMedia()` verir. Güvenlik kuralı: **birincil/İngilizce başlık-alias çakışıp ID çelişen gruplar** ayrı tutulur. Japonca franchise başlıkları (aynı film/seri adını paylaşan sıralar) bilerek eşleştirme-alias'ına KATILMAZ (yanlış gruplaşma riski). |
| **FAZ 3 — alan senkronizasyonu** | `AuthViewModel` ~L1178: *"Eksikleri Tamamlama ve Alan Senkronizasyonu (**Asla Silme Yok**)"* | Eşleşen satırlarda alanlar tamamlanır; silme göndermez. |
| **Tekil yazım/silme yolu** | `ExternalListSyncManager.syncEntry` / `deleteEntry` | Düzenleme-kaydet ve dialog akışı bu iki fonksiyondan geçer; altı kaynağa fan-out burada. |

## B. Yanlış eşleşme savunmaları — katmanlar ve BUGÜN KAPATILAN BOŞLUK

1. **`CrossProviderIdentityResolver`**: `CrossProviderIds.decode` (kaynak-namespace kodlaması: AniList 100M-offset, Bangumi-crosswalk, Shikimori=mal-namespace…) + disk-önbelleği + çok-kaynak birleştirme; arama yolları yıl+başlık filtresiyle çalışır. **Ama tam-çözülmüş (kod + önbellek "tamam") bir ID seti ağ doğrulaması OLMADAN döner** — hatalı-eski mapping aynen geçer (kod L393-395).
2. **`CrossSyncPolicy.allowFanOut`**: Bangumi satırı doğrulanmış anime-manga/film kimliği yoksa yalnız Bangumi'ye yazar.
3. **`CrossSyncIdentityGuard`** (önceden var, tescilli doktrin): kaynak MAL ID'si **resmî MAL kataloğunda** (Jikan YETERSİZ — fail-closed) başlık (güçlü akrabalık) + tür-kümesi + **tam yıl eşleşmesiyle** doğrulanır; `Trusted` asla ekleme yetkilendirmez. `Rejected`/`Unverifiable` → yazmaz.
4. **❌ BULUNAN BOŞLUK (kullanıcının şikâyetinin göbeği):** Guard'ın kendi dokümanı *"her çapraz hesaplı yazma yolu … yeniden doğrulamalıdır"* derken kodda **yalnız `AniListSyncManager` bunu yapıyordu** — `MalSyncManager`, `SimklSyncManager`, `KitsuSyncManager`, `ShikimoriSyncManager`, `BangumiSyncManager`'de guard çağrısı **0** idi. Yani: tek hatalı bir mapping (Simkl `ids.mal`, Kitsu `mappings`, ARM/Jikan çıkarımı…) MAL'de PATCH'i doğrudan başka esere yazabiliyordu (`updateMalEntry` ID'yi doğrudan kullanır), Simkl/Kitsu/Shikimori/Bangumi'de durum/puan/tarih/bölüm başka kayda gidebilirdi. **Neden şimdiye kadar AniList dışına sıçramamıştı:** resolver+karma (kategori 1) çoğu vakayı kurtarıyor; kalan vakalar tam da "sürekli yanlış eşleşme" hissini veren ince durumlardı.
5. **✅ BUGÜN EKLENEN ÖN-KAPI** (`ExternalListSyncManager`, `crossWriteBlocked`):
   - `syncEntry`: 6 hedefin tamamında dal (AniList dahil — iç-tekrar yerine erken-kapı), `OUTCOME_SKIPPED_IDENTITY` + mevcut çevrili mesaj `cross_sync_reason_identity_unverified` ("…yanlış içerik eklenmesin diye atlandı").
   - `deleteEntry`: 6 koşul + 6 `deleteGateOutcome(applicable)` — **yanlış-ID'li silme de** aynı kapıda (yanlış silme, yanlış yazımdan kötüdür).
   - Kural: `hedef ≠ satır kaynağı` ve `realMalId != null` ise guard `Verified` şart; **yerel kaynak-kendisine** yazım kapı-dışı (kendi listesinin ID'si); MAL ID'siz video-satırları (TMDB-only film) davranış değiştirmeden akar.
   - `Movie/TvShow` → verify tipi `Anime` (MAL bu tipleri anime-namespace'inde tutar; guard'ın anime kümesi "movie"/"tv" kapsar).
   - `Rejected/Unverifiable` → **atlandı, hata değil** (kuyruk şişmez — yeniden deneme kimliği düzeltemez). MAL kataloğu erişilemezse fan-out durur (fail-closed; guard "Jikan'a düşme, resmî kaynağa yaz" kuralıyla uyumlu).
   - Doğrulama **sefer başına bir kez**, önbellekli (`sourceIdentityVerified` memo + guard'ın kendi `identityCache` + `PlatformRateLimiter.acquire("mal")`).

## C. Tarih / puan / bölüm "üst üste yazma" semantiği (araştırma 10.md ile birlikte)

- **Yerel satır tek otoritedir** (local-first): fan-out yerel değerleri gönderir; FAZ 1 taze-çekimi
  sayesinde bu değerler koşunun başında uzaklardaki en-taze durumla güncellenir → "A'da eskimiş
  veriyle B'yi ezme" senaryosu koşu başında kırılır.
- **Bilinçli istisnalar (korunuyor):** puan `null` → uzakta temizlenir (yerel-hakim kararı);
  tarih `null` → **gönderilmez** (uzak tarih korunur — `sendableDate`); Bangumi/Shikimori'ye
  tarih gönderilmez (API'si kabul etmiyor).
- **Sıralı yazım:** Simkl'de history → ratings → **durum en son** (kullanıcı niyeti son sözü söyler).
- **Kırpma:** Kitsu 422-on-squeeze, MAL pre-clamp; bu oturumda Simkl `historyBatchReceipt` +
  Shikimori + Bangumi `min(progress, total)` (yalnız bilinen toplam; 12.md A16).
- **Anlık limitler:** `PlatformRateLimiter` (her fan-out çağrısı) + Simkl 10GET/1POST-sn kilidi +
  Bangumi 450ms + AniList fiili 30/dk + `Retry-After` → çapraz koşu sırtını bu araştırmadaki
  limit-korumalarına yaslar.
- **Silme:** FAZ 3 asla-silmez; `deleteEntry` doğrulanmış kimlikle + (bugünden) ön-kapıyla siler.

## D. Araştırmalar bu özelliğe NE kazandırdı (eşleme)

| Klasör dosyası | Çapraz eşitlemedeki karşılığı |
|---|---|
| 07 (şikâyetler) | "Yanlış eşleşen satır / geri dönen silme / iki araç savaşı" sınıfları → bu denetimin senaryosu |
| 09/10 (self-write hataları + semantik) | Altı yöneticinin yazdığı ALANLAR ve null/eksik-gönderim kuralları → "kim neyi eziyor" matrisi (§C) |
| 11 (birinci-el limit/doküman) | Simkl anlık-kilidi + AniList 200-hata + V2-kotası → fan-out throttle katmanı |
| 12 (kopya/ID/ölçüm) | 8'li bölüm-taxonomisi + kopya-sınıfları → clamp (A16) + `CrossSyncCandidateIndex`/`sameMedia` ayrımıyla örtüşür |
| Guard doktrini (kod-içi) | §B.4 boşluğunun giderilmesi — bu turun asıl kod işi |

## E. Kalan sınırlar (açık — gizlenen yok)

1. **`CrossSyncEntryFactory` hâlâ üretim-ölü** (A6): yalnız testler çağırıyor; FAZ 2/3 kendi
   akışını `AuthViewModel` içinde kurmuş. "Test edilen davranış üretemiyor" paradoksu yerinde —
   fabrikanın hedeflediği akış ile FAZ3'ün akışı harmanlanmalı (ayrıntı-iş, karar gerekir).
2. **Yerel yeniden-adlandırılan satır**: `aliasesOf` = title + İngilizce + Japonca (aka yok) —
   kullanıcı yerel başlığı değiştirirse çapraz kapı `Rejected` verebilir → o koşu **atlanır**
   (yanlış-yazma yok, ama senkron da durur; MAL'de katalog-başlığı disiplini bu: AniList yolu
   zaten böyle). Düzeltme adayı: DB'de eski-adı saklayan alias katmanı (açık madde).
3. **Unverifiable = atlandı, otomatik tekrar yok**: MAL kataloğu/servisi erişilemezse çapraz
   fan-out o koşuda susar; hata-kuyruğuna girmez (kimlik yeniden denemeyle düzelmez mantığı).
   Kullanıcı koşuyu tekrarlayınca yeniden denenir. AniList yoluyla TUTARLI davranış.
4. **Yıl disiplini iki yönlü keskin**: guard tam-yıl-eşleşmesi ister; yılı olmayan/el-yazısı
   satır çapraz kapıda `Unverifiable` olabilir (hatalı-yazmadan kötü değil, ama kapsam düşer).
   `mediaTypeToX` tipleri: TvShow/Movie Anime'ye maplendi (MAL ns) — Bangumi'de film tv-namespace?
   denetim: `supportsBangumi` film/dizi kabul eder, Bangumi-kendi-kaynağı kapı-dışı; MAL'den
   Bangumi'ye film-dönüşü verify-tipi=Anime ile uyumlu.
5. **FAZ 2'de alan-kazanan seçimi okunmadı**: grup-kararı (kim aynı medya) denetlendi; alan-bazlı
   birleştirme FAZ3 + yerel-otorite modeline dayanıyor (§C) — `UnifiedSyncItem` içindeki
   çakışan-alan önceliği ayrı bir turda satır-satır denetlenmeli (açık madde).
6. **CI derleme/test yok** (A5): bu turun kodu tree-sitter + sözleşme-grep ile denetlendi
   (ELSM: yeni=0 hata = baseline 0); `assembleDebug` sende.
