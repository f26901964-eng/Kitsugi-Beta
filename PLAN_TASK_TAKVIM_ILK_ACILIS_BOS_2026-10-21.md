# PLAN / TASK — Haftalık Yayın Takvimi "İlk Açılışta Boş" Düzeltmesi

Tarih: 2026-10-21
Dal: `arena/a2044ec1-kitsugi-beta`
Sürüm: v2.4.320
İlgili rapor: `KOK_NEDEN_RAPORU_TAKVIM_ILK_ACILIS_BOS_2026-10-21.md`

## İstek ve kapsam

Kullanıcı bildirimi: yayın takvimi (Keşfet → "Bu Hafta Yayında") hangi kaynağa özgü olursa olsun
(AniList, MAL, TMDB, Kitsu, Shikimori, Simkl, Bangumi) ve "Tümü" modunda dahi **ilk açılışta boş**
geliyor; geri çıkıp 2.-3. kez girildiğinde veri görünüyor. İstenen: mantık hatasını bulup düzeltmek.

Kapsam dışı: Takvim ekranının görsel/tasarım değişiklikleri, yeni özellik eklenmesi, bildirim
akışlarının (`AiringNotificationWorker`, `NotificationDiagnostics`) davranışının değiştirilmesi.

## Tespit özeti

| # | Kök neden | Dosya | Etki |
| --- | --- | --- | --- |
| 1 | `remember(viewModel.weekSchedule, …)` anahtarı: `SnapshotStateMap` **yerinde** mutasyona uğradığı için anahtar "değişmedi" sayılıyor; ilk açılışta hesaplanan boş harita önbellekte kalıyor | `KitsugiAiringCalendarScreen.kt` | **Asıl şikâyet** — tüm kaynaklarda ilk açılış boş |
| 2 | AniList isteği `null` dönünce (429 cooldown / ağ) `?: break` ile boş liste → boş harita, hata yok | `KitsugiAiringCalendarClient.kt` | "Bugün yayın yok" yanlış mesajı |
| 3 | `parseTmdbList` tüm istisnaları yutuyor | `KitsugiAiringCalendarClient.kt` | TMDB/API anahtarı hatası sessiz boş |
| 4 | Cooldown aktifken istek hiç gönderilmiyor (`return null`) | `KitsugiApiBase.kt` | Keşfet sonrası ilk açılış boş |
| 5 | Veri bir kez geldiyse süresiz kullanılıyor | `KitsugiAiringCalendarViewModel.kt` | Gün/hafta değişiminde bayat takvim |

## Tamamlanan görevler

- [x] Takvim ekranındaki türetilmiş haritayı `weekSchedule.toMap()` **içerik kopyası** anahtarıyla hesapla;
      `else` dalında da canlı map yerine kopyayı kullan (remember sonucu yalnızca anahtarlara bağlı kalsın).
- [x] `fetchAiringSchedule`'a `failOnFirstPageError` parametresi ekle: haftalık takvimde ilk sayfa
      alınamazsa `IOException`; sonraki sayfa hatalarında kısmi veri korunur.
- [x] HTTP 200 + `errors` gövdesi dönen AniList yanıtlarını da hata say (hız sınırı / servis hatası).
- [x] `parseTmdbList` `Boolean` dönsün; `fetchTmdbWeeklySchedule` 8 isteğin tamamı başarısızsa hata fırlatsın.
- [x] "Tümü" modunda kaynakları ayrı ayrı dene; biri hata verse de diğeriyle kısmi takvim göster,
      hata durumuna yalnızca ikisi de boş + hatalıysa düş.
- [x] `KitsugiApiBase`: 8 sn'ye kadar kısa AniList cooldown'larını bekleyip isteği gerçekten gönder
      (uzun cooldown'da fail-fast korunur).
- [x] ViewModel'e başarılı yükleme zamanı + 10 dakikalık TTL ekle; süre dolunca arka planda sessiz tazele
      (eski veri ekranda kalır, boş ekran parlaması olmaz).
- [x] Sürümü `2.4.320`'ye yükselt, `release_notes_v2.4.320.md` yaz, `RELEASE_NOTES.md` başına ekle.
- [x] Kök neden raporunu (`KOK_NEDEN_RAPORU_TAKVIM_ILK_ACILIS_BOS_2026-10-21.md`) yaz.

## Doğrulama

- [x] `git diff --check` temiz.
- [x] Değişen 4 Kotlin dosyası için parantez/blok dengesi denetimi (string/yorum farkındalıklı ayrıştırıcı) temiz.
- [ ] **Gradle/Android derleme ve cihaz testi: YAPILAMADI** — bu çalışma ortamında JDK/Gradle toolchain yok.
- [ ] Cihaz üzerinde görsel-etkileşim testi (aşağıdaki senaryo).

### Cihaz test senaryosu

1. Keşfet'i aç, birkaç kaynak sekmesine gir (AniList kotasını zorla).
2. "Bu Hafta Yayında" kartından takvimi **ilk** aç: gün rozetleri + başlıktaki toplam sayı dolu olmalı.
3. Kaynak değiştir (AniList → TMDB → Tümü): her birinde ilk açılış dolu olmalı.
4. Uçak modunda aç: "Bu gün için yayın bulunamadı." DEĞİL, "Takvim yüklenemedi + Yeniden Dene" görünmeli.
5. Uçak modunu kapat, "Yeniden Dene": veri gelmeli.
6. "Listemde" anahtarını aç/kapa ve ızgara/liste geçişini dene: filtre doğru uygulanmalı (boş listeye düşmemeli).

## Değişen dosyalar (tam içerik arşivde)

1. `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/KitsugiAiringCalendarScreen.kt`
2. `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/KitsugiAiringCalendarViewModel.kt`
3. `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiAiringCalendarClient.kt`
4. `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiApiBase.kt`
5. `app/build.gradle.kts` (sürüm 2.4.319 → 2.4.320)
6. `release_notes_v2.4.320.md` (yeni)
7. `RELEASE_NOTES.md` (başına v2.4.320 bölümü eklendi)
8. `KOK_NEDEN_RAPORU_TAKVIM_ILK_ACILIS_BOS_2026-10-21.md` (yeni)

Commit: `ecff6e9` — `fix(calendar): ilk açılışta boş takvim — remember anahtarı yerinde değişen SnapshotStateMap`

## Notlar / açık noktalar

- Kural: `SnapshotStateMap` / yerinde mutasyona uğrayan koleksiyonlar `remember` **anahtarı** olarak
  doğrudan kullanılmamalı; içerik kopyası (`toMap()`, `toList()`) kullanılmalı. Bu sınıf hata için
  kod tabanı taraması yapıldı; takvim dışında aynı desende kullanım bulunmadı.
- `fetchAiringWindow` (bildirimler/diagnostics) davranışı **değişmedi**: `failOnFirstPageError`
  varsayılan `false`.
