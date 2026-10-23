# Kitsugi Beta v2.4.353

## 🇹🇷 Türkçe

### 🎨 Wiki Açılır Sayfası Uygulama Temasıyla Uyumlu
- **Varsayılan tema artık uygulamayı izliyor:** `wikiPageTheme` varsayılanı `SITE` → `APP`. Koyu temada açık bir Fandom sayfası ekranı yakmaz; sayfa ile üst şerit arasında renk kırılması kalmaz. Özgün görünümü isteyenler için **Site teması (özgün)** seçeneği duruyor. Kayıtlı tercihi olan kullanıcıların seçimi değiştirilmez.
- **Tek yüzey gibi sekme şeridi:** Aktif sekme vurgu renginin %16 dolgusu + ince vurgu çerçevesi; pasif sekmeler tamamen saydam. Sekme sayısı arttığında şerit “kutu ormanına” dönmüyor. Kapat (×) yalnızca aktif/geniş sekmelerde görünüyor.
- **Bölümlere ayrılmış sayfa araçları:** Çeviri–gizlilik, Görünüm ve Sayfa başlıklı gruplar; uygulama genelindeki ayar kartlarıyla aynı dil.
- **Yükleme çizgisi kayması giderildi:** İlerleme çizgisi her zaman 2 dp yer kaplar; yükleme bitince WebView artık yukarı sıçramaz.
- **Sayfada bul çubuğu** yuvarlatıldı, odak rengi uygulama vurgusuna bağlandı.

### 🔗 Yeni Kullanım Kolaylıkları
- **Bağlantıyı paylaş** ve **Bağlantıyı kopyala:** ⋮ menüsünden, tarayıcıya çıkmadan. Özgün görünümde WebView'in gerçek adresi, sade okuyucuda sayfanın wiki adresi kullanılır.
- **Sekmeler (n) listesi:** Birden fazla sekme açıkken başlıklı, tek dokunuşla geçiş ve satır bazlı kapatma sunan kompakt liste.
- **Yazıyı büyüt / küçült:** 80–180 aralığında 10’luk adımlarla doğrudan uygulanır; her seferinde görünüm penceresini açmak gerekmez.
- **Okurken ekranı açık tut:** Yalnızca o sekme için geçerli okuma oturumu anahtarı; ayarlara yazılmaz.
- **Menü başlığı:** Kilit ikonu + alan adı ve kısa mod bilgisi (`Özgün sayfa · Masaüstü düzeni`), eski sürümde ekranın üçte birini kaplayan açıklama satırının yerini alır.
- **Ayarlardan erişim:** Ayarlar → Fandom Wiki bölümüne **Okuma görünümü** satırı eklendi (tema + yazı boyutu). Okuyucudaki pencereyle aynı ayarı yönetir; okuyucuyu açmadan değiştirilebilir.

---

## 🇬🇧 English

### 🎨 Wiki Reader Now Matches the App Theme
- **Default reading theme follows the app:** `wikiPageTheme` default changed from `SITE` to `APP`. A light Fandom page no longer glows inside a dark app, and the page/toolbar seam disappears. **Site theme (original)** remains available, and existing user choices are never overwritten.
- **Tab strip reads as one surface:** The active tab uses a 16% accent tint plus a thin accent outline; inactive tabs are fully transparent, so many tabs no longer look like a box grid. The close (×) affordance only appears on the active/wide tabs.
- **Grouped page tools:** Translation & privacy, Appearance, and Page sections mirror the app's settings cards.
- **No more loading jump:** The progress line always reserves 2 dp, so the WebView no longer shifts when loading finishes.
- **Find-in-page bar** is rounded with an app-accent focus color.

### 🔗 New Conveniences
- **Share link** and **Copy link** from the ⋮ menu without leaving the reader. Original view uses the real WebView address; the simple reader uses the page's wiki address.
- **Tabs (n) list:** With multiple tabs open, a compact list with titles, one-tap switching and per-row closing.
- **Increase / decrease text size:** Immediate 10-point steps within 80–180, no dialog round-trip.
- **Keep screen on while reading:** A session-scoped switch applied to that tab's WebView only; never written to settings.
- **Menu header:** Lock icon + host with a short mode line (`Original page · Desktop layout`), replacing the old full-width caption row.
- **Settings entry:** A new **Reading appearance** row (theme + text size) in Settings → Fandom Wiki, sharing the same preference and dialog as the reader.
