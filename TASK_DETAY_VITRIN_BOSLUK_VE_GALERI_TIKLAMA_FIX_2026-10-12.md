# TASK — Detay Vitrini Boşluk ve Galeri Tıklama Düzeltmesi

**Tarih:** 2026-10-12  
**Dal:** `arena/a5a14949-kitsugi-beta`  
**Uygulama commit'i:** `4daa511`

## Tamamlanan İşler

- [x] `KitsugiDetailHero` içindeki sabit `220.dp` alt bilgi alanını kaldır.
- [x] `DetailHero` içindeki sabit `220.dp` alt bilgi alanını kaldır.
- [x] Başlık, meta bilgileri ve rozetleri görselin alt kısmına bindir.
- [x] Koyu gradyanı yazı okunabilirliğini koruyacak şekilde görsel üzerinde tut.
- [x] API detay hero tıklamasında gerçek `heroImageModel` URL'sini callback'e gönder.
- [x] Kütüphane detay hero tıklamasında gerçek gösterilen URL'yi callback'e gönder.
- [x] Gösterilen görselin `POSTER` veya `BACKDROP` kategorisini callback'e gönder.
- [x] Tıklanan görseli galeri listesinde bulup doğru başlangıç indeksiyle aç.
- [x] Galeride bulunmayan hero görselini listenin başına ekleyerek hemen aç.
- [x] Galeri yüklenirken hero tıklamasını engelleyen korumayı kaldır.
- [x] TMDB/Kitsu/Bangumi/AniList/MAL/Shikimori URL varyantlarını normalize et.
- [x] Galeri seçim mantığı için dört birim test ekle.
- [x] Değiştirilen Kotlin dosyalarında sözdizimi kontrolü yap.
- [x] `git diff --check` çalıştır.
- [x] Değişiklikleri `arena/a5a14949-kitsugi-beta` dalına pushla.

## Test Senaryoları

1. **Dikey ekran / backdrop**
   - Detay sayfasını dikey aç.
   - Başlık ve rozetlerin görsel üzerinde olduğunu doğrula.
   - Vitrin backdrop'ına dokun.
   - Galerinin aynı backdrop ile açıldığını doğrula.

2. **Yatay ekran / poster**
   - Cihazı yataya çevir.
   - Sol panelde posterin altında eski sabit boşluğun olmadığını doğrula.
   - Postere dokun.
   - Galerinin aynı poster ile açıldığını doğrula.

3. **Galeri yüklenirken tıklama**
   - Detay sayfasını soğuk başlangıçla aç.
   - Galeri yüklemesi tamamlanmadan hero görseline dokun.
   - Tıklanan görselin hemen açıldığını doğrula.

4. **URL kalite varyantı**
   - TMDB `w500` galeri URL'si ile `original` hero URL'sini kullan.
   - Yeni bir kopya eklemek yerine mevcut öğenin açıldığını doğrula.

## Doğrulama Sonucu

- Kotlin tree-sitter sözdizimi kontrolü: **Başarılı (8/8 dosya)**
- `git diff --check`: **Başarılı**
- Android Gradle testi: **Bu çalışma ortamında Java bulunmadığı için çalıştırılamadı**

## Teslim

- Commit: `4daa511` — `Fix detail hero spacing and gallery selection`
- Uzak dal: `origin/arena/a5a14949-kitsugi-beta`
