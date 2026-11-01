# Kitsugi İkon Seti (3B, saydam zeminli)

Bu belge, uygulamadaki **ayar satırları / sekmeler / gezinme çubuğu** ikonlarının
nereden geldiğini, lisansını ve nasıl yeniden üretileceğini anlatır.

## Neden değişti?

Önceki durumda her satır tek renkli bir Material vektör ikonu kullanıyordu ve ikon,
satır rengiyle doldurulmuş **42 dp'lik bir daire** içine, 20 dp boyutunda çiziliyordu.
Sonuç: küçük ekranlarda ikonlar "◯", "•", "✓" gibi anlamsız lekelere dönüşüyor,
satırın ne yaptığını anlatmıyordu.

Yeni yaklaşım:

- **Arka plan yok.** Renkli daire zemin kaldırıldı; ikon doğrudan kart zemini üzerinde.
- **3B + renkli + saydam.** Her ikon 3B render edilmiş, saydam zeminli bir görsel.
- **İşlev odaklı.** İkon, satırın işlevini doğrudan anlatır (bağlantı = zincir,
  eklentiler = yapboz, depolama = süpürge, indirmeler = gelen tepsisi, ...).
- **Optik normalizasyon.** Tüm görseller saydam kenarlarından kırpılıp aynı kutuya
  (%92 doldurma, 192×192 tuval) oturtulur; böylece satır satır boyut zıplaması olmaz.
- **Koyu tema dengesi.** Koyu temada kaybolan birkaç varlık (profil büstü, projektör,
  film kareleri) hafifçe parlatılarak optik olarak eşitlenir.

## Kaynak ve lisans

- Set: **Microsoft Fluent Emoji — 3D** stili
- Depo: https://github.com/microsoft/fluentui-emoji
- Lisans: **MIT** — Copyright (c) Microsoft Corporation
  (metin: `third_party/fluentui-emoji_LICENSE.txt`)
- Kullanım: ticari + kişisel, atıf zorunlu değil (MIT koşulları geçerli).
- Yalnızca 3D render PNG'leri alınır; başka hiçbir varlık kopyalanmaz.

> Not: Emoji seti olmasına rağmen bu görseller "emoji" olarak değil, **uygulama ikonu**
> olarak kullanılıyor. Semantiği net olduğu ve tek bir sanat yönetimiyle üretildiği
> için (aynı ışık, aynı yumuşak gölge, aynı kalınlık) set içi tutarlılık yüksek.

## Dosya yapısı

```
app/src/main/res/drawable-nodpi/ic_kitsugi_*.png   -> uygulamaya gömülen 192×192 PNG'ler
app/src/main/java/com/kitsugi/animelist/ui/icons/KitsugiIconAssets.kt
                                                  -> KitsugiArt enum'u, otomatik eşleme, çizim
tools/iconpack/fetch_fluent3d.py                  -> varlıkları GitHub API'den indirir
tools/iconpack/normalize.js                       -> kırpar, ölçekler, drawable-nodpi'ye yazar
tools/iconpack/mock_previews.js                   -> tasarım önizlemesi (PNG) üretir
third_party/fluentui-emoji_LICENSE.txt            -> MIT lisans metni
docs/IKON_SETI.md                                 -> bu belge
```

`tools/iconpack/raw/` ve `tools/iconpack/preview/` depoya **girmez** (ara çıktı).

## Önizleme

Tüm setin etiketli görseli: **`docs/ikon-seti-onizleme.png`**
(`node tools/iconpack/sheet.js` ile yeniden üretilir).

## Eşleme tablosu (satır → ikon)

| Satır / sekme | Anahtar | Fluent varlığı |
|---|---|---|
| Kitsugi Hesabı | `account` | Identification card |
| Hesap Bağlantıları | `connections` | Link |
| Görünüm & Tercihler | `palette` | Artist palette |
| Oynatıcı | `player` | Film projector |
| Eklentiler & Akış Kaynakları | `extensions` | Puzzle piece |
| Harici Entegrasyonlar | `integrations` | Globe with meridians |
| Veri ve Yedekleme | `database` | Floppy disk |
| Depolama | `storage` | Broom |
| İndirme Ayarları | `tune` | Control knobs |
| İndirmeler | `download` | Inbox tray |
| İzleme Geçmişi | `history` | Stopwatch |
| Hakkında | `info` | Information |
| Geri Bildirim | `feedback` | Megaphone |
| Platform Hata Kodları | `warning` | Warning |
| Dahili oynatıcı sekmesi | `play` | Play button |
| Hareketler | `gestures` | Hand with fingers splayed |
| Kod çözücü | `decoder` | Film frames |
| Altyazılar | `subtitles` | Left speech bubble |
| Ses | `audio` | Speaker high volume |
| Özel butonlar | `buttons` | Video game |
| Kod düzenleyici | `code` | Laptop |
| Gelişmiş | `advanced` | Gear |
| Keşfet (nav) | `nav_explore` | Compass |
| Listem (nav) | `nav_list` | Bookmark tabs |
| Arama (nav) | `nav_search` | Magnifying glass tilted left |
| Profil (nav) | `nav_profile` | Bust in silhouette |
| Ayarlar (nav) | `nav_settings` | Gear (`advanced` ile aynı görsel) |
| Eşitleme satırları | `sync` | Repeat button |
| Ağ / DNS satırları | `network` | Satellite antenna |
| Hız / motor satırları | `speed` | Rocket |
| Zamanlayıcı satırları | `timer` | Hourglass not done |
| Parlaklık satırları | `sun` | Sun |
| "Sonraki" satırları | `next` | Next track button |
| TV / yayın satırları | `tv` | Television |
| Klavye satırları | `keyboard` | Keyboard |
| Yardım satırları | `help` | Red question mark |
| Not / başlık satırları | `memo` | Memo |
| Araç / onarım satırları | `wrench` | Wrench |

### Ek eşlemeler (liste/detay ekranları)

Bunlar aynı setin parçası; uygulamanın her yerinde aynı görsel dil kalsın diye eklendi:

| Material ikon | 3B karşılığı |
|---|---|
| Favorite / FavoriteBorder | kalp |
| ContentCopy / ContentPaste | pano |
| BarChart / DonutLarge / Insights | grafik |
| CheckCircle / DoneAll | onay |
| Pause / PauseCircle | duraklat |
| Lock / Security / VerifiedUser | kilit |
| Folder / FolderOpen / PhotoLibrary | klasör |
| Notifications* | zil |
| CalendarToday / DateRange / Event | takvim |
| AutoAwesome / NewReleases | parıltı |
| ThumbUp / Recommend | beğeni |
| Home / Dashboard | ev |
| PhoneAndroid / Tablet / Devices | mobil cihaz |
| LocationOn / Place | konum |
| FastRewind / Replay10 | geri sar |
| DarkMode / NightsStay | ay |
| Sync / Refresh / Repeat / RestartAlt | eşitleme |
| Edit / Notes / Description / Title | not kalemi |
| ChatBubble / Comment | altyazı balonu |
| Error / PriorityHigh | uyarı |
| CloudDownload | indirme |

**Bilinçli istisna:** geri oku, kapat (×), onay işareti, açılır-kapanır oklar (⌄ ^),
artı (+), filtre, "dışa aç" gibi **kontrol/gezinme mikro-ikonları** 3B sete dahil
edilmedi. Bunlar içerik logosu değil, arayüz işareti; 3B render burada gürültü
yaratıyor. İçerik/logolar tarafında eşleşme oranı **~%90**; kalanlar yalnızca
tek tük kullanılan özel ikonlar.

## Eşleme nasıl çalışır?

`KitsugiArt.fromVector(icon)` Material `ImageVector`'ün adına bakar
(`"Rounded.PlayCircle"` → `"PlayCircle"`) ve enum'daki `vectorNames` listesinde arar.
Böylece 150+ çağrı yerini tek tek değiştirmek gerekmez.

- Eşleşme varsa → 3B görsel çizilir (saydam zemin, 30 dp).
- Eşleşme yoksa → satır rengiyle boyanmış tek renkli glif çizilir (yine **arka plansız**).
- Belirli bir satırda otomatik eşleme yanlış sonuç veriyorsa çağrı yerinde
  `art = KitsugiArt.X` parametresi ile açıkça belirtilir
  (ör. "Hesap Bağlantıları" satırı `Person` ikonunu kullanır → `art = KitsugiArt.Connections`).

## Yeni ikon eklemek

0. (İlk kez) `cd tools/iconpack && npm install sharp`
1. `tools/iconpack/fetch_fluent3d.py` içindeki `MANIFEST` sözlüğüne
   `"yeni_anahtar": "Fluent varlık adı"` satırı ekleyin.
2. `python3 tools/iconpack/fetch_fluent3d.py`
3. `node tools/iconpack/normalize.js`
4. `KitsugiIconAssets.kt` içine enum satırını ve gerekiyorsa `vectorNames` listesini ekleyin.
5. İsterseniz `node tools/iconpack/mock_previews.js` ile önizleme üretin.

> `fetch_fluent3d.py` GitHub Contents API'sini kullanır; `raw.githubusercontent.com`
> engelli ağlarda da çalışsın diye `Accept: application/vnd.github.raw` başlığıyla
> `api.github.com` üzerinden indirir.
