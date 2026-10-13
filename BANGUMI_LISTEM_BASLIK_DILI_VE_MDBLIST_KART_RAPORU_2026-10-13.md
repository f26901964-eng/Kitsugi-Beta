# Bangumi "Listem" Başlık Dili + Harici Puanlar (MDBList) Kartı — Kök Neden ve Çözüm Raporu

**Tarih:** 2026-10-13 · **Sürüm:** v2.4.272 · **Durum:** Tamamlandı, CI doğrulandı

---

## 1. Bangumi "Listem" neden seçilen başlık diline uymuyordu?

### Kök neden
`GET /v0/users/{user}/collections` (Bangumi koleksiyon ucu) **kısaltılmış subject** döndürür:
yalnızca `name` (çoğu zaman Japonca/Çince özgün ad) ve `name_cn` vardır; **`infobox` yoktur**.
İngilizce (`英文名`) ve romaji (`罗马字`) adlar yalnızca subject detayında bulunur.

`BangumiImportManager` satırı şöyle yazıyordu:

```kotlin
val localizedTitle = BangumiNameLocalizer.entity(it.name, it.nameCn)   // infobox yok → alias yok
title       = localizedTitle.display                                   // = özgün (Japonca) ad
titleEnglish = localizedTitle.english                                  // = null
```

`BangumiNameLocalizer.resolve` içinde `romaji = explicitRomaji ?: nativeLatin ?: aliasLatin ?: explicitEnglish`
olduğundan ve `name` CJK olduğundan **hem romaji hem İngilizce `null`** kalıyordu.

Sonuç: `PreferenceHelpers.MediaEntry.getDisplayTitle("ROMAJI"|"ENGLISH")` hiçbir Latin aday bulamayıp
`raw` (CJK) değerine düşüyordu → liste her zaman Japonca adla çiziliyordu.
(Keşfet/arama raflarında `withLatinBangumiNames` vardı; **kütüphane satırlarında yoktu.**)

### Çözüm — 3 katman
| Katman | Dosya | Ne yapar |
|---|---|---|
| 1. Kalıcı tamamlama | `data/remote/BangumiLibraryTitleHydrator.kt` **(yeni)** | Kütüphanedeki Latin adı eksik Bangumi satırlarını bulur; `BangumiSubjectTitleResolver` ile (önbellek → subject infobox → AniList birebir eşleşme) çözer, sonucu **veritabanına** yazar. Doğrudan `dao.updateAll` kullanır → harici kaynaklara yazma denemesi tetiklenmez. |
| 2. Anlık okuma | `data/remote/BangumiTitleCache.kt` | Yeni `localizedTitleFor(rawId, currentTitle, titleLanguage)`: ROMAJI→romaji, ENGLISH→İngilizce, NATIVE→özgün ad. Aynı adı tekrar üretmez (`null` döner). |
| 3. Render güvenliği | `utils/PreferenceHelpers.kt` | `MediaEntry.getDisplayTitle`: Bangumi satırı ve çözülen ad hâlâ CJK ise önbellekten dile uygun ad kullanılır — **ağ isteği yok**, ekran bekletilmez. |

### Davranış sözleşmesi
- **Özgün ad kaybolmaz:** Latin ad ana başlığa yazılırken özgün ad `titleJapanese` alanına taşınır
  (yoksa oradaki değer korunur) → "Ana Dilde" seçeneği çalışmaya devam eder.
- **Tek seferlik maliyet:** Latin ad veritabanına yazıldığı için bir sonraki açılışta satır
  `pendingRequests` listesine hiç girmez; hiçbir ağ isteği yapılmaz.
- **Sınırlı yük:** En fazla 4 tur; her tur `BangumiSubjectTitleResolver`'in kendi eşzamanlılık
  (`Semaphore`) ve zaman aşımı bütçesine uyar. Yalnızca `source == "bangumi"` **ve** Latin adı
  gerçekten eksik satırlar hedeflenir.
- **Tetikleyici:** "Listem" ekranı (`MyListViewModel.init`) + `BangumiTitleCache.warmUp()`
  uygulama açılışında. Profil listesi de aynı önbellekten okur.

### Değişen dosyalar
- **yeni:** `app/src/main/java/com/kitsugi/animelist/data/remote/BangumiLibraryTitleHydrator.kt`
- `.../data/remote/BangumiTitleCache.kt` · `.../utils/PreferenceHelpers.kt`
- `.../ui/screens/mylist/MyListViewModel.kt` · `.../ui/screens/profile/BangumiProfileContent.kt`
- `.../KitsugiApplication.kt` (önbellek ısıtma)

---

## 2. Harici Puanlar (MDBList) kartı neden "uygulamaya yabancı" görünüyordu?

### Köken
Kart, tam doygun marka renkli bloklar (sarı IMDb, kırmızı RT, turuncu Audience…) kullanıyor ve
puanları **yatay kaydırılan** bir şeride diziyordu. Sonuç: uygulamanın `Surface` + vurgu rengi
diline tamamen zıt, kesilen ve okunması zor bir blok. Ek olarak MDBList, puanı olmayan kayıtlar
için `0` döndürdüğünden ekranda **"0 Metacritic"** gibi anlamsız kutular çiziliyordu.

### Çözüm
- **Yeni görsel dil:** Kart, "İstatistikler"/"Türler" bloklarıyla aynı (`RoundedCornerShape(22.dp)`,
  `KitsugiColors.Surface`, 16dp padding, `titleMedium` başlık). Her puan, marka rengine
  `alpha = 0.14` ile boyanmış yastıkta; skor **marka renginde büyük ve kalın** (18sp Black),
  etiket `TextSecondary`.
- **Izgara (kesilme yok):** Puanlar ekran genişliğine eşit dağılan satırlara bölünür
  (3→3, 4→4, 5→3+2, 6→3+3, 7→4+3); son satırın boş hücreleri `Spacer(weight)` ile doldurulur,
  hiçbir yastık gereksiz genişlemez.
- **0 = puan yok:** `MdbListClient.parseRatings` artık `withoutEmptyScores()` uygular
  (her iki yanıt biçiminde). Tüm puanlar 0 ise `isEmpty = true` → kart hiç çizilmez.
- **Açık tema uyumu:** `Color.forRatingSurface()` açık yüzeyde marka rengini siyaha doğru
  koyulaştırır; kontrast korunur.
- **İskelet:** `DetailRatingBadgesLoadingSkeleton` yeni ızgara geometrisine uyar → yükleme
  bitince düzen zıplamaz.
- **Kaynak metni yerelleştirildi:** "Harici Puanlar" artık `R.string.detail_external_ratings`
  (TR + EN).

### Değişen dosyalar
- `.../ui/screens/detail/ApiResultDetailComponents.kt` (kart + `MdbRatingItem` + ızgara)
- `.../ui/screens/detail/DetailLoadingSkeletons.kt` · `.../data/remote/MdbListClient.kt`
- `app/src/main/res/values/strings.xml` · `app/src/main/res/values-en/strings.xml`

---

## 3. Testler

| Test | Ne doğrular |
|---|---|
| `BangumiLibraryTitleHydratorTest` **(yeni)** | Hangi satırların çözüleceği: Latin adı olanlar atlanır, Bangumi olmayan/ID'siz satırlar dokunulmaz, aynı subject tek istek olur, `MediaType` AniList eşleşmesi için doğru haritalanır, özgün CJK ad korunur. |
| `MdbListClientTest` (genişletildi) | `0` puanlar "puan yok" sayılır; tüm puanlar 0 ise `isEmpty` doğru döner (kart çizilmez). |

Her iki sınıf CI derleme adımına filtre olarak eklendi (`.github/workflows/android-build.yml`).

---

## 4. Bonus: CI'da bulunan ve düzeltilen `main` kaynaklı derleme hatası

Derleme doğrulamasında `:app:compileFossDebugKotlin` **ICE (Internal compiler error)** veriyordu:

```
e: java.lang.StackOverflowError
  at androidx.compose.compiler.plugins.kotlin.inference.ApplierInferencer.restartable(...)
  at androidx.compose.compiler.plugins.kotlin.inference.ApplierInferencer.restartable(...)
  ... (yüzlerce kez)
```

**Kök neden:** Kod hatası **değil.** Geçici bisect ile kanıtlandı: `app/` kaynağı `main`'in
`6446f14` haline döndürülüp derlendiğinde de aynı SOE oluşuyor. Sorun,
`.github/workflows/android-build.yml` içindeki Kotlin daemon **yığın boyutunun** Compose
derleyicisinin applier-inference derin özyinelemesine yetmemesiydi.

**Düzeltme:** `org.gradle.jvmargs` ve `kotlin.daemon.jvmargs` değerlerine `-Xss16m` eklendi.
Ayrıca ICE durumunda yığın izini doğrudan bir check-run olarak yayınlayan bir teşhis adımı
eklendi (`compiler-stack-detail`).

**Sonuç:** `:app:testFossDebugUnitTest` (yeni testler dahil) + `:app:assembleFossDebug` → **başarılı**,
`kitsugi-foss-debug-apk` artifact'i üretildi.

---

## 5. Kullanıcı için not
- Romaji/İngilizce başlık tercihi ilk açılışta kütüphaneyi tarar; başlıklar birkaç saniye içinde
  Latin alfabesine döner ve **kalıcı** olur.
- Bir yapımın Latin/İngilizce karşılığı hiçbir kaynakta bulunamazsa satır özgün adıyla kalır
  (uydurma transliterasyon yapılmaz — bu bilinçli bir tercihtir).
