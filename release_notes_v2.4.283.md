# Kitsugi Beta v2.4.283

## 🇹🇷 Türkçe

### Wiki makale içi mavi bağlantılar artık uygulama içinde açılıyor
- **Kök neden:** Yapım (medya) detayındaki Wiki sekmesi, makale bölümlerinin altındaki mavi bağlantı şeridini (ör. Hunter × Hunter → *Hunters, Hunter License, Hunter Exam, Nen, Phantom Troupe, Gon Freecss, Kite…*) çizerken uygulama içi okuyucu geri çağrısını hiç iletmiyordu. Bağlantılar bu yüzden sessizce harici tarayıcıya düşüyordu; v2.4.282'deki "sıfır tarayıcı yönlendirmesi" medya makalesinin bölüm içi bağlantılarını kapsamıyordu.
- **Düzeltme:** Medya Wiki sekmesi artık bölüm altı bağlantıları, ilişkili sayfa kartları ve makale alt bilgisini aynı uygulama içi okuyucuya (`FandomWikiReader`) bağlıyor. Bağlantının hedef wiki kimliği korunuyor; karakter/ekip/seslendiren sayfalarında bağlantı, sayfanın kendi wiki'sinden farklı bir wiki'ye işaret ediyorsa okuyucu o wiki'de açılıyor.
- **İlişkili sayfa kartları:** "İlgili sayfalar" kartındaki *sayfayı aç* düğmesi ve görselsiz kart dokunuşu da artık tarayıcı yerine uygulama içi okuyucuyu açıyor (önceden her yerde tarayıcıya gidiyordu).
- **Ölü düğme kalmadı:** İlgili sayfa şeridinde uygulama içi okuyucu tanımlı değilse tıklama artık sessizce yutulmuyor.
- **Okuyucu hata durumu:** Bağlanılan sayfa yüklenemezse okuyucu artık sonsuz yükleme animasyonunda kalmıyor / tıklama tepkisiz kalmıyor; sayfa başlığıyla birlikte "Sayfa yüklenemedi · Tekrar dene" gösteriliyor (medya, karakter ve ekip okuyucu akışlarının üçünde de).

Tarayıcı yalnızca okuyucunun üst çubuğundaki dünya (🌐) simgesine bilerek dokunulduğunda açılır — davranış değişmedi.

---

## 🇬🇧 English

### In-article blue wiki links now open in-app
- **Root cause:** The media Wiki tab rendered the blue link strip under article sections (e.g. Hunter × Hunter → *Hunters, Hunter License, Hunter Exam, Nen, Phantom Troupe, Gon Freecss, Kite…*) without forwarding the in-app reader callback, so taps silently fell back to the external browser. The v2.4.282 "zero browser jumping" pass missed the in-article section links on media pages.
- **Fix:** The media Wiki tab now routes section links, related-entity cards and the article footer through the same in-app reader (`FandomWikiReader`). The target wiki id is preserved; on character/staff/voice-actor pages a link pointing at a different wiki opens the reader on that wiki.
- **Related page cards:** The *open page* button (and tapping an imageless card) in the related-pages card also opens the in-app reader instead of the browser.
- **No dead buttons:** The related-pages strip no longer swallows taps when no in-app reader is wired.
- **Reader failure state:** If a linked page cannot be loaded, the reader no longer spins forever or ignores the tap; it shows the page title with "Page could not be loaded · Retry" across media, character and staff reader flows.

The browser still only opens when the user explicitly taps the globe (🌐) action in the reader top bar.
