# PLAN/TASK — Wiki Okuyucu: Yüzde-Kodlu Çip Başlıkları + Chrome/Brave Sekme Düzeni (v2.4.285)

**Tarih:** 2026-10-14
**Sürüm:** 2.4.284 → **2.4.285** (`app/build.gradle.kts`)
**Durum:** ✅ Uygulandı (birim testleri eklendi; Gradle, JDK/Android SDK olmayan ortamda çalıştırılamadı — aşağıdaki doğrulama adımını yerelde koşun)

---

## 1. Kullanıcı Şikâyetleri (ekran görüntüleriyle)

### Sorun A — `%C5%8D` gibi görünen çipler
Yoshihiro Togashi wiki sayfasında bölüm altı bağlantı çiplerinde şu başlıklar görünüyordu:
`Shinj%C5%8D, Yamagata`, `Weekly Sh%C5%8Dnen Jump`, `Tank%C5%8Dbon`, `Ten de Sh%C5%8Dwaru Cupid`.

### Sorun B — Tarayıcı/sekme mantığı Chrome gibi değil
- Sayfa içi bir bağlantıya tıklayınca sayfa **aynı sekmede üstüne gidiyordu**; kaynak sayfa kayboluyordu.
- Sekmeler yan yana, sağa doğru açılan, sayı arttıkça **daralıp** azaldıkça **genişleyen** Chrome/Brave düzeninde değildi.
- `+` butonu yalnızca mevcut sayfayı listenin **sonuna** kopyalıyordu.

---

## 2. Kök Nedenler

### A
- Çipler, bağlantının görünen metninden değil ham `href` değerinden üretiliyor:
  `FandomHtmlText.internalWikiLinks()` → `parseWikiLink()`.
- `parseWikiLink()` yalnızca `%20`, `%2F`, `%3A`, `%28`, `%29` kodlarını elle çözüyordu.
  ō karakterinin UTF-8 karşılığı `%C5%8D` çözülmediği için çiplere olduğu gibi yazılıyordu.
- ASCII başlıklar (`Shueisha`, `Fine-art`) kodlanacak karakter içermediği için doğru görünüyor, hatayı maskeliyordu.
- Aynı bozuk başlık tıklanınca tekrar kodlanıyor (`%25…`) ve hedef sayfa da bulunamıyordu.
- `wikiLinks()` yolu (`FandomUrls.parseArticleUrl`) zaten doğru çözüyordu; sorun yalnızca bu çiplerdeydi.

### B
- `openReaderLink()` her iki ViewModel'de de `navigate(pushHistory = true)` ile **aynı sekmede** geziniyordu.
- `openNewTab()` yeni sekmeyi şeridin **sonuna** ekliyordu.
- `ReaderTabStrip` sabit aralıklı (104–240 dp) haplar kullanıyordu; eşit genişlik paylaşımı, minimum genişlikte yatay kaydırma ve aktif sekmeyi görünür tutma yoktu.

---

## 3. Yapılan İşler

### A — Yüzde-kodu düzeltmesi
- [x] `FandomHtmlText.parseWikiLink()`: başlık artık tamamen UTF-8 yüzde-kodundan çözülüyor
      (`URLDecoder`, `FandomUrls.parseArticleUrl` ile aynı yaklaşım; `+`, `%2B` olarak korunur → `C++` bozulmaz).
- [x] Bozuk yüzde dizisi (`%ZZ`) çökme yaratmadan eski elle-çözme davranışına düşüyor; `?query` kısmı kırpılıyor.
- [x] Eski bozuk çipleri anında geçersiz kılmak için bölüm önbellek anahtarı `sec:v4` → `sec:v5`
      (`FandomRepository.loadSection`, 7 günlük TTL'yi beklemeye gerek yok).
- [x] Test: `FandomHtmlTextTest` — kodlanmış ō başlıkları (gerçek vaka), kodlu noktalama, `+` koruması, bozuk kaçış.

### B — Chrome/Brave sekme düzeni
- [x] `FandomReaderBrowserController.openInNewTab(location)`:
  - Bağlantı, aktif sekmenin **hemen sağına** yeni sekme olarak açılır ve aktif olur.
  - Sayfa zaten bir sekmede açıksa kopya açılmaz, o sekmeye geçilir (büyük/küçük harf ve `_`/boşluk duyarsız).
  - Sayfanın kendi bağlantısı no-op'tur.
  - Sekme yoksa ilk sekme yaratılır; `MAX_TABS (12)` doluysa `null` döner.
- [x] `openNewTab()` (`+` çoğaltma) da artık sona değil aktif sekmenin sağına ekliyor.
- [x] `closeOtherTabs(tabId)`: sekmeye uzun basınca diğerleri kapanır, basılan kalır/aktif olur.
- [x] Aktif sekmeyi kapatma: önce sağ komşu, yoksa sol komşu (mevcut davranış korundu/testlendi).
- [x] `FandomMediaWikiViewModel.openReaderLink()` + `FandomCharacterWikiViewModel.openReaderLink()`:
      yeni-sekme akışına bağlandı; sekme sınırı doluysa aynı sekmede geçmişli gezinmeye düşülür (bağlantı ölmez).
- [x] `FandomWikiReader.ReaderTabStrip` yeniden yazıldı:
  - Sekmeler genişliği **eşit paylaşır**; açıldıkça daralır, kapandıkça genişler (üst sınır 220 dp).
  - Eşit pay 88 dp altına inerse sabit genişlik + yatay kaydırma; aktif sekme `animateScrollToItem` ile görünür tutulur.
  - Aktif sekme içerik arkaplanında, üst köşeleri yuvarlı (Chrome görünümü); dar pasif sekmelerde × gizlenir.
  - Uzun basma → diğer sekmeleri kapat (3 çağrı noktası da bağlandı: `MediaWikiTabContent`, `CharacterWikiTabSlot`, `CharacterDetailPage`).
- [x] Test: `FandomReaderBrowserTest` — mevcut 3 teste ek olarak sağa ekleme, ilk sekme, kopya önleme,
      kendine bağlantı, sınır, çoğaltmanın yeri, kapatma sırası, arkaplan sekmesi kapatma, diğerlerini kapatma, sekme başına geçmiş (toplam 13 test).

### Sürüm / notlar
- [x] `appVersionName` `2.4.284` → `2.4.285`.
- [x] `release_notes_v2.4.285.md` oluşturuldu; `RELEASE_NOTES.md` başına eklendi.

---

## 4. Değişen Dosyalar (bu ZIP'in içeriği)

| Dosya | Değişiklik |
|---|---|
| `PLAN_TASK_WIKI_SEKME_CHROME_DUZENI_VE_YUZDE_KODLU_CIP_FIX_2026-10-14.md` | Bu plan/task dosyası |
| `app/build.gradle.kts` | Sürüm 2.4.285 |
| `app/src/main/java/com/kitsugi/animelist/data/fandom/FandomHtmlText.kt` | `parseWikiLink` tam UTF-8 çözme |
| `app/src/main/java/com/kitsugi/animelist/data/fandom/FandomRepository.kt` | Önbellek `sec:v4` → `sec:v5` |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomReaderBrowser.kt` | `openInNewTab`, sağa ekleme, `closeOtherTabs` |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomWikiReader.kt` | Chrome tarzı sekme şeridi |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomMediaWikiViewModel.kt` | Bağlantılar yeni sekmede; diğerlerini kapat |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomCharacterWikiViewModel.kt` | Bağlantılar yeni sekmede; diğerlerini kapat |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/MediaWikiTabContent.kt` | `onCloseOtherTabs` bağlantısı |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/CharacterWikiTabSlot.kt` | `onCloseOtherTabs` bağlantısı |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/CharacterDetailPage.kt` | `onCloseOtherTabs` bağlantısı |
| `app/src/test/java/com/kitsugi/animelist/data/fandom/FandomHtmlTextTest.kt` | Yüzde-kodu testleri (+2 test) |
| `app/src/test/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomReaderBrowserTest.kt` | Sekme testleri (+10 test) |
| `release_notes_v2.4.285.md` | Sürüm notları (TR/EN) |
| `RELEASE_NOTES.md` | v2.4.285 bloğu başa eklendi |

---

## 5. Kabul / Doğrulama

1. `./gradlew :app:testGmsDebugUnitTest --tests "*FandomHtmlTextTest*" --tests "*FandomReaderBrowserTest*"`
2. Cihazda Yoshihiro Togashi (Hunter × Hunter wiki) sayfasını aç:
   - Çipler `Shinjō, Yamagata`, `Weekly Shōnen Jump`, `Tankōbon`, `Ten de Shōwaru Cupid` görünmeli.
   - Çipe tıklayınca sayfa, mevcut sekmenin **sağında yeni sekme** olarak açılmalı; ilk sekme yerinde kalmalı.
   - Sekmeleri çoğaltıp genişliğin eşit daraldığını, ~5+ sekmede şeridin kaydığını ve yeni sekmenin görünür olduğunu kontrol et.
   - Bir sekmeye uzun bas → diğerleri kapanmalı. Aktif sekmeyi kapat → sağ (yoksa sol) komşu açılmalı.

## 6. Notlar / Riskler
- Sekme başına sayfa/içerik durumu ViewModel'de tek `ReaderState` olarak tutulmaya devam ediyor;
  sekme değişimi sayfayı Repository önbelleğinden yeniden yüklüyor (kaydırma konumu sekme+sayfa anahtarıyla zaten korunuyor).
  Sekme başına tam içerik anlık görüntüsü (bellekte hazır sayfa) istenirse ayrı bir iş olarak planlanmalı.
- Yönlendirme (redirect) sonrası sekme başlığı `normalizeCurrentLocation` ile güncellenmeye devam ediyor;
  bu yüzden kopya kontrolü, çözülmüş başlıkla da uyumlu (büyük/küçük harf + `_` duyarsız).
