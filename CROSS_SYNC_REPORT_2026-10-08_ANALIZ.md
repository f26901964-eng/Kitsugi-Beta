# Çapraz Eşitleme Raporu — 2026-10-08 Analizi

Kaynak: Kullanıcının paylaştığı eşitleme raporu (00:27–00:37, 804 içerik, 1667 olay) ve ekran görüntüsü.
Sonuç: "Kısmen tamamlandı", 4 yazma hatası, 165 atlanan kayıt.

## Hata sınıfları ve durum

| Platform | Sayı | Neden | Durum |
|---|---|---|---|
| Kitsu (HTTP 422) | 4 | Birleştirilmiş ilerleme (ör. 13/13, 16/16) Kitsu'daki bölüm sayısından (12) büyük; "cannot exceed length of media" | **Düzeltildi**: 422'de Kitsu medya uzunluğu alınıp ilerleme o sınıra indirilerek bir kez daha yazılır (`KitsuSyncManager.writeWithLengthGuard`, `KitsuApiClient.fetchMediaLength`) |
| Eşleştirme uyarısı | 3 (Mayohiga, 86, 86 Part 2) | Aynı MAL ID, aynı yıl, ama başlık varyantı (romanizasyon/“86” ↔ “Eighty Six”) akraba sayılmıyordu | **Düzeltildi**: `CrossSyncIdentityGuard.titlesVariantRelated` (yıllar uyumluysa; sezon numaraları birebir aynı olmak zorunda) |
| Eşleştirme uyarısı | 1 (Koe no Katachi Specials) | Kitsu kaydı "Koi wo Shita no wa" (MAL 35566) — kaynak tarafında yanlış MAL eşlemesi | **Doğru davranış**: yazma durduruldu, değiştirilmedi |
| Simkl | 129 | Simkl kataloğunda yok | Simkl'e eklenemeyen içerik; sadece atlanır |
| AniList | 17 | MAL ID / ARM eşleşmesi yok (ör. Re:Zero Shin Henshuu-ban) | Kasıtlı güvenlik: başlık araması ile AniList'e yazılmıyor (yanlış sezon riski). Değişmedi |
| MyAnimeList | 2 | Doğrulanmış MAL ID yok | Değişmedi |
| Shikimori | 2 | Doğrulanmış MAL ID yok | Değişmedi |
| Kitsu | 5 atlandı | Güvenilir Kitsu eşlemesi bulunamadı | Değişmedi |

## Doğrulama

- `git diff --check`: temiz.
- Eşleştirme mantığı, rapordaki gerçek başlık çiftleriyle bağımsız bir Python portunda sınandı (7/7 beklenen sonuç).
- Android/Gradle derlemesi ve JUnit testleri bu ortamda çalıştırılamadı (JDK yok). `CrossSyncIdentityGuardTest` eklendi; derleme ve testin cihazda/CI'da çalıştırılması gerekiyor.
