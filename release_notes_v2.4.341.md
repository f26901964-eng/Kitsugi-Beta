# Kitsugi Beta v2.4.341

## 🇹🇷 Türkçe

### ⭐ Simkl Puanları — Arama, Keşfet ve Tümünü Gör Düzeltmesi
- **Eksik `extended=full` parametreleri eklendi:** Simkl arama (`/search/{type}`), Keşfet vitrin/şeritleri (`getBestMedia`), Tümünü Gör kategori/tür/prömiyer/yayın sayfaları (`getExploreGenrePage`, `getPremieresPage`, `getAiringMedia`, `searchAdvanced`) ve DVD/izleme listesi uçlarının tamamına `extended=full` eklendi.
- **Birleşik puan çıkarıcı ve önbellekli tamamlama (`extractSimklScoreInfo` + `hydrateSimklScores`):** `ratings.simkl = 0` olduğunda Elvis operatörünün `mal`/`imdb` yedeğine geçmemesi ve anime listelerinde `mal` puanının atlanması düzeltildi; eksik puanlı satırlar paralel olarak detay önbelleğinden tamamlanır.
- **Tüm puan biçimlerinde tam uyum:** `POINT_10`, `POINT_10_DECIMAL`, `POINT_100`, `POINT_5` ve `STARS` formatları 0–10 ve 0–100 ölçeklerini hatasız işler; `MediaTitleResolver.VERSION = 4` ile eski puansız Room keşfet önbellekleri yenilendi.

### ⚡ Bangumi — 5 Dakikalık Kilitlenme / Döngü Çözümü ve Tam Dil Uyumu
- **İkili semafor kilitlenmesi (deadlock) giderildi:** `BangumiSubjectTitleResolver.runInfoboxPass` içindeki iç içe `infoboxSemaphore.withPermit` kilitlenmesi kaldırıldı.
- **Sonsuz yeniden sorgulama döngüsü kapatıldı (`englishVerified`):** `Jujutsu Kaisen`, `One Piece`, `Chainsaw Man` gibi İngilizce ve Romaji adı aynı olan yapımların her açılışta "eksik İngilizce" sayılıp tekrar tekrar sorgulanması önlendi; seçilen dil (`ENGLISH` / `ROMAJI` / `NATIVE`) Bangumi'nin her ekranında eksiksiz korunur.
- **İptal edilebilir AniList jeton kovası (`awaitAniListRatePermit`):** İptal/zaman aşımında saati dakikalarca ileri saran eski slot rezervasyonu yerine jeton kovası getirildi.
- **Aşamalı (progressive) Keşfet & Tümünü Gör & v0 yedekleri:** Bangumi Keşfet 14 şeridi ~1–2 sn içinde anında çizilir, eksik İngilizce/Romaji başlıklar arka planda çözülüp ekranı canlı günceller; `CancellationException` yutulması ve `next.bgm.tv/p1` kesintilerinde `v0` yedekleri tamamlandı.

### 🎬 Ortak Çapraz Kaynak "Videolar" Sekmesi (`KitsugiCrossSourceVideoAggregator`)
- **8 kaynak tek sekmede birleştirildi:** Birincil detay, **MyAnimeList / Jikan** (tüm PV'ler ve resmi müzik videoları), **AnimeThemes** (doğrudan oynatılabilir HD OP/ED videoları), **TMDB** (`tr`/`en`/`ja` fragmanlar, teaser'lar, kamera arkası ve klipler), **AniList**, **Shikimori**, **Simkl**, **Kitsu** ve eklenti yedekleri paralel toplanır.
- **Akıllı tekilleştirme ve kaynak filtreleme:** Aynı videoyu sunan platformlar tek satırda birleştirilip tüm platform rozetleri gösterilir; üstteki kaynak çipleriyle tüm kaynaklar veya tek kaynak seçilebilir. Hem `ApiResultDetailPage` hem `MediaEntryDetailPage` ("Listede" kayıtlar dahil) tam desteklenir.
