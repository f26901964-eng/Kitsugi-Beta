# PLAN / TASK: TMDB/Simkl kadro adlarında yanlış alfabe + hayali karakter görsel zinciri

**Tarih:** 2026-10-10 · **Dal:** `arena/cdf48ba7-kitsugi-beta`

## 1. Kullanıcı Raporu

Ekran görüntüleri: TMDB kaynaklı *Puella Magi Madoka Magica: Walpurgisnacht Rising* (2026)
detay sayfası → *Karakterler* sekmesi.

1. Karakterlerin kendi görseli (Madoka, Homura, Sayaka, Kyoko, Mami, Nagisa, Kyubey,
   Shichōka) boş; yalnızca baş harfler görünüyor. Yalnızca oyuncu/seiyuu fotoğrafları dolu.
   Kullanıcı isteği: TMDB hayali karakter görseli sunamıyorsa AniList/MAL/Kitsu/Shikimori/
   Bangumi'den (uygun olan) görsel getirilsin — hem TMDB hem Simkl kaynağı için.
2. Bazı oyuncu/seslendirmen adları İngilizce/romaji yerine Arapça ("اری کیتامورا") veya
   Tayca ("ไอ โนะนะกะ") gösteriliyor; kişinin "Diğer İsimler" listesinde doğru Latin adı
   ("Eri Okamura") zaten mevcut.

## 2. Kök Nedenler

| # | Kök neden | Dosya |
|---|---|---|
| A | `pickLatinAlias()` yalnızca `!hasCjkCharacters(alias)` kontrolü yapıyordu. CJK olmayan **her** alfabe (Arapça, Tayca, Kiril, İbranice…) yanlışlıkla "Latin" sayılıyordu. | `TmdbCreditsClient.kt` |
| B | Aynı yanlış desen (`any{isLetter()} && !hasCjkCharacters`) Bangumi ve AniList köprüsü yardımcı fonksiyonlarında da tekrarlanıyordu. | `BangumiLocalizedName.kt`, `BangumiTitleCache.kt`, `KitsugiAniListPersonBridge.kt`, `KitsugiBangumiDetailClient.kt`, `KitsugiEpisodeRatingsRepository.kt` |
| C | TMDB/Simkl kaynaklı anime karakterleri için görsel zinciri yalnızca AniList'e bakıyordu (`enrichCharactersWithAnimeImages`); AniList'te eşleşme/yapım bulunamazsa (çok yeni yapım, Türkçeleştirilmiş başlık vb.) karakter görseli boş kalıyordu. | `KitsugiCharacterClient.kt` |

## 3. Yapılan Değişiklikler

### 3.1 Yanlış alfabe düzeltmesi
- `PreferenceHelpers.isLatinText(text)` eklendi: metnin TÜM harfleri gerçekten Latin
  alfabesinde mi (Temel Latin + Latin-1 Supplement + Latin Extended A/B/Additional/C,
  Türkçe İ/ı/ğ/ş/ü/ö/ç dahil) kontrol eder.
- `TmdbCreditsClient.pickLatinAlias`, `BangumiNameLocalizer.isLatinDisplayName`,
  `BangumiTitleCache.usableLatin`, `KitsugiAniListPersonBridge.isLatinName`,
  `KitsugiBangumiDetailClient.usableLatinName` + bölüm adı Latin kontrolü,
  `KitsugiEpisodeRatingsRepository` İngilizce bölüm adı kontrolü artık `isLatinText`
  kullanıyor.

### 3.2 Karakter görseli çapraz kaynak zinciri
- `KitsugiCharacterClient.fetchJikanCharacterList(malId, mediaType)`: mevcut inline MAL/Jikan
  karakter ayrıştırma kodu yeniden kullanılabilir fonksiyona çıkarıldı (davranış değişmedi).
- `enrichFromCrossSourceCharacterLists(characters, mediaType, realMalId)`: AniList denemesi
  sonrası görseli boş kalan karakterler için sırasıyla **MAL/Jikan → Shikimori → Kitsu**
  listelerine bakar (her biri 5 sn bütçeyle); yalnızca doğrulanmış `realMalId` varken çalışır.
- `mergeImagesFromReferenceCharacters(characters, reference)`: ad eşleştirmesiyle (birebir →
  token seti → altküme/jaccard ≥ 0.5) yalnızca görseli boş karakterlere görsel atar; eşleşme
  yoksa karakter değişmeden kalır.
- `"tmdb"` ve `"simkl"` karakter zincirlerinde `enrichCharactersWithAnimeImages` çağrısının
  hemen ardından `enrichFromCrossSourceCharacterLists` çağrılır.

## 4. Kabul Kriterleri
- [ ] TMDB/Simkl kaynaklı bir anime açıldığında, kişi `also_known_as` listesinde Arapça/
      Tayca/Kiril vb. bir çeviri varsa ad olarak ASLA seçilmez; Latin ad yoksa özgün
      (CJK) ad korunur.
- [ ] AniList'te eşleşmeyen/yeni bir TMDB veya Simkl anime kaydında, MAL ID'si çözülebiliyorsa
      hayali karakter görselleri MAL/Jikan, Shikimori veya Kitsu'dan dolar.
- [ ] Yanlış yapımın/karakterin görseli asla atanmaz (ad eşleşmesi + realMalId doğrulaması
      zorunlu).
- [ ] Derleme ve cihaz testi bu ortamda yapılamadı (sandbox'ta JDK/Gradle yok); Android
      Studio'da derleme + manuel doğrulama gerekli.

## 5. Bilinen Sınırlamalar
- Bangumi doğrudan görsel zincirine eklenmedi (MAL→Bangumi ID eşlemesi için ek altyapı
  gerekir); MAL/Jikan, Shikimori, Kitsu zaten kullanıcının istediği kaynakların çoğunu
  kapsıyor. İleride ihtiyaç olursa aynı `mergeImagesFromReferenceCharacters` deseniyle
  eklenebilir.
