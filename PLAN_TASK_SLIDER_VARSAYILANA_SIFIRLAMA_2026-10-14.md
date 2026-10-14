# Plan Task: Kaydırıcıları Varsayılana Sıfırlama

**Tarih:** 2026-10-14  
**Durum:** Uygulandı

## Amaç

Ayar ekranlarındaki sayısal kaydırıcıların yanına, ilgili ayarı varsayılan değerine döndüren kolay erişilebilir bir düğme eklemek. Düğme her kaydırıcının hemen sağında bulunur ve ekran okuyucu için yerelleştirilmiş açıklama taşır.

## Kapsam

- Ortak `ResettableSlider` bileşeni ve Türkçe/İngilizce erişilebilirlik etiketi.
- Oynatıcı ayarları: kaydırma hassasiyeti, MPV demuxer önbelleği, altyazı gölge uzaklığı ve kenarlık kalınlığı.
- Oynatıcı ses çıkış rotaları: hoparlör, Bluetooth, kablolu kulaklık ve HDMI gecikmeleri.
- Manga okuyucu parlaklığı: Görünüm/Tercihler ekranında ve manga okuyucu içindeki parlaklık ayarında.
- Eski/alternatif oynatıcı genel sekmesindeki ayar kaydırıcıları da aynı ortak bileşeni kullanır.

## Varsayılan değerler

| Ayar | Varsayılan |
| --- | ---: |
| Oynatıcı kaydırma hassasiyeti | 1,0× |
| MPV demuxer önbelleği | 64 MB |
| Altyazı gölge uzaklığı | 1,5 dp |
| Altyazı kenarlık kalınlığı | 1,5 dp |
| Hoparlör / Bluetooth / kablolu / HDMI gecikmesi | 0 ms |
| Manga okuyucu parlaklığı | %100 |
| Ses güçlendirme / altyazı gecikmesi / ses limiti (alternatif sekme) | 0 dB / 0 ms / %200 |

## Değiştirilen dosyalar

- `app/src/main/java/com/kitsugi/animelist/ui/components/ResettableSlider.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiPlayerSettingsDialog.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiPreferencesSettingsDialog.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/player/PlayerGeneralTab.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/manga/MangaReaderScreen.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`

## Doğrulama

- Türkçe ve İngilizce kaynak XML dosyaları ayrıştırıldı; anahtarların yinelenmediği doğrulandı.
- `git diff --check` başarılı.
- Android derlemesi çalıştırılamadı: çalışma ortamında Java/JDK kurulu değil.
