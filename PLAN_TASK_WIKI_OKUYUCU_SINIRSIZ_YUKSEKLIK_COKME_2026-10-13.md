# Plan / Görev: Wiki Okuyucu "Fandom sayfasını" — Sınırsız Yükseklik Çökmesi (+ Galeri Detay Levhası)

**Tarih:** 2026-10-13
**Branch:** arena/74634f85-kitsugi-beta
**Temel commit:** 9143599 (v2.4.282)
**Yeni sürüm:** **2.4.283**
**Cihaz:** Xiaomi 23043RP34C (pipa) · Android 13 / API 33 · arm64-v8a · v2.4.282-beta.29865164 (release)

---

## 1. Hata (kullanıcı bildirimi)

- **Belirti:** Yapım detayı → **Wiki** sekmesi → "İlgili wiki sayfaları" şeridindeki
  **"Fandom sayfasını"** butonuna basınca uygulama **anında kapanıyor** (ekran görüntüsünde turuncu çerçeveli buton).
- **İstisna:** `java.lang.IllegalStateException: Vertically scrollable component was measured with an
  infinity maximum height constraints, which is disallowed.`
- **Yığın (kısaltılmış):**
  `ScrollNode.measure` → `SimpleGraphicsLayerModifier` → `FillNode` → `ColumnMeasurePolicy` →
  `InsetsPaddingModifierNode` → `FillNode` → … → `AndroidComposeView.dispatchDraw`
- **Thread:** `main` (gerçek çökme) · Bellek 65/512 MB → OOM yok.

## 2. Kanıt (Drive raporları — 7 dosyanın tamamı okundu)

| Rapor | Zaman | Son ekran | Not |
|---|---|---|---|
| `crash_log (7).txt` | 22:18:55.016 · pid 10436 | `ApiResultDetail(Sousou no Frieren)` | **Kullanıcının bildirdiği çökme** |
| `crash_log (6).txt` | 22:16:37.392 · pid 8071 | `Galeri: Clannad: After Story (282 görsel)` | Aynı istisna, aynı yığın |
| `logcat_at_crash (3)` | 22:18:55.071 | — | İki FATAL + 22:18:32–51 arası zararsız 404/400 logları |
| `live_logcat_report (4)` | 22:18:55 | — | `E/KitsugiCrash` kopyası + çökme sonrası MIUI satırları |
| `crash_history (3)` | 12 kayıt | — | 9 sessiz kapanma + 1 native SIGSEGV + **2 Java FATAL (ikisi de bu istisna)** |
| `kitsugi_debug (4).log` | — | — | Oynatıcı tanılaması, çökme ile ilgisiz |
| `device_info (5).txt` | — | — | Sürüm/cihaz bilgisi |

> Detaylı tablo ve kayıt kayıt özet: `KOK_NEDEN_RAPORU_WIKI_OKUYUCU_VE_GALERI_SINIRSIZ_YUKSEKLIK_2026-10-13.md`

## 3. Kök neden (stack → kaynak kod birebir)

1. **Okuyucu sayfanın İÇİNE gömülü çiziliyordu** — `FandomWikiReaderSheet`, çağıran sekmenin
   kompozisyon ağacına doğrudan giriyordu: `wiki/MediaWikiTabContent.kt:178` ve
   `wiki/CharacterWikiTabSlot.kt:125`.
2. **Sekme içeriği sınırsız yükseklikle ölçülüyor** — `DetailPageScaffold.kt` portre düzenindeki
   `HorizontalPager`, sekme yüksekliğini doğal ölçmek için
   `constraints.copy(minHeight = …, maxHeight = Constraints.Infinity)` (~satır 350) kullanır;
   yatay/TV düzeninde ise sayfa içeriği zaten `Box(Modifier.fillMaxSize().verticalScroll(...))`
   (satır 212) içindedir.
3. **Kesişim = çökme** — Okuyucunun gövdesi `ReaderContent`
   → `Column(Modifier.fillMaxSize().verticalScroll(scrollState))` (`FandomWikiReader.kt:247`).
   Gömülü hâlde bu `verticalScroll` **sonsuz max height** alır (ya da dış kaydırmayla iç içe girer)
   → `checkScrollableContainerConstraints` istisnası.
4. **Tetik anı** — Buton `WikiRelatedPageCard` (`FandomWikiReader.kt:596+`) → `onOpenItem` →
   `openReader(title)`: `Loading` (iskelet, güvenli) → `ReaderState.Ready` → `ReaderContent`
   kompoze edilir → **ilk ölçümde patlar**. Veri/ağ katmanında hata yoktur.

**Doğrulama (kaynak taraması):** `windowInsetsPadding(WindowInsets.navigationBars)` uygulamada
**yalnızca** `FandomWikiReader.kt:145`'te; `wiki/` klasöründeki `verticalScroll` kullanımları ise
yalnızca `FandomWikiReader.kt:247` ve (sınırlı yükseklikli) `MediaWikiTabContent.kt:919`'da.

## 4. Yapılan değişiklikler

| # | Dosya | Değişiklik |
|---|---|---|
| 1 | `ui/screens/detail/wiki/FandomWikiReader.kt` | `FandomWikiReaderSheet` artık **kendi tam ekran `Dialog` penceresinde** çizilir (`usePlatformDefaultWidth=false`, `decorFitsSystemWindows=false`, `dismissOnBackPress=true`, `dismissOnClickOutside=false`) → pencere kısıtları her koşulda sınırlı. Ayrıca `statusBarsPadding()` + `navigationBars` insets'i, MIUI force-dark koruması (`DisableDialogWindowForceDark`) ve neden'i anlatan KDoc bloğu. Geri tuşu: yığın varsa `onBack`, boşsa `onClose`. |
| 2 | `ui/screens/detail/CharacterDetailPage.kt` | Sayfa kökündeki **ikinci (çift) okuyucu render'ı kaldırıldı** — aksi hâlde Dialog ile birlikte üst üste iki pencere açılır, geri tuşu iki kez basılması gerekirdi. Kullanılmayan importlar temizlendi. Okuyucuyu artık tek noktadan `CharacterWikiTabSlot` render eder. |
| 3 | `ui/components/KitsugiImageGalleryDialog.kt` | `GalleryDetailsSheet` kaydırma alanına `heightIn(max = screenHeightDp × 0.85)` koruması (raporsuz çalışan aynı hata sınıfı — `crash_log (6)`). |
| 4 | `ui/components/KitsugiSheetOrDialog.kt` | `DisableDialogWindowForceDark()` `private` → `internal` (okuyucu penceresiyle paylaşım). |
| 5 | `app/build.gradle.kts` | `appVersionName` **2.4.282 → 2.4.283**. |
| 6 | `RELEASE_NOTES.md` + `release_notes_v2.4.283.md` | TR + EN sürüm notları (kritik düzeltme başlığı). |

## 5. Doğrulama durumu

- [x] Kotlin söz dizimi: 4 değişen dosya `tree-sitter-kotlin` ile tarandı → **0 hata**
      (sandbox'ta JDK/Android SDK yok, `./gradlew` çalıştırılamadı)
- [x] Tüm çökme raporları baştan sona okundu (`crash_history` dâhil, `download_file` + bash ile)
- [ ] **APK derlemesi** (yerelde: `./gradlew assembleDebug`)
- [ ] Frieren → Wiki → "Fandom sayfasını" → okuyucu tam ekran açılıyor, çökmüyor
- [ ] Okuyucu içi: İçindekiler'den bölüm yükleme, ilgili sayfa kartıyla sayfa değiştirme,
      üst bar geri / kapat, tarayıcıda aç
- [ ] Aynı akış karakter ve ekip (staff) Wiki sekmelerinde
- [ ] Yatay mod / TV'de aynı akış (iç içe kaydırma yok)
- [ ] Galeri → "Detaylar" levhasını aç/kapat (5–10 kez) → çökme yok
- [ ] `Ayarlar → Hakkında`: sürüm `2.4.283-beta.<timestamp>`

## 6. Açık işler (bu düzeltmenin kapsamı DIŞINDA)

- [ ] **Native SIGSEGV ailesi** (13:58:30, v2.4.275, RenderThread/`libhwui.so`) — ayrı iş;
      `PLAN_TASK_CRASH_LOG_5_VE_TUM_COKMELER_COZUM_2026-10-13.md` Görev 1–4 kapsamında.
- [ ] **TV/D-pad okuyucu kaydırması** — okuyucu artık tam ekran pencere olduğundan
      `Modifier.dpadVerticalFastScroll(...)` bağlanabilir (bu yamada bilinçli olarak yapılmadı).
- [ ] `crash_log (6)` (Galeri) için tetikleyicinin kesin composable'ı — iki olasılık (stale ekran
      kaydıyla okuyucu **veya** Detaylar levhası) da bu yamayla kapatıldı; ileride aynı hata
      görülürse yeniden değerlendirilmeli.

## 7. Değişen dosyalar

```
app/build.gradle.kts
RELEASE_NOTES.md
release_notes_v2.4.283.md                                                        (yeni)
KOK_NEDEN_RAPORU_WIKI_OKUYUCU_VE_GALERI_SINIRSIZ_YUKSEKLIK_2026-10-13.md         (yeni)
PLAN_TASK_WIKI_OKUYUCU_SINIRSIZ_YUKSEKLIK_COKME_2026-10-13.md                   (bu dosya)
app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomWikiReader.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/detail/CharacterDetailPage.kt
app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiImageGalleryDialog.kt
app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiSheetOrDialog.kt
```

- Tam diff: `DEGISIKLIKLER_v2.4.283_wiki_okuyucu_sinirsiz_yukseklik.patch` (bu zip içinde)
- Kaynak dosyalar: `degisen_dosyalar/` (zip içinde, depo yollarıyla birebir)
