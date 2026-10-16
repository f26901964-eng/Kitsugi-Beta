# Kitsugi Beta v2.4.291

## 🇹🇷 Türkçe

### Wiki Sekmesi Artık İçeriğin Tamamını Gösteriyor
- **Kök Neden Düzeltmesi (Boş Madde Kartı):** Bazı wiki'lerde (ör. *Kimi no Na wa. Wiki*) yapım maddesi çözülüyordu ama bilgi kutusu alanları, özet ve bölümler ayrıştırılamıyordu; sekme bomboş bir "başlık + kapak" kartı gösteriyor, asıl içerik yalnızca başka bir yere tıklayınca (okuyucuda) görünüyordu. Madde içeriği boş kaldığında artık **wiki'nin kendi sayfası sekme içine yüklenir**: kapak, açıklama, giriş metni ve İçindekiler (bölümler tek dokunuşla açılır) doğrudan sekmede çizilir.
- **İlgili Wiki Sayfaları Tam Ayrıntıyla:** "İlgili wiki sayfaları" bölümünde kartlar yalnızca kapak + iki satır açıklama ve "Fandom sayfasını aç" düğmesi gösteriyordu. Artık her ilgili sayfa **giriş metni ve bilgi kutusunun tüm alanlarıyla** (spoiler korumasına saygılı) kart içinde listelenir; sayfayı açma yalnızca ikincil eylemdir.
- **Karakter / Ekip / Seslendirmen Maddeleri:** Karakter, oyuncu kadrosu, seslendirmen, ekip ve müzik maddeleri de aynı biçimde tam ayrıntıyla gösterilir (giriş metni + bilgi kutusu alanları + dil/bağlam bilgisi). Yatay kaydırmalı önizleme şeridi kaldırıldı; yerine dikey tam ayrıntı listesi geldi.
- **Okuyucuda da Aynı Davranış:** Uygulama içi wiki okuyucusunun sonundaki "İlgili wiki sayfaları" şeridi de aynı tam ayrıntı kartlarına dönüştürüldü.
- **Arka Planda Zenginleştirme:** İlgili sayfalar önce hızlıca önizleme verisiyle görünür, ardından her sayfa için giriş bölümü (section 0) ve eksiksiz bilgi kutusu çekilerek **aynı kartın içine** işlenir. İstekler 3'erli gruplar halinde atılır, her gruptan sonra liste güncellenir; ağ hatasında kartlar önizleme verisiyle kalır, liste asla boşalmaz.
- **Otomatik Çeviri Uyumu:** Madde içeriği boşken yüklenen tam sayfanın giriş metni de otomatik çeviri akışına bağlandı; "Orijinal" düğmesiyle geri alınabilir. Önizleme kartlarındaki bilgi kutusu alanları mevcut çeviri altyapısını kullanmaya devam eder.
- **Doğru Yerde Gösterim:** Madde doluysa hiçbir şey değişmez (madde sekmede gösterilmeye devam eder); yalnızca içeriği olmayan sayfalarda tam sayfa kartı devreye girer. Kaynak (Fandom ↔ Vikipedi) değiştiğinde veya madde yeniden çekildiğinde tam sayfa durumu sıfırlanır.

### Teknik İyileştirmeler
- `FandomRepository.enrichRelatedPagesWithLead(...)`: ilgili sayfaları giriş bölümü verisiyle birleştirir (giriş metni öne geçer, eksik bilgi kutusu etiketleri tamamlanır, görseller birleştirilir).
- `FandomCharacterPage.hasWikiContent`: madde için gösterilebilecek gerçek içerik olup olmadığını tek yerde belirler.
- `FandomMediaWikiViewModel.InlinePageState`: madde boşken sekme içinde gösterilen tam sayfa + bölümleri için yeni durum akışı (`ensureInlinePage`, `loadInlineSection`, `retryInlinePage`).
- Kullanılmayan `WikiPagePreviewCard` bileşeni kaldırıldı (tüm ilişkili kartlar tam ayrıntı kartına geçti).

---

## 🇬🇧 English

### The Wiki Tab Now Shows the Full Content
- **Root-Cause Fix (Empty Article Card):** On some wikis (e.g. *Kimi no Na wa. Wiki*) the media article resolved but its infobox fields, summary and sections could not be parsed, so the tab showed an empty "title + cover" card and the real content only appeared after tapping into the reader. When the article has no usable content, the **wiki's own page is now loaded inline in the tab**: cover, description, lead text and table of contents (sections expand in place).
- **Related Wiki Pages in Full Detail:** The "Related wiki pages" section used to show only a cover, a two-line description and an "Open Fandom page" button. Every related page is now listed with its **lead text and all infobox fields** (respecting spoiler protection); opening the page is a secondary action.
- **Character / Cast / Voice Actor / Staff / Music Articles:** Character, cast, voice-actor, staff and music pages get the same full-detail treatment (lead text + infobox fields + language/context). The horizontal preview rail was replaced by a vertical full-detail list.
- **Same Behaviour in the Reader:** The in-app wiki reader's trailing "Related wiki pages" rail now uses the same full-detail cards.
- **Background Enrichment:** Related pages first appear with fast preview data, then each page's lead section (section 0) and complete infobox are fetched and merged **into the same card**. Requests go out in groups of three with the list refreshing after each group; on network failure the cards keep their preview data and the list never empties.
- **Auto-Translation Parity:** The inline full page's lead text joins the auto-translation pipeline and can be toggled back with the "Original" button; related infobox fields keep using the existing translation infrastructure.
- **Correct Rendering:** When the article has content nothing changes (the article stays on the tab); the inline full-page card only kicks in for content-less pages. Switching source (Fandom ↔ Wikipedia) or re-fetching the article resets the inline page state.

### Technical Improvements
- `FandomRepository.enrichRelatedPagesWithLead(...)`: merges each related page's lead-section data (lead text takes precedence, missing infobox labels are completed, images are combined).
- `FandomCharacterPage.hasWikiContent`: single source of truth for "does this article have renderable content?".
- `FandomMediaWikiViewModel.InlinePageState`: new state flow for the inline full page and its sections (`ensureInlinePage`, `loadInlineSection`, `retryInlinePage`).
- Removed the now-unused `WikiPagePreviewCard` component (all related cards moved to the full-detail card).
