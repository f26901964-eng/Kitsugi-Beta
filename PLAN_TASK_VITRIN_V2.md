# Keşfet Vitrin (Hero) İyileştirme Planı — 2026-10-09

İki sorun ve çözümleri:

## 1) Görsel gösterim: vitrin resmi vitrini KAPLAMALI (Cover, yön/ekran boyutuna göre)

**Sorun:** Vitrin görseli önce `ContentScale.Crop` ile aşırı kırpılıyordu,
ardından "Fit + bulanık dolgu" sunumuna geçilmişti; resim vitrini
kaplamıyor, sağda küçük kalıyor + arkada bulanık zemin görünüyordu.
İstenen: **vitrin resmi, ekran boyutu ve dikey/yatay moda göre vitrini
baştan sona kaplamalı** — TMDB fanart'ın yatay dikdörtgen görselleri hem
dikey hem yatay modda, posterler dikey ekranlar için kullanılmalı.

**Çözüm (KitsugiHeroSection.kt):**
- **Kaplayan sunum:** görsel `ContentScale.Crop` ile tüm vitrin kutusunu
  doldurur. Fit/bulanık dolgu katmanları kaldırıldı.
- **Kaynak seçimi `heroImageCandidates()`:** gerçek vitrin kutusunun
  en-boy oranı (ekran boyutu) + yön bilgisiyle öncelik sırası:
  1. Geniş vitrin bandı (en/boy ≥ 1.1 — yatay mod, dikey tablet bandı,
     TV vb.) → yatay dikdörtgen fanart/backdrop önce (her iki yöne de uyumlu).
  2. Dikey-telefon vitrini (kareye yakın/dar) → dikey poster önce.
  3. Yüklenemeyen birincil görselde `onLoadingFailed` zinciriyle diğer
     adaya düşülür (backdrop ↔ poster).
- **Kırpma odağı `heroImageAlignment()`:** backdrop → merkez (özne bandı);
  poster geniş bantta kırpılmak zorunda kalırsa hafif yukarı bias
  (yüz/başlık bandı korunur); poster dikey kutuda → merkez.
- Sol/üst/alt karartma gradyanları yumuşatıldı — kaplayan görsel
  görünür kalsın, metin okunabilirliği korunsun.
- `ExploreViewModel.enrichHeroBackdrops` artık her yönde tetiklenir
  (geniş bandı olan dikey tablet de backdrop'tan yararlanır).

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
  sonuç `exploreIdentity` ile önbelleklenir; V2.1'den itibaren her yönde
  tetiklenir — geniş vitrin bandı her yönde backdrop'tan yararlanır).
- `ExploreScreen`: `displayHeroItems` = seçilen öğeler + gelen
  backdrop'lar birleştirilmiş hâli.
- `HeroSectionComponents.buildHeroMeta`: meta satırına `1.2M üye`,
  `45.3K favori` istatistikleri eklendi (seçim kriterleri görünür olsun).

## Dosya bazında değişiklikler

| Dosya | Değişiklik |
|---|---|
| `ui/screens/explore/HeroSelection.kt` | **YENİ** — skorlama + seçim motoru |
| `ui/screens/explore/AllSourcesExplore.kt` | `allSourceHeroes` yeni motora devredildi (imza aynı) |
| `ui/screens/explore/ExploreScreen.kt` | heroItems tüm bölümlerden seçim; displayHeroItems; backdrop LaunchedEffect (V2.1: her yön) |
| `ui/screens/explore/ExploreViewModel.kt` | `enrichHeroBackdrops` + `heroBackdropOverrides` state'i |
| `ui/components/KitsugiHeroSection.kt` | V2.1: kaplayan sunum (Crop + yön/oran kaynak seçimi + yedek zinciri) |
| `ui/components/KitsugiNsfwImage.kt` | V2.1: `onLoadingFailed` geri çağrısı (yedek görsel zinciri) |
| `ui/components/HeroSectionComponents.kt` | meta'ya üye/favori istatistikleri |
| `test/.../HeroSelectionTest.kt` | **YENİ** — normalizasyon, tavan, garanti testleri |
| `test/.../HeroImageSelectionTest.kt` | **YENİ (V2.1)** — yön/ekran boyutu kaynak seçimi + Crop odak testleri |

Not: `AllSourcesExploreTest.kt` değişmedi; eski sözleşmeler (kaynak
başı temsil, yetişkin filtresi) yeni mantıkla korunuyor.
