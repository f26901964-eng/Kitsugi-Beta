# CS Eklenti Tanı Raporu — 2026-10-11 (v2.4.249)

**Şikayet:** Video/anime/dizi/film verisi gelmiyor (çok sayıda eklenti).
**Kapsam:** Dağıtılan 166 eklentinin tamamı + Kitsugi-Beta uygulaması (v2.4.248 → v2.4.249).
**Kaynaklar:** Kullanıcı log raporu (Drive, 9 parça) · `Kitsugi-Plugins` deposu (main + builds) · yukarı-akım kaynakları (Kekik/nik/Kraptor/Cs-Karma/Hexated master) · servis edilen `.cs3` DEX dizgileri · Python kanıt programı.

---

## 1. Yönetici Özeti

| Kök neden kümesi | Taraf | Eklenti etkisi | Durum |
|---|---|---|---|
| Eşleştirici 1.00 reddi (`CsTitleMatcher`) | **UYGULAMA** | DiziMom, DiziYou, Dizilla, Movix + aynı hattaki TR eklentileri | ✅ v2.4.249'da düzeltildi |
| R8 Gson ad değiştirme (`NoClassDefFoundError: Gson`) | **UYGULAMA** | Torrential (+ Gson bağlayan her eklenti) | ✅ v2.4.249'da düzeltildi |
| Bayat/ölü domain haritası (7 girdi) | **UYGULAMA** | RecTV, SetFilmIzle, SelcukFlix, DiziPal, SinemaCX, DiziLife | ✅ v2.4.249'da düzeltildi |
| Site API'si değişmiş, eklenti kaynağı eski | **EKLENTİ** | DiziPal, SelcukFlix, RoketDizi | ⏳ Kaynak senkronu + yeniden derleme gerekli |
| Domain taşınmış, eklenti kaynağı eski | **EKLENTİ** | HDFilmCehennemi2 (`.biz`→`.site`) | ⏳ Tek satır + yeniden derleme |
| Yukarı-akım kaynağı ölmüş | **EKLENTİ** | NeonSpor (DMCA), XPrime (API anahtarı) | ⏳ Kaynak değişimi gerekli |
| İskelet/örnek eklenti | **EKLENTİ** | FilmPalast (`com.example`) | ⏳ Düşür veya tamamla |
| Cihazda eski+yeni DEX karışımı (stale dex) | **KURULUM** | Latanime (`EmbedMediaScanner`), 418 `KSerializer` | ⏳ **Temiz kurulum** (kaldır→kur) |
| Upstream ile birebir aynı ama site ölü olabilir | **DOĞRULA-veya-DÜŞÜR** | DiziGom, Filmİzlesene, FullHDFilmİzlede | ⏳ Canlı doğrulama gerekli |

**Dağıtım zinciri bulgusu (önemli):** Uygulamanın kurduğu binary'ler `builds` dalındaki `.cs3` dosyalarıdır ve `builds/plugins.json` ile tutarlıdır (served==installed, kanıtlandı). `main` dalındaki `prebuilt_plugins.json` **yetim metaveridir** — içindeki yüksek sürümler farklı yazarlara aittir (örn. SelcukFlix meta v49 = Kraptor soyu, dağıtılan v6 = nik soyu) ve **sürüm karşılaştırması yapılamaz**. Dağıtılan binary'ler bu repo kaynağından derlenir; eklenti düzeltmesi = repo kaynağını güncelle → push et → Derleyici yeniden derler.

---

## 2. Yöntem ve Kanıt Zinciri

1. `builds` dalı klonlandı; 166 `prebuilt/*.cs3` içindeki `manifest.json` okundu (isim + sürüm + `pluginClassName` soy bilgisi).
2. Her `.cs3`'ün DEX dizgileri çıkarıldı; gömülü endpoint'ler (`dizipal1214.com`, `selcukflix.net`, `superfilmgeldi13.art` vb.) okundu.
3. Repo kaynakları (`DiziPal.kt`, `SelcukFlix`, `RoketDizi`…) yukarı-akım master kaynaklarıyla (Kekik, nik-cloudstream) satır satır karşılaştırıldı.
4. Eşleştirici hatası Python ile birebir yeniden üretildi: eski kural 1.00 skoru reddediyor, korumalı kural kabul ediyor, Naruto/Shippuden koruması çalışmaya devam ediyor.
5. Uygulama kodu (`CsStreamRunner`, `CsTitleMatcher`, `proguard-rules.pro`, `StreamViewModel`) okundu; timeout, domain haritası ve hata yutma davranışı doğrulandı.

---

## 3. UYGULAMA TARAFI — v2.4.249'da Düzeltilenler

### 3.1. "1.00 EŞLEŞME YOK" — `CsTitleMatcher` katı takma-ad filtresi

- **Belirti:** DiziMom/DiziYou/Dizilla/Movix loglarında `1.00 EŞLEŞME YOK` — eklenti sonuç döndürüyor, uygulama tamamını eleyip "sonuç yok" diyor.
- **Kök neden:** `CsTitleMatcher.kt` içindeki Naruto/Shippuden koruma filtresi, çok dilli takma-ad kümeleri varken (`SUPERNATURAL スーパーナチュラル`) devreye giriyor ve aday Latin başlığa **birebir (%100) uysa bile** reddediyordu. Koşul: `kapsama < 0.75 && katıBenzerlik < 0.90 && unicodeBenzerlik < 0.90` → `supernatural` adayı kapsam 0.50 + katı 0.5455 + unicode 0.0 ile reddediliyor, `titleSimilarity=1.0` hiç kontrol edilmiyordu.
- **Kanıt (Python birebir yeniden üretim):**
  - Eski kural: `supernatural` adayı, skor **1.00** → **REDDEDİLDİ** ❌
  - Korumalı kural (`titleSimilarity < 0.90` koşulu eklendi): aynı aday → **KABUL** ✅
  - Naruto vs Naruto Shippuden (0.375): hâlâ **REDDEDİLDİ** ✅ (koruma sağlam)
- **Düzeltme:** Bilinen başlıklardan herhangi birine ≥0.90 benzeyen aday artık bu filtreden geçiyor. Dosya: `app/.../data/cloudstream/CsTitleMatcher.kt` (1 satır + açıklama).

### 3.2. `NoClassDefFoundError: Lcom/google/gson/Gson` — R8 koruması eksikliği

- **Belirti:** Torrential (`com.byayzen.TorrentcikPlugin` v6) açılışta patlıyor: Gson sınıfı bulunamadı.
- **Kök neden:** `proguard-rules.pro` içinde kotlinx.serialization tam keep kapsamında, **Gson değil**. R8 Gson sınıf adlarını değiştiriyor; eklenti DEX'i orijinal ada bakıyor ve patlıyor. (Ulaşılabilir kod silinmez ama ADI DEĞİŞİR — eklenti bunu kaldıramaz.)
- **Düzeltme:** `com.google.gson.**` tam keep kapsamına alındı. Dosya: `app/proguard-rules.pro`.

### 3.3. Domain haritası — 7 bayat/ölü girdi (`CsStreamRunner`)

Uygulama haritayı **tek geçiş** uygular (bulduğu ilk eşleşmede durur, zincirleme yapmaz). Bu yüzden ara basamaklar ölü bayi demektir:

| # | Girdi | Eski (sorunlu) | Yeni (2026-10-11) | Nedeni |
|---|---|---|---|---|
| 1 | RecTV | `m.prectv72.lol` (bayat) | `a.psrectv80.xyz` (Kekik-master) | 72 öldü, 80 aktif |
| 2 | SetFilmIzle | `www.setfilmizle.uk` (**UnknownHost — ölü**) | `www.setfilmizle.ltd` | Uygulama eklentiyi kendisi ölü domaine yolluyordu |
| 3 | SelcukFlix | `selcukflix.co` (bayat ara basamak) | `selcukflix.com` (doğrudan) | Tek geçiş kuralı: doğrudan güncel hedef şart |
| 4 | DiziPal | `dizipal1214.com` **haritada yoktu** | `dizipal3008.com` | Dağıtılan v33 bu domain'i kullanıyor; park sayfası HTML'i → `'<'` JSON hatası |
| 5 | DiziPal | `dizipal.bid` **iki kez** tanımlı (son yazan 1565 kazanıyordu) | yinelenen silindi | Kotlin `mapOf`'ta son girdi gölgeliyordu |
| 6 | SinemaCX | `sinema.gg` (bayat ara basamak) | `sinemacc.com` (doğrudan) | Tek geçiş kuralı |
| 7 | DiziLife | `dizi73.life` (bayat ara basamak) | `dizi75.life` (doğrudan) | Tek geçiş kuralı |

Ek not: Cloudflare korumalı host'lara arama aşamasında uzatılmış timeout (`CF_STAGE_TIMEOUT_MS`) uygulanıyor; CF hattı ayrıca incelendi, arama timeout'larının sorumlusu değil.

---

## 4. EKLENTİ TARAFI — Kaynak Senkronu Gerekenler

Soy bilgisi dağıtılan binary'nin `pluginClassName` alanından okunmuştur (olgusal).

### 4.1. Site API'si değişmiş (kaynak portu gerekli)

| Eklenti | Served | Soy | Belirti | Kök neden (kanıtlı) | Çözüm |
|---|---|---|---|---|---|
| DiziPal | v33 | keyiflerolsun | Arama boş / `'<'` JSON hatası | Repo+served araması ölü `/api/search-autocomplete` kullanıyor; Kekik-master token'lı POST `/bg/searchcontent` kullanıyor. Served endpoint `dizipal1214.com` park sayfası (uygulama haritası eklendi, kısmi iyileşme) | Kekik-master arama/load mantığını porta et + `dizipal3008.com` + yeniden derle |
| SelcukFlix | v6 | nikyokki | `izinsiz` yanıtında çökme, 0 sonuç | Served v6 JSON-dışı `izinsiz` yanıtında patlıyor; master `try/catch` + HTML `/arama` yedeğiyle hayatta kalıyor. Host `selcukflix.net` (uygulama `.com`'a yönlendiriyor) | Master mantığını porta et + yeniden derle |
| RoketDizi | v15 | nikyokki | loadLinks boş | **Repo kaynağı ile nik-master satır satır AYNI** — ikisi de site/API yanıt değişiminden etkileniyor. Host `roketdizi.live` iki tarafta da aynı | Canlı yanıt yakalanıp ayrıştırıcı uyarlanmalı (eklenti yazarı işi) |

### 4.2. Domain taşınmış (tek satır + derleme)

| Eklenti | Served | Soy | Belirti | Kök neden (kanıtlı) | Çözüm |
|---|---|---|---|---|---|
| HDFilmCehennemi2 | v7 | nikyokki | HTML/JSON uyumsuzluğu | Served/repo `.biz` park sayfası döndürüyor; nik-master `.site`'a taşınmış | `mainUrl` → `.site` + yeniden derle |

### 4.3. Yukarı-akım kaynağı ölmüş (kaynak değişimi gerekli)

| Eklenti | Served | Soy | Belirti | Kök neden (kanıtlı) | Çözüm |
|---|---|---|---|---|---|
| NeonSpor | v12 | sarapcanagii | Geçersiz M3U başlığı | M3U kaynağı olan GitHub deposu **DMCA ile kapatılmış** (API 451/Block) | Yeni kaynak bulunmadan **ölü** |
| XPrime | v6 | nikyokki | API'siz veri yok | Gömülü TMDB anahtarı (`84259…6123`); kota/iptal riski + manifest eksik | Anahtar değişimi + manifest + yeniden derle |

### 4.4. İskelet eklenti

| Eklenti | Served | Soy | Belirti | Kök neden | Çözüm |
|---|---|---|---|---|---|
| FilmPalast | v1 | **com.example** (örnek şablon!) | — | `com.example.FilmpalastPlugin` = tamamlanmamış şablon | Düşür veya tamamla |

### 4.5. Upstream ile birebir aynı — doğrula-veya-düşür

Repo/served `mainUrl` yukarı-akımla aynı; site öldüyse eklenti de ölüdür. Temiz kurulum + canlı test sonrası hâlâ ölüyseler listeden düşürülmeliler:

| Eklenti | Served | Soy | Upstream durumu |
|---|---|---|---|
| DiziGom | v9 | nikyokki | nik-master ile aynı (`101`) |
| Filmİzlesene | v3 | nikyokki | nik-master ile aynı (`.plus`) |
| FullHDFilmİzlede | v5 | nikyokki | nik-master ile aynı (`.org`) |

### 4.6. Uygulama düzeltmesiyle çözülenler (referans)

DiziMom v54, DiziYou v25, Dizilla v92, Movix v28 (1.00 reddi → §3.1); Torrential v6 (Gson → §3.2); RecTV v98, SetFilmIzle v25, SinemaCX v19 (domain → §3.3). **Not:** `prebuilt_plugins.json` içindeki yüksek sürümler (RecTV v109, Dizilla v110, DiziPal v102…) **Kraptor soyuna ait** olup dağıtılan Kekik/nik soyuyla karşılaştırılamaz — "eski binary" değil, **farklı yazarın farklı eklentisi**.

### 4.7. Bu turda sınıflandırılamayanlar

Yukarıda adı geçmeyen eklentiler için log raporunda ayırt edici belirti yoktu veya kaynak karşılaştırması gri alanda kaldı (örn. SuperFilmGeldi: `superfilmgeldi13.art` numaralı ayna — güncel aynanın kullanıcıda doğrulanması gerekli; SezonlukDizi v9, CizgiMax v9, TurkAnime v14, DiziKorea v30, DiziBox v23, FilmModu v19, KultFilmler v15, TRasyalog v53, TvDiziler v5, WebDramaTurkey v2, YabanciDizi v8, AnimeciX v21, AsyaAnimeleri v1, AsyaWatch v6, BelgeselX v13, FullHDFilmizlesene v28, HDFilmCehennemi v48, InatBox v30, JetFilmizle v42, FilmMakinesi v58 — kaynak senkronu adayları). **Temiz kurulum (v2.4.249) sonrası hâlâ sorunlu olanların güncel logları** ile ikinci tur sınıflandırma yapılacak. Tam stack içeren `.md` raporu paylaşılırsa gri alanlar kapanır.

---

## 5. Dağıtım Zinciri Bulguları (Eklenti Deposu)

1. **Tüketilen dosya:** `builds/plugins.json` (166 girdi, sürüm+URL+hash tutarlı) → uygulama buradan listeler ve indirir. Örn. SelcukFlix v6 → `.../builds/prebuilt/SelcukFlix.cs3`.
2. **`prebuilt_plugins.json` yetimdir:** 105 girdi, URL'ler yukarı-akım depoları gösteriyor, sürümler farklı yazar soylarına ait. Hiçbir şey bunu tüketmiyor; "güncel sürüm" gibi okunması yanlış kararlara yol açar.
3. **Derleyici:** `push → main`'de **değişen dizinleri** derleyip `builds`'a yazar; `workflow_dispatch` ile eklenti listesi elle verilebilir. Eklenti düzeltme akışı: kaynak güncelle → push → derlendiğini doğrula → uygulamadan eklentiyi güncelle.
4. **Kontrol:** `Kontrol.yml` + `KONTROL.py` domain değişimlerinde PR açar; binary dağıtmaz.
5. **Birleştirme:** `merge_plugins.py` son-yazan-kazanır; sorun öncelik değil, prebuilt-only değişikliklerde CI erken çıkışı (bilinen).
6. **Kraptor alan listesi:** yukarı-akım `domainListesi` 404; uygulama gömülü listeyi sunuyor; `eklenti_domainleri.txt` yalnızca belge.
7. **Soy dağılımı (166 binary):** byayzen 43 · keyiflerolsun 42 · kraptor 25 · nikyokki 21 · lagradost 5 · kerimmkirac 5 · hexated 2 · diğer (tekil) 23.

---

## 6. Kullanıcı Eylem Planı

1. **v2.4.249 APK'yı TEMİZ kur** (uygulamayı kaldır → yeniden kur → eklentileri yeniden yükle). Üzerine kurulum stale-dex'i temizlemez.
2. **Eklenti deposunda kaynak senkronu:** DiziPal (Kekik-master portu), SelcukFlix (master portu), HDFilmCehennemi2 (`.site`), RoketDizi (yanıt uyarlaması), XPrime (anahtar+manifest), NeonSpor (yeni kaynak veya düşür), FilmPalast (düşür).
3. **Doğrula-veya-düşür:** DiziGom, Filmİzlesene, FullHDFilmİzlede + §4.7 listesi — temiz kurulum sonrası canlı test.
4. **Tam stack'li `.md` raporunu paylaş** — ikinci tur sınıflandırma için.

---

## Ek A. Served↔Meta Fark Tablosu (106 girdi, olgusal)

`served` = `builds` dalında dağıtılan binary sürümü (uygulamanın kurduğu).
`meta` = `main` dalı `prebuilt_plugins.json` sürümü (YETİM — farklı yazar soyu olabilir, karşılaştırma için değil izleme içindir).

```text
4KFilmIzlesene: served=v6 meta=v--meta-yok--
AnimeSiteleri: served=v48 meta=v--meta-yok--
AnimeWorld: served=v5 meta=v--meta-yok--
AnimeciX: served=v21 meta=v87
AsyaAnimeleri: served=v1 meta=v24
AsyaWatch: served=v6 meta=v41
BasketballReplays: served=v4 meta=v--meta-yok--
BelgeselProvider: served=v60 meta=v--meta-yok--
BelgeselX: served=v13 meta=v41
CinemaCity: served=v7 meta=v--meta-yok--
CizgiMax: served=v9 meta=v36
CizgiVeDizi: served=v3 meta=v--meta-yok--
Cricify: served=v1 meta=v?
DDizi: served=v1 meta=v--meta-yok--
DMax: served=v2 meta=v--meta-yok--
DailymotionProvider: served=v4 meta=v--meta-yok--
DiziBox: served=v23 meta=v37
DiziFilmSiteleri: served=v125 meta=v--meta-yok--
DiziGom: served=v9 meta=v--meta-yok--
DiziKorea: served=v30 meta=v35
DiziMag: served=v18 meta=v--meta-yok--
DiziMom: served=v54 meta=v61
DiziPal: served=v33 meta=v102
DiziYou: served=v25 meta=v49
Dizilla: served=v92 meta=v110
DocumentaryArea: served=v2 meta=v--meta-yok--
DoramasLatinoX: served=v7 meta=v--meta-yok--
Dubbindo: served=v2 meta=v--meta-yok--
Esheaq: served=v18 meta=v--meta-yok--
Exxen: served=v3 meta=v?
F1FullRaces: served=v10 meta=v--meta-yok--
FilmIzleIlk: served=v6 meta=v--meta-yok--
FilmKovasi: served=v3 meta=v--meta-yok--
FilmMakinesi: served=v58 meta=v70
FilmModu: served=v19 meta=v41
Filmatek: served=v4 meta=v--meta-yok--
Filmmirasım: served=v4 meta=v--meta-yok--
Filmİzlesene: served=v3 meta=v--meta-yok--
Flixlatam: served=v8 meta=v--meta-yok--
FootReplays: served=v1 meta=v--meta-yok--
Footballia: served=v9 meta=v--meta-yok--
Full4kizle: served=v20 meta=v--meta-yok--
FullHDFilmizlesene: served=v28 meta=v71
FullHDFilmİzlede: served=v5 meta=v--meta-yok--
FullRaces: served=v4 meta=v--meta-yok--
Gain: served=v1 meta=v?
Gnulahd: served=v7 meta=v--meta-yok--
GolgeTV: served=v14 meta=v?
HDFilmCehennemi: served=v48 meta=v97
HDFilmCehennemi2: served=v7 meta=v--meta-yok--
HDFilmIzle: served=v7 meta=v--meta-yok--
HDFilmSitesi: served=v18 meta=v--meta-yok--
Henaojara: served=v3 meta=v--meta-yok--
InatBox: served=v30 meta=v57
InternetArchiveProvider: served=v1 meta=v--meta-yok--
InvidiousProvider: served=v9 meta=v--meta-yok--
Iwatchtheoffice: served=v3 meta=v--meta-yok--
JPFilms: served=v6 meta=v--meta-yok--
JetFilmizle: served=v42 meta=v62
KissKH: served=v4 meta=v--meta-yok--
Krmzy: served=v2 meta=v--meta-yok--
KultFilmler: served=v15 meta=v31
Latanime: served=v8 meta=v--meta-yok--
LayarKaca: served=v13 meta=v--meta-yok--
Movix: served=v28 meta=v--meta-yok--
Nekokun: served=v2 meta=v--meta-yok--
NeonSpor: served=v12 meta=v--meta-yok--
OK: served=v4 meta=v--meta-yok--
RareFilmm: served=v3 meta=v20
RecTV: served=v98 meta=v109
RoketDizi: served=v15 meta=v--meta-yok--
SSportPlus: served=v8 meta=v?
SelcukFlix: served=v6 meta=v49
SetFilmIzle: served=v25 meta=v41
SezonlukDizi: served=v9 meta=v40
SinemaCX: served=v19 meta=v35
Sinewix: served=v2 meta=v--meta-yok--
Sokuja: served=v3 meta=v--meta-yok--
Streamed: served=v27 meta=v--meta-yok--
Subsplease: served=v4 meta=v--meta-yok--
Supercartoons: served=v2 meta=v--meta-yok--
TLC: served=v1 meta=v--meta-yok--
TLCtr: served=v19 meta=v--meta-yok--
TRasyalog: served=v53 meta=v65
TVGarden: served=v11 meta=v--meta-yok--
Tabii: served=v9 meta=v?
Tafdi: served=v3 meta=v--meta-yok--
TelevizyonEklentileri: served=v41 meta=v--meta-yok--
TokyBook: served=v3 meta=v--meta-yok--
TopluDiziler: served=v135 meta=v--meta-yok--
TurkAnime: served=v14 meta=v34
TvDiziler: served=v5 meta=v18
TwitchProvider: served=v2 meta=v--meta-yok--
WFilmİzle: served=v7 meta=v--meta-yok--
WatchWrestling: served=v17 meta=v--meta-yok--
Wcoflix: served=v9 meta=v--meta-yok--
WebDramaTurkey: served=v2 meta=v39
WebteIzle: served=v59 meta=v53 (served DAHA YENİ — ters fark)
XPrime: served=v6 meta=v--meta-yok--
YabanciDizi: served=v8 meta=v18
Yablom: served=v4 meta=v--meta-yok--
YoTurkish: served=v4 meta=v--meta-yok--
YoutubeProvider: served=v1 meta=v--meta-yok--
powerDizi: served=v3 meta=v--meta-yok--
powerSinema: served=v3 meta=v--meta-yok--
vavooSpor: served=v2 meta=v--meta-yok--
```

Meta'da olup dağıtılmayanlar (isim varyantları — muhtemel çift kayıt): AsyaMinik v16 · CizgiveDizi v50 (served: CizgiVeDizi) · Ddizi v22 (served: DDizi) · HDFilmizle v25 · Hqporner v4 · IfsaLog v3 · Kanal7 v? · SineWix v33 (served: Sinewix) · YesilCamTv v9.

Not: `meta=v?` girdilerinde (Cricify, Exxen, Gain, GolgeTV, SSportPlus, Tabii) meta bozuk/eksik yazılmış — sürüm anahtarı yok.

## Ek B. Bu Sürümde Değişen Dosyalar (uygulama deposu)

- `app/src/main/java/com/kitsugi/animelist/data/cloudstream/CsTitleMatcher.kt` — §3.1 koruması
- `app/proguard-rules.pro` — §3.2 Gson keep
- `app/src/main/java/com/kitsugi/animelist/data/cloudstream/CsStreamRunner.kt` — §3.3 domain haritası (7 girdi)
- `app/build.gradle.kts` — sürüm 2.4.249
- `release_notes_v2.4.249.md` — sürüm notları (bu raporun özeti)
