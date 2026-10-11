# PLAN/TASK — Eklenti Vekil Sunucu Anahtarı, Akış Listesi Performansı, MAL Galeri Ortaklığı

Tarih: 2026-10-11
Durum: Kod düzeltmeleri tamamlandı; **Gradle derleme + cihaz doğrulaması bekliyor.**
Sürüm: 2.4.237 → **2.4.238**
Uygulama raporu: `EKLENTI_VEKIL_AKIS_PERFORMANS_MAL_GALERI_RAPORU_2026-10-11.md`
Sürüm notları: `release_notes_v2.4.238.md` (+ `RELEASE_NOTES.md` baş bölümü)

---

## Görev 1 — "GitHub Vekil Sunucu (jsDelivr)" anahtarı açılmıyor

### Belirti
Eklenti & Akış Ayarları → Video Sağlayıcıları sekmesindeki anahtara dokunuluyor; anahtar
"kapalı" durumunda kalıyor, hiçbir tepki yok.

### Kök neden (iki katman)
1. **Parametreler hiç geçilmiyordu.** `KitsugiAddonsSettingsDialog` şu varsayılanları taşıyor:
   `useGithubProxy: Boolean = false`, `onUseGithubProxyChanged: (Boolean) -> Unit = {}`.
   Ayarlar sekmesi bu değerleri geçiyordu; ancak **akış seçici** (`KitsugiStreamScreen.kt:632`) ve
   **manga kaynakları** (`MangaBrowseScreen.kt:361`) çağrıları geçmiyordu → switch her zaman
   `checked = false` çiziliyor, dokunuş boş lambda'ya gidiyordu.
2. **Ayar hiç okunmuyordu.** `CloudstreamRepoRepository` ayarı sorgulamıyordu; yalnızca "doğrudan
   istek başarısız olursa jsDelivr'a düş" yedeği vardı. Anahtar açılsa bile ilk istekler
   `raw.githubusercontent.com`'a gidiyordu.

### Yapılan işler
- [x] `KitsugiStreamScreen.kt`: telefon diyaloğu + `TvStreamSettingsDialog` çağrısına iki parametre bağlandı.
- [x] TV diyaloğuna `🌐 GitHub Vekil Sunucu (jsDelivr)` `TvSettingsActionRow` satırı eklendi
      (TV'de bu ayar hiç erişilebilir değildi).
- [x] `MangaBrowseScreen.kt`: `SettingsDataStore` + `AppSettings` + `rememberCoroutineScope` eklendi, parametreler bağlandı.
- [x] `CloudstreamRepoRepository.kt`: `settingsDataStore` + `useGithubProxySetting()`; `seedDefaultRepoIfEmpty`,
      `addRepo`, `fetchPluginsForRepo`, `syncAndAutoUpdate` (+ repo migrasyonu) artık `preferProxy` kullanıyor.
- [x] Anahtar açıkken doğrudan jsDelivr; kapalıyken doğrudan GitHub + başarısızlıkta jsDelivr yedeği korundu.

### Kabul kriteri
- Anahtar açılınca **açık kalır** (uygulama yeniden başlatıldığında da açık).
- Anahtar açıkken repo yenileme/eklenti listesi istekleri jsDelivr üzerinden başarılı olur.

---

## Görev 2 — Eklenti akış listesi veri çekerken/kaydırırken kasıyor

### Belirti
60+ sonuçlu bölümlerde (örn. Frieren S1B1 — DiziMom 1 / Anizium 60 / AnimeSiteleri 6) liste
kilitleniyor, kaydırma takılıyor, rozetler gecikiyor.

### Kök nedenler (dört ayrı)
1. **Her öğede giriş animasyonu:** `renderStreamResultsItems` her kartı
   `AnimatedVisibility(visible = true, fadeIn + slideInVertically(300ms))` ile sarıyordu.
   `visible` sabit olduğu için animasyon **her kaydırmada** yeniden oynuyordu.
2. **Kontrolsüz ağ ölçümü:** `StreamCard` compose edilir edilmez `StreamProbe.probe()` çağırıyordu
   (HLS master playlist indirme / HTTP HEAD). Hızlı kaydırmada onlarca istek kuyruğa giriyordu.
3. **Ölçüm kapısının (semaphore) kilitlenmesi:** Ölçümler bloklayıcı `OkHttp.execute()` ile
   yapılıyordu; coroutine iptal edilse bile istek arka planda sürüp izni 8 saniyeye kadar
   tutuyordu → yeni görünen kartların rozetleri hiç gelmiyordu.
4. **Filtre maliyeti:** Filtre aktif olmasa bile her `addonStates` güncellemesinde 60+ akış için
   `resolveStreamQuality` / `resolveStreamLangInfo` (regex + önbellek) çalışıyordu.

### Yapılan işler
- [x] `StreamResultsList.kt`: per-item `AnimatedVisibility` **kaldırıldı**; `renderStreamResultsItems`
      `listState` parametresi aldı; ölçüm izni `derivedStateOf { visibleItemsInfo.any { it.key == itemKey } }`.
- [x] `StreamCard.kt`: `probeEnabled` parametresi; `produceState(key1 = url, key2 = probeEnabled)`;
      **350 ms yerleşme gecikmesi** (`PROBE_SETTLE_DELAY_MS`); `StreamProbe.isProbeNeeded` kontrolü.
- [x] `StreamProbe.kt`: eşzamanlılık 3 → **2**; `isProbeNeeded()` eklendi; `probeHls` / `probeProgressive` /
      `fetchText` **suspend**; yeni `awaitResponse(call)` = `suspendCancellableCoroutine` + `Call.enqueue`
      + `invokeOnCancellation { call.cancel() }` → iptalde izin anında serbest kalır.
- [x] `StreamScreenContent.kt`: aktif filtre yokken **hızlı yol** (akış başına kalite/dil hesabı yok);
      eklenti çipleri `key`'li; iki layout da `listState` geçiyor.

### Kabul kriteri
- Liste kaydırma akıcı; hızlı kaydırmada gereksiz ağ isteği oluşmaz.
- Rozetler yalnızca görünen kartlarda ve **gerçek ölçüme dayalı** dolar (`Kalite ?` bilinmiyorsa kalır).
- "Ölçüm hatası" logları ölçüm sayısıyla orantılı azalır.

---

## Görev 3 — MAL ve MAL'a bağlı kaynaklar galeriye ortak olmalı

### Belirti
Anime/dizi/film detaylarında galeri MAL kaynaklı ek görselleri içermiyor; manga detaylarında
galeri yalnızca kapaktan ibaret kalıyor.

### Kök neden
- `KitsugiMalDetailClient.fetchPictures` yalnızca **MAL/Jikan kaynaklı detay** yolunda çağrılıyordu
  (`enrichWithinBudget`); diğer kaynaklarda `detail.pictures` boş kalıyordu.
- Manga dalında `fetchFanartGallery` **erken dönüş** yapıyordu → MAL ek görselleri hiç katılmıyordu.
- `fetchGalleryPictures()` yardımcısı repoda vardı ama **hiçbir yerden çağrılmıyordu** (ölü kod).

### Yapılan işler
- [x] `KitsugiMalDetailClient.kt`: `/pictures` için 80 kayıtlık `BoundedCache` (`mal.pictures`);
      boş sonuç önbelleğe **yazılmaz** (geçici 429/ağ hatası görselleri kalıcı engellemesin).
- [x] `ApiResultDetailViewModel.kt` (API detay sayfaları):
      anime/film → `resolveMediaGalleryIdentity()` ile MAL kimliği, `malDef` Fanart/TMDB/Shikimori ile
      **paralel**; manga → erken dönüşten önce MAL görselleri. Yeni `fetchMalGalleryItems()`
      (12 sn bütçe, URL'den kategori çıkarımı, kaynak etiketi "Jikan (MAL)", açıklama "MAL / Jikan ek görsel").
- [x] `MediaEntryDetailViewModel.kt` (kütüphane/MyList detayları): aynı birleştirme +
      `realMalIdForGallery()` güvenli kimlik çözümü.
- [x] **Kimlik güvenliği:** AniList (+100M) / Kitsu (+300M) / Bangumi stable ID'leri, TMDB ve Simkl
      kimlikleri MAL ID olarak **kullanılmaz**; kimlik yoksa istek yapılmaz.

### Kabul kriteri
- AniList/Kitsu/Bangumi kaynaklı bir anime detayında galeride "Jikan (MAL)" etiketli ek görseller görünür.
- Manga detayında galeri kapağın ötesine geçer.
- Yanlış yapıma ait görsel görünmez (kimlik çözülemiyorsa istek yok).

---

## Değişen dosyalar (bu görevde)

| Dosya | Ne yapıldı |
|---|---|
| `ui/screens/stream/KitsugiStreamScreen.kt` | Vekil sunucu parametreleri (telefon + TV) + TV ayar satırı |
| `ui/screens/manga/MangaBrowseScreen.kt` | Vekil sunucu parametreleri + DataStore bağlantısı |
| `data/repository/CloudstreamRepoRepository.kt` | `useGithubProxySetting()` + repo/eklenti yollarında `preferProxy` |
| `data/repository/StreamProbe.kt` | İptal edilebilir ölçüm, `isProbeNeeded`, kapasite 3 → 2 |
| `ui/screens/stream/StreamCard.kt` | `probeEnabled`, 350 ms yerleşme gecikmesi |
| `ui/screens/stream/StreamResultsList.kt` | Per-item animasyon kaldırıldı, görünürlük tabanlı ölçüm |
| `ui/screens/stream/StreamScreenContent.kt` | Filtresiz hızlı yol, anahtarlı çipler, `listState` |
| `ui/screens/detail/ApiResultDetailViewModel.kt` | MAL `/pictures` birleştirmesi (anime + manga) |
| `ui/screens/detail/MediaEntryDetailViewModel.kt` | MAL `/pictures` birleştirmesi (kütüphane kayıtları) |
| `data/remote/KitsugiMalDetailClient.kt` | `/pictures` LRU önbelleği |
| `app/build.gradle.kts` | Sürüm 2.4.238 |
| `RELEASE_NOTES.md`, `release_notes_v2.4.238.md` | Sürüm notları |

Toplam: **9 kod dosyası**, +293 / −53 satır.

---

## Doğrulama planı (cihaz)

1. **Vekil sunucu:** Ayarlar → Eklentiler → anahtarı aç → uygulamayı kapat/aç → açık kaldığını doğrula.
   Ardından bir repoyu yenile (logcat: `Direct GitHub fetch` yerine jsDelivr isteği).
2. **Performans:** 60+ sonuçlu bölümü aç, hızlıca yukarı/aşağı kaydır; kare düşmesi ve rozet
   gecikmesini gözle. `adb logcat -s StreamProbe` ile ölçüm sayısının kart sayısıyla değil,
   **görünen kart sayısıyla** orantılı olduğunu doğrula.
3. **Galeri:** AniList/Kitsu/Bangumi kaynaklı bir anime detayında galeride "Jikan (MAL)" kaynaklı
   görseller; bir manga detayında kapak dışındaki ek görseller.

## Riskler / geri alma
- `StreamProbe` artık `Call.enqueue` kullanır: davranış aynı, sadece iptal gerçekten uygulanır.
- Görünürlük tabanlı ölçüm nedeniyle rozetler görünür kartta 350 ms + ölçüm süresi kadar gecikebilir
  (bilinçli tercih: doğru veri, akıcı liste).
- MAL galerisi kimlik yoksa hiç istek yapmaz → yanlış eşleşme riski yoktur.
- Her dosya bağımsız geri alınabilir; `app/build.gradle.kts` + notlar hariç tutulursa derleme aynı kalır.
