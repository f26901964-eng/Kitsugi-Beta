# PLAN / TASK: Bangumi "Listem" başlık dili (İngilizce) düzeltmesi

**Tarih:** 2026-10-20
**Sürüm:** v2.4.315 (önceki: v2.4.314)
**Durum:** Kod tamamlandı, CI'da derleme doğrulandı. Cihaz/birim testleri kullanıcıda.
**Kök neden raporu:** `KOK_NEDEN_RAPORU_BANGUMI_LISTEM_BASLIK_DILI_INGILIZCEYE_UYMUYOR_2026-10-20.md`
**Sürüm notu:** `release_notes_v2.4.315.md`

---

## Kullanıcı bildirimi

> "Bangumi listesi geldikten sonra bile yeni veya eski fark etmeksizin hâlen Japonca veya
> Çince ad ile geliyor, varsayılan dil İng seçili olmasına rağmen. Şu sorunu dile uyacak
> şekilde tanımlayıp düzeltir misin?"

Kanıt: Listem → Bangumi ekran görüntüsü (20 Eki 22:23). "717 Bangumi kaydı başarıyla
aktarıldı" bildirimi görünürken tüm kartlar Japonca/Çince (`王ドロボウJING`,
`東京ミュウミュウ`, `デュラララ!!`, `花咲くいろは`...). Tek Latin başlık `Angel Beats!`
çünkü onun Bangumi özgün adı zaten Latin.

## Sorunun tanımı (başlık dili sözleşmesi)

Kütüphane satırı başlığı 3 varyantla saklar: `title` (romaji/Latin ana ad),
`titleEnglish` (gerçek İngilizce ad), `titleJapanese` (özgün ad). Gösterim seçili dile göre:

| Başlık dili | Gösterim sırası |
|---|---|
| `ENGLISH` | İngilizce → romaji → özgün ad |
| `ROMAJI` | romaji → İngilizce → özgün ad |
| `NATIVE` | özgün ad → diğerleri |

Gösterim seçimi doğruydu; hata, varyantları dolduran hattaki 3 kusurun birleşimiydi.

## Kabul ölçütleri

- [ ] Başlık dili `ENGLISH` iken Bangumi "Listem" kartları (717 kayıt dahil) İngilizce,
      İngilizcesi yoksa romaji ile görünür; Japonca/Çince yalnızca hiçbir kaynakta Latin
      karşılığı bulunamayan kayıtlarda kalır.
- [ ] `ROMAJI` seçiliyken romaji, `NATIVE` seçiliyken özgün ad görünür (özgün ad kaybolmaz).
- [ ] Yeni içe aktarımda tamamlama aktarım biter bitmez başlar; uygulama kapatılıp açılmadan
      başlıklar aşamalı olarak seçili dile döner.
- [ ] Yeniden içe aktarım/senkron, daha önce tamamlanmış İngilizce/romaji adları Japonca/Çince
      ada geri döndürmez ("yeni veya eski fark etmeksizin" şikâyetinin tekrarı olmaz).
- [ ] Başlık dili ayarı değişince eksik varyantlar Listem'de tamamlanır.
- [ ] AniList/MAL/Simkl/Kitsu/Shikimori listelerinde gerileme olmaz (başlık koruma kuralı
      Latin adı olmayan satırlarda davranışı değiştirmez).

## Kök nedenler (özet — ayrıntı kök neden raporunda)

1. **KN-1 Tetikleyici eksik:** Latin ad tamamlayıcı (`BangumiLibraryTitleHydrator`) yalnızca
   `MyListViewModel.init`'te çalışıyordu. Aktarım Listem açıkken bitince yeni satırlar
   taramayı hiç görmüyordu.
2. **KN-2 Kuyruk tıkanması:** "Güvenilir İngilizce yoksa bekleyen" tanımı + her geçişte
   baştan `take(limit)` + "değişim yoksa taramayı bırak" kuralı, ilk ~40 romaji-only satırın
   arkasındaki yüzlerce kaydın (717) hiç denenmemesine yol açıyordu.
3. **KN-3 Ezme (clobber):** `smartImport` güncelleme kolu gelen CJK satırı aynen yazıyor;
   her yeniden aktarım tamamlanmış İngilizce/romaji adları siliyordu.

Ek eksikler: aktarımda kalıcı ad önbelleği kullanılmıyordu; ağsız (cacheOnly) geçiş hiç
çalışmıyordu; `ENGLISH` gösterim yedeği romaji gösterilirken önbellekteki İngilizceyi
kullanmıyordu; dil değişiminde tetik yoktu.

---

## TASK listesi

### T1 — Tamamlama motoru (baştan yazım) ✅
**Dosya:** `app/src/main/java/com/kitsugi/animelist/data/remote/BangumiLibraryTitleHydrator.kt`
- İki kademe: `LATIN eksik` (dilden bağımsız, öncelikli) + `İngilizce zenginleştirme`
  (yalnızca ENGLISH, tur başına max 2 dilim). Yeni saf API: `pendingRequestsByNeed(...)`,
  `pendingRequests(entities, titleLanguage)` geriye uyumlu.
- İlerleme garantisi: snapshot + 60'lık dilimler (çözümleyici limitleri AniList 96 /
  infobox 120 / TMDB 60'ı kapsar), max 16 dilim = 960 satır; erken çıkış kaldırıldı;
  her dilim ayrı DB transaction → arayüzde aşamalı dönüşüm.
- Önce ağsız `cacheOnly` geçişi. `Bangumi #<id>` yer tutucusu infobox yoluyla çözülür.
- `hydrate(database, titleLanguage)`; tek argümanlı sürüm `ENGLISH` varsayar.

### T2 — Yeniden aktarımda başlık koruma ✅
**Dosya:** `app/src/main/java/com/kitsugi/animelist/data/local/MediaEntryRepository.kt`
- Yeni saf fonksiyon `preserveResolvedTitles(existing, imported)`; `smartImport` güncelleme
  kolunda kullanıldı (yalnızca yükseltme: CJK gelen ad Latin mevcudu ezemez, Latin
  `titleEnglish` korunur, `titleJapanese`/özgün ad kaybolmaz).

### T3 — Aktarım anında önbellek ✅
**Dosya:** `app/src/main/java/com/kitsugi/animelist/data/auth/BangumiImportManager.kt`
- Yeni saf fonksiyon `resolveImportTitles(localized, cached, fallback)`; her satır
  `BangumiTitleCache` ile zenginleştirilerek yazılır (title=romaji, titleEnglish, titleJapanese).

### T4 — Tetikleyiciler ✅
- `app/.../ui/app/AuthViewModel.kt`: `launchBangumiTitleHydration()` eklendi;
  `importBangumiList` başarısı + çapraz senkron doğrulama aktarımı sonrasında çağrılır.
- `app/.../ui/screens/mylist/MyListViewModel.kt`: `refreshBangumiTitles(titleLanguage?)`
  (null ise `SettingsDataStore.settingsFlow.first().titleLanguage`); init bunu kullanır.
- `app/.../ui/screens/mylist/MyListScreen.kt`: `LaunchedEffect(appSettings.titleLanguage)`
  ile dil değişiminde yeniden tetikleme.

### T5 — Gösterim önbellek yedeği ✅
**Dosya:** `app/src/main/java/com/kitsugi/animelist/utils/PreferenceHelpers.kt`
- `bangumiCachedTitleOrNull`: ENGLISH'te "karşılandı" ölçütü = Latin `titleEnglish` varlığı;
  romaji yedeği gösteriliyorsa önbellekteki ayrı İngilizce ad kazanır.

### T6 — Sürüm + CI ✅
- `app/build.gradle.kts`: `appVersionName 2.4.314 → 2.4.315`.
- `release_notes_v2.4.315.md` (TR+EN), kök neden raporu.
- `.github/workflows/android-build.yml`: yeni test sınıfları filtreye eklendi; hata özeti
  adımına JUnit XML assertion ayrıntısı eklendi.

### T7 — Birim testleri (kullanıcı doğrulayacak) ⏳
- `BangumiLibraryTitleHydratorTest` (genişletildi): kademe sırası (LATIN önce), ROMAJI için
  romaji-only tamamlanmış sayılır/ENGLISH için zenginleştirme, NATIVE'de zenginleştirme yok,
  250 satırda kırpma yok, `Bangumi #id` yer tutucusu.
- `MediaEntryRepositoryTitlePreservationTest` (yeni): CJK yeniden aktarım Latin/İng adları
  ezemez; Latin aktarım kazanır; boş başlık/özgün ad koruma.
- `BangumiImportTitlesTest` (yeni): önbellek romaji/İngilizce aktarımda işlenir; önbelleksiz
  davranış aynı; yer tutucu fallback.
- `BangumiDisplayTitleLanguageTest` (yeni): ENGLISH→İngilizce (romaji yedek), ROMAJI→romaji,
  NATIVE→özgün; çözülmemiş satır özgün ad (hatanın ilk hali).

**Cihaz test senaryosu:**
1. Ayarlar → Başlık Dili = İngilizce. Listem → Bangumi: senkronla (717 kayıt). Uygulamayı
   kapatmadan bekle → başlıklar dilim dilim İngilizce/romaji olmalı (ilk ekran saniyeler içinde).
2. Tekrar senkronla → İngilizce başlıklar Japoncaya GERİ DÖNMEMELİ.
3. Başlık dili Ana Dil yap → özgün adlar; Romaji yap → romaji; tekrar İngilizce yap.
4. Bir Bangumi kartının detayına girip çık; listede başlık değişmemeli/kaybolmamalı.

### ⚠️ Bilinen durum (bu task dışı)
CI koşusunda **derleme başarılı**, yalnız önceden var olan 2 ilgisiz test düşüyor
(`malweb` paketine bu değişiklik dokunmadı — `git diff` ile doğrulandı, v2.4.314'ten beri
olduğu gibi):
- `MalNotificationsClientTest.acceptsOnlySameOriginNotificationContinuationUrls`
- `MalWebParserTest.followsOnlyObservedSameOriginNextPageLinks`
Kullanıcı isteğiyle test tarafı ona bırakıldı.

---

## Değişen dosyalar (zip içeriği)

```
.github/workflows/android-build.yml
app/build.gradle.kts
app/src/main/java/com/kitsugi/animelist/data/auth/BangumiImportManager.kt
app/src/main/java/com/kitsugi/animelist/data/local/MediaEntryRepository.kt
app/src/main/java/com/kitsugi/animelist/data/remote/BangumiLibraryTitleHydrator.kt
app/src/main/java/com/kitsugi/animelist/ui/app/AuthViewModel.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListScreen.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListViewModel.kt
app/src/main/java/com/kitsugi/animelist/utils/PreferenceHelpers.kt
app/src/test/java/com/kitsugi/animelist/data/auth/BangumiImportTitlesTest.kt
app/src/test/java/com/kitsugi/animelist/data/local/MediaEntryRepositoryTitlePreservationTest.kt
app/src/test/java/com/kitsugi/animelist/data/remote/BangumiLibraryTitleHydratorTest.kt
app/src/test/java/com/kitsugi/animelist/utils/BangumiDisplayTitleLanguageTest.kt
KOK_NEDEN_RAPORU_BANGUMI_LISTEM_BASLIK_DILI_INGILIZCEYE_UYMUYOR_2026-10-20.md
PLAN_TASK_BANGUMI_LISTEM_BASLIK_DILI_INGILIZCE_FIX_2026-10-20.md  (bu dosya)
release_notes_v2.4.315.md
```

Commit'ler (branch `arena/87545b5b-kitsugi-beta`):
- `516a396` fix(bangumi): Listem baslik dili Ingilizceye uymuyor - 3 kok neden (v2.4.315)
- `70278d7` ci: birim testi assertion ayrintisini (JUnit XML) hata ozetine ekle
