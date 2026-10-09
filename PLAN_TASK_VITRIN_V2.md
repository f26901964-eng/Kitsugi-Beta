# Keşfet Vitrin (Hero) İyileştirme Planı — 2026-10-09

İki sorun ve çözümleri:

## 1) Görsel gösterim: kenarlardan aşırı kırpma (portre + yatay mod)

**Sorun:** Vitrin görseli `ContentScale.Crop` ile sabit yükseklikli kutuya
giriyordu; sağ/sol/üst/aşağı kesiliyor, yalnızca orta bant görünüyordu.
Üst/alt gradyanlar da ortadaki bölgeyi daha da görünmez kılıyordu.

**Çözüm (KitsugiHeroSection.kt):**
- Katmanlı sunum:
  1. **Arka dolgu:** aynı görsel Crop + bulanık (API 31+ `Modifier.blur`,
     altında `BlurTransformation` bitmap blur) + %42 karartma → kutuyu
     baştan sona doldurur, "siyah boşluk" olmaz.
  2. **Ön plan:** aynı görsel `ContentScale.Fit` → resim ekran oranına,
     yönüne (portre/yatay) göre **neredeyse tamamen görünür, kesilmez**.
- Yatay modda ön plan `CenterEnd` (sağa yaslı) — sol taraftaki metin
  bloğu görselin üstüne binmez; portrede `TopCenter`.
- Üst/alt karartma gradyanları yumuşatıldı (resmin büyük kısmı net
  kalsın, alt başlık/metin okunabilirliği korundu).
- Otomatik görsel seçimi aynı kaldı: portre → `imageUrl` (poster),
  yatay → `backdropUrl ?: imageUrl`.

## 2) Vitrin içeriği: daha fazla ve veriye dayalı seçim

**Sorun:** Tümü modunda kaynak başına 1 öğe (7'ye kadar); kaynak
modunda yalnızca ilk 5 top anime. Sıralama yoktu.

**Çözüm — yeni motor `ui/screens/explore/HeroSelection.kt`:**
- Sayısal skor (0..1 normalize): **puan** %40, **üye/izlenme** %30,
  **favori** %20, **rank** %10. Ölçek farkları normalize edilir
  (çoğu kaynak 0–10, Simkl 0–100). Eksik metrik ceza değil — ağırlık
  mevcut metriklere yeniden dağıtılır (TMDB'nin üyesi olmadığı için
  puanına göre değerlendirilir).
- Kategori bonusu (`heroCategoryBoost`): trend 1.15, en yüksek puanlı
  1.12, yeni eklenen/film/sezon 1.10, top 1.05, yayında 1.00,
  yakında 0.95 → trend, yeni eklenen, manga vb. uygun şartlarla vitrine girer.
- Seçim (`selectHeroItems`):
  - **Tümü modu:** her kaynaktan en az 1 temsil GARANTİ + kalan
    kontenjan skor sırasıyla; kaynak başına en fazla 2, kategori başına
    en fazla 3 → limit **12**.
  - **Kaynak modu:** o kaynağın TÜM bölümleri (top, trend, yeni,
    manga, film, …) aday olur; kategori tavanı 3 ile çeşitlilik → limit **10**.
  - Tavanlar kontenjanı boş bırakmaz: son aşamada esnetilerek doldurulur.
  - Nihai liste skor sırasına göre; vitrin ilk sayfası en güçlü adayla açılır.

**Ek iyileştirmeler:**
- `ExploreViewModel.enrichHeroBackdrops(...)`: seçilen vitrin
  öğelerinde eksik yatay arka planlar TMDB'den tamamlanır (manga hariç;
  sonuç `exploreIdentity` ile önbelleklenir, sadece yatay modda tetiklenir).
- `ExploreScreen`: `displayHeroItems` = seçilen öğeler + gelen
  backdrop'lar birleştirilmiş hâli.
- `HeroSectionComponents.buildHeroMeta`: meta satırına `1.2M üye`,
  `45.3K favori` istatistikleri eklendi (seçim kriterleri görünür olsun).

## Dosya bazında değişiklikler

| Dosya | Değişiklik |
|---|---|
| `ui/screens/explore/HeroSelection.kt` | **YENİ** — skorlama + seçim motoru |
| `ui/screens/explore/AllSourcesExplore.kt` | `allSourceHeroes` yeni motora devredildi (imza aynı) |
| `ui/screens/explore/ExploreScreen.kt` | heroItems tüm bölümlerden seçim; displayHeroItems; backdrop LaunchedEffect |
| `ui/screens/explore/ExploreViewModel.kt` | `enrichHeroBackdrops` + `heroBackdropOverrides` state'i |
| `ui/components/KitsugiHeroSection.kt` | katmanlı görsel (Fit + bulanık dolgu), gradient yumuşama |
| `ui/components/HeroSectionComponents.kt` | meta'ya üye/favori istatistikleri |
| `test/.../HeroSelectionTest.kt` | **YENİ** — normalizasyon, tavan, garanti testleri |

Not: `AllSourcesExploreTest.kt` değişmedi; eski sözleşmeler (kaynak
başı temsil, yetişkin filtresi) yeni mantıkla korunuyor.
