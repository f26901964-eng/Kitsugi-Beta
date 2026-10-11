# Plan / Task: Ayarlar Ekranı Arama, Kategoriler ve Görsel Uyum

**Tarih:** 2026-10-11  
**Durum:** Kod değişiklikleri uygulandı; derleme ve cihaz üzerinde görsel doğrulama bekliyor.

## Amaç

Ayarlar ana sayfasını anlaşılır kategorilere ayırmak, ayarları uygulama içinden bulmayı kolaylaştırmak ve alt sayfalar, sekmeler ile gömülü ayar içeriklerinde ortak Kitsugi görünümünü sürdürmek. Mevcut ayar davranışlarını ve kayıt mekanizmasını değiştirmeden hedef sayfalara yönlendirme yapmak.

## Yapılanlar

- Ayarlar ana sayfasına Türkçe/İngilizce arama alanı, canlı sonuçlar ve boş sonuç durumu eklendi.
- Arama metni noktalama ve Türkçe karakter farklarına göre normalleştiriliyor; seçilen sonuç uygun sayfaya, sekmeye, oynatıcı alt ekranına veya mevcut uygulama eylemine yönlendiriliyor.
- Ana sayfa Hesap, Uygulama/Görünüm, Kaynaklar/Entegrasyonlar, Veri/Depolama ve Destek/Tanılama gruplarına ayrıldı. İzleme Geçmişi veri bölümünde, hata kataloğu tanılama bölümünde yer alıyor.
- Görünüm, eklentiler, entegrasyonlar ve sistem ayarları için ortak vurgulu/ikonlu sekme bileşeni kullanıldı; eklenti sekme adları yerelleştirildi.
- Ayar alt sayfalarında ortak geri başlığı ve gezinme çubuğu güvenli alanı uygulandı. Oynatıcı başlığı etkin alt ekrana göre güncelleniyor.
- Depolama ekranındaki yinelenen kart katmanı sadeleştirildi.
- Gömülü Entegrasyonlar ve Sistem ayarlarında yinelenen “Tamam” altbilgisi kapatıldı; ayrı modal kullanımındaki mevcut kapatma davranışı korunuyor.
- Sayfa/sekme/alt ekran başlangıç seçimi eklendi; ayarların mevcut callback ve saklama davranışları korundu.

## Etkilenen dosyalar

- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiAddonsSettingsDialog.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiIntegrationsSettingsDialog.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiPlayerSettingsDialog.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiPreferencesSettingsDialog.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiSettingsComponents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiSystemSettingsDialog.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/settings/SettingsScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/settings/SettingsSearch.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/settings/StorageSettingsContent.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`

## Kontrol ve kalan doğrulama

- `git diff --check` başarılı.
- Ayar aramasında kullanılan string kaynakları Türkçe ve İngilizce kaynak kümelerinde bulundu; ilgili XML dosyaları parse edildi.
- Gradle derlemesi ve emülatör/cihaz üzerinde görsel smoke test henüz yapılamadı. Ortamda `java`/JDK ve `JAVA_HOME` bulunmadığından `:app:compileFossDebugKotlin` çalıştırılamıyor.
- Sonraki doğrulama: JDK sağlandığında derlemeyi çalıştır; ayarlar ana sayfası, arama boş/dolu durumları, sekme hedefleri, geri gezinme, küçük ekran ve gezinme çubuğu boşluklarını cihazda kontrol et.
