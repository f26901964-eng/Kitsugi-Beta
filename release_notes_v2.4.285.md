# Kitsugi Beta v2.4.285

## 🇹🇷 Türkçe

### Wiki Bağlantı Çiplerinde Yüzde-Kodlu Başlık Hatası Düzeltildi
- **Kök Neden:** Bölüm altı bağlantı çipleri, bağlantının görünen metninden değil ham `href` adresinden üretiliyordu (`FandomHtmlText.parseWikiLink`). Bu çözümleyici yalnızca `%20`, `%2F`, `%3A`, `%28`, `%29` kodlarını elle çözüyordu; ō gibi ASCII dışı karakterlerin UTF-8 kodları (`%C5%8D`) olduğu gibi kalıyordu. Bu yüzden çiplerde `Shinj%C5%8D, Yamagata`, `Weekly Sh%C5%8Dnen Jump`, `Tank%C5%8Dbon` gibi başlıklar görünüyordu. Düz ASCII başlıklar (`Shueisha`, `Fine-art`…) şans eseri doğru göründüğü için hata yalnızca özel karakterli başlıklarda fark ediliyordu.
- **Düzeltme:** Başlığın tamamı artık UTF-8 olarak yüzde-kodundan çözülüyor (`Shinjō, Yamagata`, `Weekly Shōnen Jump`, `Tankōbon`, `Ten de Shōwaru Cupid`). `C++` gibi başlıklardaki `+` işareti de boşluğa dönüşmeden korunuyor; bozuk yüzde dizileri uygulamayı düşürmeden eski güvenli davranışa düşüyor.
- **Tıklama da düzeldi:** Çipe tıklanınca kodlu başlık sayfa aramasında tekrar kodlanıyor (`%25…`) ve sayfa bulunamıyordu; artık doğru sayfa açılıyor. Aynı düzelme "İlgili wiki sayfaları" adaylarını da kapsıyor.
- **Eski önbellek temizliği:** Bölüm önbelleği anahtarı `sec:v4` → `sec:v5` yükseltildi; 7 günlük eski, bozuk çipli kayıtlar güncelleme sonrası anında geçersiz sayılıyor, kullanıcının önbellek temizlemesi gerekmiyor.
- **Testler:** `FandomHtmlTextTest`'e kodlanmış ō/ş başlıkları, kodlu noktalama, `+` koruması ve bozuk kaçış dizisi senaryoları eklendi.

### Wiki Sekmeleri Chrome/Brave Düzenine Geçti
- **Bağlantılar yeni sekmede:** Sayfa içi bir bağlantıya, ilgili sayfa kartına veya bölüm çipine tıklamak artık bulunduğun sayfanın üstüne gitmiyor; sayfa, aktif sekmenin **hemen sağında yeni sekme** olarak açılıyor. Kaynak sayfa bir dokunuş uzağında aynen duruyor.
- **Daralıp genişleyen sekmeler:** Sekmeler genişliği eşit paylaşır — sekme açıldıkça hepsi birlikte daralır, kapandıkça yeniden genişler. En dar genişliğe gelinince şerit yatay kaydırılabilir olur ve yeni açılan sekme otomatik görünür alana getirilir.
- **Aktif sekme görünümü:** Chrome'daki gibi aktif sekme içerikle aynı arkaplanda üst köşeleri yuvarlı durur; daralmış pasif sekmelerde kapat (×) gizlenir, sekme genişleyince geri gelir.
- **Kopya önleme:** Aynı sayfa zaten bir sekmede açıksa ikincisi açılmaz, var olan sekmeye geçilir; sayfanın kendi bağlantısına tıklamak hiçbir şey yapmaz.
- **Sekme sınırı koruması:** 12 sekme doluysa bağlantı ölü doğmaz — aynı sekmede geçmişe eklenerek açılır, geri tuşu kaynak sayfaya döndürür.
- **`+` ve kapatma düzeni:** `+` ile çoğaltılan sekme de listenin sonuna değil aktif sekmenin sağına açılır. Aktif sekmeyi kapatmak önce sağ komşuyu, yoksa solu etkinleştirir. Sekmeye **uzun basmak** diğer tüm sekmeleri kapatır.
- **Testler:** `FandomReaderBrowserTest` ile sekme sırası, sağa ekleme, kopya önleme, sınır ve kapatma davranışları kapsama alındı.

---

## 🇬🇧 English

### Fixed Percent-Encoded Titles in Wiki Link Chips
- **Root cause:** Section link chips were derived from the raw `href` instead of a decoded title (`FandomHtmlText.parseWikiLink`), and only a handful of escapes (`%20`, `%2F`, `%3A`, `%28`, `%29`) were decoded manually. UTF-8 sequences for non-ASCII characters (e.g. ō = `%C5%8D`) leaked through, rendering chips like `Shinj%C5%8D, Yamagata`, `Weekly Sh%C5%8Dnen Jump`, `Tank%C5%8Dbon`. Plain-ASCII titles looked fine by luck, masking the bug.
- **Fix:** Titles are now fully percent-decoded as UTF-8 (`Shinjō, Yamagata`, `Weekly Shōnen Jump`, `Tankōbon`, `Ten de Shōwaru Cupid`). Literal `+` (e.g. `C++`) is preserved instead of becoming a space, and malformed escapes fall back safely without crashing.
- **Navigation fixed too:** Tapping a chip previously re-encoded the encoded title (`%25…`) and the page could not be found; chips and "Related wiki pages" candidates now open the correct article.
- **Stale cache invalidated:** Section cache key bumped `sec:v4` → `sec:v5`, so old broken chips (7-day TTL) disappear immediately after updating — no manual cache clear needed.
- **Tests:** Added coverage in `FandomHtmlTextTest` for encoded ō/ş titles, encoded punctuation, plus-sign preservation, and malformed escapes.

### Wiki Tabs Reworked to Chrome/Brave Behaviour
- **Links open in a new tab:** Tapping an in-page link, related-page card or section chip no longer navigates away; the page opens in a **new tab immediately to the right** of the active tab, leaving the source page one tap away.
- **Shrinking/growing tabs:** Tabs share the available width equally — they shrink together as tabs are opened and expand again as they are closed. At minimum width the strip becomes horizontally scrollable and the newly opened tab is auto-scrolled into view.
- **Active tab look:** Like Chrome, the active tab sits on the content background with rounded top corners; on narrow inactive tabs the close (×) button hides and returns when there is room.
- **No duplicates:** If the page is already open, its tab is focused instead of opening a second copy; tapping a page's link to itself is a no-op.
- **Tab-limit guard:** When all 12 tabs are in use, a link still works — it opens in the current tab with history, so Back returns to the source page.
- **`+` and closing order:** Duplicated tabs also open to the right of the active tab. Closing the active tab activates the right neighbour first, then the left. **Long-pressing** a tab closes all other tabs.
- **Tests:** `FandomReaderBrowserTest` covers tab order, right-side insertion, deduplication, the tab limit and close behaviour.
