# Kitsugi Oynatıcı — Teslim Durumu

**Tarih:** 2026-10-18
**Dal:** `arena/13680ce5-kitsugi-beta`
**Temel commit:** `3bef456`
**Durum:** Planlanan düzeltmeler tamamlandı (statik doğrulama).

> Derleme/lint/cihaz testi bu ortamda yapılamadı: JDK, Android SDK ve Gradle yok.
> Ayrıntılı değişiklik listesi ve cihaz testi önerileri için
> `OYNATICI_MOTOR_RAPORU_2026-10-18.md` dosyasına bakın.

## Kapatılan Maddeler

1. `GainAudioProcessor` 8/24/32-bit PCM desteği.
2. Ölü `PlayerSourceController` / `PlayerTrackController` kaldırıldı (çift kaynak değiştirme yok).
3. "MPV" tercihi doğrulandı: dahili libmpv motoru; harici uygulama `EXTERNAL`.
4. Media3 `prepare()` kullanıcı oynatma hızını koruyor.
5. Media3 `release()` ASS handler kaydını temizliyor.
6. Hata ekranı "Tekrar dene" kaynağı yeniden çözümlüyor.
7. MPV `play()/pause()` dosya yüklenmeden READY yaymıyor.
8. MPV ekran görüntüsünde asenkron dosya yazımı bekleniyor.
9. Still-watching diyaloğu onayında oynatma devam ediyor.
10. Dokunma/çift dokunma hareketsizlik sayacını sıfırlıyor.
11. Gömülü altyazılar ekran görüntüsü "altyazı dahil" anahtarında görünüyor.
12. EXTERNAL tercihinde Activity kapanmadan dahili motor hazırlanmıyor.
13. Ses gecikmesi işlemcisinde kare hizalı atlama ve tampon boyutu düzeltildi.
14. `retryCurrentSource` / `onRetry` prepare hataları yakalanıyor.

## Paket İçeriği

- Değişen 21 kod dosyası + 2 silinen dosya (`SILINEN_DOSYALAR.txt` içinde)
- `PLAN_TASK_IZLE_STREAM_FIX_2026-10-17.md`
- `OYNATICI_MOTOR_RAPORU_2026-10-18.md`
- Bu durum dosyası

## Doğrulama

- `git diff --check`: temiz
- Değişen Kotlin dosyalarında süslü parantez/parantez dengesi: temiz
- Derleme: yapılmadı
