# Kitsugi — "Yakında Yayında" Yayılımı + Karakter/Kişi Birleşik Galeri: Teşhis & Uygulama

**Tarih:** 2026-10-09 · **Dal:** `arena/30a4bbac-kitsugi-beta` · **Temel commit:** `2126157` (v2.4.220)
**Hedef sürüm:** v2.4.221 (`versionCode = 20261009XXX` zaman damgasından türetilir)
**Durum:** Kod değişiklikleri TAMAM; sandbox'ta JDK/Gradle **yok** → derleme doğrulaması kullanıcı tarafında.

---

## 1. Kullanıcı İstekleri

> 1. "MAL'a yeni eklediğimiz kaynaklardan dolayı karakterlere veya seslendirmenlere yeni ekstra
>    resimler gelmiş. Ben diyorum ki artık tüm kaynaklarda olabildiğince tüm kaynaklar birlikte
>    resim verisi versin, oluyorsa bu mantığı düzgünce oturtabilirsen."
> 2. "AL ve MAL'da 'Yakında Yayında' var; TMDB'de de var ancak düzgün çalışamıyor — düzelt.
>    Bu mantığın olmadığı **Kitsu, Shikimori, Bangumi, Simkl** kaynaklarında hem sayfa hem şerit
>    olsun; **Tümü sayfasında da çıksın**."

---

## 2. ÖDEME (Üst Özet)

### Görev A — Birleşik Karakter/Kişi Galerisi

Yeni ortak toplayıcı `data/remote/KitsugiPersonImageAggregator.kt`:

| Kaynak | Kimlik çözümü | Görsel kaynağı |
|---|---|---|
| MAL (Jikan) | bilinen MAL kimliği (jikan/mal sayfa, Shikimori aynası, Kitsu→Jikan dalı) | `/characters|people/{id}/pictures` (çoklu) |
| AniList | detaydan `aniListId` veya sayfa kimliği; yoksa isim araması (sıkı eşleşme) | `image.large` |
| Shikimori | MAL kimliği (Shikimori kimlik uzayı = MAL) | `image.original` |
| Kitsu | kaynak kitsu ise kimlik; yoksa `filter[name]` araması (sıkı) | `attributes.image.original` |
| Bangumi | kaynak bangumi ise kimlik (stableId çözümü); yoksa `/v0/search/characters|persons` (sıkı) | `images.large` (+ detay yedeği) |
| TMDB | kaynak tmdb ise kişi kimliği; yoksa `search/person` (sıkı) | `/person/{id}/images` profil dizisi (en çok 12) |

- Düzen sırası: birincil (sayfa) görseli → MAL → AniList → Shikimori → Kitsu → Bangumi → TMDB;
  `distinctBy(url)` ile tekilleştirme; her görsel kendi kaynak etiketi/badgesini taşır.
- Bütçe: tüm kaynaklar paralel, toplam ≤ 9 sn; süre dolarsa kalan iptal, kısmi sonuç döner.
- Güvenlik: isim eşleşmesi **normalize tam eşitlik** veya **jeton-kümesi eşitliği** (Japonca ad sırası);
  MAL kimliği yalnızca kanıtlı uzaylardan türetilir → yanlış kişi/karakter görseli riski yok.
- TMDB katkısı `tmdbEnabled` ayarına uyar; kurgusal karakterlerde TMDB atlanır (Kitsu da kişilerde atlanır).

Bağlantılar: `CharacterDetailViewModel.buildCharacterGallery` ve `StaffDetailViewModel.buildStaffGallery`
artık toplayıcıyı kullanır; eski tek-kaynak `fetchJikanPictures` kopyaları kaldırıldı
(Jikan çağrısı toplayıcı internal `JikanGateway` zinciri üzerinden devam eder).

### Görev B — "Yakında Yayında" Her Yerde

1. **TMDB haftalık takvim düzeltmesi** (`KitsugiAiringCalendarClient.fetchTmdbWeeklySchedule`):
   - Eski: `tv/on_the_air` + `first_air_date` → uzun soluklu dizi ilk yayın tarihinin (yıllar önceki)
     hafta gününe düşüyor; geçmiş epoch, sahte `Bölüm 1`, rastgele günler.
   - Yeni: haftanın HER GÜNÜ için `discover/tv?air_date.gte=D&air_date.lte=D` (TMDB'nin bölüm-bazlı
     gerçek yayın filtresi) → kayıtlar gerçek yayın gününe; filmler `discover/movie` ile haftalık
     aralıkta kendi vizyon günlerine; `episode = -1` işareti "bölüm numarası bilinmiyor" (UI "Dizi"
     rozeti gösterir).
2. **TMDB şerit düzeltmeleri** (`ExploreViewModel.loadTmdbData` + prefetch):
   - `nextAiringEpisode = "-1|epoch"` (TMDB upcoming konvansiyonu) → sayaç "çıkıyor" + doğru vurgu rengi.
   - Alt yazı: "1. Bölüm" yerine "Dizi / Film".
   - Dedupe anahtarına medya türü eklendi (TMDB film/dizi kimlikleri çakışabilir).
3. **Kitsu / Shikimori / Bangumi / Simkl**: kesin bölüm saatlerini hiçbiri sağlamadığından şerit
   ve takvim **ortak takvim verisiyle** beslenir (`fetchSharedAiringSoon` — gerçek AniList/MAL
   kimlikleri; takvim sayfasının mevcut sözleşmesi). Simkl'in saatsiz "best/upcoming" listesi şeritten
   kalktı (geri sayım yoktu); yerel prömiyer ızgarası kategori çipinde korundu.
4. **Tümü sayfası**: kaynak alanlarının üstünde tek ortak geri sayımlı şerit (AniList→MAL→TMDB önceliği);
   ok → haftalık takvim; kaynak-atlama indekslerine ek öğe hesaba katıldı
   (`extraItemsAfterIntro`); `owned()` artık `airingSoonAnime`'yi filtreleMeZ (ortak, gerçek kimlikli veri).
5. **Şerit ok hedefi** (TMDB/SIMKL): ızgara "tümü" sayfası yerine haftalık takvim (diğer kaynaklarla aynı
   sözleşme; ızgara kategori çiplerinden erişilebilir).

---

## 3. Değişen Dosyalar

| Dosya | Değişiklik |
|---|---|
| `data/remote/KitsugiPersonImageAggregator.kt` | **YENİ** — çoklu-kaynak toplayıcı |
| `ui/screens/detail/CharacterDetailViewModel.kt` | galeri → toplayıcı; kullanılmayan import temizliği |
| `ui/screens/detail/StaffDetailViewModel.kt` | galeri → toplayıcı; kullanılmayan import temizliği |
| `data/remote/KitsugiAiringCalendarClient.kt` | TMDB haftalık per-gün `air_date` düzeltmesi; parseTmdbList override+tür-duyarlı dedupe |
| `ui/screens/explore/KitsugiAiringCalendarComponents.kt` | `episode < 0` → "Dizi" rozeti/rozet-metni |
| `ui/screens/explore/ExploreViewModel.kt` | `fetchSharedAiringSoon`; 4 loader'a şerit verisi; TMDB eşleme düzeltmeleri (2 yer: loader + prefetch) |
| `ui/screens/explore/ExploreScreen.kt` | TMDB/SIMKL oku → takvim; Tümü ortak şeridi (`sharedAiringSoon`, `airingSoonTitle`) |
| `ui/screens/explore/AllSourcesExploreContent.kt` | `airingSoonShelf/title/onOpenAiringCalendar` parametreleri + şerit öğesi |
| `ui/screens/explore/AllSourcesExplore.kt` | `owned()` airingSoonAnime filtresi kaldırıldı; `allSourceHeaderIndices(extraItemsAfterIntro)` |
| `app/src/test/.../AllSourcesExploreTest.kt` | +2 birim test |
| `app/build.gradle.kts` | 2.4.220 → **2.4.221** |
| `RELEASE_NOTES.md` | v2.4.221 TR + EN bölümleri |

---

## 4. Doğrulama Adımları (kullanıcı tarafı)

1. **TMDB sekmesi → Keşfet:** "Yakında Yayında" şeridi "X gün sonra çıkıyor" geri sayımı ile; ok →
   haftalık takvim; diziler doğru günlerde, "Dizi" rozeti ile; bu hafta vizyona giren filmler kendi
   günlerinde "Film" rozetiyle.
2. **Kitsu/Shikimori/Bangumi/Simkl sekmeleri → Keşfet:** geri sayımlı şerit görünür; kart → detay açılır
   (AniList rotası); kategori çipi "Yakında Yayında" takvimi açar.
3. **Tümü sekmesi:** üstte tek ortak şerit + ok → takvim; kaynak başlıklarına atlama doğru çalışır.
4. **Karakter detayı (her kaynaktan):** galeri diyaloğunda "Diğer Resimler" sekmesinde MAL/AniList/
   Shikimori/Kitsu/Bangumi rozetleri; listenin ilk resmi açılan kaynağın görseli.
5. **Seslendirmen/kişi detayı:** MAL durumunda Jikan ekleri + AniList/Shikimori/Bangumi katkıları;
   TMDB kişilerinde profil fotoğrafı dizisi; yalnızca doğru kişinin görselleri (sıkı isim eşleşmesi).
6. `./gradlew :app:compileFossDebugKotlin :app:testFossDebugUnitTest`
   (AllSourcesExploreTest dahil yeşil olmalı)

## 5. Notlar / Sınırlar

- Sandbox'ta ağ erişimi kısıtlı (yalnız github/npm/pypi) → TMDB/jikan/kitsu canlı doğrulaması YAPILAMADI;
  tasarım TMDB `discover/tv?air_date.*` (TMDB dokümanı: bölüm-bazlı filtre) ve Kitsu `filter[name]`
  (resmî Characters filtresi) ile doğrulandı.
- İsim bazlı çözümlemeler bilinçli **konservatif**: eşleşme yoksa katkı yok (yanlış görselden iyi).
- Kitsu kişi/personel browse uçları görsel sağlamaz → Kitsu yalnızca karakterlerde katkı verir.
