# Plan ve Görev: Vitrin Logoları ve Çok Kaynaklı Galeriler

**Tarih:** 9 Ekim 2026  
**Depo:** `f26901964-eng/Kitsugi-Beta`  
**Dal:** `arena/a4cc6b62-kitsugi-beta`

## Amaç

Bangumi ve Simkl keşfet/vitrin alanlarında metin başlığı yerine kaynak logolarını göstermek; karakter, ekip/personel ve seslendirmen galerilerindeki eksik MAL bağlantılı görselleri gidermek; anime, film ve TV medya galerileriyle kişi galerilerinin farklı kaynaklarda doğru kimlikle çalışmasını sağlamak.

## Kabul ölçütleri

- [x] Bangumi ve Simkl vitrinleri uygun eser logosunu bulup gösterir; logo bulunamazsa başlık metni yedek olarak kalır.
- [x] Kaynak kimlikleri birbirine karıştırılmaz; Bangumi ve Simkl kimlikleri MAL kimliği gibi kullanılmaz, gereken çapraz kimlikler doğru kaynaktan çözülür.
- [x] Kişi galerileri için MAL/Jikan görselleri geçerli MAL ID'siyle ya da tekil ve sıkı isim eşleşmesiyle alınır; Unicode/CJK adları desteklenir.
- [x] Karakter, ekip/personel ve seslendirmen görselleri mevcut sağlayıcı sonuçlarıyla birleştirilir; Jikan `/pictures` isteği yalnızca MAL kimliği mevcut olduğunda yapılır.
- [x] Anime, film ve TV galerilerinde kaynak türüne uygun MAL, TMDB, AniList, Kitsu ve diğer sağlayıcı kimlikleri çözülür; kaynaklar arası görseller eklenebilir.
- [x] Kimlik çözümleme ve logo politikaları için birim testleri eklenir/güncellenir.

## Uygulama özeti

| Dosya | Değişiklik |
|---|---|
| `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiHeroSection.kt` | Kaynak farkındalıklı logo bulma; Bangumi için çapraz ID çözümü ve sınırlı eşzamanlı istekler. |
| `app/src/main/java/com/kitsugi/animelist/ui/components/HeroLogoPolicy.kt` | Bangumi/Simkl vitrinlerinde logo deneme ve metin yedeği politikasını ayırır. |
| `app/src/main/java/com/kitsugi/animelist/data/remote/SimklApiClient.kt` | Simkl keşfet sonuçlarında TMDB kimliğinin korunmasını sağlar. |
| `app/src/main/java/com/kitsugi/animelist/data/remote/MediaGalleryIdentity.kt` | Kaynak ve medya türüne göre galeri kimliklerini güvenli biçimde eşler. |
| `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiMalDetailClient.kt` | Galeri için Jikan `/pictures` görsellerini getiren işlevi açığa çıkarır. |
| `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiPersonImageAggregator.kt` | Geçerli MAL ID'si veya tekil/sıkı Jikan isim eşleşmesiyle kişiyi çözer; Unicode adları ve sağlayıcı görsellerini destekler. |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailViewModel.kt` | Medya galerilerinde doğru kaynak kimliğini çözer ve uygun MAL/Shikimori görsellerini birleştirir. |
| `app/src/test/java/com/kitsugi/animelist/ui/screens/explore/HeroSelectionTest.kt` | Vitrin logo politikasına ilişkin testleri günceller. |
| `app/src/test/java/com/kitsugi/animelist/data/remote/MediaGalleryIdentityTest.kt` | Kaynak/medya kimliği eşlemelerini sınar. |
| `app/src/test/java/com/kitsugi/animelist/data/remote/KitsugiPersonImageAggregatorTest.kt` | Kişi eşleştirme ve Unicode ad davranışını sınar. |

## Doğrulama ve açık işler

- `git diff --check`: başarılı.
- Android birim testleri ve derleme: **çalıştırılamadı**. Ortamda Java/JDK bulunmuyor (`JAVA_HOME` tanımsız, `java` komutu yok); bu nedenle testlerin geçtiği iddia edilmez.
- JDK bulunan Android geliştirme ortamında ilgili birim testlerini ve uygulama derlemesini çalıştırıp doğrulayın.

## Teslim paketi

ZIP; çalışma ağacındaki değiştirilmiş ve yeni dosyaların tam içeriklerini, bu plan/görev belgesini ve SHA-256 değerleriyle dosya listesini içeren `MANIFEST.json` dosyasını içerir. Dosyalar depo köküne göre aynı göreli yollarla saklanır. ZIP tam proje yedeği değildir; yalnızca bu görev kapsamındaki değişiklik dosyalarını içerir.
