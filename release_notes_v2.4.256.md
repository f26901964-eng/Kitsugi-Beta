# Kitsugi-Beta v2.4.256 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🖼️ 1. Detay Vitrini: Yön ⇄ Görsel Otomatik Eşleşmesi ve TAM Kapsama (Hem Kaynak Hem Listem Detay)

Detay sayfalarındaki (API kaynaklı detay sayfası ve Kütüphane / Listem detay sayfası) vitrin görsel mekaniği baştan tasarlandı:

#### 1.1 Kök Neden
- Eski kod her iki yönde de "önce yatay arka plan + 16:9 bant" kuralını zorluyordu.
- **Yatay mod:** Dar-uzun sol panele yatay arka plan sıkıştırıldığı için iki yandan kırpılıyor ve çirkin bir görüntü oluşuyordu.
- **Dikey mod:** Oran tutmadığında "tam görünür + bulanık dolgu" moduna kaçıp alanı tam kaplamıyordu.
- Oysa bir yapımın görsel çifti sabittir: dikey poster (2:3) ve yatay arka plan (16:9). Ekran yönüne uygun olan görsel otomatik seçilmelidir.

#### 1.2 Yeni Mekanik
- **Yatay mod → Dikey POSTER:** TMDB/Fanart kalite sırası → kaynak poster → dikey galeri. Görsel alanı 2:3 uzun-dar kutu olarak konumlanır.
- **Dikey mod → Yatay ARKA PLAN:** TMDB/Fanart kalite sırası → kaynak arka plan → yatay galeri. Görsel alanı 16:9 geniş-kısa bant olarak konumlanır.
- **Otomatik Oran Kilitlenmesi:** Görsel yüklendiğinde `onImageAspectResolved` tetiklenir ve kutunun en-boy oranı görselin gerçek oranına kilitlenir. Kutu, gösterdiği görselle birebir aynı şekle gelir.
- **TAM Kapsama (`ContentScale.Crop`):** Bulanık dolgu/boşluk katmanı detay vitrinlerinden tamamen kaldırıldı. Kutu orana kilitlendiği için kırpma kaybı olmaksızın görsel alanı %100 doldurur.
- **Yedek Koruması:** Eşlerden biri eksikse (ör. arka planı olmayan bazı manga kayıtları) diğeri gösterilir; oran güvenli aralığa (yatayda 0.5–1.6, dikeyde 1.0–2.2) sınırlandırılır.

---

## 🇬🇧 English

### 🖼️ 1. Detail Hero: Orientation ⇄ Artwork Auto-Pairing & Full Coverage (Source & Library Detail)

The hero artwork mechanics for detail pages (API source details and Library/Listem details) have been redesigned from the ground up:

#### 1.1 Root Cause
- The previous implementation enforced a "backdrop first + 16:9 band" rule across both orientations.
- **Landscape mode:** Squeezing a horizontal backdrop into a tall/narrow left panel caused aggressive side cropping.
- **Portrait mode:** Aspect ratio deviations fell back to "contain + blurred fill", leaving awkward dead margins.
- A media title naturally has a paired set of visuals: vertical POSTER (2:3) and horizontal BACKDROP (16:9). The screen orientation should determine the matching visual.

#### 1.2 New Mechanics
- **Landscape mode → Vertical POSTER:** TMDB/Fanart quality ranking → source poster → vertical gallery. Renders in a tall 2:3 box tailored for landscape panels.
- **Portrait mode → Horizontal BACKDROP:** Quality ranking → source backdrop → horizontal gallery. Renders as a cinematic 16:9 header banner.
- **Aspect Ratio Locking:** Upon image decode, `onImageAspectResolved` locks the container's aspect ratio to the exact dimensions of the image.
- **FULL Coverage (`ContentScale.Crop`):** Ambient blurred padding layers have been removed from detail heros. Because the container locks to the image aspect ratio, the image fills the space seamlessly with zero crop loss.
- **Graceful Fallback:** If one counterpart is missing (e.g. backdropless manga), the other is displayed within safe bounds ([0.5, 1.6] landscape, [1.0, 2.2] portrait).
