# PLAN_TASK: Bu Hafta Yayında Takvimi — Izgara Kartı Çerçeve Taşması Düzeltmesi

- **Tarih:** 2026-10-11
- **Dal:** `arena/9a8f06ae-kitsugi-beta`
- **Değişen dosya:** `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/KitsugiAiringCalendarComponents.kt`

## Sorun

Bu Hafta Yayında ekranının ızgara (grid) görünümünde kartların alt satırındaki
tarih/saat metni (`2026-10-11 01:00 • Yayında`) tek satıra sıkıştırılıyordu.
Metin kart genişliğine sığmadığı için `Yayı...` şeklinde kesiliyor, kartlar
çerçeveye uyumsuz görünüyordu.

## Çözüm

- `AiringTimeText` bileşenine `maxLines` ve `modifier` parametreleri eklendi
  (varsayılan değerler mevcut davranışı korur: tek satır, `Modifier`).
- Izgara kartındaki bilgi satırı:
  - Sabit `34.dp` yüksekliğe alındı; böylece tüm kartlar aynı boyda kalır.
  - Tarih/saat metni `weight(1f)` ile kalan genişliği kullanır ve en fazla
    iki satıra kadar sarılır; metin artık kesilmez.
  - Saat ikonu metnin ilk satırıyla hizalandı (`Alignment.Top`, `padding(top = 2.dp)`).
- Liste görünümü (`AiringEntryWideCard`) ve yatay (landscape) kart etkilenmedi.

## Doğrulama

- Kod incelemesi ile `AiringTimeText` tüm çağrı noktalarında geriye uyumlu
  olduğu kontrol edildi (`AiringEntryWideCard` ve iskelet ekranı varsayılan parametrelerle çalışır).
- Projede Android SDK / Gradle bulunmadığı için derleme ve cihaz testi bu oturumda yapılamadı.
  Tablet/telefon dikey ve yatay ekranlarda Pazar sekmesi ile manuel kontrol önerilir.
