# PLAN / TASK — Detay Sayfası Düzeltmeleri (İzle Butonu, Gerçek Karakter Sayfaları, Kopyala Butonları)

**Tarih:** 2026-10-08
**Dal:** `arena/2b23fb65-kitsugi-beta`
**Durum:** Uygulandı (derleme yapılmadı — kullanıcı talebiyle yalnızca kaynak değişiklikleri)

---

## İstek 1 — İzle butonu küçük olsun (1. ekran)

Detay sayfalarındaki **İzle** butonu ekrandan taşacak kadar büyük olmasın; yalnızca
ikon + "İzle" yazısını kapsayacak şekilde (wrap-content) küçük kalsın.

### Yapılan
- `ApiDetailLeftPanel.kt` — İzle butonundan `Modifier.fillMaxWidth()` kaldırıldı;
  buton artık içeriğine göre sarılıyor (FlowRow içinde "Listeye Ekle" / "Kaynakta Aç"
  ile aynı hizada akıyor). TV odak (`leftPanelFocusRequester` / `tabBarFocusRequester`) korundu.
- `KitsugiDetailInfoSection.kt` → `QuickActions` — MediaEntry detay sayfasındaki
  İzle butonundan da `fillMaxWidth()` kaldırıldı (aynı kompakt görünüm).

> Not: Manga "Oku" butonuna dokunulmadı (talep yalnızca İzle içindi).

---

## İstek 2 — Simkl / TMDB karakter bölümleri: gerçek dizi/film karakterleri
### sayfası açılmıyor (2. ve 3. ekranlar)

**Sorun:** Simkl/TMDB kaynaklı **canlı çekim (animasyon olmayan)** dizi/filmlerde
karakter kartına basınca doğru karakter sayfası açılmıyordu. Karakter detay akışı
her zaman önce AniList/Jikan üzerinden **bulanık (fuzzy) isim araması** yapıyordu;
gerçek karakter isimleri (ör. "Sheriff Kerry Kane", "Hal Jordan / Halogram",
"Peter Parker / Spider-Man") rastgele anime karakterleriyle eşleşiyor ya da
boş/junk bir sayfayla sonuçlanıyordu.

**İstenen davranış:**
- Animasyon / anime tarzı içeriklerin **hayali karakterleri** → AniList / MAL / Kitsu
  üzerinden (mevcut davranış korundu).
- **Normal (canlı çekim) dizi ve filmlerin karakterleri** → doğrudan **TMDB/Simkl**
  verisiyle açılmalı (kişi kartı + "Oyuncu" satırı + filmografi).

### Yapılan
- `KitsugiModels.kt` — `KitsugiCharacter`'a `isRealMediaRole: Boolean = false`
  alanı eklendi (canlı çekim medya karakteri mi?).
- `KitsugiCharacterClient.kt` → `fetchCharacters`
  - `"simkl"` ve `"tmdb"` dallarında medya "anime mi?" tespiti:
    `mediaType == Anime || realMalId > 0`
  - **Anime değilse** TMDB credits karakterleri `isRealMediaRole = true` ile
    işaretlenir ve `enrichCharactersWithAnimeImages` **çalıştırılmaz**
    (böylece gerçek karakterler yanlışlıkla AniList karakterlerine fuzzy eşlenmez).
  - Anime ise mevcut enrichment akışı aynen korunur (eşleşenler `source="anilist"`).
- `KitsugiCharacterClient.kt` → `fetchCharacterDetail(..., isRealMediaRole)`
  - `"tmdb"` dalında `isRealMediaRole == true` ise **AniList/Jikan isim araması
    tamamen atlanır**; sayfa doğrudan `TmdbApiClient().fetchPersonCharacterDetail()`
    verisiyle kurulur:
    - `name` = karakter/rol adı (temizlenmiş)
    - `nativeName` = oyuncunun kendi adı
    - `voiceActors` = oyuncu satırı (`language = "oyuncu"` — eskisi gibi "Japonca" değil)
    - biyografi / doğum günü / cinsiyet / filmografi = TMDB kişi verisi
  - Kişi verisi alınamazsa eski arama akışına güvenlik amaçlı düşülür.
- Navigasyon zinciri `isRealMediaRole` bayrağını taşır:
  - `JikanApiClient.fetchCharacterDetail` → parametre passthrough
  - `CharacterDetailViewModel` → `loadCharacter/retry/forceRefresh/fetchCharacterDetail`
  - `CharacterDetailPage` → yeni `isRealMediaRole` parametresi
  - `AppNavigation.kt` → `DetailScreen.CharacterDetail` + `AppStateKey.CharacterDetail`
  - `AppRoot.kt` → AppStateKey eşlemesi
  - `AppRootDetailPages.kt` → Karakterler sekmesi tıklamaları
    (`char.isRealMediaRole` ile `CharacterDetail`'a taşınır)

---

## İstek 3 — Bilgiler bölümlerine minik kopyala butonları (4. ekran)

Karakter / seslendirme sayfalarındaki gibi; anime-dizi-film detay sayfalarının
**Bilgiler** kartındaki **her bilgi satırına ayrı ayrı** minik kopyala butonları.

### Yapılan
- `ApiResultDetailComponents.kt` → `ApiInfoSection` / `ApiInfoRow`
  - Her satır değerinin yanına `KitsugiMiniCopyButton` eklendi
    (Durum, Sezon, Başlangıç, Bitiş, Kaynak, Stüdyo, Süre, Yayın, Yaş Sınırı,
    İngilizce, Japonca, Diğer Adlar).
  - **Çoklu değerli satırlar** ("Diğer Adlar", "Stüdyo") artık her değeri ayrı
    satırda, **her birinin kendi kopyala butonuyla** gösteriyor ("parça parça").
- `KitsugiDetailInfoSection.kt` → `EntryInfoSection` / `EntryInfoRow`
  (anime/MediaEntry detay sayfası) — aynı çoklu-değer + satır bazlı kopyala
  düzeni uygulandı (tek-değer kopyalama zaten vardı; Diğer Adlar/Stüdyo
  artık ayrı ayrı kopyalanabiliyor).

---

## Değişen Dosyalar (11)

| # | Dosya | İstek |
|---|-------|-------|
| 1 | `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiDetailLeftPanel.kt` | 1 |
| 2 | `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailInfoSection.kt` | 1 + 3 |
| 3 | `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiModels.kt` | 2 |
| 4 | `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiCharacterClient.kt` | 2 |
| 5 | `app/src/main/java/com/kitsugi/animelist/data/remote/JikanApiClient.kt` | 2 |
| 6 | `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/CharacterDetailViewModel.kt` | 2 |
| 7 | `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/CharacterDetailPage.kt` | 2 |
| 8 | `app/src/main/java/com/kitsugi/animelist/AppNavigation.kt` | 2 |
| 9 | `app/src/main/java/com/kitsugi/animelist/AppRoot.kt` | 2 |
| 10 | `app/src/main/java/com/kitsugi/animelist/AppRootDetailPages.kt` | 2 |
| 11 | `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailComponents.kt` | 3 |

## Test Notları
- [ ] İzle butonu: Simkl/TMDB/anime detay sayfalarında küçük (ikon+yazı); odak TV'de çalışıyor.
- [ ] Lanterns (Simkl, canlı dizi): "Hal Jordan / Halogram" → TMDB karakter sayfası
      (Kyle Chandler "Oyuncu" satırı) açılıyor.
- [ ] Spider-Man: Brand New Day (TMDB, canlı film): "Peter Parker / Spider-Man",
      "MJ" vb. → doğrudan TMDB karakter sayfası açılıyor (yanlış anime karakteri değil).
- [ ] Anime (ör. Frieren): karakterler AniList/MAL üzerinden açılmaya devam ediyor.
- [ ] Bilgiler kartı: her satırda kopyala butonu; "Diğer Adlar" isimleri tek tek kopyalanabiliyor.
