# KÖK NEDEN RAPORU — Bağlı hesap kaynaklı veriyle Keşfet / Tümünü Göster / Arama kasması

Tarih: 2026-10-11
Kapsam: AniList, Simkl, Kitsu, Shikimori, Bangumi, MAL kaynaklı vitrin/raf verisi; bu verileri gösteren Keşfet, "Tümünü Göster" ve Arama ekranları.

## Belirti
Kaynak hesabı bağlıyken Keşfet, Tümünü Göster ve Arama sayfaları kaydırırken ve veri yüklenirken kasıyor / donuyor.

## Kök neden
`ui/screens/explore/ExploreMapping.kt` → `getMediaEntryFromMap()`

Her kart (ve her arama sonucu) "listemde var mı?" kontrolü için bu fonksiyonu **kompozisyon sırasında** çağırıyor
(`KitsugiHorizontalMediaSection`, `KitsugiMediaGridDialog`, arama `filteredResults`). Eşleşen bir kayıt bulunamazsa
(çoğu kart için normal durum), fonksiyon `"$source|all"` anahtarına düşüp **kaynağın bütün liste kayıtlarını** tarıyordu.
Her taramada `MediaEntry.matches()` çağrılıyor ve bu fonksiyon başlıkları her seferinde NFKC normalizasyonu + regex ile
işliyordu. Sonuç:

- maliyet ≈ (görünen kart/sonuç sayısı) × (bağlı hesaptaki kayıt sayısı) × (başlık normalizasyonu)
- her kaydırmada ve her veri geldiğinde yeniden; ana iş parçacığında (kompozisyon) çalışıyor
- kayıt listesi büyüdükçe (hesap senkronizasyonu) kötüleşiyor

## Düzeltme
1. `"$source|all"` tam tarama geri dönüşü kaldırıldı (yazma ve okuma tarafı).
2. Tam tarama ile bulunabilen tek durum olan `realMalId` eşleşmesi için indeks anahtarı eklendi
   (`$source|id|<rMal>`); eşleşme kuralı (`matchesInSource`) değişmedi.
3. Birim testi eklendi: `ExploreMappingSourceScopeTest` → "same-source realMalId match is found through the index without a full scan".

## Doğrulama notu
Bu ortamda Java/Android SDK ve internet erişimi (Google Maven) olmadığından Gradle derlemesi ve cihaz testi çalıştırılamadı.
Derleme: `bash gradlew :app:testFossDebugUnitTest` (uygun ortamda). Cihazda: bağlı hesaplı Keşfet → kaynak → "Tümünü Göster"
kaydırma ve Arama sonuçları; kaydırma kareleri ve `concurrent copying GC` sıklığı kontrol edilmeli.

## Önceki raporlarla ilişki
`KOK_NEDEN_RAPORU_TUMUNU_GOSTER_DONGU_KASMA_2026-10-11.md` ve `KESFET_KAYNAK_YUKLEME_PERFORMANS_RAPORU_2026-10-10.md`
içindeki düzeltmeler (KitsugiLazyKeys, shimmer, sayfalama bütçesi, remember'lı bölümler) kodda mevcut ve korunmuştur.
Bu rapor ayrı ve daha önce teşhis edilmemiş bir maliyet kaynağını ele alır.
