# Kitsugi Beta v2.4.342

## 🇹🇷 Türkçe

### 🔞 Bangumi +18 (R18) Görsellerinde Bulanıklık ve Yetişkin İçerik Düzeltmesi
- **Koleksiyon İçe Aktarımında NSFW Bayrağı Çözümlemesi:** Bangumi kullanıcı koleksiyonu yanıtındaki kısaltılmış `subject` nesnesi `nsfw` alanını taşımadığı için kayıtlar `isAdult=false` olarak ekleniyor ve +18 bulanıklık ayarı açık olsa bile afişler bulanıklaşmıyordu. Yeni `BangumiAdultResolver` ile `GET /v0/subjects/{id}` detayından gerçek `nsfw` değeri çözümlenerek içe aktarıma dahil edildi.
- **Arka Plan Taraması (Migration v2):** Mevcut yerel Bangumi kayıtları için göç sürümü 2'ye yükseltildi. Stable ID → Bangumi `subject_id` dönüşümü yapılarak mevcut yerel kayıtların yetişkin bayrakları arka planda taranır ve güncellenir.
- **Güvenli Önbellekleme & Sağlayıcı İzolasyonu:** Başarılı pozitif/negatif sonuçlar sınırlı bellek önbelleğinde tutulur; ağ hataları önbelleğe alınmaz ve yeniden denenebilir. Bangumi, MAL ve TMDB kimlik alanlarının birbirine karışması önlendi.
- **Birim Testleri:** `AdultFlagBackfillMigrationTest` ile stable ID dönüşümü ve sağlayıcı ad alanı izolasyonu doğrulandı (9 testin tamamı geçti).

### 🌐 Bangumi Başlık Dili Sözleşmesi Tüm Yüzeylerde (İlişkiler, Karakterler, Yapımlar, Kişi Adları)
- **Seçili Dil Artık Her Bangumi Yüzeyinde:** İngilizce / Romaji / Özgün dil tercihi; Detay → İlişkiler & Öneriler, Kişi detayı → Karakterler & Yapımlar & Seslendirenler, Karakter detayı → Yapımlar & Seslendirenler ve Detay → Karakterler & Ekip sekmelerinde aynı zincirle uygulanır (İngilizce → Romaji → Özgün / Romaji → İngilizce → Özgün / Özgün).
- **Yeni `BangumiEntityNameResolver`:** Bangumi karakter ve kişi (seiyuu/staff) adları için AniList GraphQL üzerinde aliased toplu arama + **birebir (normalize) ad eşleşmesi** (`name.native / name.full / name.alternative`). Kısmi eşleşme kesinlikle reddedilir, yanlış kişi adı asla gösterilmez. Çözülen adlar disk önbelleğine (`bangumi_entity_name_cache_v1`) kaydedilir.
- **İlişkiler/Öneriler Ortak Çözümleyiciye Bağlandı:** p1 slim subject listeleri artık `BangumiSubjectTitleResolver` (önbellek → AniList toplu arama → subject infobox → TMDB) ile çözülür; eski yavaş infobox döngüsü kaldırıldı.
- **Arka Plan Isınma Deseni:** İlişkiler, öneriler ve kadro sekmeleri için senkron bütçeyi aşan CJK kayıtlar arka planda çözülerek sekme ve `DetailCache` anında güncellenir; ikinci ziyarette liste tamamen seçili dilde açılır.
- **Birim Testleri:** `BangumiEntityNameResolverTest` (10 testin tamamı geçti).

### 🛠️ Derleme ve Kararlılık
- Android AAPT kaynak derleyicisinde kaçışsız apostroflardan kaynaklanan derleme hataları (`values/strings.xml` ve `values-en/strings.xml`) giderildi.

---

## 🇬🇧 English

### 🔞 Bangumi R18 Image Blur & NSFW Flag Resolution
- **NSFW Resolution on Collection Import:** Bangumi collection responses return slim subject objects lacking the `nsfw` property, causing imported items to remain `isAdult=false` and bypassing user blur settings. With `BangumiAdultResolver`, subject details (`GET /v0/subjects/{id}`) are queried to properly detect adult flags on import.
- **Background Backfill Migration (v2):** Upgraded migration version to 2 for existing local Bangumi records. Resolves Bangumi stable IDs to raw `subject_id`s and backfills adult flags in background batches.
- **Safe Caching & Provider Isolation:** Positive and negative adult lookups are cached in-memory with bounded capacity; network failures remain retryable. Stable namespaces prevent collisions between Bangumi, MAL, and TMDB IDs.
- **Unit Tests:** Validated via `AdultFlagBackfillMigrationTest` (all 9 unit tests passed).

### 🌐 Universal Bangumi Title & Entity Language Contract Across All Surfaces
- **Language Preference Honored Universally:** English / Romaji / Native title language preference is consistently applied across Detail → Relations & Recommendations, Person detail → Characters & Works & Voice actors, Character detail → Works & Voice actors, and Detail → Characters & Staff tabs.
- **New `BangumiEntityNameResolver`:** Implements AniList GraphQL aliased batch queries with **strict normalized name matching** (`name.native / name.full / name.alternative`). Partial matches are strictly rejected. Results are cached persistently on disk (`bangumi_entity_name_cache_v1`).
- **Unified Relations & Recommendations Resolution:** Connected p1 slim subject rows to `BangumiSubjectTitleResolver` (persistent cache → AniList batch → infobox → TMDB fallback).
- **Background Warm Pattern:** Names and titles exceeding the synchronous display budget are resolved in the background and dynamically pushed to tab state and `DetailCache`. Subsequent visits open instantly in the preferred language.
- **Unit Tests:** Validated via `BangumiEntityNameResolverTest` (all 10 unit tests passed).

### 🛠️ Build & Stability
- Fixed AAPT resource compilation errors caused by unescaped ASCII apostrophes across localized string resources.
