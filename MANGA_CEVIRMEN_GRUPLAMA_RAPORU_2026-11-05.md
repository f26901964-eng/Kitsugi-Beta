# Kitsugi Beta — Manga: Aynı Bölümün Çok Çevirmenli (Scanlator) Sürümleri

**Tarih:** 5 Kasım 2026
**Kapsam:** Manga detay ekranı (bölüm listesi) + manga okuyucu (bölüm geçişi)
**Branch:** `arena/fa251852-kitsugi-beta`

---

## 1. Sorun

Manga eklentileri aynı bölümü birden çok çevirmen (scanlator) üzerinden sunuyor:

```
Bölüm 3   AnimeWho
Bölüm 3   WebtoonTR
Bölüm 3   Tempest Fansub
Bölüm 4   AnimeWho
```

Bu, iki ayrı kullanıcı problemine yol açıyordu:

1. **Detay ekranında gürültü:** Aynı bölüm 3 kez listeleniyor; hangisinin ne olduğu, hangisinde kaldığınız belli değil.
2. **Okuyucuda sessiz çevirmen atlaması:** "Sonraki bölüm" basıldığında sıradaki *liste elemanı* açılıyordu; bu çoğu zaman **başka bir çevirmenin** aynı bölümü ya da rastgele bir sürüm oluyordu. Kullanıcı farkında olmadan çevirmen değiştiriyor, sayfa sayısı ve konum şaşıyordu.

---

## 2. Tasarım kararları

| Karar | Gerekçe |
|---|---|
| **Grup anahtarı = bölüm numarası** (`n:3`), numara yoksa normalize edilmiş ad (`t:bölüm 3`) | Kaynaklar bölüm adını farklı yazıyor (`Chapter 3`, `Bölüm 3`, `Bölüm 3 – ...`). Numara en güvenilir ortak payda; `chapterNumber` alanı yoksa ad içindeki ilk sayı çıkarılır. |
| **Yıkıcı olmayan gruplama** (aç/kapa) | Düz listede tekilleştirme yapılsaydı diğer çevirmenlere erişim tamamen kaybolurdu. Grup satırı tek satır gösterir, dokununca sürümler açılır. |
| **Birincil sürüm = hatırlanan çevirmen → ilerlemesi olan → en yeni yükleme** | Kullanıcı bir kez çevirmen seçtiyse akış onunla devam eder; hiç seçmediyse yarım kaldığı sürüm öne çıkar. |
| **Tercih deposu: SharedPreferences** (`MangaScanlatorPreferences`) | Okuyucu ilk karesini çizerken senkron okunması gerekiyor (DataStore asenkron). Aynı gerekçe `MangaReaderPreferences` için de kullanıldı. |
| **İlerleme zaten `chapterUrl` bazlı** | Her sürümün kendi `lastPageIndex / totalPages / isCompleted` kaydı var; gruplama bunu birleştirerek gösteriyor, veri modeli değişmedi. |
| **Geçiş penceresi sessiz otomatik seçim yapmaz** | "Hangi çevirmenle devam edeyim?" sorusu ancak kullanıcı "Bir daha sorma" derse atlanır. |

---

## 3. Yeni / değişen dosyalar

| Dosya | Görev |
|---|---|
| `data/manga/MangaChapterGrouping.kt` *(yeni)* | `MangaChapterGroup` modeli + gruplama algoritması: `groupKey()`, `chapterNumberOf()`, `group()`, `pickVariant()`, `variantForScanlator()`, `normalizedScanlator()`. |
| `data/manga/MangaChapterChoice.kt` *(yeni)* | `ChapterVariantInfo` (sürüm + ilerleme), `ChapterChoiceRequest` (pencere isteği + ön seçim), `ChapterChoiceReason` (Next / Previous / Manual). |
| `data/manga/MangaScanlatorPreferences.kt` *(yeni)* | Manga bazında çevirmen tercihi: `preferredScanlator`, `groupChoice`, `autoContinue`. |
| `ui/screens/manga/components/MangaChapterChoiceDialog.kt` *(yeni)* | Ayrıntılı çevirmen seçim penceresi: sürüm kartları, sayfa ilerlemesi, yükleme tarihi, "hatırla" ve "bir daha sorma" anahtarları. |
| `ui/screens/manga/MangaDetailViewModel.kt` | `ChapterGroupRow`, `UiState.groups/visibleGroups/scanlators/scanlatorFilter/mergedScanlators/expandedGroupKeys/preferredScanlator`; `toggleGroup`, `toggleMergedScanlators`, `setScanlatorFilter`, `rememberVariant`, `clearPreferredScanlator`, `setAutoContinue`. |
| `ui/screens/manga/MangaDetailScreen.kt` | Birleşik/ayrık görünüm düğmesi, çevirmen filtre çipleri, hatırlanan tercih rozeti, açılır çevirmen satırları (`ChapterGroupListItem` + `ChapterVariantRow`). |
| `ui/screens/manga/MangaReaderViewModel.kt` | Grup tabanlı `goToNextChapter/goToPreviousChapter`, `requestGroup()` (ilerleme toplayıp pencere isteği yayınlar), `confirmChapterChoice`, `dismissChapterChoice`, `nextChapterVariantCount`. |
| `ui/screens/manga/MangaReaderScreen.kt` | Seçim penceresini bağlar, üst barda çevirmen rozeti, bölüm seçicide gruplar. |
| `ui/screens/manga/components/MangaReaderControls.kt` | `MangaChapterPickerSheet` artık gruplarla çalışıyor (dokun-aç, "Önerilen" işareti); üst bar çevirmen rozeti. |
| `ui/screens/manga/components/MangaWebtoonReader.kt` | Bölüm sonu kartında "%d çevirmen" ipucu. |
| `ui/tv/manga/TvMangaReaderScreen.kt` | TV'de HUD bölünmesin diye ön seçili sürüm sessizce onaylanır. |
| `res/values/strings.xml`, `res/values-en/strings.xml` | 24 yeni TR/EN metin. |

---

## 4. Davranış matrisi

| Durum | Sonuç |
|---|---|
| Detay ekranı, bölümün tek çevirmeni var | Tek satır; dokununca doğrudan açılır (eski davranış). |
| Detay ekranı, bölümün N>1 çevirmeni var | Tek satır + `N çevirmen` rozeti + aç/kapa oku; açılınca her sürüm ayrı satır (çevirmen adı, sayfa, tarih, okundu işareti). |
| Okuyucuda "sonraki bölüm", hedef bölüm tek çevirmenli | Doğrudan geçer. |
| Okuyucuda "sonraki bölüm", hedef bölüm çok çevirmenli, "bir daha sorma" kapalı | Ayrıntılı pencere: her çevirmenin adı, bölüm adı, yükleme tarihi, kaldığı sayfa; ön seçim hatırlanan çevirmen. |
| Okuyucuda "sonraki bölüm", "bir daha sorma" açık | Hatırlanan çevirmenle doğrudan geçer (hatırlanan yoksa pencere yine açılır). |
| Bölüm seçici (okuyucu içi) | Gruplar listelenir; çok çevirmenli grup dokunuşta açılır, sürüm seçimi doğrudan açılır. |
| TV okuyucu | Ön seçili sürüm sessizce onaylanır (HUD odak yönetimi bozulmasın). |
| Detay ekranında çevirmen filtresi | Çip ile yalnızca o çevirmenin bölümleri; filtre sonucu boşsa bilgilendirme satırı. |

---

## 5. Kenar durumları (ele alınan)

- **Bölüm numarası yok / ad sayı içermiyor:** `t:<ad>` anahtarı ile ada göre gruplanır; aynı ad = aynı bölüm.
- **`scanlator` null veya boş:** "Çevirmen belirtilmemiş" olarak gösterilir, gruplama yine çalışır.
- **Farklı yazımlar (`AnimeWho` / `animewho`):** Normalize edilmiş karşılaştırma.
- **Navigasyondan gelen bölüm listede yok (URL farklı):** `currentGroupIndex()` önce URL ile, bulamazsa bölüm numarasıyla eşleştirir; "sonraki bölüm" yine çalışır.
- **Hızlı ardışık geçiş:** İlerleme sorgusu bitmeden bölüm değişirse pencere yayınlanmaz (bayat istek koruması).
- **Aynı bölümde çevirmen değiştirme:** İlerleme `chapterUrl` bazlı olduğu için her sürüm kendi kaldığı sayfadan açılır.
- **Grup içi okundu durumu:** Satır, sürümlerden herhangi biri bitmişse "okundu" görünümü alır; sayfa ilerlemesi birincil sürümden gelir.

---

## 6. Doğrulama

Bu ortamda **Android SDK / JDK / Gradle yok**, derleme yapılamıyor. Yapılan statik doğrulamalar:

| Kontrol | Sonuç |
|---|---|
| Süslü parantez / normal parantez / köşeli parantez dengesi (yorum ve string'ler ayıklanarak), 11 dosya | 0 hata |
| String literal sayısı tekliği (odd-quote) | 0 hata |
| `R.string.*` referanslarının `values*/strings.xml` içinde varlığı (11 dosya) | 0 eksik |
| `values/strings.xml` ve `values-en/strings.xml` XML geçerliliği | İkisi de geçerli, 17.593 string, 0 tekrar |
| TR/EN string sayısı eşitliği | 17.593 / 17.593 |

> Not: `lint { abortOnError = false }` olduğu için derleme hataları yalnızca gerçek Kotlin derlemesinde görülebilir; cihaz/emülatör testi gerekiyor.

---

## 7. Cihaz test listesi

1. Detay ekranı: aynı bölümün 3 çevirmeni olan manga → **tek satır + "3 çevirmen"** görünüyor mu?
2. Satıra dokun → çevirmen listesi açılıyor, tekrar dokun → kapanıyor mu?
3. Açılan listeden bir çevirmen seç → doğru sürüm açılıyor, ilerleme o sürümün sayfasından mı geliyor?
4. Üst bardaki birleştir/ayır ikonu → tüm sürümler ayrı satır olarak listeleniyor mu?
5. Çevirmen filtre çipi → yalnız o çevirmenin bölümleri; "Tümü" ile geri dönüş; sonuç yoksa uyarı satırı.
6. "Tercih: AnimeWho" rozeti → X ile temizlenince ön seçim değişiyor mu?
7. Okuyucuda son bölüme gel → "Sonraki bölüm" → çok çevirmenli hedefte **ayrıntılı pencere** açılıyor mu (çevirmen adı, sayfa, tarih)?
8. Pencerede başka çevirmen seç → "Devam et" → o sürüm açılıyor, sayfa sayacı doğru mu?
9. "Bir daha sorma" açık + devam et → sonraki geçişte pencere çıkmadan aynı çevirmenle geçiyor mu?
10. "Vazgeç" → pencere kapanıyor, mevcut bölüm/sayfa değişmiyor mu?
11. Uzun basılan/dokunulan bölüm seçici (okuyucu içi) → çok çevirmenli grupta aç/kapa, tek sürümde doğrudan açılış.
12. Webtoon modunda bölüm sonu kartı → "%d çevirmen arasından seçim yapacaksın" ipucu görünüyor mu?
13. TV: "sonraki bölüm" → pencere açılmadan ön seçili sürümle geçiyor mu (HUD odağı bozulmuyor mu)?
14. RTL / LTR / Dikey / Webtoon modlarının hiçbiri bozulmadı mı?
15. Ayar ekranı, kütüphane, anime tarafı etkilenmedi mi (regresyon).

---

## 8. Bilinen sınırlar / sonraki adımlar

- **TV detay ekranı hâlâ düz liste:** Gruplama telefon/tablet detay ekranında; TV listesi odak (`focusRestorer`, `dpadRepeatThrottle`) davranışını bozmamak için değiştirilmedi. TV'de grup + varyant seçimi ayrı bir odak tasarımı gerektiriyor.
- **Grup bazında toplu ilerleme göstergesi** (ör. "3 sürümden 1'i okundu") satır altında tek satır özet olarak yok; rozet + sürüm satırlarındaki işaretlerle veriliyor.
- **Tarih biçimi** cihaz yereline göre (`d MMM yyyy`); kaynak saniye/milisaniye karışık gönderirse ikisi de tolere ediliyor.
- **Çevirmen tercihi manga bazında**, global "hep aynı çevirmen" ayarı yok (istenirse `MangaScanlatorPreferences` içine global anahtar eklenebilir).
