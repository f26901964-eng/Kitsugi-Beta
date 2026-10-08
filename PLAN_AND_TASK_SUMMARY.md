# Kitsugi-Beta Değişiklik ve Görev Planı (Task Summary)

## 📌 1. Görev: Bildirimler Ekranı Kaynak Seçici Paneli
### Yapılan İşlemler:
1. **İşaretli Yatay Sekmeler Barı Kaldırıldı:**
   - `KitsugiNotificationsScreen.kt` içerisindeki yatay kaydırılabilir 5'li platform sekmeleri (`AniList`, `MAL`, `TMDB & Simkl`, `Kitsu`, `Shikimori`) kaldırıldı.
2. **Listem Tarzı Seçici Hap Buton (`NotificationSourceSelectorPill`) Eklendi:**
   - Üst barın hemen altına, seçili platformun logosunu, adını, bağlantı durumu rozetini (bağlıysa yeşil nokta, AniList için okunmamış bildirim sayısını) ve açılır ok ikonunu (`ArrowDropDown`) içeren buton yerleştirildi.
3. **Listem Sayfası Tarzı Açılır Modal Bottom Sheet (`NotificationSourcePickerSheet`) Eklendi:**
   - 5 kaynak (AniList, MyAnimeList, TMDB & Simkl, Kitsu, Shikimori) listelendi.
   - Her öğede platform logosu, platform adı, `@kullanıcıadı` veya bağlantı durumu rozetleri, AniList yeni bildirim sayısı ve detaylı kaynak açıklamaları eklendi.
   - Seçili platform onay rozetiyle vurgulandı; tıklandığında ilgili sekmeye animasyonlu geçiş yapılıp panel otomatik kapatılıyor.

---

## 📌 2. Görev: Kitsu Mangalarında Yanlış Logo ve Resim Düzeltmeleri
### Yapılan İşlemler:
1. **Manga TMDB / Fanart Ayrımı:**
   - Manga yapımları TMDB veya Fanart.tv'de bulunmaz. ARM API'nin (Anime Relations Map) manga ID'lerini anime sanarak alakasız anime yapımlarıyla (örn. Berserk -> Hungry Heart) eşleştirmesi engellendi.
2. **Logo ve Galeri Filtreleri:**
   - `ApiResultDetailViewModel.kt` ve `MediaEntryDetailViewModel.kt` içerisinde `isManga` kontrolü eklenerek mangalara yanlış anime logolarının ve galerilerinin eklenmesi önlendi.
   - Manga içeriklerinde galeriye yalnızca eserin kendi kapak ve banner görselleri dahil edildi.
3. **Kitsu Manga Model Genişletmesi:**
   - `KitsuExploreClient.kt` içerisinde manga verileri ayrıştırılırken eksik olan `coverImage` (banner/arka plan) ve orijinal görseller (`pictures`) veri modeline eklendi.
4. **Kimlik Çözümleme Koruması:**
   - `KitsugiIdResolver.kt`, `KitsugiDetailClient.kt` ve `KitsugiHeroSection.kt` içerisinde `mediaType == MediaType.Manga` durumunda ARM API sorguları ve TMDB fallback'leri devre dışı bırakıldı.

---

## 📂 Değişen Dosyalar Listesi
1. `app/src/main/java/com/kitsugi/animelist/ui/screens/notifications/KitsugiNotificationsScreen.kt`
2. `app/src/main/java/com/kitsugi/animelist/data/remote/KitsuExploreClient.kt`
3. `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiDetailClient.kt`
4. `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiIdResolver.kt`
5. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiHeroSection.kt`
6. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailViewModel.kt`
7. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/MediaEntryDetailViewModel.kt`
8. `PLAN_AND_TASK_SUMMARY.md`
