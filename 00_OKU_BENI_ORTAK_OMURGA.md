# 00 — OKU BENİ (Ortak Kaynak & Altyazı Omurgası)

**Konu:** Kitsugi'de "kaynak kayırma" — aynı bölümün bir kaynaktan ayrıntılı gelip diğerinden gelmemesi;
altyazıların bir kaynakta çalışıp diğerinde kaybolması.

**Tarih:** 2026-10-25 · **Dal:** `arena/8eba864b-kitsugi-beta` · **Commit:** `36c1c1f`

## Bu klasörde ne var?

| Dosya | Ne işe yarar |
|---|---|
| `KOK_NEDEN_RAPORU_KAYNAK_ALTYAZI_ESITSIZLIGI_2026-10-25.md` | 11 kök neden, kod satır referanslarıyla. Sorunun "neden"i. |
| `COZUM_RAPORU_ORTAK_KAYNAK_ALTYAZI_OMURGASI_2026-10-25.md` | Ne değişti, önce/sonra davranış tabloları, mimari, riskler. |
| `PLAN_TASK_ORTAK_KAYNAK_ALTYAZI_OMURGASI_2026-10-25.md` | **Kaldığın yerden devam dosyası.** Faz 3 iş listesi + test kontrol listesi + sonraki oturum başlangıç komutları. |
| `ORTAK_KAYNAK_ALTYAZI_OMURGASI_2026-10-25.patch` | Tüm kod değişiklikleri (git format-patch). `git am` ile uygulanır. |
| `Kaynak Dosyalar (yeni)/` | Yeni eklenen 4 Kotlin dosyası (tek tek okumak için). |

## 3 satırda sonuç

1. **Ortak kimlik:** `MediaIdentity` + `MediaIdentityRegistry` — giriş noktası ne olursa olsun aynı MAL/AniList/TMDB/IMDb/Kitsu kimliği kullanılıyor.
2. **Ortak altyazı havuzu:** `SubtitleMemoryStore` + `SubtitleHub` — bir kez bulunan altyazı kalıcı saklanıyor, her kaynakta tekrar sunuluyor; kaynak değişince liste sıfırlanmıyor.
3. **Akış eşitliği:** doğrudan `cs3Url` artık "tohum"; diğer eklentiler de taranıyor (eski erken `return` kaldırıldı).

## Sıradaki ilk iş

Derleme + cihaz testi. `PLAN_TASK…md §6` kontrol listesi, `§7` ise derlemede ilk bakılacak 4 noktayı içerir.
Sonra Faz 3.1: altyazı eklentilerini "Altyazı Eklentileri" grubunda ayırmak.
