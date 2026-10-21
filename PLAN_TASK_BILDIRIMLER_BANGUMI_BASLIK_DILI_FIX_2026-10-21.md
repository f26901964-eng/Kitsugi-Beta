# Plan / Task — Bildirimler → Bangumi Sekmesi Başlık Dili Düzeltmesi

**Tarih:** 2026-10-21 · **Sürüm:** v2.4.325 · **Durum:** Tamamlandı

---

## Kullanıcı bildirimi

> "Bangumi bildirim sayfası da aynı şekilde yine seçili dile uymalı [...] Banguminin her yerde
> ekstra bu dile uyması gerektiğini ben tek tek söylemek istemiyorum artık [...] düzelt şu
> banguminin nereye erişimi varsa animesi dizisi filmi karakteri ekibi adını varsa kesinlikle
> ing seçmişim ben ing gösterecek, seçili dile uymalı."

Ekran görüntüleri: Bildirimler → Bangumi sekmesindeki tüm kayıtlar (İzleniyor / Bangumi izleme
listesinde) Japonca/Çince başlıkla görünüyor; yalnızca yayın takvimiyle eşleşen tek kayıt
(`Hotaru no Yomeiri`) doğru (Latin) başlıkla geliyor.

## Hedef

Bildirimler ekranının Bangumi sekmesi, uygulamanın geri kalanıyla (Listem, Profil) **aynı**
başlık dili sözleşmesine uysun: kullanıcı İngilizce/Romaji/Özgün hangisini seçtiyse, Bangumi'den
gelen her anime/dizi/film adı o dilde (mümkünse) gösterilsin.

## Kapsam taraması (yapıldı)

| Ekran | Durum | Not |
|---|---|---|
| Listem (MyList) | ✅ Zaten doğru | 2026-10-20 tarihli ayrı düzeltmede ele alındı |
| Profil (BangumiProfileContent) | ✅ Zaten doğru | `BangumiNameLocalizer` + `BangumiTitleCache` + `BangumiSubjectTitleResolver` kullanıyor |
| Karakter detay ("Yapımlar") | ✅ Büyük ölçüde doğru | `enrichCharacterDetailNames` arka planda zenginleştiriyor; yalnızca hiçbir kaynakta resmi Latin karşılığı olmayan nadir kayıtlar özgün kalıyor (bilinçli tasarım) |
| Ekip/Kişi detay ("Karakterler"/"Yapımlar") | ✅ Büyük ölçüde doğru | `enrichStaffDetailNames` aynı mekanizmayı kullanıyor |
| **Bildirimler → Bangumi** | ❌ **Bulunan tek gerçek kusur** | `col.subject?.displayTitle` / yerel `me.title` doğrudan karta yazılıyordu, hiçbir dil çözümlemesi yoktu |

## Yapılan değişiklikler

1. **`KitsugiNotificationsViewModel.kt`**
   - Yeni `resolveBangumiWatchingTitles(watching, titleLanguage)` (private suspend fun):
     Profil sekmesiyle birebir aynı sözleşmeyi uygular — önce kalıcı `BangumiTitleCache`'den
     anında karşılık, hâlâ gerekiyorsa `BangumiSubjectTitleResolver` ile ön planda çözüm.
   - `BangumiIdNamespace.rawIdOrNull(stableId)` ile önbellek anahtarlama hatası (stable ID ≠
     gerçek Bangumi `subject_id`) düzeltildi.
   - `loadBangumi()` artık `titleLanguage` parametresi alıyor (verilmezse `SettingsDataStore`'dan
     okunur); koleksiyon/yerel kayıtlar oluşturulduktan hemen sonra başlıklar yerelleştiriliyor.
2. **`KitsugiNotificationsScreen.kt`**
   - Her iki `loadBangumi(...)` çağrısı `appSettings.titleLanguage`'i iletiyor.
   - İlk yükleme `LaunchedEffect`'inin anahtarına `appSettings.titleLanguage` eklendi — başlık
     dili ayarı değiştiğinde Bangumi sekmesi açıksa bildirimler kendiliğinden yeniden yükleniyor.
3. **`app/build.gradle.kts`** — sürüm `2.4.325`'e yükseltildi.
4. **`release_notes_v2.4.325.md`** + **`RELEASE_NOTES.md`** — kullanıcıya dönük not eklendi.
5. **`KOK_NEDEN_RAPORU_BILDIRIMLER_BANGUMI_BASLIK_DILI_2026-10-21.md`** — ayrıntılı kök neden
   analizi.

## Kapsam dışı (bilinçli, değiştirilmedi)

- Karakter/kişi adları ve niş yapım başlıkları (radyo programı, foto kitabı vb.) için Bangumi'de
  hiçbir kaynakta (infobox/AniList/TMDB) resmi Latin karşılığı yoksa özgün ad gösterilmeye devam
  eder — uydurma transliterasyon yanlış bilgi riski taşıdığı için önceki kök neden raporlarında
  bilinçli olarak reddedilmiştir.

## Doğrulama

- Derleyici bu ortamda mevcut değil (JDK yok); değişiklik satır satır elle doğrulandı ve
  `BangumiProfileContent.kt`'deki (üretimde çalışan) aynı desenle birebir eşleştirildi.
- Önerilen manuel doğrulama: Ayarlar → Başlık Dili → İngilizce seçiliyken Bildirimler → Bangumi
  sekmesini aç; "İzleniyor" kartlarının İngilizce/romaji başlıkla geldiğini doğrula; dili
  Özgün'e çevirip sekmenin kendiliğinden yeniden yüklendiğini ve özgün adlara döndüğünü doğrula.
