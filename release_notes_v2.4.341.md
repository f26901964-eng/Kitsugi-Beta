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

### ➕➖ Ekleme ve Silme Ne Olacak? (senaryolar)
- **Listeden çıkardığın kayıt artık gerçekten gidiyor:** silme, günde bir yapılan tam uzlaştırmada uygulanır. Yalnızca **daha önce sunucuda görülmüş** satırlar silinir — çevrimdışı eklediğin, gönderimi "atlandı" denen ya da MAL'da `nsfw=false` yüzünden yanıtta olmayan kayıtlar **asla** silinmez.
- **Yarım yanıt koruması:** sunucu listenin %30'undan azını döndürürse (kota, sayfa kesintisi, oturum sorunu) hiçbir şey silinmez. Boş yanıt da kütüphaneyi temizlemez.
- **"Sildiğim şey geri geldi" bitti:** çevrimdışı silinen kaydın `DELETE` işi kuyrukta beklerken liste yenilenirse, sunucudan gelen eski kayıt geri eklenmez (tombstone koruması).
- **Çevrimdışı eklediğim kayıt kaybolmuyor:** bekleyen `UPDATE`'i olan satır ne ezilir ne silinir; mesajda `· n bekleyen düzenleme korundu` yazar.
- **Web'den sildiğin kayıt** en geç 24 saat içinde (günlük tam turda) yerelde de silinir; delta turları silmeyi göremez — bu bilinçli bir veri güvenliği tercihi.

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

### ➕➖ What Happens When You Add or Remove Something?
- **Removed entries are actually removed now:** deletions apply during the once-a-day full reconciliation, and only to rows **previously seen on the server** — locally added rows, rows whose push was "skipped", and MAL adult rows hidden by `nsfw=false` are never deleted.
- **Partial-response guard:** if the server returns less than 30% of the local row count (quota, truncated page, auth issue), nothing is deleted; an empty response never wipes the library.
- **"Deleted items came back" is fixed:** if a list refresh runs while an offline `DELETE` is still queued, the stale server row is not re-inserted (tombstone guard).
- **Offline additions survive:** rows with a queued `UPDATE` are neither overwritten nor deleted, and the toast says `· n pending edits kept`.
- **Entries deleted on the web** disappear locally within 24 hours (next full round); delta rounds cannot detect deletions — a deliberate data-safety trade-off.
