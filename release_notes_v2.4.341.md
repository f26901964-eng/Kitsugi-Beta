# Kitsugi Beta v2.4.341

## 🇹🇷 Türkçe

### 🔔 Akıllı Bildirim Butonu — Hangi Kaynağı Seçtiysen Bildirimler O Kaynakla Açılır
- **Sekme duyarsız sabit açılış gitti:** Artık **Listem**, **Keşfet**, **Arama** ve **Profil**
  sekmelerinde hangi kaynağı seçtiysen (AniList, MyAnimeList, TMDB & Simkl, Kitsu, Shikimori,
  Bangumi), 🔔 bildirim butonuna bastığında bildirimler sayfası **doğrudan o kaynağın
  sekmesiyle** açılır.
- **Arama sekmesine bildirim butonu eklendi:** "Arama" başlığının yanında, Keşfet ve Listem ile
  aynı görünümde 🔔 butonu; arama çubuğundaki kaynak seçimi neyse bildirimler ona göre açılır.
- **"Tümü" seçiliyse eski davranış:** Birleşik görünümün tek bir bildirim kaynağı olmadığı için
  bağlı olan ilk hesabın bildirimleri gösterilir.
- **Sayfa içi seçim korunur:** Bildirimler sayfasında kaynağı elle değiştirdiğinde seçimin
  geri alınmaz; sekmeye dönüp aynı kaynakla tekrar girdiğinde kaydırma konumu da korunur.
- **MAL kimlik uyumu:** Uygulama içinde `mal` / `jikan` olarak dolaşan MyAnimeList kaynakları
  tek ve doğru bildirim sekmesine yönlenir.

---

## 🇬🇧 English

### 🔔 Smart Notification Button — Opens the Notifications of the Source You Picked
- **No more one-fixed-source opening:** On the **My List**, **Explore**, **Search**, and
  **Profile** tabs, the 🔔 button now opens the notifications screen directly on the tab of the
  source you currently have selected (AniList, MyAnimeList, TMDB & Simkl, Kitsu, Shikimori,
  Bangumi).
- **Notification button added to Search:** A 🔔 button now sits next to the "Search" title,
  matching the Explore / My List styling, and follows the source engine picked in the search bar.
- **"All" keeps legacy behaviour:** Since the combined view has no single notification source,
  the first connected account is shown as before.
- **In-page choice is preserved:** Switching sources manually inside the notifications screen is
  never overridden, and re-entering with the same source restores the scroll position.
- **MAL identity consistency:** Sources travelling internally as `mal` / `jikan` both resolve to
  the single, correct MyAnimeList notifications tab.
