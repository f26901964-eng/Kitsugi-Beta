# PLAN & TASK: İngilizce Dil Seçeneğinin Tamamlanması (Hardcoded TR Metinlerin Localize Edilmesi)

**Tarih:** 2026-10-10
**Durum:** Devam ediyor (Batch 1/4 tamamlandı)
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

## 5. Kalan İşler

### Batch 2+ (sonraki turlar)
- **~679 benzersiz metin / ~782 kullanım yeri** — çoğunlukla:
  - Detay sayfaları (KitsugiDetailInfoSection, karakter/stüdyo sayfaları)
  - Manga okuyucu ekranları
  - TV arayüzü (TvClassicHomeContent, TvModernHomeContent, TV arama/stream)
  - Eklenti yönetimi (plugin dialogları, repo kartları)
  - Arama ve filtre bileşenleri
  - Hata/boş durum mesajları

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
