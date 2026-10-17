# Kitsugi Beta v2.4.301

## 🇹🇷 Türkçe

### 🖼️ Keşfet Vitrini Yeniden Tasarlandı (Nuvio Tarzı Yerleşim)

- **Kök Neden Düzeltildi:** Vitrin görseli `ContentScale.Fit` ile çok geniş bir kutuya sığdırılıyordu. Bu yüzden 16:9 bir görsel **yanlarda boş kutu** bırakıyor, 3:1 bir banner ise **ince bir şeride** düşüp gerisini bulanık olmayan düşük opaklıklı bir "hayalet kopya" ile dolduruyordu. Artık görsel **her zaman kutuyu tamamen kaplıyor** (`ContentScale.Crop`); kaynak ne olursa olsun vitrin aynı, tutarlı görünüyor.
- **Yeni Yerleşim Politikası (`HeroVitrinLayout`):** Dikey telefonda üstte oranı sabit bir **görsel şeridi**, altta başlık/logo, alt başlık ve meta satırları için **ayrılmış sabit yuva** var. Başlık ile logo artık görselin üstüne binmiyor. Yatay mod, tablet ve TV'de ise görsel bandın tamamını kaplıyor ve metin Nuvio TV'deki gibi gradyan scim'in üzerine bindiriliyor.
- **Bulanık Ortam Katmanı:** Keskin görselin altında kalan alan artık aynı görselin **bulanık** kopyasıyla doluyor. Boş kutu, yarım görsel veya bulanık olmayan ikinci kopya bir daha görünmüyor. Coil'in disk cache'i yalnızca URL'e göre çalıştığı için bu katman **ek internet tüketimi yapmıyor**.
- **Nuvio'dan Alınan Scrim'ler:** Nuvio Mobile'in dikey gradyanı ve Nuvio TV'nin sol yatay gradyanı uygulandı; durum çubuğu için üstte hafif bir koyulaştırma eklendi. Metin her kaynakta okunur kalıyor.
- **Ölçüler Görselden Bağımsız:** Bant yüksekliği, görsel şeridi ve metin yuvası yalnızca cihaz geometrisinden (ve yazı ölçeğinden) türetiliyor. Görselin oranı, yükleme durumu veya kaynak tamamlanması sayfayı kaydıramıyor; büyük yazı ölçeğinde bant gerektiği kadar büyüyor.
- **Yükleme İskeleti Eşitlendi:** Vitrin iskeleti artık gerçek vitrinle aynı yerleşimi kullanıyor; görsel geldiğinde sayfa zıplamıyor.
- **Dokunulmayanlar:** Otomatik geçiş, sayfa göstergeleri, logo yuvası, kaynak rozeti, TV kumanda odağı, yetişkin bulanıklığı ve kaynak kotaları aynen korundu.

---

## 🇬🇧 English

### 🖼️ Explore Showcase Redesigned (Nuvio-Style Layout)

- **Root Cause Fixed:** The showcase artwork was fitted (`ContentScale.Fit`) into a very wide box, so a 16:9 image left **empty bars on both sides** while a 3:1 banner collapsed into a **thin strip** backed by a low-opacity, unblurred "ghost copy". The artwork now **always fills its box** (`ContentScale.Crop`), so every source renders identically and consistently.
- **New Layout Policy (`HeroVitrinLayout`):** On portrait phones the artwork occupies a fixed-aspect **strip at the top**, while the title/logo, subtitle and meta rows live in a **dedicated fixed zone below**. The title and logo no longer overlap the artwork. In landscape, on tablets and on TV the artwork fills the whole band and the metadata is layered over a gradient scrim, exactly like Nuvio TV.
- **Blurred Ambient Layer:** The area beneath the sharp artwork is now filled with a **blurred** copy of the same image. Empty boxes, half images and unblurred duplicates are gone. Because Coil's disk cache is keyed by URL only, this layer adds **no extra network traffic**.
- **Nuvio-Derived Scrims:** Nuvio Mobile's vertical gradient and Nuvio TV's left-hand horizontal gradient are applied, plus a subtle top scrim for the status bar. Metadata stays readable on every source.
- **Image-Independent Geometry:** Band height, artwork strip and text zone are derived only from device geometry (and font scale). Artwork aspect, loading state or a late-arriving source can never shift the layout; with a large font scale the band grows instead of clipping.
- **Matching Loading Skeleton:** The showcase skeleton now uses the very same layout policy, so the screen does not jump when artwork arrives.
- **Unchanged:** Auto-advance, page indicators, the logo slot, the source pill, TV remote focus, adult-content blur and the per-source quotas all behave exactly as before.
