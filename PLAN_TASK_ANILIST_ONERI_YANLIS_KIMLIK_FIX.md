# PLAN / TASK: AniList öneri yanlış kimlik düzeltmesi

## Sorun
Detay sayfasında (Rent-a-Girlfriend) "Öneriler" sekmesinde alakasız bir yapım
(STEEL BALL RUN JoJo's Bizarre Adventure) listeleniyordu.

## Kök neden
- Manuel eklenen AniList kayıtlarında `malId = null` kalıyor (MyListScreen.kt).
- `MediaEntryDetailViewModel.externalIdOf()` içinde `else -> entry.malId ?: entry.id`
  AniList için de geçerli olduğundan yerel DB satır numarası (`entry.id`) dış kimlik
  gibi kullanılıyordu. Bu numara başka bir yapımın MAL ID'sine denk gelebiliyordu.
- `KitsugiMediaRelationsClient.fetchRecommendations` bu sahte kimlikle AniList/Jikan
  sorgusu yapıyordu (`idMal` olarak).

## Yapılan değişiklikler
1. `MediaEntryDetailViewModel.kt` — `externalIdOf`: anilist için yalnızca gerçek
   `malId` (>0) kullanılır; yoksa 0.
2. `KitsugiMediaRelationsClient.kt` — `fetchRecommendations`: AniList kaydının kimliği
   yoksa başlıkla (`fetchRecommendationsFromAniListBySearch`) aranır; başlık da yoksa
   boş liste döner.

## Bilinen etkiler / riskler
- Manuel AniList kayıtlarında detay ve bazı sekmeler artık kimlik olmadığından boş
  veya hata gösterebilir (yanlış veri yerine). Cihazda doğrulanmalı.
- Kalıcı çözüm: kayda AniList media ID'si kaydetmek (ayrı görev).
- Derleme ve cihaz testi bu ortamda yapılmadı.

## Doğrulama checklist
- [ ] Manuel eklenen AniList kaydında öneri sekmesi alakasız yapım göstermiyor
- [ ] AniList içe aktarılmış (malId dolu) kayıtlar eskisi gibi çalışıyor
- [ ] Kimliksiz AniList kaydında başlık araması sonuç veriyor
- [ ] `./gradlew :app:compileDebugKotlin` başarılı

## Commit
aec5163 — fix(detail): AniList kayıtlarında yerel satır numarasını dış kimlik olarak kullanma
Branch: arena/c45530c4-kitsugi-beta
