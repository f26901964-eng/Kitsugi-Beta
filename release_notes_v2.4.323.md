# Kitsugi Beta v2.4.323

## 🇹🇷 Türkçe

### 📐 Detay Sayfası Harici Puanlar (MDBList) & Kart Boşluk Düzeltmesi

Bu sürümde, detay sayfasında (API detay görünümü) "Harici Puanlar" kartının altındaki boşluğun sıfır kalması sorunu düzeltildi ve tüm kart yerleşimi simetrik hale getirildi.

- **Harici Puanlar alt boşluğu eşitlendi (`ApiDetailTabContents.kt`):**
  - **Sorun:** `ApiDetailOverviewTab` görünümünde kartlar manuel `Spacer(14.dp)` ile diziliyordu. Harici Puanlar (MDBList) kartının üstünde 14 dp boşluk varken, altındaki "Türler" veya "Stüdyolar" kartı ile arasında boşluk 0 dp kalıyor ve kartlar birbirine yapışıyordu.
  - **Çözüm:** `ApiDetailOverviewTab` konteyneri, `EntryDetailOverviewTab` ile birebir aynı düzene (`Column(verticalArrangement = Arrangement.spacedBy(14.dp))`) geçirildi.
  - **Otomatik Aralık Korunması:** Manuel Spacer'lar kaldırıldığı için, MDBList harici puanları yüklenmediğinde ya da bulunmadığında da komşu kartlar arasındaki 14 dp mesafe bozulmadan ve fazladan boşluk bırakmadan korunur.
  - **Tüm Detay Sekmeleri Denetlendi:** Detay sayfalarındaki diğer tüm sekmelerin (`StatsTab`, `ReviewsTab`, `CharactersTab`, `RelationsTab`, `RecommendationsTab`) `Arrangement.spacedBy(...)` yapısı kontrol edildi; aralık dengesizliği yaşayan başka bir kart bulunmamaktadır.

---

## 🇬🇧 English

### 📐 Detail Page External Ratings (MDBList) & Card Spacing Fix

Resolves card vertical spacing inconsistency where the "External Ratings" (MDBList) card lacked bottom margin on the API detail overview page.

- **External Ratings bottom spacing aligned (`ApiDetailTabContents.kt`):**
  - **Issue:** `ApiDetailOverviewTab` used ad-hoc `Spacer(14.dp)` between cards. While 14 dp existed above External Ratings, the bottom gap before "Genres" or "Studios" was 0 dp, causing visual clamping.
  - **Fix:** Switched `ApiDetailOverviewTab` to use `Column(verticalArrangement = Arrangement.spacedBy(14.dp))`, fully aligning it with `EntryDetailOverviewTab`.
  - **Dynamic Card Spacing:** Conditional cards like `ApiMdbListRatingCard` cleanly yield spacing when empty or hidden without leaving phantom gaps.
  - **Audited Secondary Tabs:** Verified all other detail tabs (`StatsTab`, `ReviewsTab`, `CharactersTab`, `RelationsTab`, `RecommendationsTab`) for spacing consistency.
