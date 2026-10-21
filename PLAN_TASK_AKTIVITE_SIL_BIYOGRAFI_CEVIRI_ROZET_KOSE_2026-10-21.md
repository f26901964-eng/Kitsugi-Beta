# Plan / Task — Aktivite Sil Butonu, Biyografi Otomatik Çevirisi, Takip Metni ve Rozet Köşeleri

**Tarih:** 2026-10-21
**Sürüm:** v2.4.337
**Durum:** Uygulandı (derleme/cihaz doğrulaması bu ortamda yapılamadı — aşağıya bakın)

## İstenenler

1. **Aktivite kartı:** Üç nokta (⋮) düğmesi ve tek satırlık "Sil" açılır paneli kalksın; yerine doğrudan sil butonu gelsin.
2. **Biyografi/açıklama çevirisi:** Kullanıcıların biyografisi, açıklaması vb. "otomatik çeviri" açıksa wiki sayfası çevirme mantığıyla çevrilsin.
3. **Takip butonu:** Takip edilen profilde "Takip Ediliyorsun" yerine doğru ifade kullanılsın (edilgen → etken).
4. **Rozetler:** Hangi Listem görünümü olursa olsun rozetler köşelere uygun biçimde yapışık olsun; görseli fazla kaplamasın.

## Uygulama

### 1) Aktivite silme
- `ProfileActivityComponents.kt` → `ActivityCard`:
  - `MoreVert` + `KitsugiDropdownMenu` + `KitsugiMenuItem` bloğu kaldırıldı.
  - Yerine 30dp `IconButton` + `Icons.Rounded.DeleteOutline` (18dp, `AccentRed %85`) eklendi; tıklama doğrudan `onDeleteClick(activityId)` çağırır.
  - `showMenu` durumu ve iki içe aktarım silindi. Onay penceresi çağrı yerinde (`KitsugiProfileScreen` → `AlertDialog`) korunuyor.

### 2) Biyografi otomatik çevirisi
- **Yeni:** `ui/components/KitsugiAutoTranslation.kt`
  - `AutoTranslationState(displayText, isLoading, hasTranslation, showingOriginal, toggleOriginal)`
  - `rememberAutoTranslatedText(text, autoTranslateEnabled)` — düz metin (Kitsu, MAL).
  - `rememberAutoTranslatedHtml(html, autoTranslateEnabled)` — HTML gövde (AniList, MAL HTML, Simkl).
  - Çeviri `TranslationManager` ile uygulama içinde; sonuç `remember` ile bellekte tutulur, tekrar istek atılmaz; `CancellationException` yeniden fırlatılır.
- **Yeni:** `utils/KitsugiHtmlTranslation.kt`
  - `extractTextSegments` / `rebuild` / `translateHtml`: yalnızca metin düğümleri çevrilir, etiketler/öznitelikler aynen korunur, `<script>/<style>/<code>/<pre>` atlanır, URL/entity/1 karakterlik parçalar çevrilmez.
  - Kısmi başarı: çevrilemeyen düğüm orijinal kalır; hiçbiri çevrilemezse `null` (hiç çeviri uygulanmaz).
- **Bağlanan ekranlar:**
  - `tabs/AniListInfoTab.kt` (kendi profili "Hakkında") — `autoTranslateEnabled`, `preferredTranslator` parametreleri; kompakt "Orijinal/Çeviri" çipi + yüklenme animasyonu.
  - `tabs/UserProfileInfoTab.kt` (başka kullanıcı "Hakkında") — aynı sözleşme; `KitsugiUserProfileScreen` ayarları geçirir.
  - `KitsuProfileContent.kt` (`KitsuAboutTab`), `ShikimoriProfileContent.kt` (`ShikimoriAboutTab`), `MalProfileExtras.kt` (`MalAboutExtras`, düz metin + HTML), `SimklProfileContent.kt` (biyografi).
- **Yan onarım:** `KitsugiHtmlWebView.kt` → `AndroidView` artık `update` bloğunda HTML değişimini yakalayıp `loadDataWithBaseURL` ile yeniden yüklüyor. Önceden yalnızca ilk veri yükleniyordu; çeviri ekrana yansımazdı.

### 3) Takip metni
- `res/values/strings.xml` → `profile_following_button`: `Takip Ediliyorsun` → `Takiptesin`. İngilizce (`Following`) değişmedi; `profile_follows_you` ("Sizi takip ediyor") korundu.

### 4) Rozet köşeleri
- `ui/components/KitsugiMediaEntryCard.kt`:
  - `PosterCornerTag` ortak bileşeni: 9sp, dar padding, `widthIn(max = 108.dp)`, tek satır ellipsis, `shape` parametreli.
  - `TopEndCornerShape` / `BottomEndCornerShape`: dış köşe düz (postere yapışık), iç köşeler 6dp.
  - Izgara (`grid_2col`), büyük (yatay + dikey poster) görünümlerinde durum rozeti sağ üst köşeye, puan rozeti sağ alt köşeye tam yapışık; favori yıldızı sol üst köşeye yapışık.
  - `PosterView` (kompakt/rahat/minimalist) kaynak rozeti poster genişliğine göre 16/18/22dp; öncelik rozeti sağ üst köşeye yapışık.
  - Artık kullanılmayan `PosterBadgeInset` kaldırıldı.

### 5) Sürüm
- `app/build.gradle.kts` → `appVersionName = "2.4.337"`.
- `release_notes_v2.4.337.md` ve `RELEASE_NOTES.md` güncellendi.

## Testler
- `app/src/test/java/com/kitsugi/animelist/utils/KitsugiHtmlTranslationTest.kt` eklendi (9 test):
  - metin düğümü sırası, script/style/code atlama, kısa/URL/entity atlama,
  - `rebuild` etiket koruması ve eksik çeviri davranışı,
  - `translateHtml` tam/kısmi/başarısız senaryoları.

## Doğrulama sınırları
- Bu ortamda **JDK ve Android SDK yok**; `./gradlew` çalıştırılamadı, APK üretilmedi, birim testleri koşulmadı.
- Kotlin dosyalarında parantez/süslü parantez dengesi betikle denetlendi; derleyici doğrulaması yapılmadı.
- Görsel doğrulama (açık/koyu tema, TV odağı, dar ekran) cihazda yapılmalıdır.
