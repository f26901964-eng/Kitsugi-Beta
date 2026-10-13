# PLAN/TASK — Wiki makale içi mavi bağlantıların uygulama içinde açılması

Tarih: 2026-10-13 · Sürüm: **v2.4.283**
Kullanıcı bildirimi: Hunter × Hunter detayında Wiki sekmesinde bölüm metninin altındaki mavi yazılı butonlar (ekran görüntüsünde turuncu kutu: *Hunters, Hunter License, Hunter Exam, Nen, Phantom Troupe, Gon Freecss, Kite, Gi…*) uygulama içinde açılmalıydı, açılmıyordu.

## Kök neden

Ekran görüntüsündeki şerit, `CharacterWikiTabContent.SectionBody` içindeki `AccentBlue` renkli `TextButton` satırıdır (`content.linkedEntities` / `content.links`). Zincir:

```
MediaWikiTabContent (yapım Wiki sekmesi)
  └─ CharacterWikiTabContent(onOpenWikiPage = ???)
       └─ WikiSectionRow(onOpenLink = { onOpenWikiPage?.invoke(it) ?: openExternal(...) })
            └─ SectionBody(mavi TextButton'lar)
```

- Karakter/ekip sayfaları `onOpenWikiPage = { wikiViewModel.openReader(title) }` iletiyordu → uygulama içi ✔
- **Medya Wiki sekmesi (`MediaWikiTabContent.kt`) bu parametreyi hiç iletmiyordu** → varsayılan `null` → tıklama `openExternal()` ile tarayıcıya gidiyordu. v2.4.282'nin "sıfır tarayıcı" düzeltmesi ilgili-sayfa şeridi / rehber / aramayı bağlamış, makale bölüm içi bağlantıları atlamıştı.

Aynı turda bulunan komşu kusurlar:

| # | Kusur | Etki |
|---|---|---|
| 1 | `MediaWikiTabContent`, `CharacterWikiTabContent`'e `onOpenWikiPage` iletmiyor | Medya makalesi bölüm altı mavi linkler + makale alt bilgisi tarayıcıya gidiyor (bildirilen hata) |
| 2 | `WikiRelatedEntitiesCard` hiçbir okuyucu geri çağrısı almıyor; "sayfayı aç" düğmesi ve görselsiz kart her zaman `openExternal` | Karakter sayfalarında bile ilişkili sayfa kartları tarayıcıya gidiyor |
| 3 | `CharacterRelatedStrip.onOpenPage = { onOpenWikiPage?.invoke(p) }` — geri çağrı yoksa tıklama sessizce yutuluyor | Ölü düğme (medya düzeltmesi sonrası pratikte kapanıyor, yine de sertleştirildi) |
| 4 | `ReaderState.Failed` hiçbir arayüzde tüketilmiyor: medya okuyucuda sonsuz spinner, karakter/ekipte okuyucu hiç açılmıyor (`if (readerReady != null)`) | Yeni bağlanan linklerden biri yüklenemezse kullanıcı "tıkladım, olmadı" görür |
| 5 | Bölüm altı `linkedEntities` tıklaması yalnızca başlığı iletiyor, `FandomWikiLink.wikiId` kayboluyor | Farklı wiki'ye işaret eden bağlantı yanlış wiki'de aranabilir |

## Yapılan değişiklikler

| Dosya | Değişiklik |
|---|---|
| `ui/screens/detail/wiki/MediaWikiTabContent.kt` | `CharacterWikiTabContent` çağrısına `onOpenWikiPage` (boşsa ana sayfa okuyucusu) + `onOpenWikiLink` eklendi; okuyucu sayfasına `failedTitle`/`onRetryFailed` iletildi |
| `ui/screens/detail/wiki/CharacterWikiTabContent.kt` | Yeni `onOpenWikiLink: ((FandomWikiLink) -> Unit)?` parametresi; `WikiSectionRow` → `SectionBody` zincirinden geçirildi, mavi şerit wiki kimliğini koruyarak açıyor. `WikiRelatedEntitiesCard` artık `onOpenWikiLink`/`onOpenWikiPage` alıyor ("sayfayı aç" + görselsiz kart uygulama içi; ikisi de yoksa tarayıcı son çare). İlgili şeritte ölü tıklama kaldırıldı |
| `ui/screens/detail/wiki/CharacterWikiTabSlot.kt` | Durumsuz yuvaya `onOpenWikiLink` iletimi; VM'li yuva `{ openReader(pageTitle, wikiIdOverride = link.wikiId) }` veriyor. Okuyucu artık Loading/Failed'da da çiziliyor (hata + tekrar dene) |
| `ui/screens/detail/CharacterDetailPage.kt` | İki Wiki sekmesi çağrısına (dikey + yatay düzen) `onOpenWikiLink`; okuyucu Loading/Failed'da da çiziliyor |
| `ui/screens/detail/wiki/FandomWikiReader.kt` | `failedTitle` + `onRetryFailed` parametreleri ve `ReaderFailed` kartı (mevcut `fandom_reader_failed` + `action_retry_label` metinleri, yeni metin yok) |
| `app/build.gradle.kts` | `2.4.282` → `2.4.283` |

Bilerek dışarıda bırakılan tarayıcı çıkışları (önceki tasarımla aynı): okuyucu üst çubuğundaki 🌐 düğmesi, `WikiItemSheet`'teki açıkça "tarayıcıda aç" etiketli (şu an erişilemez/ölü) düğme, uygulama içi okuyucu hiç bağlı değilken son çare `openExternal` yedekleri.

## Doğrulama

- Sandbox'ta JDK/Android SDK yok → derleme alınamadı (önceki raporlardaki gibi). Statik denetim yapıldı: parantez/süslü dengesi 5 dosyada da 0 fark, `onOpenWikiLink` zinciri tüm çağrı noktalarında (medya + 2× karakter düzeni + ekip VM yuvası) kapalı devre, kalan `openExternal` çağrıları tek tek sınıflandırıldı (yukarıda).
- Yeni string eklenmedi → TR/EN parite denetimi gerekmiyor.

## Cihazda test

1. Hunter × Hunter (veya herhangi bir yapım) → **Wiki** → bir bölümü aç → alttaki mavi şeritte *Nen*, *Phantom Troupe*, *Gon Freecss* → dokun: sayfa **uygulama içi okuyucuda** açılmalı, geri tuşu önceki wiki sayfasına dönmeli, tarayıcı açılmamalı.
2. Aynı şeritte var olmayan/yanlış bir başlığa denk gelinirse: sonsuz spinner yerine "Sayfa yüklenemedi · Tekrar dene".
3. Karakter detayı → Wiki → bölüm altı mavi linkler + "İlgili sayfalar" kartındaki *sayfayı aç*: uygulama içi okuyucu; farklı wiki'li bağlantıda doğru wiki.
4. Ekip/seslendiren detayı → Wiki → aynı kontroller (VM'li yuva).
5. Okuyucuda 🌐 simgesi hâlâ tarayıcıyı açıyor mu (bilerek bırakılan tek çıkış)?
