# Kitsugi Beta v2.4.339

## 🇹🇷 Türkçe

### 🌐 Bangumi Başlık Dili Sözleşmesi Tüm Yüzeylerde (İlişkiler, Karakterler, Yapımlar, Kişi Adları)
- **Seçili dil artık her Bangumi yüzeyinde geçerli:** İngilizce / Romaji / Özgün dil tercihi;
  Detay → İlişkiler & Öneriler, Kişi detayı → Karakterler & Yapımlar & Seslendirenler,
  Karakter detayı → Yapımlar & Seslendirenler ve Detay → Karakterler & Ekip sekmelerinde
  aynı zincirle uygulanır (İngilizce → Romaji → Özgün / Romaji → İngilizce → Özgün / Özgün).
- **Yeni `BangumiEntityNameResolver`:** Bangumi karakter ve kişi (seiyuu/staff) adları için
  AniList GraphQL üzerinde aliased toplu arama + **birebir (normalize) ad eşleşmesi**
  (`name.native / name.full / name.alternative`). Kısmi eşleşme reddedilir — yanlış kişinin
  adı asla gösterilmez. Sonuçlar kalıcı önbelleğe yazılır (`bangumi_entity_name_cache_v1`);
  bir ekranda çözülen ad diğer tüm ekranlarda ağ isteği olmadan görünür.
- **İlişkiler/Öneriler ortak çözümleyiciye bağlandı:** p1 slim subject listeleri artık
  `BangumiSubjectTitleResolver` (önbellek → AniList toplu arama → subject infobox → TMDB)
  ile çözülür; eski kayıt-başına infobox döngüsü kaldırıldı.
- **Arka plan ısınma deseni tamamlandı:** İlişkiler, öneriler ve kadro sekmeleri için
  senkron bütçeyi aşan CJK ad/başlıklar arka planda çözülür, kalıcı önbelleğe yazılır ve
  sekme ile `DetailCache` anında güncellenir. İkinci ziyarette liste tamamen seçili dilde açılır.
- **Birim testleri:** `BangumiEntityNameResolverTest` — normalize anahtar eşitliği, birebir
  eşleşme (native/full/alternative), kısmi & süper-dize reddi, aliased sorgu kurulumu ve
  `applyResolvedLatinTitle` + `getDisplayTitle` dil zinciri.
- Kök neden raporu: `KOK_NEDEN_RAPORU_BANGUMI_BASLIK_DILI_TUM_YUZEYLER_2026-10-22.md`.

---

## 🇬 English

###  Bangumi Title-Language Contract Across Every Surface (Relations, Characters, Works, Person Names)
- **Selected language now honored on every Bangumi surface:** English / Romaji / Native
  preference applies with one chain (English → Romaji → Native / Romaji → English → Native /
  Native) on Detail → Relations & Recommendations, Person detail → Characters & Works &
  Voice actors, Character detail → Works & Voice actors, and Detail → Characters & Staff tabs.
- **New `BangumiEntityNameResolver`:** AniList GraphQL aliased batch search with **exact
  (normalized) name matching** (`name.native / name.full / name.alternative`) for Bangumi
  character and person (seiyuu/staff) names. Partial matches are rejected — a wrong person's
  name is never shown. Results persist in `bangumi_entity_name_cache_v1`; a name resolved on
  one screen appears on all others without network calls.
- **Relations/Recommendations joined the shared resolver:** p1 slim subject lists now resolve
  via `BangumiSubjectTitleResolver` (cache → AniList batch → subject infobox → TMDB); the old
  per-record infobox loop was removed.
- **Background warm pattern completed:** CJK names/titles exceeding the synchronous budget are
  resolved in background for relations, recommendations and cast tabs, persisted, and pushed
  into the tab state and `DetailCache`. Second visit opens fully in the selected language.
- **Unit tests:** `BangumiEntityNameResolverTest` — normalized key equality, exact matching
  (native/full/alternative), partial & superstring rejection, aliased query construction, and
  the `applyResolvedLatinTitle` + `getDisplayTitle` language chain.
- Root-cause report: `KOK_NEDEN_RAPORU_BANGUMI_BASLIK_DILI_TUM_YUZEYLER_2026-10-22.md`.
