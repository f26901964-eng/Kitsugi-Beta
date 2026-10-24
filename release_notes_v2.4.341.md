# Kitsugi Beta v2.4.341

## 🇹🇷 Türkçe

### 🎨 Wiki teması artık gerçekten çalışıyor
- **"Tema seçiyorum, hiçbir şey değişmiyor" sorunu kökten çözüldü.** Açık/Koyu tema artık yalnızca wikinin tema sınıfını değiştirmiyor; wiki renklerini topluluk CSS'inde sabitlediği durumlarda devreye giren **uyumlu bir renk katmanı** da uyguluyor.
- **Site teması (varsayılan) korunuyor:** hiçbir stil, sınıf veya öznitelik eklenmiyor.
- **Uygulamayla uyumlu palet:** "Uygulama temasını izle" uygulamanın gerçek zemin/metin rengini kullanır; zorlanan açık/koyu temada ise her zaman okunur nötr renkler seçilir.
- **Resim, video, oynatıcı ve galerilere dokunulmuyor:** filtre, tersleme veya renk eğrisi yok.
- **Geç gelen site render'ları durumu bozamıyor:** sayfa kendi sınıflarını sıfırlarsa tema kısa süre içinde yeniden uygulanır.
- **Canlı önizleme:** görünüm penceresinde radyo düğmesine dokunduğunuz anda tema uygulanır; "Kapat" derseniz eski tercihe döner.
- **Koyu temada kaydırma çubuğu ve form alanları da koyu.**

### 📐 Wiki sayfası açılırken kendini ekrana sığdırıyor
- **Elle yakınlaştırma tarihe karıştı.** Masaüstü düzeni artık cihaz genişliğine göre hesaplanan ölçekle açılıyor; ilk kare bile ekrana sığıyor.
- **Döndürme ve pencere değişiminde** sayfa yeniden sığdırılıyor.
- **Kendi yakınlaştırmanız ezilmiyor:** iki parmakla yakınlaştırdıktan sonra otomatik sığdırma durur.
- **Yeni "Sayfayı ekrana sığdır" eylemi** (sayfa araçları): istediğiniz an tek dokunuşla sıfırlar.

### 🔤 Büyütülen sayfa okunur kalıyor
- Sığdırma küçülttüğü için yazı da otomatik dengeleniyor: telefonda %100 tercih ekranda ~%225'e denk geliyor, tablette ~%112'ye.
- Pencerede **"Ekranda etkin boyut"** satırı gerçek değeri gösterir; kaydırıcıya ek olarak **A− / A+** düğmeleri eklendi.
- Sistem yazı ölçeği ve 300% motor tavanı korunuyor.

---

## 🇬🇧 English

### 🎨 Wiki themes finally work
- **Root cause fixed:** Light/Dark no longer just toggles the wiki theme class. When a wiki hard-codes its colours in community CSS, a **compatible colour layer** is applied on top, so the choice is always visible.
- **Site theme stays the default** and remains byte-for-byte untouched.
- **Palette matches the app:** "Follow app theme" uses the app's own background/text colour; forced light/dark always falls back to readable neutrals.
- **Images, videos, players and galleries are never filtered, inverted or recoloured.**
- **Late site renders cannot undo the choice:** the state is re-applied after the site resets classes or replaces `<body>`/`<head>`.
- **Live preview:** picking a mode in the appearance dialog applies it immediately; closing without applying reverts.
- **Dark theme also covers scrollbars and form controls.**

### 📐 The page fits the screen on open
- **No more manual pinch-to-fit.** Desktop layout opens at a scale derived from the device width; even the first frame fits.
- **Rotation and window changes** refit automatically.
- **Your own pinch is respected:** automatic fitting stops after you zoom.
- **New "Fit page to screen" action** in page tools resets it at any time.

### 🔤 Fitted pages stay readable
- Text zoom is compensated for the fit: a 100% preference lands near 225% on a phone and ~112% on a tablet.
- The dialog shows the **effective on-screen size**, plus new **A− / A+** buttons.
- System font scaling and the 300% engine ceiling are preserved.
