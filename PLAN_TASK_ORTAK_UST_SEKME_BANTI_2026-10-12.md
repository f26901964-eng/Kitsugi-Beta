# PLAN / TASK — Ortak Üst Sekme Bandı + Kaydırmalı Sayfalar (4 Ekran)

**Tarih:** 2026-10-12
**Dal:** `arena/24b4c8c6-kitsugi-beta`
**Sürüm:** 2.4.257 → **2.4.258**
**Şikâyet:** "Görünüm & Tercihler"deki üst sekme bandı ve sağa/sola kaydırma mantığı; Oynatıcı Ayarları,
İndirmeler, Platform Hata Kodları ve Hesap Bağlantıları ekranlarında da aynı olmalı. Ayrıca bant
dikey/yatay, telefon/tablet ve her ekran boyutunda uyumlu olmalı; **ekranın sağına/soluna taşmamalı**.

---

## 0. Kullanıcı kararları (uygulamadan önce netleştirildi)

| # | Ekran | Seçilen davranış |
|---|-------|------------------|
| 1 | Oynatıcı Ayarları | 8 bölüm = **8 sekme**; ana menü listesi kaldırılır, sağa/sola kaydırma ile bölüm geçişi |
| 2 | Platform Hata Kodları | Kategoriler (Tümü/Manga/CloudStream/Genel/AniList/…) **sekme bandına** dönüşür; arama üstte kalır |
| 3 | Hesap Bağlantıları | **2 sekme**: Platformlar \| Senkronizasyon |

---

## 1. Ortak bileşen: `KitsugiSettingsSegmentedTabs` (KitsugiSettingsComponents.kt)

Tek noktada toplanan, tüm ayar sayfalarının kullandığı bant.

### Eski davranış (sorun)
```kotlin
Row(Modifier.fillMaxWidth()...) {
    tabs.forEachIndexed { index, tab ->
        Column(Modifier.weight(1f)) { ... }   // eşit bölünür, kaydırma YOK
    }
}
```
- Sekme sayısı 3'ten fazla olduğunda (ör. Oynatıcı Ayarları = 8) her sekme 40-45dp'ye sıkışır,
  metin okunmaz hale gelir; ya da tasarım gereği taşar.

### Yeni davranış (çözüm)
```kotlin
BoxWithConstraints(...) {
    val evenWidth = usableWidth / tabs.size
    val scrollable = evenWidth < SettingsTabMinWidth        // 104dp
    val segmentWidth = if (scrollable)
        (viewportWidth * 0.34f).coerceIn(104.dp, 168.dp)     // yatay/tablet üst sınırı
    else evenWidth

    LaunchedEffect(selectedIndex, focusedIndex, scrollable, ...) {
        // seçili (TV'de odaklı) sekmeyi görünür alana ortala
        scrollState.animateScrollTo((itemCenter - viewportWidth / 2f).coerceIn(0f, maxScroll))
    }

    Row(Modifier.then(if (scrollable) Modifier.horizontalScroll(scrollState).drawWithContent { /* kenar solma ipuçları */ } else Modifier)) { ... }
}
```

| Kural | Sonuç |
|---|---|
| Sekmeler sığıyorsa | Eşit bölünür (eski görünüm birebir korunur — Görünüm & Tercihler 3 sekme) |
| Sığmıyorsa | Bant **kendi içinde** yatay kaydırılır → ekrana taşma yok |
| Seçili sekme | Otomatik ortalanır (uzak sekmeye geçişte bant kendini kaydırır) |
| Kenarlar | "Devamı var" solma ipucu (22dp gradyan) |
| TV | D-pad ile odaklanan sekme `onFocusChanged` ile görünür alana kaydırılır |
| Logo desteği | `KitsugiSettingsTabOption(title, icon, platformId)` — verilirse ikon yerine `KitsugiPlatformLogo` |
| Responsive | `BoxWithConstraints` ile ölçüm; dikey/yatay, telefon/tablet/TV aynı kurallarla |

---

## 2. Oynatıcı Ayarları (`KitsugiPlayerSettingsDialog.kt`)

- `PlayerSettingsTab(subScreen, titleRes, icon)` + `PlayerSettingsTabs` (8 kayıt) eklendi.
- `AnimatedContent { when (activeSubScreen) }` → **`HorizontalPager(pageCount = 8)`**.
- Ana menü `LazyColumn`'u (8 `KitsugiSettingsItem`) **silindi**; `PlayerSettingsSubScreen.Main` artık sekme değil.
- `rememberPagerState(initialPage = indexOf(initialSubScreen))` sayesinde arama sonucundan derin bağlantı
  (ör. "Kod düzenleyici") doğrudan ilgili sekmede açılır.
- `LaunchedEffect(pagerState.currentPage) { onSubScreenChanged(...) }` ile dış dünya bilgilendirilir.
- **Her sekmenin kendi `LazyListState`'i** (pager sayfa kapsamında `rememberLazyListState()`) → bir bölümde
  aşağı inip diğerine geçince kaydırma konumu karışmaz.
- `subScreenScrollState` / `mainMenuScrollState` / `activeScrollState` kaldırıldı; `KitsugiSheetOrDialog`
  `sheetGesturesEnabled = false` ile açılıyor (yatay pager'lı diyaloglar için önerilen ayar).
- Header artık tek seviyeli: başlık sabit `pd_title`, geri butonu `onDismiss`.
- `BackHandler(enabled = embeddedMode) { onDismiss() }` (eski "alt sayfadan ana menüye dön" mantığı kalktı).
- `SettingsScreen.SettingsRoute.PlayerSettings`: başlık sabit "Oynatıcı Ayarları", geri → ayarlar ana sayfası.

---

## 3. İndirmeler (`DownloadsScreen.kt`)

- `TabRow + TabRowDefaults.SecondaryIndicator` bloğu (alt çizgili sekmeler) kaldırıldı.
- Yerine ortak bant: `Videolar (n)` / `Altyazılar (n)` / `Resimler (n)` + ikonlar.
- `HorizontalPager` ve klasör kısayolu aynen korundu; sayı metinleri (`downloads_videos_count`,
  `inline_subtitles_1_s_b81ee5c`, `downloads_images_count`) bandın başlığı olarak kullanılıyor.

---

## 4. Platform Hata Kodları (`PlatformErrorCatalogScreen.kt`)

- `FilterChip` satırı → **11 sekmeli ortak bant** (Tümü · Manga · CloudStream · Genel · AniList ·
  MyAnimeList · Kitsu · Shikimori · Bangumi · TMDB · Simkl).
- Platform sekmelerinde **orijinal platform logoları** (`platformLogoId()` → `anilist`, `mal`, `kitsu`, …).
- Sayfa başına liste: `ErrorCatalogPage(filterKey, normalizedQuery)`
  - `categoryDiagnosticCodes()` → Tümü: hepsi, Manga/CloudStream: yalnızca o platformun kodları, diğer sekmeler: boş.
  - `categoryListCodes()` → Tümü: hepsi, platform sekmesi: yalnızca o platformun kodları.
  - **Düzeltme:** Eskiden platform filtresi seçilse bile tanılama (Manga/CloudStream) kodları
    filtrelenmiyordu; artık her kategori yalnızca kendi kodlarını gösteriyor.
- Arama kutusu ve kayıt sayacı (`%1$d kod`) üstte sabit; sayaç **görünen kategoriye** göre hesaplanıyor.

---

## 5. Hesap Bağlantıları (`AccountSettingsSubPages.kt`)

- `AccountConnectionsHubContent` iki sekmeli yapıya çevrildi: **Platformlar** | **Senkronizasyon**.
- Sayfalar ayrı bileşenler: `PlatformConnectionsPage` (6 platform kartı) ve `SyncConnectionsPage`
  (Cross-Sync kartı + bağlı hesap sayısı).
- Her sayfanın kendi `ScrollState`'i var.

---

## 6. Dosya listesi

| Dosya | Değişiklik |
|---|---|
| `ui/components/KitsugiSettingsComponents.kt` | Ortak bant: responsive ölçüm, kaydırma, otomatik ortalama, kenar solması, logo desteği |
| `ui/components/KitsugiPlayerSettingsDialog.kt` | 8 sekme + `HorizontalPager`, ana menü kaldırıldı, sekme başına kaydırma konumu |
| `ui/screens/offline/DownloadsScreen.kt` | `TabRow` → ortak bant (3 sekme) |
| `ui/screens/settings/PlatformErrorCatalogScreen.kt` | Çipler → 11 sekme + sayfa başına filtre, logo desteği |
| `ui/screens/settings/AccountSettingsSubPages.kt` | Hub → 2 sekme (Platformlar / Senkronizasyon) |
| `ui/screens/settings/SettingsScreen.kt` | Oynatıcı sayfası başlığı/geri davranışı sadeleştirildi |
| `app/build.gradle.kts` | `appVersionName` = **2.4.258** |
| `release_notes_v2.4.258.md`, `RELEASE_NOTES.md` | Sürüm notları |

---

## 7. Riskler / notlar

- **Yatay kaydırma çakışması:** Oynatıcı bölümlerindeki `Slider`'lar kendi dokunuşlarını tüketir;
  sayfa kaydırma yalnızca boş alanda çalışır (Compose nested-scroll davranışı).
- **TV:** Sekme değişimi için D-pad odak + `onSelect`; odaklanan sekme otomatik kaydırılır.
- **Dil:** Yeni metin eklenmedi; tüm sekme başlıkları mevcut TR/EN kaynaklarından gelir.
- **`PlayerSettingsSubScreen.Main`** enum değeri geriye dönük uyumluluk için korunuyor (arama/deep-link yolu).
