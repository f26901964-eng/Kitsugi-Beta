# PLAN / TASK — Birleşik Wiki Önizleme Kartı (WikiPagePreviewCard) — v2.4.291 (2026-10-16)

## 1. Sorun (Kök Neden)
Daha önce "yapıldı" diye duyurulan birleşik wiki önizleme kartı kodda **hiç yoktu**:
- `WikiPagePreviewCard` adında bir bileşen bulunmuyordu.
- İki farklı kart ailesi yan yana yaşıyordu:
  - `WikiRelatedPageCard` (FandomWikiReader.kt) — `160 dp` genişlik, değişken yükseklik.
  - `WikiRelatedPageDetailCard` / `WikiRelatedEntityDetailCard` (FandomInlineProfile.kt) — çağrı yerlerinde `Modifier.width(300.dp)`, tüm infobox alanlarını satır içi açtığı için yüksekliği sınırsız/değişken.
- Hiçbir wiki LazyRow şeridinde `rememberLazyListState()` + snap fling yoktu; kartlar hizalanmıyor, kaydırma rastgele noktada duruyordu.

## 2. Hedef / Kabul Kriterleri
- [x] Karakter ve yapım sayfalarındaki oyuncu kadrosu (cast), seslendirenler (voice actors), ekip üyeleri (staff/crew), müzik/şarkı listeleri ve ilgili wiki sayfaları aynı `280 × 144 dp` tek tip modern kartla (`WikiPagePreviewCard`) gösterilir.
- [x] Karta dokununca tarayıcıya atlamak yerine doğrudan uygulama içi Fandom veya Vikipedi okuyucusu (`FandomWikiReaderSheet`) açılır; kart bileşeni `ACTION_VIEW` çalıştırmaz.
- [x] Tüm yatay wiki şeritleri sola hizalıdır; kaydırma bırakıldığında sıradaki karta pürüzsüzce oturur (`WikiPreviewStripRow` + `KitsugiScrollDefaults.rememberRowSnapFling`, `SnapPosition.Start`).
- [x] Karakter ve yapım wiki sekmelerinde "İlgili Wiki Sayfaları" şeridi listelenir; Vikipedi kaynağında başlık otomatik "İlgili maddeler" olur, kart kaynak rozetleri (Fandom / Vikipedi / Wikipedia) kaynağa göre güncellenir.
- [x] Kritik hata düzeltmeleri: `CharacterWikiTabContent` içindeki `page.pageTitle` kullanımı, şeritlerdeki `rememberLazyListState()` eksiklikleri ve `MediaWikiTabContent` içindeki `$wikiId` kapsamı (ilgili sayfalar şeridi artık etkin kaynağın `wikiId`sini açıkça parametre alır).

## 3. Değişen Dosyalar (bu zip'in içeriği)
| Dosya | Değişiklik |
| --- | --- |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/WikiPagePreviewCard.kt` | **YENİ.** Birleşik kart (280×144) + `WikiPreviewStripRow` (sola hizalı, snap-scroll). `FandomRelatedPage` ve `FandomWikiLink` (cast/VA/staff/müzik) aşırı yüklemeleri; kaynak rozeti kartın kendi `wikiId`sinden üretilir. |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/CharacterWikiTabContent.kt` | `CharacterRelatedStrip`, rol grupları (`WikiRelatedEntitiesCard`) ve bölüm içi `linkedEntities`/`links` şeritleri yeni karta geçti; `accentColor`/`sourceWikiId`, `WikiSectionRow` → `SectionBody` zinciriyle taşındı; kullanılmayan LazyRow importları temizlendi. |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/MediaWikiTabContent.kt` | `RelatedPagesStrip` yeni karta geçti ve `ready.ref.wikiId` açıkça geçirilir (`$wikiId` kapsam düzeltmesi); kullanılmayan `lazy.items` importu kaldırıldı. |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomWikiReader.kt` | Okuyucu içi `ReaderRelatedCard` yeni karta geçti; eski `WikiRelatedPageCard` (160 dp) ve `PagePlaceholder` silindi; kullanılmayan `CircleShape` importu kaldırıldı. |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomInlineProfile.kt` | **SİLİNDİ.** Eski 300 dp detay kartları (`WikiRelatedPageDetailCard`, `WikiRelatedEntityDetailCard`, `WikiInlineProfileCard`) artık yok; zengin infobox içeriği kart yerine okuyucuda açılır. Silme, ekteki `.patch` dosyasında da kayıtlıdır. |
| `app/build.gradle.kts` | Sürüm `2.4.290` → `2.4.291`. |
| `release_notes_v2.4.291.md` | **YENİ.** TR + EN sürüm notları. |
| `PLAN_TASK_BIRLESIK_WIKI_ONIZLEME_KARTI_2026-10-16.md` | **YENİ.** Bu dosya. |

## 4. Uygulanan Akış Notları
- Çağrı zincirleri doğrulandı: Karakter (portre + yatay), Ekip (`CharacterWikiTabSlot(viewModel)`), Yapım (`MediaWikiTabSlot`), gömülü kart (`MediaWikiSectionCard` → Manga/TV detayları) — hepsinde `onOpenWikiPage/onOpenWikiLink → ViewModel.openReader → FandomWikiReaderSheet`.
- Kalan `openExternal` kullanımları yalnızca açık etiketli "tarayıcıda aç" düğmeleri ve geri çağrı yoksa son-çare dallarıdır; üretim akışlarında kart tıklaması tarayıcıya düşmez.
- Kart içindeki görsele ayrı tıklama kaldırıldı: kartın tamamı tek tıklama hedefidir ve okuyucuyu açar (okuyucu içinde galeri zaten var).

## 5. Doğrulama
- [x] `grep`: Eski kartlara (`WikiRelatedPageCard`, `WikiRelatedPageDetailCard`, `WikiRelatedEntityDetailCard`, `300.dp`, `160.dp`) wiki paketi içinde referans kalmadı.
- [x] `grep`: 6 şeridin tamamı `WikiPreviewStripRow` kullanıyor (karakter ilgili, rol grupları, bölüm içi ×2, medya ilgili, okuyucu ilgili).
- [x] Tam `git diff` gözden geçirildi; değiştirilen 4 Kotlin dosyasında parantez/süslü parantez dengesi kontrol edildi.
- [ ] **Kullanıcı/CI doğrulaması gerekli:** Bu çalışma ortamında JDK/Android SDK olmadığı için Gradle derlemesi çalıştırılamadı. Kabul öncesi: `./gradlew :app:compileFossDebugKotlin` ve cihazda karakter + yapım wiki sekmelerinde kart/snap/okuyucu akışının gözle kontrolü.

## 6. Paketleme
Bu plan, değişen dosyalar ve `v2.4.291_wiki_preview.patch` (silinen dosya dahil tam diff) ile birlikte `Kitsugi_Beta_v2.4.291_Birlesik_Wiki_Onizleme_Karti.zip` olarak paketlenip Google Drive'a yüklenir.
