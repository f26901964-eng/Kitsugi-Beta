# Profil İstatistik ve Sosyal Alt Kısayolları — Plan / Task

## İstek
Favoriler sekmesindeki sol alt kategori seçimi ve sağ alt hızlı yukarı çıkma kontrollerini İstatistikler ve Sosyal sekmelerine de aynı mantıkla eklemek; hem kişisel AniList profiline hem diğer kullanıcıların profillerine uygulamak.

## Uygulanan değişiklikler
- `ProfileFavoritesListemStyle.kt`: Mevcut kayan kontroller kategori etiketleri ve isteğe bağlı sayaçlarla yeniden kullanılabilir hale getirildi. Yukarı çıkma eşiği ve davranışı korunuyor.
- `AniListProfileContent.kt`: Sekmeye göre istatistik alt başlıkları, favori kategorileri veya sosyal filtreler aynı alt kontrol üzerinden seçiliyor.
- `KitsugiUserProfileScreen.kt`: Diğer kullanıcı profiline aynı kategori seçimi ve yukarı çıkma işlevi eklendi.
- Sosyal filtrelerde takipçi/takip edilen sayıları gösteriliyor; istatistik alt başlıklarında sayı gösterilmiyor.

## Kontrol / Kabul kriterleri
- İstatistikler ve Sosyal sekmelerinde sol alt düğme ilgili alt başlık listesini açmalı, seçilen başlığı etkinleştirmeli.
- Aşağı kaydırıldığında sağ alt yukarı çıkma düğmesi görünmeli ve dokunulduğunda liste başına dönmeli.
- Favoriler sekmesinin mevcut işleyişi korunmalı.
- Kişisel AniList ve diğer kullanıcı profillerinde davranış aynı olmalı.

`git diff --check` başarılı. Android SDK mevcut ortamda bulunmadığından cihaz/derleme doğrulaması yapılmadı.
