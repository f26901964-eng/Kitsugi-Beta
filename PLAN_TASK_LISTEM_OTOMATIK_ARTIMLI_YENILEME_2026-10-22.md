# PLAN / TASK — Listem: Otomatik + Artımlı Yenileme (2026-10-22)

**Dal:** `arena/bf0ccbea-kitsugi-beta`
**Kapsam:** Listem sayfası, arka plan liste yenilemesi, yenileme mesajları ve Listem'e özgü mantık hataları.

## Kullanıcı isteği

> "Hangi kaynağa ait olursa olsun veya Listem sayfasında Tümü seçili olursa olsun artık
> sayfalar otomatik güncellenmeli; yenilenme özelliği de listeyi sıfırdan çekermiş gibi
> değil, eksikleri tamamlarmış gibi yenilemeli. Listem sayfası mantığındaki eksik/hatalı
> davranışları da bul ve düzelt."

## Tespit edilen kök nedenler

| # | Sorun | Etki |
|---|-------|------|
| 1 | Arka plan otomatik yenileme yalnızca **AniList · MAL · Simkl**'i kapsıyordu; **Kitsu, Shikimori, Bangumi** hiç otomatik güncellenmiyordu. | "Hangi kaynak olursa olsun kendini güncellemiyor" — özellikle yalnızca Bangumi/Kitsu bağlı kullanıcılarda hiçbir şey otomatik gelmiyordu. |
| 2 | Tek bir **global** `last_list_sync_ms` damgası + **24 saat** kilidi vardı; damga `syncCount > 0` koşuluyla, **kısmi** başarıda da yazılıyordu. | Bir kaynak hata verse diğerleri de 24 saat kilitleniyor; yeni bağlanan hesap açılışta hiç çekilmiyordu. |
| 3 | Yenileme `replaceSourcePreservingAdultFlags` = **`deleteBySource + insertAll`** (sil–yeniden yaz) idi. | Liste her açılışta baştan kuruluyordu: bekleyen çevrimdışı senkron işleri düşüyor, Room tüm tabloyu geçersiz kılıp listeyi baştan çiziyor, kaydırma konumu bozuluyordu. |
| 4 | `smartImport` **her eşleşen satırı koşulsuz UPDATE** ediyordu (değişmese bile). | "Yenile" = tüm kütüphaneyi yeniden yazmak; sayfalar baştan çiziliyordu. |
| 5 | Pull-to-refresh göstergesi `onRefresh()` çağrısından **hemen sonra** kapatılıyordu (istekler ViewModel'de asenkron). | Kullanıcı yenilendiğini hiç göremiyordu; animasyon bir kare bile görünmüyordu. |
| 6 | Bağlı olmayan sekmede de pull-to-refresh ağ isteği başlatıyordu. | Gereksiz token hataları / hata snackbar'ları. |
| 7 | `AppViewModel.hasAnyConnectedAccount` **Bangumi'yi saymıyordu**. | Yalnızca Bangumi bağlı kullanıcı uygulamayı açtığında "Tümü" yerine boş AniList sekmesi açılıyordu. |
| 8 | `AiringNotificationWorker` gece Simkl'i **sil–yeniden yaz** ile yeniliyordu. | Gece işi çalıştığında Listem sayfaları baştan kuruluyordu. |

## Uygulanan değişiklikler

### 1. `MediaEntryRepository` — artımlı birleştirme çekirdeği
- Yeni `planSourceMerge(...)` (saf fonksiyon, Room'a dokunmaz):
  - eşleşen satır **hiç değişmediyse UPDATE listesine girmez** → Room'a yazılmaz, akış emit etmez;
  - +18 işareti **geri alınmaz** (`true → false` yazılmaz);
  - çözülmüş Latin/İngilizce başlıklar korunur (`preserveResolvedTitles`);
  - uzak kaynağın göndermediği `updatedAt` damgası **sıfırlanmaz**;
  - kimlik anahtarları satır başına bir kez hesaplanır (1000 satırda ~1M gereksiz küme tahsisi gitti).
- Yeni `SourceRefreshStats(fetched, added, updated, unchanged)`.
- Yeni `refreshFromSource(source, entries)`: **hiçbir satırı silmez**, bekleyen senkron işini düşürmez, yalnızca eksikleri ekler / gerçek farkları yazar.
- `smartImport(...)` aynı planlayıcıyı kullanır (davranışı korunur, artık gereksiz UPDATE üretmez).

### 2. `core/sync/AutoListRefresh` (yeni)
- **Altı kaynak** (AniList · MAL · Simkl · Kitsu · Shikimori · Bangumi), yalnızca **bağlı** olanlara istek.
- **Kaynak başına** deneme/başarı damgası: bir kaynağın hatası diğerlerini kilitlemez; sürekli hata veren kaynak her açılışta yeniden denenmez.
- Eşzamanlılık kilidi (mutex): uygulama açılışı ile Listem açılışı çakışırsa iş **bir kez** yapılır.
- Süreler: açılışta `6 saat`, Listem açılışında `15 dk` bayatlık eşiği.
- `summaryMessage(...)`: "Listem güncellendi · AniList: +3 yeni, 1 güncel" — otomatik çalışmada değişiklik yoksa **sessiz**.

### 3. Bağlantılar
- `KitsugiApplication`: eski 90 satırlık üç kaynaklı blok → `AutoListRefresh.refreshStale(STARTUP_MIN_INTERVAL_MS)`.
- `MyListTabPageWrapper` + `MyListScreen`: sayfa açılışında `refreshStale(FOREGROUND_MIN_INTERVAL_MS)`; seçili sekmeden **bağımsız** olarak tüm bağlı kaynaklar taranır.
- `MyListContentPage`: gösterge artık `AuthViewModel.isAnyListImportRunning` (gerçek durum); bağlı olmayan sekmede istek başlatılmaz.
- `AiringNotificationWorker`: Simkl gece yenilemesi artık `refreshFromSource`.

### 4. Kullanıcı mesajları
- `account_sync_refresh_changed` / `account_sync_refresh_unchanged` (TR + EN):
  "AniList yenilendi — 3 yeni kayıt eklendi, 1 kayıt güncellendi (liste baştan çekilmedi)".
- Altı kaynağın içe aktarma mesajı artık "N kayıt aktarıldı" yerine gerçek farkı söyler.

### 5. Listem mantık düzeltmeleri
- `hasAnyConnectedAccount` → Bangumi eklendi (açılış sekmesi doğru).
- Pull-to-refresh: gerçek durum + bağlantı koşulu + çift istek koruması.

## Eklenen testler

`app/src/test/java/com/kitsugi/animelist/data/local/SourceMergePlanTest.kt`
- `identicalRemoteListWritesNothing` — değişmeyen liste DB'ye hiç yazılmaz.
- `missingEntryIsAddedAndExistingRowIsKept` — yalnızca eksik eklenir, silme yok.
- `changedProgressUpdatesOnlyThatRow` — yalnızca değişen satır UPDATE edilir.
- `allowDeleteTrueStillReportsRemovals` — `allowDelete = true` davranışı korunur (tam içe aktarma).
- `adultFlagAndResolvedTitlesAreNeverRolledBack` — +18 ve çözülmüş başlık korunur.
- `zeroRemoteTimestampKeepsLocalUpdatedAt` — zaman damgası sıfırlanmaz.
- `emptyRemoteListNeverDeletesLocalRows` — boş yanıt silme sayılmaz.

## Kabul kriterleri

- [ ] Yalnızca Bangumi/Kitsu/Shikimori bağlıyken Listem açıldığında liste kendiliğinden dolar.
- [ ] Hangi platform sekmesi seçili olursa olsun, açılışta tüm bağlı kaynaklar için artımlı yenileme tetiklenir ve **tüm** sayfalar güncellenir (Room akışı).
- [ ] Yenileme sırasında liste boşalmaz, kaydırma konumu korunur; değişiklik yoksa DB'ye yazılmaz.
- [ ] Pull-to-refresh göstergesi gerçek iş bitene kadar görünür.
- [ ] `./gradlew testDebugUnitTest --tests "com.kitsugi.animelist.data.local.SourceMergePlanTest"` yeşil.

## Notlar / bilinçli takaslar

- **Otomatik yenileme uzakta silinen kayıtları yerelden silmez.** Kullanıcı verisi kaybını önlemek için silme yalnızca tam içe aktarma (`allowDelete = true`) akışında yapılır.
- Listem sayfası **açıkken** periyodik yenileme yoktur (ağ/kota dostu); sayfa her açıldığında 15 dk eşiğiyle kontrol edilir.
- Sandbox'ta Android SDK/JDK bulunmadığından Gradle derlemesi burada koşturulamadı; kod derlemesi ve testler geliştirici makinesinde `./gradlew testDebugUnitTest` ile doğrulanmalıdır.
