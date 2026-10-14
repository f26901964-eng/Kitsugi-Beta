# PLAN & TASK — Vikipedi (Wikipedia) Entegrasyonu Baştan Sona Düzeltme

**Tarih:** 2026-10-14
**Sürüm:** 2.4.289 (önceki 2.4.288)
**Kapsam:** Yapım detay sayfası → Wiki sekmesi → Vikipedi yolu (çözümleme, veri katmanı, ViewModel, UI, metinler)
**İlgili dosyalar:** `KOK_NEDEN_RAPORU_VIKIPEDI_ENTEGRASYONU_2026-10-14.md`, `release_notes_v2.4.289.md`
**Commit'ler:** `80b131d`, `fc3be5c`, `f336a8e` (branch: `arena/ccdaf028-kitsugi-beta`)

---

## 1) Amaç

Kullanıcının 5 ekran görüntüsüyle bildirdiği Vikipedi kaynaklı tutarsızlıkların tamamını kök
nedenden çözmek ve Vikipedi yolunu baştan sona gözden geçirmek:

1. "İlgili wiki sayfaları" şeridinde yapımla ilgisiz maddeler (Tarım, Hukuk, Ahlat, Türkçe…)
2. Vikipedi sekmesinde alakasız sayfa açılması / okuyucunun "Anasayfa" göstermesi
3. "Vikipedi'de aç" düğmesinin madde yerine ana sayfayı açması
4. "Wiki özeti" kartında Orijinal ↔ Çeviri geçişinin içeriği tamamen değiştirmesi

---

## 2) Görev listesi ve durum

| # | Görev | Durum | Dosya(lar) |
| --- | --- | --- | --- |
| T1 | İlgili maddeleri ana sayfa yerine yapımın kendi maddesinden türet | ✅ | `FandomMediaWikiViewModel.loadRelatedPagesForWikipedia` |
| T2 | Ansiklopedi iskelet sayfası filtresi (Anasayfa/Portal/Yardım/Şablon/Dosya/Kategori/Özel/Taslak) | ✅ | `FandomMediaWikiViewModel` (companion sabitleri) |
| T3 | Vikipedi'de ana sayfa tabanlı ilgili sayfa akışını kapat | ✅ | `ensureContentLoaded` |
| T4 | "Vikipedi'de aç" → çözülen maddeyi aç (ana sayfa sadece son çare) | ✅ | `openReaderMainPage` |
| T5 | Üst kaynak kartında madde başlığını göster + dil sürümü adı ("Vikipedi · Açık Ansiklopedi") | ✅ | `MediaWikiTabContent.MediaWikiHeaderCard` |
| T6 | Çevirileri kaynak metne bağla (`translationBases`) + bayat çeviri temizliği | ✅ | `FandomMediaWikiViewModel.translate/currentTextForKey/pruneStaleTranslations` |
| T7 | Kaynak değişiminde tüm türev durumu sıfırla (`switchSource`) | ✅ | `FandomMediaWikiViewModel.switchSource` |
| T8 | Vikipedi'de sessiz TR dil geçişini kaldır (madde + karakter sayfası) | ✅ | `FandomRepository.buildMediaArticle`, `buildPage` |
| T9 | Kaynak dili hedef dille aynıysa uygulama içi çeviriyi hiç başlatma | ✅ | `isTargetLanguageSource` |
| T10 | Vikipedi birincil kaynakken kardeş dil sürümünü de kaynak çipi yap (TR ↔ EN) | ✅ | `FandomWikiResolver.resolveWikipediaSources/resolveAll` |
| T11 | Sitelink anahtarı doğrulaması (commonswiki gibi geçersiz anahtarları atla) | ✅ | `FandomWikiResolver.add` |
| T12 | İsimle aramada Wikidata kimlik denetimi (`wikibase_item`) | ✅ | `WikipediaResolver.identityMismatch`, `FandomApi.pageWikidataItems` |
| T13 | Vikipedi'de "evren rehberi" kartını kapat (`GuideState.Unsupported`) | ✅ | `FandomMediaWikiViewModel`, `MediaWikiGuideCard` |
| T14 | Vikipedi'de "Seri özeti" (ana sayfa özeti) kartını gösterme | ✅ | `MediaWikiTabContent` |
| T15 | Kaynak/lisans etiketlerini düzelt (Kaynak: Vikipedi + CC BY-SA 4.0) | ✅ | `CharacterWikiTabContent.WikiFooter`, `MediaWikiFooter`, `WikiItemSheet` |
| T16 | Galeri kaynak adını wiki ailesine göre göster | ✅ | `FandomUrls.displaySourceName` + 3 çağrı yeri |
| T17 | Vikipedi'de gereksiz `prop=images` isteğini atla | ✅ | `FandomRepository.enrichArticlePage` |
| T18 | Okuyucu özet satırını Vikipedi'de `extracts` ile doldur | ✅ | `FandomApi.pageMeta` |
| T19 | Madde önbellek sürümünü `v6`'ya yükselt | ✅ | `FandomRepository.mediaArticleCacheKey` |
| T20 | Kaynak çipleri satırını yatay kaydırılabilir yap + yinelenen isim aramasını engelle | ✅ | `MediaWikiTabContent`, `FandomWikiResolver.resolveAll` |
| T21 | Birim testi: kaynak adı eşlemesi | ✅ | `FandomUrlsTest` |
| T22 | Sürüm notları (TR/EN) + kök neden raporu | ✅ | `release_notes_v2.4.289.md`, `RELEASE_NOTES.md`, `KOK_NEDEN_RAPORU_…md` |
| T23 | Derleme + birim testleri (`./gradlew test assembleGmsRelease`) | ⏳ **Kullanıcıda** | — |
| T24 | Cihazda uçtan uca doğrulama (aşağıdaki senaryolar) | ⏳ **Kullanıcıda** | — |

---

## 3) Doğrulama senaryoları (cihazda)

1. **Attack on Titan** detay → Wiki sekmesi:
   - "İlgili maddeler" şeridinde yapımla ilgili başlıklar olmalı (Hajime Isayama, Wit Studio, anime…);
     Tarım/Hukuk/Ahlat/Türkçe **olmamalı**.
   - Herhangi bir karta dokun → o **maddenin** açılması (Anasayfa değil).
2. Üstteki **"Vikipedi'de oku"** düğmesi → çözülen madde (ör. "Attack on Titan (dizi)").
3. Kaynak çipleri: **Vikipedi (TR)** ↔ **Wikipedia** geçişi; geçişte İçindekiler, özet ve
   çeviri durumu **karışmamalı** (eski maddenin metni kalmamalı).
4. "Wiki özeti" kartında Orijinal ↔ Çeviri:
   - Vikipedi TR + Türkçe hedef → çeviri düğmesi **içerik değiştirmemeli** (aynı metin).
   - Wikipedia EN + Türkçe hedef → Çeviri, **aynı metnin** çevirisi olmalı.
5. "Evren rehberi" ve "Seri özeti" kartları Vikipedi kaynağında **görünmemeli**.
6. Alt bilgi: **"Kaynak: Vikipedi"** + CC BY-SA 4.0 (Fandom eki olmamalı).
7. Fandom kaynağına dönüşte tüm Vikipedi davranışının **eski hâlinde** kaldığını doğrula
   (ilgili wiki sayfaları ana sayfadan, evren rehberi açık, "Fandom sayfasını" etiketi).

---

## 4) Riskler / notlar

- Bu ortamda JDK/Gradle olmadığı için **derleme ve testler çalıştırılamadı**; dosyalar Kotlin
  söz dizimi ayrıştırıcısıyla doğrulandı (47 dosya, hata yok). Tip kontrolü kullanıcı tarafında
  yapılacak (T23).
- Ağ erişimi olmadığı için canlı Vikipedi yanıtlarıyla deneme yapılamadı (T24).
- Değişiklikler geriye dönük uyumlu: Fandom davranışı korunuyor, yalnızca eski madde önbellek
  sürümü (`v5` → `v6`) bilinçli olarak geçersiz kılındı.

---

## 5) İsteğe bağlı kalan iyileştirmeler (yapılmadı, öneri)

- Vikipedi maddelerinde "Ayrıca bakınız" bölümünü ayrı bir "İlgili maddeler" bloğu olarak öne çıkarma.
- Okuyucuda Vikipedi için "kaynakça/dış bağlantılar" bölümlerini özel bir görünümle listeleme.
- `WikipediaResolver` isim aramasını Türkçe başlık varyantlarıyla (ör. "(dizi)", "(anime)")
  genişletme ve sonucu kısa süreli bellekte tutma (ağ trafiği azaltma).
