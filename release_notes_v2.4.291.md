# Kitsugi Beta v2.4.291

## 🇹🇷 Türkçe

### Birleşik Wiki Önizleme Kartı (WikiPagePreviewCard)
- **Tek Tip Modern Kart:** Karakter ve yapım sayfalarındaki oyuncu kadrosu (cast), seslendirenler (voice actors), ekip üyeleri (staff/crew), müzik/şarkı listeleri ve ilgili wiki sayfaları artık aynı `280 × 144 dp` boyutundaki tek tip `WikiPagePreviewCard` ile gösterilir. Daha önce yan yana duran 160 dp'lik küçük kart ile yüksekliği içeriğe göre uzayan 300 dp'lik detay kartı kaldırıldı; eski kart bileşenleri (`WikiRelatedPageCard`, `WikiRelatedPageDetailCard`, `WikiRelatedEntityDetailCard`) koddan silindi.
- **Kart İçeriği:** Solda kapak görseli (yoksa başlık harfi yer tutucusu), sağda başlık + kısa açıklama ve alt satırda kaynak rozeti (Fandom / Vikipedi) ile rol rozeti (Kadro, Seslendiren, Ekip, Müzik…) bulunur.
- **Doğrudan Uygulama İçi Okuyucu:** Karta dokunulduğunda tarayıcıya atlamak yerine doğrudan uygulama içi Fandom veya Vikipedi okuyucusu açılır. Kart bileşeninin kendisi hiçbir koşulda harici tarayıcı (ACTION_VIEW) çalıştırmaz; tarayıcıya çıkış yalnızca okuyucunun üst çubuğundaki açık düğmededir.
- **Snap-Scroll / Kart Hizalama:** Tüm yatay wiki şeritleri sola hizalıdır ve kaydırma bırakıldığında sıradaki karta pürüzsüzce oturur (`WikiPreviewStripRow` + `KitsugiScrollDefaults.rememberRowSnapFling`, `SnapPosition.Start`). Bu düzen karakter sekmesi, yapım sekmesi, bölüm içi bağlantı şeritleri ve okuyucu içindeki ilgili sayfalar şeridinde ortaktır.
- **İlgili Wiki Sayfaları Şeridi:** Karakter ve yapım wiki sekmelerinde ilgili sayfalar listelenir; Vikipedi kaynağında başlık otomatik olarak "İlgili maddeler" olur, Fandom kaynağında "İlgili wiki sayfaları" kalır. Kaynak (Fandom ↔ Vikipedi) değiştirildiğinde başlık ve kart rozetleri kendiliğinden güncellenir.
- **Wiki Otomatik Çeviri & Orijinal/Çeviri Geçişi Güvenilirliği:** Tıklanabilir bağlantı içeren paragraflarda koruma jetonu bozulsa bile metin parçaları ayrı ayrı çevrilerek tüm paragrafın orijinale düşmesi engellendi; bağlantı adları ve hedefleri korundu. Açıklama kartındaki Orijinal/Çeviri düğmesi ve kaynaklar arası geçiş hafızası güvenilir hale getirildi.
- **Kritik Hata Düzeltmeleri:** `CharacterWikiTabContent` içerisindeki `page.pageTitle` kullanımı, şeritlerdeki `rememberLazyListState()` eksiklikleri ve `MediaWikiTabContent` kapsamındaki wiki kimliği (`wikiId`) geçişleri düzeltildi.

---

## 🇬🇧 English

### Unified Wiki Preview Card (WikiPagePreviewCard)
- **Single Modern Card:** Cast, voice actors, staff/crew, music/track lists and related wiki pages on character and production pages now all use the same `280 × 144 dp` `WikiPagePreviewCard`. The previous 160 dp card and the variable-height 300 dp detail cards (`WikiRelatedPageCard`, `WikiRelatedPageDetailCard`, `WikiRelatedEntityDetailCard`) were removed.
- **Card Content:** Cover image on the left (title-letter placeholder when missing), title + short description on the right, and a bottom row with the source badge (Fandom / Wikipedia) and a role badge (Cast, Voice Actors, Staff, Music…).
- **Direct In-App Reader:** Tapping a card opens the in-app Fandom or Wikipedia reader instead of jumping to a browser. The card itself never fires an external browser intent; browser exit exists only as the explicit button in the reader's top bar.
- **Snap-Scroll / Card Alignment:** All horizontal wiki strips are left-aligned and snap smoothly to the next card when scrolling stops (`WikiPreviewStripRow` + `KitsugiScrollDefaults.rememberRowSnapFling`, `SnapPosition.Start`), shared by the character tab, media tab, in-section link strips and the reader's related-pages strip.
- **Related Wiki Pages Strip:** Related pages are listed in both character and media wiki tabs; the heading automatically becomes "Related articles" for Wikipedia sources and stays "Related wiki pages" for Fandom. Switching the source (Fandom ↔ Wikipedia) updates the heading and card badges automatically.
- **Reliable Wiki Automatic Translation & Original/Translation Toggle:** When link protection tokens are dropped by the translation service, text segments between links are now translated individually so the whole paragraph is no longer dropped; clickable links and targets remain intact. Improved toggle reliability on description cards and state persistence across source switches.
- **Critical Fixes:** Resolved `page.pageTitle` usage in `CharacterWikiTabContent`, missing `rememberLazyListState()` wiring in strips, and `wikiId` scoping in `MediaWikiTabContent`.
