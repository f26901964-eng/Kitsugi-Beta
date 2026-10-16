# PLAN / TASK — Wiki Açılır Sayfalarında Eksiksiz Otomatik Çeviri + Konum Koruması

**Tarih:** 2026-10-16
**Dal:** `arena/ef4bb12a-kitsugi-beta`
**Kapsam:** Fandom + Vikipedi uygulama içi wiki okuyucusu ("açılır wiki sayfaları")

## Kullanıcı sözleşmesi (birebir)

> "Mavi tıklanır bağlantılar haricindekiler konumları korunarak otomatik çeviri; Google
> Translate (otomatik çeviri) açıksa bu wiki sayfaları özel olarak çeviriye gidip gelmeli ve
> konumları da aynen korunmalı. Bu wiki sayfalarında çok fazla mavi tıklanır, çevrilmemesi
> gereken bağlantı var; geri kalan beyaz metinler seçili dile çevrilmeli."

Kabul kriterleri:

1. Otomatik çeviri açıkken okuyucu sayfası **bütün olarak** seçili dilde görünür:
   başlık açıklaması + İçindekiler satırları + giriş bölümü + açılan her bölüm.
2. Mavi tıklanır bağlantılar (sayfa adları, kart başlıkları, çipler, satır içi bağlantı
   etiketleri) **hiç çevrilmez**; dokunma hedefleri ve konumları değişmez.
3. Görseller, kartlar, madde işaretleri ve blok sırası birebir korunur; hiçbir görsel
   metnin altına yığılmaz.
4. Sayfa tek dokunuşla Orijinal'e döner, tek dokunuşla (ağ isteği olmadan) Çeviri'ye
   geri gelir.
5. Geçiş sırasında **okuma konumu** korunur: kullanıcı aynı paragrafa bakar.
6. Hedef dille aynı dildeki kaynak (ör. Vikipedi TR + Türkçe hedef) gereksiz çeviri
   isteği üretmez.

## Kök nedenler (uygulamadan önce tespit edilenler)

1. **Giriş bölümü hiç çevrilmiyordu.** `ReaderLeadCard`, `ReaderSectionContent`'e
   `translations` haritasını geçmiyordu. Bölüm çevirisi blok blok (çocuk anahtarlara)
   yazıldığı için yapı korumalı çeviri görünmüyor, bölüm anahtarındaki `COMPLETE`
   işareti de düz metin yedeğinden ayıklandığı için **orijinal metin** kalıyordu; buna
   rağmen "Orijinal" düğmesi görünüyordu (düğme çalışıyor, metin değişmiyordu).
2. **İçindekiler karma dildi.** Bölüm başlıkları yalnızca sözlükten yerelleştiriliyordu
   (`localizedSectionTitle`); sözlükte olmayan her başlık (ör. "Powers and Abilities",
   "Critical response") İngilizce kalıyordu.
3. **Konum korunmuyordu.** Orijinal/Çeviri geçişinde çeviri farklı satır sayısı ürettiği
   için eski piksel kaydırma değeri başka bir paragrafa denk geliyordu.
4. **Görsel altı açıklamaları (figcaption) çevrilmiyordu**; beyaz metin olmalarına rağmen
   çeviri akışının dışındaydı.
5. **Karakter sekmesinde hedef dil kontrolü yoktu** (yapım sekmesinde vardı): aynı dildeki
   kaynaklar boşuna çeviriye gidiyordu.

## Görevler

- [x] 1. `ReaderLeadCard` → `ReaderSectionContent(translations = state.translations)`.
- [x] 2. İçindekiler satırları için `FandomReaderTranslationKeys.contents()` anahtarı
      (`reader-toc:<wiki>:<başlık>`) ve satır başına çocuk anahtar.
- [x] 3. `translateReaderContents()` (her iki VM): başlıklar TEK istekte, `⟪§⟫` satır
      jetonuyla; satır sayısı kanıtlanmazsa satır satır yedek — hiçbir başlık kaymaz.
- [x] 4. Otomatik çeviri açıkken sayfa açılışında İçindekiler de çevrilir; ayar/hedef dil
      değişiminde `retranslateLoadedTexts` içinde tazelenir.
- [x] 5. `FandomWikiReader` İçindekiler satırları: bölüm başlığının kendi çevirisi →
      İçindekiler toplu çevirisi → sözlük sırasıyla başlık çözümü.
- [x] 6. Sayfa geneli Orijinal/Çeviri şeridi (`ReaderTranslationBar`) + VM'lerde
      `setReaderOriginal(showOriginal)`; "Orijinal" çeviriyi SİLMEZ.
- [x] 7. Okuma konumu çapası (`WikiReaderLayoutAnchor.kt`): görünümün üstünü içeren blok
      ölçülür, geçişten sonra aynı bloğun okuma oranı geri kurulur.
- [x] 8. Görsel altı yazıları (`caption`) yapı korunarak çevrilir; URL/dosya adı ve
      görselin konumu değişmez.
- [x] 9. Karakter VM'ine hedef dil kaynağı kontrolü (yapım sekmesiyle parite).
- [x] 10. Testler: `WikiReaderLayoutAnchorTest`, `WikiTranslationProtectionTest`
      (görsel alt yazısı + okuyucu anahtarları).

## Değişen / eklenen dosyalar

1. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomWikiReader.kt`
2. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomReaderBrowser.kt`
3. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/WikiTranslationProtection.kt`
4. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/WikiReaderLayoutAnchor.kt` (yeni)
5. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomMediaWikiViewModel.kt`
6. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomCharacterWikiViewModel.kt`
7. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/MediaWikiTabContent.kt`
8. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/CharacterWikiTabSlot.kt`
9. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/CharacterDetailPage.kt`
10. `app/src/main/res/values/strings.xml`, `app/src/main/res/values-en/strings.xml`
11. `app/src/test/java/com/kitsugi/animelist/ui/screens/detail/wiki/WikiReaderLayoutAnchorTest.kt` (yeni)
12. `app/src/test/java/com/kitsugi/animelist/ui/screens/detail/wiki/WikiTranslationProtectionTest.kt`
13. Rapor: `WIKI_OKUYUCU_TAM_CEVIRI_VE_KONUM_RAPORU_2026-10-16.md`

## Doğrulama

- `git diff --check` temiz.
- TR/EN string paritesi: 7603 = 7603, fark kümesi boş; her iki dosya XML olarak geçerli.
- Okuyucu/sekme dosyalarındaki tüm `R.string.*` başvuruları iki dilde de mevcut.
- Arena ortamında **JVM yok** (java/javac/kotlinc bulunamadı), bu yüzden derleme/test burada
  değil **CI'da** doğrulandı (`android-build.yml`, arena/** push tetikler):
  - `38950075976`: `compileFossDebugKotlin` 2 hata → `readerContentsTitle` `@Composable` yapıldı.
  - `38951424067`: ana + test kaynak kümesi derlendi; görsel bloğu testi yeni figcaption
    sözleşmesine göre güncellendi.
  - `38952367447`: ✅ wiki + `data.fandom.*` testleri yeşil. Kalan kırmızılar bu işle
    ilgisiz ve önceden kırıktı (`MalNotificationsClientTest`, `MalWebParserTest`).
- İş akışına `--tests 'com.kitsugi.animelist.ui.screens.detail.wiki.*'` eklendi; aksi hâlde
  bu testler derleniyor ama koşmuyordu.
