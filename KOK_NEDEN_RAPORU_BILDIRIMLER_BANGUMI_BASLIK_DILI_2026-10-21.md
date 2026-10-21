# Kök Neden Raporu — Bildirimler → Bangumi Sekmesi Başlık Dili

**Tarih:** 2026-10-21 · **Sürüm:** v2.4.325 · **Durum:** Düzeltildi (kod)

---

## Kullanıcı bildirimi

> "Bangumi bildirim sayfası da aynı şekilde yine seçili dile uymalı [...] Banguminin her yerde
> ekstra bu dile uyması gerektiğini ben tek tek söylemek istemiyorum artık [...] düzelt şu
> banguminin nereye erişimi varsa animesi dizisi filmi karakteri ekibi adını varsa kesinlikle
> ing seçmişim ben ing gösterecek, seçili dile uymalı."

Ekli ekran görüntüleri: Bildirimler → Bangumi sekmesinde listelenen her kayıt ("İzleniyor",
"Bangumi izleme listesinde (在看)") Japonca/Çince başlıkla görünüyor; yalnızca yayın takvimiyle
eşleşen tek kayıt (`Hotaru no Yomeiri`, AniList takviminden gelen Latin başlıkla) doğru.

## Kapsam ve önceki çalışma

20 Ekim'de "Listem" ızgarası için aynı semptom kapsamlı biçimde çözülmüştü
(`KOK_NEDEN_RAPORU_BANGUMI_LISTEM_BASLIK_DILI_INGILIZCEYE_UYMUYOR_2026-10-20.md`) ve "Profil"
sekmesi (`BangumiProfileContent.kt`) zaten `BangumiNameLocalizer` + `BangumiTitleCache` +
`BangumiSubjectTitleResolver` üçlüsünü kullanıyor. Bu rapor kapsamında kod tabanı tarandı:

- `BangumiProfileContent.kt` (Profil sekmesi, koleksiyonlar + favoriler) → **doğru**, zaten
  seçili dile uyuyor.
- `CharacterDetailViewModel.kt` / `StaffDetailViewModel.kt` ("Karakterler"/"Yapımlar" listeleri)
  → arka planda `enrichCharacterDetailNames` / `enrichStaffDetailNames` ile zenginleştiriliyor;
  yalnızca Bangumi'de hiçbir yerde (infobox, AniList, TMDB) resmi İngilizce/romaji karşılığı
  bulunmayan nadir kayıtlar (ör. radyo programları, foto kitapları) özgün adında kalıyor — bu,
  önceki tasarım kararı gereği **bilinçli bir davranış** (uydurma transliterasyon yapılmaz).
- **`KitsugiNotificationsViewModel.kt` (Bildirimler → Bangumi)** → tek gerçek kusur burada
  bulundu.

## Kök neden

Bangumi `GET /v0/users/{username}/collections` ucu kısaltılmış `BangumiSlimSubject` döndürür:
yalnızca `name` (özgün, çoğunlukla Japonca) ve `name_cn` (Çince). `displayTitle` getter'ı
(`name.ifBlank { nameCn }`) bilinçli olarak "dil tercihi henüz uygulanmadığında güvenli
varsayılan" olarak belgelenmiş — yani çağıranın bunu başlık dili diline göre işlemesi beklenir.

`KitsugiNotificationsViewModel.loadBangumi()` bu `displayTitle`'ı doğrudan `NotifItem.title`'a
yazıyordu:

```kotlin
title = col.subject?.displayTitle ?: "Bangumi"
```

Hiçbir yerde `BangumiTitleCache` / `BangumiSubjectTitleResolver` / `BangumiNameLocalizer`
çağrılmıyordu — "Listem" ve "Profil" sekmelerinde zaten var olan dil çözümleme hattından
tamamen bağımsız bir kod yoluydu. Sonuç: takvime düşmeyen (yalnızca "izleniyor" durumundaki)
her kayıt, kullanıcının başlık dili tercihi ne olursa olsun her zaman özgün adıyla görünüyordu.

Takvime düşen (son 7 gün içinde yayınlanan bölümü olan) tek kayıt doğru görünüyordu çünkü o
dal `entry.title`'ı (AniList `airingSchedules` yanıtından) kullanıyor — bu kaynak zaten Latin
başlıkla geliyor.

## Düzeltme

`KitsugiNotificationsViewModel.kt`:

1. **`resolveBangumiWatchingTitles(watching, titleLanguage)`** (yeni, private suspend fun) —
   Profil sekmesiyle birebir aynı sözleşmeyi uygular:
   - Her kayıt için `BangumiNameLocalizer.entity(title)` ile dil-nötr temsile çevrilir.
   - `BangumiTitleCache.localizedTitleFor(rawId, base, titleLanguage)` ile kalıcı önbellekten
     anında bir karşılık aranır (ağ isteği yok).
   - Hâlâ CJK kaldıysa veya İngilizce seçiliyken güvenilir İngilizce yoksa,
     `BangumiSubjectTitleResolver.resolve(...)` ile (ön plan modunda, bildirimler ekranı zaten
     ağ bekliyor) çözülür; sonuç kalıcı önbelleğe yazılır.
   - **Önemli düzeltme detayı:** `BangumiTitleCache` her zaman **gerçek Bangumi `subject_id`**
     (raw) ile anahtarlanır; bu ekrandaki `stableId` ise uygulama genelindeki offset'li kimliktir
     (`BangumiIdNamespace`, +500.000.000). Önbellek çağrılarından önce
     `BangumiIdNamespace.rawIdOrNull(stableId)` ile dönüştürme yapılmazsa önbellek hep ıskalanır.
2. `loadBangumi()` artık `titleLanguage` parametresi alıyor (verilmezse `SettingsDataStore`'dan
   okunur) ve koleksiyon/yerel kayıt listesini oluşturduktan hemen sonra
   `resolveBangumiWatchingTitles` ile başlıkları yerelleştiriyor; takvime düşmeyen kayıtlar
   artık bu yerelleştirilmiş başlıkla kart oluşturuyor.
3. `KitsugiNotificationsScreen.kt` — her iki çağrı noktası (`LaunchedEffect` ilk yükleme +
   `refreshCurrentPage()`) `appSettings.titleLanguage`'i iletiyor; `LaunchedEffect` anahtarına
   `appSettings.titleLanguage` eklendi, böylece Ayarlar'da başlık dili değiştirildiğinde Bangumi
   sekmesi açıksa bildirimler kendiliğinden yeniden yüklenip doğru dilde yeniden çiziliyor.

## Değişen dosyalar

- `app/.../ui/screens/notifications/KitsugiNotificationsViewModel.kt`
- `app/.../ui/screens/notifications/KitsugiNotificationsScreen.kt`
- `app/build.gradle.kts` (sürüm 2.4.325), `release_notes_v2.4.325.md`

## Kapsam dışı bırakılanlar (bilinçli)

- "Karakterler"/"Yapımlar" listelerindeki, Bangumi'de hiçbir kaynakta (infobox/AniList/TMDB)
  resmi Latin karşılığı bulunmayan nadir ad/başlıklar (radyo programları, foto kitapları vb.)
  kasıtlı olarak özgün adında bırakılır — uydurma transliterasyon yanlış bilgi riski taşıdığı
  için önceki kök neden raporlarında bilinçli olarak reddedilmiştir.
