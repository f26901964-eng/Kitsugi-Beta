# PLAN_TASK — Listem başlık animasyonu + kategori paneli + uygulama geneli küre yükleyici

**Tarih:** 2026-10-13 · **Sürüm aralığı:** v2.4.277 → (yayınlanacak) · **Dal:** `arena/7f55f039-kitsugi-beta`
**Kullanıcı isteği:** (1) Listem üst barı kaydırınca "pat diye" gidip gelmesin, diğer sayfalar gibi smooth/animasyonlu olsun; (2) kategori açılır paneli "Filtrele & Sırala" alttan açılır paneliyle tıpatıp aynı olsun; (3) resimdeki yüklenme animasyonu (plazma ışık küresi) nerede varsa tüm dönen yüklenme animasyonlarının yerine geçsin.

Kök neden analizi ve detaylı teknik açıklama: `LISTEM_BASLIK_ANIMASYONU_KATEGORI_PANELI_VE_KURE_LOADER_RAPORU_2026-10-13.md`

---

## Görev listesi

### T1 — Listem başlığı: animasyonsuz çökme (pat diye gitme)
- [x] `MyListHeaderSection`: `headerHeight = 60.dp/0.dp` + `collapseProgress = 0f/1f` düz değerleri → `KitsugiStickyHeaderReveal` (260 ms giriş / 200 ms çıkış, `FastOutSlowInEasing`)
- [x] Karar tek yerden: yeni `rememberScrollAwareHeaderState(listState)` helper (`KitsugiAnimations.kt`) — `NestedScrollConnection.onPreScroll` ile **jest yönü** okunur (aşağı → gizle, yukarı → göster), ilk 120 px ölü bölge, tepeye dönüşte her zaman açık
- [x] Bölüm içindeki ikinci (çakışan) `headerCollapsed` LaunchedEffect bloğu kaldırıldı → `headerCollapsed: Boolean` parametresiyle dışarıdan besleniyor
- [x] `MyListScreen`'deki ölü `showHeader` state'i ve yazımı silindi (çift kaynak riski bitti)
- [x] Kullanıcının herkese açık listesi (`KitsugiUserMediaListScreen`) aynı davranışa bağlandı
- **Kabul:** aşağı kaydırınca bar süzülerek çıkıyor, yukarı kaydırınca süzülerek geri geliyor, liste zıplamıyor, titreme yok

### T2 — Kategori paneli = premium alt panel dili
- [x] `KitsugiListStatusBottomSheet`: `KitsugiSheetOrDialog` → `KitsugiPremiumSheet` (kulp + ikon karosu + başlık + rozet + çarpı + aurora çizgisi + scrim + yay açılışı)
- [x] Satırlar: seçiliyken filtre panelindeki seçili haplarla aynı gradyan dolgu; adet sağda hap rozeti
- [x] Alt eylem çifti: **Tümü** (sıfırla) + **Bitti** (kapat) — filtre paneliyle aynı düzen/ölçüler
- [x] Etiketler elle Türkçe metin yerine `R.string.mylist_filter_*` (Planlanan/Planlandı tutarsızlığı kapandı, EN çeviri geldi)
- [x] Davranış korundu: seçim anında uygulanır ve panel kapanır; TV'de ortalanmış dialog
- **Kabul:** yan yana bakıldığında iki panel aynı bileşenden çıkmış gibi (kullanıcı "tıpatıp aynı" istedi)

### T3 — Uygulama geneli: dönen halka → ışık küresi
- [x] `ui.theme.gradient.CircularProgressIndicator` (indeterminate overload) → `KitsugiPlasmaLoader` delegasyonu; imza korundu, çağrılar bozulmadı
- [x] Doğrudan material3 kullanan **13 dosya / 23 çağrı noktası** ortak sarmalayıcıya bağlandı (yalnızca import satırı)
- [x] Betikle doğrulama: **40 dosyada 52** belirsiz çağrının 52'si ortak sarmalayıcıda, material3'ten çizen **0**
- [x] Korunanlar: determinate (yüzdeli) indirme halkaları, `LinearProgressIndicator`lar
- [x] Perf/GC koruması: renk döngüsü 32 adıma kuantalama; **≤30 dp için kompakt çizim yolu** (3 gradyan, karışım modu yok); çizim `size` parametresi yerine ölçülen kutuya bağlı (küçük rozetlerde taşma bitti)
- [x] Kasıtlı dokunulmayan: `KitsugiCrossSyncDialog` dönen Sync ikonu (durum sembolü), pull-to-refresh bileziği (jest göstergesi — ayrı karar)
- **Kabul:** oynatıcı tamponlama, wiki sekmeleri, eklenti satırları, depolama taraması, MAL profili, OAuth WebView, fragman overlay'i, sheet'ler → hepsi küre

---

## Doğrulama
- CI `🏗️ Android Derleme Doğrulama` → **success**: `:app:testFossDebugUnitTest` + `:app:assembleFossDebug` (foss debug APK artifact) — run `38402253102`
- `scripts/check_localization_resource_parity.py` → TR/EN 7378 dize senkron (yeni dize eklenmedi)
- Betik kontrolleri: parantez/süslü denge, `CircularProgressIndicator` import/çözümleme tutarlılığı
- Bu ortamda emulator/Android SDK olmadığı için **görsel doğrulama cihazda yapılmalı**

## Kalan / opsiyonel
- [ ] Sürüm bump (2.4.278) + `release_notes_v2.4.278.md`
- [ ] Pull-to-refresh göstergesinin de küreye çevrilmesi (`PullToRefreshBox` → `indicator` slotu)
- [ ] Cihazda animasyon hızı/büyüklük ince ayarı (başlık süresi, küre boyutu, satır yoğunluğu)

## Commit'ler
```
b8046fd docs(ui): raporda yüklenici sayımlarını betikle doğrulanmış değerlerle güncelle
effad3a docs(ui): rapora CI doğrulaması ve kapsamdışı notlar eklendi
717b57c perf(ui): nabız kuantalamasını geri al, renk döngüsünü 32 adıma çıkar
2a743d1 fix(ui): animasyonlu Listem başlığı, premium kategori paneli ve plazma küresi yükleyici
```
