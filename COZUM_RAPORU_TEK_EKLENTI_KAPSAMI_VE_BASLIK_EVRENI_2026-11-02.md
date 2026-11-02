# Kök neden + çözüm — akış aramasında "kaynağa göre değişen" başlık evreni ve eklentiye özel arama karışması

**Tarih:** 2026-11-02
**Sürüm:** v2.4.340 tabanı (sonraki derleme)
**Kapsam:** (1) Akış seçici ekranı (`KitsugiStreamActivity` / `StreamViewModel`), (2) tam ekran oynatıcının
sonraki bölüm akışı (`KitsugiPlayerViewModel`), (3) eklenti arama boru hattının başlık evreni
(`CsStreamRunner` + yeni `MalTitleAliasProvider`), (4) MAL detay istemcisi (`KitsugiMalDetailClient`).

> **DÜZELTME (2026-11-02, kullanıcı geri bildirimi):** Bu raporun ilk sürümü sorunu ters yönde
> yazmıştı ("AniList'ten gelince bozuk"). Kullanıcının bildirimi **tam tersi**: **MAL kaydından
> açıldığında** Anizm / TrAnimeIzle / AnimeciX gibi eklentiler veri veremiyor, **AniList
> kaydından açıldığında** veriyor. Aşağıdaki analiz bu yöne göre düzeltildi.

---

## 1. Şikâyet özeti

| # | Kullanıcı gözlemi | Beklenen davranış |
|---|---|---|
| A | **MAL** kaydından İzle / bölüm tıklanınca Anizm / TrAnimeIzle / AnimeciX "akış bulamadı" diyor; **AniList** kaydından aynı eklentiler buluyor. | Kaynak fark etmeksizin aynı eklenti seti aynı sonucu vermeli. |
| B | Bir eklentinin **kendi keşfet/arama** sayfasından "İzle" ya da bölüm satırına basıldığında sonuç yalnızca o eklentiden gelmeli; ama bazen diğer kaynaklarla karışıyor. | Eklentiye özel akış kapsamlı kalmalı; genişletme kullanıcı tercihi olmalı. |

---

## 2. Kök neden A — eklentiye giden arama sorgusu kaynağa göre daralıyordu

`CsStreamRunner.runGetStreams` arama varyantlarını **detay sayfasının gönderdiği başlık alanlarından**
üretiyor. Bu alanların doluluğu kaynağa göre değişiyordu:

| Alan | AniList detayı | MAL/Jikan detayı (öncesi) |
|---|---|---|
| `title` (ana başlık) | Romaji (JP olmayanlarda İngilizce) | Romaji |
| `titleEnglish` | ✔ `KitsugiAniListDetailClient` doldurur | ✔ doldurur |
| `titleRomaji` | ✔ **doldurur** | ✘ **boş bırakılıyordu** |
| `titleNative` | ✔ **doldurur** | ✘ **boş bırakılıyordu** (sadece `titleJapanese` yazılıyordu) |
| `synonyms` | ✔ | ✔ |
| TMDB `tmdbSeason` | AniList dış bağlantılarından çıkarılır | ✘ genellikle yok |

Yani **aynı anime** MAL kaydından açıldığında eklentiye **daha az sayıda başlık varyantı** gidiyordu
(Romaji + İngilizce), AniList kaydından açıldığında ise daha geniş bir küme (Romaji + İngilizce +
Yerel/Japonca + eş anlamlılar). Türk eklentileri sitelerinde çoğu zaman Romaji **veya Türkçe** başlık
taşıdığı için MAL tarafındaki dar küme "bulunamadı" ile sonuçlanıyordu.

Ayrıca TMDB `tr-TR` başlığı (`TmdbTitleLocalizer`) yalnızca TMDB kimliği çözüldüğünde eklenebiliyor;
MAL kaydında TMDB kimliği çoğu zaman detay sayfasından gelmiyor — yalnızca ARM üzerinden çözülüyor.

### Çözüm

1. **`KitsugiMalDetailClient` (her iki yol: Jikan + resmî MAL v2)** artık `titleRomaji` ve
   `titleNative` alanlarını da dolduruyor. Böylece MAL kaydından açılan akış ekranı, AniList kaydıyla
   **aynı genişlikte** alternatif başlık kümesi gönderiyor.
2. **`MalTitleAliasProvider` (yeni):** MAL kimliği varsa Jikan `anime/{id}` yanıtındaki **tüm** başlık
   diğer adları (Default / English / Japanese / Synonym / Turkish …) okunup arama varyantlarına eklenir.
   Bu, kaynaktan bağımsız olarak aynı başlık evrenini garanti eder — MAL tarafındaki eksikliği de,
   ileride başka bir kaynakta oluşabilecek aynı eksikliği de kapatır.
   - Oturum-önbellekli (`BoundedCache`, 400 kayıt), eşzamanlı çağrılar `Mutex` ile tek isteğe indirilir
     (164 eklenti tek Jikan isteğini paylaşır), boş/hatalı sonuç negatif önbelleğe yazılır.
   - 4 sn tavan: zenginleştirme bir "bonus"; Jikan yavaşsa eklenti araması bekletilmez ve zaman aşımı
     önbelleğe yazılmaz.
   - `JikanGateway` üzerinden gidilir (mevcut hız sınırı / devre kesici / yedek host korunur).
   - En fazla 8 diğer ad, normalize başlığa göre tekilleştirilir.

### Elenen hipotezler (yanlış çıktı, kayda geçsin)

- **ARM `source` adı:** `KitsugiIdResolver` MAL için `source=myanimelist` kullanıyor. ARM (arm-server)
  API'sinde MAL kaynağının adı gerçekten `myanimelist` — bu doğru, sorun burada değil.
- **MAL kimliğinin offset olarak geçmesi:** `StreamMediaIds` MAL/Jikan için `malId` alanına gerçek MAL
  kimliğini koyuyor; AniList/Kitsu offset'leri ayrı dallarda. Burada da sorun yok.

---

## 3. Kök neden B — doğrudan URL başarısız olunca sessizce TÜM kaynaklara düşülüyordu

Eski `StreamViewModel.fetchStreams`:

```kotlin
if (resolved.isNotEmpty()) { ...; return }
...
_addonStates.value = emptyList()   // ← bütün liste silinir
// ardından Stremio addon'ları + TÜM CloudStream eklentileri aranır
```

Eklentinin kendi bölüm URL'si bir kez boş döndüğünde (site yapısı değişti, bölüm eşleşmedi, geçici ağ
hatası) ekran **tüm kaynakların ortak aramasına** düşüyordu. Kullanıcı "sadece bu eklenti" beklerken
listeye başka kaynaklar karışıyor, bazen çalışıp bazen çalışmadığı için davranış "bozuk" görünüyordu.
Aynı davranış tam ekran oynatıcının sonraki bölüm akışında (`KitsugiPlayerViewModel.fetchStreamsForEpisode`)
da vardı.

### Çözüm 1 — akış seçicide gerçek "tek eklenti" modu

- `StreamViewModel.fetchStreams`: `cs3ApiName` dolu ve kullanıcı genişletme istemediyse **yeni
  `runScopedPluginFetch`** çalışır:
  1. doğrudan sağlayıcı URL'si (`getStreamsForUrl`, artık `mediaType` de geçiliyor),
  2. aynı eklentinin **kendi başlık araması** (`getStreams`, aynı kimlik dönüşümüyle).
  Hiçbir adımda başka eklenti veya Stremio addon'ı devreye girmez.
- `_scopedSourceName` durumu eklendi; ekran bunu bir bildirim şeridiyle gösterir:
  *"Yalnızca ⚡ X eklentisinde arandı; diğer kaynaklar listeye karıştırılmadı."* +
  **"Tüm kaynaklarda ara"** düğmesi.
- `StreamViewModel.searchAllSources()` / `startFetch(allSources = true)`: genişletme artık **açık
  kullanıcı tercihi**; ortak arama (Stremio + tüm CS eklentileri) o zaman koşar ve kapsam bayrağı
  fetch anahtarına eklenir (önbellek çakışması olmaz).
- Kimlik çözümü (ARM/TMDB) yalnızca başlık aramasına gerekli olduğunda çalışır; doğrudan URL hızlı
  yolu ek ağ gecikmesi beklemez.

### Çözüm 2 — oynatıcının sonraki bölümü

`KitsugiPlayerViewModel.fetchStreamsForEpisode`:

1. doğrudan URL (`mediaType` ile — önceden hiç geçilmiyordu, tür filtresi/ID kullanımı eksik kalıyordu),
2. aynı eklentinin kendi başlık araması,
3. ancak bu ikisi de boşsa ortak aramaya düşülür (oynatmanın tamamen durmaması için) ve durum loglanır.

---

## 4. Değişen dosyalar

| Dosya | Değişiklik |
|---|---|
| `data/cloudstream/MalTitleAliasProvider.kt` | **Yeni.** Jikan başlık diğer adları, önbellekli + tek uçuş + saf ayrıştırıcı. |
| `data/cloudstream/CsStreamRunner.kt` | `effectiveAlts` artık MAL başlık evreniyle zenginleşiyor. |
| `data/remote/KitsugiMalDetailClient.kt` | MAL detayında `titleRomaji` / `titleNative` dolduruluyor (Jikan + resmî MAL v2). |
| `ui/screens/stream/StreamViewModel.kt` | `runScopedPluginFetch`, `scopedSourceName`, `searchAllSources`, `startFetch(allSources)`. |
| `ui/screens/stream/StreamSourceScope.kt` | **Yeni.** "Tek eklenti mi, ortak arama mı?" kuralı — tek yerde, test edilebilir. |
| `ui/screens/stream/StreamScreenContent.kt` | `ScopedSourceNotice` şeridi (dikey + yatay düzen). |
| `ui/screens/stream/KitsugiStreamScreen.kt` | Yeni durumu toplayıp ekrana bağlar. |
| `ui/screens/fullscreen/KitsugiPlayerViewModel.kt` | Sonraki bölümde kapsamlı çözüm + `mediaType`. |
| `res/values/strings.xml`, `res/values-en/strings.xml` | 2 yeni metin (parite betiği doğrulandı). |
| `test/.../MalTitleAliasProviderTest.kt` | Başlık ayrıştırma, boş/bozuk yanıt, 8 ad sınırı testleri. |
| `test/.../stream/StreamSourceScopeTest.kt` | Kapsam kuralı + görünen ad üretimi testleri. |

## 5. Doğrulama

- `python3 scripts/check_localization_resource_parity.py` → **in sync (17.698 string, 4 array)**.
- `MalTitleAliasProviderTest` (3 test), `StreamSourceScopeTest` (3 test).
- Davranış doğrulaması cihazda yapılmalı (bu ortamda Android derleyicisi yok):
  1. **MAL kaydı** → İzle → Anizm / TrAnimeIzle / AnimeciX sonuçları **AniList kaydıyla aynı** olmalı.
  2. Eklenti keşfet → içerik → bölüm → İzle → listede **yalnızca** o eklenti + bildirim şeridi;
     "Tüm kaynaklarda ara" ile tüm kaynaklar.
  3. Eklenti bölüm URL'si bozuk senaryo (eski/yanlış sezon): liste artık sessizce diğer
     kaynaklarla dolmamalı, aynı eklentinin başlık araması denenmeli.

## 6. Açık kalan soru (cihaz verisi gerekiyor)

MAL yolunda hâlâ "veri yok" görülürse hangi aşamada durduğunu kesinleştirmek için gereken bilgi:
eklenti satırındaki **hata mesajı** (ör. "Arama sonucu yok (18 varyant)", "⏱ zaman aşımı",
"🔐 Bot/WAF") ve **Eklenti Günlükleri** (CsTrace) çıktısı. Bu ikisi olmadan kalan fark (ör. sezon
numarası / bölüm eşleştirme) tahminle düzeltilmemeli.

## 7. Bilinçli olarak yapılmayanlar

- TV akış ekranı (`TvStreamScreen`) hâlâ eklenti kapsamı bayraklarını taşımıyor; TV tarafında
  kapsam desteği ayrı bir iş olarak bırakıldı.
- `CsPluginStatusTracker` oturum bloklama eşiği (3 hata) değiştirilmedi.
- `determineTargetSeason` (MAL tarafında `tmdbSeason` çoğu zaman yok) değiştirilmedi: sezon
  çıkarımını değiştirmek yanlış bölüm oynatma riski taşıyor, cihaz doğrulaması gerekiyor.
