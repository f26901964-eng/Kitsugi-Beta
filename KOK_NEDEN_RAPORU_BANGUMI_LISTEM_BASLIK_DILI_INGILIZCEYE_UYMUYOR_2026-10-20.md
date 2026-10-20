# Kök Neden Raporu — Bangumi "Listem" Başlık Dili (İngilizce) Yok Sayılıyor

**Tarih:** 2026-10-20 · **Sürüm:** v2.4.315 · **Durum:** Düzeltildi (kod + birim testleri)

---

## Kullanıcı bildirimi

> "Bangumi listesi geldikten sonra bile yeni veya eski fark etmeksizin hâlen Japonca veya
> Çince ad ile geliyor, varsayılan dil İng seçili olmasına rağmen."

Ekran görüntüsü (20 Eki, 22:23): **Listem → Bangumi** ızgarasında `王ドロボウJING`,
`東京ミュウミュウ`, `デュラララ!!`, `花咲くいろは`, `カーニバル・ファンタズム`… Kartların
hemen üzerinde **"717 Bangumi kaydı başarıyla aktarıldı"** bildirimi görünüyor. Tek Latin
başlık `Angel Beats!` — çünkü onun Bangumi özgün adı zaten Latin alfabesinde.

Bu iki ayrıntı teşhisin anahtarıdır: (1) aktarım **Listem açıkken** yeni bitmiş,
(2) Latin özgün adlı kayıt doğru görünüyor → gösterim (render) katmanı sağlıklı,
sorun Latin/İngilizce başlık verisinin satırlara **hiç ulaşamaması / ulaşsa bile silinmesi**.

---

## Sorunun tanımı (başlık dili sözleşmesi)

Bangumi kütüphane satırı başlığı üç varyantla saklamalı ve gösterim bunlardan seçmelidir:

| Alan | Anlam |
|---|---|
| `title` | Romaji / Latin ana ad |
| `titleEnglish` | Gerçek İngilizce ad |
| `titleJapanese` | Özgün ad (Japonca/Çince) |

| Seçili başlık dili | Gösterim sırası |
|---|---|
| `ENGLISH` | İngilizce → romaji → özgün ad |
| `ROMAJI` | romaji → İngilizce → özgün ad |
| `NATIVE` | özgün ad → diğerleri |

İhlal, bu seçimde değildi; varyantların doldurulma hattındaki **üç kusurun birleşimiydi.**
Biri bile tek başına hatayı üretmeye yeterdi; üçü birlikte "asla düzelmiyor" hissini verdi.

---

## Kök nedenler

### KN-1 — İçe aktarım sonrası tamamlama hiç tetiklenmiyordu (yeni kayıtlar)

Bangumi koleksiyon ucu (`GET /v0/users/{user}/collections`) kısaltılmış条目 döndürür:
`name` + `name_cn`, **infobox yok**. İngilizce/romaji adlar bu yanıtta bulunmaz;
`BangumiLibraryTitleHydrator` bunları sonradan (AniList birebir eşleşmesi / subject
infobox'ı / TMDB) tamamlar.

Tamamlayıcının tek tetikleyicisi `MyListViewModel.init` idi. Kullanıcının akışı ise:
Listem **zaten açık** → Bangumi senkron düğmesi → `AuthViewModel.importBangumiList` →
"717 kayıt aktarıldı". ViewModel çoktan yaratıldığı için init taraması aktarılan
satırları hiç göremedi; aktarım sonrası da kimse taramayı yeniden başlatmıyordu.
Uygulama süreci ölene / ViewModel yeniden yaratılana kadar liste CJK kalıyordu.

### KN-2 — Tamamlayıcı kuyruk tıkanması: 717 kaydın arka sırası hiç denenmiyordu (tüm kayıtlar)

Uygulama yeniden açılsa bile tarama ilerleyemiyordu:

1. `pendingRequests`, "güvenilir İngilizce yoksa" satırı bekleyen sayıyordu. Romaji'si
   olup ayrı İngilizcesi bulunamayan satırlar (İngilizcesi romaji ile aynı olan çok
   sayıda yapım dahil) **sonsuza dek** bekleyen kaldı.
2. Çözümleyici her geçişte bekleyenleri başından `take(limit)` ile kırpıyordu
   (AniList 24/96, infobox 40/120). Aynı baş satırlar her turda bütçeyi yiyordu.
3. Tamamlayıcı, bir turda veritabanı değişimi olmadığında (`!applyResolvedTitles`)
   **tüm taramayı yarıda bırakıyordu**. Baştaki romaji-only satırlar değişim
   üretmediğinden ilk ~40 satır sonrası kalıcı olarak kilitliydi — her açılışta aynı
   noktada aynı iptal. Negatif önbellek bellek-içi olduğu için süreç yeniden
   başlayınca tıkanma baştan kuruluyordu.

### KN-3 — Her yeniden aktarım, tamamlanmış adları CJK ile eziyordu (eski kayıtlar)

`MediaEntryRepository.smartImport` güncelleme kolu gelen satırı aynen yazıyordu:

```kotlin
updates[match.id] = imported.copy(id = match.id, isAdult = ...).toEntity()
```

+18 işareti bilinçli olarak korunuyordu ama **başlıklar korunmuyordu**. Bangumi aktarımı
her seferinde `title = özgün CJK ad`, `titleEnglish = null` getirdiğinden, KN-2'ye rağmen
bir şekilde tamamlanmış satırlar bile bir sonraki senkronda başa dönüyordu.
Kullanıcının "yeni veya eski fark etmeksizin" ifadesinin karşılığı budur.

### Katkıda bulunan eksikler

- İçe aktarım, başka ekranlarda zaten öğrenilmiş Latin adları tutan kalıcı
  `BangumiTitleCache`'e bakmıyordu; önbellekten anında doldurulabilecek satırlar bile
  tamamlayıcıyı bekliyordu.
- Tamamlayıcının ağsız (`cacheOnly`) geçişi hiç kullanılmıyordu.
- Gösterimdeki önbellek yedeği (`PreferenceHelpers.bangumiCachedTitleOrNull`), Latin bir
  ad (romaji) gösteriliyorsa `ENGLISH` dilinde bile önbelleğe bakmıyordu; önbellekteki
  ayrı İngilizce ad DB'ye yazılana dek kullanılamıyordu.
- Başlık dili ayarlardan değiştiğinde hiçbir tarama tetiklenmiyordu.

---

## Düzeltme (v2.4.315)

### 1. Tamamlama motoru — `BangumiLibraryTitleHydrator` (baştan yazıldı)

- **İki kademe:** `LATIN eksik` (gösterim CJK'ya düşer; dilden bağımsız, öncelikli) ve
  `İngilizce zenginleştirme` (romaji var, ayrı İngilizce yok; yalnızca `ENGLISH`
  seçiliyken, tur başına sınırlı). Romaji, `ENGLISH` için geçerli bir yedektir;
  zenginleştirme kuyruğu tıkayamaz.
- **İlerleme garantisi:** bekleyenler anlık görüntü olarak alınır, çözümleyicinin tüm
  arka plan limitlerini kapsayan 60'lık dilimlerle, sonuçtan bağımsız olarak **sonuna
  kadar** işlenir (16 dilim = 960 satır > 717). "Değişim yoksa dur" kuralı kaldırıldı;
  her dilim ayrıca DB'ye yazılır → başlıklar ekranda aşamalı olarak Latin ada döner.
- **Ağsız ilk geçiş:** `cacheOnly` taraması daha önce öğrenilmiş tüm adları tek işlemde
  uygular.
- İçe aktarımın `Bangumi #<id>` yer tutucusu da infobox yoluyla çözülebilir duruma geldi.

### 2. Başlık koruma — `MediaEntryRepository.preserveResolvedTitles` (yeni)

`smartImport` güncelleme kolu artık başlıklarda yalnızca yükseltmeye izin verir:
gelen CJK ad, mevcut Latin adı ezemez; Latin `titleEnglish` gelmedikçe mevcut korunur;
özgün ad (`titleJapanese`) asla kaybolmaz (+18 korumasıyla aynı ilke).

### 3. İçe aktarımda önbellek — `BangumiImportManager.resolveImportTitles` (yeni)

Aktarılan her satır yazılırken kalıcı Latin ad önbelleğine bakılır; bilinen karşılıklar
satıra ilk andan işlenir (`title` = romaji, `titleEnglish`, `titleJapanese` = özgün ad).

### 4. Tetikleyiciler

| Olay | Tetik |
|---|---|
| Bangumi içe aktarımı bitti (`importBangumiList`) | `AuthViewModel.launchBangumiTitleHydration()` |
| Çapraz senkron doğrulama aktarımı bitti | Aynı |
| Listem ViewModel'i yaratıldı | `MyListViewModel.refreshBangumiTitles()` (dil ayardan okunur) |
| Başlık dili ayarı değişti | `MyListScreen` → `LaunchedEffect(titleLanguage)` |

Taramalar tamamlayıcı kilidiyle sıralanır; üst üste tetikleme mükerrer ağ işi üretmez.

### 5. Gösterim yedeği — `PreferenceHelpers.bangumiCachedTitleOrNull`

`ENGLISH` seçiliyken "karşılandı" ölçütü, gösterimin gerçek Latin `titleEnglish`
alanından gelmesidir; romaji yedeği gösteriliyorsa önbellekteki ayrı İngilizce ad kazanır.

---

## Değişen dosyalar

- `app/.../data/remote/BangumiLibraryTitleHydrator.kt` (baştan yazım)
- `app/.../data/local/MediaEntryRepository.kt` (`preserveResolvedTitles` + `smartImport`)
- `app/.../data/auth/BangumiImportManager.kt` (`resolveImportTitles` + önbellek kullanımı)
- `app/.../utils/PreferenceHelpers.kt` (İng önbellek yedeği ölçütü)
- `app/.../ui/app/AuthViewModel.kt` (aktarım sonrası tetik ×2 + yardımcı)
- `app/.../ui/screens/mylist/MyListViewModel.kt` (`refreshBangumiTitles`, dile duyarlı)
- `app/.../ui/screens/mylist/MyListScreen.kt` (dil değişimi tetikleyicisi)
- `app/build.gradle.kts` (sürüm 2.4.315), `release_notes_v2.4.315.md`, CI test filtreleri

## Testler

| Test sınıfı | Ne doğrular |
|---|---|
| `BangumiLibraryTitleHydratorTest` (genişletildi) | LATIN kademesi zenginleştirmeden önce gelir; romaji-only, ROMAJI için tamamlanmış sayılır ama ENGLISH için zenginleştirme adayıdır; NATIVE'de zenginleştirme çalışmaz; 250 satırlık seçim kırpılmaz; `Bangumi #id` yer tutucusu LATIN kademesine girer. (Eski testler aynen geçer.) |
| `MediaEntryRepositoryTitlePreservationTest` **(yeni)** | CJK yeniden aktarım çözülmüş Latin/İng adları ezemez; Latin aktarım yine kazanır; boş gelen başlık mevcudu bozmaz; özgün ad/arama terimi korunur. |
| `BangumiImportTitlesTest` **(yeni)** | Önbellekteki romaji/İngilizce aktarım anında satıra işlenir; önbelleksiz davranış eskisiyle aynı; eksik subject yer tutucuya düşer. |
| `BangumiDisplayTitleLanguageTest` **(yeni)** | ENGLISH→İngilizce (romaji yedeğiyle), ROMAJI→romaji, NATIVE→özgün ad; varyantlar boşken gösterim bilinçli olarak özgün adadır (hatanın görünür hali). |

Yeni sınıflar Android CI test filtresine eklendi (`.github/workflows/android-build.yml`).

## Kullanıcıya etkisi / doğrulama

- 717 kayıt aktarımı bittiği anda tamamlama başlar; başlıklar dilim dilim (saniyeler
  içinde ilk ekran) seçili dile döner, uygulama kapatılıp açılmasa bile.
- Bir yapım bir kez çözüldü mü adları kalıcıdır: veritabanı satırında + kalıcı ad
  önbelleğinde saklanır, sonraki aktarımlar **ezemez**.
- Hiçbir kaynakta Latin/İngilizce karşılığı bulunamayan (nadir) kayıtlar özgün adıyla
  kalır — uydurma transliterasyon yapılmaz (bilinçli davranış, korundu).
- Doğrulama: birim testleri + Android CI (`testFossDebugUnitTest` ilgili filtreler +
  `assembleFossDebug`).
