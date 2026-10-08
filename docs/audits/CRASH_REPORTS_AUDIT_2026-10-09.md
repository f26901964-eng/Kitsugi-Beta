# Çökme Raporları Denetimi — 2026-10-09

Kapsam: Drive üzerindeki üç "TEMİZ OLMAYAN KAPANMA" raporu (A, B, C). Her satır aşağıda bir
bulgu olarak listelenmiştir. Durum sütunu şu anlamdadır:

- **Düzeltildi (kod)** — kodda giderildi; cihazda doğrulanmadı.
- **Kısmen** — azaltıldı ama kök neden hâlâ açık.
- **Açık** — henüz çözülmedi; ek veri/inceleme gerekir.
- **Doğrulandı (mevcut kodda yok)** — eski sürümde vardı, mevcut kodda yeniden üretilemiyor.

> Derleme ve testler çalıştırılmadı (ortamda Java yok). Kotlin değişiklikleri parantez dengesi ve
> elle gözden geçirme ile, C++ değişiklikleri `g++ -fsyntax-only` ile (x86_64 yolu, Android başlıkları
> için taslaklarla) kontrol edildi. aarch64 yolu yalnızca alan erişim kalıbı olarak sınandı.

## Özet tablosu

| ID | Rapor | Konu | Durum |
|----|-------|------|-------|
| A-1 | A | Sessiz native çökme (23:07); çökme anına ait native satır yok | Kısmen |
| A-2 | A | Dört eklentinin otomatik güncellemesi HTTP 404 ile başarısız | Düzeltildi (kod) — kök neden depo tarafında |
| A-3 | A | "Log tamponunda QueueBuffer time out", "sticky GC", "Room hit" | Açık (performans, izleme) |
| A-4 | A | Başarısız native raporda "rapor üretilemez" metni | Düzeltildi (kod) |
| B-1 | B | `IllegalArgumentException: Belirsiz kitsu eşlemesi` (v2.4.189) | Doğrulandı (mevcut kodda yok) |
| B-2 | B | Sürüm eski (2.4.189); cihazın güncellenmesi gerekir | Kullanıcı aksiyonu |
| C-1 | C | "ÖLÜM SEBEBİ" yanlış süreci gösteriyor (pid 8690, 10-06) | Düzeltildi (kod) |
| C-2 | C | "SON EKRAN: Stats(depth=1)" bayat; gerçek son ekran Tab(Profile) | Düzeltildi (kod) |
| C-3 | C | Native iz adresleri modüle eşlenemiyor (maps yok) | Düzeltildi (kod) — sonraki çökmede işe yarar |
| C-4 | C | Native izin ikinci kaydı (SIGABRT, çöp fault_addr) | Düzeltildi (kod) |
| C-5 | C | Native iz yalnızca 48 çerçeve; PC/LR yok | Düzeltildi (kod) |
| C-6 | C | RenderThread SIGSEGV (fault 0x20) — tekrarlayan imza | Açık (kök neden bilinmiyor) |
| C-7 | C | Hızlı sekme değişimi, ana iş parçacığında 226 ms binder çağrısı, "Skipped 39 frames" | Açık (performans) |
| C-8 | C | Bangumi kaynağında karakter detayı istemcide desteklenmiyor | Kısmen (profil sekmesi web'e yönlendiriyor) |

Ek (kod incelemesi, rapordan bağımsız):

| ID | Konu | Durum |
|----|------|-------|
| X-1 | Native izin 30 dakikalık pencere ile okunuyordu; sessiz kapanma raporu saatler sonra açılırsa kanıt kayboluyordu | Düzeltildi (kod): sessiz kapanma yolunda 7 gün |
| X-2 | `_Unwind_Backtrace` sinyal işleyicide async-signal-safe değil | Açık (sınırlama; PC/LR ile kısmen telafi edildi) |
| X-3 | Alternatif sinyal yığını yalnızca ana iş parçacığında kurulu | Açık (RenderThread için kapsam dışı bırakıldı) |
| X-4 | `GlobalScope.launch` (plugin yeniden yükleme) | Açık (düşük öncelik, davranış değişmedi) |

---

## Rapor A — `11j43…` (v2.4.206-beta.29858057, 2026-10-08 23:07:52)

Başlık alanları: arka planda (BACKGROUND), yaşam süresi 15 sn, son ekran `Tab(Explore)`.

- **A-1 (Sessiz native çökme).** Rapor "rapor üretilemez" diyor; çökme anı (23:07) için logda bir
  `Fatal signal` satırı yok. Logda görünen SIGSEGV (`code 1`, `fault addr 0x20`, RenderThread)
  satırları 10-06 09:35, 10-06 23:50, 10-08 20:39 süreçlerine ait; yani bu imza başka süreçlerde de
  tekrarlanmış (bkz. C-6). Native iz bu rapora eklenmemiş. Olası nedenler: (a) işleyici o an kurulu
  değildi, (b) iz yazılamadı, (c) iz 30 dakika penceresi dışında kaldı (bkz. X-1). Kesin neden
  kanıtlanamadı. Yapılan: X-1 düzeltmesi; C-3/C-5 ile bir sonraki çökmede maps + PC/LR gelecek.
- **A-2 (Eklenti otomatik güncelleme 404'leri).** JetFilmizle (58→97), HDFilmCehennemi (58→97),
  FullHDFilmizlesene (33→72), FilmMakinesi (59→70). Her biri 6–8 aday URL denedi (`?_ts=` ile),
  hepsi 404 döndü, sonra "Failed to download update" yazıldı. Her eşitlemede tekrarlanıyordu.
  - Kod: `CsAutoUpdateBackoff` (yeni) — başarısız (plugin, sürüm) çifti 12 saat boyunca yeniden
    denenmez; başarılı güncellemede temizlenir; 7 günden eski kayıtlar budanır.
    `CloudstreamRepoRepository.syncAndAutoUpdate` bu kontrolü kullanır.
  - Kök neden depo tarafında: manifest (ör. v97) listeliyor ama dosya yansıda yok. Uygulama sürüm
    yükseltemez; eklenti deposunun (Kraptor123/cs-kraptor builds dalı) durumu kontrol edilmeli.
- **A-3 (Log bulguları).** `QueueBuffer time out` (RenderInspector), "sticky GC" (en yüksek 512 MB),
  `Room hit: malId=16498 → tmdbId=1429` (bilgi amaçlı). Yük/performans göstergesi; kod değişikliği
  yapılmadı.
- **A-4 (Metin).** Başlık "rapor üretilemez" diyordu; bu yanıltıcıydı (rapor üretilmişti, yalnızca Java
  yığını yoktu). Metin güncellendi: "Java istisnası yok … ham yerel iz raporun sonundaki bölümde".

## Rapor B — `18Is6…` (v2.4.189-beta.29856355, 2026-10-07 17:56:25)

- **B-1.** `IllegalArgumentException: Belirsiz kitsu eşlemesi: Date A Bullet: Nightmare or Queen;
  yerel kayıtlar korunuyor` — `MediaEntryRepository.smartImport`, iş parçacığı `DefaultDispatcher-worker-8`,
  BACKGROUND, daemon; heap 39 MB / 512 MB. Mevcut kodda `MediaEntryRepository.kt` içinde `require`,
  `check`, `error` veya `throw` yok; belirsiz eşleşmede `keyMatch ?: exactTitleMatch ?: exactEngMatch ?:
  maxByOrNull{updatedAt} ?: first()` zinciri kullanılıyor. Sürüm notlarında da bu düzeltme kayıtlı.
  Doğrulandı: mevcut kodda yeniden üretilmiyor.
- **B-2.** Rapor 2.4.189 sürümünden. Cihazdaki uygulama güncel değilse bu hata sürüm farkıyla görülür.

## Rapor C — `1Q6og…` (v2.4.207-beta.29858160, 2026-10-08 23:37:27)

Başlık: ön planda (FOREGROUND), yaşam süresi 1473 sn (23:12:42 → son canlı 23:37:16), pid 28270.

- **C-1 (Yanlış ölüm sebebi — kritik).** "ÖLÜM SEBEBİ" satırı `pid 8690` için `10-06 09:35:29` tarihli
  satırı gösteriyordu; yani iki gün önceki başka bir sürecin çökmesi bu raporun teşhisi olarak yazılmıştı.
  Neden: logcat tamponunun tamamında ilk `Fatal signal` satırı alınıyordu, süreç filtresi yoktu.
  Düzeltme: yalnızca çöken sürecin pid'i ve oturum penceresi (başlangıç − 30 sn … son canlı + 15 dk)
  değerlendiriliyor; `exiting due to SIG_DFL handler` satırı da native kanıt sayılıyor. Bu rapordaki
  gerçek kanıt (`10-08 23:37:24.093 28270 28354 F libc : exiting due to SIG_DFL handler for signal 11`)
  artık teşhis olarak seçilecek. `OutOfMemoryError` yalnızca çöken pid'den, ilgisiz kanıt satırları
  kaldırıldı.
- **C-2 (SON EKRAN bayat).** Rapor `Stats(depth=1)` yazıyordu; eylem izinin son satırı
  `23:37:23 ekran → Tab(tab=Profile)`. Neden: "screen" alanı periyodik heartbeat anlık görüntüsünden
  okunuyordu. Düzeltme: eylem izindeki son `ekran →` kaydı kullanılıyor; anlık görüntü farklıysa parantez
  içinde yazılıyor.
- **C-3 (Native iz çözümlenemiyor).** Çerçeveler ham adres (`0x7f5a109550` …). `/proc/self/maps` raporda
  yoktu, bu yüzden hangi `.so` dosyası olduğu belirlenemiyor. Düzeltme: işleyici artık `/proc/self/maps`
  dökümünü (yalnızca open/read/write/close) dosyaya yazıyor; `addr2line`/`ndk-stack` ile çevrimdışı çözüm
  mümkün. **Bu düzeltme bir sonraki çökmeden itibaren işe yarar.**
- **C-4 (Çift kayıt).** İzde SIGSEGV'den sonra bir SIGABRT kaydı var (`si_code −1`, çöp `fault_addr`).
  Düzeltme: işleyici yalnızca ilk çökmeyi kaydediyor (`g_recorded`).
- **C-5 (Çerçeve sınırı ve PC/LR).** 48 çerçeve (sınırda kesiliyordu); kesin hata adresi (PC) ve dönüş
  adresi (LR) ayrıca yazılmıyordu. Düzeltme: 96 çerçeve; `ucontext` üzerinden `pc`/`lr` (aarch64 ve arm).
- **C-6 (RenderThread SIGSEGV, fault 0x20 — tekrarlayan).** Aynı imza dört süreçte görüldü: 10-06 09:35
  (pid 8690), 10-06 23:50 (pid 15600), 10-08 20:39 (pid 13024), 10-08 23:37 (pid 28270). Hepsi RenderThread,
  `fault addr 0x20` (NULL işaretçi + 0x20 ofseti tipik bir yapı erişimi). Kesin neden **bilinmiyor**.
  Kod incelemesinde öne çıkan adaylar (kanıtsız):
  1. Görüntülere uygulanan `Modifier.blur` / `RenderEffect` (`KitsugiNsfwImage`, API 31+). Keşfet, Profil
     ve Stats gibi görsel yoğun ekranlarda kullanılıyor; çökme ekranlarıyla örtüşüyor.
  2. Hızlı sekme değişimi sırasında ağır composable'ların (`y7.e2.a`, `x2.a.k` derleme uyarısı) yarattığı
     yoğun RenderThread işi.
  3. Video yüzeyleri (`KitsugiMpvSurfaceView`, `PlayerView`) — bu ekranlarda aktif değil, düşük olasılık.
  Doğrulama için: bir sonraki çökme raporundaki `native_crash.txt` içindeki maps + PC/LR ile `.so` adı
  ve ofset bulunmalı.
- **C-7 (Hızlı sekme değişimi ve kare kaybı).** Son 21 saniyede 10 sekme değişimi (Explore → MyList →
  Search → Profile → Settings → Profile → Stats → Profile → Search → Profile). Logcat: `QueueBuffer`/
  `DequeueBuffer` zaman aşımları, "Skipped 39 frames", ana iş parçacığında 226 ms `IContentProvider`
  binder çağrısı, büyük composable derlemesi. Kod değişikliği yapılmadı; ölçüm gerekir.
- **C-8 (Bangumi kaynaklı öğeler).** Eylem izinde `ApiResultDetail(source=bangumi)` kayıtları var
  (ör. malId `500010380` Steins;Gate; `500473417` film; `500707656` ve `500268279` manga). Bu akış
  çalışıyor. Not: `CharacterDetail(characterId=40882, source=anilist)` kaydı **AniList** kaynaklı
  (Eren Yeager), Bangumi değil. Bangumi karakter/kişi detayı istemcide desteklenmiyor
  (`KitsugiCharacterClient.fetchCharacterDetail` → `else -> null`); bu çökme değil, boş sonuçtur.
  Yeni Bangumi profil sekmesi karakter/kişi öğelerini bgm.tv sayfasında açar (uygulama içi detay yok).

## Native işleyici (kitsugi_crash_handler.cpp) — ek bulgular

- `sigaltstack` yalnızca `nativeInstall` çağrılan (ana) iş parçacığında kurulu → RenderThread'de taşma
  durumunda işleyici çalışamayabilir (X-3, açık).
- `_Unwind_Backtrace` async-signal-safe değil; kilitli bir durumda (ör. dlopen sırasında) takılma riski
  var (X-2, açık). PC/LR bağlamdan alındığı için en azından ilk çerçeve garanti.
- Maps dökümü, PC/LR, tek kayıt koruması, 96 çerçeve ve `<stdint.h>` eklendi.

## Yapılan kod değişiklikleri (bu denetim)

| Dosya | Değişiklik |
|-------|-----------|
| `core/diagnostics/KitsugiSessionSupervisor.kt` | pid/zaman penceresi, SON EKRAN eylem izinden, metin |
| `core/diagnostics/NativeCrashBridge.kt` | `UNCLEAN_TRACE_MAX_AGE_MS` (7 gün) |
| `core/diagnostics/KitsugiCrashLogger.kt` | sessiz kapanma dallarında 7 günlük native pencere |
| `app/src/main/cpp/kitsugi_crash_handler.cpp` | maps dökümü, PC/LR, tek kayıt, 96 çerçeve, `<stdint.h>` |
| `data/cloudstream/CsAutoUpdateBackoff.kt` (yeni) | başarısız sürüm için 12 saatlik geri çekilme |
| `data/repository/CloudstreamRepoRepository.kt` | geri çekilme kontrolü, başarı/başarısızlık kaydı |

## Cihazda doğrulanması gerekenler

1. Eklenti otomatik güncelleme: uygulamayı birkaç kez açın; logda `Auto-update skipped (recent failure …)`
   satırları görünmeli, 404 istek sayısı azalmalı. Eklenti sürümleri değişmeyebilir (depo kaynaklı).
2. Çökme raporu: bir sonraki çökmede `native_crash.txt` içinde `=== PROC MAPS` bölümü ve `pc=`/`lr=`
   satırları olmalı. Raporda "ÖLÜM SEBEBİ" yalnızca çöken pid'den gelmeli.
3. "SON EKRAN" alanı çökme anındaki son sekmeyi göstermeli.
4. Bangumi profil sekmesi (bkz. `docs/audits/BANGUMI_PROFILE_RESEARCH_2026-10-09.md`).

## Açık kalan işler

- C-6'nın kök nedeni: bir sonraki çökme raporu (maps + PC/LR) ile `.so` ve ofset belirlenmeli.
  Aday olan `Modifier.blur`/RenderEffect kullanımı, cihazda kapatıp karşılaştırarak test edilebilir.
- C-7 için performans profili (Macrobenchmark / Perfetto) gerekli; hızlı sekme değişiminde ağır ekranların
  yeniden oluşturulması azaltılabilir.
- X-2/X-3: sinyal işleyicide yığın ve unwinder sınırlamaları; üretim için frame-pointer tabanlı yürüyüş ya
  da çevrimdışı sembolleştirme önerilir.
