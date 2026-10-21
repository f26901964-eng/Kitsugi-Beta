# Plan / Task — Wiki tarayıcı şeridi, bağlantıların yeni sekmede açılması ve OP/ED siyah ekran

**Tarih:** 2026-10-21
**Sürüm:** v2.4.337 (yayınlanmadı — çalışma ağacında)
**Baz commit:** `971abf1` — `fix(notifications,mal): MAL bildirimleri devam sayfalaması … (v2.4.336)`
**Çalışma dalı:** `arena/3f9f6303-kitsugi-beta`
**İlgili rapor:** `COZUM_RAPORU_WIKI_SERIDI_VE_TEMA_OYNATICI_2026-10-21.md`
**Sürüm notları:** `release_notes_v2.4.337.md`, `RELEASE_NOTES.md`

---

## 1. Kullanıcı istekleri (birebir kapsam)

| # | İstek | Durum |
|---|---|---|
| A | Fandom wiki tarayıcısında WebView üstündeki dağınık kontrol paneli kompakt, premium ve uygulama temasıyla uyumlu olacak | ✅ |
| A2 | “Hepsini derle topla derken bir buton var sağ köşede 3 nokta **hepsini oraya taşı sadece sekme kısmı kalsın**” | ✅ |
| B | “**Tarayıcı içindeyken tıklanılan bağlantılar yeni sekme olarak açılsın otomatik**” | ✅ |
| C | Detay sayfası “Videolar” sekmesinde OP/ED satır içi oynatıcıları siyah ekran veriyor | ✅ |

**Kabul kriterleri**

1. WebView’in üstünde **yalnızca sekme şeridi** görünür; başka hiçbir görünür kontrol satırı kalmaz.
2. Geri/ileri, mod seçimi, çeviri, reklam engeli, masaüstü düzeni, yenile, sayfada bul, görünüm, hakkında, tarayıcıda aç, yeni sekme ve kapat — hepsi erişilebilir olmalı (hiçbir işlev kaybolmaz).
3. WebView içinde dokunulan **düz makale bağlantısı** yeni sekmede açılır; kaynak sayfa yerinde kalır.
4. Sekme açılamayan durumlar (çapa, `diff`/`oldid`/`action=edit`, `Special:`/`File:`/`Category:`, 12 sekme sınırı, JS yönlendirme) **ölü doğmaz**: mevcut sekmede geçmişli gezinmeye düşer.
5. OP/ED satırı sesli ama görüntüsüz kalmaz; kare çizilmiyorsa motor değişir, iki motor da olmazsa dürüst hata kartı çıkar.
6. Tüm UI metinleri `R.string` üzerinden; her yeni anahtar **hem `values` hem `values-en`** içinde.

---

## 2. Tasarım kararları

### 2.1 Tek görünür kontrol: sekme şeridi + sağ köşede `⋮`

- **Başlık çubuğu tamamen kaldırıldı.** Eski tasarımda başlık + alan adı satırı vardı; kullanıcı “sadece sekme kısmı kalsın” dediği için sayfa kimliği artık sekme çipinden okunuyor (Chrome davranışı).
- **Sekme şeridi tek sekmede de görünür.** Önceki turda “tek sekmede gizle” kuralı vardı; bu kullanıcı isteğiyle çeliştiği için kaldırıldı.
- **`+` sabit yerde**, 12 sekmede pasifleşir (kaybolmaz → düzen zıplamaz).
- **`⋮` 34 dp daire**, menü satırları 48 dp: tek elle erişim + erişilebilirlik.
- Şerit toplamı: 3 dp üst boşluk + 32 dp sekme + 5 dp alt boşluk + 1 dp ayırıcı = **~41 dp** (önce ~220 dp).

### 2.2 Sayfa araçları artık satır değil, tek sayfa

- Çeviri ve reklam engeli anahtarları, görünüm/yazı boyutu, sayfada bul, hakkında ve durum/uyarı listesi tek dialogda.
- Açma isteği `WikiPageToolsController.openRequest` sayacı ile tek yönlü akar; `consume()` isteği **bir kez** tüketir (sekme değişince eski istek yeni sayfada dialog açmaz).

### 2.3 Bağlantı → yeni sekme

`shouldOverrideUrlLoading` (yalnızca ana çerçeve):

| Koşul | Sonuç |
|---|---|
| Dokunuş yok (`hasGesture()` = false) | mevcut sekmede (JS/form/otomatik yönlendirme) |
| Aynı belge (`#bölüm`) | mevcut sekmede |
| Düz makale bağlantısı | **yeni sekme**, kaynak korunur |
| Çapa / `diff` / `oldid` / `action≠view` / `Special:` / `File:` / `Category:` / başka wiki | mevcut sekmede |
| 12 sekme dolu | mevcut sekmede, geçmişe eklenerek |
| Aynı sayfa başka sekmede | yeni sekme yok, o sekmeye geçilir |

Karar iki VM’de tek yerde: `openReaderWebLink(url): Boolean`. `true` dönerse WebView navigasyonu iptal edilir.

---

## 3. Uygulama planı ve yapılanlar

- [x] `WikiChromeComponents.kt` — ortak yüzey parçaları (`WikiIconAction`, `WikiChromeHairline`).
- [x] `FandomWikiReader.kt` — `ReaderTopBar` kaldırıldı; `ReaderTabStrip` `trailing` yuvasıyla `⋮` menüsünü (`ReaderBrowserMenu`) taşıyor; `ReaderMenuItem` / `ReaderMenuDivider`; `switchToOriginal` ile bayat WebView oturumu temizliği.
- [x] `WikiPageTools.kt` — görünür satır kaldırıldı; `WikiPageToolsController` + `WikiPageToolsDialog` + `WikiReadingSettingsDialog` + bilgi dialogu + `wikiStatusLines`.
- [x] `WikiOriginalPage.kt` — yalnızca WebView + bul çubuğu; `shouldOverrideUrlLoading` makale bağlantısını yeni sekmeye yönlendiriyor; `toolsController` / `onOpenLinkInNewTab` parametreleri.
- [x] `FandomMediaWikiViewModel.kt`, `FandomCharacterWikiViewModel.kt` — `openReaderWebLink(url)` (makale filtresi, aynı sayfa kontrolü, sekme sınırı, yerinde fallback).
- [x] `MediaWikiTabContent.kt`, `CharacterWikiTabSlot.kt`, `CharacterDetailPage.kt` — `onOpenLinkInNewTab = viewModel::openReaderWebLink`.
- [x] `res/values/strings.xml` + `res/values-en/strings.xml` — yeni anahtarlar iki dilde birebir.
- [x] OP/ED siyah ekran: Media3-first + `key(playerEngine)` (3 çağrı yeri), VP9 ön kontrolü, `hasRenderedVideoFrame` kare doğrulaması, MPV yedeğinde `clip()` kaldırma, dürüst hata kartı, `InlinePlayerSupportTest`.
- [x] Dokümantasyon: rapor + iki sürüm notu dosyası.
- [x] Statik doğrulama: parantez/süslü denge, `R.string` çapraz kontrolü (17.538 anahtar × 2 dil), ikon importları, `git diff --check`, ölü kod temizliği.

**Yapılamayan:** `./gradlew` derlemesi ve cihaz testi — bu ortamda JDK/Android SDK yok (`java`, `kotlinc`, `gradle` bulunmuyor).

---

## 4. Cihazda doğrulanacaklar (saha kontrol listesi)

1. WebView üstünde yalnızca ~41 dp sekme şeridi + `⋮` görünüyor mu?
2. `⋮` menüsündeki her öğe çalışıyor mu (geri/ileri, mod, sayfa araçları, masaüstü/mobil, yenile, tarayıcıda aç, yeni sekme, kapat)?
3. “Sayfa araçları” dialogunda çeviri ve reklam engeli anahtarları anında uygulanıyor mu?
4. Makale bağlantısına dokun → yeni sekme sağda açılıyor, kaynak sekme duruyor mu?
5. `Special:`/`diff` bağlantısı aynı sekmede mi açılıyor?
6. 12 sekme doluyken bağlantı ölü doğmuyor mu?
7. OP/ED satırında `engine=MEDIA3 visibleVideo=true` logu ilk 6 saniyede görünüyor mu?

---

## 5. Değişen dosya özeti

| Tür | Dosya |
|---|---|
| Yeni | `ui/screens/detail/wiki/WikiChromeComponents.kt` |
| Wiki UI | `FandomWikiReader.kt`, `WikiPageTools.kt`, `WikiOriginalPage.kt` |
| Wiki VM | `FandomMediaWikiViewModel.kt`, `FandomCharacterWikiViewModel.kt` |
| Wiki çağrı yerleri | `MediaWikiTabContent.kt`, `CharacterWikiTabSlot.kt`, `CharacterDetailPage.kt` |
| Oynatıcı | `InlinePlayerSupport.kt`, `KitsugiDetailThemesTrailerComponents.kt`, `KitsugiDetailPlayerComponents.kt`, `KitsugiFullscreenPlayerScreen.kt` |
| Motor | `PlayerEngine.kt`, `Media3PlayerEngine.kt`, `MpvPlayerEngine.kt`, `KitsugiMpvSurfaceView.kt` |
| Test | `app/src/test/.../InlinePlayerSupportTest.kt` |
| Kaynak | `res/values/strings.xml`, `res/values-en/strings.xml`, `app/build.gradle.kts` |
| Doküman | `RELEASE_NOTES.md`, `release_notes_v2.4.337.md`, `COZUM_RAPORU_WIKI_SERIDI_VE_TEMA_OYNATICI_2026-10-21.md`, bu dosya |
