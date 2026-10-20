# Kitsugi Beta v2.4.313

## 🇹🇷 Türkçe

### 🎨 Hızlı Ekleme Butonu Kart Dili Uyumu (`KitsugiQuickAddButton`)
- **Köşe Rozeti Tasarımı:** Posterin üstüne binen 30dp daire ve beyaz halka yerine, kartın diğer köşe rozetleriyle (sıra etiketi `#1`, kaynak logosu, durum rozeti) birebir uyumlu, köşeye sıfır boşlukla oturan ve yalnızca alt-sol köşesi yuvarlatılmış yarı saydam füme cam rozet yapısına geçildi.
- **Dinamik Boyutlandırma:** Kart tipine göre optimize edilmiş boyutlar; Grid ve TV posterlerinde 28dp, liste ve detaylı sezonluk kartlarda 24dp.
- **Sessiz ve Uyumlu Liste Durumu:** Listedeki kartlar için göz yoran parlak yeşil bloklar yerine, füme cam zemin + ince yeşil çerçeve ve yeşil tik tasarımı uygulandı; durum rozetleriyle görsel karmaşa engellendi.
- **Akıcı Animasyonlar:** Zemin, çerçeve ve ikon rengi geçişleri `animateColorAsState` ile yumuşatıldı.
- **TV Odak İyileştirmesi:** Android TV'de poster clip sınırlarında kırpılmaya yol açan büyüme efekti devre dışı bırakıldı (`scaleOnFocus = false`), odak konturu rozetin geometrisine tam kilitlendi.
- **Kart Entegrasyonları (`DetailedSeasonalMediaCard`, `KitsugiExploreMediaCard`, `KitsugiRankingMediaCard`):** Dış boşluklar (padding) kaldırılarak rozetlerin poster köşesine pürüzsüz yapışması sağlandı.

---

## 🇬🇧 English

### 🎨 Quick-Add Button Card Aesthetics Parity (`KitsugiQuickAddButton`)
- **Seamless Corner Badge Redesign:** Replaced the circular 30dp sticker-like badge with an integrated, semi-transparent frosted glass badge featuring a single rounded bottom-start corner, matching existing card chrome (rank `#1`, source logos, status badges).
- **Adaptive Layout Sizing:** Sized contextually per view: 28dp on grid and TV posters, 24dp on list and seasonal card variations.
- **Subtle In-List Representation:** Eliminated jarring bright green fill blocks in favor of a soft dark glass base, subtle green outline, and green checkmark that blends naturally with the existing card status.
- **Fluid Color Transitions:** Animated scrim background, border strokes, and icon tint with `animateColorAsState`.
- **Android TV Focus Polish:** Disabled focus expansion (`scaleOnFocus = false`) to prevent clipping artifacts inside poster clipping boundaries, keeping focus rings tightly contoured.
- **Card Integrations (`DetailedSeasonalMediaCard`, `KitsugiExploreMediaCard`, `KitsugiRankingMediaCard`):** Stripped outer padding to dock the badge flush against poster bounds.
