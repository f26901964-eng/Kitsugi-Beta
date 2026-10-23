# Kitsugi Beta v2.4.341

## 🇹🇷 Türkçe

### 🔄 Liste Yenileme Mimarisi — "Her Yenilemede Baştan Tam Çekim" Bitti
- **Kaynak başına tazelik defteri:** Artık her platform (AniList · MAL · Simkl · Kitsu · Shikimori · Bangumi) için **kendi** son senkron zamanı, son tam senkron zamanı, hata sayacı ve (destekleyen kaynaklarda) uzak revizyon imzası tutulur. Bir sekmeyi yenilemek diğerlerini "taze" göstermez.
- **Fark (delta) çekimi:** AniList'te yalnızca `updatedAt_greater` süzgeciyle **değişen kayıtlar** çekilir. Simkl'de `sync/activities` imzası değişmediyse liste **hiç** indirilmez. Kitsu'da `library-entries?filter[since]=…` ile yalnızca son senkron sonrası değişenler alınır. Tam uzlaştırma günde bir kez yapılır.
- **Değişmeyen satır yazılmaz:** `smartImport` artık satırları `updatedAt` dışındaki alanlarda karşılaştırır. Hiçbir şey değişmediyse **veritabanına yazım yapılmaz**, Room yeni emisyon üretmez, Listem'in birleşik hesaplaması (kimlik köprüsü + gruplama) baştan koşmaz.
- **Sil-yeniden-yaz kaldırıldı:** Arka plan yenilemeleri `deleteBySource + insertAll` yerine fark-tabanlı birleştirme kullanır; satır kimlikleri, kaydırma konumu ve bekleyen senkron işleri korunur.
- **Eşzamanlı çift istek engeli:** Aynı kaynak için aynı anda tek yenileme (single-flight). "Tümü"yü yenile artık altı kaynağı **sırayla** (staggered) çalıştırır; eskiden altısı birden paralel başlıyordu.
- **Bekleyen yerel düzenlemeler korunur:** Çevrimdışı yapılmış ve henüz sunucuya gönderilememiş değişiklikler, liste yenilenirken uzak veriyle **ezilmez**.
- **Dürüst geri bildirim:** "823 kayıt aktarıldı" yerine gerçek sonuç: `+3 yeni, 2 güncellendi`, `liste zaten güncel`, `sunucuda değişiklik yok`, `az önce yenilendi`.
- **Pull-to-refresh göstergesi** artık gerçek senkron durumuna bağlı; bir kare yanıp sönmüyor.

### 📊 Kaynak Yenileme Davranışı (özet)
| Kaynak | Arka planda | Elle yenileme | Tam uzlaştırma |
|---|---|---|---|
| AniList | 1 saatten sık: atlanır, sonrası delta | delta (uç reddederse tam) | 24 saatte bir |
| Simkl | activities imzası aynıysa atlanır | imza değişmediyse atlanır | 24 saatte bir |
| Kitsu | 1 saatten sık: atlanır, sonrası delta | delta (`filter[since]`) | 24 saatte bir |
| MAL / Shikimori / Bangumi | 6 saatten sık: atlanır | 45 sn sonra tam liste | her tam çekim |

> **Dürüst sınır:** MAL, Shikimori ve Bangumi'nin API'sinde "değişenleri getir" parametresi **yok**
> (Shikimori `/api/v2/user_rates` yalnızca `user_id`/`status`/`page` süzgeçlerini destekliyor;
> `updated_at` sıralaması upstream'de henüz yok). Bu üç kaynakta tam liste çekilir; ancak
> değişmeyen satırlar yazılmadığı için maliyet yalnızca ağ indirmesidir.

---

## 🇬🇧 English

### 🔄 List Refresh Architecture — No More "Full Re-fetch on Every Refresh"
- **Per-source freshness ledger:** Each platform (AniList · MAL · Simkl · Kitsu · Shikimori · Bangumi) now tracks its own last-success, last-full-sync, failure counter and (where supported) remote revision signature.
- **Delta fetching:** AniList fetches only records changed since the last sync (`updatedAt_greater`), Kitsu uses `library-entries?filter[since]=…`, and Simkl skips the list entirely when `sync/activities` is unchanged. Full reconciliation runs at most once a day.
- **No writes when nothing changed:** `smartImport` compares rows ignoring `updatedAt`; a no-op refresh performs **zero** database writes, so Room emits nothing and the combined library is not recomputed.
- **Destructive rewrite removed:** Background refreshes merge instead of `deleteBySource + insertAll`; row IDs, scroll positions and queued sync jobs stay valid.
- **Single-flight:** One in-flight refresh per source. "Refresh all" now runs sources **sequentially with staggering** instead of six parallel full fetches.
- **Pending local edits are preserved:** Offline edits still waiting in the outbox are never overwritten by a server pull.
- **Honest feedback:** Real diffs (`+3 new, 2 updated`, `already up to date`, `no server changes`) instead of "823 entries imported".
- **Pull-to-refresh indicator** now reflects actual sync state instead of flashing for a single frame.
