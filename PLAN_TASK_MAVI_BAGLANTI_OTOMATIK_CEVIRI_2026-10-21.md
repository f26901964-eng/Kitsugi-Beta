# PLAN & TASK — Wiki Mavi (Bağlantı) Metinlerinin Otomatik Çevirisi

**Tarih:** 2026-10-21
**Sürüm:** v2.4.318
**Dal:** `arena/dc163303-kitsugi-beta`
**Commit:** `feat(wiki): mavi baglanti metinlerini yuvali ceviri ile cevir (v2.4.318)`
**Durum:** ✅ Kod tamam · ⏳ Cihazda doğrulama bekliyor

---

## 1. Hedef

Wiki açılır sayfalarında otomatik çeviri açıkken **mavi (bağlantı) metinler de çevrilsin**, ama:

| # | Kural | Neden |
|---|-------|-------|
| K1 | Etiket **tam olarak eski konumunda** kalsın | Cümle dizilimi bozulmasın, kelimeler yapışmasın |
| K2 | Etiket **tıklanabilir** kalsın | Mavi metin gezinme işlevini kaybetmesin |
| K3 | Gidilecek **URL arkada aynı** kalsın | Yanlış sayfaya düşülmesin |
| K4 | Uygulama **kasmadan/donmadan** çalışsın, çeviri **yavaşlamasın** | İstek sayısı artmasın (azalsın) |
| K5 | Sağlayıcı jetonu bozarsa **kırık mavi metin gösterilmesin** | Güvenli yedek zorunlu |

---

## 2. Kök neden (özet)

* Eski tasarım etiketleri çevirmiyordu → cümle yarı İngilizce kalıyordu.
* Metin bağlantı aralarına bölünüp **her parça ayrı istekte** çevriliyordu → boşluk kaybı ("678 articles**ve**") ve istek patlaması (429/gecikme).
* Çizici tıklanabilir alanı `wikiLinkRanges(text, links)` ile **metin içinde etiket arayarak** bulduğu için metin çevrilip etiket çevrilmeyince eşleşme kopuyordu.

Detaylı analiz: `MAVI_BAGLANTI_OTOMATIK_CEVIRI_RAPORU_2026-10-21.md`

---

## 3. Çözüm tasarımı — "yuvalı çeviri"

```
Kaynak : Read the Characters and Terminology pages.
Yuva   : Read the ⟦L0⟧Characters⟦/0⟧ and ⟦L1⟧Terminology⟦/1⟧ pages.
Yanıt  : ⟦L0⟧Karakterler⟦/0⟧ ve ⟦L1⟧Terminoloji⟦/1⟧ sayfalarını okuyun.
Ekran  : [Karakterler] ve [Terminoloji] sayfalarını okuyun.
```

**Neden `⟦…⟧`, neden `⟪…⟫` değil?**
`TranslationManager.maskMarkdown` çeviri sonundaki tanınmayan `⟪…⟫` jetonlarını temizliyor.
`⟦…⟧` biçimi bu temizlikten etkilenmiyor (dosyada aynı amaçla kullanılan eski kalıntı temizliği de bunu doğruluyor).

**Bütünlük denetimi:** her yuva tam 1 kez açılıp kapanmalı, yuvalar üst üste binmemeli.
Aksi hâlde `parseLinkSpans` → `null` → **güvenli yedek** (eski parça-parça yol, etiketler orijinal).

**Kalıcılık:** çevrilmiş etiketler `…:l<indeks>` çocuk anahtarı olarak metinle **aynı** çeviri
haritasında saklanır; `withTranslatedBlocks` bunları `FandomBlockLink.label`'a yazar, `url`'ye
dokunmaz. Etiket anahtarları metinden **önce** akıtılır (`onBlock`).

---

## 4. Görev listesi

- [x] **T1** `WikiStructuredTranslationKeys.link(baseKey, index, linkIndex)` anahtarı (`:l<idx>`)
- [x] **T2** `WikiTranslatedBlockText` veri sınıfı (metin + etiket haritası)
- [x] **T3** `markLinkSpans` — etiketleri `⟦L<n>⟧ … ⟦/<n>⟧` yuvalarına sar
- [x] **T4** `parseLinkSpans` — yuvaları doğrula/çöz, bozuksa `null`
- [x] **T5** `translateBlockText` — tek istek: cümle + etiketler; `preferredLabels` desteği
- [x] **T6** `translateLinkedSegments` — güvenli yedek yol (etiketler orijinal)
- [x] **T7** `translateSectionBlocks` — blok başına tek etiket haritası + `publishLabels` akışı
- [x] **T8** Görsel altı yazısını (figcaption) yuvalı akışa taşı
- [x] **T9** `withTranslatedBlocks` — etiketleri `links`'e geri yaz (`url` sabit)
- [x] **T10** `keepsLinks` doğrulaması — bağlantı çözülemezse o yüzeyin çevirisini reddet
- [x] **T11** `shouldTranslateBlockText` + `looksLikeSentence` — cümle görünümlü kart başlığı çevrilir, `href` korunur
- [x] **T12** `WikiLinkRange.linkIndex` ekle (anahtar yazımı için sıra bilgisi)
- [x] **T13** Testler: 3 test güncellendi, 6 yeni test eklendi
- [x] **T14** `appVersionName = "2.4.318"` + `release_notes_v2.4.318.md` + `RELEASE_NOTES.md`
- [x] **T15** Rapor dosyası: `MAVI_BAGLANTI_OTOMATIK_CEVIRI_RAPORU_2026-10-21.md`
- [ ] **T16** Cihazda manuel doğrulama (aşağıdaki kontrol listesi)
- [ ] **T17** (opsiyonel) `./gradlew :app:testDebugUnitTest --tests "*WikiTranslationProtectionTest*"`

---

## 5. Değişen / eklenen dosyalar

| Dosya | Tür | Ne yapıldı |
|-------|-----|-----------|
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/WikiTranslationProtection.kt` | Kod | Yuvalı çeviri motoru, `:l<idx>` anahtarları, güvenli yedek, `keepsLinks`, kart başlığı kuralı |
| `app/src/test/java/com/kitsugi/animelist/ui/screens/detail/wiki/WikiTranslationProtectionTest.kt` | Test | 3 test güncellendi, 6 yeni sözleşme testi |
| `app/build.gradle.kts` | Yapı | `appVersionName` 2.4.317 → 2.4.318 |
| `release_notes_v2.4.318.md` | Doküman | TR + EN sürüm notu |
| `RELEASE_NOTES.md` | Doküman | Yeni sürüm en üste eklendi |
| `MAVI_BAGLANTI_OTOMATIK_CEVIRI_RAPORU_2026-10-21.md` | Doküman | Kök neden + tasarım + doğrulama |
| `PLAN_TASK_MAVI_BAGLANTI_OTOMATIK_CEVIRI_2026-10-21.md` | Doküman | Bu dosya |

> Çizim tarafında (`FandomWikiReader.kt`, `MediaWikiTabContent.kt`, `CharacterWikiTabContent.kt`)
> **değişiklik gerekmedi**: hepsi zaten `withTranslatedBlocks` + `wikiLinkRanges` üzerinden çalışıyor.

---

## 6. Davranış matrisi

| Senaryo | Beklenen | Test |
|---------|----------|------|
| Bağlantılı paragraf | Tek istek, etiketler yerinde, URL aynı | `blockWithLinksIsTranslatedAsOneSentenceAndLabelsKeepTheirSlot`, `inlineLinkLabelsAreTranslatedInPlaceAndUrlsNeverChange` |
| Jeton bozuk | Parça-parça çeviri, etiket orijinal, bağlantı çalışır | `brokenSlotsFallBackToUntranslatedLabelsWithoutLosingTargets` |
| Eski önbellek (etiket anahtarı yok) | Çeviri reddedilir, orijinal çizilir | `translatedLabelsArePersistedAsChildKeysSoTheCacheRoundTripKeepsLinks` |
| Akış sırası | Etiket anahtarı metinden önce | `translatedLabelKeysAreStreamedBeforeTheTranslatedText` |
| 40 tekrarlı bağlantı | Tek istek, 40 tıklanabilir alan | `manyRepeatedLinksRemainProtectedWithoutTokenLimits` |
| Kart başlığı = cümle | Çevrilir, `href` korunur | `linkedCardTitleThatIsARealSentenceIsTranslatedButKeepsItsHref` |
| Kart başlığı = sayfa adı | Çevrilmez, istek gitmez | `shortLinkedCardTitleStaysAsAPageName` |
| Yalnızca bağlantıdan oluşan satır | Değişmez, en çok 1 istek | `linkOnlyParagraphCostsAtMostOneRequestAndKeepsEverything` |
| "Orijinal" düğmesi | Metin + etiket birlikte geri alınır | `withoutTranslationDropsBaseKeyAndAllBlockChildren` |

---

## 7. Manuel doğrulama (T16)

Date A Live Wiki → ana sayfa · `Ayarlar → Çeviri → Otomatik çeviri` **açık**:

- [ ] "This wiki is dedicated to …" cümlesi Türkçe; karta dokununca **aynı** sayfa açılıyor.
- [ ] "Characters — Terminology — Media …" satırının **tamamı** Türkçe ve her parça tıklanabilir.
- [ ] "… 678 madde / 10.857 görsel …" cümlesinde **kelimeler yapışmıyor**.
- [ ] "Orijinal"e bas → metin **ve** mavi etiketler birlikte İngilizceye dönüyor.
- [ ] Sayfa açılışında donma yok; Logcat `TranslationManager` satırlarında istek sayısı eskisinden **az**.
- [ ] Uçak modunda (ağ yok) sayfa açılıyor, mavi alanlar yine tıklanabilir (yedek yol).

---

## 8. Riskler ve geri alma

| Risk | Etki | Önlem |
|------|------|-------|
| Sağlayıcı `⟦…⟧` jetonlarını düzenli bozuyor | Etiket çevrilmez (eski davranış) | Güvenli yedek; kırık metin yok. Logcat'te `parseLinkSpans` sonrası fallback sayısı izlenebilir |
| Çeviri haritası yarım yazılır | O blok orijinal çizilir | `keepsLinks` reddi + etiketlerin metinden önce akıtılması |
| Marka adları çevrilir ("Steam" → "Buhar") | Kozmetik | İleride marka sözlüğü (rapor §7) |

**Geri alma:** tek dosya geri alınır → `git revert <commit>`; anahtar şeması geriye dönük uyumlu
(eski önbelleklerde `:l*` anahtarı yoksa `withTranslatedBlocks` orijinal etiketlerle çalışır).

---

## 9. Sonraki adımlar (kapsam dışı)

1. **"Hikaye" bölümünün iki kez çizilmesi** — `ReaderLeadCard` (bölüm 0) ile İçindekiler'den açılan
   bölüm aynı blokları taşıyor. Öneri: blok imzası ile tekilleştirme.
2. **Marka/özel ad sözlüğü** — etiket çevirisinden önce korunacak kısa liste.
3. **LINK_GROUP çipleri** — istenirse yuvalı çeviri tek satırla açılabilir.
