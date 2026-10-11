# Kitsugi-Beta v2.4.249 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🔬 0. Bu Sürüm Nedir? (CS Eklenti Tanı Raporu 2026-10-11)

Kullanıcı raporundaki "video/anime/dizi/film verisi gelmiyor" şikayetleri üzerine, **166 dağıtılan eklentinin tamamı** uygulama tarafıyla karşılaştırıldı: servis edilen `.cs3` binary'leri DEX seviyesinde okundu, repo kaynaklarıyla ve yukarı-akım (Kekik/nik/Kraptor) kaynaklarıyla satır satır karşılaştırıldı, eşleştirici hatası Python kanıtıyla yeniden üretildi. Sonuç: **3 uygulama tarafı kök neden bulundu ve bu sürümde düzeltildi**; kalan arızalar eklenti kaynağı/dağıtımı tarafında, tam listeyle raporlandı (`CS_EKLENTI_TANI_RAPORU_v2.4.249.md`).

> ⚠️ **TEMİZ KURULUM ŞART:** Bu sürüme **güncelleme değil, kaldırıp yeniden kurarak** geçin. Loglardaki `NoClassDefFoundError: EmbedMediaScanner` ve `KSerializer` arızaları, cihazda eski+yeni DEX karışımından (stale dex) kaynaklanıyor; üzerine kurulum bu karışımı temizlemiyor.

### 🎯 1. "1.00 EŞLEŞME YOK" Hatası Düzeltildi (`CsTitleMatcher`)

- **Kök neden:** Çok dilli takma-ad kümeleri (`SUPERNATURAL スーパーナチュラル` gibi AniList/TMDB eşanlamlıları) Naruto/Shippuden koruma filtresini tetikliyor, aday **birebir %100 eşleşse bile** (`Supernatural`, skor 1.00) reddediliyordu. DiziMom/DiziYou/Dizilla/Movix'te "1.00 EŞLEŞME YOK" satırlarının sebebi buydu — eklentiler sonuç döndürüyor, uygulama hepsini çöpe atıyordu.
- **Düzeltme:** Bilinen başlıklardan **herhangi birine ≥0.90 benzeyen aday artık reddedilemez**. Naruto (0.38) vs Shippuden koruması aynen duruyor — kanıt testiyle doğrulandı (reddedilen 1.00 kabul ediliyor, Naruto hâlâ reddediliyor).
- **Etkilenen eklentiler:** DiziMom, DiziYou, Dizilla, Movix ve aynı arama hattını kullanan tüm TR eklentileri.

### 📦 2. R8/Gson Koruması (`proguard-rules.pro`)

- **Kök neden:** R8, Gson sınıf adlarını değiştiriyordu; `Torrential` gibi Gson'u isimle bağlayan eklentiler `NoClassDefFoundError: Lcom/google/gson/Gson` ile patlıyordu. kotlinx.serialization koruması vardı, Gson koruması eksikti.
- **Düzeltme:** `com.google.gson.**` tam keep kapsamına alındı.

### 🗺️ 3. Domain Haritası — 7 Düzeltme (`CsStreamRunner`)

Uygulama tek geçiş uygular (zincirleme yok); haritadaki bayat/ölü hedefler eklentiyi doğrudan çukura yönlendiriyordu:

| Eski hedef (bayat/ölü) | Yeni hedef (2026-10-11 yukarı-akım) | Etkilenen eklenti |
|---|---|---|
| RecTV `m.prectv72.lol` | `a.psrectv80.xyz` (Kekik-master) | RecTV |
| SetFilmIzle `www.setfilmizle.uk` (**ölü**, UnknownHost) | `www.setfilmizle.ltd` | SetFilmIzle |
| SelcukFlix `selcukflix.co` (bayat ara basamak) | `selcukflix.com` (doğrudan) | SelcukFlix |
| DiziPal `dizipal1214.com` (**haritasızdı**, park sayfası) | `dizipal3008.com` | DiziPal |
| `dizipal.bid` yinelenen girdi (3008'i 1565 ile gölgeliyordu) | silindi | DiziPal |
| Sinema `sinema.gg` (bayat ara basamak) | `sinemacc.com` (doğrudan) | SinemaCX |
| DiziLife `dizi73.life` (bayat ara basamak) | `dizi75.life` (doğrudan) | DiziLife |

### 📋 4. Eklenti Tarafı Bulgular (özet — detay raporda)

Bu sürümle düzelmeyen arızalar eklenti kaynağındadır ve eklenti deposunda kaynak senkronu + yeniden derleme gerektirir: **DiziPal** (çağrısı değişmiş, `/api/search-autocomplete` ölü), **SelcukFlix** (`izinsiz` yanıtında çöküyor, `/arama` yedeği yok), **RoketDizi** (site yanıtı değişmiş), **HDFilmCehennemi2** (`.biz` park sayfası, `.site` aktif), **NeonSpor** (M3U kaynağı DMCA ile kapatılmış — ölü), **XPrime** (gömülü TMDB anahtarı), **FilmPalast** (`com.example` iskeleti). `prebuilt_plugins.json` içindeki yüksek sürüm numaraları **farklı yazarlara ait** (Kraptor v49 vs nik v6) — sürüm karşılaştırması yapılamaz; dağıtılan binary'ler bu repo kaynağından derleniyor.

## 🇬🇧 English

### 🔬 0. What Is This Release? (CS Plugin Diagnosis 2026-10-11)

Following the "no video/anime/series/movie data" reports, **all 166 served plugins** were compared against the app: served `.cs3` binaries were read at DEX level, diffed against repo sources and upstream (Kekik/nik/Kraptor) line by line, and the matcher bug was reproduced with a Python proof. Result: **3 app-side root causes found and fixed in this release**; remaining failures are plugin-source/distribution side, fully listed in the report (`CS_EKLENTI_TANI_RAPORU_v2.4.249.md`, Turkish).

> ⚠️ **CLEAN INSTALL REQUIRED:** Do **not** update over the old build — uninstall and reinstall. The `NoClassDefFoundError: EmbedMediaScanner` and `KSerializer` failures come from a stale old+new DEX mix on device; updating over it does not clear the mix.

### 🎯 1. "1.00 EŞLEŞME YOK" Fixed (`CsTitleMatcher`)

- **Root cause:** Multi-lingual alias sets (e.g. `SUPERNATURAL スーパーナチュラル`) triggered the Naruto/Shippuden franchise guard, rejecting candidates with a **perfect 1.00 score**. Plugins returned results; the app threw them all away (DiziMom/DiziYou/Dizilla/Movix).
- **Fix:** A candidate scoring ≥0.90 against ANY known title can no longer be rejected. The Naruto (0.38) vs Shippuden guard is intact — verified by proof test.

### 📦 2. R8/Gson Keep (`proguard-rules.pro`)

- R8 renamed Gson classes; plugins binding Gson by name (e.g. `Torrential`) crashed with `NoClassDefFoundError`. `com.google.gson.**` is now fully kept.

### 🗺️ 3. Domain Map — 7 Corrections (`CsStreamRunner`)

The app applies a single pass (no chaining); stale/dead targets steered plugins into the ditch: RecTV → `a.psrectv80.xyz`; SetFilmIzle → `www.setfilmizle.ltd` (old target was dead); SelcukFlix → `selcukflix.com` direct; DiziPal `dizipal1214.com` → `dizipal3008.com` (was unmapped); duplicate `dizipal.bid` entry removed; Sinema → `sinemacc.com` direct; DiziLife → `dizi75.life` direct.

### 📋 4. Plugin-Side Findings (summary)

Failures not fixed by this release live in plugin sources and need source sync + rebuild in the plugin repo: **DiziPal** (site API changed), **SelcukFlix** (crashes on `izinsiz`), **RoketDizi** (site response changed), **HDFilmCehennemi2** (`.biz` parked, `.site` live), **NeonSpor** (M3U source DMCA'd — dead), **XPrime** (hardcoded TMDB key), **FilmPalast** (`com.example` skeleton).
