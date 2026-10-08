# Kitsugi — "Hiçbir Eklentiden Veri Gelmiyor" Teşhis & Çözüm Raporu

**Tarih:** 2026-10-09
**Dal:** `arena/c3061910-kitsugi-beta` (v2.4.210 adayı)
**Kapsam:** Türkçe CloudStream (.cs3) eklentilerinin stream ekranında sıfır sonuç
döndürmesi ("Bu anime için akış bulunamadı"), Türkçe sitelerin video kaynaklarının
çekilmesi/oynatılması boru hattı.

---

## 1. Şikâyet

> "Önceden iyi kötü veri gelirdi, artık BİR TANE bile eklentiden veri gelmiyor.
> 4KFilmIzlesene, AltiYuzAltmisAltiFilmIzle, DiziFilmSiteleri, Turkdizileri… hepsi boş."

Ekran kanıtı (uploads/image-1.png): *Spider-Man: Brand New Day* (tt22084616) detayında
⚡ 4KFilmIzlesene ve ⚡ AltiYuzAltmisAltiFilmIzle kartlarında kırmızı nokta +
"Bu anime için akış bulunamadı".

## 2. Kök Nedenler (kod kanıtlarıyla)

### KN-1 — `KNOWN_BROKEN_DOMAINS` SESSİZ atlama kapısı (KRİTİK)
`CsStreamRunner.kt` içinde `getStreams()`, `safeSearch()` ve `safeLoad()`;
eklentinin `mainUrl`'si bu listedeki bir domaini içeriyorsa **hiç denemeden**
`emptyList()` dönüyordu. UI bunu "Bu anime için akış bulunamadı" olarak gösteriyordu.

- `4kfilmizlesene.nl` listedeydi — ama bu, 4KFilmIzlesene eklentisinin **KENDİ güncel
  domain'i** (Kitsugi-Plugins kaynak + `builds` dalındaki derli .cs3 ikisi de
  `https://www.4kfilmizlesene.nl` içerir; dex string taramasıyla doğrulandı).
  → Eklenti daha hiç aranmadan sıfıra iniyordu. Ekran görüntüsündeki kırmızı kartın
  birebir açıklaması.
- `666filmizle.site` listedeydi — ama `domain_fixes.json` **aynı domain'i**
  AltiYuzAltmisAltiFilmIzle için zorla uyguluyordu (`domains` haritası).
  → Çelişki: uzak liste "bu domaini kullan", yerleşik liste "bu domain ölü, atla".
  Sonuç: eklenti her zaman sıfır. Ekran görüntüsündeki ikinci kırmızı kart.

### KN-2 — `domain_fixes.json` → `blocked` listesi (176 eklenti) SESSİZ atlama
Uygulama açılışta `main` dalından `domain_fixes.json` çekiyor; `blocked` dizisindeki
eklentiler `getStreams()`'de `return emptyList()` ile atlanıyordu. Liste +18
içeriğin yanında **normal Türkçe eklentileri** de içeriyordu: `4kfilmizlesene`,
`dizigom`, `dizimag`, `dizipaloriginal`, `setfilmizle`, `hdfilmsitesi`,
`hdfilmcehennemi2`, `filmkovasi`, `filmizleilk`, `fullhdfilm`, `xprime`, `inatbox`,
`canlitv`, `kisskh`, `layarkaca`, `netflixmirror`, `puhu`, `rarefilmm`, `sokuja`,
`subsplease`, `torrentfilm`, `yereldiziler` … → çalışan/çalışabilecek eklentiler
cihazda kalıcı olarak sıfıra iniyordu. (+18 filtreleme kodda zaten `ADULT_PLUGINS`
+ `showAdultContent` ile ayrıca yapılıyor; `blocked` listesi buna gerek olmadan
normal eklentileri de öldürüyordu.)

### KN-3 — UI gerçek hatayı gizliyordu
`StreamViewModel` akış boşsa hatayı null geçiyor, `updateAddonState` bunu otomatik
"Bu anime için akış bulunamadı" mesajına çeviriyordu. DNS ölümü, Cloudflare bloğu,
timeout gibi **gerçek sebep** `CsPluginStatusTracker`'da kayıtlı olsa bile ekranda
gözükmüyordu → kullanıcı "eklenti bozuk" sanıyordu.

## 3. Uygulanan Düzeltmeler (v2.4.210)

| Dosya | Değişiklik |
|---|---|
| `CsStreamRunner.kt` | `KNOWN_BROKEN_DOMAINS` üç kapıda (getStreams/safeSearch/safeLoad) artık **yalnızca uyarı log'u**; atlama YOK. Domain gerçekten ölüyse arama hızlı DNS/HTTP hatası verir, hata tracker'a düşer ve UI'da görünür. |
| `CsStreamRunner.kt` | `4kfilmizlesene.nl` ve `666filmizle.site` listeden çıkarıldı (eklentinin kendi / uzak listenin zorladığı güncel domain'lerdi). |
| `CsStreamRunner.kt` | `domain_fixes.json` `blocked` eşleşmesi artık **yalnızca uyarı + `recordSkip`**; eklenti yine de denenir (liste eskise bile cihazda veri akışı kesilmez). |
| `CsPluginStatusTracker.kt` | Yeni `recordSkip()` — atlama/engel sebebi UI'da gösterilebilir hale geldi. |
| `StreamViewModel.kt` | Akış boşsa kart artık tracker'daki **gerçek hatayı** gösterir (ör. "Unable to resolve host …", "Cloudflare doğrulaması gerekiyor", "domain_fixes blocked listesinde — yine de denendi"). |
| `domain_fixes.json` | `blocked` listesi **boşaltıldı** (176 girdi kaldırıldı). +18 filtre kodda `ADULT_PLUGINS` ile zaten ayrı. `domains` haritası (58 güncel domain) korunuyor. **Not:** uygulama bu dosyayı `main` dalından çektiği için bu değişiklik PR ile main'e girdikten sonra cihazlara yayılır; APK tarafındaki `recordSkip` değişikliği ise main'e girmeden önce bile eski listeye karşı koruma sağlar. |

Davranış özeti: **Eklenti artık asla sessizce atlanmaz.** Ya veri getirir ya da
gerçek hata sebebiyle kartta görünür. Ölü domain'ler arama başına ~1-2 sn'lik DNS
hatasıyla elenir; provider timeout bütçesi (40 sn) içinde kalır.

## 4. Türkçe Site → Video Kaynağı Uyarlam Haritası

Kitsugi-Plugins (açık kaynak depo) kaynakları taranarak derlenmiştir. Amaç: hangi
sitenin hangi video barındırıcısı/embed'i verdiği ve uygulamadaki hangi çözümleme
aşamasının bunu oynatılabilir akışa çevirdiği.

| Eklenti / Site grubu | Kaynakların verdiği embed/host | Çözümleme aşaması (CsStreamRunner.resolveEmbedUrl) |
|---|---|---|
| 4KFilmIzlesene (`div.video-content iframe`) | Eklentinin kendi extractor'ları + genel hostlar | Aşama 1 `loadExtractor` (kütüphane) → Aşama 3 EmbedMediaScanner |
| AltiYuzAltmisAltiFilmIzle (`button.player-sources__btn[data-frame]`) | iframe zinciri | Aşama 3 iframe takibi (≤2 katman) + scanner |
| DiziFilmSiteleri (FilmMakinesi, FullHDFilmizlesene, KultFilmler, SezonlukDizi, Sinewix, Dizipal, FilmizleCh, DiziSol, DiziFilmLife, FilmizleNow, SetFilmIzle, Diziroll) | CloseLoad, RapidVid, TRsTX, VidMoxy, Sobreatsesuyp, TurboImgz, TurkeyPlayer (eklenti-içi extractor'lar) + filemoon/vidmoly/dood/mixdrop/streamtape/mp4upload/voe/upstream/gdrive/mcloud | Eklenti extractor'ları → Aşama 1 kütüphane (FileMoon*, Dood*, OkRu, MailRu, Dailymotion AAR'da mevcut) → Aşama 2 app wrapper'ları (Mcloud/Mixdrop/Mp4Upload/StreamTape/Upstream/Voe/XStreamCDN/Gdrive/JWPlayer) → Aşama 3 scanner → Aşama 4 WebView sniffer |
| TurkAnime | AES şifreli embed (`/embed/#/url/<base64>`) | Özel AES-128 çözümü (CF_PROTECTED, 90 sn timeout) |
| TrAnimeci | WAF/__NEXT_DATA__ | WebView JS injection arama + CF timeout |
| ok.ru / odnoklassniki, my.mail.ru, vk, dailymotion, pixeldrain | Doğrudan host | Kütüphane extractor'ları (Odnoklassniki/OkRuHTTP/MailRu/CloudMailRu/Dailymotion AAR'da doğrulandı) |

Oynatıcı: Media3 (HLS/MP4/DASH, referer/UA header enjeksiyonlu) varsayılan; kodek
hatasında (4001-4005, 5001-5002) MPV motoruna geçer — önceki oturumda düzeltilen
davranış korunuyor. Altyazılar `subtitleCallback` → `SubtitleInput` (dil kodu
`detectLanguageCode`) ile oynatıcıya taşınır; ses rayları MPV'de tam, Media3'te
kapsayıcıya göre (HLS/MP4) seçilir.

## 5. Doğrulama

- `domain_fixes.json` → `python3 -m json` geçerli; `blocked=0`, `domains=58` korunuyor.
- `git diff` ile üç Kotlin dosyasındaki değişiklikler satır satır incelendi;
  sözdizimi bütünlüğü (blok/parantez dengesi) kontrol edildi.
- `builds/prebuilt/4KFilmIzlesene.cs3` (builds dalı) indirilip dex string'leri
  çıkarıldı: kaynak kodla aynı domain + sürüm 6 → derlemeler bayat DEĞİL; sorun
  kaynak/derleme farkı değil, app tarafı atlama kapılarıydı.
- **Yapılamayan:** bu ortamda JDK/Gradle/Android SDK yok → Kotlin **derlenemedi**;
  Türkçe sitelere ağ erişimi yok → canlı domain/selector doğrulaması yapılamadı.
  Derleme + cihaz testi kullanıcı tarafında gereklidir.

## 6. Takip Önerileri

1. Bu dalı `main`'e birleştir (PR) → `domain_fixes.json` temiz hali cihazlara yayılır.
2. APK'yı bu daldan derle → sessiz atlama kapıları kalkar, gerçek hata görünürlüğü gelir.
3. Site domain döndürdüğünde tek güncelleme noktası: `domain_fixes.json` `domains`
   haritası (GitHub Actions + AI zaten güncelliyor) — uygulama artık eski listeye
   takılmadan yeni domaini dener.
