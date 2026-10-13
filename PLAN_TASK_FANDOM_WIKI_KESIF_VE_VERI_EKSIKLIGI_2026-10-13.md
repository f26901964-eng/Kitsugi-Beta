# PLAN/TASK — Fandom wiki: bulunamayan wikiler ve eksik veri (canlı denetimle)

Tarih: 2026-10-13
Durum: **Kodlandı.** Kotlin derlemesi + `data.fandom.*` birim testleri CI'da yeşil (Android Derleme Doğrulama).
Cihaz testi kullanıcıda. Dal: `arena/7ccfeaca-kitsugi-beta`.

## Sorun
Yedi kaynaktaki (AniList, MAL, Kitsu, Shikimori, Bangumi, TMDB, Simkl) birçok yapım için Fandom wikisi var ama
uygulama bulamıyor; bulunca da veri eksik geliyor.

## Yöntem
`tools/wiki_audit/audit.py` (+ `.github/workflows/wiki-audit.yml`): keşif mantığının Python karşılığını canlı Fandom,
Wikidata ve AniList üzerinde 138 vakayla çalıştırır (sandbox'ta ağ yok, Actions'ta koşar; sonuç `tools/wiki_audit/results/`).
Kotlin'in birebir kopyası değildir; her politika değişikliği Kotlin birim testiyle de kilitlendi.

## Kanıtlanan nedenler ve çözümler — KEŞİF
| # | Neden (canlı kanıt) | Çözüm |
|---|---|---|
| K1 | Eklenti manga/manhwa ekranları yalnızca başlık gönderir; isim yolu karakter kanıtı olmadan reddeder (ör. Naruto, Noblesse). | `FandomAniListSource.findByTitle` + `FandomAniListParser.parseTitleSearch/pickTitleMatch`: başlık AniList'te **birebir** eşleşirse kimlik + başlangıç yılı tamamlanır (`FandomWikiResolver.bridgeQueryByTitle`). Bulanık eşleşme yok. |
| K2 | Topluluk aramasının `pageCount` alanı kaba kova (85 makaleli wiki → 10). Eşik 20 gerçek wikileri eliyordu (Barakamon, Texhnolyze, Uchuu Kyoudai). | `FandomNameMatcher.MIN_PAGE_COUNT` 20→10. Gerçek boyut `siteinfo` ile kimlik kanıtında doğrulanır. |
| K3 | Dil öneki olmayan wiki "İngilizce" sanılıyordu (`wiedzmin` Lehçe, `ru.domb` Rusça). | `pickAlive` artık `siteinfo.lang`'a göre en > tr > diğer; kök aday sınırı 4→8; yabancı dilli Wikidata sonucu son çare olarak ertelenir. |
| K4 | Ezici karakter örtüşmesi olduğu halde puan 0.53–0.59'da kalıp reddedildi (Space Brothers, kiss×sis, Noblesse, Wind Breaker). | `FandomNameMatcher.score`: 3 yeni PROBABLE kuralı (ana karakterlerin tamamı + ≥4 karakter ≥%60; birebir wiki adı + ≥%50; ≥3 yerel ad doğrulaması). Oyun hub'ı hariç. |
| K5 | Eski "bulunamadı" / yanlış seçimler önbellekte kalıyordu. | `wiki:v2→v3`. |

## Kanıtlanan nedenler ve çözümler — EKSİK VERİ
| # | Neden | Çözüm |
|---|---|---|
| V1 | `prop=extracts` Fandom'da desteklenmiyor; açıklama yalnızca `fandomdescription` (≈500 karakterde "..." ile kesik). | `FandomLeadParser.mergeDescription`: tam giriş metni. |
| V2 | Bölüm 0 (giriş + infobox) hiç yüklenmiyordu (`sections` 0'ı listelemez). | `FandomRepository.applyLead/loadLead` (`lead:v1`), `FandomApi.sectionHtml(…, 0)`. |
| V3 | `pageprops.infoboxes` JSON'u bağlantılı metni (ör. seslendirmenler) ve liste ayırıcılarını düşürür. | `FandomLeadParser.parseInfobox` (işlenmiş `aside.portable-infobox`), `mergeInfobox`; `FandomInfoboxParser.buildField`; görsel sınırı 8→24, değer sınırı 600→2000. |
| V4 | Uzun paragraflar 1600 karakterde, bölümler 120 blokta, tablolar 32 satırda kesiliyordu. | `FandomHtmlText`: 8000 karakter / 400 blok / 150 satır; bölüm görseli 12→24. |
| — | Önbellek anahtarları | `media-page v3→v4`, `char v3→v4`, `sec v3→v4`. |

## Canlı sonuç (son denetim, `results/report.md`)
- Anime/manga vakalarında "wiki var ama bulunamadı": **29 → 0**; doğrulanmış vakalarda **yanlış wiki yok**.
- Denetimdeki "My Hero Academia yanlış" sahte alarmdı: `bokunoheroacademia` → `myheroacademia`'ya yönleniyor (aynı wiki).
- Fandom'da gerçekten wikisi olmayanlar: Bible Black, Redo of Healer, Ping Pong the Animation (canlı aramada sonuç yok).
- Çözülemeyen: Overflow, Bastard, Parasite (film).

## Sınırlar
- 138 vakalık örneklem; Kitsu/Bangumi/Shikimori/Simkl yolları ayrıca canlı ölçülmedi.
- Dizi/film: gerçek uygulamada TMDB kimliği var; karakter kanıtı olmayan tek kelimelik başlıklar yıla bağlı.
- Lead/infobox ayrıştırıcısı fixture HTML ile test edildi, cihazda görsel doğrulama yok.

## Değişen dosyalar
Kod: `data/fandom/` → `FandomAniListParser`, `FandomAniListSource`, `FandomHtmlText`, `FandomInfoboxParser`,
`FandomLeadParser` (yeni), `FandomNameMatcher`, `FandomRepository`, `FandomWikiResolver`.
Test: `FandomAniListParserTest`, `FandomNameMatcherTest`, `FandomLeadParserTest` (yeni).
Araç: `tools/wiki_audit/*`, `.github/workflows/wiki-audit.yml`.

## Cihaz test rehberi
1. Eklenti kaynağından Naruto / One Piece / Berserk (manga) aç → Wiki sekmesi gelmeli.
2. Barakamon, Texhnolyze, Space Brothers → küçük wiki görünmeli.
3. The Witcher / Lord of the Rings → Lehçe/Rusça değil, İngilizce wiki seçilmeli.
4. Gintama / Noblesse karakter sayfası → giriş metni kesik olmamalı, infobox'ta seslendirmen bağlantıları görünmeli.
5. Eski önbellek: anahtarlar yükseltildiği için veri temizlemeden yeniden çözülmeli.
