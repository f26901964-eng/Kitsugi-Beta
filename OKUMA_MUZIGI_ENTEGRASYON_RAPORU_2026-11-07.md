# Okuma Müziği (Manga Okuyucusu) — Kaynak, API ve Ses Entegrasyonu Raporu

**Tarih:** 2026-11-07
**Kapsam:** Manga okuyucusu ayarlar paneli (açılır panel), Entegrasyonlar → Müzik sekmesi,
ses oynatma motoru, ön plan servisi + bildirim paneli.

---

## 1. Tespit edilen sorunlar (kök neden)

| # | Belirti | Kök neden |
|---|---------|-----------|
| 1 | Müzik/ambiyans özelliği **hiç yok** — ne okuyucuda ne entegrasyonlarda bir kaynak, API bağlantısı veya istasyon alanı vardı | Özellik kod tabanında hiç eklenmemiş. Ekranlarda yarım kalan hiçbir alan yoktu; bu yüzden "tıklıyorum ses çalmıyor" davranışı **tasarım gereği** ortaya çıkıyordu. |
| 2 | Okuyucu ayarları açılır panelinde alt kısımdaki ayarlar erişilemiyor / panel "donuyor" | `KitsugiSheetOrDialog` içeriği **kaydırılamıyordu**; içerik yüksekliği ekranın %90'ını aştığında (renk filtresi, parlaklık vb.) tamamen kırpılıyordu. |
| 3 | Bildirim panelinde medya denetimi yok | Arka planda çalma/denetim için **MediaSession + ön plan servisi** yoktu. |
| 4 | Olası "ses yok" senaryoları | (a) HTTP/`.m3u8`/HTML adreslerinin oynatıcıya verilmesi, (b) foreground servis kısıtı olan cihazlarda sesin tamamen susması, (c) `POST_NOTIFICATIONS` verilmediğinde denetim görünmemesi. |

---

## 2. Yapılan işler

### 2.1 Çekirdek katman — `core/music/`

| Dosya | Görev |
|-------|-------|
| `MangaMusicModels.kt` | `MangaMusicSource` (RADIO_BROWSER / BUILT_IN / OFF), `MangaMusicMood` (lo-fi, klasik, caz, ambiyans), `MusicStation`, `MusicPlaybackState`, `MusicStationLoadResult` |
| `MangaMusicCatalog.kt` | İstasyon kataloğu: **radio-browser.info** açık API'si (anahtar yok, 3 mirror, 20 dk önbellek) + **ağ gerektirmeyen yerleşik yedek liste** (SomaFM / NPO Radio 4). URL doğrulama, kodc/HLS filtresi, tekrar ayıklama. |
| `MangaMusicSettingsStore.kt` | Ayrı DataStore: etkin, kaynak, ruh hâli, ses, otomatik başlatma, son istasyon |
| `MangaMusicPlayer.kt` | Tek örnek oynatıcı: çal/duraklat, sonraki/önceki, ses, hata yönetimi, canlı yayın düşünce otomatik sıradaki istasyon, **yerel motor yedeği** |
| `MangaMusicService.kt` | `MediaSessionService` + `ExoPlayer`: arka plan çalma, **bildirim paneli / kilit ekranı** denetimi, ses odağı, kulaklık çıkarılınca duraklama |

**Kaynak & API bağlantısı (sorulan kısım):**
- `https://<mirror>/json/stations/search?tag=<lo-fi|classical|jazz|ambient>&limit=30&order=votes&reverse=true&hidebroken=true`
- Yanıt doğrudan ses akışına çevrilir; `url_resolved` yoksa `url`, o da yoksa istasyon atılır.
- Yalnızca `https://` + doğrudan ses (`.m3u8`, `.pls`, `.m3u`, `.html`, `.xsp3` elenir) kabul edilir.
- API ulaşılamazsa liste **boş dönmez**: yerleşik yedek istasyonlar `usedFallback=true` ile döner, panelde "çevrimdışı yedek liste kullanılıyor" uyarısı gösterilir.

### 2.2 Okuyucu paneli — `MangaMusicPanel.kt`

- Açma/kapama anahtarı, ruh hâli çipleri, kaynak çipleri (Radyo API / Dahili liste),
  çal–duraklat, önceki/sonraki, ses sürgüsü, istasyon listesi (ilk 6), "Yenile",
  durum satırı (kaynak / hata / yedek uyarısı).
- Panel kapansa da müzik devam eder; okuyucu üst çubuğuna **tek dokunuşluk müzik düğmesi** eklendi
  (çalıyorken vurgu rengiyle yanar).
- Okuyucu açılışında "okuyucu açılınca başlat" işaretliyse son istasyon kendiliğinden başlar;
  bölüm geçişlerinde çalan yayın yeniden başlatılmaz.

### 2.3 Okuyucu ayarlar paneli düzeltmesi

- `KitsugiSheetOrDialog(innerColumnScrollState = …)` + `Modifier.verticalScroll(…)` eklendi.
  Panel artık ekran yüksekliğini aşsa da tüm ayarlar kaydırılarak erişilebilir.
- Müzik bölümü panelin en üstüne kart olarak yerleştirildi.

### 2.4 Entegrasyonlar → **Müzik** sekmesi

`KitsugiIntegrationsSettingsDialog` 4 sekmeden 5 sekmeye çıkarıldı (`Müzik`).
`KitsugiMusicIntegrationTab.kt` içeriği:
- Okuma müziği ana anahtarı + okuyucu açılışında otomatik başlatma
- Kaynak seçimi (Radyo API / Dahili liste), ruh hâli seçimi, ses seviyesi
- **Bağlantıyı test et**: gerçek ağ isteği atar, `"Bağlantı başarılı · N istasyon bulundu"`
  veya `"Bağlantı kurulamadı: <neden>"` döner; yedek listeye düşülürse bunu açıkça yazar.
- Aktif yayın varsa istasyon adı ve durumu gösterilir.

### 2.5 Ses motoru sağlamlaştırmaları

- `AudioAttributes(USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC)` + `handleAudioFocus=true`.
- `setHandleAudioBecomingNoisy(true)` — kulaklık çıkarılınca yayın durur.
- Foreground servis 5 saniye içinde foreground'a geçmezse ANR riski doğduğu için
  `onStartCommand`'da çalma yoksa servis kendini kapatır.
- Servis başlatılamazsa (Android 12+ arka plan kısıtı, MIUI/EMUI pil optimizasyonu)
  900 ms sonra **yerel motor** devreye girer: bildirim olmasa da ses mutlaka çalar.

---

## 3. Değişen / eklenen dosyalar

```
app/src/main/java/com/kitsugi/animelist/core/music/MangaMusicModels.kt          (yeni)
app/src/main/java/com/kitsugi/animelist/core/music/MangaMusicCatalog.kt         (yeni)
app/src/main/java/com/kitsugi/animelist/core/music/MangaMusicSettingsStore.kt   (yeni)
app/src/main/java/com/kitsugi/animelist/core/music/MangaMusicPlayer.kt          (yeni)
app/src/main/java/com/kitsugi/animelist/core/music/MangaMusicService.kt         (yeni)
app/src/main/java/com/kitsugi/animelist/ui/screens/manga/components/MangaMusicPanel.kt  (yeni)
app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiMusicIntegrationTab.kt     (yeni)
app/src/main/java/com/kitsugi/animelist/ui/screens/manga/MangaReaderScreen.kt   (panel + düğme + kaydırma)
app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiIntegrationsSettingsDialog.kt (5. sekme)
app/src/main/AndroidManifest.xml                                               (MediaSessionService)
app/src/main/res/values/strings.xml, values-en/strings.xml                    (33 yeni metin, TR+EN)
app/src/test/java/com/kitsugi/animelist/core/music/MangaMusicCatalogTest.kt     (yeni testler)
```

---

## 4. Testler

`MangaMusicCatalogTest` (JVM, ağ gerektirmez):
1. Yalnızca HTTPS doğrudan akışlar geçerli; `http://`, `.m3u8`, `.pls`, `.html` reddedilir.
2. Ayrıştırıcı bozuk kayıtları (boş ad, HLS bayrağı, video kodeği) ve tekrarları ayıklar.
3. Geçersiz JSON'da çökmez.
4. Her ruh hâlinin en az bir oynatılabilir yerleşik yedeği var.
5. Bilinmeyen etiket/kaynak değerleri güvenli varsayılana düşer.
6. Kaydedilmiş istasyon ayarlardan doğru geri kurulur.

---

## 5. Elle doğrulama adımları (cihazda)

1. **Ayarlar → Entegrasyonlar → Müzik** → "Bağlantıyı test et" → istasyon sayısı görünmeli.
2. Manga okuyucusunu aç → üst çubuktaki müzik düğmesine bas → ses başlamalı;
   bildirim panelinde ve kilit ekranında istasyon adı + oynat/duraklat görünmeli.
3. Ayarlar panelini aç → tüm bölümler (müzik, okuma yönü, sığdırma, renk filtresi, parlaklık)
   kaydırılarak erişilebilir olmalı.
4. Uçak moduna geç → "Dahili liste" kaynağı seç → yine ses çalmalı (SomaFM/NPO akışları).
5. Okuyucudan çık → ses devam etmeli (bildirimden durdurulabilmeli).

---

## 6. Bilinen sınırlar

- Radyo yayınları **canlı akıştır**: ileri/geri sarma yoktur, parça başına süre gösterilmez.
- radio-browser.info topluluk dizini olduğu için tek tek istasyonlar zaman zaman düşebilir;
  bu durumda oynatıcı otomatik olarak sıradaki istasyona geçer (2 deneme) ve hata metni gösterilir.
- Uygulama tamamen kapalıyken (görevden silinmiş + müzik çalmıyorken) servis kendini kapatır;
  bu bilinçli bir tercihtir (hayalet bildirim bırakmamak için).
