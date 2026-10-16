# PLAN_TASK — Wiki (Fandom + Vikipedi) i18n Eksiksizleştirme (2026-10-16)

Bağlı rapor: `WIKI_I18N_EKSIKSIZ_COZUM_RAPORU_2026-10-16.md`
Dal: `arena/9355169a-kitsugi-beta` · Commit: `12ab3fe`

## Görev tanımı
Wiki sekmesinde (Fandom + Vikipedi) İngilizce kalan **tüm global** metinleri
(bilgi kutusu etiketleri, grup başlıkları, kısa değerler, bölüm/İçindekiler başlıkları)
TR+EN dil dosyalarına eksiksiz eklemek ve eşleyicileri genişletmek.

## Task listesi
- [x] 1. Wiki UI dosyalarında hardcode metin denetimi (MediaWikiTabContent, CharacterWikiTabContent,
      FandomWikiReader, WikiPagePreviewCard, WikiTranslateButton, WikiTabPlaceholders,
      FandomWikiSettingsSection) → hardcode yok; tüm kroma stringResource'den.
- [x] 2. Ekran görüntüsü kök neden: `FandomInfoboxLabels` / `FandomSectionTitles` sözlük açıkları
      (`Music by`, `Licensed by`, `Original network`, `Webtoon · service`, `Critical response`,
      `Accolades`, `Live-action series`, `Continuation`, `Second edition`, `Other characters`…).
- [x] 3. Yanlış dal eşleşmelerini düzelt: producer→Yapımcı, magazine→Dergi, label→Müzik şirketi,
      streaming→Dijital yayın platformu, demographic→Hedef kitle, çıplak "music"→Müzik.
- [x] 4. `values/strings.xml` (TR) + `values-en/strings.xml` (EN): 215 yeni string çifti
      (29 etiket, 12 grup başlığı, 47 bölüm başlığı, 127 kısa değer).
- [x] 5. `FandomInfoboxLabels`: ~150 yeni etiket anahtarı + value/header dalları.
- [x] 6. `FandomSectionTitles`: ~120 yeni başlık anahtarı + baskı (edition) kalıpları.
- [x] 7. Doğrulama: TR/EN parite 7601=7601, XML parse, çift `when` anahtarı yok,
      tüm `R.string.*` iki dilde mevcut, ekran görüntüsü simülasyonu temiz.
- [x] 8. Rapor + commit + push (`12ab3fe`).
- [ ] 9. (Opsiyonel) Tarih/ay adı yerelleştirme katmanı — kullanıcı onayına bırakıldı.
- [ ] 10. (Opsiyonel) PR açılması — kullanıcı onayına bırakıldı.

## Bilinçli kapsam dışı
- Evren içi özel adlar (Sung Family, Shadow Army, National Level Hunters, Monarchs,
  Solo Leveling: Ragnarok…) → sözlükle çevrilmez; çeviri düğmesine bırakılır.
- Serbest metin değerler ve tarih/ay adları → çeviri düğmesi / opsiyonel task 9.

## Değişen dosyalar
1. `app/src/main/res/values/strings.xml`
2. `app/src/main/res/values-en/strings.xml`
3. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomInfoboxLabels.kt`
4. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomSectionTitles.kt`
5. `WIKI_I18N_EKSIKSIZ_COZUM_RAPORU_2026-10-16.md`
6. `PLAN_TASK_WIKI_I18N_EKSIKSIZ_2026-10-16.md` (bu dosya)
