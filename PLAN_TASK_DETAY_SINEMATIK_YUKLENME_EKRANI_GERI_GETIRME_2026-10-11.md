# PLAN / TASK — Detay Sayfalarının Sinematik Yüklenme Ekranını Geri Getirme

**Tarih:** 2026-10-11  
**Durum:** Kod tamamlandı; derleme ortamda JDK olmadığı için çalıştırılamadı.

## Kullanıcı talebi

Detay sayfaları açılırken daha önce görünen tam ekran sinematik yüklenme görünümünü geri getirmek: başlık/ad, mevcut görsel, ayar açıksa eser logosu ve kaynağın adı/orijinal logosu; görselin de ekran arka planında kullanılması. Bu akış medya, karakter, ekip ve stüdyo detaylarına uygun şekilde uygulanmalı.

## Uygulananlar

- Sinematik yüklenme bileşeni tekrar detay sayfalarının yüklenme durumlarına bağlandı:
  - Kütüphane medya detayı ve API/arama medya detayı
  - Karakter, ekip/personel ve stüdyo/yapımcı detayı
  - Android TV medya detayı (karakter/ekip/stüdyo sayfaları zaten ortak detay sayfalarını kullanıyor)
- Sayfaya girişte mevcut rotadan gelen ad, görsel ve kaynak korunuyor; medya logo ayarı açıksa mevcut logo yüklenme ekranında kullanılıyor.
- Arka plan görseli ve kaynağın adı/orijinal logosu `KitsugiSourceNamePill` ile gösteriliyor. Bilinmeyen eklenti kaynaklarında genel kaynak simgesi kullanılıyor.
- TMDB şirket/ağ varyantları (`tmdb_network`, `tmdb-network`, `tmdbnetwork`) TMDB adı ve logosuna eşlendi.
- Kısa yatay ekranlarda görsel ve yükleme içeriği küçültüldü. Yetişkin içerik bulanıklığı mevcut ayarı izliyor; çökme riski taşıyan ek, koşulsuz GPU blur katmanı geri eklenmedi.
- Var olan sayfa geçiş animasyonları değiştirilmedi.

## Değişen kod dosyaları

- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiCinematicLoadingScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiPlatformLogo.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailPage.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/CharacterDetailPage.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/MediaEntryDetailPage.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/StaffDetailPage.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/StudioDetailPage.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/tv/detail/TvDetailScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/utils/KitsugiTranslations.kt`

## İlgili plan/task belgeleri

- `PLAN_TASK_STUDYO_KESFET_PARITESI_KAYNAK_ROZET_2026-10-09.md` — detay yükleme ekranında kaynak adı ve logosu için önceki kaynak/rozet çalışması.
- `docs/PLAN_TASK_STUDYO_YAPIMCI_KAYNAK_KIMLIGI_FIX_2026-10-09.md` — stüdyo kimliği, ad/görselin detay rotasına taşınması.
- `PLAN_TASK_LOGO_DIL_SEZON_2026-10-09.md` — detay logosu ve logo tercihlerinin bağlamı.

## Doğrulama

- `git diff --check`: başarılı.
- Gradle/Android derlemesi: çalıştırılamadı; ortamda `java`/JDK ve Android SDK tanımlı değil.
