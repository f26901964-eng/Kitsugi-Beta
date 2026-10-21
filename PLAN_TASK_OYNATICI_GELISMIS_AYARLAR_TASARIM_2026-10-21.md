# Plan / Task — Oynatıcı gelişmiş ayarlar tasarım düzenlemesi

Tarih: 2026-10-21
Dal: `arena/282aece1-kitsugi-beta`
Temel commit: `eca7c47376662a4d0540c31730de2232238c62ea`

## Sorun ve hedef

Gelişmiş oynatıcı ayarları tek bir varsayılan Material kartında toplanıyordu. Sürekli açık dört metin alanı, her alanın altındaki ayrı Kaydet düğmesi ve uzun yatay seçim sıraları ekranı kalabalıklaştırıyordu. Kartın renkleri uygulamanın ortak ayar bileşenlerinden farklıydı.

Hedef: Ayarların veri modeli, kayıt anahtarları ve oynatıcı davranışları korunarak ekranı uygulamanın ortak tema ve ayar satırlarıyla uyumlu hale getirmek.

## Tamamlanan işler

- [x] Dev kart yerine ayrı anahtarlı LazyColumn öğeleri ve yedi bölüm: Ses ve dil, Altyazı tercihleri, Görünüm ve kontroller, İzleme ve hatırlama, Resim içinde resim (PiP), Giriş atlama, Çift dokunma hareketleri.
- [x] Bölümlerde ortak `KitsugiSettingsSection` ve düzenlenebilir tercihlerde `KitsugiSettingsItem` kullanımı.
- [x] Metin alanlarını mevcut değeri gösteren satırlara dönüştürme; dokununca Kaydet/İptal içeren düzenleme penceresi açma.
- [x] Altyazı kenarlığı ve çift dokunma seçimlerini mevcut seçimi gösteren satırlara ve radyo seçenekli pencerelere dönüştürme.
- [x] Kenarlık seçeneklerinin teknik kayıt değerlerini koruyarak görünen adlarını yerelleştirme.
- [x] Anahtar satırlarında dikey ortalama, uzun başlıklara esnek alan, tüm satırdan açma/kapatma ve Switch erişilebilirlik rolü.
- [x] Kaydırıcı ve anahtarlarda Kitsugi yüzey/vurgu renkleri; kaydırıcılara erişilebilirlik etiketi.
- [x] Kayıt işlemlerini üst ekranın coroutine scope'una bağlama; lazy bölüm ekrandan çıkınca kayıt işinin bölümle birlikte iptal edilmesini önleme.
- [x] Yeni metinlerin Türkçe ve İngilizce kaynaklara eklenmesi.
- [x] 32 kayıt callback'i, kullanılan ayar özellikleri ve kalıcı seçim değerlerinin korunması.

## Değişen uygulama dosyaları

1. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiPlayerSettingsDialog.kt`
   - Gelişmiş sekmedeki tek öğe çağrısı yerine grupları ekleyen `playerParityOptions` LazyListScope uzantısı ve ekran scope'u kullanıldı.
2. `app/src/main/java/com/kitsugi/animelist/ui/components/player/PlayerParityOptions.kt`
   - Bölümlere ayrılmış düzen, temalı kontroller, metin düzenleme ve seçim pencereleri.
3. `app/src/main/res/values/player_parity_strings.xml`
   - Türkçe bölüm ve kontrol metinleri.
4. `app/src/main/res/values-en/player_parity_strings.xml`
   - Eşleşen İngilizce metinler.

## Yapılan doğrulamalar

- [x] `git diff --check`: başarılı.
- [x] `python3 scripts/check_localization_resource_parity.py`: başarılı; 17.671 string ve 4 array, format yer tutucuları dahil eşleşiyor.
- [x] Düzenlenen `PlayerParityOptions.kt` için tree-sitter Kotlin sözdizimi ayrıştırması: başarılı. Bu kontrol Android/Kotlin derlemesi değildir.
- [x] Önceki sürümle statik karşılaştırma: 32 `store.set...` çağrısı, ayar özellikleri ve sekiz kalıcı kenarlık/hareket seçim değeri korunuyor.
- [ ] Android derlemesi: ortamda Java/Android SDK olmadığı için çalıştırılmadı.
- [ ] Cihaz/emülatör görsel ve etkileşim testleri: henüz yapılmadı.

## Cihazda tamamlanacak testler

- [ ] Dar telefon, tablet ve büyük yazı ölçeğinde metinlerin/satırların taşmadığını doğrula.
- [ ] Ses dili, altyazı listeleri ve fontu düzenle; Kaydet sonrası ekranı yeniden açıp kalıcılığı kontrol et.
- [ ] Metin düzenleme penceresinde İptal/geri tuşunun mevcut değeri değiştirmediğini doğrula.
- [ ] Her anahtarın satırdan ve anahtar alanından tek kez değiştiğini kontrol et.
- [ ] Kaydırıcı aralıklarını, kayıt sonrası değerlerini ve hızlı kaydırma sırasında kayıt davranışını kontrol et.
- [ ] Üç kenarlık seçeneğini ve sol/orta/sağ çift dokunma eylemlerini seçip kalıcılığı kontrol et.
- [ ] Otomatik atlamayı açınca bekleme süresinin göründüğünü, kapatınca gizlendiğini kontrol et.
- [ ] Açık/koyu tema, farklı vurgu rengi, Türkçe/İngilizce ve TalkBack/klavye gezinmesini kontrol et.
- [ ] Alttaki kullanıcı MPV dosyaları bölümünün çalışmaya devam ettiğini kontrol et.

## ZIP içeriği ve uygulama

Arşiv yalnızca yukarıdaki dört değişen uygulama dosyasının tam güncel içeriklerini ve bu plan/task dosyasını içerir. APK, tüm depo veya derleme çıktısı içermez.

1. Mevcut çalışma kopyanı yedekle.
2. ZIP'i proje köküne çıkar; arşivdeki `app/...` yollarını koru.
3. Aynı dosyalarda başka yerel değişiklikler varsa doğrudan üzerine yazmak yerine farkları birleştir.
4. Android derlemesini ve yukarıdaki cihaz testlerini çalıştır.

Veri tabanı veya ayar anahtarı geçişi gerektirmez. Sürüm numarası değiştirilmedi.
