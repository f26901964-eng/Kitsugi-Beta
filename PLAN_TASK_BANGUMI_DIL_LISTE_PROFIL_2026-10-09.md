# Görev Planı: Bangumi dil tercihi, liste adları ve profil kontrolleri (2026-10-09)

## Kullanıcı talebi
1. Bangumi kaynaklı tüm ekranlarda (detay, karakter, kişi, ilişki, öneri, ekip, keşfet listeleri) uygulamanın seçili başlık dili uygulanmalı.
2. Dil İngilizce ise önce İngilizce, yoksa Romaji, en son çare orijinal ad gösterilmeli. Ing/Romaji varken Çince/Japonca/Korece ad görünmemeli.
3. AniList profil favorilerindeki "Karakterler" (kategori) butonu çok yukarıda duruyor; aşağı indirilmeli.
4. Aşağı kaydırınca çıkan "yukarı git" butonu kullanıcı kaydırdığında hemen görünmeli.

## Kök nedenler
- Bangumi liste uçları (`/v0/search/subjects`, `/v0/subjects`, browse uçları) infobox döndürmez. İngilizce/romaji ad sadece subject detayında olduğu için liste kartları Korece/Japonca adla çiziliyordu.
- `BangumiLocalizedName.alternatives` Japonca/Çince/Korece varyantları da içeriyordu ("Diğer Adlar").
- Karakter/kişi detayında `nativeName` alt satırı dil seçiminden bağımsız gösteriliyordu.
- AniList profil favori kontrollerinde `bottomOffset = 96.dp` kullanılıyordu (kullanıcı profilinde 20.dp).
- Yukarı kaydırma butonu `firstVisibleItemIndex > 3` koşuluna bağlıydı.

## Yapılan değişiklikler
| Dosya | Değişiklik |
|---|---|
| `data/remote/KitsugiBangumiClient.kt` | `withLatinBangumiNames()` eklendi: CJK başlıklı satırlar için subject bir kez çekilir, Latin ad `BangumiTitleCache`'e yazılır. 15 liste/arama çağrısına bağlandı (semaphore 4). |
| `data/remote/BangumiLocalizedName.kt` | `alternatives` yalnızca Latin harfli adları içerir. |
| `ui/screens/detail/CharacterDetailPage.kt` | `nativeName` yalnızca NATIVE / JAPANESE_STAFF dillerinde gösterilir. |
| `ui/screens/detail/StaffDetailPage.kt` | Aynı kural. |
| `ui/screens/profile/AniListProfileContent.kt` | Kategori butonu `bottomOffset` 96.dp → 20.dp. |
| `ui/screens/profile/ProfileFavoritesListemStyle.kt` | Yukarı kaydırma: `index > 0 || offset > 240`. |

## Bilinen sınırlar / sonraki adımlar
- Karakter ve kişi arama listelerinde (`searchCharacters`, `searchPeople`) infobox zenginleştirmesi yok.
- Detaydaki "Japonca" / "İngilizce" etiketli satırlar ham değeri gösteriyor (bilinçli bırakıldı).
- Ortamda Android SDK olmadığı için derleme ve cihaz testi yapılmadı. Derleme + manuel test gerekli.

## Doğrulama listesi
- [ ] Keşfet > Bangumi Dizileri: "김비서가 왜 그럴까" → "What's Wrong with Secretary Kim" (İngilizce) / romaji karşılığı.
- [ ] Ayarlarda dil İngilizce/Romaji/Japonca değiştirilince kartlar ve detay başlıkları buna uyuyor.
- [ ] Bangumi detayında "Diğer Adlar" içinde CJK ad yok.
- [ ] Karakter/kişi detayında Japonca alt satır sadece Japonca dilde görünüyor.
- [ ] Profil > Favoriler > Karakterler butonu alt kenara yakın.
- [ ] Favorilerde biraz aşağı kaydırınca yukarı butonu çıkıyor.
