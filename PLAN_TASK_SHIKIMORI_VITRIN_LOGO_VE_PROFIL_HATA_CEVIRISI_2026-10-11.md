# Plan / Task — Shikimori Vitrin Logoları ve Profil Hata Açıklamaları

**Tarih:** 2026-10-11
**Kapsam:** Keşfet vitrini ve harici platform profil hata ekranları

## Sorunlar

1. Shikimori içerikleri vitrinde başlık logosu aramıyordu; `showAnimeLogos` kapalıyken logo politikası Shikimori'yi kapsamıyordu. Keşfet sonuçlarında Shikimori'nin kendi ID'si taşınırken, varsa MAL eşleme kimliği (`myanimelist_id`) `realMalId` alanına aktarılmıyordu.
2. Profil ekranındaki AniList 429 örneğinde sunucunun ham hata metni/JSON'u kullanıcıya doğrudan gösteriliyordu. Diğer profil kaynaklarının hata durumları da aynı ortak bileşenden geçiyordu.

## Yapılanlar

- `HeroLogoPolicy.kt`: Shikimori ve `shiki` takma adı için anime vitrin logosu aramasını, global logo ayarı kapalı olsa da etkinleştir.
- `KitsugiShikimoriClient.kt`: anime/manga arama ve keşfet sonuçlarına geçerli `myanimelist_id` değerini `realMalId` olarak ekle; logo eşlemesi gereksiz Shikimori-ID → MAL-ID sorgularına daha az bağımlı olsun.
- `ProfileErrorFormatter.kt`: HTTP kodu ve hata imzalarından 429, oturum, izin, 404, zaman aşımı, sunucu, ağ, istek ve genel hata sınıflarını belirle. Ham sunucu cevabı UI modeline taşınmaz.
- `ProfileSharedComponents.kt`: Profil hata ekranında ham metin yerine platform adı, kısa anlaşılır başlık, varsa HTTP kodu ve Türkçe/İngilizce mini açıklamaları göster.
- `values/strings.xml` ve `values-en/strings.xml`: bütün hata sınıfları için iki dilde açıklama metinleri.
- Testler: `HeroSelectionTest.kt` Shikimori logo politikasını kapsayacak şekilde genişletildi; `ProfileErrorFormatterTest.kt` API hata sınıflandırması için eklendi.

## Kabul Kriterleri

- Shikimori anime vitrini logo araması başlatır; gerçek görsel bulunmazsa var olan başlık metni yedeği korunur.
- Profil hatalarında ham JSON veya sağlayıcı hata gövdesi gösterilmez.
- 429 durumunda HTTP 429 rozeti ve kısa Türkçe + İngilizce hız sınırı açıklaması görünür.
- Herhangi bir tanınmayan hata güvenli, genel açıklamaya düşer.
- `values` ve `values-en` string kaynakları anahtar/placeholder bakımından eşleşir.

## Doğrulama

- `python3 scripts/check_localization_resource_parity.py` — başarılı (5.972 string, 4 array).
- `git diff --check` — başarılı.
- `bash ./gradlew :app:testDebugUnitTest` — çalıştırılamadı: ortamda Java/JDK yok (`JAVA_HOME` ayarlı değil ve `java` bulunamadı).
