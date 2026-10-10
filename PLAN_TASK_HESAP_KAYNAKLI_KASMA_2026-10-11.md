# PLAN/TASK — Bağlı hesap kaynaklı Keşfet / Tümünü Göster / Arama kasması

Tarih: 2026-10-11
Durum: Kod düzeltmesi yapıldı (commit `dfe12f4`); derleme ve cihaz testi bekliyor.

## Hata
Kaynak hesabı (AniList, Simkl, Kitsu, Shikimori, Bangumi, MAL) bağlıyken Keşfet, "Tümünü Göster" ve Arama
sayfaları kaydırırken ve veri yüklenirken kasıyor / donuyor.

## Kök neden
`getMediaEntryFromMap()` (ui/screens/explore/ExploreMapping.kt): eşleşmeyen her kart/sonuç için kaynağın tüm
liste kayıtları taranıyor, her taramada başlıklar NFKC + regex ile normalize ediliyordu. Kart kompozisyonu sırasında
çalıştığı için maliyet (görünen kart) × (hesaptaki kayıt) ile büyüyordu.

## Yapılan işler
- [x] `"$source|all"` tam tarama geri dönüşü kaldırıldı (yazma + okuma).
- [x] `realMalId` eşleşmesi için indeks anahtarı eklendi (`$source|id|<rMal>`).
- [x] Birim testi: `ExploreMappingSourceScopeTest` → realMalId eşleşmesi indeksten bulunuyor.
- [x] Kök neden raporu: `KOK_NEDEN_RAPORU_HESAP_KAYNAKLI_KASMA_2026-10-11.md`

## Değişen dosyalar
- app/src/main/java/com/kitsugi/animelist/ui/screens/explore/ExploreMapping.kt
- app/src/test/java/com/kitsugi/animelist/ui/screens/explore/ExploreMappingSourceScopeTest.kt
- KOK_NEDEN_RAPORU_HESAP_KAYNAKLI_KASMA_2026-10-11.md
- PLAN_TASK_HESAP_KAYNAKLI_KASMA_2026-10-11.md

## Doğrulama (bekleniyor)
1. Derleme: `bash gradlew :app:testFossDebugUnitTest` (Java/Android SDK gerekli; bu ortamda yok).
2. Cihaz: bağlı hesaplı Keşfet → kaynak → "Tümünü Göster" kaydırma; Arama sonuçları kaydırma.
   Kare düşüşü ve `concurrent copying GC` sıklığı azalmalı.
3. Hâlâ kasma varsa ekran + kaynak bilgisiyle geri bildirim.

## Kalan riskler
- Bu düzeltme yalnızca eşleşme maliyetini hedefler. Hesap senkronizasyonu sırasında `entriesFlow` sık
  yayınlanıyorsa indeks her seferinde yeniden kurulur (kayıt sayısıyla doğrusal, ucuz). Gerekirse ayrıca incelenmeli.
