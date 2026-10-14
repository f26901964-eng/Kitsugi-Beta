# Wiki Önbelleği · Sürüm Notları · Wiki Orijinal/Çeviri Geçişi — Uygulama Raporu

Tarih: 2026-10-14 · Sürüm: **v2.4.290** · Dal: `arena/5d043630-kitsugi-beta`

Kullanıcı geri bildiriminden üç iş:

1. Depolama ekranında **wiki önbelleği/verisi** ile ilgili depolama bilgisi gösterilmeli (görsel 1).
2. "Uygulama Hakkında" ekranı GitHub üzerinden güncellendiği için **son güncellemenin sürüm notları** orada görünebilmeli (görsel 2).
3. Wiki sayfası ve **açılır wiki sayfası** bilgi kutusunda, otomatik çeviriye dahil **tüm** yerlerde harici çeviri düğmesinin hemen yanında **Orijinal/Çeviri** düğmesi olmalı (görsel 3).

---

## 1) Depolama → "Wiki önbelleği" kalemi

### Sorun
Ayarlar > Depolama ekranı yalnızca dosya tabanlı kalemleri (görseller, P2P, manga, önizleme, altyazı, veritabanı dosyaları, teşhis, WebView, eklenti) listeliyordu. Fandom/Wikipedia maddeleri, bilgi kutuları ve karakter sayfaları için tutulan wiki kayıtları görünmüyordu; kullanıcı yer açmak için hangi kalemi temizleyeceğini bilemiyordu.

### Çözüm (katman katman)

| Katman | Dosya | Değişiklik |
|---|---|---|
| DAO | `data/local/PersistentDetailCacheDao.kt` | Yeni `sizeByPrefix(prefix): Long` (`COALESCE(SUM(LENGTH(detailJson)),0)`) ve `countByPrefix(prefix): Int` sorguları. |
| Depo | `data/fandom/FandomRepository.kt` | Yeni `cacheStats(context): Pair<Long, Int>` (ölçüm) ve `clearCache(context)` (temizlik). |
| Bekçi | `core/storage/StorageJanitor.kt` | Yeni `Bucket.WIKI_CACHE` (`"wiki_cache"`, `clearable = true`), `clean()` ve `statsOf()` dalları, `statsOf` artık `suspend`. |
| Arayüz | `ui/screens/settings/StorageSettingsContent.kt` | Yeni kalem satırı: `Icons.Rounded.AutoStories` + yeni `KitsugiColors.AccentSky`. |
| Metin | `res/values/strings.xml`, `res/values-en/strings.xml` | `storage_bucket_wiki`, `storage_bucket_wiki_desc` (TR + EN). |

### Neden bu kararlar?
- **Mantıksal ölçüm:** SQLite dosyası silme sonrası küçülmediği için (VACUUM gerekir) kalem, diskteki dosya boyutu yerine **`detailJson` uzunlukları toplamını** raporlar. Böylece satırdaki sayı ile "Temizle" sonrası geri kazanım tutarlı olur.
- **Kullanıcı kararları korunur:** `FandomRepository.clearAll` (Ayarlar > Fandom Wiki → "Wiki önbelleğini temizle") eşleşme kararlarını da siler. Depolama ekranındaki kalem ise bilinçli olarak `clearCache` kullanır: yalnızca yeniden üretilebilir kayıtlar (`fandom:v1:` önekli satırlar) gider, "bu eşleşme doğru/yanlış" kararları yerinde kalır.
- **Önek zorunlu:** Aynı tablo (`persistent_details_cache`) medya detay önbelleğini de tutar (`KitsugiDetailClient`); bu yüzden ölçüm ve silme yalnızca `fandom:v1:` önekiyle sınırlıdır.
- **Otomatik bakım dokunulmadı:** 30 günden eski kayıtlar `StorageJanitor.pruneDatabase` / `MappingSyncWorker` ile zaten budanıyor; metin bu davranışı açıklar.

---

## 2) Uygulama Hakkında → Sürüm Notları kartı

### Sorun
`KitsugiUpdateRepository.checkForUpdate()` yalnızca **kurulu sürümden yeni** bir sürüm varsa `AppRelease` döner; aksi hâlde `null` döner ve `AppUpdateViewModel` "Uygulamanız güncel!" bildirimi ile `Idle` durumuna döner. Bu yüzden kurulu sürüm güncelken GitHub'daki son sürümün notları **hiçbir yerde** görünmüyordu.

### Çözüm

| Dosya | Değişiklik |
|---|---|
| `core/update/KitsugiUpdateRepository.kt` | Tekrar eden mantık ayrıştırıldı: `fetchLatestReleaseJson()`, `buildRelease(json, requireApk)` ve `selectApkAsset(json)`. Yeni `latestRelease(context, force)` (yeni sürüm koşulu aramaz, APK eki zorunlu değil) ve `isNewerThanInstalled(release)` eklendi. |
| `core/update/ReleaseNotesCache.kt` (yeni) | Son başarılı yanıtın **6 saatlik** disk önbelleği (SharedPreferences). GitHub kimliksiz istek sınırı saatte 60 olduğu için ekran açılışı ile güncelleme denetimi aynı uçta çakışmaz. |
| `ui/components/KitsugiUpdateDialog.kt` | `CleanReleaseNotesText` `internal` yapıldı: güncelleme penceresi ile Hakkında kartı aynı biçimlendirmeyi paylaşır. |
| `ui/screens/more/AboutScreen.kt` | Yeni `ReleaseNotesCard` + `ReleaseNotesUiState` (Loading/Unavailable/Failed/Ready). Kart; son sürüm rozetini, "Yeni sürüm hazır" / "Kurulu sürüm en güncel" durumunu, kurulu sürümü, yayın tarihini, notları (en fazla 300 dp, kendi içinde kaydırılabilir) ve "GitHub'da tüm sürümler" bağlantısını gösterir. |
| `res/values*/strings.xml` | 12 yeni `about_release_notes_*` dizesi (TR + EN, format yer tutucuları eşleşiyor). |

Davranış: ekran açılır → önbellek taze ise anında notlar; değilse ağdan çekilip önbelleğe yazılır. "Yenile" önbelleği atlar (`force = true`). Ağ hatasında kullanıcıya neden + "Yeniden dene" sunulur.

---

## 3) Wiki'de Orijinal/Çeviri geçişi (bilgi kutusu + açılır sayfa)

### Bulgu
`MediaWikiTabContent.kt` içinde dört `WikiTranslateButton` çağrısı var. Bunlardan **üçü** geçiş düğmesi olmadan çiziliyordu:

| Çağrı | Konum | Durum |
|---|---|---|
| `MediaWikiSummaryCard` | Wiki sekmesi seri özeti | Zaten geçişliydi. |
| `WikiItemSheet` başlığı | Açılır wiki sayfası açıklaması | **Yeni:** `itemKey(title)` anahtarıyla geçiş. |
| `ArticleInfoboxCard` | Açılır wiki sayfası bilgi kutusu | **Yeni:** `articleInfoboxKey(title)` anahtarıyla geçiş + "bilgi kutusunu kopyala". |
| `ArticleSectionRow` | Açılır wiki sayfası bölümleri | **Yeni:** `articleSectionKey(title, index)` anahtarıyla geçiş. |

### Ayrıntılar
- `ArticleInfoboxCard` yeni `showingOriginal` / `onToggleOriginal` parametrelerini alır; `infoboxHasTranslation` hem toplu çeviri işaretine (`item-info:<başlık>`) hem de alan çevirilerine (`item-if:<index>:<başlık>`) bakar. "Orijinal" seçiliyken alanlar `localizedInfoboxValue(field.value)` gösterir; çeviri bellekte kalır ve tekrar ağa gidilmez.
- Açıklama ve özet metni de aynı anahtarı izler: `if (descriptionShowingOriginal) description else (translatedDescription ?: description)`.
- `ArticleSectionRow`: `hasTranslation = !translated.isNullOrBlank()`, gövde `if (showingOriginal) null else translated` ile çizilir.
- **Wikipedia dahil:** Kaynak seçimi (`switchSource`) yalnızca `ref`'i değiştirdiği ve aynı bileşenler çizildiği için geçiş düğmesi Fandom ve Vikipedi (TR) kaynaklarında da çalışır; anahtarlar kaynak bazlı olduğundan durumlar karışmaz.
- **Bilgi kutusunu kopyala:** `ArticleInfoboxCard` araç satırı karakter wiki sekmesindeki düzenle eşitlendi: `[Orijinal/Çeviri] [çevir] [kopyala]`. Kopya metni yalnızca görünen alanları (`visibleCount`) alır, spoiler ile gizlenen alanları `[gizli]` ile maskeler, "Orijinal" modunda çeviri yerine wiki metnini yazar. `CopyIconButton` ve `copyWikiText` (karakter sekmesinde tanımlıydı) `internal` yapılarak paylaşıldı.
- Karakter wiki sekmesi (`CharacterWikiTabContent`) ve okuyucu (`FandomWikiReader`) zaten geçiş düğmelerine sahipti; bu iş kapsamında **dokunulmadı** (yalnızca paylaşılan yardımcıların görünürlüğü değişti).

---

## Değişen dosyalar (özet)

```
app/build.gradle.kts                                             appVersionName → 2.4.290
app/src/main/java/com/kitsugi/animelist/core/storage/StorageJanitor.kt
app/src/main/java/com/kitsugi/animelist/core/update/KitsugiUpdateRepository.kt
app/src/main/java/com/kitsugi/animelist/core/update/ReleaseNotesCache.kt        (yeni)
app/src/main/java/com/kitsugi/animelist/data/fandom/FandomRepository.kt
app/src/main/java/com/kitsugi/animelist/data/local/PersistentDetailCacheDao.kt
app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiUpdateDialog.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/CharacterWikiTabContent.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/MediaWikiTabContent.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/more/AboutScreen.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/settings/StorageSettingsContent.kt
app/src/main/java/com/kitsugi/animelist/ui/theme/KitsugiColors.kt
app/src/main/res/values/strings.xml · app/src/main/res/values-en/strings.xml
release_notes_v2.4.290.md                                        (yeni)
RELEASE_NOTES.md
```

## Doğrulama

- `python3 scripts/check_localization_resource_parity.py` → **"Localization resources are in sync: 7447 strings and 4 arrays"** (TR/EN anahtar ve format yer tutucu eşitliği).
- Room şeması **değişmedi** (yalnızca yeni sorgular eklendi); migrasyon gerekmez.
- Derleme doğrulaması GitHub Actions `android-build.yml` ile yapılır (yerel Android SDK yok).
