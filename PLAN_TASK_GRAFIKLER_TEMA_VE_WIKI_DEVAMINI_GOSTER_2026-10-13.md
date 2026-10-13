# PLAN/TASK — "Grafikler" sekmesi tema uyumu + Wiki "Evren rehberi" devamını göster

Tarih: 2026-10-13
Durum: **Kodlandı.** Derleme + cihaz testi kullanıcıda (sandbox'da JDK/Android SDK yok).
Ekran görüntüleri: `uploads/image-1.png` (Grafikler), `uploads/image-2.png` (Evren rehberi).

## Kullanıcı geri bildirimi

1. **Grafikler sekmesi çok kötü görünüyor**, uygulamanın temasıyla uyumlu değil:
   ham "kutu kutu" satırlar, neon dolgulu durum pilleri (siyah yazılı), sabit
   gökkuşağı renkli puan çubukları, sağdan taşan/kayan lejant (`10.803 Ara…`).
2. **Evren rehberi bölümlerinin (Organizasyonlar / Yetenekler / Mekânlar / Terimler)
   "devamını göster" düğmesi ve mantığı olmalı** — çipler sayfayı aşağı doğru
   sonsuz uzatıyor, kategoride daha fazlası varsa da getirilmiyor.

## 1) Grafikler sekmesi yeniden tasarlandı (`StatsTab.kt`)

Tek bir görsel dil: **kart (18 dp radius, `Surface` + %25 `Border`) → vurgu renginde
ikon kutusu (34 dp) → başlık + alt bilgi satırı**. Tema gradyanına duyarlı
`gradient.background/border/Text/Icon` kullanıldığı için açık/koyu ve gradyan
temalarda aynı görünüm korunur.

### Sıralama
- Satırlar tek kartta toplanır; arada ince ayraç (52 dp iç boşlukla hizalı).
- **Sıra rozeti**: 40 dp daire, popülerlik → `AccentRed`, puan → `AccentOrange`
  (14 dp ikon kutusu ve `#13` etiketi aynı tonda).
- Alt satırda "Popülerlik" / "Puan" etiketi + kalp/yıldız ikonu (eskiden yalnızca ikon vardı).
- "Tüm Zamanların / 2012 Yaz Sezonu …" çeviri mantığı aynen korundu.

### Durum Dağılımı
- Yatay kaydırılan **neon piller kaldırıldı** (taşma sorununun kaynağı).
- Yerine: 12 dp yığın gösterge çubuğu (segmentler arası 2 dp boşluk) +
  **saran lejant çipleri** (`FlowRow`): renk noktası + etiket + tam sayı + **yüzde**.
- Renkler uygulamanın kendi paletinden: izleniyor `#34D399`, tamamlanan `#60A5FA`,
  planlanan `#818CF8`, bırakılan `#FB7185`, ara verilen `#94A3B8`.
- Bilinmeyen durumlar için tüm etiketler artık TR **ve** EN (`detail_stats_status_*`).

### Puan Dağılımı
- **Sabit gökkuşağı paleti kaldırıldı**; çubuklar tema vurgusundan türetiliyor:
  `lerp(accent %45, accent, oran)` — en yüksek puan tam vurgu renginde.
- 10 kolon kartı eşit paylaşıyor (`weight(1f)`), **yatay kaydırma yok**; arkada
  yumuşak ray (track), değer üstte kısa biçimde (`6,7B` / `81B` / `1,2Mn` TR,
  `6.7K` / `81K` / `1.2M` EN), altta puan.
- Başlık altında **toplam oy + ortalama puan** (`725.516 oy · Ort. 81,5`).
- Yüzde TR yazımında başa gelir (`%81,2`), EN'de sona (`81.2%`).

### İskelet
- `DetailLoadingSkeleton` (Grafikler) yeni düzene göre güncellendi: sıralama
  satırları, gösterge çubuğu + lejant çipleri, 10 puan kolonu. Yükleme → içerik
  geçişinde düzen zıplaması yok.

## 2) Evren rehberi: "Devamını göster" (kategori sayfalaması)

Eskiden her grup için **tek istek, 14 çip** çekiliyordu ve hepsi birden basılıyordu.

**Veri katmanı**
- `FandomApi.categoryMembersPage(wikiId, kategori, limit, cmcontinue)`
  → `FandomCategoryMembersPage(items, nextToken)`. `cmcontinue` jetonu istekte taşınır;
  `categoryMembers()` eski imza olarak korundu (yalnızca ilk sayfa).
- `FandomApiParsers.parseContinueToken()` → `continue.cmcontinue` (yoksa `null`).
- `FandomGuideGroupContent.nextToken` + `hasMore` alanı (önbellek uyumlu: varsayılanlı).
- `FandomRepository.loadMoreGuideItems(...)` → sonraki 14'lük sayfa. İlk sayfa kalıcı
  önbellekte (7 gün), **devamı oturum belleğinde** tutulur (önbelleğe yazılmaz).

**ViewModel (`FandomMediaWikiViewModel`)**
- Yeni `GuideMore` durumu: `Idle` / `Loading(group)` / `Failed(group)`.
- `loadMoreGuide(group)`: jeton yoksa istek atmaz, aynı anda tek grup yükler,
  gelen çipleri `distinctBy { title }` ile birleştirir, yeni jetonu saklar.
  Hata → mevcut çipler korunur, düğme "Devamı yüklenemedi · Tekrar dene" durumuna geçer.
- Sekme/medya değişiminde (`bind`) ve `retryGuide`'da durum sıfırlanır.

**Arayüz (`MediaWikiTabContent`)**
- Her grup `GuideGroupSection` oldu: başlık + çipler + eylem düğmeleri.
- Katlanmışken **ilk 8 çip** görünür → düğme: `Devamını göster (+6)`.
  Dokununca gizli çipler açılır; wiki'de başka sayfa varsa **aynı dokunuşla**
  sonraki 14'lük sayfa çekilir.
- Yükleme sırasında düğme spinner + "Yükleniyor…" olur ve kilitlenir; tamamen
  açıkken `Daha az göster` ile katlanır. Genişleme `animateContentSize` ile yumuşak.
- Çipler artık vurgu renginde ince çerçeve taşıyor; eylem düğmeleri TV'de
  `tvClickable` ile odak halkası alıyor (kumanda ile kullanılabilir).

**Metinler:** `fandom_guide_show_more`, `fandom_guide_show_more_count (+%1$d)`,
`fandom_guide_show_less`, `fandom_guide_more_loading` (TR + EN).

## Değişen dosyalar

| Dosya | Ne yapıldı |
| --- | --- |
| `ui/screens/detail/StatsTab.kt` | Grafikler sekmesi baştan yazıldı (kart/rozet/lejant/puan kolonları) |
| `ui/screens/detail/DetailLoadingSkeletons.kt` | Grafikler iskeleti yeni düzene uyarlandı |
| `data/fandom/FandomApi.kt` | `categoryMembersPage` (+`cmcontinue`), eski imza korundu |
| `data/fandom/FandomApiParsers.kt` | `parseContinueToken` |
| `data/fandom/FandomModels.kt` | `FandomGuideGroupContent.nextToken/hasMore`, `FandomCategoryMembersPage` |
| `data/fandom/FandomRepository.kt` | İlk sayfada jeton saklama + `loadMoreGuideItems` |
| `ui/.../wiki/FandomMediaWikiViewModel.kt` | `GuideMore` durumu, `loadMoreGuide(group)` |
| `ui/.../wiki/MediaWikiTabContent.kt` | Katla/aç gruplar, "Devamını göster" / "Daha az göster" |
| `res/values{,‑en}/strings.xml` | Yeni TR/EN metinler (`fandom_guide_*`, `detail_stats_*`) |

## Cihazda test adımları

1. Bir anime/dizi detayı → **Grafikler**: kartlar temayla uyumlu mu, gradyan tema
   seçiliyken renkler bozuluyor mu, yatay taşma/kaydırma kalmadı mı?
2. Aynı sayfa → **Wiki**: her grupta katlanmış 8 çip + "Devamını göster (+N)";
   düğmeye basınca gizli çipler açılıyor ve **ek çipler geliyor** mu (Re:Zero gibi
   kalabalık wikilerde sayfalama görünür)? "Daha az göster" ile katlanıyor mu?
3. Uçak modunda "Devamını göster" → hata ipucu çıkıyor, çipler kaybolmuyor mu?
4. Android TV: Wiki sekmesindeki düğmelere kumandayla odaklanıp basılabiliyor mu?
5. İngilizce dilde (Ayarlar → Dil) etiketler/yüzdeler doğru biçimde mi?

## Notlar / kararlar

- "Devamını göster" verisi bilinçli olarak **önbelleğe yazılmaz**: ilk sayfa 7 gün
  kalıcı önbellekte kalır, eklenen sayfalar yalnızca o oturumda görünür (önbellek
  tutarlılığı + şişme riski yok).
- TV detay ekranındaki istatistik bölümü (`TvStatsSection`) ayrı tasarımdır ve bu
  turda **değiştirilmedi**; TV yalnızca ortak iskeletten yararlanır.
- Puan ölçeği otomatik algılanır (10–100 veya 1–10); ortalama aynı ölçekte gösterilir.
