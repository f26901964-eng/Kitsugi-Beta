# PLAN & TASK: İngilizce Dil Seçeneğinin Tamamlanması (Hardcoded TR Metinlerin Localize Edilmesi)

**Tarih:** 2026-10-10
**Durum:** Devam ediyor (Batch 1 ve 2 tamamlandı)
**İlgili sürüm:** v2.4.232+

---

## 1. Problem

Kullanıcı bildirimine göre: uygulamada **İngilizce dil seçeneği seçildiğinde bile bazı kısımlar hâlâ Türkçe** görünüyor. Özellikle uygulamanın kendi UI metinlerinde (butonlar, dialog başlıkları, ayar açıklamaları, profil sekmeleri vb.) sorun yaşanıyor.

## 2. Teşhis (Kök Neden Analizi)

### Kontrol edilen ve SAĞLIKLI bulunanlar
- `app/src/main/res/values-en/strings.xml` — **3748 anahtar**, `values/strings.xml` ile tam parite. Eksik anahtar YOK.
- Locale mekanizması — `MainActivity.attachBaseContext()` + `KitsugiApplication` üzerinden `app_language` tercihi düzgün uygulanıyor.

### Asıl kök neden
**~1007 benzersiz Türkçe UI metni, Kotlin kaynak kodunun içine doğrudan gömülmüş** (hardcoded):
- 244 dosyada, toplam **1694 kullanım yeri**
- `Text("Kapat")`, `title = "AniList Hesabını Bağla"`, `contentDescription = "..."` gibi kalıplar
- Bu metinler `strings.xml`'e hiç taşınmamış olduğu için dil İngilizceye çevrilse bile ekranda Türkçe kalıyor

### En çok etkilenen dosyalar (kullanım sayısına göre)
1. `AccountSettingsSubPages.kt` (32)
2. `KitsugiAccountConnectionsDialog.kt` (28)
3. `MangaExtensionTab.kt` (27)
4. `DownloadsScreen.kt` (25)
5. `KitsugiShikimoriLoginDialog.kt` (21)
6. `ShikimoriProfileContent.kt` / `KitsugiAccountContent.kt` / `KitsugiBangumiLoginDialog.kt` / `KitsugiApiSearchDialog.kt` (20'şer)
7. `KitsugiEditMediaSheet.kt` (19) ve devamı...

## 3. Çözüm Stratejisi

Her gömülü metin için:
1. `values/strings.xml` → Türkçe değeriyle yeni `<string>` kaynağı
2. `values-en/strings.xml` → İngilizce çevirisiyle aynı anahtarla kayıt
3. Kodda `Text("...")` / `param = "..."` → `stringResource(R.string.anahtar[, arg])`

**Kurallar:**
- Interpolasyon: `"Bölüm $epNum"` → XML: `Bölüm %1$s`, kod: `stringResource(R.string.x, epNum)`
- `stringResource` sadece @Composable bağlamında kullanılır; notification builder / veri sınıfı gibi non-composable bağlamlar ayrı ele alınır (`context.getString(...)`)
- Anahtar üretimi: Türkçe metnin ASCII slug'i + çakışma durumunda 6 haneli hash
- XML escaping: `'` → `\'`, `&` → `&amp;`, argümanlı stringlerde `%` → `%%`

## 4. Batch 1 — Tamamlandı ✅ (commit `f25818b`)

- **328 yeni string kaynağı** (TR + EN) eklendi → her iki dosya da 4076 anahtar
- **911 kullanım yeri** patch'lendi (185 Kotlin dosyası)
- Kapsam: genel butonlar (Kapat→Close, İptal→Cancel, Geri→Back, Kaydet→Save, Paylaş→Share...), hesap bağlantıları diyalogları, ayar alt sayfaları, profil sekmeleri, oynatıcı kontrolleri, indirmeler, keşfet rafları

### Doğrulama sonuçları (hepsi geçti)
- XML dosyaları well-formed, TR/EN anahtar paritesi: 4076/4076
- Kodda kullanılan 1542 referansın tamamı çözümleniyor (0 eksik)
- Placeholder uyumsuzluğu: 0
- `stringResource` hiçbir non-composable blok içinde değil
- Duplicate import yok

## 4b. Batch 2 — Tamamlandı ✅ (2026-10-11)

**Kapsam:** Kullanıcı arayüzünde fiilen görünen TÜM sabit Türkçe metinler + Türkçeye özgü harf
içermediği için ilk taramada görünmeyen aksansız metinler ("Sil", "Kopyala", "Tamam", "Toplam" ...).

**Yapılanlar**
- **825 yeni string kaynağı** (TR + EN) eklendi; 7 yinelenen anahtar (aynı metnin Batch-1 kopyası) silindi.
  Her iki dil dosyası da **5052 anahtar** ve tam parite.
- **1.685 kod noktası** kaynağa bağlandı: 891 `stringResource(...)` + 794 `KitsugiStrings.get(...)`.
- **Yeni altyapı:** `utils/KitsugiStrings.kt` — Composable olmayan bağlamlarda (ViewModel, servis,
  Worker, veri katmanı) metin çözer. `KitsugiApplication.attachBaseContext` içinde `install` edilir;
  çözümleme sırası: aktif Activity kaynakları → uygulama kaynakları → `attachBaseContext` bağlamı.
- İçe aktarma etiketleri gibi **görünen ama karşılaştırmada kullanılan** metinler (ör.
  `MediaEntryBackup` içindeki "…'ten içe aktarıldı" kontrolü) aynı kaynaktan okunacak şekilde bağlandı.

**Doğrulama (hepsi geçti)**
- Kodda kullanılan **3.011 `R.string` referansının tamamı** çözümleniyor (0 eksik).
- TR/EN anahtar + placeholder paritesi: `scripts/check_localization_resource_parity.py` → yeşil.
- XML dosyaları well-formed; `%` kaçışları (`%%`, `%%%2$s`) ve `\n`/`\'`/`\"` kaçışları korundu.
- `stringResource` çağrılarının Composable olmayan bloklarda (remember / LaunchedEffect / onClick /
  withContext) kullanılmadığı özel bir tarayıcı ile doğrulandı → 0 ihlal.

**Özellikle dokunulmayanlar (bilinçli)**
- Geliştirici tanı/log içerikleri (crash log dosyaları, logcat dökümleri, regex desenleri).
- Türkçe→diğer diller **sözlük tabloları** (`KitsugiTranslations`, `BangumiTagDictionary`,
  `BangumiRoleDictionary`, `SearchTranslation`, `KitsugiTranslateUtils`).
- Yalnızca karşılaştırma amaçlı sabitler (`"Bölüm"` dosya adı kontrolü, `"türkçe"` dil adı eşleşmesi).

---

## 5. Kalan İşler

### Batch 3+ (sonraki turlar)
- **Veri/üretim katmanındaki kullanıcı mesajları** (UI sınıflandırması dışında kalan ~2.238 kullanım):
  senkronizasyon raporları, içe aktarma ilerleme metinleri, hata/uyarı cümleleri
  (`AuthViewModel`, `*SyncManager`, `CrossSyncReportFormatter`, `NotificationDiagnostics` ...).
  Bunların bir kısmı `KitsugiStrings.get(...)` ile doğrudan kaynağa bağlanabilir.
- Yazılım geliştirici tanı metinleri (log dosyası içerikleri) bilinçli olarak Türkçe bırakıldı;
  istenirse sonraki turda İngilizceye çevrilebilir.

### Non-composable bağlamlar (103 kullanım) — ayrı strateji
- **Notification builder'ları** (`AnimeDownloadService`, `AiringNotificationWorker`, `KeepAliveService`, `PlayerMediaSessionHelper`): `context.getString(R.string.x)` gerektirir
- **Veri sınıfı etiketleri** (`AniListUserOptions.kt`, `StartTab.kt`): gösterim anında `@StringRes` anahtarına map edilmeli
- **Yönetici sınıfları** (`BangumiAuthManager`, `BangumiImportManager`, `AuthViewModel`): hata mesajları — gösterim yerine göre `context.getString` veya mevcut kaynak kullanımı

## 6. Riskler ve Notlar

- Sandbox ortamında Android SDK bulunmadığı için derleme testi yapılamadı; kullanıcı tarafında bir build alınması önerilir
- Script tabanlı toplu işlem sonrası satır numaraları kaydığı için, işlem her seferinde taze extraction ile tek seferde uygulanmalı (aksi halde yanlış satırlara yama riski)
- Çeviri kalitesi: bağlam korunarak çevrildi; özel isimler (AniList, Simkl, Kitsu, Shikimori, Bangumi, platform adları) olduğu gibi bırakıldı

## 7. Dosya Envanteri (Batch 1)

- `app/src/main/res/values/strings.xml` (+328 kayıt)
- `app/src/main/res/values-en/strings.xml` (+328 kayıt)
- 185 Kotlin dosyasında `stringResource()` geçişi (tam liste commit `f25818b` içinde)
