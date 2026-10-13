# PLAN / TASK — Ayarlar Seçim Panellerini AniHyou Tarzı Modal Pencerelere Dönüştürme

**Tarih:** 2026-10-13  
**Durum:** Tamamlandı  
**Dal:** `arena/0b33270f-kitsugi-beta`

## Kullanıcı isteği

Ayarlar sayfasında satırların altında/üstünde çapa bağlı olarak açılan, sayfa düzenini örten ve geniş alan kaplayan seçim panellerinin kaldırılması; bunların AniHyou referans görsellerindeki gibi:

- ekranın ortasında açılan,
- arka planı karartan,
- başlıklı,
- radyo düğmeli seçenekler kullanan,
- sağ altta **Kapat** eylemi bulunan,
- uzun seçenek listelerini kendi içinde kaydırabilen

modal seçim pencerelerine dönüştürülmesi.

## Uygulama planı

1. Açılır menü sunum biçimini CompositionLocal üzerinden bağlama göre seçilebilir hâle getir.
2. Ayarlar ekranı ağacında modal sunumu etkinleştir; uygulamanın diğer ekranlarındaki kompakt açılır menüleri değiştirme.
3. Ortak AniHyou tarzı seçim penceresini oluştur:
   - karartılmış arka plan,
   - ortalanmış ve genişliği sınırlandırılmış yüzey,
   - 28 dp yuvarlatılmış köşeler,
   - başlık,
   - kaydırılabilir seçenek alanı,
   - radyo düğmeli satırlar,
   - Kapat düğmesi.
4. Ayarlar içindeki tüm aktif açılır seçimlere anlamlı pencere başlıkları ekle.
5. Telefon, tablet ve TV yerleşimleri için duyarlı genişlik/yükseklik sınırları uygula.
6. Kotlin sözdizimini ve değişiklik kapsamını doğrula.

## Gerçekleştirilen çözüm

### 1. Bağlama göre sunum seçimi

`KitsugiDropdownPresentation` ve `LocalKitsugiDropdownPresentation` eklendi.

- Varsayılan davranış: `AnchoredPopup`
- Ayarlar ekranındaki davranış: `SettingsDialog`

Bu sayede arama, liste, detay ve diğer ekranlardaki mevcut kompakt açılır menüler korunurken yalnızca ayarlar ağacındaki seçimler modal pencereye dönüştürülür.

### 2. Ortak modal seçim penceresi

`KitsugiSettingsDropdownDialog` ortak bileşeni eklendi.

Özellikleri:

- `Dialog` tabanlı modal sunum
- ekranı karartan arka plan
- ekran merkezinde konumlandırma
- telefon/tablet için en fazla 560 dp genişlik
- ekran yüksekliğinin en fazla %86’sını kullanma
- 28 dp köşe yuvarlama
- tema uyumlu güçlü yüzey rengi
- uzun listelerde dahili dikey kaydırma
- başlık ve sağ altta yerelleştirilmiş **Kapat / Close** eylemi

### 3. Radyo düğmeli seçenek satırları

`KitsugiMenuItem`, modal seçim penceresi içinde çalıştığında:

- eski gradyan seçim çubuğu ve onay işareti yerine radyo düğmesi gösterir,
- tüm satırı dokunulabilir ve TV odaklanabilir tutar,
- seçili/seçili olmayan renkleri uygulama temasına bağlar.

Çapa bağlı normal menülerde eski görünüm korunur.

### 4. Ayarlar kapsamı

Toplam **36 aktif açılır seçim menüsüne** özel başlık eklendi:

- Görünüm ve tercihler: 12
- Oynatıcı ayarları: 20
- Stremio ayarları: 1
- P2P ayarları: 3

Kapsanan örnekler:

- Tema Modu
- Uygulama Dili
- Varsayılan Sekme
- Anime & Manga Başlık Dili
- Ekip & Karakter Adı Dili
- Dizi & Film Başlık Dili
- Liste Düzeni
- Puan Formatı ve Puanlama Adımları
- Manga Okuma Yönü, Renk Filtresi ve Sayfa Sığdırma
- Varsayılan Video Oynatıcı
- Harici Oynatıcı
- Intro Atlama Süresi
- Otomatik Oynatma Limiti
- MPV görüntü ayarları
- Altyazı boyutu, rengi, hizası, arka planı ve kenarlığı
- Ses güçlendirme ve gecikme
- Stremio varsayılan oynatıcı motoru
- P2P yükleme limiti, torrent profili ve önbellek boyutu

## Değişen dosyalar

1. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiSettingsComponents.kt`
2. `app/src/main/java/com/kitsugi/animelist/ui/screens/settings/SettingsScreen.kt`
3. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiPreferencesSettingsDialog.kt`
4. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiPlayerSettingsDialog.kt`
5. `app/src/main/java/com/kitsugi/animelist/ui/components/addons/StremioAddonTab.kt`
6. `app/src/main/java/com/kitsugi/animelist/ui/components/p2p/P2pSettingsSection.kt`
7. `PLAN_TASK_AYARLAR_ANIHYOU_MODAL_SECIM_PENCERELERI_2026-10-13.md`

## Doğrulama

- Değiştirilen 6 Kotlin dosyası Tree-sitter Kotlin ayrıştırıcısıyla kontrol edildi.
- Tüm dosyalarda `ERROR` ve eksik sözdizimi düğümü sayısı: **0**.
- Ayarlar kapsamındaki aktif açılır menü başlık kontrolü:
  - Tercihler: 12/12
  - Oynatıcı: 20/20
  - Stremio: 1/1
  - P2P: 3/3
- `git diff --check`: başarılı.
- Tam Android/Gradle derlemesi çalışma ortamında Java/JDK ve Android SDK bulunmadığı için çalıştırılamadı.

## Kabul ölçütleri

- [x] Ayarlar seçimleri artık satıra bağlı büyük panel olarak açılmıyor.
- [x] Seçim pencereleri ekran ortasında modal olarak açılıyor.
- [x] Arka plan karartılıyor.
- [x] Her pencerenin anlamlı bir başlığı bulunuyor.
- [x] Seçenekler radyo düğmeleriyle gösteriliyor.
- [x] Kapat eylemi bulunuyor.
- [x] Uzun listeler pencere içinde kaydırılıyor.
- [x] Ayarlar dışındaki açılır menüler etkilenmiyor.
- [x] Telefon, tablet ve TV yerleşimleri için boyut sınırları uygulanıyor.
