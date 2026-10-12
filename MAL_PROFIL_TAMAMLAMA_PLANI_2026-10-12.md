# MAL Profil Tamamlama Planı - Kapsamlı Analiz ve Uygulama Rehberi

**Tarih:** 2026-10-12  
**Amaç:** MyAnimeList profil sayfasında AniList'te olup MAL'de eksik olan tüm özellikleri tamamlamak ve MAL'in web API'si ile sunabileceği her şeyi araştırmak

---

## 📊 MEVCUT DURUM ANALİZİ

### ✅ Şu Anda MAL'de Olan Özellikler

**1. Profil Header:**
- ✅ Avatar resmi
- ✅ Kullanıcı adı
- ✅ Konum
- ✅ Katılım tarihi
- ✅ Paylaşım butonu
- ❌ Banner resmi (AniList'te var, MAL'de yok - gradient arka plan kullanılıyor)

**2. Hakkında Sekmesi (Info Tab):**
- ✅ Temel bilgiler (cinsiyet, doğum günü, son görülme)
- ✅ Hakkında yazısı (düz metin)
- ✅ Anime/Manga istatistik kartları
- ❌ HTML formatlı hakkında (AniList'te var)
- ❌ Çeviri butonu
- ❌ Kopyalama butonu
- ❌ Donator rozeti

**3. Aktivite Sekmesi (Activity Tab):**
- ✅ Son güncellemeler (recent updates)
- ✅ İzleme/okuma geçmişi (history)
- ❌ Zengin aktivite akışı (AniList'te like, reply, text activities var)
- ❌ Medya görselleri ile aktivite kartları

**4. İstatistik Sekmesi (Statistics Tab):**
- ✅ Temel sayılar (toplam kayıt, ortalama puan)
- ✅ Durum dağılımı (watching, completed, on-hold, dropped, plan to watch)
- ✅ Bölgesel dağılım çubuğu (segmented bar)
- ❌ Puan dağılımı grafiği (score distribution - AniList'te var)
- ❌ Bölüm/uzunluk dağılımı (length distribution)
- ❌ Format dağılımı (TV, Movie, OVA, ONA, vb.)
- ❌ Ülke dağılımı (country distribution)
- ❌ Yayın yılı dağılımı (release year distribution)
- ❌ İzleme/okuma yılı dağılımı (watch/read year distribution)
- ❌ Tür sıralaması (genres ranking)
- ❌ Etiket sıralaması (tags ranking)
- ❌ Personel sıralaması (staff ranking)
- ❌ Seslendirme sanatçısı sıralaması (voice actors ranking)
- ❌ Stüdyo sıralaması (studios ranking)

**5. Favoriler Sekmesi (Favorites Tab):**
- ✅ Favori anime
- ✅ Favori manga
- ✅ Favori karakterler
- ✅ Favori personel (people)
- ❌ Favori stüdyolar (AniList'te var, MAL'de yok)
- ❌ Otomatik sayfalama (auto-load more)

**6. Sosyal Sekmesi (Social Tab):**
- ✅ Arkadaşlar listesi (friends)
- ❌ Takipçiler listesi (followers - AniList'te var)
- ❌ Takip edilenler listesi (following - AniList'te var)
- ❌ Tıklanabilir kullanıcı profilleri

---

## 🔍 MAL WEB SAYFALARINDAN ÇEKİLEBİLECEK VERİLER

### Mevcut MalWebParser Yetenekleri

Şu anda parse edilen sayfalar:
1. `/profile/{username}` - Ana profil sayfası
2. `/profile/{username}/friends?p=N` - Arkadaşlar sayfası
3. `/history/{username}` - İzleme/okuma geçmişi

### 🆕 Eklenebilecek MAL Sayfaları

**1. İstatistik Alt Sayfaları:**
- `/profile/{username}/statistics` - Temel istatistikler (zaten alınıyor)
- `/profile/{username}/statistics/anime` - Detaylı anime istatistikleri
- `/profile/{username}/statistics/manga` - Detaylı manga istatistikleri
- `/profile/{username}/statistics/anime/score` - Puan dağılımı
- `/profile/{username}/statistics/anime/type` - Format dağılımı
- `/profile/{username}/statistics/anime/rating` - Derecelendirme dağılımı
- `/profile/{username}/statistics/anime/status` - Durum dağılımı
- `/profile/{username}/statistics/anime/genre` - Tür istatistikleri
- `/profile/{username}/statistics/anime/studio` - Stüdyo istatistikleri
- `/profile/{username}/statistics/anime/voice_actor` - Seslendirme sanatçısı istatistikleri
- `/profile/{username}/statistics/anime/producer` - Yapımcı istatistikleri
- Aynı pattern manga için de geçerli

**2. Liste Sayfaları:**
- `/animelist/{username}?status=1` - Watching
- `/animelist/{username}?status=2` - Completed
- `/animelist/{username}?status=3` - On-Hold
- `/animelist/{username}?status=4` - Dropped
- `/animelist/{username}?status=6` - Plan to Watch
- `/mangalist/{username}?status=1` - Reading
- `/mangalist/{username}?status=2` - Completed
- `/mangalist/{username}?status=3` - On-Hold
- `/mangalist/{username}?status=4` - Dropped
- `/mangalist/{username}?status=6` - Plan to Read

**3. İncelemeler ve Öneriler:**
- `/profile/{username}/reviews` - Kullanıcının yazdığı incelemeler
- `/profile/{username}/recommendations` - Kullanıcının yaptığı öneriler

**4. Kulüpler:**
- `/profile/{username}/clubs` - Üye olunan kulüpler

**5. Forum Aktivitesi:**
- `/profile/{username}/forum` - Forum gönderileri
- `/profile/{username}/forum/topics` - Açılan konular
- `/profile/{username}/forum/posts` - Yapılan yorumlar

**6. Profil Yorumları:**
- `/profile/{username}/comments` - Profil sayfasındaki yorumlar

---

## 📋 UYGULAMA PLANI - ÖNCELİK SIRASI

### 🎯 FAZA 1: Kritik Eksiklikler (Yüksek Öncelik)

#### 1.1. İstatistik Sekmesi Genişletme
**Amaç:** AniList'teki detaylı istatistikleri MAL'e getirmek

**Görevler:**
- [ ] `MalWebParser`'a istatistik alt sayfalarını parse etme özelliği ekle
  - [ ] `parseAnimeStatistics()` - `/profile/{username}/statistics/anime`
  - [ ] `parseMangaStatistics()` - `/profile/{username}/statistics/manga`
  - [ ] `parseScoreDistribution()` - Puan dağılımı
  - [ ] `parseTypeDistribution()` - Format dağılımı (TV, Movie, OVA, vb.)
  - [ ] `parseStatusDistribution()` - Durum dağılımı
  - [ ] `parseGenreStatistics()` - Tür sıralaması
  - [ ] `parseStudioStatistics()` - Stüdyo sıralaması
  - [ ] `parseVoiceActorStatistics()` - Seslendirme sanatçısı sıralaması
  
- [ ] `MalWebJson`'a yeni JSON üretme fonksiyonları ekle
  - [ ] `scoreDistribution()` 
  - [ ] `typeDistribution()`
  - [ ] `genreStatistics()`
  - [ ] `studioStatistics()`
  - [ ] `voiceActorStatistics()`

- [ ] `MalProfileState`'e yeni alanlar ekle
  ```kotlin
  val animeScoreDistribution: List<ScoreStatItem> = emptyList()
  val mangaScoreDistribution: List<ScoreStatItem> = emptyList()
  val animeTypeDistribution: List<FormatStatItem> = emptyList()
  val mangaTypeDistribution: List<FormatStatItem> = emptyList()
  val animeGenreStats: List<RankedStatItem> = emptyList()
  val mangaGenreStats: List<RankedStatItem> = emptyList()
  val animeStudioStats: List<RankedStatItem> = emptyList()
  val animeVoiceActorStats: List<RankedStatItem> = emptyList()
  ```

- [ ] `MalProfileContent.kt` İstatistik sekmesini genişlet
  - [ ] Alt sekmeler ekle: Genel, Türler, Stüdyolar, Seslendirme Sanatçıları
  - [ ] Puan dağılımı grafiği (VerticalStatsBar)
  - [ ] Format dağılımı grafiği (HorizontalStatsBar)
  - [ ] Tür sıralaması listesi (PositionalStatItemCard)
  - [ ] Stüdyo sıralaması listesi
  - [ ] Seslendirme sanatçısı sıralaması listesi

**Tahmini Süre:** 3-4 gün

---

#### 1.2. Sosyal Sekmesi Genişletme
**Amaç:** Takipçi/takip edilen ayrımını getirmek (MAL'de "friends" tek liste)

**Araştırma:**
- MAL'de "followers" ve "following" kavramı yok, sadece "friends" var
- `/profile/{username}/friends` sayfası zaten parse ediliyor
- **ÇÖZÜM:** Mevcut "friends" listesini kullan, ama UI'da "Arkadaşlar" olarak göster
- Alternatif: MAL'in yeni özelliklerini kontrol et (bazı kullanıcılarda followers/following olabilir)

**Görevler:**
- [ ] MAL profil sayfasında "followers" ve "following" olup olmadığını kontrol et
- [ ] Varsa `MalWebParser.parseFollowers()` ve `parseFollowing()` ekle
- [ ] Yoksa mevcut friends listesini kullan, UI'da "Arkadaşlar" etiketi göster
- [ ] Tıklanabilir kullanıcı profilleri ekle (onUserProfileClick callback)

**Tahmini Süre:** 0.5-1 gün

---

#### 1.3. Banner Desteği
**Amaç:** MAL profil banner'ını çekmek

**Araştırma:**
- MAL profil sayfalarında banner resmi var mı?
- Varsa hangi HTML elementinde?
- Örnek: `.user-image img` avatar için, banner için ne var?

**Görevler:**
- [ ] MAL profil sayfasını incele, banner varsa selector'ı bul
- [ ] `MalWebParser.Profile`'a `bannerUrl` alanı ekle
- [ ] `MalProfileState`'e `bannerUrl` ekle
- [ ] `MalProfileContent` header'ında banner'ı göster (AniList'teki gibi)

**Tahmini Süre:** 0.5 gün

---

### 🎯 FAZA 2: Gelişmiş Özellikler (Orta Öncelik)

#### 2.1. Hakkında Sekmesi Zenginleştirme
**Amaç:** HTML formatlı hakkında ve etkileşim butonları

**Görevler:**
- [ ] `MalProfileState.about` alanını HTML olarak parse et (şu anda düz metin)
- [ ] `KitsugiHtmlWebView` component'ini kullanarak HTML'i render et
- [ ] Çeviri butonu ekle (AniList'teki gibi)
- [ ] Kopyalama butonu ekle
- [ ] Görselleri tıklanabilir yap (onImageClick callback)

**Tahmini Süre:** 1 gün

---

#### 2.2. Favoriler Sayfalama
**Amaç:** Otomatik daha fazla yükleme (AniList'teki gibi)

**Araştırma:**
- MAL favoriler sayfası sayfalı mı?
- Varsa URL pattern'i ne? `/profile/{username}/favorites?p=2`?

**Görevler:**
- [ ] MAL favorites sayfasını incele, pagination var mı?
- [ ] Varsa `MalWebParser.parseFavoritesPage()` ekle
- [ ] `MalProfileState`'e pagination state'leri ekle (favAnimePage, favAnimeHasNext, vb.)
- [ ] `ProfileFavoritesAutoLoad` component'ini MAL için de aktifleştir
- [ ] `KitsugiProfileViewModel`'e `loadMoreMalFavorites()` fonksiyonu ekle

**Tahmini Süre:** 1-2 gün

---

#### 2.3. Aktivite Sekmesi Zenginleştirme
**Amaç:** Daha zengin aktivite kartları

**Görevler:**
- [ ] Mevcut `MalActivityCard` component'ini geliştir
- [ ] Medya görsellerini büyüt (44x62 → 60x85)
- [ ] Puan ve ilerleme bilgisini daha belirgin göster
- [ ] Tıklanabilir her yeri (şu anda sadece row clickable)
- [ ] Boş durum mesajını güzelleştir

**Tahmini Süre:** 0.5 gün

---

### 🎯 FAZA 3: Ekstra Özellikler (Düşük Öncelik)

#### 3.1. İncelemeler Sayfası
**Amaç:** Kullanıcının yazdığı incelemeleri göstermek

**Görevler:**
- [ ] `MalWebParser.parseReviews()` ekle
- [ ] `MalWebJson.reviews()` ekle
- [ ] Yeni sekme veya modal: "İncelemeler"
- [ ] İnceleme kartları (başlık, puan, tarih, metin özeti)

**Tahmini Süre:** 2 gün

---

#### 3.2. Öneriler Sayfası
**Amaç:** Kullanıcının yaptığı anime/manga önerilerini göstermek

**Görevler:**
- [ ] `MalWebParser.parseRecommendations()` ekle
- [ ] Yeni sekme veya modal: "Öneriler"
- [ ] Öneri kartları (önerilen eser, öneri metni, tarih)

**Tahmini Süre:** 1.5 gün

---

#### 3.3. Kulüpler Sayfası
**Amaç:** Üye olunan kulüpleri göstermek

**Görevler:**
- [ ] `MalWebParser.parseClubs()` ekle
- [ ] Yeni sekme veya modal: "Kulüpler"
- [ ] Kulüp kartları (isim, görsel, üye sayısı)

**Tahmini Süre:** 1 gün

---

#### 3.4. Forum Aktivitesi
**Amaç:** Forum gönderilerini göstermek

**Görevler:**
- [ ] `MalWebParser.parseForumTopics()` ve `parseForumPosts()` ekle
- [ ] Yeni sekme: "Forum"
- [ ] Forum gönderileri listesi (başlık, kategori, tarih, yanıt sayısı)

**Tahmini Süre:** 2 gün

---

## 🔬 TEKNİK ARAŞTIRMA GÖREVLERİ

### 1. MAL Profil Sayfası HTML Yapısı İncelemesi

**Araştırılacaklar:**
```bash
# Örnek profil sayfası indir
curl -H "User-Agent: Mozilla/5.0" "https://myanimelist.net/profile/Xinil" > mal_profile_sample.html

# İstatistik sayfaları
curl -H "User-Agent: Mozilla/5.0" "https://myanimelist.net/profile/Xinil/statistics" > mal_stats.html
curl -H "User-Agent: Mozilla/5.0" "https://myanimelist.net/profile/Xinil/statistics/anime" > mal_anime_stats.html

# Favoriler sayfalama
curl -H "User-Agent: Mozilla/5.0" "https://myanimelist.net/profile/Xinil/favorites?p=2" > mal_favorites_page2.html

# İncelemeler
curl -H "User-Agent: Mozilla/5.0" "https://myanimelist.net/profile/Xinil/reviews" > mal_reviews.html

# Öneriler
curl -H "User-Agent: Mozilla/5.0" "https://myanimelist.net/profile/Xinil/recommendations" > mal_recommendations.html

# Kulüpler
curl -H "User-Agent: Mozilla/5.0" "https://myanimelist.net/profile/Xinil/clubs" > mal_clubs.html
```

**İncelenecek HTML Elementleri:**
- Banner resmi: `.user-image`, `.profile-banner`, `header` içinde `img`
- İstatistik alt sayfaları: `.statistics-updates`, `.stats-graph`, `.di-tc`
- Puan dağılımı: `#statistics-anime-score`, `.stats-graph .bar`
- Format dağılımı: `#statistics-anime-type`
- Tür istatistikleri: `#statistics-anime-genre`
- Stüdyo istatistikleri: `#statistics-anime-studio`
- Seslendirme: `#statistics-anime-voice_actor`

### 2. Jikan API Karşılaştırması

**Kontrol edilecek uçlar:**
```
GET /users/{username}/full - Tam profil
GET /users/{username}/statistics - İstatistikler
GET /users/{username}/favorites - Favoriler
GET /users/{username}/userupdates - Son güncellemeler
GET /users/{username}/about - Hakkında
GET /users/{username}/history - Geçmiş
GET /users/{username}/friends - Arkadaşlar
GET /users/{username}/reviews - İncelemeler
GET /users/{username}/recommendations - Öneriler
GET /users/{username}/clubs - Kulüpler
GET /users/{username}/forum - Forum
```

**Soru:** Jikan bu uçları destekliyor mu? Varsa neden cihaz üzerinde parse etmiyoruz?

**Cevap:** Jikan destekliyor ama:
1. Merkezi sunucu IP'si ban riski yüksek
2. Rate limit çok sıkı (3 req/s)
3. Cihaz üzerinde parse daha hızlı ve güvenilir
4. Her kullanıcı kendi IP'sinden istek atıyor

---

## 📝 UYGULAMA ADIMLARI (DETAILED)

### Adım 1: MalWebParser Genişletme

**Dosya:** `app/src/main/java/com/kitsugi/animelist/data/remote/malweb/MalWebParser.kt`

**Eklenecek Fonksiyonlar:**

```kotlin
// 1. Detaylı Anime İstatistikleri
data class DetailedAnimeStats(
    val scoreDistribution: List<ScoreBucket>,
    val typeDistribution: List<TypeBucket>,
    val statusDistribution: List<StatusBucket>,
    val genreStats: List<GenreStat>,
    val studioStats: List<StudioStat>,
    val voiceActorStats: List<VoiceActorStat>,
    val ratingDistribution: List<RatingBucket>
)

data class ScoreBucket(val score: Int, val count: Int, val percentage: Double)
data class TypeBucket(val type: String, val count: Int, val percentage: Double)
data class StatusBucket(val status: String, val count: Int, val percentage: Double)
data class GenreStat(val genreId: Int, val name: String, val count: Int, val meanScore: Double)
data class StudioStat(val studioId: Int, val name: String, val count: Int, val meanScore: Double)
data class VoiceActorStat(val vaId: Int, val name: String, val count: Int, val meanScore: Double)
data class RatingBucket(val rating: String, val count: Int, val percentage: Double)

fun parseDetailedAnimeStatistics(html: String): DetailedAnimeStats? {
    val doc = Jsoup.parse(html, MAL_BASE)
    // TODO: Implement parsing logic
}

// 2. İncelemeler
data class Review(
    val reviewId: Int,
    val malId: Int,
    val kind: String,
    val title: String,
    val url: String,
    val image: Images,
    val score: Int,
    val helpfulCount: Int,
    val dateIso: String?,
    val textPreview: String
)

fun parseReviews(html: String): List<Review>? {
    val doc = Jsoup.parse(html, MAL_BASE)
    // TODO: Implement parsing logic
}

// 3. Öneriler
data class Recommendation(
    val recommendationId: Int,
    val malId: Int,
    val kind: String,
    val title: String,
    val url: String,
    val image: Images,
    val recommendationText: String,
    val dateIso: String?
)

fun parseRecommendations(html: String): List<Recommendation>? {
    val doc = Jsoup.parse(html, MAL_BASE)
    // TODO: Implement parsing logic
}

// 4. Kulüpler
data class Club(
    val clubId: Int,
    val name: String,
    val url: String,
    val image: Images,
    val memberCount: Int,
    val category: String?
)

fun parseClubs(html: String): List<Club>? {
    val doc = Jsoup.parse(html, MAL_BASE)
    // TODO: Implement parsing logic
}
```

### Adım 2: MalWebSource Endpoint Ekleme

**Dosya:** `app/src/main/java/com/kitsugi/animelist/data/remote/malweb/MalWebSource.kt`

**Eklenecek Endpointler:**

```kotlin
private val USER_PATH = Regex(
    """^users/([A-Za-z0-9_\-]{2,32})(?:/(full|statistics|favorites|about|userupdates|friends|history|reviews|recommendations|clubs))?/?$"""
)

// fetch() fonksiyonuna ekle:
"reviews" -> {
    val html = when (val r = page("/profile/$u/reviews")) {
        is PageResult.Html -> r.html
        is PageResult.Error -> return r.result
    }
    val reviews = MalWebParser.parseReviews(html) ?: return unexpectedPage("/profile/$u/reviews")
    JikanResult.Ok(MalWebJson.reviews(reviews).toString())
}

"recommendations" -> {
    val html = when (val r = page("/profile/$u/recommendations")) {
        is PageResult.Html -> r.html
        is PageResult.Error -> return r.result
    }
    val recs = MalWebParser.parseRecommendations(html) ?: return unexpectedPage(path)
    JikanResult.Ok(MalWebJson.recommendations(recs).toString())
}

"clubs" -> {
    val html = when (val r = page("/profile/$u/clubs")) {
        is PageResult.Html -> r.html
        is PageResult.Error -> return r.result
    }
    val clubs = MalWebParser.parseClubs(html) ?: return unexpectedPage(path)
    JikanResult.Ok(MalWebJson.clubs(clubs).toString())
}
```

### Adım 3: MalWebJson Yeni Üreticiler

**Dosya:** `app/src/main/java/com/kitsugi/animelist/data/remote/malweb/MalWebJson.kt`

**Eklenecek Fonksiyonlar:**

```kotlin
fun detailedAnimeStatistics(stats: MalWebParser.DetailedAnimeStats): String {
    val json = JSONObject()
    // Score distribution
    val scoreArray = JSONArray()
    stats.scoreDistribution.forEach { bucket ->
        scoreArray.put(JSONObject()
            .put("score", bucket.score)
            .put("count", bucket.count)
            .put("percentage", bucket.percentage)
        )
    }
    json.put("scores", scoreArray)
    
    // Type distribution
    val typeArray = JSONArray()
    stats.typeDistribution.forEach { bucket ->
        typeArray.put(JSONObject()
            .put("type", bucket.type)
            .put("count", bucket.count)
        )
    }
    json.put("types", typeArray)
    
    // Genre stats
    val genreArray = JSONArray()
    stats.genreStats.forEach { genre ->
        genreArray.put(JSONObject()
            .put("mal_id", genre.genreId)
            .put("name", genre.name)
            .put("count", genre.count)
            .put("mean_score", genre.meanScore)
        )
    }
    json.put("genres", genreArray)
    
    // ... studio, voice actor stats
    
    return wrap(json)
}

fun reviews(reviews: List<MalWebParser.Review>): String {
    val array = JSONArray()
    reviews.forEach { review ->
        array.put(JSONObject()
            .put("mal_id", review.reviewId)
            .put("anime_mal_id", review.malId)
            .put("title", review.title)
            .put("score", review.score)
            .put("helpful_count", review.helpfulCount)
            .put("date", review.dateIso)
            .put("review", review.textPreview)
        )
    }
    return JSONObject().put("data", array).toString()
}
```

### Adım 4: MalProfileState Genişletme

**Dosya:** `app/src/main/java/com/kitsugi/animelist/ui/app/KitsugiProfileViewModel.kt`

**Eklenecek Alanlar:**

```kotlin
data class MalProfileState(
    // ... mevcut alanlar ...
    
    // Yeni istatistik alanları
    val animeScoreDistribution: List<ScoreStatItem> = emptyList(),
    val mangaScoreDistribution: List<ScoreStatItem> = emptyList(),
    val animeTypeDistribution: List<FormatStatItem> = emptyList(),
    val mangaTypeDistribution: List<FormatStatItem> = emptyList(),
    val animeGenreStats: List<RankedStatItem> = emptyList(),
    val mangaGenreStats: List<RankedStatItem> = emptyList(),
    val animeStudioStats: List<RankedStatItem> = emptyList(),
    val animeVoiceActorStats: List<RankedStatItem> = emptyList(),
    
    // Banner
    val bannerUrl: String? = null,
    
    // İncelemeler, öneriler, kulüpler
    val reviews: List<MalReview> = emptyList(),
    val recommendations: List<MalRecommendation> = emptyList(),
    val clubs: List<MalClub> = emptyList(),
    
    // Pagination
    val favAnimePage: Int = 1,
    val favAnimeHasNext: Boolean = false,
    val favMangaPage: Int = 1,
    val favMangaHasNext: Boolean = false,
    val favCharPage: Int = 1,
    val favCharHasNext: Boolean = false,
    val favStaffPage: Int = 1,
    val favStaffHasNext: Boolean = false,
    
    // Stats sub-tabs
    val statsSubTab: Int = 0  // 0: Genel, 1: Türler, 2: Stüdyolar, 3: Seslendirme
)
```

### Adım 5: MalProfileContent UI Genişletme

**Dosya:** `app/src/main/java/com/kitsugi/animelist/ui/screens/profile/MalProfileContent.kt`

**Değişiklikler:**

1. **Banner Desteği:**
```kotlin
// Header'da banner göster
if (!state.bannerUrl.isNullOrBlank()) {
    AsyncImage(
        model = state.bannerUrl,
        contentDescription = bannerTitle,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
    )
} else {
    // Gradient fallback
}
```

2. **İstatistik Sekmesi Alt Sekmeler:**
```kotlin
// Sticky header'a sub-tabs ekle
if (pagerState.currentPage == 2) {
    LazyRow {
        items(4) { idx ->
            val label = listOf(
                stringResource(R.string.profile_stats_general),
                stringResource(R.string.detail_section_genres),
                stringResource(R.string.detail_section_studios),
                stringResource(R.string.character_tab_voice_actors)
            )[idx]
            ProfileFilterChip(
                text = label,
                isSelected = statsSubTab == idx,
                accentColor = accentColor,
                onClick = { statsSubTab = idx }
            )
        }
    }
}
```

3. **Puan Dağılımı Grafiği:**
```kotlin
// İstatistik sekmesi genel alt sekmesinde
if (state.animeScoreDistribution.isNotEmpty()) {
    Text("Puan Dağılımı", ...)
    VerticalStatsBar(
        stats = state.animeScoreDistribution.map { 
            it.score.toString() to it.count.toFloat() 
        },
        accentColor = accentColor
    )
}
```

4. **Tür/Stüdyo/Seslendirme Sıralaması:**
```kotlin
// Alt sekmelere göre liste göster
val currentList = when (statsSubTab) {
    1 -> state.animeGenreStats
    2 -> state.animeStudioStats
    3 -> state.animeVoiceActorStats
    else -> emptyList()
}

currentList.forEachIndexed { idx, item ->
    PositionalStatItemCard(
        rank = idx + 1,
        title = item.name,
        count = item.count,
        meanScore = item.meanScore,
        imageUrl = item.imageUrl,
        onClick = { /* Navigate to detail */ }
    )
}
```

---

## 🧪 TEST PLANI

### Birim Testleri

1. **MalWebParser Testleri:**
   - [ ] `parseProfile()` - Banner extraction
   - [ ] `parseDetailedAnimeStatistics()` - Score distribution
   - [ ] `parseDetailedAnimeStatistics()` - Type distribution
   - [ ] `parseDetailedAnimeStatistics()` - Genre stats
   - [ ] `parseReviews()` - Review list
   - [ ] `parseRecommendations()` - Recommendation list
   - [ ] `parseClubs()` - Club list

2. **MalWebJson Testleri:**
   - [ ] `detailedAnimeStatistics()` - JSON format
   - [ ] `reviews()` - JSON format
   - [ ] `recommendations()` - JSON format

### Entegrasyon Testleri

1. **Gerçek MAL Profilleri ile Test:**
   - [ ] Xinil (MAL kurucusu) - Zengin profil
   - [ ] Kullanıcı profili - Orta düzey aktivite
   - [ ] Yeni kullanıcı - Minimal veri
   - [ ] Özel profil (private) - Hata yönetimi

2. **Edge Cases:**
   - [ ] İstatistiği olmayan kullanıcı
   - [ ] Favorisi olmayan kullanıcı
   - [ ] İncelemesi olmayan kullanıcı
   - [ ] Kulüp üyeliği olmayan kullanıcı
   - [ ] Banner olmayan kullanıcı

### UI Testleri

1. **İstatistik Sekmesi:**
   - [ ] Puan dağılımı grafiği render oluyor mu?
   - [ ] Format dağılımı grafiği render oluyor mu?
   - [ ] Alt sekmeler arasında geçiş çalışıyor mu?
   - [ ] Tür sıralaması listesi tıklanabilir mi?
   - [ ] Stüdyo sıralaması listesi tıklanabilir mi?

2. **Banner:**
   - [ ] Banner olan profillerde gösteriliyor mu?
   - [ ] Banner olmayan profillerde gradient gösteriliyor mu?
   - [ ] Banner tıklanınca galeri açılıyor mu?

3. **Performans:**
   - [ ] İstatistik sayfası parse süresi < 500ms
   - [ ] UI render süresi < 100ms
   - [ ] Scroll performansı (60 FPS)

---

## 📊 BAŞARI KRİTERLERİ

### Fonksiyonel Gereksinimler
- ✅ MAL istatistik sekmesi AniList ile aynı seviyede
- ✅ Puan dağılımı grafiği çalışıyor
- ✅ Format dağılımı grafiği çalışıyor
- ✅ Tür/Stüdyo/Seslendirme sıralaması çalışıyor
- ✅ Banner desteği eklendi
- ✅ Hakkında sekmesi HTML formatlı
- ✅ Çeviri ve kopyalama butonları eklendi

### Performans Gereksinimleri
- ✅ İstatistik parse süresi < 500ms
- ✅ UI render süresi < 100ms
- ✅ Scroll performansı 60 FPS
- ✅ Memory leak yok

### Kullanıcı Deneyimi
- ✅ AniList kullanıcıları MAL profilinde aynı konforu buluyor
- ✅ İstatistikler görsel ve anlaşılır
- ✅ Navigasyon akıcı
- ✅ Boş durumlar kullanıcı dostu

---

## 🚀 YAYIN PLANI

### Sprint 1 (Hafta 1-2): Kritik Özellikler
- [ ] İstatistik sekmesi genişletme (Faza 1.1)
- [ ] Banner desteği (Faza 1.3)
- [ ] Hakkında sekmesi zenginleştirme (Faza 2.1)

### Sprint 2 (Hafta 3): Gelişmiş Özellikler
- [ ] Sosyal sekmesi genişletme (Faza 1.2)
- [ ] Favoriler sayfalama (Faza 2.2)
- [ ] Aktivite sekmesi zenginleştirme (Faza 2.3)

### Sprint 3 (Hafta 4): Ekstra Özellikler
- [ ] İncelemeler sayfası (Faza 3.1)
- [ ] Öneriler sayfası (Faza 3.2)
- [ ] Kulüpler sayfası (Faza 3.3)
- [ ] Forum aktivitesi (Faza 3.4)

---

## 📚 KAYNAKLAR

### Referans Projeler
1. **Jikan API** - https://jikan.moe/
   - MAL unofficial API
   - HTML parsing mantığı referans alınabilir

2. **Miribyou** - https://github.com/nattadasu/miribyou
   - Server-side MAL scraper
   - Endpoint yapısı referans alınabilir

3. **AniHyou** - https://github.com/axiel7/AniHyou-android
   - AniList Android client
   - UI/UX referans alınabilir

### Dokümantasyon
1. **Jsoup** - https://jsoup.org/cookbook/
   - HTML parsing kütüphanesi
   - Selector syntax

2. **MAL HTML Yapısı**
   - Profil sayfası: `.user-profile`, `.user-image`, `.user-status`
   - İstatistikler: `.stats.anime`, `.stats.manga`, `.di-tc`
   - Favoriler: `#anime_favorites`, `#manga_favorites`

---

## ⚠️ RİSKLER VE ÖNLEMLER

### 1. MAL Rate Limiting
**Risk:** Çok fazla istek atınca IP ban
**Önlem:** 
- `MIN_INTERVAL_MS = 1_500L` zaten var
- `BLOCK_COOLDOWN_MS = 10L * 60L` zaten var
- Cache mekanizması zaten var

### 2. HTML Yapısı Değişikliği
**Risk:** MAL HTML yapısını değiştirince parser bozulur
**Önlem:**
- Try-catch blokları ekle
- Null-safe parsing
- Fallback mekanizmaları (miribyou/Jikan)

### 3. Performans Düşüşü
**Risk:** Çok fazla parse işlemi UI'yı yavaşlatır
**Önlem:**
- Background thread'de parse et
- Lazy loading kullan
- Pagination uygula

### 4. Memory Leak
**Risk:** Büyük HTML document'leri memory'de kalır
**Önlem:**
- Parse sonrası document'i null yap
- WeakReference kullan
- GC'yi tetikle

---

## 📝 NOTLAR

### Önemli Hatırlatmalar
1. **Cihaz Üzeri Parsing:** Her zaman cihaz üzerinde parse et, merkezi sunucuya yük bindirme
2. **Fallback Mekanizması:** MalWebSource başarısız olursa miribyou/Jikan'a düş
3. **Cache:** Aynı sayfayı birden fazla kez indirme, 10 dakika cache'le
4. **Rate Limit:** İki istek arası en az 1.5 saniye bekle
5. **User-Agent:** Gerçekçi bir User-Agent kullan (Mobile Chrome)
6. **Error Handling:** Beklenmeyen sayfa (captcha, bakım) gelirse 10 dakika cooldown

### Kodlama Standartları
1. **Kotlin Conventions:** Null-safety, data classes, sealed classes
2. **Compose Best Practices:** State hoisting, remember, LaunchedEffect
3. **Performance:** LazyColumn, LazyRow, key'ler, stable composables
4. **Testing:** Unit tests, integration tests, UI tests

### Dokümantasyon
1. **KDoc:** Her public fonksiyon için açıklama
2. **Inline Comments:** Karmaşık parsing mantığı için yorumlar
3. **README:** Yeni özellikler için güncelleme

---

## ✅ KONTROL LİSTESİ

### Faza 1: Kritik Eksiklikler
- [ ] 1.1 İstatistik sekmesi genişletme
  - [ ] MalWebParser genişletildi
  - [ ] MalWebJson genişletildi
  - [ ] MalProfileState genişletildi
  - [ ] MalProfileContent UI güncellendi
  - [ ] Test edildi
  
- [ ] 1.2 Sosyal sekmesi genişletme
  - [ ] Followers/following araştırıldı
  - [ ] UI güncellendi
  - [ ] Test edildi
  
- [ ] 1.3 Banner desteği
  - [ ] Banner extraction eklendi
  - [ ] UI güncellendi
  - [ ] Test edildi

### Faza 2: Gelişmiş Özellikler
- [ ] 2.1 Hakkında sekmesi zenginleştirme
- [ ] 2.2 Favoriler sayfalama
- [ ] 2.3 Aktivite sekmesi zenginleştirme

### Faza 3: Ekstra Özellikler
- [ ] 3.1 İncelemeler sayfası
- [ ] 3.2 Öneriler sayfası
- [ ] 3.3 Kulüpler sayfası
- [ ] 3.4 Forum aktivitesi

---

## 🎯 SONUÇ

Bu plan ile MAL profil sayfası AniList ile aynı seviyeye gelecek ve hatta bazı alanlarda (incelemeler, öneriler, kulüpler) daha zengin olacak. Cihaz üzeri parsing sayesinde merkezi sunucu yükü artmayacak ve her kullanıcı kendi IP'sinden istek atacak.

**Tahmini Toplam Süre:** 2-3 hafta (sprint bazında)

**Başarı Kriteri:** MAL profil sayfasında kullanıcı "AniList'te olan her şey burada da var" diyebilmeli.

---

**Hazırlayan:** Kitsugi Development Team  
**Tarih:** 2026-10-12  
**Versiyon:** 1.0
