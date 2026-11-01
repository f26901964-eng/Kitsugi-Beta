# Kitsugi İkon Seti (3B · saydam zemin · arka plansız)

Bu belge uygulamadaki **ayar satırı / sekme / gezinme çubuğu** ikonlarının kaynağını,
lisansını, eşleme tablosunu ve sıfırdan nasıl yeniden üretileceğini anlatır.

## Neden bu set?

Önceki durumda her satır tek renkli bir Material vektör ikonu kullanıyordu ve ikon,
satır rengiyle doldurulmuş **42 dp'lik yuvarlak bir zemin** üzerine 20 dp boyutunda
çiziliyordu. Küçük ekranlarda bunlar "◯", "•", "✓" gibi anlamsız lekelere dönüşüyor,
satırın ne yaptığını anlatmıyordu. Ara adımda kullanılan emoji seti de "premium"
durmayıp uygulamanın koyu arayüzünden kopuyordu.

Yeni yaklaşımın kuralları:

- **Arka plan yok.** Renkli daire / plaka tamamen kaldırıldı; ikon doğrudan kart
  zemini üzerinde durur, arkası saydamdır.
- **3B + cam görünüm.** Tüm set tek bir sanat yönetimiyle üretilmiş 3B render'lardır:
  cam yüzey, mor → pembe → turuncu degrade, yumuşak iç ışık. Kitsugi'nin koyu teması
  (`#080A10`) ve turuncu vurgusu (`#FB923C`) ile aynı ailedendir.
- **İşlev odaklı.** İkon satırın işlevini doğrudan anlatır (bağlantı = zincir,
  eklenti = yapboz, depolama = çöp kutusu, indirme = bavul, geçmiş = saat, ...).
- **Tek aile, tek ölçek.** Tüm görseller saydam kenarlarından kırpılır, hafif gölge
  kalıntıları temizlenir, alan tabanlı optik dengeyle aynı kutuya (%92 doldurma,
  192×192 tuval) oturtulur. Böylece satır satır boyut zıplaması olmaz.
- **Aynı set her yerde.** Ayarlar satırları, oynatıcı ayarları sekmeleri, alt gezinme
  çubuğu, liste/detay eylemleri ve diyaloglar aynı varlıkları paylaşır — ekranlar
  arasında üslup farkı yoktur.

## Kaynak ve lisans

- Set: **3Dicons** — https://3dicons.co (Vijay Verma)
- Lisans: **CC0 1.0 Universal** (kamu malı; atıf gerekmez, ticari kullanım serbest)
  — tam metin: `third_party/3dicons_LICENSE.txt`
- Render'ların indirildiği ayna depo: https://github.com/Vyshnav2255/notion-icons-3d
  (Apache-2.0; atıf notu: `third_party/3dicons_NOTICE.txt`)
- Stil: `front/gradient` (ön açı, cam degrade). Denenip **reddedilen** stiller:
  `front/premium` (metallik, renksiz), `front/color` (mat, cansız), `iso/premium`.
- Yalnızca PNG render'ları alınır; başka hiçbir varlık kopyalanmaz.

## Dosya yapısı

```
app/src/main/res/drawable-nodpi/ic_kitsugi_*.png   -> uygulamaya gömülen 192×192 PNG'ler (50 adet)
app/src/main/java/com/kitsugi/animelist/ui/icons/KitsugiIconAssets.kt
                                                  -> KitsugiArt enum'u, Material adı eşlemesi, çizim
tools/iconpack/fetch_3dicons.py                   -> varlıkları GitHub Contents API'den indirir
tools/iconpack/raw3d/                             -> indirilen ham PNG'ler (depoya girmez)
tools/iconpack/normalize.js                       -> kırpar, dengeler, drawable-nodpi'ye yazar
tools/iconpack/icons.map.js                       -> mantıksal anahtar -> 3dicons adı + Türkçe etiket
tools/iconpack/mock_previews.js                   -> ekran önizlemeleri (PNG) üretir
tools/iconpack/sheet.js                           -> tüm setin etiketli sayfası (docs/ikon-seti-onizleme.png)
third_party/3dicons_LICENSE.txt                   -> CC0 1.0 lisans metni
third_party/3dicons_NOTICE.txt                    -> ayna depo / Apache-2.0 atıf notu
docs/IKON_SETI.md                                 -> bu belge
```

Varlık adı = 3dicons kaynak adı, tireler alt çizgiye çevrilir
(`video-camera` → `ic_kitsugi_video_camera.png`).

## Üretim

```bash
cd tools/iconpack
npm install          # sharp
npm run fetch        # 50 PNG -> raw3d/
npm run normalize    # 50 ikon -> app/src/main/res/drawable-nodpi (192×192, saydam)
npm run preview      # ekran önizleme PNG'leri
npm run sheet        # docs/ikon-seti-onizleme.png
```

Yeni bir ikon eklemek için:

1. `fetch_3dicons.py` içindeki `NAMES` listesine 3dicons adını ekleyin. Adlar
   `https://api.github.com/repos/Vyshnav2255/notion-icons-3d/contents/icons/front/gradient`
   listesinden alınır; sette `calender`, `sheild`, `zoom`, `picture` gibi yazımlar
   orijinalindedir.
2. `icons.map.js` içine `KEYMAP` + `LABELS` satırı ekleyin.
3. `npm run fetch && npm run normalize`, ardından `KitsugiIconAssets.kt` içine enum
   girdisi + Material adı eşlemesi ekleyin.

> `raw.githubusercontent.com` bu geliştirme ortamından kapalıdır; indirme, Contents
> API listesi + `Accept: application/vnd.github.raw` başlığı ile yapılır.

## Eşleme tablosu (satır → 3dicons varlığı)

| Anahtar | Kullanıldığı yer | 3dicons |
|---|---|---|
| `account` | Kitsugi Hesabı | `card` |
| `connections` | Hesap Bağlantıları | `link` |
| `palette` | Görünüm & Tercihler | `colour-palette` |
| `player` | Oynatıcı | `video-camera` |
| `play` | Oynat | `play` |
| `pause` | Duraklat | `pause` |
| `next` | Sonraki | `next` |
| `rewind` | Geri sar | `back` |
| `extensions` | Eklentiler & Kaynaklar | `puzzle` |
| `integrations` | Harici Entegrasyonlar | `sphere` |
| `network` | Ağ / DNS | `wifi` |
| `sync` | Eşitleme | `wifi` |
| `database` | Veri ve Yedekleme | `locker` |
| `storage` | Depolama | `trash-can` |
| `tune` | İndirme Ayarları | `setting` |
| `download` | İndirmeler | `travel` |
| `history` | İzleme Geçmişi | `clock` |
| `timer` | Zamanlayıcı | `clock` |
| `info` | Hakkında | `bulb` |
| `feedback` | Geri Bildirim | `megaphone` |
| `warning` | Platform Hata Kodları | `sheild` |
| `help` | Yardım | `chat` |
| `memo` | Not / Başlık | `pencil` |
| `gestures` | Hareketler | `magic-trick` |
| `decoder` | Kod çözücü | `tool` |
| `subtitles` | Altyazılar | `chat-text` |
| `audio` | Ses | `headphone` |
| `buttons` | Özel Butonlar | `toggle` |
| `code` | Kod Düzenleyici | `computer` |
| `advanced` | Gelişmiş | `lab` |
| `speed` | Hız | `rocket` |
| `sun` | Aydınlık tema | `sun` |
| `moon` | Karanlık tema | `moon` |
| `tv` | TV / Yayın | `computer` |
| `key` | Şifre / Anahtar | `key` |
| `star` | Puan / Yıldız | `star` |
| `image` | Görsel | `picture` |
| `heart` | Favori | `heart` |
| `clipboard` | Kopyala | `copy` |
| `chart` | İstatistik | `chart` |
| `check_circle` | Tamamlandı | `tick` |
| `lock` | Güvenlik | `lock` |
| `folder` | Klasör | `folder` |
| `bell` | Bildirim | `bell` |
| `calendar` | Takvim | `calender` |
| `sparkles` | Yenilik | `flash` |
| `thumbup` | Beğeni | `thumb-up` |
| `mobile` | Mobil cihaz | `mobile` |
| `pin` | Konum | `pin` |
| `nav_explore` | Keşfet (gezinme) | `explorer` |
| `nav_list` | Listem (gezinme) | `bookmark` |
| `nav_search` | Arama (gezinme) | `zoom` |
| `nav_profile` | Profil (gezinme) | `boy` |
| `nav_settings` | Ayarlar (gezinme) | `setting` |

## Çizim kuralları

| Bağlam | 3B ikon boyutu | Not |
|---|---|---|
| Ayar satırı | 30 dp (32 dp slot) | Metin hizası sabit kalsın diye slot hep aynı |
| Sekme şeridi | 21 dp | Seçili sekmede vurgu rengi + alt etiket |
| Alt gezinme çubuğu | 21 dp (seçili 24 dp) | Seçili değilken %70 alfa |
| Liste / detay eylemi | 24–26 dp | Metin düğmelerinde glif boyutu |
| Satır ayırıcı girintisi | 62 dp | İkon + boşluk hizası |

Eşleşme bulunamazsa `KitsugiRowIcon` **arka plansız**, satır rengiyle boyanmış tek
renkli glife düşer. Close, ArrowBack, Check, ExpandMore, Chevron*, Add, FilterList,
OpenInNew gibi kontrol mikro-ikonları bilerek 2B çizgi olarak kalır; onlar "eylem"
ikonudur, "konu" ikonu değil.

## Önizleme

- Tüm set (etiketli): **`docs/ikon-seti-onizleme.png`** (`npm run sheet`)
- Ekran mock'ları: `npm run preview` → `onizleme_ayarlar.png`, `onizleme_oynatici.png`

## Teknik notlar

- Varlıklar `drawable-nodpi` içinde: DPI ölçeklemesi yok, tek kopya → APK şişmez
  (50 ikon, toplam ~390 KB).
- `KitsugiArt.fromVector()` Material ikon adından otomatik eşleme yapar; 150+ çağrı
  yerini tek tek değiştirmek gerekmez. Yanlış eşleşme riski olan yerlerde
  `art = KitsugiArt.X` ile açıkça belirtilir.
- Aynı varlık birden fazla anahtar tarafından paylaşılabilir (ör. `wifi` → Ağ +
  Eşitleme, `clock` → Geçmiş + Zamanlayıcı, `computer` → Kod + TV).
