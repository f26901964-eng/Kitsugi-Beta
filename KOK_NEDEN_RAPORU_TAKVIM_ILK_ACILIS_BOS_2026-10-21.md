# KÖK NEDEN RAPORU — Haftalık Yayın Takvimi İlk Açılışta Boş Geliyor

Tarih: 2026-10-21
Dal: `arena/a2044ec1-kitsugi-beta`
Sürüm: v2.4.320

## Şikâyet (kullanıcı)

> "Yayın takvimi hangi kaynağa özgü olursa olsun, 'Tümü' kısmında dahi, ilk tıkladığımda veri olması gerekirken boş geliyor. 2.-3. defa tıklayınca (geri gelip tekrar girince) veri geliyor. Bir mantık hatası olmalı."

Ekran görüntüsünde: başlıkta toplam sayı yok, gün sekmelerinde rozet yok, gövdede
"Bu gün için yayın bulunamadı." mesajı — yani **`filteredScheduleMap` tamamen boş**,
hata durumu değil.

## Etkilenen dosyalar

| Dosya | Rol |
| --- | --- |
| `ui/screens/explore/KitsugiAiringCalendarScreen.kt` | Sunum: `filteredScheduleMap` türetimi (ASIL HATA) |
| `ui/screens/explore/KitsugiAiringCalendarViewModel.kt` | `weekSchedule` (`mutableStateMapOf`) + yükleme durumu |
| `data/remote/KitsugiAiringCalendarClient.kt` | AniList / TMDB haftalık takvim istekleri |
| `data/remote/KitsugiApiBase.kt` | AniList 429 cooldown kapısı |

## Kök neden 1 (asıl) — `remember` anahtarı yerinde değişen Map

`KitsugiAiringCalendarScreen` içinde:

```kotlin
val filteredScheduleMap = remember(viewModel.weekSchedule, showOnlyMyList, currentEntries, showAdultContent) {
    …
}
```

`weekSchedule` ViewModel'de `mutableStateMapOf<Int, List<AiringEntry>>()` olarak tanımlı ve
yükleme bittiğinde **aynı örnek üzerinde** güncelleniyor:

```kotlin
weekSchedule.clear()
weekSchedule.putAll(schedule)
```

Compose'un `remember(vararg keys)` mekanizması anahtarları **referans** üzerinden karşılaştırır;
aynı map örneği verildiğinde anahtar "değişmedi" sayılır. Dolayısıyla akış şu şekilde kilitleniyor:

1. Ekran ilk açılır → `weekSchedule` boş → `remember` **boş haritayı** hesaplayıp önbelleğe alır.
2. Ağdan veri gelir → ViewModel map'i yerinde doldurur → recomposition tetiklenir
   (`weekSchedule.isEmpty()` okuması sayesinde).
3. `remember` anahtarı aynı örnek olduğu için **önbellekteki boş harita** döner.
4. Ekran boş kalır: rozet yok, toplam sayı yok, "Bu gün için yayın bulunamadı."

Geri çıkıp tekrar girmek yeni bir composition oluşturduğu için `remember` bu kez dolu map ile
hesaplanır → kullanıcının "ikinci/üçüncü açılışta geliyor" gözlemi birebir bu davranışa karşılık gelir.

Kaynak (`preferredSource`) fark etmez: `filteredScheduleMap` her modda bu yolla türetiliyor.

### Çözüm

```kotlin
// Yerinde değişen map yerine içerik kopyası anahtar olarak kullanılır.
val scheduleSnapshot = viewModel.weekSchedule.toMap()
val filteredScheduleMap = remember(scheduleSnapshot, showOnlyMyList, currentEntries, showAdultContent) { … }
```

* `toMap()` snapshot okuması kaydeder → veri gelince recomposition garanti.
* Kopya, içerik değişince `remember`'ı geçersiz kılar (referans değil içerik karşılaştırması).

> Aynı sınıf hata `ExploreScreen`'de yok: orada modeller `mutableStateOf(list)` ile
> **yeni liste örneği** olarak atanıyor, bu yüzden `remember(viewModel.topAnime, …)` çalışıyor.
> Kural: `mutableStateMapOf` / yerinde mutasyon varsa `remember` anahtarı olarak map'in kendisi kullanılmamalı.

## Kök neden 2 — sessiz başarısızlık ("boş" ile "hata" karışıyordu)

* `KitsugiAiringCalendarClient.fetchAiringSchedule`, `executeAniListQuery` **null** döndüğünde
  `?: break` ile döngüyü kırıp **boş liste** döndürüyordu. `null` ise "veri yok" değil;
  AniList 429 cooldown'ı veya ağ hatası demek.
* `KitsugiApiBase.executeAniListQuery`, cooldown aktifken ağa çıkmadan `null` dönüyor.
  Keşfet ekranı AniList kotasını doldurduğu için takvim ilk açılışta bu duvara çarpıyordu.
* `parseTmdbList` tüm istisnaları `catch` içinde yutuyordu → TMDB/API anahtarı hatasında da
  takvim sessizce boş görünüyordu.

Sonuç: kullanıcıya "bugün yayın yok" deniyordu; oysa istek hiç yapılmamış ya da başarısız olmuştu.

### Çözüm

1. `fetchAiringSchedule(..., failOnFirstPageError = true)`: haftalık takvim yolunda **ilk sayfa**
   alınamazsa `IOException` fırlatılır → ekran `AiringErrorState` ("Takvim yüklenemedi" + Yeniden Dene).
   Sonraki sayfalar başarısız olursa eldeki kısmi veri korunur.
2. HTTP 200 + `errors` gövdesi dönen AniList yanıtları da hata olarak değerlendirilir.
3. `parseTmdbList` artık `Boolean` döner; 8 isteğin hiçbiri başarılı değilse hata fırlatılır.
4. "Tümü" modunda kaynaklar **ayrı ayrı** denenir: biri hata verse bile diğeriyle kısmi takvim
   gösterilir; yalnızca ikisi de boş + hatalıysa hata durumuna düşülür.
5. `KitsugiApiBase`: 8 saniyeye kadar olan kısa cooldown'lar beklenip istek gerçekten gönderilir
   (uzun cooldown'da fail-fast korunur). Bu, "ilk açılışta istek hiç atılmıyor" durumunu kaldırır.

## Kök neden 3 — bayat takvim

ViewModel, `weekSchedule.isNotEmpty()` olduğu sürece tekrar yükleme yapmıyordu; süreç ayakta
kaldıkça gün/hafta değişse bile eski takvim gösterilebiliyordu. Başarılı yükleme zamanı
(`lastLoadedAtMs`) tutulup **10 dakikalık TTL** eklendi: süre dolduğunda ekran açılışında arka
planda sessizce tazelenir; eski veri ekranda kaldığı için boş ekran görünmez.

## Doğrulama

* `git diff --check` benzeri denetim + değişen 4 Kotlin dosyası parantez/blok dengesi kontrolü: temiz.
* Gradle/Android derleme ve cihaz testi: bu ortamda JDK/Gradle toolchain yok → **yapılamadı**
  (önceki oturumlarda da aynı kısıt raporlanmıştı).
* Davranış doğrulaması için önerilen cihaz testi:
  1. Keşfet'i açıp birkaç kaynak sekmesine gir (AniList kotasını zorla).
  2. "Bu Hafta Yayında" kartından takvimi **ilk** açışta: gün rozetleri + toplam sayı dolu mu?
  3. Kaynak değiştir (AniList → TMDB → Tümü), her birinde ilk açılış dolu mu?
  4. Uçak modunda aç: "Bu gün için yayın bulunamadı" DEĞİL, "Takvim yüklenemedi + Yeniden Dene" görünmeli.
  5. Uçak modunu kapat, Yeniden Dene: veri gelmeli.

## Değişen dosyalar

* `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/KitsugiAiringCalendarScreen.kt`
* `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/KitsugiAiringCalendarViewModel.kt`
* `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiAiringCalendarClient.kt`
* `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiApiBase.kt`
* `app/build.gradle.kts` (sürüm 2.4.320)
* `release_notes_v2.4.320.md`, `RELEASE_NOTES.md`
