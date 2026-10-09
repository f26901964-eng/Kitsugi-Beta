# Fragman Zinciri, Buton Hizası, Çarpı Butonları ve Geri Tuşu Raporu — 2026-10-09

Kapsam: kullanıcı bildirimleri (detay sayfası fragmanları, İzle butonu hizası, oynatıcı içi/dışı
çarpı butonları, cihaz geri tuşunun oynatıcıda algılanmaması).

---

## 1. Detay sayfalarına çok kaynaklı YouTube fragman zinciri

**Yeni dosya:** `app/src/main/java/com/kitsugi/animelist/data/trailer/DetailTrailerFallback.kt`

Detay sayfası hangi kaynaktan açılırsa açılsın (AniList, MAL/Jikan, Kitsu, Bangumi, Shikimori,
Simkl, TMDB) fragman kartının dolması için yedek çözümleme zinciri eklendi. Zincir yalnızca
**kaynağın kendisi fragman vermediyse** (`KitsugiMediaDetail.trailerUrl` boşsa) çalışır:

1. **Kaynağın kendisi** — mevcut istemciler `trailerUrl` doldurur (değişiklik yok).
2. **TMDB (diğer kaynaklar)** — `TrailerService.getExternalTrailerUrl` (TR-öncelikli YouTube
   anahtarı, ID bazlı, hızlı).
3. **Diğer metadata kaynakları** — AniList / Jikan(MAL) / Kitsu istemcilerine kimlik bazlı
   **paralel** sondaj (arama yok; yalnızca elde var olan ID'ler, 9 sn tavan).
4. **Eklentiler (Cloudstream)** — `CsStreamRunner.searchAllAddons` başlık araması + en iyi 3
   eşleşmede `safeLoad` → `LoadResponse.trailers[].extractorUrl` (25 sn tavan, en pahalı adım
   olduğu için en son koşulur).

- Yalnızca YouTube oynayabilir URL'leri kabul edilir (`isYouTubeTrailer`: watch / youtu.be /
  embed / shorts / bare-ID).
- Sonuçlar (boş dönenler dahil) bellek içi önbelleğe yazılır; aynı yapım için zincir tekrar
  koşmaz. Negatif önbellek `containsKey` ile ayrıştırılır.
- Entegrasyon: `ApiResultDetailViewModel.fetchDetail` ve `MediaEntryDetailViewModel.fetchDetail`
  içinde `viewModelScope.launch` ile arka planda; bulunan URL `_detailState`'e `copy(trailerUrl=)`
  ile yazılır ve `DetailCache`'e.persist edilir → kart (`KitsugiTrailerCard`) kendiliğinden belirir.
- Hiçbir aşama exception fırlatmaz; fragman yoksa sayfa eski davranışını korur.

## 2. İzle / Oku butonunun diğer aksiyon butonlarıyla hizalanması

**Dosya:** `ui/components/KitsugiUiverseGlowButton.kt` → `KitsugiDetailActionButton`

Ekran görüntülerindeki uyumsuzluk: İzle/Oku (`KitsugiDetailActionButton`) 16dp köşeli, 20x14dp
dolgulu, 16sp yazılıyken; Düzenle / MAL'da Gör / +1 bölüm / Favori Yap / Kaynakta Aç / Sil
(`ApiActionButton` / `ActionButton`) 999dp hap formunda, 14x10dp dolgulu, `labelMedium` yazılıydı.

Yeni biçim (tek aile): `RoundedCornerShape(999.dp)` + `padding(14.dp, 10.dp)` +
`MaterialTheme.typography.labelMedium` + 16dp ikon. Tüm kullanım noktaları (Api/Entry detay sol
paneli, QuickActions, Manga detay, boş/hata durumları) otomatik olarak hizalanır.

## 3. Medya player DIŞINDAKİ son eklenen çarpı butonlarının kaldırılması

**Dosya:** `ui/components/KitsugiSheetOrDialog.kt`

v2.4.215'te eklenen evrensel yüzen çarpı (`showCloseButton` + `KitsugiSheetCloseButton`;
BottomSheet / tam ekran Dialog / TV Dialog dallarının üçü) **tamamen kaldırıldı**:
- `showCloseButton` parametresi ve 3 render bloğu silindi (hiçbir çağrı noktası parametreyi
  geçmiyordu — doğrulandı).
- `KitsugiSheetCloseButton` composable'ı ve artık kullanılmayan import'lar silindi.
- Sheet'lerin kendi özgün başlık çarpıları (ör. Aktivite sheet başlığı) tasarımın parçasıdır,
  korunmuştur; kaldırılan yalnızca sonradan eklenen yüzen katmandı.
- Çıkış garantisi kaybolmadı: BottomSheet dalında `BackHandler`, Dialog dallarında
  `dismissOnBackPress` mevcut; ayrıca bkz. madde 5.

## 4. Medya player İÇİNDE çarpısı olmayan açılır sayfalara çarpı eklenmesi

Yalnızca çarpısı olmayanlara eklendi:

- **`PlayerSheet.kt` (tüm oynatıcı alttan açılır sayfaları):** sürükleme kolu satırına sağa
  hizalı kapatma çarpısı eklendi → Altyazılar, Ses Parçaları, Kalite/Kaynak, Daha Fazla,
  Bölümler, Oynatma Hızı, Ekran Görüntüsü ve jenerik track sheet'lerinin hepsi tek dokunuşla
  kapanır (hiçbirinde çarpı yoktu; panellerde zaten olduğu için panellere dokunulmadı).
- **`PlayerDialogsHost.kt` → `EpisodeListDialog`:** başlık satırına çarpı eklendi (geri tuşu
  dışında kapanışı yoktu).
- **`components/QualityProfileDialog.kt`:** sağ üste çarpı eklendi.
- `IntegerPickerDialog` AlertDialog olduğu için (İptal/Tamam butonları mevcut) kapsam dışı.

## 5. Oynatıcının cihaz geri tuşunu yutmaması (jest + 3 tuş + donanım)

**Dosya:** `ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt`

Kök neden: jest tabanlı gezinmede sistem `KEYCODE_BACK` **üretmez**; doğrudan
`OnBackPressedDispatcher` çalışır. Compose'taki `onPreviewKeyEvent(Key.Back)` işleyicisi ise
yalnızca odak Compose'tayken/donanım tuşlarında devreye girer; odak MPV yüzeyinde veya başka
düğümlerdeyken geri tuşu hiçbir zincire düşmüyordu.

Çözüm: Activity'ye dispatcher seviyesinde `OnBackPressedCallback` eklendi — deterministik zincir:
`dialog → panel → sheet → kontrolleri gizle → oynatıcıdan çık`. PiP modunda zincir by-pass edilir
(kararı sistem verir). Zincir tükendiğinde callback kendini geçici devre dışı bırakıp olayı
Activity'nin kendi kapanışına bırakır. Compose'taki TV/donanım tuşu işleyicisi aynı zinciri
yansıttığı için davranış her giriş yolunda tutarlıdır.

---

## Doğrulama (dürüst beyan)

Bu sandbox'ta JDK/Android SDK yok (`java: command not found`, `ANDROID_HOME` boş); derleme
çalıştırılamadı. Statik doğrulamalar:

- Düzenlenen/eklenen 9 dosyada `{}`, `()`, `[]` dengesi: hepsi eşit.
- Yeni import'lar kontrol edildi (PlayerSheet: Icon/IconButton/Icons/Close; PlayerDialogsHost:
  IconButton/Icons/Close; Activity: OnBackPressedCallback; UiverseGlowButton: MaterialTheme,
  kullanılmayan `sp` import'u silindi; KitsugiSheetOrDialog: 5 kullanılmayan import silindi).
- `KitsugiSheetCloseButton` / `showCloseButton` referansı kod tabanında kalmadı (grep).
- `DetailTrailerFallback` erişimleri modül içi görünürlükle uyumlu (`internal object` istemciler,
  `internal suspend fun safeLoad` aynı modülde).

**Cihazda test edilecekler:** detay sayfalarında fragman kartının kaynak fark etmeksizin dolması;
İzle butonunun diğer butonlarla aynı hizada durması; player dışı sheet'lerde yüzen çarpının
kaybolması; player sheet'lerinde çarpının çıkması; jest ve 3 tuşlu gezinmede geri tuşunun
önce sheet/panel/dialogu, sonra kontrolleri, sonra oynatıcıyı kapatması.
