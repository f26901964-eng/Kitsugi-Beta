# Görev Listesi — Vitrin İyileştirme (testler sana bırakıldı)

## Yapılanlar (tamamlandı) ✅

1. **Görsel kaplama (Cover) sunumu** — vitrin görseli artık `ContentScale.Crop`
   ile vitrini BAŞTAN SONA KAPLAR (Fit/bulanık dolgu kaldırıldı). Kaynak
   seçimi `heroImageCandidates()` ile ekran boyutu (vitrin kutusunun en-boy
   oranı) ve dikey/yatay moda göre: geniş bantta yatay fanart/backdrop önce,
   dikey telefon vitrininde poster önce; yükleme hatasında diğer adaya düşer
   (`onLoadingFailed` zinciri). Kırpma odağı `heroImageAlignment()`: backdrop
   merkez, poster geniş bantta hafif yukarı. Gradientler kaplayan görseli
   öldürmeyecek şekilde yumuşatıldı.
2. **Veriye dayalı vitrin seçimi** — `HeroSelection.kt`: puan, üye,
   favori, rank metrikleri normalize edilir; trend/yeni/manga kategori
   bonusları; Tümü modunda **her kaynaktan en az 1 garanti + 12'ye
   kadar**, kaynak modunda **tüm bölümlerden 10'a kadar** kontenjanlı
   seçim.
3. **Eksik arka planlar** — seçilen öğelerde TMDB'den backdrop
   tamamlama (yatay mod, manga hariç, önbellekli).
4. **Meta satırı** — üye/favori sayıları vitrin başlığında görünür.
5. **Yeni test dosyası** — `HeroSelectionTest.kt` yazıldı (10 test).

## Sana kalanlar ❯

- [ ] **Derleme kontrolü:**
      `./gradlew :app:compileDebugKotlin`
- [ ] **Unit testler (senin çalıştırman gerekiyor):**
      `./gradlew :app:testDebugUnitTest`
      (özellikle `AllSourcesExploreTest` ve `HeroSelectionTest`)
      Not: Sandbox'ta Java/Gradle yoktu; seçim motoru bağımsız
      Kotlin 2.0.21 derleyicisiyle 41 iddiada ayrıca doğrulandı,
      Compose tarafı sadece kod incelemesiyle garantilendi.
- [ ] **Manuel QA:**
  - [ ] Vitrin görseli tüm alanı kaplıyor mu? (Cover — boşluk/bulanık dolgu yok)
  - [ ] Yatay mod + geniş bantta yatay fanart/backdrop, dikey telefon vitrininde
        poster seçiliyor mu? (ekran boyutuna göre tablet bandı da backdropta)
  - [ ] Poster geniş bantta kırpılırken yüz/başlık bandı görünür kalıyor mu?
  - [ ] Bozuk/yüklenemeyen görselde yedek adaya (backdrop ↔ poster) düşüyor mu?
  - [ ] API < 31 cihaz/emülatörde NSFW bulanıklığı (bitmap blur) bozulmadı mı?
  - [ ] Tümü modu: her kaynak vitrinde temsil + 12 öğe
  - [ ] Kaynak modları (TMDB/SIMKL/MAL/AniList/Kitsu/Shikimori/Bangumi):
        10 öğe, manga/trend/yeni bölümlerden karışım
  - [ ] Yetişkin filtresi açık/kapalı davranış
  - [ ] `hideScores` açıkken puan gizli, üye/favori görünür mü
  - [ ] Logo modu (`showAnimeLogos`) — kimlik eşleşmesi korunuyor mu

## Uygulama notu

Zip içinde `app/...` yol yapısı korunmuştur: repo köküne açıp
doğrudan üzerine kopyalayabilirsin. `changes.patch` takip edilen
5 dosyanın diff'ini içerir; 2 yeni dosya tam hâliyle pakette.
