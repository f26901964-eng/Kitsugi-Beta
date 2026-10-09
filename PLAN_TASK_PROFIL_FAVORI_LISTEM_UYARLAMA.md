# Plan / Görev: Profil Favorileri ve Kullanıcı Listelerinin Listem Uyarlaması

Tarih: 2026-10-09
Dal: `arena/16085dca-kitsugi-beta`
Commit: `c68a40a`

## Amaç
- Profil > Favoriler sekmesi (kendi profilin ve diğer kullanıcılar) Listem (MyListScreen) görünümüyle birebir aynı olsun.
- "Daha Fazla Yükle" butonu kaldırılsın; aşağı kaydırınca otomatik yüklensin.
- Listem'deki alt kontroller (sol altta kategori, sağ altta yukarı kaydırma) favorilerde de çalışsın.
- Diğer kullanıcıların anime/manga listesi Listem mantığıyla (durum grupları, layout ayarı) çalışsın.

## Yapılanlar
| # | Dosya | Açıklama |
|---|-------|----------|
| 1 | `ui/screens/profile/ProfileFavoritesListemStyle.kt` (yeni) | Listem layout'larına (compact, comfortable, minimalist, large, grid_2col) uygun favori kartları; ortak içerik ızgarası; otomatik sayfalama (`ProfileFavoritesAutoLoad`); alt kontroller ve kategori sayfası (`ProfileFavoritesFloatingControls`). |
| 2 | `ui/screens/profile/tabs/AniListFavoritesTab.kt` | Kendi favori sekmesi yeni kart yapısına geçirildi, "Daha Fazla Yükle" kaldırıldı. Layout ve blur ayarı parametre olarak alınıyor. |
| 3 | `ui/screens/profile/tabs/UserProfileFavoritesTab.kt` | Diğer kullanıcı favori sekmesi aynı yapıya geçirildi; `LaunchedEffect` ile tüm sayfaları birden çekme kaldırıldı. |
| 4 | `ui/screens/profile/AniListProfileContent.kt` | LazyColumn `Box` içine alındı, otomatik sayfalama ve alt kontroller eklendi (kategori sayısı + layout ayarı). |
| 5 | `ui/screens/profile/KitsugiUserProfileScreen.kt` | Aynı otomatik sayfalama ve alt kontroller diğer kullanıcı profiline eklendi. |
| 6 | `ui/screens/profile/KitsugiUserMediaListScreen.kt` | Grid/liste ayrımı kaldırıldı. Liste, Listem'in `MyListGroupedContent` / `MyListFlatContent` bileşenleriyle render ediliyor; `UserMediaListItem` -> `MediaEntry` dönüşümü eklendi. |

## Tasarım kararları
- Favorilerde durum/ilerleme olmadığı için Listem kartları yerine durum bilgisi içermeyen aynı şekilli kartlar kullanıldı (durum hapı yanlış bilgi verirdi).
- Stüdyo gibi görseli olmayan öğelerde baş harf gösteriliyor.
- Diğer kullanıcının listesinde "+" butonu detay sayfasını açıyor (başkasının listesi düzenlenemez).
- Kategori seçimi ve sayfalama profil ekranındaki mevcut `viewModel.loadMoreFavorites` akışını kullanıyor; yeni veri katmanı yok.

## Kapsam dışı / bilinen sınırlar
- MAL, Kitsu, Shikimori, Bangumi, Simkl profillerindeki favoriler bu değişikliğe dahil değil.
- "Tümünü Gör" sheet'i korundu.
- Derleme ortamında Android SDK/JDK olmadığı için **derleme yapılmadı**; Android Studio'da derleme ve manuel test gerekiyor.

## Test listesi (manuel)
- [ ] Kendi profil > Favoriler: Anime / Manga / Karakterler / Ekip / Stüdyolar sekmeleri çalışıyor mu
- [ ] Aşağı kaydırınca sonraki sayfa otomatik geliyor mu (buton yok)
- [ ] Listem'de layout değiştirince favori kartları da değişiyor mu (compact, large, grid_2col vb.)
- [ ] Sol alt kategori butonu sayfayı ve kategoriyi değiştiriyor mu
- [ ] Sağ alt yukarı kaydırma çalışıyor mu (4. öğeden sonra görünür)
- [ ] Diğer kullanıcı profilinde aynı davranışlar
- [ ] Diğer kullanıcının anime/manga listesi: durum grupları, layout ayarı, tıklayınca detay
- [ ] Adult içerik blur ayarı favorilerde de çalışıyor mu
