# PLAN/TASK — Kurulum bildirimi, Uncharted galeri, çeviri hedef dili ve harici çeviri düğmeleri

Tarih: 2026-10-11
Durum: Kod değişiklikleri tamamlandı. Derleme (Gradle) ve cihazda doğrulama YAPILMADI
(sandbox'ta Java/Android SDK/Gradle yok).

## 1. Eklenti kurulum paneli → Android sistem bildirimi
Hata: Eklenti toplu kurulumu sırasında tam ekranı kaplayan "⚡ Eklentiler Kuruluyor" paneli
(AppBulkInstallProgress) sürekli görünüyordu.
Çözüm:
- AppBulkInstallProgress.kt silindi (git rm ile staged).
- AppRoot.kt içindeki panel çağrısı ve import kaldırıldı.
- Yeni core/notifications/AddonInstallNotifier.kt: kalıcı, sessiz ilerleme bildirimi
  (kanal kitsugi_addon_install, IMPORTANCE_LOW, id 4107) ve bitince sonuç bildirimi (id 4108).
  POST_NOTIFICATIONS izni canNotify() ile denetlenir.
- AddonViewModel init: bulkInstall* durumunu snapshotFlow ile izleyip bildirimi günceller.
- Ayarlardaki satır içi sonuç mesajı değiştirilmedi.

## 2. Uncharted detay sayfasında alakasız galeri/poster (image-2, image-3)
Kök neden (kod okumasıyla; canlı olarak yeniden üretilmedi):
- Film olarak aranan kayıt TMDB'de galeri döndürmeyince, film ID'si TV ad alanında
  (/tv/{id}/external_ids) çözümleniyordu. Böylece yanlış bir diziye (Family Full House)
  ait TVDB ID'si çıkıyor ve posterler/arka planlar o diziden geliyordu.
- Aynı şekilde TMDB'den dönen dizi sonucu başlık kontrolü olmadan kabul ediliyordu
  ("Yaklaşan Yayın: Bölüm 7" bilgisi bu yüzden yanlış geliyordu).
Düzeltme:
- KitsugiEpisodeRatingsRepository.resolveTvdbIdFromTmdb(allowTmdbExternalIds=false) — film
  yedek yolunda TMDB dış ID'leri kullanılmaz (yalnızca MAL → AniList → Kitsu).
- KitsugiDetailClient: tmdbTitleMatches yardımcısı; tür-karşıtı TMDB ve Simkl yedek
  sonuçları yalnızca başlık eşleşirse kabul edilir.
Doğrulama bekliyor: Uncharted sayfası (image-2/3) cihazda kontrol edilecek.

## 3. Yorumlardaki "Türkçe" sekmesi ve çevirinin bazı kısımlarda çalışmaması (image-4/5)
Kök neden:
- "Türkçe" etiketi sabit R.string.turkce idi; çeviri çağrıları translateToTurkish ile
  sabit "tr" hedefine gidiyordu. Ayardaki hedef dil yok sayılıyordu.
- TranslationManager.translateTo: bir parça başarısız olunca (fetchSingleChunk null döner)
  tüm metin orijinal haliyle dönüyordu; başarılı parçalar atılıyordu.
- Hızlı ardışık isteklerde 429 riski; yeniden deneme kısaydı.
Düzeltme:
- translateToTurkish → translateToTarget (hedef: KitsugiTranslatePrefs.resolvedTarget()).
  Tüm çağrı noktaları güncellendi.
- Kısmi başarıda birleşik metin dönülüyor; yalnızca tam başarılı çeviri önbelleğe yazılıyor.
- requestGate = Semaphore(2); yeniden deneme 1 sn / 2 sn geri çekilme ile.
- KitsugiTranslateUtils.targetLanguageName(code) ile etiket dinamik
  (KitsugiAllReviewsBottomSheet, KitsugiReviewDetailBottomSheet, KitsugiActivityDetailBottomSheet,
  KitsugiAllTopicsBottomSheet, KitsugiTopicDetailBottomSheet, TvTopicDetailDialog).
Doğrulama bekliyor: Ayarlarda hedef dil değiştirilince etiket ve çeviri dili değişmeli.

## 4. Çeviri düğmeleri → harici çeviri uygulaması (image-6 ve diğerleri)
- Stüdyo "Hakkında" (StudioDetailPage), karakter/kişi biyografisi (CharacterDetailComponents,
  StaffDetailComponents), detay özeti (EntryDetailTabContents), ApiResultDetailPage,
  spoiler sayfası (KitsugiMarkdownText) düğmeleri artık KitsugiTranslateUtils.openTranslator
  ile harici uygulamayı açar; satır içi çeviri yolu bu düğmelerden kaldırıldı.
- Kullanılmayan onTranslate* parametreleri imzalarda bırakıldı (yalnızca uyarı üretir).
- StudioDetailViewModel'deki otomatik çeviri (hedef dil ayarına göre) değişmedi.

## Değiştirilen dosyalar
Bkz. zip içindeki liste: kitsugi-degisenler-2026-10-11.zip → DEGISEN_DOSYALAR.txt

## Derleme/doğrulama kontrol listesi
- [ ] ./gradlew assembleDebug (derleme hataları: AddonInstallNotifier, TranslationManager, çağrı noktaları)
- [ ] Eklenti toplu kurulumu: bildirim çubuğunda ilerleme ve sonuç; tam ekran panel yok
- [ ] Uncharted: galeri/poster ve "Yaklaşan Yayın" doğru dizi/film
- [ ] Yorumlar: "Türkçe" etiketi hedef dile göre; tüm paragraflar çevriliyor
- [ ] Stüdyo/karakter/özet çeviri düğmeleri harici uygulamayı açıyor
