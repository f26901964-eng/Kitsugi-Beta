# PLAN & TASK — Listeye Ekleme Otomasyonları (Otomatik Tarih + Konfor Paketi)

**Tarih:** 2026-10-20
**Branch:** `arena/fdc78627-kitsugi-beta`
**Hedef sürüm:** v2.4.314
**İstek:** "Hangi kaynaktan listeye bir şey eklersek, 'İzleniyor' seçildiği anda otomatik olarak o günün
tarihi başlangıç tarihi olarak atılmalı. Buna benzer ekstrem kullanışlı özellikler de eklenmeli."

---

## 1. Mevcut durum (tespit)

| # | Bulgu | Konum |
|---|-------|-------|
| M1 | Yeni kayıt **her kaynaktan** `status = Planned`, `startDate = null` olarak giriyor. | `AppViewModel.insertApiSelection()` |
| M2 | Otomatik tarih **yalnızca kaydetme anında**, **gizlice** doluyor; kullanıcı "İzleniyor"a basınca satırda hiçbir şey görünmüyor. | `AppRoot.onConfirmEdit` (`resolvedStartDate` / `resolvedEndDate`) |
| M3 | İkinci, **farklı** bir kural daha var: bölüm artırımında `Planned → Watching` geçişi + tarih. | `AppRoot.incrementEntryProgress` |
| M3b | Üçüncü kopya: Listem ekranındaki `+1` kısayolu da **kendi** kuralını taşıyor. | `MyListViewModel.incrementEntryProgress` |
| M3c | Dördüncü yol: favoriye alma, listede yoksa kaydı doğrudan `Planned` olarak yaratıyor. | `AppRootDetailPages.onToggleFavoriteClick` |
| M4 | `Tamamlandı` seçilince ilerleme otomatik tamamlanmıyor (AniList/MAL paritesi yok). | — |
| M5 | Tarih satırlarında "Bugün / Dün / Temizle" hızlı kısayolu yok; her seferinde `DatePickerDialog`. | `KitsugiEditMediaSheet` |
| M6 | Bitiş tarihi başlangıçtan önce seçilebiliyor, hiçbir uyarı yok. | `KitsugiEditMediaSheet` |
| M7 | Hızlı ekleme (`+` rozeti) her zaman `Planned` ekliyor; varsayılan durum tercihi yok. | `AppViewModel.insertApiSelection` |
| M8 | `KitsugiAniListEditorSheet` / `KitsugiMalEditorSheet` / `KitsugiManualMediaEntryEditorSheet` **çağrılmıyor** (ölü kod). Tek gerçek düzenleyici `KitsugiEditMediaSheet`. | — |

**Kök sorun:** durum → tarih/ilerleme kuralları **4 ayrı yere** dağılmış, kurallar birbiriyle
uyumsuz (ör. `Completed` + eksik başlangıç tarihi yalnızca iki yolda dolduruluyordu) ve
hiçbiri kullanıcıya görünmüyor.

---

## 2. Çözüm mimarisi

Tek bir kural motoru: **`model/EntryAutoRules.kt`**. Tüm yollar (canlı düzenleyici, kaydetme,
hızlı ekleme, bölüm artırımı) bu motoru çağırır. Davranış kullanıcı ayarıyla kapatılabilir.

### 2.1 Kural tablosu

| Durum | Başlangıç tarihi | Bitiş tarihi | İlerleme |
|-------|------------------|--------------|----------|
| `Watching` / `Repeating` | boşsa → **bugün** | dokunulmaz | dokunulmaz |
| `Completed` | boşsa → **bugün** | boşsa → **bugün** | durum **değiştiyse** → `total` |
| `Planned` / `Paused` / `Dropped` | dokunulmaz | dokunulmaz | dokunulmaz |

> **Dolu tarihler asla üzerine yazılmaz**; geri alma (Planned'a dönmek) tarihleri silmez.
>
> **`statusChanged` koruması:** ilerlemenin otomatik tamamlanması yalnızca durum gerçekten
> değiştiğinde tetiklenir. Zaten `Completed` olan bir kaydın sadece puanı düzenlenirse
> ilerlemeye dokunulmaz — aksi hâlde kullanıcının bilerek yarım bıraktığı ilerleme, görünür
> bir neden olmadan son bölüme zıplardı. Panel açılışında da `statusChanged = false`
> geçilir; yalnızca **eksik tarihler** doldurulur.

### 2.2 Yeni ayarlar (Ayarlar → Tercihler → Liste)

| Anahtar | Varsayılan | Açıklama |
|---------|-----------|----------|
| `auto_date_on_status_change` | `true` | Duruma göre tarihi otomatik doldur |
| `auto_progress_on_complete` | `true` | "Tamamlandı"da ilerlemeyi tamamla |
| `default_add_status` | `PLANNED` | Hızlı eklemede varsayılan durum (`PLANNED`/`WATCHING`/`COMPLETED`) |

`KitsugiContentPrefs` üzerinden global okunur (mevcut `scoreStep` deseni) → `insertApiSelection`
gibi Compose dışı bağlamlarda da çalışır.

---

## 3. İş listesi

- [x] **T1** `model/EntryAutoRules.kt` — saf kural motoru (`today()`, `yesterday()`, `resolveOnStatusChange()`)
- [x] **T2** Ayar katmanı: `AppSettings` → `SettingsDataStore` (keys + okuma + setter) → `KitsugiContentPrefs`
- [x] **T3** Ayar arayüzü: `GeneralSettings` + `AppRootSettingsExtras` + `SettingsScreen` + `KitsugiPreferencesSettingsDialog` (Liste sekmesi)
- [x] **T4** `KitsugiEditMediaSheet`: durum hapına basınca **anında** tarih/ilerleme doldurma
- [x] **T5** `KitsugiEditMediaSheet`: tarih satırlarına **Bugün / Dün / Temizle** hızlı çipleri
- [x] **T6** `KitsugiEditMediaSheet`: bitiş < başlangıç uyarısı
- [x] **T7** `AppRoot.onConfirmEdit` + `AppRoot.incrementEntryProgress` → ortak motor
- [x] **T7b** `MyListViewModel.incrementEntryProgress` + `AppRootDetailPages` favori ekleme → ortak motor
- [x] **T8** `AppViewModel.insertApiSelection` → varsayılan durum + otomatik tarih
- [x] **T9** i18n: `values/strings.xml` + `values-en/strings.xml`
- [x] **T10** Derleme doğrulaması + sürüm notları + PR

---

## 4. Kapsam dışı (bilinçli)

- **"Geri al" (undo) snackbar'ı** — snackbar altyapısı aksiyon desteklemiyor; ayrı bir iş.
- **Elle tarih girilince durumu otomatik `Watching` yapma** — sürpriz davranış riski.
- **Ölü düzenleyiciler** (`KitsugiAniListEditorSheet`, `KitsugiMalEditorSheet`,
  `KitsugiManualMediaEntryEditorSheet`) — çağrılmadıkları için dokunulmadı; ayrı bir temizlik işi.
