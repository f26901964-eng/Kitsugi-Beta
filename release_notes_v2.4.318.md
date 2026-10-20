# Kitsugi Beta v2.4.318

## 🇹🇷 Türkçe

### 🔵 Wiki Okuyucuda Mavi (Bağlantı) Metinlerin Otomatik Çevirisi

Bu sürüm, wiki açılır sayfalarında otomatik çevirinin **mavi/bağlantı metinlerini atlaması** yüzünden oluşan üç ayrı bozulmayı tek kökten çözer. Ayrıntılı analiz: `MAVI_BAGLANTI_OTOMATIK_CEVIRI_RAPORU_2026-10-21.md`.

**Şikâyet (ekran görüntüsüyle gelen):**
1. Cümlelerin içindeki mavi kelimeler (`stubs`, `images`, `Characters`, `Terminology` …) hiç çevrilmiyordu; cümle yarı İngilizce yarı Türkçe kalıyordu.
2. Mavi kelimeler çevrilmediği için parça parça çeviri **kelimeleri birbirine yapıştırıyordu**: “oluşturduk 678 articlesve yaklaşık olarak yüklenmiş 10,857 images …”.
3. Kart başlığı bir sayfaya bağlıysa hiç çevrilmiyordu; ana sayfa kartlarında “This wiki is dedicated to everything related to the series Date A Live, that anyone can edit!” gibi **tam bir İngilizce cümle** öylece kalıyordu.

**Çözüm — “yuvalı çeviri” (`WikiTranslationProtection.kt`):**

- **Mavi metin artık çevriliyor, konumu birebir korunuyor.** Her bağlantı etiketi çeviriye gönderilmeden önce `⟦L0⟧Characters⟦/0⟧` biçiminde bir yuvaya alınır. Google cümleyi **tek parça** olarak çevirir; etiket tam olarak eski yerinde, cümlenin içinde kalır.
- **Dokunma hedefi (URL) hiç değişmiyor ve hiç gönderilmiyor.** Çeviri yalnızca `label` alanına dokunur; `url` alanı blok verisinde olduğu gibi kalır. Mavi kelimeye dokununca gidilecek sayfa, çeviriden önce neyse aynıdır.
- **Çevrilmiş etiket, metinle birlikte saklanıyor (`…:l<indeks>` çocuk anahtarı).** Böylece çeviri haritası önbellekten/akıştan geri geldiğinde metin çevrilmiş etiketi, bağlantı listesi de aynı etiketi taşır; mavi alan tıklanabilirliğini kaybetmez.
- **Jeton bozulursa güvenli yedek.** Sağlayıcı bir yuvayı düşürür/bozarsa metin eski güvenli yoldan (parça parça, etiketler orijinal) çevrilir. Hiçbir koşulda yarım jeton, kaymış bağlantı veya cümlenin tamamını yutan bir mavi alan gösterilmez.
- **Eski önbellek koruması.** Çevrilmiş metin kendi bağlantılarını çözemiyorsa (etiket anahtarı olmayan eski kayıt) o yüzeyin çevirisi reddedilir; orijinal gösterilir.
- **Hız: istek sayısı arttı değil, azaldı.** Eskiden her bağlantı arası ayrı istek atılıyordu (bir paragrafta 5-8 istek). Şimdi cümle + etiketleri **tek istekte** gidiyor; 40 tekrarlı bir bağlantı listesi bile tek istek harcıyor.
- **Cümle görünümlü kart başlıkları çevriliyor.** `href` korunur; yalnızca kısa sayfa adları (“Read more”, “Kimi no Na wa. Soundtracks”) orijinal kalır.
- **Görsel altı yazıları** (figcaption) da aynı yuvalı akıştan geçer; açıklamadaki mavi kelimeler de yerinde çevrilir.

### 🧪 Birim Testleri
- `WikiTranslationProtectionTest`: mavi etiketlerin yerinde çevrilmesi, URL'lerin hiç değişmemesi, jeton bozulduğunda güvenli yedeğe düşülmesi, etiket anahtarlarının metinden önce akması, 40 tekrarlı bağlantıda tek istek, cümle görünümlü kart başlığının çevrilip kısa sayfa adının çevrilmemesi doğrulandı.

---

## 🇬🇧 English

### 🔵 Automatic Translation of Blue (Link) Text in the Wiki Reader

Fixes three separate breakages caused by auto-translation skipping **blue / linked text** inside wiki reader pages.

- **Link labels are now translated in place.** Each label is wrapped in a `⟦L0⟧Characters⟦/0⟧` slot before being sent, so the provider translates the whole sentence at once and the label stays exactly where it was.
- **Tap targets (URLs) never change and are never sent.** Only `label` is touched; `url` stays in the block data, so tapping a blue word opens exactly the same page as before.
- **Translated labels are persisted next to the text** (`…:l<index>` child keys) so text and link list always agree after a cache/stream round trip — the blue area never loses its click target.
- **Safe fallback when slots are damaged.** If the provider drops a slot marker, the block falls back to the old per-segment path with original labels. A half-stripped marker, a shifted link, or a link swallowing an entire sentence can never reach the UI.
- **Legacy cache guard.** A translated text that cannot resolve its own links is rejected and the original is rendered.
- **Fewer requests, not more.** One request per block (sentence + labels) instead of one per segment; even a paragraph with 40 repeated links costs a single request.
- **Sentence-like linked card titles are translated** (Fandom main-page cards) while `href` is preserved; short page names stay untouched.
