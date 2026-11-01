# PLAN & TASK — 3B İkon Seti, Tema ve Renk Uyumu

**Tarih:** 2026-11-01
**Dal:** `arena/50b23715-kitsugi-beta`
**Durum:** Tamamlandı (derleme doğrulaması hariç — bu ortamda JDK/Android SDK yok)

---

## 1. Görev (kullanıcı isteği)

1. Uygulamadaki ikonlar **gerçek, premium görünen 3B ikonlar** olsun.
2. **Arka plan plakası olmasın** (renkli daire/plaka yok).
3. Her satırın **işleviyle uyumlu** olsun, koyu Kitsugi temasına uysun.
4. İkonlar **uygulamanın her yerinde aynı set** olsun — "eşitsizlik olmasın".
5. Tıklanabilir/açılır tüm alanlarda eksik gedik kalmasın.
6. İkonlar **renk seçimi + tema + gradyan** ile **otomatik** uyumlu çalışsın.
7. Sonuçlar kullanıcıya **görsel olarak** gösterilsin.

---

## 2. Plan (adım adım)

| # | Adım | Durum |
|---|---|---|
| 1 | Aday setleri karşılaştır (Fluent 3D, 3dicons front/premium, front/color, front/gradient, iso) | ✅ |
| 2 | Stil seçimi: **3dicons `front/gradient`** (CC0, cam degrade, saydam zemin) | ✅ |
| 3 | 122 varlık envanteri + 50 mantıksal anahtara eşleme tablosu | ✅ |
| 4 | İndirme betiği (`fetch_3dicons.py`) — GitHub Contents API | ✅ |
| 5 | Normalize boru hattı (`normalize.js`): kırp → optik denge → alfa/gölge temizliği → 192×192 | ✅ |
| 6 | `KitsugiIconAssets.kt`: `KitsugiArt` enum (50 varlık, ~300 Material adı eşlemesi), `KitsugiArtIcon`, `KitsugiRowIcon` | ✅ |
| 7 | Ayar satırları, oynatıcı sekmeleri, alt gezinme çubuğu → 3B | ✅ |
| 8 | Ekranlarda doğrudan çizilen ikonları 3B'ye taşı (önce 88, sonra kalan **tümü**) | ✅ |
| 9 | Tema uyumu: koyu/AMOLED olduğu gibi, açık temada yumuşak gölge, seçili durumda vurgu hâlesi | ✅ |
| 10 | **Otomatik renk uyumu**: vurgu rengine (gradyan dâhil) göre ikon ton kaydırması | ✅ |
| 11 | Önizleme sayfaları (`sheet.js`, `mock_previews.js`, `theme_matrix.js`) + ekran görüntüleri | ✅ |
| 12 | Lisans/atıf: CC0 tam metin + ayna depo notu; Fluent MIT dosyasının kaldırılması | ✅ |
| 13 | `docs/IKON_SETI.md` belgesi (kaynak, eşleme tablosu, kurallar, kapsam) | ✅ |
| 13b | **Tam kapsam:** 799 `Icon(...)` çağrısının tamamı tek giriş noktasından 3B'ye bağlandı (`gradient/Icon` + `KitsugiIcon` alias) | ✅ |
| 14 | Gradle derlemesi / APK doğrulaması | ⛔ bu ortamda yapılamadı |

---

## 3. Teknik kararlar

- **Set:** 3dicons (Vijay Verma) — **CC0 1.0**; render'lar `Vyshnav2255/notion-icons-3d` aynasından (Apache-2.0).
- **Stil:** `front/gradient` — cam yüzey, mor→pembe→turuncu degrade, saydam zemin.
  Reddedilenler: `front/premium` (metallik, renksiz), `front/color` (mat/cansız), `iso/*`.
- **Normalizasyon:** trim → alan tabanlı optik denge (%92 kutu, en fazla 1.45× büyütme,
  tuval sınırı) → alfa tabanı 26 (gölge kalıntısı temizliği) → 192×192 PNG.
- **Kural:** 3B = "konu" ikonu (≥20 dp), 2B = "kontrol" glifi (kapat/geri/onay/chevron/
  filtre/⋮, 10–18 dp).
- **Tema uyumu:** koyu/AMOLED'de dokunulmaz; açık temada plaka değil radyal gölge;
  seçili sekmede vurgu renginden hâle (`accentGlow`).
- **Otomatik renk uyumu:** setin doğal tonu 303,7°; vurgu renginin tonu okunur, ikon
  yalnızca ton ekseninde döndürülür (`ColorFilter.colorMatrix(hueRotation)`), doygunluk
  ve parlaklık korunur. Gradyan vurguda iki rengin ortası; doygunluğu %12 altındaki
  (gri/beyaz) vurgularda kaydırma yapılmaz. Kapatma: `iconsFollowAccent = false`.

---

## 4. Değişen dosya grupları (121 dosya)

| Grup | Adet | Not |
|---|---|---|
| `app/src/main/res/drawable-nodpi/ic_kitsugi_*.png` | 50 | 192×192, saydam, toplam ~390 KB |
| `ui/icons/KitsugiIconAssets.kt` | 1 | Enum + eşleme + çizim + tema/ton mantığı |
| `ui/theme/Theme.kt` | 1 | `iconsFollowAccent` parametresi, ton kaydırmasının sağlanması |
| UI dosyaları (ekran/bileşen/diyalog) | 55 | İkonların 3B'ye taşınması, hâle, gölge |
| `tools/iconpack/*` | 6 | fetch / normalize / map / sheet / mock / theme_matrix |
| `third_party/*` | 2 | CC0 lisans metni + ayna depo notu |
| `docs/*` | 3 | `IKON_SETI.md`, iki önizleme PNG'si |

Tam liste: zip içindeki `DEGISEN_DOSYALAR/` klasörü ve `degisiklikler.patch` (main'e göre).

---

## 5. Commit'ler

| Commit | İçerik |
|---|---|
| `ee584c7` | 3dicons `front/gradient` setine geçiş (50 varlık, normalize, enum) |
| `7d98411` | Önizleme sayfası düzeltmeleri |
| `5ec2c09` | Ekranlardaki "konu" ikonlarının 3B'ye taşınması + tema/hâle uyumu |
| `e7d8b31` | Vurgu rengine göre **otomatik ton kaydırması** |
| `f1777c1` | Plan/task dokümanı |
| `f0dd916` | Tüm `Icon(...)` çağrılarının tek giriş noktasından 3B'ye bağlanması (799 çağrı, 0 kaçak) |

---

---

## 5b. Tur 3 — kontrol glifleri (mono 385 → 0)

Kullanıcı geri bildirimi: *"birçok alanın logosu eksik"* + *"tek bir ikon bile 3B
ailenin dışında kalmasın"*. Bu turda mono kalan **385 çağrı / 75 ad** kapatıldı.

| Adım | Yapılan | Sonuç |
|---|---|---|
| 1 | 3dicons aynasında kontrol işareti taraması | Yalnız `plus`, `tick`, `back`, `next`, `zoom`, `thumb-up/down` var |
| 2 | Setin paletini örnekleme (5 render, 6 dikey bant) | `#E9A57E → #CF6A86 → #B04F9B → #8A35AB → #7043B2` |
| 3 | `tools/iconpack/control_glyphs.js` yazıldı | 36 şekil + 10 yön varyantı + 6 harf |
| 4 | 46 glif 128×128 saydam PNG olarak üretildi | `ic_kitsugi_glyph_*.png`, 252 KB |
| 5 | 3dicons'tan `plus` ve `thumb_down` indirilip normalize edildi | `ic_kitsugi_plus.png`, `ic_kitsugi_thumb_down.png` |
| 6 | `KitsugiIconAssets.kt`: 48 yeni enum girdisi + `CONTROL_ALIASES` (164 ad) | `fromVector` önce kontrol glifine bakıyor |
| 7 | Kapsam denetimi yeniden koşuldu | **1533/1533 = %100, mono 0, kaçak 0** |
| 8 | Görseller üretildi | `ikon-kontrol-sayfasi/baglam/boyut.png` + güncel denetim |

Yeni dosyalar: `docs/IKON_KONTROL_GLIFLERI.md`, `tools/iconpack/control_glyphs.js`,
`tools/iconpack/control_previews.js`, 48 PNG.

---

## 5c. Tur 4 — 3B hacim + işlev eşleşmesi

Kullanıcı geri bildirimi: *"alakasız logoları işlevlerine uygun koymamışsın"* +
*"türettiğin logoların tamamı 3B olmalı, paint'ten hallice"*.

| Adım | Yapılan | Sonuç |
|---|---|---|
| 1 | Yeni çizim hattı `tools/iconpack/glyph3d.js` | temas gölgesi + extrude gövde + pah + parlama |
| 2 | 46 kontrol glifi yeniden render | artık hacimli, 128×128 |
| 3 | `tools/iconpack/object_glyphs.js` | 9 nesne glifi: sync, sliders, download, upload, timer, code, tv, tag, backup |
| 4 | 3dicons'tan 38 yeni render | belge, defter, posta, sohbet, hedef, bayrak, kupa, ateş, hediye, hesap makinesi, makas, boya, fırça, damlalık, mikrofon, müzik, #, metin, küp, cüzdan, pil, kalp-bildirim, yeni/favori klasör, konum, telefon, spor, madalya, şemsiye, daire, çanta, satranç, para, bardak… |
| 5 | Eşleme tek tabloya indirildi (`NAME_MAP`) | 428 satır / 268 ad, çakışma 0 |
| 6 | Semantik düzeltmeler | Anime kartı → video, Manga → defter, arabellek satırları → kaydırıcı, eşitleme → sync, indirme → indir, TV → TV, kod → kod |
| 7 | Yeni denetim görseli | `docs/ikon-islev-haritasi.png` (128 varlık ↔ işlev) |
| 8 | Kapsam yeniden ölçüldü | **1533/1533 = %100, mono 0** |

---

## 5d. Tur 5 — malzeme kalitesi (bulanıklık düzeltmesi)

Kullanıcı geri bildirimi: *"ürettiğin 3B sözde logolar bokum gibi duruyor, bulanık"*.

| Kök neden | Düzeltme |
|---|---|
| SVG 24×24 px'e render edilip 176 px'e büyütülüyordu | `g3d.svg(spec, 1024)` — 1024 px render + LANCZOS3 küçültme |
| 12 yarı saydam ekstrüzyon katmanı kenarları yumuşatıyordu | 2 **opak** kaydırma katmanı → keskin siluet |
| Geniş beyaz kontur "neon tüp" etkisi veriyordu | spekülar kontur kaldırıldı; yerine şekle kırpılmış radyal ışık |
| Palet kuantizasyonu gradyanda bant/mush üretiyordu | saydam RGBA PNG (palet yok) |
| Harf glifleri konturla çiziliyordu (içi boş) | dolgu ile çiziliyor |

Sonuç: 46 glif + 9 nesne glifi 3dicons render'larıyla aynı malzeme dilinde
(`docs/ikon-malzeme-karsilastirma.png`). Toplam glif boyutu 324 KB.

---

## 5e. Tur 6 — gerçek 3B render hattı (SDF)

Kullanıcı geri bildirimi: *"bunlar orijinal 3B logolara benzemiyor"*.

| Önce | Şimdi |
|---|---|
| SVG gradyan + 2 kaydırılmış kopya | **SDF + analitik yüzey**: gerçek hacim, normal'e göre gölgeleme |
| Tek linear gradyan | Lambert + iki loblu spekülar + fresnel + gökyüzü + geniş parlama |
| Düz siluet | `0.62` yarıçaplı yuvarlatılmış kenar (fillet) |
| Gölge: bulanık leke | ışığın tersine kaydırılmış temas gölgesi |
| Ayrıntılar kayboluyordu | render sonrası oyma katmanı (kamera merceği, tuşlar, mumlar…) |

Yeni dosya: `tools/iconpack/sdf3d.js` (kesin Öklid mesafe dönüşümü + malzeme modeli).
46 kontrol glifi + 9 nesne glifi bu hatla yeniden üretildi.

---

## 6. Kalan işler / riskler

1. **Derleme doğrulaması yapılmadı** — ortamda JDK/Android SDK yok. Android Studio'da
   `./gradlew :app:assembleDebug` çalıştırılmalı; çıkan hata bu turda kapatılmalı.
2. İstenirse Ayarlar → Görünüm altına **"İkon tonu: Vurguya uyarla / Doğal palet"**
   anahtarı eklenebilir (şu an varsayılan açık, tek parametreyle kapatılıyor).
3. ~~İstenirse kalan küçük aksiyon glifleri 3B'ye çevrilebilir.~~ **Tamamlandı (tur 3):**
   kopyala/paylaş/sil/beğen dahil hiçbir ad 2B kalmadı; mono = 0.
4. Açık tema ve AMOLED cihazlarda göz kontrolü önerilir (gölge/hâle katmanı yeni).

### 5f. Malzeme çeşitliliği (v7)

Ölçüm: orijinal 3dicons render'ları tek tip değil — her nesne kendi malzemesiyle
modellenmiş (mat deri, seramik, emaye, cam, metal). v6'ya kadar 55 kendi
glifimizin hepsi aynı parlak plastik malzemeyle üretiliyordu; "her logonun kendi
mantığına uygun 3B stili yok" eleştirisi buradan geliyordu.

v7'de `sdf3d.js` içine 7 malzemeli bir sistem eklendi (`MATERIALS`) ve her glif
işlevine göre eşlendi:

- `clay` (mat seramik) 9 · `gloss` (parlak emaye) 10 · `gel` (jöle) 10 ·
  `paper` (ince kâğıt) 9 · `rubber` (mat kauçuk) 6 · `glass` (cam) 5 ·
  `obsidian` (koyu cam) 6 → toplam 55 glif.

Ayrıca `render_glyph.js` ile çok parçalı gövde desteği geldi: kamera (emaye gövde +
obsidyen mercek), klavye (kauçuk gövde + emaye tuşlar), kek (kil + mumlar), göz
(cam + obsidyen bebek), TV (kauçuk + obsidyen ekran), karusel, kaydırıcılar,
zamanlayıcı. Renk yerleşimi de glif başına `phase`/`span` ile kaydırıldı.

Kanıt: `docs/ikon-stil-haritasi.png` (malzemeye göre gruplu 55 glif),
`docs/ikon-malzeme-haritasi.md` (glif → işlev → malzeme → gerekçe tablosu),
`docs/ikon-malzeme-karsilastirma.png` (orijinal vs üretilen).
