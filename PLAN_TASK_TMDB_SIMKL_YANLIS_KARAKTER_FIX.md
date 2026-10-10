# PLAN / TASK: TMDB & Simkl Yanlış Karakter Detay Sayfası Fix

## Sorun
- Simkl ve TMDB kaynaklı yapımlarda (ör. Spider-Man: Brand New Day) karakter kartına tıklanınca yanlış karakterin detay sayfası açılıyor.
- Örnek: "Peter Parker / Spider-Man" kartı -> "Makishima Yuusuke" (Yowamushi Pedal, "Peak Spider" lakaplı) detay sayfası.

## Kök Neden (kod incelemesi)
- Simkl ve TMDB kadrosu TMDB credits'ten geliyor (`TmdbCreditsClient`).
- Kurgusal karakter detayı (`KitsugiCharacterClient.fetchCharacterDetail`, `tmdb` dalı, `isRealMediaRole == false`) karakter adıyla yedek arama yapıyor.
- AniList yedeği zaten `isVerifiedAniListCharacterNode` ile doğrulanıyordu.
- Jikan yedeği ise `searchMalCharacters(...).firstOrNull()` sonucunu HİÇ doğrulamadan kullanıyordu. Jikan bulanık arama yaptığı için "Spider" -> "Peak Spider" eşleşmesi yanlış karakteri açtı.
- Kart adı "Gerçek Ad / Kurgusal Ad" biçimindeyken aramada "/ Spider-Man" kısmı kullanılıyordu.

## Yapılan Değişiklikler
1. Jikan yedek sonucu yalnızca `KitsugiPersonImageAggregator.nameMatches` ile karakter adı eşleşirse kabul ediliyor.
2. Jikan aramasında `isUnambiguousName` koruması eklendi (tek kelimelik belirsiz adlar aranmıyor).
3. `primaryCharacterName()` yardımcı fonksiyonu eklendi: "Gerçek Ad / Kurgusal Ad" -> "Gerçek Ad". Hem AniList hem Jikan yedeğinde kullanılıyor.

## Değişen Dosyalar
- `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiCharacterClient.kt`
- `PLAN_TASK_TMDB_SIMKL_YANLIS_KARAKTER_FIX.md` (bu dosya)

## Doğrulama (Yapılacak)
- [ ] Uygulamayı derle (Android SDK ortamında; sandbox'ta derleme yapılamadı).
- [ ] Spider-Man: Brand New Day > Karakterler > Peter Parker kartına tıkla: Peter Parker / Spider-Man detayı açılmalı.
- [ ] TMDB'de anime dışı kurgusal karakterlerden birkaçını (ör. Bruce Banner / Hulk) kontrol et.
- [ ] Simkl üzerinden açılan bir yapımda aynı kontrolü yap.
- [ ] Gerçek kişi (live-action oyuncu) kartlarının etkilenmediğini doğrula (`isRealMediaRole` yolu değişmedi).

## Bilinen Kalan Riskler (ayrı iş olarak değerlendirilecek)
- `CharacterDetailPage` ViewModel anahtarı ve `DetailCache` `source + characterId` ile çalışıyor. TMDB karakter ID'si oyuncunun TMDB person ID'si olduğu için farklı kaynaklar arasında ID çakışması teorik olarak mümkün. Bu, bu fix'in kapsamı dışında.

## Commit
- `f3fbd72` fix(karakter): TMDB/Simkl kurgusal karakter detayında yanlış karakter açılması
