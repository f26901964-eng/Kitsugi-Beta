# Wiki (Fandom + Vikipedi) i18n Eksiksizleştirme Raporu — 2026-10-16

## Sorun (ekran görüntüleri: Solo Leveling → Wiki sekmesi)
1. **Bilgi kutusu etiket sütunu** karma dildi: `Yaratıcı / Çizer / Yayıncı / Yönetmen / Stüdyo / Bölüm sayısı`
   çevrilmişken `Music by`, `Licensed by`, `Original network`, `Webtoon · service` İngilizce kalıyordu.
2. **İçindekiler / bölüm başlıkları** karma dildi: `Hikâye / Karakterler / Medya ve uyarlamalar / Karşılanış`
   çevrilmişken `Critical response`, `Accolades`, `Live-action series`, `Continuation`, `Second edition`,
   `Other characters` vb. İngilizce kalıyordu.

## Kök neden
- Wiki arayüz kroması (Bilgi kutusu, İçindekiler, Spoiler, Daha az göster, kopyala/çevir düğmeleri…)
  **zaten tamamen string kaynağından** geliyor; wiki UI dosyalarında hardcode metin YOK
  (denetlendi: `MediaWikiTabContent`, `CharacterWikiTabContent`, `FandomWikiReader`,
  `WikiPagePreviewCard`, `WikiTranslateButton`, `WikiTabPlaceholders`, `FandomWikiSettingsSection` —
  bulunan tek sabitler madde işareti `•` ve tırnak karakteri).
- Eksik olan katman, İngilizce şablon alanlarını/başlıklarını arayüz diline eşleyen iki saf sözlük:
  `FandomInfoboxLabels` (etiket / grup başlığı / kısa değer) ve `FandomSectionTitles` (bölüm başlığı).
  Sözlükte olmayan anahtar özgün (İngilizce) biçimiyle ekrana basılıyordu.
- Ek not: TR Vikipedi'deki bazı madde başlıkları topluluk çevirisinde İngilizce bırakılmıştır
  (ör. `Second edition`, `Critical response`); uygulama başlık eşleyicisi bu kalıntıları da
  yerelleştirebildiği için sözlüğün genişletilmesi tek kalıcı çözümdür.

## Çözüm
### 1) Dil dosyalarına 215 yeni çift eklendi (TR `values/strings.xml` + EN `values-en/strings.xml`, aynı sıra)
- **29 bilgi kutusu etiketi:** `fandom_ib_music` (Müzik / Music by), `fandom_ib_licensed` (Lisanslayan /
  Licensed by), `fandom_ib_service` (Yayın servisi / Serialization service), `fandom_ib_producer`,
  `fandom_ib_magazine`, `fandom_ib_record_label`, `fandom_ib_rating`, `fandom_ib_season_count`,
  `fandom_ib_box_office`, `fandom_ib_budget`, `fandom_ib_awards`, `fandom_ib_language`,
  `fandom_ib_source`, `fandom_ib_years_active`, `fandom_ib_known_for`, `fandom_ib_spouse`,
  `fandom_ib_children`, `fandom_ib_parents`, `fandom_ib_siblings`, `fandom_ib_sound_director`,
  `fandom_ib_screenplay`, `fandom_ib_distributor`, `fandom_ib_first_published`,
  `fandom_ib_english_title`, `fandom_ib_literal_title`, `fandom_ib_demographic`,
  `fandom_ib_streaming`, `fandom_ib_translator`, `fandom_ib_license`.
- **12 grup başlığı:** roman, manga, webtoon, anime, film, OVA/ONA, video oyunu, sahne uyarlamaları,
  live-action, ilgili eserler, müzik, eğitim.
- **47 bölüm başlığı:** `Accolades`, `Live-action series/film`, `Continuation`, `Spin-off'lar`,
  `Öncül eserler`, `Yeniden yapımlar`, `Baskılar` + sıralı baskılar (Birinci…Onuncu, Gözden geçirilmiş,
  Yeni, Özel, Deluxe, Yıl dönümü, Omnibus, Tam, Koleksiyon), `Diğer karakterler`, `Yardımcı karakterler`,
  `Antagonistler ve kötüler`, `Organizasyonlar ve gruplar`, `Temalar ve analiz`, `Ev medyası ve dağıtım`,
  `Oyuncu seçimi`, `Çekimler`, `Görsel efektler`, `Senaryo`, `Webtoon`, `Webtoon'da`, `Filmde`,
  `Ek okumalar`, `Etkinlikler`, `Çeviriler ve yerelleştirme`, `Tartışmalar ve eleştiriler`,
  `Hayran kitlesi`, `Esin kaynakları`, `Kültürel göndermeler`, `Tanıtım ve pazarlama`,
  `Gişe hasılatı`, `İzlenme oranları`.
- **127 kısa değer:** medeni durum (bekâr/evli/boşanmış/dul/nişanlı/ilişkide), durum
  (mühürlendi/hapiste/esir/komada/dirildi/reenkarne/uyandı/ele geçirildi/dönüştü/evrimleşti/birleşti/sürgün),
  cinsiyet kimlikleri (non-binary, intersex, agender, genderfluid, trans), türler (vampir, kurt adam, elf,
  cüce, ejderha, melek, tanrı/ça, ruh, hayalet, zombi, uzaylı, robot, android, siborg, yapay zekâ, klon,
  mutant, insanoid, canavar, hayvan, yokai, yaşayan ölü) ve ~75 meslek/rol (avcı, paralı asker, suikastçı,
  şövalye, samuray, ninja, büyücü, cadı, sihirbaz, kahraman, kral/kraliçe/prens/prenses/imparator…,
  profesör, doktor, bilim insanı, mühendis, programcı, yazar, sanatçı, oyuncu, idol, şarkıcı, müzisyen,
  dedektif, polis, avukat, politikacı, öğrenci türleri, ödül avcısı, CEO, müdür, gazeteci, fotoğrafçı,
  tasarımcı, mimar, pilot, şoför, tamirci, demirci, simyacı, çağırıcı, şifacı, okçu, kılıç ustası, kaptan,
  komutan, lider, çiftçi, barmen, barista, garson, hemşire, itfaiyeci…) + hizalanma (iyi/kötü/tarafsız).

### 2) Eşleyiciler genişletildi
- `FandomInfoboxLabels.labelRes`: ekran görüntüsündeki dört kaçak anahtar eklendi
  (`music by`, `licensed by`, `original network`, `webtoon · service` / `webtoon service`) +
  ~150 yeni anahtar varyantı; yanlış eşleşmeler düzeltildi (`producer/produced by` artık Stüdyo değil
  **Yapımcı**, `magazine/serialization` artık Yayıncı değil **Dergi**, `label` artık **Müzik şirketi**,
  `streaming` artık Kanal değil **Dijital yayın platformu**, `demographic` artık Tür değil **Hedef kitle**,
  çıplak `music` artık Tema şarkısı değil **Müzik**).
- `FandomInfoboxLabels.headerResId`: 12 yeni medya grubu başlığı + mevcut gruplara eş anlamlılar
  (dub/voice cast, reading order/watch order, staff/crew, fighting styles, supplementary info…).
- `FandomInfoboxLabels.valueResId`: yukarıdaki 127 değer için dallar.
- `FandomSectionTitles.resId`: `critical response → Karşılanış`, `accolades → Ödüller ve başarılar`,
  `live-action series → Live-action dizi`, `continuation → Devam eserleri`, `second edition → İkinci baskı`
  (ve diğer baskılar), `other characters → Diğer karakterler` vb. + mevcut dallara ~120 anahtar
  (manga chapters, tankōbon, broadcast history, pre/post-production, worldbuilding/lore, romance,
  cultural impact, soundtrack/OST, tabletop/arcade, tie-ins, further reading…).

### 3) Bilinçli olarak çevrilmeyenler (özgün biçimde kalır)
- Evren içi özel adlar: `Sung Family`, `Shadow Army`, `National Level Hunters`, `Monarchs`,
  `Solo Leveling: Ragnarok` vb. — sözlükle çevirilmek yanlış eşleşme üretir.
- Serbest metin değerler (akraba listeleri, alıntılar) ve tarih/ay adları — bunlar için arayüzdeki
  çeviri düğmeleri (`fandom_translate_infobox`, `WikiTranslateButton`) tasarlanmıştır.

## Doğrulama
- TR/EN parite: 7601 = 7601 string, fark kümesi boş; iki dosya da XML olarak parse ediliyor.
- `when` dallarında çift anahtar yok (otomatik kontrol); küme parantez / parantez dengesi tamam.
- Kodda başvurulan tüm `R.string.*` adları her iki dilde de var (PlatformErrorCatalog istisnası
  ayrı dosyadadır: `platform_error_codes.xml` — mevcut durum, bu değişiklikle ilgisiz).
- Ekran görüntüsü simülasyonu: `Music by`, `Licensed by`, `Original network`, `Webtoon · service`,
  `Critical response`, `Accolades`, `Live-action series`, `Continuation`, `Second edition`,
  `Other characters` … tamamı artık string kaynağına eşleniyor.

## Değişen dosyalar
- `app/src/main/res/values/strings.xml` (+215)
- `app/src/main/res/values-en/strings.xml` (+215)
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomInfoboxLabels.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomSectionTitles.kt`
- Bu rapor
