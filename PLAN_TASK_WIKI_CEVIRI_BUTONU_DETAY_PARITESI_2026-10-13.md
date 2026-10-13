# PLAN/TASK — Wiki çeviri butonlarının detay sayfası "Açıklama" çeviri butonuyla birebir eşitlenmesi

Tarih: 2026-10-13
Durum: **Kodlandı.** Derleme + cihaz testi kullanıcıda (sandbox'da JDK/Android SDK yok; doğrulama GitHub Actions `android-build.yml` ile).
Önceki adım: `PLAN_TASK_WIKI_ANINDA_SEKME_VE_MEDYA_WIKI_FAZ2_2026-10-13.md` (Faz 2) ve Fandom Wiki Faz 3 (`da5e149`).

## Kullanıcı isteği (özet)

> "Wiki sayfalarındaki **tüm** çeviri butonları; **otomatik çeviri**, **çeviri butonunu gösterme**,
> **yüklenme animasyonu** ve **harici [çeviri uygulamasında] açma** mekaniği açısından detay
> sayfalarındaki **Açıklama** çeviri butonuyla **tıpatıp aynı** olmalı. Ayarlarda otomatik çeviri
> açıksa bu mantık çalışmaya devam edecek."

Ekran görüntülerinde işaretlenen yerler: medya wiki özeti, medya wiki kart açıklaması, yapım wiki
"İçindekiler" bölümleri (bu sayfada buton çıkmıyor), karakter wiki "Bilgi kutusu" ve "Wiki özeti".

## Referans davranış (detay sayfası Açıklama kartı)

`DetailSynopsisCard` (`ui/screens/detail/KitsugiDetailInfoSection.kt`) + `EntryDetailTabContents.kt:117`:

1. **Buton her zaman vardır** (orijinal metin boş değilse) — `KitsugiTranslateIconButton`. Metin
   çevrilmişse "Orijinali göster" yazı düğmesine dönüşmez.
2. **Dokununca harici çeviri uygulaması açılır:** `context.openTranslator(originalText, preferredTranslator)`
   → kullanıcı tercihi (GOOGLE / DEEPL / TRANSLATE_YOU) → DeepL mini → DeepL → Google mini → Google
   uygulaması → Google web → TranslateYou.
3. **Yüklenme animasyonu:** `isLoading = LocalKitsugiTranslationLoading.current` → `KitsugiTranslateIconButton`
   ikonu nabız gibi yanıp söner (alpha 0.30↔1, 700 ms) ve etrafında dönen halka çıkar; buton o süre
   dokunulamaz. (Galeri butonuyla aynı animasyon.)
4. **Otomatik çeviri:** ayar açıksa VM metni uygulama içinde çevirir (`translateToTarget(text, onPartial)`
   ile **parça parça akış**); kapanınca orijinale döner; hedef dil değişince tazelenir; ayar sayfa
   açıkken değiştirilirse anında tepki verir (`settingsFlow` dinleyicisi).

## Wiki tarafındaki sorun (önceki durum)

- `CharacterWikiTabContent.kt` → private `TranslateToggle(key, isTranslated, isLoading, …)`:
  çevrilmişse **"Orijinali göster"** yazı düğmesi, çevrilmemişse 36 dp ikon; dokununca **uygulama içi**
  `translate(key)` çalışıyordu (harici uygulama açılmıyordu).
- `MediaWikiTabContent.kt` → internal `WikiTranslateToggle(...)`: aynı mantık (özet + kart açıklaması).
- Sonuç: tanki butonun "görünme/dönüşme" davranışı, tıklama hedefi ve yükleme durumu detay
  sayfasındakiyle uyuşmuyordu.

## Yapılan değişiklikler

### 1. Yeni ortak bileşen: `wiki/WikiTranslateButton.kt`

Tek çatı altında detay sayfası mantığının tamamı:

```kotlin
WikiTranslateButton(
    text = <orijinal metin>,
    preferredTranslator = <ayar tercihi>,
    accentColor = <vurgu>,
    isLoading = <bu metnin uygulama içi çevirisi sürüyor mu>
)
```

- Metin boşsa hiç çizilmez (detay kartındaki `!originalText.isNullOrBlank()` koşulunun eşi).
- Dokununca `openTranslator(text, preferredTranslator)` → **harici çeviri uygulaması**.
- `isLoading = isLoading || LocalKitsugiTranslationLoading.current` → hem wiki çevirisi hem sayfa
  düzeyi (açıklama/biyografi) çevirisi sürerken aynı nabız + halka animasyonu.
- Boyut/ikon/tint çağrısı `DetailSynopsisCard` ile birebir aynı (varsayılan 24 dp ikon, 32 dp halka,
  `R.string.action_translate` içerik açıklaması).

### 2. Karakter / kişi wiki sekmesi (`CharacterWikiTabContent.kt`)

- "Wiki özeti" (açıklama), **Bilgi kutusu** ve **her bölüm** için `TranslateToggle` yerine
  `WikiTranslateButton` çağrılıyor; üçü de artık **her zaman ikon**, dokununca **harici uygulama**.
- Bilgi kutusu için harici uygulamaya gönderilen metin yeni `buildInfoboxSourceText(...)` ile
  üretilir: etiketler/değerler wiki'nin kendi dilinde, **spoiler (gizli) alanların değeri gönderilmez**
  (`[…]`). Kopyalama metniyle (`buildInfoboxCopyText`) aynı `hiddenIndices` paylaşılır.
- Bölümler için `state.content.plainText()` (orijinal) gönderilir.
- `TranslateToggle` tamamen kaldırıldı; `onTranslate` / `onTranslateInfobox` / `onShowOriginal`
  parametreleri (işlevsiz kaldıkları için) UI zincirinden çıkarıldı.

### 3. Medya wiki sekmesi (`MediaWikiTabContent.kt`)

- Seri özeti kartı ve çip/arama sonucu kartı (`WikiItemSheet`) artık `WikiTranslateButton` kullanıyor.
- `WikiTranslateToggle` kaldırıldı; `onTranslate` / `onShowOriginal` parametreleri kaldırıldı.
- `MediaWikiTabSlot` ayarlardaki çeviri tercihini VM'den toplayıp içeriğe aktarır.

### 4. ViewModel'ler: otomatik çeviri "detay sayfası gibi" davranıyor

`FandomCharacterWikiViewModel` ve `FandomMediaWikiViewModel`:

- **`preferredTranslator` StateFlow:** ayarlardan (`SettingsDataStore.settingsFlow`) okunur; harici
  çeviri tercihi UI'ya buradan akar (detay sayfalarındaki `settingsState.preferredTranslator` eşi).
- **Ayar dinleyicisi:** `(autoTranslateEnabled, translateTargetLanguage, preferredTranslator)`
  üçlüsü `distinctUntilChanged` ile izlenir:
  - otomatik çeviri **açılınca** → bellekteki tüm wiki metinleri çevrilir (`retranslateLoadedTexts`),
  - **kapanınca** → çalışan işler iptal edilir ve metinler orijinale döner,
  - **hedef dil değişince** → `force` ile çeviriler tazelenir (önbelleğe yazılan eski dil kullanılmaz).
- **Parça parça akış:** `translateToTarget(text) { partial -> … }` — detay sayfası açıklamasındaki
  `onPartial` akışının aynısı; çeviri geldikçe metin ekranda tamamlanır, yüklenme halkası dönmeye
  devam eder. Çeviri boş dönerse/zaten hedef dildeyse akan taslak geri alınır (orijinal görünür).
- **`force` desteği:** `translate(key, force)` ve `translateInfobox(force)`; hedef dil değişimi ve
  "otomatik çeviri yeni açıldı" durumlarında eski sonuç atlanır.
- **Karakter wiki:** sayfa çözümlendiğinde otomatik çeviri açıksa artık **özet + bilgi kutusu** birlikte
  çevrilir (bölümler açıldıkça `loadSection` içinde çevrilir). Bilgi kutusunun elle çevrilme yolu
  kalktığı için bu tamamlayıcı adım zorunluydu.

### 5. Yuvalar / çağrı yerleri

- `CharacterWikiTabSlot` (her iki varyant) ve `MediaWikiTabSlot`: `preferredTranslator` aktarımı;
  `onTranslate`/`onShowOriginal`/`onTranslateInfobox` parametreleri kaldırıldı.
- `CharacterDetailPage.kt` (dikey + yatay düzen, 2 çağrı): `preferredTranslator = preferredTranslator`
  eklendi, kaldırılan parametreler silindi.
- `StaffDetailPage`, `MediaEntryDetailPage`, `ApiResultDetailPage`, `MangaDetailScreen`, `TvDetailScreen`:
  kod değişikliği **gerekmedi** — hepsi VM'e bağlı yuvayı (`CharacterWikiTabSlot(viewModel=…)` /
  `MediaWikiSectionCard(...) → MediaWikiTabSlot(viewModel=…)`) kullandığı için yeni davranışı
  otomatik alır.

### 6. Dizgi

- `fandom_show_original` ("Orijinali göster") artık hiçbir kod yolunda kullanılmıyor; anahtar
  `strings.xml` / `values-en/strings.xml` içinde ileride kullanılmak üzere bırakıldı (yeni dizgi
  eklenmedi).

## Beklenen davranış (kabul ölçütleri)

| # | Senaryo | Beklenen |
|---|---|---|
| 1 | Wiki sekmesi, otomatik çeviri **kapalı** | Tüm metinler orijinal; çeviri ikonuna dokununca **harici çeviri uygulaması** açılır (metin orijinal hâliyle gider) |
| 2 | Wiki sekmesi, otomatik çeviri **açık** | Metinler kendiliğinden çevrilir; çeviri sürerken ikonlarda **nabız + dönen halka** animasyonu; butonlar o süre dokunulamaz |
| 3 | Çeviri tamamlandığında | İkon **yerinde kalır** ("Orijinali göster" düğmesi yok); metin çevrilmiş hâlde durur |
| 4 | Sekme açıkken ayar aç/kapa | Anında tepki: açılınca çevrilir, kapanınca orijinale döner |
| 5 | Sekme açıkken hedef dil değişimi | Çeviriler yeni dile tazelenir |
| 6 | Bilgi kutusu | Toplu çeviri ikonu harici uygulamayı açar; gizli spoiler alanlarının değeri gönderilmez |
| 7 | Bölüm satırları | Bölüm yüklenince (otomatik çeviri açıksa) çevrilir; ikon bölümün orijinal metnini harici uygulamada açar |
| 8 | Medya wiki kartı/özeti | Çip açıklaması ve seri özeti aynı kurallara uyar |

## Değişen dosyalar

| Dosya | Açıklama |
|---|---|
| `app/.../detail/wiki/WikiTranslateButton.kt` | **Yeni:** ortak çeviri butonu (harici açma + nabız/halka animasyonu) |
| `app/.../detail/wiki/CharacterWikiTabContent.kt` | Özet/bilgi kutusu/bölüm butonları yeni bileşene geçti; `TranslateToggle` kaldırıldı; `buildInfoboxSourceText` eklendi |
| `app/.../detail/wiki/MediaWikiTabContent.kt` | Özet + kart açıklaması yeni bileşene geçti; `WikiTranslateToggle` kaldırıldı |
| `app/.../detail/wiki/CharacterWikiTabSlot.kt` | `preferredTranslator` aktarımı; işlevsiz parametrelerin temizliği |
| `app/.../detail/wiki/FandomCharacterWikiViewModel.kt` | `preferredTranslator` akışı, ayar dinleyicisi (`retranslateLoadedTexts`), `force`, parça parça akış, bilgi kutusu otomatik çevirisi |
| `app/.../detail/wiki/FandomMediaWikiViewModel.kt` | Aynı üç mekanizma (tercih akışı, ayar dinleyicisi, `force`) + parça parça akış |
| `app/.../detail/CharacterDetailPage.kt` | İki wiki yuvası çağrısı güncellendi (`preferredTranslator`, kaldırılan parametreler) |

## Sonraki adımlar (öneri)

- Cihazda `Frieren` (medya) ve `Fern` (karakter) sayfalarında 6 kabul ölçütünün doğrulanması.
- İsteğe bağlı: çeviri butonlarına **uzun basma** ile "orijinali göster" hızlı yolu (bu turda
  birebirlik bozulmasın diye eklenmedi).
- İsteğe bağlı: `fandom_show_original` dizgisinin iki dilden de silinmesi (başka bir tur kullanmazsa).
