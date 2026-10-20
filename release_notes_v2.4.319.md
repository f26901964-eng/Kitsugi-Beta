# Kitsugi Beta v2.4.319

## 🇹🇷 Türkçe

### 🔵 Wiki Okuyucuda Mavi (Bağlantı) Metinlerin Otomatik Çevirisi

Bu sürüm, wiki açılır sayfalarında otomatik çevirinin **mavi/bağlantı metinlerini atlaması** yüzünden oluşan üç ayrı bozulmayı tek kökten çözer. Ayrıntılı analiz: `MAVI_BAGLANTI_OTOMATIK_CEVIRI_RAPORU_2026-10-21.md`.

- **Mavi metin artık çevriliyor, konumu birebir korunuyor:** Her bağlantı etiketi çeviriye gönderilmeden önce `⟦L0⟧Characters⟦/0⟧` biçiminde bir yuvaya alınır. Google cümleyi **tek parça** olarak çevirir; etiket tam olarak eski yerinde, cümlenin içinde kalır. Yarı İngilizce cümleler engellendi.
- **Dokunma hedefi (URL) hiç değişmiyor ve hiç gönderilmiyor:** Çeviri yalnızca `label` alanına dokunur; `url` alanı blok verisinde olduğu gibi kalır. Mavi kelimeye dokununca gidilecek sayfa, çeviriden önce neyse birebir aynıdır.
- **Kelimelerin birbirine yapışması çözüldü:** Eskiden cümle bağlantıların arasına parçalanıp ayrı ayrı çevrildiği için "678 articlesve" gibi yapışmalar oluşuyordu. Cümle bütünlüğü korunduğu için akıcı ve hatasız Türkçe metin elde edilir.
- **Çevrilmiş etiket metinle birlikte saklanıyor (`…:l<indeks>` çocuk anahtarı):** Böylece çeviri haritası önbellekten/akıştan geri geldiğinde metin çevrilmiş etiketi, bağlantı listesi de aynı etiketi taşır; mavi alan tıklanabilirliğini asla kaybetmez.
- **Jeton bozulursa güvenli yedek:** Sağlayıcı bir yuvayı düşürür veya bozarsa metin eski güvenli yoldan (parça parça, etiketler orijinal) çevrilir. Hiçbir koşulda yarım jeton, kaymış bağlantı veya cümlenin tamamını yutan bir mavi alan ekranda görünmez.
- **Eski önbellek koruması:** Çevrilmiş metin kendi bağlantılarını çözemiyorsa (etiket anahtarı olmayan eski kayıt) o yüzeyin çevirisi reddedilir; orijinal metin gösterilir.
- **Hız ve performans:** Eskiden her bağlantı arası ayrı istek atılıyordu (bir paragrafta 5-8 istek). Artık cümle ve tüm etiketler **tek istekte** gidiyor; 40 tekrarlı bir bağlantı listesi bile tek istek harcıyor.
- **Cümle görünümlü kart başlıkları:** Ana sayfa kartlarındaki cümle niteliğindeki başlıklar çevrilirken `href` korunur; kısa sayfa adları ise orijinal kalır.
- **Görsel altı yazıları (figcaption):** Açıklamalardaki mavi bağlantılar da aynı yuvalı akıştan geçer.

### 🧪 Birim Testleri
- `WikiTranslationProtectionTest`: Mavi etiketlerin yerinde çevrilmesi, URL'lerin hiç değişmemesi, jeton bozulduğunda güvenli yedeğe düşülmesi, etiket anahtarlarının metinden önce akması, 40 tekrarlı bağlantıda tek istek, cümle görünümlü kart başlığının çevrilip kısa sayfa adının çevrilmemesi doğrulandı (6 yeni test).

---

## 🇬🇧 English

### 🔵 Automatic Translation of Blue (Link) Text in the Wiki Reader

Fixes three separate breakages caused by auto-translation skipping **blue / linked text** inside wiki reader pages. Detailed report in `MAVI_BAGLANTI_OTOMATIK_CEVIRI_RAPORU_2026-10-21.md`.

- **Link labels are now translated in place:** Each label is wrapped in a `⟦L0⟧Characters⟦/0⟧` slot before being sent, so the provider translates the whole sentence at once and the label stays exactly where it was.
- **Tap targets (URLs) never change and are never sent:** Only `label` is touched; `url` stays in the block data, so tapping a blue word opens exactly the same page as before.
- **Resolved word agglutination:** Previously, sentences were split between links resulting in broken concatenations ("678 articlesand"); unified sentence translation ensures clean spacing and natural grammar.
- **Translated labels are persisted next to the text (`…:l<index>` child keys):** Text and link list always agree after a cache/stream round trip; the blue area never loses its click target.
- **Safe fallback when slots are damaged:** If the provider drops a slot marker, the block falls back to the old per-segment path with original labels. A half-stripped marker, a shifted link, or a link swallowing an entire sentence can never reach the UI.
- **Legacy cache guard:** A translated text that cannot resolve its own links is rejected and the original is rendered.
- **Fewer requests, not more:** One request per block (sentence + labels) instead of one per segment; even a paragraph with 40 repeated links costs a single request.
- **Sentence-like linked card titles are translated:** Fandom main-page card sentences are translated while `href` is preserved; short page titles stay untouched.
- **Figure captions (figcaption):** Blue link text in image captions is translated in place through the same slot pipeline.

### 🧪 Unit Tests
- `WikiTranslationProtectionTest`: Verified in-place label translation, invariant URLs, slot recovery fallback, pre-flight label key emissions, single request on 40 links, and sentence vs title card discrimination (6 new tests).
