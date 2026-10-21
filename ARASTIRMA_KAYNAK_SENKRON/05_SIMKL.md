# Simkl — Araştırma Notları (2026-10-21)

## 1. Yazma kilidi: kullanıcılık 20 SANYE (resmi "Edge cases and gotchas")
- Simkl, kullanıcılı TÜM senkron yazımlarını **20 saniyelik kilit** altında sıraya alır: `/sync/history`, `/sync/history/remove`, `/sync/add-to-list`, `/sync/ratings`, `/sync/ratings/remove`, `/sync/watched`.
- Aynı kullanıcıda arka arkaya iki write → ikincisi kilitte bekler; 20 sn'yi aşarsa **400 `rate_limit`** döner.
- Resmi reçete: "N paralel çağrı yerine TEK çağrıya batch'le; 400 rate_limit alırsan birkaç sn bekle, BİR kez dene."
  Kaynak: https://api.simkl.org/guides/sync

## 2. `POST /sync/history` yazınca liste otomatik taşınıyor (çifte-karar tuzağı)
- History call, öğeyi kendiliğinden listeye yerleştirir ve **"completed yazımı hâlâ yayında olan bir şova sessizce watching'e düşürülür"** — sunucunun kararı ne ise o saklanır.
- Resmi uyarı: history'den sonra **sorgusuz `add-to-list` çağırmak sunucunun daha akıllı kararını ezer**.
  Kaynak: https://api.simkl.org/guides/mark-as-watched
- **Ayrıca:** final bölüm pingleme tek başına `completed`'a GEÇİRMEZ (MAL/AniList'in aksine) — anisync #16: https://github.com/atharvkharbade/anisync-addon/issues/16
  → istemci status'ü AÇIKÇA yazmazsa seri "Watching"da takılı kalır.

## 3. SİLME endpoint'i haritası (en kritik bulgu)
| İstenen işlem | Gerçek endpoint | Durum |
|---|---|---|
| İzlenenleri kaldır | `POST /sync/history/remove` | ✓ var |
| Puanı sil | `POST /sync/ratings/remove` | ✓ var |
| **Watchlist'ten tam çıkar** | `POST /sync/remove-from-list` diye bir şey **YOK** | ✗ dokümante değil |
- Silo-Server #5 (2026-10-06) bunu **canlı testle** kanıtladı: var-olmayan yola bile **200 + null body** dönüyor (!), öğe listede kalıyor; activities'te `removed_from_list` oynamıyor:
  https://github.com/Silo-Server/silo-plugin-watchprovider-simkl/issues/5
  - Topluluk çözümü: `POST /sync/add-to-list {"to":"dropped"}` ile çıkarmak (geçmişi+puanı korur, listeden çıkar).
- Web sitesindeki "Remove from List" **web-özel**; ilerlemeyle birlikte siler ve "yeniden eklersen baştan başlarsın" (docs.simkl.org).
- **Silme-puan ilişkisi:** listeden çıkarılan öğenin **puanı Simkl tarafından silinir**, history (izleme zamanları) ise korunur; yeniden ekleme delta'da "taze kayıt" görünür:
  https://api.simkl.org/guides/sync
- Yine de uygulamalar ısrarla kırık endpoint kullanıyor: custom-lists guide "Write verbs are ignored, not rejected — DELETE /lists/123456 200 döner, bir şey silinmez":
  https://api.simkl.org/guides/custom-lists

## 4. Alan/dizi tuzakları
- **Anime, watchlist'te `shows[]` içindedir**; top-level `anime[]`'a gönderilen öğeler sessizce yutulur (simalytics-ios #47 notu): https://github.com/Kyter-com/simalytics-ios/issues/47
- `extended=full` olmadan `include_all_episodes` hiçbir şey getirmez; tamamlanmış kütüphane `extended=full + include_all_episodes` ister (resmi sync guide).
- **20.000 öğe/istek** üst sınırı (60.000 `simkl_ids_only` ile); `max_items` 400'ü tekrar-denemek çözüm değil, tip/bölümeye bölmek gerek — simkl-mcp #59: https://github.com/vitorhugo-dotnet/simkl-mcp-cloudflare-workers/issues/59
- İlk senkron sırasında `extended=full` tüm kütüphaneyi **onlarca MB** yapabilir → `date_from` delta şart.
- Rewatch: `?allow_rewatch=yes` olmadan final-bölüm ping'i yok sayılır (PRO/VIP).
- Dokümanlar 2026-05'te **Apiary'den api.simkl.org'a taşındı**; Apiary Ekim 2026'da kapanıyor (eski linkler ölecek): https://github.com/SIMKL/API

## 5. Kitsugi'deki karşılığı (kod denetimi 2026-10-21)
- **Sıra:** history → ratings → **add-to-list EN SON** (`SimklSyncManager` L461-495) — resmi "add-to-list kararı ezer" uyarısına KASITLI uyum: burada ezen, **kullanıcının kendi isteği** (yerel kayıt = doğru). Bilinçli tasarım ✓.
- **Silme:** `deleteEntryFromSimkl` → `removeFromList()` = **`POST /sync/history/remove`** (gerçek endpoint ✓, var-olmayan yola değil) + `removeRating` (ratings/remove). Puan+izleme temizlenir.
  ⚠️ **Açık madde:** watchlist-status'ten tam çıkarma API'de yok (yukarıdaki §3). Öğe, 0 ilerlemeyle eski status'ünde kalabilir. Topluluk workaround'u (dropped'a taşımak) yan-etki riskiyle (hiç listede olmayan öğeye dropped-yaratma) uygulanmadı — kullanıcı kararıyla listelenir.
- `mediaKey` eşlemesi: anime→`shows` ✓ (§4 tuzağı çözümlü). lookup yoksa `unresolvedMedia` skip (kuyrukta dönme yok).
- 20 sn kilidi: çağrılar coroutine içinde sıralı (tek-tek await) → paralel write yok ✓; silmede 2 write art arda → sunucu kendi kuyruğunda çözer (<20 sn süren işlem yok).
