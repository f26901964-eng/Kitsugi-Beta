# Kitsugi Beta v2.4.293

## 🇹🇷 Türkçe

### 📚 Wiki Sekmesi: Tam İçerik ve Ayrıntılı Kartlar
- **Boş Maddelerde Tam Sayfa:** Yapım maddesi içeriği boş kalan veya bilgi kutusu ayrıştırılamayan wiki sayfalarında (örneğin *Kimi no Na wa. Wiki*) sekme artık boş bir kart yerine doğrudan wiki'nin tam sayfasını yükler (kapak, açıklama, giriş metni ve yerinde açılan İçindekiler bölümleri).
- **Tam Ayrıntılı İlişkili Kartlar:** İlgili wiki sayfaları, karakterler, oyuncu kadrosu, seslendirmenler, ekip ve müzik maddeleri artık yalnızca 2 satırlık bir önizleme değil; giriş metni ve eksiksiz bilgi kutusu alanlarıyla (spoiler korumalı) tam ayrıntıyla gösterilir. "Sayfayı aç" eylemi ikincil seçenek haline getirildi.
- **Arka Planda Aşamalı Zenginleştirme:** İlgili sayfalar önce hızlıca önizleme verisiyle listelenir, ardından arka planda her sayfanın giriş bölümü (section 0) ve tam bilgi kutusu çekilerek 3'erli gruplar halinde kartların içine işlenir. Ağ hatasında kartlar kaybolmaz veya boşalmaz.
- **Okuyucu İçi İlgili Sayfalar Uyumlaştırması:** Tam wiki okuyucusunun sonundaki ilgili sayfalar şeridi de aynı tam ayrıntı kartlarına ve spoiler korumasına kavuşturuldu.
- **Çeviri ve Geçiş Uyumu:** Sekme içine yüklenen tam sayfanın giriş metni ve zengin kartlar otomatik çeviri akışına bağlandı; "Orijinal / Çeviri" düğmeleriyle sorunsuz yönetilebilir.

---

## 🇬🇧 English

### 📚 Wiki Tab: Full In-Tab Content & Rich Detail Cards
- **Full Inline Page for Empty Articles:** When an article lacks structured infobox content or fails to parse (e.g. *Kimi no Na wa. Wiki*), the tab now renders the complete wiki page directly inside the tab (cover, description, lead section, and collapsible table of contents) without requiring user navigation.
- **Rich Detail Cards:** Related wiki pages, cast, voice actors, crew/staff, and soundtrack entries are no longer truncated 2-line preview cards; they now feature the full lead summary and complete infobox fields with spoiler protection. Opening the external/reader page is now a secondary action.
- **Progressive Background Enrichment:** Related pages display initial preview data instantly, while lead text and comprehensive infoboxes are fetched in the background in chunks of 3 and populated into the cards. Cards never drop or disappear on network failures.
- **Reader Related Section Parity:** The related pages strip at the bottom of the full wiki reader now shares the exact same rich detail card layout and spoiler guard.
- **Translation & Toggle Integration:** In-tab page leads and enriched cards seamlessly plug into the auto-translation pipeline with reliable "Original / Translation" toggles.
