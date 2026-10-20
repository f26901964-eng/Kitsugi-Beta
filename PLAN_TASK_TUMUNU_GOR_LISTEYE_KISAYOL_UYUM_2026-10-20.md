# Rapor — "Tümünü Gör" kartlarındaki listeye ekleme kısayolu yeniden uyarlandı

**Tarih:** 2026-10-20
**Durum:** Kod hazır (Kitsugi-Beta çalışma kopyasında uygulandı ve diff'e döküldü); cihaz/Gradle doğrulaması bekliyor.

---

## Sorun (ekran görüntüsündeki hâl)

Önceki sürümde kısayol, posterin üstüne binen **30dp daire + 1dp beyaz halka** idi:

- Kartın kendi köşe rozetleri (sıra `#1`, kaynak logosu `A`, durum rozeti) **köşeye yapışık, tek köşesi yuvarlatılmış, yarı saydam** öğeler. Daire + beyaz halka bu dilin tamamen dışında kaldığı için "postere sonradan yapıştırılmış" gibi duruyordu.
- Liste görünümünde poster sadece 86×120dp; 30dp'lik daire + 6dp boşluk posterin sağ üstünü ciddi şekilde kapatıyordu.
- Görün listedeyken rozet **tam parlak yeşil dolguya** dönüyordu. 40 kartın çoğu listede olduğu için ekran yeşil bloklarla doluyor ve alttaki asıl durum rozetiyle (o da yeşil) yarışıyordu.
- `tvClickable` varsayılanı `scaleOnFocus = true` olduğu için TV'de rozet odaklanınca büyüyor, ama posterin `clip`'i içinde kaldığı için kenardan kırpılıyordu.

## Yeni tasarım

`KitsugiQuickAddButton` artık bir "daire" değil, **kartın kendi köşe rozeti**:

| | Önce | Sonra |
|---|---|---|
| Biçim | 30dp daire + beyaz halka | Köşeye sıfır boşlukla oturan, **yalnızca alt-sol köşesi yuvarlatılmış** rozet |
| Boyut | Her yerde 30dp | Grid/TV posterinde **28dp**, liste kartlarında **24dp** |
| Zemin | Parlak accent (ya da parlak yeşil) | Füme cam `#000 %52` — posterin üstünde okunur, görseli boğmaz |
| Listede | Parlak yeşil dolgu | Aynı cam zemin + **yeşil ince çerçeve + yeşil tik** (sessiz durum) |
| Liste dışı | Accent daire + beyaz `+` | Cam zemin + beyaz `+` (nötr) |
| TV odağı | Büyüme (kırpılıyordu) | Büyüme yok; odak konturu rozetin şekline kırpılıyor |
| Geçiş | Ani renk değişimi | Zemin/çerçeve/tint `animateColorAsState` ile yumuşak geçiş |

## Değişen dosyalar

1. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiQuickAddButton.kt`
   - Daire `CircleShape` yerine `RoundedCornerShape(bottomStart = cornerRadius)`
   - `size: Dp = 28.dp`, `cornerRadius: Dp = 10.dp` parametreleri
   - `scrimColor`, `borderColor`, `iconTint` için `animateColorAsState`
   - `tvClickable(scaleOnFocus = false)`
2. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiExploreMediaCard.kt`
   - Grid / dikey / TV varyantlarında padding (6dp) kaldırıldı; rozet posterin köşesine yapışık
   - Boyut 28dp / 26dp (grid/TV)
3. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiRankingMediaCard.kt`
   - Liste görünümünde 24dp, alt-sol köşe 9dp; padding yok
4. `app/src/main/java/com/kitsugi/animelist/ui/components/DetailedSeasonalMediaCard.kt`
   - Sezonluk kartlarda 24dp, alt-sol köşe 9dp; padding yok
