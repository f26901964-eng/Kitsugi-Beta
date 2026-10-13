# PLAN TASK — Karakter / Ekip / Seslendiren Wiki: Ölü "Tekrar Dene" ve Bayat "Bulunamadı" Fix'i (2026-10-13)

Sürüm: **v2.4.282** · Dal: `arena/1788057b-kitsugi-beta` · Commit: `d765c5f`
Kök neden raporu: `KOK_NEDEN_RAPORU_KARAKTER_WIKI_BULUNAMADI_VE_OLU_TEKRAR_DENE_2026-10-13.md`

## 1. Görev tanımı (kullanıcı bildirimi)

1. Fandom'da sayfası olan karakterlerde (ör. **Itsuka Kotori / Date A Live** →
   `date-a-live.fandom.com/wiki/Kotori_Itsuka`) karakter detayındaki Wiki sekmesi
   "Wiki bulunamadı" diyor.
2. Karakter, ekip ve seslendiren detaylarındaki **"Tekrar dene"** düğmesine basınca hiçbir
   şey olmuyor (ağ isteği bile gitmiyor hissi).

## 2. Tespit edilen kök nedenler

| # | Kök neden | Etki |
|---|---|---|
| 1 | `FandomCharacterWikiViewModel.retry()` yalnızca `rebind()` yapıyordu; karakter (`char:v3`, 3 gün) + yapım wiki (`wiki:v2`, 3 gün) + süreç içi `missingWikis` (30 dk) önbellekleri aynı sonucu ağa çıkmadan geri veriyordu. | Düğme ölü. |
| 2 | Geçici kesintiler (HTTP 403/408, kimlik köprüsü istisnaları, tek Wikidata sorgusu hatası) `failureCount`'i arttırmadığı için "bulunamadı" **3 gün kalıcı** yazılıyordu. | Wiki varken günlerce "bulunamadı". |
| 3 | `WikidataClient.characterArticles()` Action API yedeğinde ilk null'da `return emptyList()` ile zinciri kırıyordu. | Karakter kimlik yolu sessizce ölüyordu. |

## 3. Yapılan değişiklikler

| Dosya | Değişiklik |
|---|---|
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomCharacterWikiViewModel.kt` | `retry()` = `FandomWikiResolver.forgetMissingWikis()` + `rebind(forceRefresh = true)`; `bindQuery(..., forceRefresh)` parametresi. |
| `app/src/main/java/com/kitsugi/animelist/data/fandom/FandomRepository.kt` | `resolveCharacterPage` / `resolveMediaWiki` + `forceRefresh` (olumsuz kayıtlar dahil önbellek okunmaz); `NEGATIVE_TTL_MS` 3 gün → 1 gün; sınıf dokümanı. |
| `app/src/main/java/com/kitsugi/animelist/data/fandom/FandomCharacterResolver.kt` | `resolve(query, forceRefresh)`; bayrak yapım wiki çözümlemesine taşınır. |
| `app/src/main/java/com/kitsugi/animelist/data/fandom/FandomWikiResolver.kt` | `safely {}` istisnada `FandomHttp.markFailure()`; companion'a `forgetMissingWikis()`. |
| `app/src/main/java/com/kitsugi/animelist/data/fandom/FandomHttp.kt` | 403/408 geçici hata sayılır (429/5xx zaten); 404 bilinçli olarak sayılmaz. |
| `app/src/main/java/com/kitsugi/animelist/data/fandom/WikidataClient.kt` | `characterArticles` yedeğinde `return emptyList()` → `continue`. |
| `app/build.gradle.kts` | `appVersionName = "2.4.282"`. |
| `RELEASE_NOTES.md`, `release_notes_v2.4.282.md` | Sürüm notları. |

## 4. Kabul kriterleri (cihaz testi)

- [ ] Wiki sekmesi "bulunamadı" durumundayken "Tekrar dene" → iskelet görünür, gerçek istekler
      gider (logcat'te `FandomHttp` satırları), sonuç tazelenir.
- [ ] Kotori örneği: `date-a-live` wiki + `Kotori Itsuka` sayfası sekmede dolu gelir
      (bilgi kutusu + bölümler).
- [ ] Ekip ve seslendiren detaylarındaki Wiki sekmesi retry'si de aynı davranışı gösterir
      (aynı ViewModel).
- [ ] Uçak modunda retry → hata yine "bulunamadı" kartına düşer ama kalıcı yazılmaz
      (10 dk geçici); bağlantı gelince retry kurtarır.
- [ ] Ayarlar → Fandom Wiki → "önbelleği temizle" akışı eskisi gibi çalışır.

## 5. Riskler ve geri alma

- `forceRefresh` yalnızca kullanıcı başlatmalı retry yolunda `true`; normal bağlama ve tercih
  değişimi önbellekle çalışmaya devam eder (API yükü artmaz).
- Olumsuz TTL kısalması en kötü ihtimalle günde birkaç ek istek üretir (sekme yalnızca sayfa
  açılışında çözümlenir).
- Geri alma: tek commit (`d765c5f`) revert edilebilir.
