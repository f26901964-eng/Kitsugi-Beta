# Listem Başlık Animasyonu + Kategori Paneli + Uygulama Geneli Küre Yükleyici

**Tarih:** 2026-10-13 · **Dal:** `arena/7f55f039-kitsugi-beta` · **Önceki sürüm:** v2.4.277

Kullanıcı şikâyeti (ekran görüntüleriyle):

1. *"Listem sayfasındaki kısım aşağı kaydırdığımız anda pat diye gidip gelmemeli; diğer
   sayfalardaki gibi smooth, animasyonlu olmalı."* (resim 1–3)
2. *"Şu filtre açılır sayfasını [kategori panelini] alttan açılır 'Filtrele & Sırala'
   paneliyle tıpatıp aynı yap."* (resim 2 ↔ resim 3)
3. *"Bu resimdeki yüklenme animasyonu nerede olursa olsun, bu tarz dönen yüklenme
   animasyonlarının tamamının yerine geçsin."* (resim 4 — plazma ışık küresi)

---

## 1. Kök neden: başlık neden "pat" diye gidiyordu?

`MyListHeaderSection` (`ui/screens/mylist/MyListComponents.kt`) çökme durumunu iki **düz**
değerle taşıyordu:

```kotlin
val headerHeight = if (headerCollapsed) 0.dp else expandedHeaderHeight   // 60.dp / 0.dp
val collapseProgress = if (headerCollapsed) 1f else 0f                   // 0f / 1f
```

`animateDpAsState` / `AnimatedVisibility` yoktu. Yani tek karede 60.dp → 0.dp'ye atlıyordu;
üst bar kaybolurken liste de anında 60.dp yukarı zıplıyordu. "Geri gelme" de aynı şekilde
ani oluyordu. Ek olarak `MyListScreen` tarafında `showHeader` diye **ölü bir state** vardı:
scroll akışında yazılıyor, hiç okunmuyordu (çift kaynak riski).

### Çözüm

* Başlık, uygulamanın ortak hareket diline bağlandı: `KitsugiStickyHeaderReveal`
  (`KitsugiAnimations.kt` — `KitsugiMotion.stickyHeaderEnter/ExitMillis`, 260/200 ms,
  `FastOutSlowInEasing`). Yükseklik + soluklaşma + yukarı süzülme **birlikte** animasyonlanır;
  liste başlıkla birlikte akarak kayar.
* Karar tek yerden verilsin diye `rememberScrollAwareHeaderState(listState)` helper'ı eklendi
  (`KitsugiAnimations.kt`). Üst bar artık **jest yönüyle** çalışır (PluginPickerScreen'deki
  kalıp): `NestedScrollConnection.onPreScroll` içinde `available.y` okunur
  (aşağı → gizle, yukarı → göster). Konum farklarından okumadığımız için başlığın açılıp
  kapanmasının yarattığı liste kayması yeni bir "kaydırma" gibi algınıp **titreme üretmez**.
  Listenin ilk 120 px'inde (ölü bölge) başlık gizlenmez; tepeye dönülünce her zaman açılır.
* Ölü `showHeader` state'i ve `MyListHeaderSection` içindeki ikinci (çakışan) karar bloğu
  kaldırıldı; başlık görünürlüğü `headerCollapsed` parametresi olarak aşağı indiriliyor.
* Aynı davranış kullanıcının herkese açık listesine de (`KitsugiUserMediaListScreen`)
  verildi — iki sayfa artık aynı hissi veriyor.

**Dosyalar:** `KitsugiAnimations.kt`, `mylist/MyListComponents.kt`, `mylist/MyListScreen.kt`,
`profile/KitsugiUserMediaListScreen.kt`

---

## 2. Kategori paneli "Filtrele & Sırala" ile tıpatıp aynı yüzey dili

`KitsugiListStatusBottomSheet` (Tümü / İzleniyor / Tamamlandı / Planlanan / Durduruldu /
Bırakıldı / Yetişkin / Favoriler) düz bir `ModalBottomSheet` içinde, **başlıksız, çarpısız,
karartmasız** açılıyordu. Aynı ekrandaki filtre paneli ise `KitsugiPremiumSheet`
kullanıyordu; iki panel yan yana gelince görsel dil tamamen kopuyordu.

Panel artık doğrudan `KitsugiPremiumSheet` üzerine kurulu:

* gradyan sürükleme kulpu, ikon karosu + başlık ("Kategori") + aktif seçim rozeti + çarpı,
  aurora saç çizgisi, parlak kenarlık/gölge, karartılmış scrim ve yay fiziğiyle (damping 0.8)
  seken açılış — **filtre paneliyle birebir aynı bileşen**, dolayısıyla birebir aynı görünüm;
* satırlar seçiliyken filtre panelindeki seçili haplardaki gradyan dolguyu alıyor
  (soluk renk değil), adet sayısı sağda hap rozeti içinde;
* altta filtre paneliyle aynı eylem çifti: **Tümü** (sıfırlar) + **Bitti** (kapatır);
* etiketler elle yazılmış Türkçe metinler yerine `R.string.mylist_filter_*` kaynaklarından
  geliyor → "Planlanan/Planlandı" tutarsızlığı giderildi, İngilizce arayüzde çevrilir;
* seçim eskisi gibi anında uygulanır ve panel kapanır.

**Dosya:** `mylist/components/KitsugiListStatusBottomSheet.kt`

---

## 3. Uygulama geneli: dönen halka → ışık küresi

Resim 4'teki animasyon `KitsugiPlasmaLoader` (aynı sayfada fragman oynatıcısının içindeki
46 dp'lik küre). Uygulamada **57 ayrı yerde** hâlâ `CircularProgressIndicator` halkası
dönüyordu (30'u `ui.theme.gradient` sarmalayıcısı üzerinden, 27'si doğrudan material3'ten).

Yapılan:

1. **Tek noktadan bağlama:** `ui.theme.gradient.CircularProgressIndicator`
   (belirsiz/indeterminate overload) artık halka çizmiyor, `KitsugiPlasmaLoader`'ı çağırıyor.
   İmza aynen korundu (`color`, `strokeWidth`, `trackColor`, `strokeCap`), böylece hiçbir çağrı
   noktası bozulmadı. Boyut çağıranın `Modifier.size(...)` değeri; verilmemişse 40 dp.
2. **Doğrudan material3 kullanan 13 dosya** bu ortak sarmalayıcıya bağlandı (yalnızca import
   satırı değişti/eklendi) — oynatıcı tamponlama, wiki sekmeleri, Fandom görsel yükleme,
   eklenti satırları, P2P, depolama taraması, MAL profili, OAuth WebView, fragman overlay'i vb.
3. **Belirli (yüzdeli) göstergeler halka olarak kaldı:** indirme ilerlemesi
   (`KitsugiDetailEpisodesTab`) ve `LinearProgressIndicator`'lar — bunlar "yükleniyor" değil
   "şu kadar tamam" bilgisi veriyor.
4. **Köstebek koruması (performans):** bu 57 nokta liste satırlarında/rozetlerde aynı anda
   onlarca örneği çizilebiliyordu. plazma küresinde kare başına ~8 gradyan üretiliyordu ve
   repoda bunun GC/RenderThread çökmesine yol açtığı defalarca raporlandı. Önlemler:
   * renk döngüsü (6 sn) 32 adıma **kuantalandı** → gradyan renkleri her kare değil, adım
     değiştikçe kuruluyor; çekirdek nabzı (yarıçap) bilinçli olarak sürekli bırakıldı,
     çünkü onu kuantalamak kürenin nefes alma hareketini takılma yaptıracaktı;
   * **30 dp ve altı için kompakt çizim yolu** eklendi (kehribar hâle + akkor çekirdek +
     yörüngedeki parlak nokta = 3 gradyan, dönen poligonlar ve karışım modları yok);
   * çizim artık `size` parametresine değil **ölçülen kutuya** (`DrawScope.size`) bağlandı;
     küçük boyutlarda ışıma komşu satırlara taşmıyor;
   * kompakt boyutta alt lav gölge katmanı atlanıyor.
5. Kasıtlı olarak dokunulmayan: `KitsugiCrossSyncDialog` içindeki **dönen "Sync" ikonu** —
   o bir yüklenme placeholder'i değil, durum göstergesi (tamamlanınca ✓, hata varsa ⚠ oluyor).

**Dosyalar:** `KitsugiPlasmaLoader.kt`, `ui/theme/gradient/KitsugiGradientMaterial.kt` +
13 çağrı dosyası.

---

## Doğrulama

* Bu sandbox'ta JDK/Android SDK yok, derleme **CI**'da doğrulanıyor:
  `.github/workflows/android-build.yml` (`:app:assembleFossDebug` + foss debug APK artifact'i).
* `python3 scripts/check_localization_resource_parity.py` → 7378 dize, TR/EN senkron
  (yeni dize eklenmedi; mevcut `search_filter_category` ve `mylist_filter_*` kullanıldı).
* Tüm düzenlenen dosyalarda parantez/süslü parantez dengesi ve import tutarlılığı betikle
  kontrol edildi; `CircularProgressIndicator` çağrılarının tamamı artık tek sarmalayıcıdan geçiyor.

## Geri alma (tek dosyada)

Halka görünümüne dönmek için `KitsugiGradientMaterial.kt` içindeki indeterminate
`CircularProgressIndicator` gövdesini eski `material3` delegasyonuna çevirmek yeterli;
`KitsugiPlasmaLoader` değişiklikleri (kompakt yol + kuantalama) diğer 70 kullanımı da
olumlu etkilediği için korunabilir.
