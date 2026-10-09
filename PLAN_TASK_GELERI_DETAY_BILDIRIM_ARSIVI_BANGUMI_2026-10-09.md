# PLAN TASK — GELERİ API DETAYLARI + BİLDİRİM ARŞİVİ + BANGUMİ BİLDİRİM KAYNAĞI

Tarih: 2026-10-09
Dal: `arena/fe931d8f-kitsugi-beta`
İstek:
1. Fanart API'sinden gelen resimlerde, API üzerinden alınabilen ayrıntıların galeride görünmesi
   (yatay modda sağ panel; dikey modda mantıksal adaptasyon).
2. Kaynak tarafında tutulmayan bildirimlerin (Kitsu/MAL gibi) yedekleme mekanizmasıyla
   kalıcı olarak tutulması.
3. Bildirimler sayfasında eksik olan Bangumi bildirim alanının eklenmesi.

## 1) GELERİ / FANART API DETAYLARI (GALERİ)

### Tespit
- `fanart.tv` v3 API'si görsel başına yalnızca `name`, `iMDb`, `url`, `lang` alanlarını döndürür.
  Web sayfasındaki "Uploader / Copyright / Downloads / Approved by / Uploaded" alanları API'de
  YOKTUR → uydurulmaz, yalnızca gerçekten gelen alanlar gösterilir.
- `GalleryItem` modeline `details: Map<String, String>` (sıralı) alanı eklendi.
- `FanartApiClient.appendImages`: `name` → "Ad", `iMDb` → "IMDb ID"; ayrıca API `name`'i
  "Açıklama" satırında URL türetimine göre öncelikli kullanılır (CharacterART karakter adı).

### UI
- **Yatay:** `GalleryLandscapeLayout` sağ panel "Detaylar" sekmesinde, "Açıklama" satırının
  altına `item.details.forEach { DetailRow(label, value) }` eklendi.
- **Dikey:** Panel alanı olmadığı için `KitsugiGalleryHeader`'a "Detaylar" (ℹ️) butonu eklendi;
  buton `GalleryDetailsSheet` (ModalBottomSheet) açar. Levha, yatay panelle aynı veri kümesini
  gösterir: Kaynak (logo/rozet), Tür, Dil, Boyut (varsa), API detay satırları, Açıklama, Sayfa.
- `KitsugiGalleryHeader` yalnızca dikey düzen tarafından kullanılır; yatay düzen sağ panelde
  kendi başlığını kurar → davranış değişmedi.

### Dosyalar
- `data/remote/GalleryItem.kt`
- `data/remote/FanartApiClient.kt`
- `ui/components/KitsugiImageGalleryDialog.kt`

## 2) BİLDİRİM ARŞİVİ (YEDEKLENMEYEN KAYNAKLARIN KALICI TUTULMASI)

### Tespit
- MAL / Simkl / Kitsu / Bangumi API'lerinde **kişisel bildirim ucu yoktur**; bu kaynakların
  "bildirimleri" Kitsugi tarafında üretilir (yayın takvimi + izleme listesi eşleşmesi) ve
  sunucuda hiçbir yerde saklanmaz → 7 günlük pencereden düşen kayıtlar kayboluyordu.
- AniList ve Shikimori kendi bildirimlerini sunucularında tutar → bunlar arşivlenmez
  (kaynak akışı tek gerçek kaynaktır; sayfalama/okunmamış sayacı bozulmaz).

### Yerel katman — `data/notifications/NotificationArchiveStore.kt` (yeni)
- Dosya: `filesDir/notifications_archive.json`
- Yapı: `version` + `sources: Map<kaynak, List<ArchivedNotif>>`
- `ArchivedNotif`: id, source, title, body, dateText, tsMs, imageUrl, mediaId, mediaType
- Kurallar: id ile tekilleştirme (yeni çekim kazanır), `tsMs`'ye göre yeni→eski, kaynak başına
  300 kayıt; atomik yazım (tmp + rename); mutex ile tekilleştirilmiş erişim.

### UI katmanı — `KitsugiNotificationsViewModel.kt`
- `NotifItem.tsMs: Long?` eklendi (sıralama + birleştirme anahtarı); MAL/Simkl/Kitsu/Bangumi
  yükleyicilerinde doldurulur.
- `archiveAndMerge(source, live)`:
  1. canlı liste arşive yazılır (`mergeSourceAsync` — akışı bekletmez),
  2. giriş varsa buluta itilir (`KitsugiAccountRepository.pushNotificationArchive`, IO'da),
  3. ekrana canlı liste + arşivde olup canlıda olmayan kayıtlar (id tekilleştirme) gösterilir.
- AniList / Shikimori yükleyicileri `archiveAndMerge` kullanmaz.

### Bulut katmanı — `data/account/KitsugiAccountRepository.kt`
- `user_data` tablosuna `notifications` anahtarı (yeni anahtar için şema değişikliği yok).
- `pushNotificationArchive(context)` — yerel arşivi buluta yazar.
- `pullAndMergeNotificationArchive(context)` — buluttan çeker, arşiv birleştirme kuralıyla
  yerelle birleştirir, sonucu hem dosyaya hem buluta geri yazar.
- Tetikleme: her bildirim yüklemesi sonrası (giriş varsa, arka plan) + hesap ekranında
  giriş/kayıt sonrası + "Şimdi eşitle" (`KitsugiAccountContent.kt`).

## 3) BANGUMİ BİLDİRİM KAYNAĞI

### Tespit
- Bangumi (bgm.tv) API'sinde (v0 + next.bgm.tv/p1) **halka açık bildirim ucu yoktur**.
  Bu yüzden sekme, uygulamanın "bildirim ucu olmayan kaynak" deseniyle (MAL, Kitsu) aynı
  mantığı uygular; kullanıcıya açık bilgi notuyla sunulur.

### Üretim akışı — `KitsugiNotificationsViewModel.loadBangumi`
1. `BangumiAuthStore.getValidToken` + `resolveUsername`; bağlıysa
   `GET /v0/users/{u}/collections?type=3` (在看/DOING, 50 kayıt). Bağlı değilse yerel
   `MediaEntry` (source=bangumi, İzleniyor/Tekrar) — `malId` zaten stableId (500M+).
2. Önbellekli (bellek + dosya) çapraz kimlik çözümü: `KitsugiBangumiDetailClient.resolveCrossIds`
   → MAL ID (paralel, ilk 50 kayıt).
3. `KitsugiAiringCalendarClient.fetchAiringWindow` (son 7 gün + 2 saat) eşleşmesi →
   "Bölüm N yayınlandı / yayınlanacak" kayıtları (dateText + tsMs + stableId mediaId →
   detay yönlendirmesi yerel kaydı bulur).
4. Takvime düşmeyen izlenenler "İzleniyor • N. bölüm" / "Bangumi izleme listesinde (在看)"
   olarak listelenir.
5. Sonuç `archiveAndMerge("Bangumi", ...)` ile yerel arşive + buluta yazılır.

### UI
- `NotifPlatform.BANGUMI` eklendi; pager 5→6 sayfa; kaynak seçici panel, `isCurrentConnected`,
  `refreshCurrentPage`, ilk sayfa eşlemesi ve teşhis güncellendi.
- `AppRootDetailPages`: `isBangumiConnected = authViewModel.isBangumiConnected` iletildi.
- `NotificationDiagnostics.checkBangumi`: token + DOING koleksiyonu + örneklem çapraz
  çözüm + takvim eşleşmesi raporlanır (6 kaynak).
- Logo: `KitsugiPlatformLogo` zaten "bangumi" → `ic_logo_bangumi` destekliyor.

### Dosyalar
- `ui/screens/notifications/KitsugiNotificationsScreen.kt`
- `ui/screens/notifications/KitsugiNotificationsViewModel.kt`
- `data/notifications/NotificationDiagnostics.kt`
- `AppRootDetailPages.kt`
- `res/values/strings.xml` + `res/values-en/strings.xml` (5 yeni anahtar + teşhis alt başlığı 5→6)

## Doğrulama Notları
- Ortamda JDK/Android SDK bulunmadığından derleme yapılamadı; tüm değişiklikler elle gözden
  geçirildi (import, enum exhaustiveness, brace dengesinden XML doğrulamasına).
- Gerçek cihazda test önerileri:
  1. Fanart.tv içeren bir anime detayında (örn. Date A Live) galeriyi yatay + dikey aç;
     "Ad/IMDb ID" satırlarını ve dikeyde ℹ️ butonunu doğrula.
  2. MAL sekmesinde 7 günden eski yayınlanmış bölüm üret (test için arşiv dosyasına kayıt
     elle ekle) → pencereden düşse de listede kalmasını doğrula.
  3. Kitsugi hesabına gir → "Şimdi eşitle" → `user_data.notifications` satırını doğrula.
  4. Bangumi sekmesini aç: bağlıyken 在看 listesi + takvim eşleşmesi; bağlanmamışken yerel
     kayıtlar + bilgi notu; teşhis panelinde 6 satır.
