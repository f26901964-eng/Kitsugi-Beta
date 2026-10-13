# KÖK NEDEN RAPORU — Wiki Okuyucu "Fandom sayfasını" Butonu + Galeri Detay Levhası Çökmesi

**Tarih:** 2026-10-13
**Cihaz:** Xiaomi 23043RP34C (pipa), Android 13 (API 33), arm64-v8a
**Uygulama:** 2.4.282-beta.29865164 (release)
**Belirti:** Yapım detayı → Wiki sekmesi → "İlgili wiki sayfaları" şeridindeki **"Fandom sayfasını"** butonuna basınca uygulama anında kapanıyor (ekran görüntüsünde turuncu çerçeveyle işaretlenen buton).

---

## 1. Okunan Çökme Raporları (Drive klasörü — 7 dosya)

| Dosya | Boyut | Ne içeriyor | Durum |
|---|---|---|---|
| `crash_log (7).txt` | 11,483 B | 22:18:55 çökmesi — **kullanıcının bildirdiği çökme** (Frieren detay sayfası) | ✅ tamamı okundu |
| `crash_log (6).txt` | 11,221 B | 22:16:37 çökmesi — aynı istisna, **Galeri (Clannad: After Story, 282 görsel)** açıkken | ✅ tamamı okundu |
| `device_info (5).txt` | 264 B | Cihaz/sürüm bilgisi (Xiaomi pipa, v2.4.282-beta.29865164) | ✅ tamamı okundu |
| `logcat_at_crash (3).txt` | 96,577 B | Çökme anı logcat (son 500 satır): 22:16:37 ve 22:18:55 FATAL EXCEPTION'ları + çökme öncesi 22:18:32–22:18:51 uygulama logları | ✅ tamamı okundu |
| `live_logcat_report (4).txt` | 96,683 B | Canlı logcat (son 400 satır): MIUIScout/PerfMonitor uyarıları + 22:18:55 çökme stack'i (`KitsugiCrash` etiketiyle) | ✅ tamamı okundu |
| `crash_history (3).txt` | 373,897 B | **9 sessiz kapanma kaydı + 1 NATIVE SIGSEGV (13:58:30) + 2 tam çökme raporu** (22:16:37 ve 22:18:55) | ✅ tamamı okundu |
| `kitsugi_debug (4).log` | 57,946 B | Oynatıcı tanılama (`PlayerLoadingDiag`, animethemes/googlevideo oturumları, `code=2004 msg=Source error`) — çökme ile ilgisi YOK | ✅ tamamı okundu |

> Not: `logcat_at_crash`, `live_logcat_report` ve `crash_history` dosyaları araç arayüzünde tek istekte kesiliyordu; dosyalar `download_file` ile çalışma alanına (`google_drive/`, `.gitignore`'da) indirilip **bash ile baştan sona** okundu. Gürültü satırları (PerfMonitor/MIUIScout) ayıklanarak çökme anına ait **tüm** anlamlı satırlar incelendi; ayrıca `crash_history` içindeki **her kaydın** "ÖLÜM SEBEBİ" ve "SON EKRAN" bölümleri tek tek listelendi.

### 1.1 Tüm geçmişin özeti (`crash_history (3).txt` — 12 kayıt)

| # | Zaman | Süreç | Son ekran / son eylem | Ölüm sebebi |
|---|---|---|---|---|
| 1 | 13:58:30 | pid 29747 · 2.4.275 | `Tab(Profile)` / `Galeri: My Deer Friend Nokotan (127 görsel)` | **NATIVE SIGSEGV** (`libc: SIG_DFL signal 11`, ucontext 0x7f61cc4e20) |
| 2–10 | 14:37:44 … 22:09:17 | 2.4.275–2.4.278 | `About`, `ApiResultDetail(...)`, `Tab(Explore)`, `Galeri: …`, `Tab(MyList)` | **SEBEP BULUNAMADI** (sessiz SIGKILL — arka plan kısıtlaması/log arabelleği) |
| 11 | 22:16:37.392 | pid 8071 · 2.4.282 | `Galeri: Clannad: After Story (282 görsel)`, `galeri gezinme: sayfa 6/71` | **JAVA FATAL** — aşağıdaki istisna |
| 12 | 22:18:55.016 | pid 10436 · 2.4.282 | `ApiResultDetail(Sousou no Frieren)` (kullanıcının ekran görüntüsü) | **JAVA FATAL** — aynı istisna |

**Sonuç:** Tüm geçmişte **Java istisnası olan yalnızca 2 çökme** vardır ve ikisi de **birebir aynı** `IllegalStateException`'dır. Yani kullanıcının bildirdiği çökme (ekran görüntüsündeki Frieren + "Fandom sayfasını" butonu) rapor #12'dir; #11 ise aynı hatanın galeri oturumundaki ikinci görünümüdür.

> 13:58:30'daki **NATIVE SIGSEGV** (v2.4.275, RenderThread/`libhwui.so` force-dark ailesi) farklı bir çökmedir ve bu raporun kapsamı dışındadır; `PLAN_TASK_CRASH_LOG_5_VE_TUM_COKMELER_COZUM_2026-10-13.md` Görev 1–4 ile ele alınmıştır.

### 1.2 Rapor #1'in ayrıntısı (native SIGSEGV — bu düzeltmenin konusu DEĞİL)
`crash_history` başındaki `=== Eski geçmiş kısaltıldı ===` bölümü: **13:58:30, pid 29747, v2.4.275-beta.29864801**, oturum başı 13:52:02, son canlı an 13:58:18, `libc: SIG_DFL handler for signal 11`, son ekran `Tab(tab=Profile)` / anlık görüntü `Galeri: My Deer Friend Nokotan (127 görsel)`, eylem izi `galeri gezinme: sayfa 2/47 → 12/47`. Logcat kanıtları ayrıca MIUI'nin `setViewRootImplForceDark: false … reason: AppDarkModeEnable` satırlarını ve `PerfMonitor doFrame` gecikmelerini içerir. Bu, Java istisnası olmayan **native** bir çökmedir (RenderThread/GPU/JNI ailesi).

---

## 2. Çökme İmzası (üç dosyada birebir aynı)

```
java.lang.IllegalStateException: Vertically scrollable component was measured with an
infinity maximum height constraints, which is disallowed. One of the common reasons is
nesting layouts like LazyColumn and Column(Modifier.verticalScroll()). ...

  at androidx.compose.foundation.CheckScrollableContainerConstraintsKt.checkScrollableContainerConstraints-K40F9xA
  at androidx.compose.foundation.ScrollNode.measure-3p2s80s          ← Modifier.verticalScroll düğümü
  at androidx.compose.ui.graphics.SimpleGraphicsLayerModifier.measure-3p2s80s   ← scrollable'ın kırpma katmanı
  at androidx.compose.foundation.layout.FillNode.measure-3p2s80s      ← fillMaxSize()
  ...
  at androidx.compose.foundation.layout.ColumnMeasurePolicy.measure-3p2s80s   ← ÜST Column
  at androidx.compose.foundation.layout.InsetsPaddingModifierNode.measure-3p2s80s ← windowInsetsPadding(navigationBars)
  at androidx.compose.foundation.layout.FillNode.measure-3p2s80s      ← fillMaxSize()
  ...
  at androidx.compose.ui.platform.AndroidComposeView.dispatchDraw     ← çizim sırasında (draw) patlıyor
```

### 2.1 Stack → kaynak kod eşlemesi (birebir)

Yukarıdaki zincir, uygulamada **tek bir yere** birebir oturur: uygulama içi wiki okuyucusu.

| Stack'teki düğüm | Kaynak kod |
|---|---|
| `ScrollNode` + kırpma `graphicsLayer` + `fillMaxSize` (`FillNode`) | `FandomWikiReader.kt:247` → `ReaderContent`: `Column(Modifier.fillMaxSize().verticalScroll(scrollState)…)` |
| Üst `ColumnMeasurePolicy` + `InsetsPaddingModifierNode` + `fillMaxSize` | `FandomWikiReader.kt:143-145` → okuyucunun `Column(Modifier.fillMaxSize().statusBarsPadding().windowInsetsPadding(WindowInsets.navigationBars))` |
| `Surface` + `graphicsLayer` (en dış) | `FandomWikiReader.kt:135-137` → `Surface(Modifier.fillMaxSize(), color = KitsugiColors.Background)` |

Doğrulama: `windowInsetsPadding(WindowInsets.navigationBars)` **uygulamada yalnızca** `FandomWikiReader.kt:145` satırında geçer; `wiki/` klasöründeki `verticalScroll` kullanımları ise `FandomWikiReader.kt:247` ve sınırlı yüksekliğe sahip `MediaWikiTabContent.kt:919` (`WikiItemSheet`, `heightIn(max=…)`) ile sınırlıdır. Yani çöken kaydırılabilir bileşen **kesin olarak okuyucunun gövdesidir**.

* **Thread:** `main` (FOREGROUND Ana UI Thread) → gerçek çökme, ANR değil.
* **Bellek:** 65 MB / 512 MB — OOM yok (`crash_log (7)`, `Önbellekler: bangumi.titleCache:538, ...`).
* **22:16:37 olayı:** son ekran `Galeri: Clannad: After Story (282 görsel)`, breadcrumb'lar `galeri gezinme: sayfa 6/71`.
* **22:18:55 olayı:** son ekran `ApiResultDetail(... Sousou no Frieren ... )`; yani kullanıcı Frieren detay sayfasında Wiki sekmesindeyken çöktü. Yeni oturum açılışında kurtarma kaydı bunu doğruluyor:
  `W KitsugiSession: Temiz olmayan kapanma tespit edildi (önceki pid=10436, ekran=ApiResultDetail(... Frieren ...))`.

---

## 3. KÖK NEDEN — Zincir (kanıtlı, satır satır)

### 3.1 Okuyucu, sayfa içeriğinin İÇİNE gömülü çiziliyordu
`FandomWikiReaderSheet`, çağıran sekmenin kompozisyon ağacına **doğrudan** giriyordu (ayrı pencere/sheet YOK):

* `wiki/MediaWikiTabContent.kt:182` → `FandomWikiReaderSheet(...)` (yapım detayı Wiki sekmesi; `MediaWikiTabSlot`)
* `wiki/CharacterWikiTabSlot.kt:125` → `FandomWikiReaderSheet(...)` (karakter/ekip wiki sekmesi)

Okuyucunun gövdesi kaydırılabilir bir sütundu:

* `wiki/FandomWikiReader.kt` (eski hâli) → `Surface(Modifier.fillMaxSize())` → `Column(fillMaxSize, windowInsetsPadding(navigationBars))` → `ReaderContent` → **`Column(Modifier.fillMaxSize().verticalScroll(scrollState))`**

### 3.2 Sekme içeriği SINIRSIZ yükseklik kısıtıyla ölçülüyor
`DetailPageScaffold.kt` içindeki portre düzen, sekme yüksekliğini doğal hâliyle ölçebilmek için pager'ı **bilerek** sınırsız ölçüyor:

```kotlin
// DetailPageScaffold.kt  (portre, item(key = "content"))
HorizontalPager(
    modifier = Modifier.fillMaxWidth()
        .layout { measurable, constraints ->
            val minPagerHeightPx = with(density) { (screenHeightDp - 64).dp.roundToPx() }
            val placeable = measurable.measure(
                constraints.copy(
                    minHeight = minPagerHeightPx,
                    maxHeight = androidx.compose.ui.unit.Constraints.Infinity   // ← SATIR 350
                )
            )
            ...
        }
) { page -> Box(Modifier.fillMaxWidth().heightIn(min = 600.dp)) { Column { pageContent(page) } } }
```

Yatay/TV düzeninde ise sekme içeriği zaten bir kaydırma kutusunun içinde:

```kotlin
// DetailPageScaffold.kt (yatay düzen, SATIR 212)
Box(Modifier.fillMaxSize().verticalScroll(pageScrollState).padding(...)) { pageContent(page) }
```

Aynı desen karakter/ekip sayfalarında da var:
`CharacterDetailPage.kt:432`, `StaffDetailPage.kt:404` → `Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()))` içinde `CharacterWikiTabSlot` (satır 467 / 664).

### 3.3 İkisinin kesişimi = çökme
Okuyucu bu ağacın içine gömülü çizildiğinde iki durumdan biri oluşur:

1. **Portre:** `Constraints.Infinity` yukarıdan aşağıya `fillMaxSize` ile iner → `verticalScroll` düğümü **sonsuz max height** alır → `checkScrollableContainerConstraints` istisna fırlatır.
2. **Yatay/TV:** Okuyucunun `verticalScroll`'ü başka bir `verticalScroll`'ün İÇİNE girer → Compose bunu da aynı istisnayla reddeder.

### 3.4 Neden TAM OLARAK o butona basınca çöküyor?
Buton: `WikiRelatedPageCard` (`FandomWikiReader.kt:596-660`) → `Text("Fandom sayfasını")` → `onClick = { onOpenItem(page.title) }` (`MediaWikiTabContent.kt:438-443`) → `viewModel.openReader(title)`.

`openReader` sırasıyla: `readerState = Loading` → okuyucu yalnızca üst çubuk + iskelet çizer (kaydırmasız, sorun yok) → ağdan sayfa meta verisi gelir → **`ReaderState.Ready`** → `MediaWikiTabSlot` içindeki `if (ready != null && readerState != Closed) { FandomWikiReaderSheet(...) }` (satır 178) devreye girer → `ReaderContent` kompoze edilir → **`verticalScroll` ilk kez ölçülür** → istisna.

Bu yüzden çökme, butona basıldıktan sonra **sayfa içeriği geldiği anda** oluşur; veri/ağ katmanında (JSON, WikiCode, HTTP) hiçbir hata yoktur (`logcat_at_crash` içinde 22:18:51'e kadar yalnızca zararsız 404/400 uyarıları vardır). Aynı sebeple:
* Wiki sekmesi ve "İlgili wiki sayfaları" şeridi sorunsuz çizilir (şeritte kaydırma **yatay**dır, yükseklik kısıtı önemsizdir),
* butona basılmadığı sürece uygulama saatlerce sorunsuz çalışır (rapor #12 öncesi oturum 123 sn, rapor #11 öncesi 440 sn — çökme yalnızca okuyucu açıldığında tetiklenir).

### 3.5 İkinci çökme (22:16:37 — Galeri) neden aynı sınıfta?
Stack **birebir aynıdır** (aynı düğümler, aynı sıra) → yine "sınırsız yükseklikle ölçülen bir `verticalScroll`" vakasıdır. İki olası tetik vardır ve ikisi de bu yamada kapatılmıştır:

1. **Okuyucunun kendisi:** Galeri bir `Dialog` olduğu için kapatıldığında "ekran" breadcrumb'ı güncellenmez; yani kullanıcı 22:16:28'de galeriyi kapatıp Clannad detay sayfasının Wiki sekmesinde "Fandom sayfasını" butonuna basmışsa, son kayıtlı ekran hâlâ `Galeri: …` görünür. Bu durumda #11, #12 ile **aynı** tetiktir.
2. **Galeri "Detaylar" levhası:** `KitsugiImageGalleryDialog.kt` içinde `verticalScroll` taşıyan **tek** bileşen `GalleryDetailsSheet`'tir (satır 1540) ve bu levha yükseklik korumasından yoksundu.

Her iki olasılık da kapatıldı: (1) okuyucu artık kendi penceresinde (sınırlı kısıt), (2) levhaya `heightIn(max = %85 ekran)` eklendi. Uygulamanın geri kalanında bu koruma zaten vardır: `KitsugiSheetOrDialog.kt:312` ve `wiki/MediaWikiTabContent.kt:918` (`WikiItemSheet`) → `Column(fillMaxWidth().heightIn(max = …).verticalScroll(…))`.

---

## 4. YAPILAN DÜZELTMELER

### 4.1 Birincil düzeltme — Okuyucu artık KENDİ penceresinde açılıyor
`FandomWikiReader.kt` → `FandomWikiReaderSheet` artık tam ekran bir `Dialog` içinde çiziliyor:

```kotlin
Dialog(
    onDismissRequest = { if (hasBackStack) onBack() else onClose() },
    properties = DialogProperties(
        usePlatformDefaultWidth = false,
        dismissOnBackPress = true,
        dismissOnClickOutside = false,
        decorFitsSystemWindows = false
    )
) {
    DisableDialogWindowForceDark()
    Surface(Modifier.fillMaxSize(), color = KitsugiColors.Background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().windowInsetsPadding(WindowInsets.navigationBars)) {
            ReaderTopBar(...)   // geri / kapat / tarayıcıda aç
            ReaderContent(...)  // verticalScroll — artık SINIRLI kısıtla ölçülür
        }
    }
}
```

* Dialog penceresi **her koşulda sınırlıdır** → `verticalScroll` sonsuz kısıt alamaz, iç içe geçemez ⇒ **çökme sınıfı tamamen ortadan kalkar**.
* KDoc zaten "**Tam ekran** uygulama içi wiki okuyucu" diyordu; bu düzeltme tasarımı da doğru hâline getirir (üst çubuk + kaydırılabilir gövde gerçekten tüm ekranı kaplar).
* Geri tuşu davranışı korundu: yığın varsa önceki wiki sayfasına döner, yığın boşsa okuyucuyu kapatır (`hasBackStack` mantığı üst çubukla birebir aynı).
* Durum çubuğu/gezinme çubuğu boşlukları (`statusBarsPadding` + `navigationBars`) eklendi; MIUI force-dark Dialog'da da kapatılıyor.
* **Tüm çağrı noktaları tek noktadan düzeldi:** yapım detayı (`MediaWikiTabSlot`), gömülü wiki kartı (`MediaWikiSectionCard`), karakter ve ekip sayfaları (`CharacterWikiTabSlot`).

### 4.2 Çift çizim temizliği
`CharacterDetailPage.kt` içinde okuyucu, wiki sekmesinin yanı sıra sayfa kökünde **ikinci kez** de çiziliyordu. Dialog'a geçişte bu iki pencere üst üste açılacağı için (geri tuşunun iki kez basılması gerekirdi) kök kopya kaldırıldı; okuyucuyu tek noktadan `CharacterWikiTabSlot` render eder. Kullanılmayan importlar da temizlendi.

### 4.3 İkincil sertleştirme — Galeri ayrıntı levhası
`KitsugiImageGalleryDialog.kt` → `GalleryDetailsSheet` içindeki kaydırılabilir sütuna, uygulamanın diğer tüm sheet'lerinde kullanılan koruma eklendi:

```kotlin
val maxContentHeight = LocalConfiguration.current.screenHeightDp.dp * 0.85f
...
Column(
    Modifier.fillMaxWidth()
        .heightIn(max = maxContentHeight)   // ← SINIRSIZ kısıt artık imkânsız
        .verticalScroll(rememberScrollState())
        ...
)
```

### 4.4 Altyapı
`KitsugiSheetOrDialog.kt` içindeki `DisableDialogWindowForceDark()` `private` → `internal` yapıldı; okuyucu penceresi de MIUI force-dark korumasını aynen kullanıyor.

---

## 5. Değişen Dosyalar

| Dosya | Değişiklik |
|---|---|
| `ui/screens/detail/wiki/FandomWikiReader.kt` | Okuyucu tam ekran `Dialog` penceresinde; insets + force-dark koruması; açıklayıcı KDoc |
| `ui/screens/detail/CharacterDetailPage.kt` | Kök seviyedeki ikinci okuyucu render'ı kaldırıldı (çift pencere önlendi) |
| `ui/components/KitsugiImageGalleryDialog.kt` | `GalleryDetailsSheet` kaydırma alanına `heightIn(max = %85 ekran)` koruması |
| `ui/components/KitsugiSheetOrDialog.kt` | `DisableDialogWindowForceDark()` `internal` (okuyucu ile paylaşım) |
| `app/build.gradle.kts` | Sürüm → **2.4.283** |
| `release_notes_v2.4.283.md`, `RELEASE_NOTES.md` | Sürüm notları (TR + EN) |

---

## 6. Doğrulama Adımları (cihazda yapılmalı — sandbox'ta JDK/Android SDK YOK)

1. Frieren (veya herhangi bir yapım) → **Wiki** sekmesi → "İlgili wiki sayfaları" → **"Fandom sayfasını"** → okuyucu tam ekran açılmalı, **çökmemeli**.
2. Okuyucuda: İçindekiler'den bölüm yükleme, "İlgili wiki sayfaları" kartına dokunma (sayfa değiştirme), üst bar **geri** (yığında geri dönüş) ve **kapat**, **tarayıcıda aç**.
3. Aynı akışı **karakter detayı → Wiki** sekmesinde ve **ekip (staff) detayı → Wiki** sekmesinde tekrarla.
4. Telefonu **yatay** çevirip (veya Android TV'de) yukarıdaki akışı tekrarla; ikinci bir kaydırma kabı içinde çökmemeli.
5. **Galeri:** bir yapımın galerisini aç → birkaç sayfa gez → **"Detaylar"** levhasını aç/kapat (5–10 kez) → çökme olmamalı.
6. Sürüm notları: `Ayarlar → Hakkında` üzerinde `2.4.283-beta.<timestamp>` görünmeli.

---

## 7. Kapsam Dışı / Açık İşler

* **Native SIGSEGV ailesi (v2.4.275, RenderThread/`libhwui.so`)** — bu raporun konusu değil; `PLAN_TASK_CRASH_LOG_5_VE_TUM_COKMELER_COZUM_2026-10-13.md` Görev 1–4 kapsamında ele alındı. Yeni bir native çökme gelirse ayrı rapor gerekir.
* **TV kumandası (D-pad) ile okuyucu kaydırma** — okuyucu artık tam ekran Dialog olduğu için TV'de kaydırma için `Modifier.dpadVerticalFastScroll(...)` bağlanması faydalı olur (bu düzeltmede çökme odaklı minimum değişiklik yapıldı; davranış öncekinden kötü değil).
* **Araçlar:** Tüm raporlar **tamamıyla** okundu (logcat/crash_history dosyaları `download_file` ile çalışma alanındaki `google_drive/` klasörüne indirilip bash ile satır satır incelendi; bu klasör `.gitignore`'da olduğu için depoya girmez). Sandbox'ta JDK/Android SDK bulunmadığından `./gradlew` **çalıştırılamadı**; Kotlin dosyaları `tree-sitter-kotlin` ile söz dizimi açısından doğrulandı (0 hata) ve derleme cihazda yapılmalıdır.
