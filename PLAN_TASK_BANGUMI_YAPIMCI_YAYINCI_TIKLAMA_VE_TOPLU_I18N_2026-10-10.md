# PLAN_TASK — Bangumi Yapımcı/Yayıncı Çipleri Tıklama + Toplu Arayüz Dili (i18n)

**Tarih:** 2026-10-10 · **Dal:** `arena/48f9e7ca-kitsugi-beta` · **Commit:** `c448e4a`
**Kapsam:** Detay sayfasında Bangumi kaynaklı **Yapımcılar** ve **Yayıncı Ağlar** çiplerinin tıklanamaması + Bangumi akışlarının (stüdyo/yapımcı detay sayfası, kart etiketleri, sekmeler, galeri) uygulama dili yerine sert kodlu Türkçe görünmesi.
**Girdi:** Kullanıcı ekran görüntüleri (Steins;Gate detay — çipler pasif; Sun Television Co., Ltd. yapım listesi — TR etiketler).

---

## Plan (kök neden → çözüm eşlemesi)

| # | Kök neden | Kanıt | Çözüm |
|---|---|---|---|
| P1 | Bangumi infobox adları düz metin; kimlik yalnız enrich turunda çözülüyordu ve `STUDIO_LOOKUP_LIMIT = 8` idi — 15+ kurumlu sayfalarda (Steins;Gate: 1 stüdyo + 8 yapımcı + 6 yayıncı) yayıncılar limitten düşüp `id = 0` kalıyordu | `KitsugiBangumiDetailClient.withStudioPersonIds` | Limit **8 → 24**, toplu zaman aşımı 6sn → 10sn |
| P2 | Çip tıklanabilirlik kapısı `id > 0 || source != "bangumi"` idi; kimliksiz çip ölüydü | `KitsugiDetailComponents.KitsugiStudiosCard` | Kapı kaldırıldı: **adı dolu her çip tıklanır**, ad hedefe taşınır |
| P3 | `KitsugiStudioClient.fetchStudioDetail` bangumi kolunda `name` yok sayılıyordu | aynı dosya | `id <= 0` / boş sonuçta **adla çözümleme**: `KitsugiBangumiDetailClient.resolveCompanyPersonId(name)` → `/v0/search/persons` type=2 (公司); şirket yoksa type=1 (kişi — yapımcı şahıslar kendi yapım listesiyle açılır) |
| P4 | Kimliksiz sayfada "Yeniden Dene" çalışmıyordu (`studioId > 0` şartı) | `StudioDetailViewModel.retry` | `studioId > 0 || name != null` |
| P5 | Bölüm başlıkları, stüdyo sayfası metinleri, tür/sıralama çipleri, galeri kategorileri, medya türü ve izleme durumu etiketleri Kotlin içine sert kodlu Türkçe'ydi | 20+ dosya | Hepsi `strings.xml` (values=TR, values-en=EN) anahtarlarına taşındı; merkezi Composable yardımcılar: `MediaType.localizedLabel()`, `GalleryCategory.localizedLabel()`, `WatchStatus.localizedLabel()` (+ Composable olmayan bağlamlar için `plainLabel()` / `labelRes()`) |
| P6 | Veri katmanı rol/başlık yedekleri Türkçe sabitti ("Yapım Şirketi", "Başlıksız") | `KitsugiStudioClient` | `isTurkish()`'e göre ikili üretim (Studio / Production Company, Untitled, Unknown…) |

## Task'lar (tamamlandı)

- [x] T1 — `KitsugiDetailComponents`: çip tıklanabilirlik kapısı kaldırıldı (stüdyo/yapımcı/yayıncı), başlıklar `stringResource`
- [x] T2 — `KitsugiBangumiDetailClient`: `resolveCompanyPersonId()` public API + type=1 fallback + limit/zaman aşımı artırımı
- [x] T3 — `KitsugiStudioClient`: bangumi kolunda adla çözümleme; rol/başlık etiketleri dil duyarlı
- [x] T4 — `StudioDetailViewModel`: kimliksiz retry + hata metni `strings.xml`
- [x] T5 — `StudioDetailPage` / `StudioDetailComponents`: sayfanın TÜM metinleri dil dosyasına (Yapımlar (N), içerik sayacı, filtre/sıralama sheet'i, boş/hata durumları, hero pill'leri, cd'ler); `StudioTypeFilter`/`StudioSortOption` → `@StringRes`
- [x] T6 — `AddonFullScreenGridPage` + `AddonHomeParity`: eklenti "Tümünü Gör" sayfası aynı anahtarlara bağlandı
- [x] T7 — `FullScreenMediaGridPage` + `KitsugiMediaFilterBottomSheet`: keşfet tür (`KitsugiGenreItem`) ve sıralama (`KitsugiGridSortOption`) çipleri `@StringRes`
- [x] T8 — Detay sekmeleri (Bilgi…Bölümler) + Bilgi tabı etiketleri + Açıklama/İstatistik kartları → `strings.xml`
- [x] T9 — Galeri: `GalleryCategory` enum → `@StringRes` + `localizedLabel()`; "Tümü (N)" sayaçları
- [x] T10 — `WatchStatus` enum → `@StringRes`; 15 kullanım Composable/composable-olmayan ayrımıyla güncellendi
- [x] T11 — `values/strings.xml` + `values-en/strings.xml`: ~80 yeni anahtar, birebir senkron (3823/3823)

## Doğrulama

- Tüm `R.string.*` referansları iki dil dosyasıyla çapraz kontrol edildi (eksik anahtar yok).
- Sert kodlu Türkçe taraması: değiştirilen dosyalarda yalnız emoji/yorum kaldı.
- Sandbox'ta Gradle bağımlılık indirmesi kapalı olduğundan APK derlenemedi → cihazda doğrulama önerilir:
  1) Steins;Gate detayında yapımcı/yayıncı çipine tıkla → Bangumi kurum sayfası açılmalı;
  2) Dili EN yap → aynı sayfa + stüdyo sayfası + kart rozetleri İngilizce görünmeli.
