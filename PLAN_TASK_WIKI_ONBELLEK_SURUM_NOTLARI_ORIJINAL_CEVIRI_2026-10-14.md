# PLAN/TASK — Depolama "Wiki Önbelleği" + Hakkında Sürüm Notları + Wiki Orijinal/Çeviri Geçişi (v2.4.290)

**Tarih:** 2026-10-14
**Sürüm:** 2.4.289 → **2.4.290** (`app/build.gradle.kts`)
**Dal:** `arena/5d043630-kitsugi-beta` · **PR:** [#13](https://github.com/f26901964-eng/Kitsugi-Beta/pull/13)
**Durum:** ✅ Uygulandı ve CI'da derlendi (`android-build.yml` → **success**, foss debug APK üretildi)

---

## 1. Kullanıcı Şikâyetleri (ekran görüntüleriyle)

### Sorun A — Depolama ekranında wiki verisi görünmüyor (görsel 1: Ayarlar > Depolama)
- Ekranda Görsel önbelleği, P2P, Manga, Video önizlemeleri, Altyazı, Veritabanı, Teşhis, Web verileri ve Eklenti kalemleri vardı; **wiki önbelleği/verisi yoktu**.
- Kullanıcı yer açmak için hangi verinin ne kadar kapladığını göremiyor, tek çare olarak tüm uygulama verisini silmek (hesap/mylist kaybı) kalıyordu.

### Sorun B — "Uygulama Hakkında"da son güncellemenin sürüm notları yok (görsel 2: Uygulama Hakkında)
- Uygulama GitHub Releases üzerinden güncelleniyor; ancak ekranda yalnızca "Otomatik Kontrol" anahtarı ve "Şimdi Denetle" düğmesi var.
- **Kurulu sürüm güncelken** son sürümün değişiklik notları hiçbir yerde görünmüyordu ("Uygulamanız güncel!" bildirimi dışında geri bildirim yok).

### Sorun C — Bilgi kutusunda Orijinal/Çeviri düğmesi yok (görsel 3: Wiki sekmesi, "Bilgi kutusu")
- Bilgi kutusunun sağ üstünde yalnızca **harici çeviri** düğmesi (文A) ve kopyala düğmesi vardı.
- Uygulama içi (otomatik) çeviri ekranda gösterilirken **orijinal metne dönüş yolu yoktu**; karakter wiki sekmesinde bu düğme varken yapım maddesi tarafında yoktu.
- İsteğin kapsamı: *"wiki sayfası ve açılır wiki sayfasında, Wikipedia dahil otomatik çeviriye giren tüm kısımlarda harici çeviri düğmesinin hemen yanında Orijinal/Çeviri düğmesi."*

---

## 2. Kök Nedenler

### A
- `StorageJanitor.Bucket` enum'unda **dosya tabanlı** kalemler vardı; wiki kayıtları ise **Room** içinde (`persistent_details_cache`, `fandom:v1:` önekli satırlar) tutuluyor → dosya taramasıyla hiç görünmüyordu.
- Aynı tablo medya detay önbelleğini de barındırdığı için (`KitsugiDetailClient` `detailCacheKey`) ölçüm/silme mutlaka **önekle** sınırlanmalı.
- SQLite silme sonrası dosya boyutunu kendiliğinden küçültmez (VACUUM gerekir) → ölçüm mantıksal veri boyutu üzerinden yapılmalı, aksi hâlde "Temizle" hiçbir şey kazanmamış gibi görünür.

### B
- `KitsugiUpdateRepository.checkForUpdate()` yeni sürüm yoksa **null** döner; `AppUpdateViewModel` `Idle`'a döner ve pencere açılmaz.
- `AppRelease` yalnızca bu akış içinde üretiliyordu; **sürümden bağımsız** "son sürüm bilgisi" API'si yoktu.
- GitHub Releases API'si kimliksiz isteklerde **saatte 60** ile sınırlı → Hakkında ekranı her açılışta istek atarsa güncelleme denetimi de kilitlenir; disk önbelleği şart.

### C
- `MediaWikiTabContent.kt` içindeki dört `WikiTranslateButton` çağrısından **üçü** `hasTranslation` / `showingOriginal` / `onToggleOriginal` parametreleri olmadan çiziliyordu:
  - `WikiItemSheet` başlığı (açılır sayfa açıklaması),
  - `ArticleInfoboxCard` (bilgi kutusu),
  - `ArticleSectionRow` (madde bölümleri).
- `WikiTranslateButton` geçiş düğmesini yalnızca `hasTranslation && onToggleOriginal != null` koşulunda çizer → parametreler verilmediği için düğme hiç doğmuyordu.
- ViewModel tarafı hazırdı: `_originalKeys` + `toggleOriginal(key)` ve anahtar üreticileri (`itemKey`, `articleInfoboxKey`, `articleInfoboxFieldKey`, `articleSectionKey`) mevcuttu; eksik olan yalnızca **arayüz bağlantısı**ydı.

---

## 3. Yapılan İşler

### A — Depolama: "Wiki önbelleği" kalemi
- [x] `PersistentDetailCacheDao`:
  - `sizeByPrefix(prefix): Long` → `COALESCE(SUM(LENGTH(detailJson)), 0)` (mantıksal bayt),
  - `countByPrefix(prefix): Int` → `COUNT(*)`,
  - önek LIKE joker karakteri içermemeli (yorumla belgelendi).
- [x] `FandomRepository`:
  - `cacheStats(context): Pair<Long, Int>` → (bayt, kayıt sayısı),
  - `clearCache(context)` → yalnızca önbellek: `memory.clear()` + `fandom:v1:` satırlarını siler, **`FandomUserChoices` korunur** (`clearAll`'dan farkı bu; sınıf dokümanına yazıldı).
- [x] `StorageJanitor`:
  - Yeni `Bucket.WIKI_CACHE("wiki_cache", …, clearable = true)` (DATABASE'den sonra),
  - `clean()` → `FandomRepository.clearCache(context)`,
  - `statsOf()` artık `private suspend fun` (Room sorgusu) ve WIKI_CACHE dalı `runCatching { FandomRepository.cacheStats(context) }.getOrDefault(0L to 0)`,
  - Sınıf dokümanına "Kalem kapsamı" bölümü (dosya tabanlı / mantıksal / dosya boyutu ayrımı).
- [x] `StorageSettingsContent`: `WIKI_CACHE` → `Icons.Rounded.AutoStories` + yeni `KitsugiColors.AccentSky` (`0xFF38BDF8`).
- [x] Metinler: `storage_bucket_wiki`, `storage_bucket_wiki_desc` (TR + EN, `storage_bucket_database_desc` satırından sonra).
- [x] **Bilinçli olarak dokunulmayan:** arka plan budaması (`pruneDatabase` / `MappingSyncWorker`, 30 gün) wiki satırlarını zaten temizliyor; ikinci bir budama eklenmedi.

### B — Hakkında: GitHub sürüm notları kartı
- [x] `KitsugiUpdateRepository` yeniden yapılandırıldı:
  - `fetchLatestReleaseJson()` (GitHub → Codeberg yedeği; davranış aynı),
  - `buildRelease(json, requireApk)` + `selectApkAsset(json)` (flavor eşleşmesi korundu),
  - `latestRelease(context, force)` → yeni sürüm koşulu aramaz, APK eki zorunlu değil, sonucu önbelleğe yazar,
  - `isNewerThanInstalled(release)` → karar mantığı güncelleme akışıyla **aynı** fonksiyon (`isNewerRelease`).
- [x] `ReleaseNotesCache` (yeni): SharedPreferences tabanlı **6 saatlik** önbellek; bozuk/eksik kayıt sessizce yok sayılır; `clear()` destekli.
- [x] `KitsugiUpdateDialog.CleanReleaseNotesText` → `private` ⇒ **`internal`** (güncelleme penceresi ile Hakkında kartı aynı biçimlendirmeyi paylaşır).
- [x] `AboutScreen`:
  - `ReleaseNotesUiState` (`Loading` / `Unavailable` / `Failed` / `Ready`),
  - `LaunchedEffect(refreshKey)` ile yükleme; `force = refreshKey > 0` (ilk açılış önbellekten, "Yenile" ağdan),
  - `ReleaseNotesCard`: son sürüm rozeti (`vX.Y.Z`), "Yeni sürüm hazır" / "Kurulu sürüm en güncel", kurulu sürüm, yayın tarihi, notlar (en fazla 300 dp, kendi içinde kaydırılır), "GitHub'da tüm sürümler" bağlantısı, yenile düğmesi.
- [x] Metinler: 12 adet `about_release_notes_*` (TR + EN; `%1$s` yer tutucuları eşit).

### C — Wiki: Orijinal/Çeviri geçişi her yerde
- [x] `ArticleInfoboxCard` (açılır wiki sayfası bilgi kutusu):
  - Yeni `showingOriginal` + `onToggleOriginal` parametreleri,
  - `infoboxKey = articleInfoboxKey(keyTitle)`, `infoboxHasTranslation` (toplu çeviri işareti **veya** herhangi bir alan çevirisi),
  - "Orijinal" seçiliyken alanlar `localizedInfoboxValue(field.value)`; çeviri bellekte kalır, ağa tekrar gidilmez,
  - **"Bilgi kutusunu kopyala"** düğmesi eklendi (karakter sekmesindeki düzenle eşit): yalnızca görünen alanlar (`visibleCount`), gizli alanlar `[gizli]`, "Orijinal" modunda kaynak metin.
- [x] `WikiItemSheet` başlığı + özeti: `itemKey(title)` anahtarıyla geçiş; özet metni `if (descriptionShowingOriginal) description else (translatedDescription ?: description)`.
- [x] `ArticleSectionRow`: yeni `showingOriginal` / `onToggleOriginal` parametreleri; gövde `if (showingOriginal) null else translated`.
- [x] Bölüm listesi: `val sectionKey = articleSectionKey(title, index)` ile anahtar tek yerde üretiliyor (ölçüm/geçiş tutarlılığı).
- [x] `CopyIconButton` ve `copyWikiText` (karakter sekmesinde tanımlıydı) **`internal`** yapıldı; aynı paket paylaşımı böylece kod kopyası olmadan sağlandı.
- [x] **Wikipedia dahil:** Kaynak seçimi yalnızca `ref`'i değiştirdiği ve aynı bileşenler çizildiği için geçiş Fandom ve Vikipedi (TR) kaynaklarında da çalışır; anahtarlar kaynak bazlı olduğundan durumlar karışmaz.
- [x] Zaten kapsananlar **dokunulmadan** bırakıldı: karakter wiki sekmesi (`CharacterWikiTabContent`), okuyucu (`FandomWikiReader`: giriş + açıklama + bölümler), wiki sekmesi seri özeti (`MediaWikiSummaryCard`).

### Sürüm / notlar
- [x] `appVersionName` `2.4.289` → `2.4.290`.
- [x] `release_notes_v2.4.290.md` oluşturuldu; `RELEASE_NOTES.md` başına eklendi.
- [x] Rapor: `WIKI_DEPOLAMA_SURUM_NOTLARI_RAPORU_2026-10-14.md`.

---

## 4. Değişen Dosyalar (bu ZIP'in içeriği)

| Dosya | Değişiklik |
|---|---|
| `PLAN_TASK_WIKI_ONBELLEK_SURUM_NOTLARI_ORIJINAL_CEVIRI_2026-10-14.md` | Bu plan/task dosyası |
| `WIKI_DEPOLAMA_SURUM_NOTLARI_RAPORU_2026-10-14.md` | Uygulama raporu |
| `release_notes_v2.4.290.md` | Sürüm notları (TR/EN) |
| `RELEASE_NOTES.md` | v2.4.290 bloğu başa eklendi |
| `app/build.gradle.kts` | Sürüm 2.4.290 |
| `app/src/main/java/com/kitsugi/animelist/data/local/PersistentDetailCacheDao.kt` | `sizeByPrefix` / `countByPrefix` |
| `app/src/main/java/com/kitsugi/animelist/data/fandom/FandomRepository.kt` | `cacheStats` + `clearCache` (kararlar korunur) |
| `app/src/main/java/com/kitsugi/animelist/core/storage/StorageJanitor.kt` | `Bucket.WIKI_CACHE`, `statsOf` suspend, doküman |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/settings/StorageSettingsContent.kt` | Yeni kalem satırı (ikon/renk) |
| `app/src/main/java/com/kitsugi/animelist/ui/theme/KitsugiColors.kt` | `AccentSky` |
| `app/src/main/java/com/kitsugi/animelist/core/update/KitsugiUpdateRepository.kt` | `latestRelease` + `isNewerThanInstalled` + ayrıştırma |
| `app/src/main/java/com/kitsugi/animelist/core/update/ReleaseNotesCache.kt` | **Yeni** — 6 saatlik not önbelleği |
| `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiUpdateDialog.kt` | `CleanReleaseNotesText` → `internal` |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/more/AboutScreen.kt` | `ReleaseNotesCard` + durum makinesi |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/MediaWikiTabContent.kt` | Bilgi kutusu/açıklama/bölüm geçişi + kopyala |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/CharacterWikiTabContent.kt` | Paylaşılan yardımcılar `internal` |
| `app/src/main/res/values/strings.xml` | 14 yeni dize (TR) |
| `app/src/main/res/values-en/strings.xml` | 14 yeni dize (EN) |

---

## 5. Kabul / Doğrulama

**Otomatik (bu depoda koşuldu):**
1. `python3 scripts/check_localization_resource_parity.py` → *"Localization resources are in sync: 7447 strings and 4 arrays (including matching format placeholders)"*.
2. GitHub Actions `android-build.yml` (commit `e72bb10`) → **success**: filtrelenmiş birim testleri + `assembleFossDebug` (APK artifact).
3. Room şeması değişmedi (yalnızca yeni sorgular) → migrasyon gerekmez.

**Cihazda elle kontrol listesi:**
1. **Depolama:** Ayarlar > Depolama → "Wiki önbelleği" satırı görünmeli (boyut + kayıt sayısı).
   - Bir wiki maddesi açtıktan sonra değer artmalı.
   - "Temizle" → onay yok (önbellek), bildirim "X geri kazanıldı" olmalı; wiki açıldığında içerik yeniden yüklenmeli.
   - Ayarlar > Fandom Wiki > "Yeniden dene"/eşleşme kararları **korunmuş** olmalı (aynı wiki tekrar onay istememeli).
2. **Hakkında:** "Uygulama Hakkında" → "Sürüm Notları" kartı ilk açılışta önbellekten/anında dolar.
   - Uçak modunda aç → hata metni + "Yeniden dene" görünmeli (çökme yok).
   - "Yenile" → notlar tazelenmeli; "GitHub'da tüm sürümler" → tarayıcıda Releases sayfası.
   - Yeni sürüm yayınlandığında rozet "Yeni sürüm hazır" olmalı.
3. **Wiki:** Yapım wiki sekmesi → bir çipe/aramaya dokun (açılır wiki sayfası).
   - Bilgi kutusunda çeviri düğmesinin solunda **Orijinal** görünmeli (otomatik çeviri açıkken); "Orijinal"/"Çeviri" geçişi alanları değiştirmeli.
   - Açıklama ve bölümlerde aynı düğme; "Orijinal"de bölüm gövdesi çevrilmemiş metni göstermeli.
   - Bilgi kutusu kopyala → panoda "Etiket: değer" satırları, spoiler alanları `[gizli]`.
   - Kaynağı **Vikipedi (TR)** yap → aynı düğmelerin çalıştığını doğrula.

---

## 6. Notlar / Riskler

- **Ölçüm birimi farkı:** Wiki kalemi *mantıksal* bayt (Room verisi), Veritabanı kalemi *dosya* boyutu gösterir. Bu bilinçli: silme sonrası SQLite dosyası küçülmediği için mantıksal ölçüm geri kazanımı doğru yansıtır. "Toplam kullanım" bu iki birimi birlikte toplar (kalem açıklamaları bunu belirtir).
- **Kullanıcı kararları:** `clearCache` eşleşme kararlarını korur; tam sıfırlama isteyen kullanıcı için Ayarlar > Fandom Wiki > "Wiki önbelleğini temizle" (`clearAll`) yolu aynen duruyor (ekranda ayrıca belirtilmedi — gerekirse ileride ikinci bir satır eklenebilir).
- **İstek sınırı:** Sürüm notları 6 saat önbelleklenir; kullanıcı sürekli "Yenile"ye basarsa GitHub sınırına takılabilir (hata metni sınırı açıkça söylemez, yalnızca "alınamadı" der). İleride 403/429 ayrımı için ayrı mesaj eklenebilir.
- **`isNewerRelease` iki aşamalı:** Önce zaman damgası tabanlı `versionCode` (>100000) karşılaştırılır, sonra anlamsal sürüm adı. Etiket `v2.4.290` biçiminde yayınlandığı sürece "Güncelleme var" rozeti doğru çalışır (release workflow'u bu biçimde etiket üretir).
- **Geçiş durumu anahtarları:** `originalKeys` yalnızca bellekte tutulur; ekran/sekme kapanınca sıfırlanır (mevcut davranış, wiki sekmesi genelinde aynı).
