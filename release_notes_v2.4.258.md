# Kitsugi-Beta v2.4.258 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🧭 1. Ortak Üst Sekme Bandı Artık Her Yerde (Görünüm & Tercihler Mantığı)

"Ayarlar > Görünüm & Tercihler" ekranındaki üst sekme bandı ve sağa/sola kaydırma mantığı
artık ortak bir bileşen (`KitsugiSettingsSegmentedTabs`) ve **dört ekranda aynı şekilde** çalışıyor:

| Ekran | Yeni üst bant | Kaydırma |
|---|---|---|
| **Oynatıcı Ayarları** | Dahili oynatıcı · Hareketler · Kod çözücü · Alt yazılar · Ses · Özel butonlar · Kod düzenleyici · Gelişmiş (8 sekme) | Sağa/sola kaydırma ile bölüm geçişi |
| **İndirmeler** | Videolar (n) · Altyazılar (n) · Resimler (n) | Sayfa kaydırma |
| **Platform Hata Kodları** | Tümü · Manga · CloudStream · Genel · AniList · MyAnimeList · Kitsu · Shikimori · Bangumi · TMDB · Simkl (11 sekme) | Kategori kaydırma |
| **Hesap Bağlantıları** | Platformlar · Senkronizasyon | Sayfa kaydırma |

- **Oynatıcı Ayarları:** Ana menü listesi kaldırıldı; bölümler kaldığı yerden değil, doğrudan
  sekmeden açılıyor. Her sekmenin **kendi kaydırma konumu** korunuyor (bir bölümde aşağı inip
  diğerine geçince konum karışmıyor). Geri tuşu ve üst başlık artık tek: "Oynatıcı Ayarları".
- **Platform Hata Kodları:** Filtre çipleri yerine sekme bandı; her kategori **yalnızca kendi
  kodlarını** listeliyor (AniList sekmesi=AniList kodları, Manga sekmesi=Manga kodları …),
  arama kutusu tüm kategorilerde ortak çalışıyor. Sekmelerde platformların **orijinal logoları** var.
- **Hesap Bağlantıları:** Platformlar ve Senkronizasyon artık iki ayrı sekme.

### 📐 2. Taşma Yok — Dikey/Yatay, Telefon/Tablet/TV Uyumlu Bant

Ortak bant artık ekran genişliğine göre **kendini ayarlıyor**:

- Sekmeler ekrana **sığıyorsa** eşit bölünür (eski görünüm birebir korunur).
- **Sığmıyorsa** bant kendi içinde yatay kaydırılır — sekmeler ekranın sağına/soluna **taşmaz**.
- **Seçili sekme otomatik olarak ortalanır** (uzak bir sekmeye geçildiğinde bant kendini kaydırır).
- Kaydırılabilir bandın kenarlarında "devamı var" **solma ipucu** çizilir.
- **TV'de** D-pad ile odaklanan sekme de otomatik olarak görünür alana getirilir.
- Tablette/yatay ekranda sekmeler dev boyuta kaçmaz (üst sınır 168dp), telefonda ~3 sekme görünür.

### 🧩 3. Sekme Logoları

Bant, istenirse ikon yerine **platform logosu** gösterebiliyor (`platformId`): hata kodu
rehberindeki AniList / MyAnimeList / Kitsu / Shikimori / Bangumi / TMDB / Simkl sekmeleri
artık uygulamanın gerçek platform logolarını kullanıyor.

---

## 🇬🇧 English

### 🧭 1. Shared Top Tab Bar Everywhere (Appearance & Preferences Behaviour)

The segmented tab bar and left/right swipe logic of "Settings > Appearance & Preferences" is now a
shared component (`KitsugiSettingsSegmentedTabs`) and behaves **identically on four screens**:

| Screen | New top bar | Swipe |
|---|---|---|
| **Player Settings** | Internal player · Gestures · Decoder · Subtitles · Audio · Custom buttons · Code editor · Advanced (8 tabs) | Swipe between sections |
| **Downloads** | Videos (n) · Subtitles (n) · Images (n) | Page swipe |
| **Platform Error Codes** | All · Manga · CloudStream · General · AniList · MyAnimeList · Kitsu · Shikimori · Bangumi · TMDB · Simkl (11 tabs) | Category swipe |
| **Account Connections** | Platforms · Sync | Page swipe |

- **Player Settings:** the main menu list is gone; sections open directly from the tab bar. Each tab
  keeps **its own scroll position**. Back button and title are now single-level ("Player Settings").
- **Platform Error Codes:** filter chips replaced by the tab bar; each category lists **only its own
  codes**, and search stays global. Platform tabs show the **original platform logos**.
- **Account Connections:** Platforms and Sync are now two tabs.

### 📐 2. No Overflow — Portrait/Landscape, Phone/Tablet/TV Friendly

- Tabs **fit** the screen → distributed evenly (previous look preserved).
- Do **not** fit → the bar scrolls **inside itself**; tabs never bleed off the screen edges.
- The **selected tab is auto-centered**; subtle edge fades hint more tabs exist.
- On **TV**, the D-pad focused tab is scrolled into view automatically.
- On tablets/landscape tabs are capped at 168dp, so ~3 tabs stay visible on phones.

### 🧩 3. Tab Logos

The bar can render a **platform logo** instead of an icon (`platformId`): the AniList / MyAnimeList /
Kitsu / Shikimori / Bangumi / TMDB / Simkl tabs of the error-code reference now use the app's real
platform logos.
