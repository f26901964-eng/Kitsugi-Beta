# PLAN_TASK — P2P Önbellek Birikimi + Bangumi Kurum Sayfaları

**Tarih:** 2026-10-11 · **Dal:** `arena/05816649-kitsugi-beta` · **Baz:** `ce36a3f`
**Kapsam:** (1) P2P torrent önbelleğinin diskte birikmesi ve sınırın uygulanmaması, (2) Bangumi yapımcı/yayıncı/stüdyo sayfalarının açılmaması veya veri göstermemesi.

---

## Plan (kök neden → çözüm eşlemesi)

| # | Kök neden | Kanıt | Çözüm |
|---|---|---|---|
| P1 | Oynatıcı kapanınca `P2pStreamingEngine.stopStream()` yalnızca akışı durduruyor, disk önbelleği boşaltılmıyordu | `KitsugiFullscreenPlayerScreen` `DisposableEffect.onDispose`, `scheduleStop` | Akış durunca `reclaimDiskCache(0L)` çağrılır (ayar açıksa) |
| P2 | Motor **korunan (aktif) torrent verisini tahliye etmez** (`"disk cache reclaim limited by protected torrent data"`). Torrent'ler motordan hiç kaldırılmadığı için sınır uygulanmıyordu | `libnuvio_engine.so` metinleri; `knownTorrentIds` yalnızca kapanışta temizleniyordu | Akış durunca `removeTorrent(id)` → ardından `reclaimDiskCache(0L)`; bağlanamayan başlatmalarda eklenen torrent de kaldırılır |
| P3 | Uygulama öldürülürse kapanış temizliği çalışmıyordu | — | Açılışta, motor çalışmıyorken ve akış yokken `p2p-engine/payload` artıkları süpürülür (`sweepLeftoverCacheOnLaunch`) |
| P4 | Ayar yoktu; kullanıcı davranışı kontrol edemiyordu | `P2pSettingsSection` | Yeni ayar: **"Video kapanınca önbelleği temizle"** (varsayılan AÇIK, `P2pSettingsRepository`) |
| P5 | Bangumi kurum adı araması hata/iptal verince boş liste → sonuç `0` olarak **kalıcı** önbelleğe yazılıyordu. Çip tıklaması ve "Yeniden Dene" aynı önbellekten `0` okuyup ağa gitmiyordu | `KitsugiBangumiDetailClient.findStudioPersonId` (`runCatching` iptali de yutuyordu) | Hata/iptal önbelleğe alınmaz; yalnızca başarılı-boş sonuç 10 dk negatif önbelleğe alınır (`STUDIO_MISS_TTL_MS`) |
| P6 | Kurum araması yalnızca ilk 5 sonucu alıyordu; aynı adlı çok kayıtta doğru kurum dışarıda kalıyordu | `searchPersons(limit = 5)` | Limit 5 → 10 (`STUDIO_SEARCH_LIMIT`) |

## Değişen dosyalar

- `app/src/main/java/com/kitsugi/animelist/core/p2p/P2pModels.kt` — `P2pSettingsUiState.clearCacheOnClose`
- `app/src/main/java/com/kitsugi/animelist/core/p2p/P2pSettingsRepository.kt` — ayar anahtarı, okuma/yazma, yükleme
- `app/src/main/java/com/kitsugi/animelist/core/p2p/P2pStreamingEngine.kt` — kapanışta torrent kaldırma + önbellek sıfırlama, açılış süpürmesi, bağlanamayan başlatma temizliği
- `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiBangumiDetailClient.kt` — kurum arama negatif önbellek düzeltmesi, limit artırımı
- `app/src/main/java/com/kitsugi/animelist/ui/components/p2p/P2pSettingsSection.kt` — yeni anahtar UI'ı

## Commit'ler

- `6de63b3` — P1, P3, P4, P5, P6
- `989d71b` — P2 (torrent kaldırma)

## Task'lar

- [x] T1 — P2P önbellek sıfırlama (kapanışta)
- [x] T2 — Motor koruma kısıtının çözümü (`removeTorrent` + `reclaimDiskCache`)
- [x] T3 — Açılış artık süpürmesi
- [x] T4 — "Video kapanınca önbelleği temizle" ayarı (UI + kalıcılık)
- [x] T5 — Bangumi kurum araması: hata önbelleğe alınmıyor, negatif TTL
- [x] T6 — Kurum araması limiti 10

## Bilinen sınırlar / açık noktalar

- Oynatma sırasında izlenen torrent korunduğu için, tek bir büyük paket sınırı geçici olarak aşabilir; oynatıcı kapanınca silinir. Oynatma sırasında da sınırı zorlamak için motorun parça tahliyesi cihazda ölçülmeli.
- `removeTorrent`'in veriyi diskten silip silmediği cihazda doğrulanmalı.
- Bangumi `/v0/search/persons` ucu deneysel. Canlı yanıt sandbox'tan doğrulanamadı; sorun sürerse logcat (`KitsugiBangumiDetailClient`) incelenmeli.

## Doğrulama

- Sandbox'ta JDK/Gradle ve Android bağımlılıkları yok; **derleme yapılmadı**. Motor sınıf imzaları (`removeTorrent(String, Continuation)`, `reclaimDiskCache(long, Continuation)`) AAR sınıf dosyasından doğrulandı.
- Cihaz testi önerilir:
  1. Bir torrent izleyip kapatın → Depolama ekranında P2P önbelleği sıfırlanmalı.
  2. Uygulamayı öldürüp açın → `p2p-engine/payload` artıkları silinmiş olmalı.
  3. Clannad detayında bir yapımcı/yayıncı çipine tıklayın → Bangumi kurum sayfası açılmalı; açılmazsa "Yeniden Dene" çalışmalı.
