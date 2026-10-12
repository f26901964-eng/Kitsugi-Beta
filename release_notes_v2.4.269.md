# Kitsugi-Beta v2.4.269 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🌸 Yeni Premium Açılır Panel (KitsugiPremiumSheet)
- **Yay Fiziği & Aurora Glow:** Açılır panellere hafifçe sekerek açılan yay (spring) animasyonlu giriş ve yumuşak kayarak kapanış eklendi. Vurgu rengiyle uyumlu parlayan gradyan kenarlık ve renkli aurora gölgesi entegre edildi.
- **Modern Detaylar:** Gradyan sürükleme kulpu, gradyan ikon karosu, saç çizgisi ayracı, aktif rozet ve modern kapatma butonu. Skrim (karartma) dışına dokunulduğunda akıcı animasyonla kapanır; Android TV'de ortalanmış dialog görünümüne otomatik uyum sağlar.
- **Tema Entegrasyonu:** `LocalKitsugiAccent`, `Brush` ve `OnAccent` ile hem düz hem de gradyan temalarda renk uyumu korunur.

### 📋 Listem Filtre Alanı Panele Taşındı (KitsugiMyListFilterSheet)
- **Zıplamasız Liste Deneyimi:** Ekrandaki satır içi filtre alanı kaldırıldı; sağ üstteki filtre butonu artık yeni premium açılır paneli tetikler. Liste boyutu kaybolmaz ve zıplama yaşanmaz.
- **Tüm Filtreler Tek Panelde:** İçerik türü hapları, Durum, Medya Türü, Puan, Yıl, Ekstra ve Sıralama seçenekleri gradyan noktalı modern başlıklarla panele toplandı. "N aktif" rozeti, Temizle ve gradyan Bitti butonları eklendi.
- **Profil Listesi Uyumu:** Profil sekmesindeki kullanıcı listesi de aynı başlığı ve paneli paylaşır.

### ⚙️ Ayarlar Sekmelerinde Yenilenen Açılır Menüler
- **KitsugiDropdownMenu:** Yay animasyonlu "pop" açılış, gradyan kenarlık, aurora ışık bandı ve renkli glow ile premium yüzeye geçirildi.
- **KitsugiMenuItem:** Seçili satırda yay animasyonlu gradyan seçim çubuğu ve sekerek beliren onay işareti entegre edildi (layout kayması engellendi).
- **Kapsam:** Tercihler, Oynatıcı, Eklentiler, P2P, Stremio ve sistem ayarlarındaki tüm açılır menüler güncellendi.

---

## 🇬🇧 English

### 🌸 New Premium Sheet Architecture (KitsugiPremiumSheet)
- **Spring Physics & Aurora Glow:** Bottom sheets now enter with a delightful spring bounce and exit with smooth damping. Features gradient borders and ambient aurora glow tinted by the active theme accent.
- **Polished Sheet Controls:** Features a gradient drag handle, gradient icon tile, hairline divider, active badge, and modern dismiss button. Tap outside triggers animated dismissal; automatically adapts into a centered dialog on Android TV.
- **Theme Native:** Built with `LocalKitsugiAccent`, `Brush`, and `OnAccent` for seamless harmony across solid and gradient themes.

### 📋 MyList Filters Relocated to Premium Sheet (KitsugiMyListFilterSheet)
- **Stable Layout:** Replaced the inline filter section with a dedicated premium filter sheet opened via the top-right filter icon, eliminating list jumps and viewport shifting.
- **All Filter Categories in One Sheet:** Status, Media Type, Score, Year, Extra, and Sorting options organized under gradient-dotted section headers. Includes "N active" indicator, Reset, and Done actions.
- **Profile User List Parity:** Profile screen user list automatically inherits the new header and filter sheet.

### ⚙️ Overhauled Dropdown Menus Across Settings
- **KitsugiDropdownMenu:** Upgraded to the premium surface language featuring a spring "pop" entrance, gradient borders, aurora highlights, and ambient glow.
- **KitsugiMenuItem:** Features an animated spring selection bar and bouncing checkmark on selected items without layout shift.
- **Universal Application:** Applies across Preferences, Player, Extensions, P2P, Stremio, and all settings tabs.
