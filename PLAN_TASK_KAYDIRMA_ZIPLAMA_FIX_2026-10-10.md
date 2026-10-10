# PLAN / TASK — Kaydırma zıplaması, kilitlenme ve kutu geri çekilmesi

Tarih: 2026-10-10  
Dal: `arena/99bc4050-kitsugi-beta`

## İstek

Sayfalar ve kutucuklar ileri-geri zıplıyor, uygulama kasıyor/donuyor, yukarı-aşağı kaydırma çalışmıyor. Son kutu/çerçeve değişikliklerinden sonra özellikle AniList popüler anime ızgarası bozulmuş.

## Neden

1. Telefonda odaklanan kart, varsayılan `BringIntoView` ile listeyi parmağın tersine geri çekiyordu.
2. Neon çerçevedeki renkli `shadow` her kartta yazılım bulanıklığı üretiyordu.
3. Tür şeritleri iç içe `LazyRow` olduğu için ana ızgara kaydırırken yeniden ölçülüyordu.
4. Üst başlık ve yapışkan sekmeler kaydırma sınırında yükseklik animasyonuyla viewport'u değiştiriyordu.
5. Profil ve keşfet kaydırma konumu observable state'e her pikselde yazılıyor, sayfa yeniden kuruluyordu.
6. İç içe satırlardaki snap fling dikey kaydırmayı çalıyordu.

## Yapılanlar

- [x] Telefon/tablette `LocalBringIntoViewSpec` no-op. TV kendi spec'ini korur.
- [x] Mobil `tvClickable` odak almaz (`canFocus = false`).
- [x] Neon çerçeve yalnızca kenarlık; renkli yazılım gölgesi yok.
- [x] Tam ekran, stüdyo ve eklenti ızgaralarında tür şeridi düz `horizontalScroll`.
- [x] Yüzen başlıklar yükseklik animasyonu yerine solar. Yapışkan başlıklarda eşik (histerezis) var; `scrollToItem` zıplaması kaldırıldı.
- [x] Pager `pageHeights` yazımları 4px altındaki ölçüm gürültüsünü yok sayar.
- [x] Profil kaydırma indeksi/ofseti observable değil; `snapshotFlow` ile toplanır.
- [x] `rememberRetainedLazyListState` kaydırma yazımında sayfayı yeniden kurmaz.
- [x] Ana sayfa, keşfet, arama, profil ve inceleme satırlarından `rememberSnapFlingBehavior` kaldırıldı.
- [x] Listem başlığı her pikselde yükseklik değiştirmez; eşik aşılınca bir kez kapanır.

## Kontrol

- Bu ortamda Android SDK / Gradle derlemesi yok. Cihazda doğrulanmalı.
- Cihazda kontrol: AniList popüler anime ızgarasında yukarı-aşağı kaydırma, tür çiplerini yatay kaydırma, detay sekmesi, Listem, keşfet rafları ve profil. Sayfa kendini geri çekmemeli, kutucuklar başa zıplamamalı.

## Paket notu

Bu dosya ile birlikte ZIP'teki `app/src/...` yolları depo köküne göredir. Paket tam proje/APK değil; bu görevde değişen kaynak dosyalarını içerir.
