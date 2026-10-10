# Kitsugi — Kaynak Bazlı Arama & Video Veri Çekme Mekaniği (Film / Dizi Ayrı Boru Hatları)

**Tarih:** 2026-10-10
**Dal:** `arena/a9503a7b-kitsugi-beta` (temel: `94a3c7f` / v2.4.226)
**Sürüm:** v2.4.227
**Şikâyet:** *"CS eklentileri (özellikle Türkçe) üzerinden video bulunamıyor. Uygulama
kaynaklardan isim+sezon bilgisiyle doğrudan veri çekmeye çalışıyor; oysa hiçbir sitede
'Mushoku Tensei Sezon 1 Bölüm 1' diye aratınca veri gelmez. Önce eser adı aranmalı,
içerik sayfasına girilmeli, sonra sezon+bölüm sayfasına inilip o bölüme ait video
verileri çekilmeli. Filmler sezon mantığıyla depolanmadığı için film mekaniği ayrı,
dizi/anime mekaniği ayrı ve her kaynak için çok düzgün oturtulmalı."*

---

## 1. İlke (Tüm Kaynaklar İçin Ortalık Kural)

```
BAŞLIK ARA (search) → İÇERİK SAYFASINA GİR (load) → VİDEO VERİLERİNİ ÇEK (loadLinks)
```

- Arama sorgusuna **asla** sezon/bölüm karışmaz. Sezon/bölüm bilgisi yalnızca:
  1. arama sonuçları içinden doğru eseri **seçerken** (skorlama),
  2. içerik sayfası yüklendikten **sonra** bölüm listesinden doğru bölümü **eşleştirirken** kullanılır.
- Bu ilke `CsTitleMatcher.buildPlainTitleVariants` (çıplak başlık varyantları) ile
  kod düzeyinde zorunlu kılındı; sezon ekli varyantlar `buildSeasonScopedVariants`
  içine ayrıştırıldı ve yalnızca aşağıdaki iki güvenlik ağında kullanılır.

## 2. İki Ayrı Boru Hattı

### 2.1 Film Mekaniği — `extractMovieStreams`

| Adım | Davranış |
|---|---|
| search | Yalnızca çıplak eser adı (+ dil alternatifleri + ASCII varyantlar). Sezon sorgusu YOK. |
| load | Eşleşen içerik sayfası yüklenir. |
| loadLinks | Sayfanın kendisi video sayfasıdır: `MovieLoadResponse.dataUrl ?: url`. Bölüm eşleştirme YOK. |

### 2.2 Dizi/Anime Mekaniği — `extractSeriesStreams`

| Adım | Davranış |
|---|---|
| search | Yalnızca çıplak eser adı. |
| load | Eşleşen içerik sayfası yüklenir. |
| bölüm eşleştirme | `CsEpisodeMatcher` ile hedef S+E, yüklenen sayfanın bölüm listesinden seçilir. |
| sayfa-tipli plugin | Eklenti bölüm listesi doldurmuyorsa (powerDizi/XPrime tarzı) sayfa URL'si bölüm kapsayıcısıdır. |
| **sezon sayfası gezinmesi** | Bölüm listesi DOLU ama hedef sezon yoksa: `navigateToSeasonPage` eklentinin KENDİ search+load akışıyla sezon girdisini bulur ve o sayfadan S+E verisini çeker. CloudStream API'sinde sezon gezinme metodu olmadığından kaynak-bağımsız tek genel yol budur. |

Dispatcher: `loadAndExtractStreams` — `isMovie` bayrağına göre iki hattan birine yönlendirir;
`getStreams` (detay sayfası) ve `getStreamsForUrl` (doğrudan URL / eklenti detay penceresi)
girişlerinin ikisi de aynı dispatcher'ı kullanır, yani mekanik **her kaynak nazarında** tektir.

## 3. Eski Davranış Hataları (Bu Oturumda Düzeltilen)

| # | Eski davranış (HATA) | Yeni davranış |
|---|---|---|
| 1 | Sezon ekli başlık varyantları ("X 2. Sezon") birincil arama sorguları arasına karışıyordu. | Birincil arama yalnızca çıplak başlık; sezon varyantları sadece güvenlik ağı. |
| 2 | Bölüm listesi dolu ama eşleşme yoksa loadLinks'e **dizi sayfası URL'si** veriliyordu → 25 sn bütçe çöpe, kart "akış bulunamadı". | Dizi sayfasına loadLinks YASAK; gerçek sebep tracker'a yazılır + sezon sayfası gezinmesi denenir. |
| 3 | Çıplak arama sıfır sonuç verdiğinde sezonları ayrı indeksleyen sitelerde (örn. Türkçe dizi siteleri "X 2. Sezon" girdisi) ikinci şans yoktu. | "Sezon girdisi araması" aşaması: yalnızca çıplak arama boşsa sezon ekli sorgular denenir. |
| 4 | Film ve dizi çözümleme mantığı `runGetStreams` içinde satır içi kopya + `loadAndExtractStreams` içinde ikinci kopya olarak çift yaşıyordu; ikisi birbirinden sapmıştı. | Tek dispatcher + iki adlandırılmış boru hattı (`extractMovieStreams` / `extractSeriesStreams`); çift `load` çağrısı `preloaded` ile önlenir. |
| 5 | Boru hattı yalnızca `isMovie` bayrağına bakıyordu; kaynak filmi "tek bölüm" dizi girdisi olarak döndürürse (TR sitelerinde yaygın) film borusu dizi sayfasına loadLinks deniyordu. | Dispatcher yüklenen sayfanın GERÇEK tipine bakar (`responseIsEpisodic`): bayrak ile sayfa tipi uyuşmazsa bölümlü yanıt dizi borusuna, bölümsüz yanıt film borusuna gider. |

## 4. Değişen Dosyalar

| Dosya | Değişiklik |
|---|---|
| `CsTitleMatcher.kt` | `buildTitleVariants` → `buildPlainTitleVariants` + `buildSeasonScopedVariants` ayrıştırması (eski imza uyumluluk için korundu). |
| `CsStreamRunner.kt` | Birincil arama yalnızca çıplak varyantlar; "SON ÇARE 1.5" sezon girdisi araması; dispatcher + film/dizi boru hatları + `navigateToSeasonPage` + `episodeCountOf`. |
| `app/build.gradle.kts` | `2.4.226` → `2.4.227`. |
| `RELEASE_NOTES.md` | v2.4.227 TR/EN notları. |

## 5. Davranış Özeti (Kart Dili)

- Film: `🎬 Film mekaniği: içerik sayfası → loadLinks`.
- Dizi, bölüm sayfada: `📺 Dizi mekaniği: SxEy bölüm verisi yüklenen sayfada bulundu`.
- Dizi, sezon ayrı sayfada: `⤷ Sezon gezinmesi başarılı: 'X 2. Sezon' → … → SxEy`.
- Dizi, hiçbir yol bulamazsa kartta gerçek sebep: *"SxEy bulunamadı — sezon sayfası
  gezinmesi de sonuç vermedi (yüklenen sayfada N bölüm vardı)"*.

## 6. Doğrulama Notu

Bu sandbox'ta JDK/Android SDK bulunmadığından (`java` yok, Gradle dağıtımı izin verilen
ağ listesinden indirilemiyor) `assembleFossRelease` derlemesi çalıştırılamadı. Değişiklikler
kod incelemesi + sözdizim bölgesi gözden geçirmesi ile doğrulandı; ilk cihaz derlemesinde
`CsStreamRunner` log'ları (`CS_SEARCH_ERR` / `PLUGIN_DIAG` filtreleri) yeni aşamaların
izini uçtan uca gösterecek şekilde yazıldı.
