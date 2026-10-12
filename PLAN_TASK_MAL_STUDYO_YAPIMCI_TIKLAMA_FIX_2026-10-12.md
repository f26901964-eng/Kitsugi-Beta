# PLAN / TASK — MAL Manga Detay: "Stüdyolar/Yapımcılar" Yanlış Veri + Tıklanınca Açılmama

**Tarih:** 2026-10-12
**Dal:** `arena/b37a1fdf-kitsugi-beta`
**Şikâyet:** "Bu stüdyo yapımcı MAL'de açılmıyor… artık uygulamam açılmayan, verisi gelmeyen bir kısım istemiyorum."
Ekran görüntüsü: Grand Blue (manga) MAL detay sayfası → **Stüdyolar** kartında `Kenji Inoue`, `Kimitake Yoshioka`
(yazarlar!) ve **Yapımcılar** altında `good! Afternoon` (dergi!) — çiplere tıklanınca boş/hata stüdyo sayfası açılıyordu.
Aynı ekranda **Durum** değeri çevrilmemiş `currently_publishing` olarak görünüyordu.

---

## 0. Kök neden (dosya ve satır düzeyinde)

| # | Kaynak | Bulgu |
|---|---|---|
| 1 | `KitsugiMalDetailClient` — Jikan parse (manga) | `authors` → **`studios`** listesine `role=STUDIO` olarak konuyordu; `serializations` → **`producers`** listesine `role=PRODUCER` |
| 2 | `KitsugiMalDetailClient.parseOfficialMalDetailJson` (manga) | Aynı yanlış eşleme: `authors[].node` → studios, `serialization[].node` → producers |
| 3 | `ApiDetailTabContents` / `EntryDetailTabContents` | Her çip `StudioDetailPage`'e gidiyordu → `KitsugiStudioClient.fetchJikanStudioDetail` Jikan **`/producers/{id}`** çağırıyor |
| 4 | `KitsugiStudioClient` | Kişi (person) ID'si `/producers`'ta yok; dergi ID'si ya 404 ya da **başka bir şirkete** denk geliyor → `namesMatch` reddediyor → ad kurtarma (producers/anilist/tmdb) da başarısız → **`StudioDetailState.Error` / boş sayfa** |
| 5 | `KitsugiStaffClient.fetchStaffDetail` | `if (staffId <= 0) return null` guard'ı kimliksiz kişilerin ad ile çözümlenmesini tamamen engelliyordu |
| 6 | `parseOfficialMalDetailJson` status `when` | `currently_publishing` / `not_yet_published` eşlenmemiş → ham `currently_publishing` ekranda görünüyordu |
| 7 | `SearchViewModel.applyDetailFilterRequest` | `DetailSearchFilterRequest.magazineId/producerId/studioId/networkId` alanları **tanımlı ama hiç taşınmıryordu** (eksik feature); `supportsDetailFilter` ID facetlerini "desteklenmiyor" sayıp tüm motorları kapatıyordu |
| 8 | `SearchUiState.activeFilterCount` (ALL) | ID facetlerini saymadığı için boş sorgu + dergi filtresi aramayı **hiç tetiklemezdi** |

---

## 1. Yapılan düzeltmeler

### 1.1 Veri katmanı — doğru roller
- `StudioRole` enum'una **`AUTHOR`** eklendi.
- `KitsugiMalDetailClient` (iki parse yolu):
  - `authors` → `studios` listesinde **`role=AUTHOR`** (artık "Yazarlar" başlığı altında).
  - `serializations` / `serialization` → **`role=MAGAZINE`** ve ayrı `KitsugiMediaDetail.serializations` listesine.
  - Jikan `publishers` artık parse ediliyor → **`role=PUBLISHER`** (`/producers/{id}` kimlik uzayında — Kodansha vb. için stüdyo sayfası **çalışır**).
- Durum eşlemesi: `currently_publishing → Publishing`, `not_yet_published → Not yet published`;
  `statusMap`'e `Currently Publishing` / `Not yet published` TR karşılıkları eklendi.
  Artık "Durum: **Yayımlanıyor**" görünecek.

### 1.2 UI — rol duyarlı bölüm başlıkları (`KitsugiStudiosCard`)
- Grup başlıkları role göre: **Yazarlar** (AUTHOR), **Yayıncılar** (PUBLISHER), **Dergiler** (MAGAZINE),
  aksi hâlde Stüdyolar / Yapımcılar / Yayıncı Ağlar. Yeni string'ler TR+EN.
- `networks` yuvası manga için `detail.serializations` ile dolduruluyor (Bangumi'nin yıllardır ürettiği
  ama hiç gösterilmeyen `serializations` verisi de artık ekrana geliyor).

### 1.3 Tıklama yönlendirmesi (rol bazlı) — her çip VERİYLE açılıyor
| Çip | Hedef | Veri kaynağı |
|---|---|---|
| Yazar (AUTHOR) | **Kişi (Staff) detay sayfası** | `Jikan /people/{id}/full` — biyografi, doğum tarihi, **manga/anime yapımları** (yeni: `manga` dizisi parse ediliyor) |
| Dergi (MAGAZINE) | **Dergiye göre filtrelenmiş arama** | `Jikan /manga?magazines={id}` — derginin tüm mangaları |
| Yayıncı/Stüdyo/Yapımcı (PUBLISHER/STUDIO/PRODUCER) | Stüdyo detay sayfası | `Jikan /producers/{id}` (eski davranış, publisher kimlik uzayıyla uyumlu) |

- `ApiDetailOverviewTab` / `EntryDetailOverviewTab`: `onStaffClick` + `onMagazineClick` parametreleri;
  `ApiResultDetailPage` / `MediaEntryDetailPage` → `AppRootDetailPages` üzerinden StaffDetail navigasyonu
  ve `triggerDetailSearchFilter(magazineId, magazineName)` bağlandı.
- Dergi çipi yalnızca MAL/Jikan kimlik uzayında ID taşır; Bangumi şirket ID'si yanlışlıkla
  `magazines=` parametresine girmesin diye kaynak kontrolü var (id yoksa ad ile arama).

### 1.4 Arama tarafı — `DetailSearchFilterRequest` ID facetleri tamamlandı
- `supportsDetailFilter`: `magazineId/producerId/studioId` → yalnız **MAL** motoru,
  `networkId` → yalnız **TMDB**; uygulanamayan motorlar filtresiz sonuç döndürmesin diye kapanır.
- `applyDetailFilterRequest`: `magazineId` → `MalSpecificFilters.magazineId`,
  `producerId/studioId` → `MalSpecificFilters.producerId`, `networkId` → `TmdbSpecificFilters.networkId`.
- Kimliksiz dergi kayıtları (id≤0) için güvenli düşüş: filtreyi ada göre metin aramasına indirger
  (**boş/ölü ekran asla kalmaz**).
- `SearchUiState.activeFilterCount` (ALL): ID facetleri artık sayılıyor — boş sorguyla da arama tetiklenir.

### 1.5 Kişi sayfası sağlamlığı (`KitsugiStaffClient` / `StaffDetailViewModel`)
- `staffId <= 0` iken ad → `Jikan /people?q=` ile kimlik çözümlenir; çözümlenemezse sayfa hiç açılmaz
  (hata/boş sayfa yerine). `retry()` / `forceRefresh()` de ad tabanlı kişilerde çalışır hâle geldi.
- Kişinin **manga yapımları** da "Yapımları" listesinde gösteriliyor (yazar/çizerler için kritik).

---

## 2. Doğrulama

- 14 dosyada parantez/delta analizi: HEAD ile birebir aynı (0 regresyon).
- `strings.xml` / `strings-en.xml` XML doğrulaması geçti.
- Tüm yeni çağrılar tek çağrı noktası (named args) — derleme kırığı riski yok denecek kadar düşük.
  (Sandbox'ta Android SDK/JDK yok; tam Gradle derlemesi yerine statik doğrulama yapıldı.)
- Uçtan uca davranış (Grand Blue senaryosu):
  1. "Yazarlar: Kenji Inoue / Kimitake Yoshioka" → tıkla → **Jikan kişi sayfası** (eserler listeli).
  2. "Dergiler: good! Afternoon" → tıkla → **Arama: dergiye ait mangalar** (`magazines=8`).
  3. "Durum: Yayımlanıyor" (ham `currently_publishing` yok).

## 3. Kapsam dışı / not
- TV arayüzünün kendi stüdyo/ekip akışı ayrıdır (dokunulmadı); telefon/tablet detay sayfaları kapsandı.
- Bangumi manga "Yazarlar" listesi hâlâ Bangumi kaynaklı detaylarda ayrı bir iş (infobox `作者`) —
  bu görev MAL/Jikan veri yolunu düzeltti; Bangumi için de `serializations` artık görünür.
