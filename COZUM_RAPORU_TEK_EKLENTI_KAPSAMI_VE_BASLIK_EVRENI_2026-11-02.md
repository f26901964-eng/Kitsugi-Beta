# Kök neden + çözüm — "AniList'ten gelince eklenti veri vermiyor" ve "eklentiye özel arama diğer kaynaklarla karışıyor"

**Tarih:** 2026-11-02
**Sürüm:** v2.4.340 tabanı (sonraki derleme)
**Kapsam:** (1) Akış seçici ekranı (`KitsugiStreamActivity` / `StreamViewModel`), (2) tam ekran oynatıcının
sonraki bölüm akışı (`KitsugiPlayerViewModel`), (3) eklenti arama boru hattının başlık evreni
(`CsStreamRunner` + yeni `MalTitleAliasProvider`).

---

## 1. Şikâyet özeti

| # | Kullanıcı gözlemi | Beklenen davranış |
|---|---|---|
| A | Aynı anime **AniList** kaydından açıldığında Anizm / TrAnimeIzle / AnimeciX gibi eklentiler "akış bulunamadı" diyor; **MAL** kaydından açıldığında buluyor. | Kaynak fark etmeksizin aynı eklenti seti aynı sonucu vermeli. |
| B | Bir eklentinin **kendi keşfet/arama** sayfasından "İzle" ya da bölüm satırına basıldığında sonuç yalnızca o eklentiden gelmeli; ama bazen diğer kaynaklarla karışıyor. | Eklentiye özel akış kapsamlı kalmalı; genişletme kullanıcı tercihi olmalı. |

---

## 2. Kök neden A — eklentiye giden arama sorgusu kaynağa göre değişiyordu

`CsStreamRunner.runGetStreams` arama varyantlarını detay sayfasının gönderdiği başlıklardan üretiyordu:

- **MAL/Jikan** kaydı → `title` = **Romaji** ("Shingeki no Kyojin")
- **AniList** kaydı → `title` = **İngilizce / Romaji** karışımı ("Attack on Titan"), yeni/kısa adlı
  yapılarda çoğu zaman yalnızca İngilizce
- TMDB zenginleştirmesi yalnızca **tr-TR** başlığını ekliyordu (`TmdbTitleLocalizer`)

Türk eklentileri sitelerinde genelde **Romaji + Türkçe** başlık taşır. AniList'ten gelen İngilizce
başlıkla yapılan 18 varyantlık arama, site indeksinde karşılık bulamadığı için eklenti "isimle
bulamıyor" durumuna düşüyordu — kaynak MAL olunca Romaji başlık geldiği için aynı eklenti çalışıyordu.
**Ortak sorun buydu:** eklentinin başarısı, detay sayfasının hangi kaynaktan açıldığına bağlıydı.

### Çözüm

`data/cloudstream/MalTitleAliasProvider.kt` (yeni):

- MAL kimliği varsa Jikan `anime/{id}` yanıtındaki **tüm** başlık diğer adları (Default / English /
  Japanese / Synonym / Turkish …) okunur ve arama varyantlarına eklenir.
- Oturum-önbellekli (`BoundedCache`, 400 kayıt) + eşzamanlı çağrılar `Mutex` ile tek isteğe indirilir;
  164 eklenti tek Jikan isteğini paylaşır. Boş/hatalı sonuç da negatif önbelleğe yazılır (kota koruması).
- `JikanGateway` üzerinden gidilir: mevcut hız sınırı, devre kesici ve yedek host mantığı korunur.
- En fazla 8 diğer ad alınır, normalize başlığa göre tekilleştirilir (sorgu maliyeti sabit kalır).

`CsStreamRunner.runGetStreams` içinde TMDB tr-TR başlığının hemen ardına bağlandı; hata/eksik durumda
davranış eskisiyle aynı kalır (`getOrDefault(alternativeTitles)`).

**Sonuç:** Aynı anime hangi kaynaktan açılırsa açılsın eklenti aynı başlık evreniyle arar
(Romaji + İngilizce + Japonca + Türkçe + eş anlamlılar). AniList/MAL farkı ortadan kalkar.

---

## 3. Kök neden B — doğrudan URL başarısız olunca sessizce TÜM kaynaklara düşülüyordu

Eski `StreamViewModel.fetchStreams`:

```kotlin
if (resolved.isNotEmpty()) { ...; return }
...
_addonStates.value = emptyList()   // ← bütün liste silinir
// ardından Stremio addon'ları + TÜM CloudStream eklentileri aranır
```

Yani eklentinin kendi bölüm URL'si (site yapısı değişti, bölüm eşleşmedi, geçici ağ hatası) bir kez
boş döndüğünde ekran **tüm kaynakların ortak aramasına** düşüyordu. Kullanıcı "sadece bu eklenti"
beklerken listede başka kaynaklar beliriyor, sonraki denemede bazen çalıştığı için davranış
"bazen çalışıyor bazen bozuluyor" gibi görünüyordu. Aynı davranış tam ekran oynatıcının sonraki bölüm
akışında (`KitsugiPlayerViewModel.fetchStreamsForEpisode`) da vardı.

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
- `MalTitleAliasProviderTest` (3 test): çoklu tür ayrıştırma, bozuk/boş yük, ad sayısı tavanı.
- `StreamSourceScopeTest` (3 test): eklenti akışında kapsam, ortak aramada kapsamsızlık, "⚡ " öneki tekilliği.
- Davranış doğrulaması cihazda yapılmalı (bu ortamda Android derleyicisi yok):
  1. AniList kaydı → İzle → Anizm / TrAnimeIzle / AnimeciX sonuçları MAL kaydıyla aynı olmalı.
  2. Eklenti keşfet → içerik → bölüm → İzle → listede **yalnızca** o eklenti + bildirim şeridi;
     "Tüm kaynaklarda ara" ile tüm kaynaklar.
  3. Eklenti bölüm URL'si bozuk senaryo (eski/yanlış sezon): liste artık sessizce diğer
     kaynaklarla dolmamalı, aynı eklentinin başlık araması denenmeli.

## 6. Bilinçli olarak yapılmayanlar

- TV akış ekranı (`TvStreamScreen`) hâlâ eklenti kapsamı bayraklarını taşımıyor; TV tarafında
  kapsam desteği ayrı bir iş olarak bırakıldı.
- `CsPluginStatusTracker` oturum bloklama eşiği (3 hata) değiştirilmedi.
