# Task Plan — Listem / Tüm Kaynaklar

## Amaç
Listem ekranına, bağlı kütüphaneleri tek görünümde birleştiren bir **Tümü** kaynağı eklemek; aynı içeriğin farklı platformlarda bulunmasını platform logolarıyla göstermek.

## Kapsam ve tamamlanan işler
- [x] Kaynak seçici alt sayfasına ve hızlı kaynak çiplerine **Tümü** seçeneğini ekle.
- [x] AniList, MyAnimeList, Simkl, Kitsu ve Shikimori kayıtlarını birleşik görünümde topla.
- [x] Ortak medya kimliği / başlık eşleştirmesiyle yinelenen kayıtları tek karta indir; her kaydın geldiği platform rozetlerini koru.
- [x] Rozetleri ortak medya kartı üzerinden kompakt, rahat, ayrıntılı, kare grid ve minimalist kart düzenlerine aktar.
- [x] Dar kartlarda kaynak rozetlerinin taşmaması için satır akışını (FlowRow) kullan.
- [x] Birleşik görünümde aşağı çekerek yenilemede bağlı platformların senkronizasyonunu başlat.
- [x] Birleşik kütüphane gruplaması, platform kimliği çakışmaları ve kaynak takma adları için unit test ekle.

## Doğrulama
- `git diff --check`: başarılı.
- Unit test/build: bu çalışma ortamında Java/JDK bulunmadığından çalıştırılamadı (`JAVA_HOME` tanımlı değil ve `java` PATH'te yok).

## Arşiv içeriği
Bu arşiv, değişen/eklenen Kotlin dosyalarını orijinal repo dizin yapılarıyla ve bu plan dosyasıyla içerir.
