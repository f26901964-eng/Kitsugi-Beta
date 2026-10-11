# Plan / Task — Eklentiler Sayfası Tema Uyumu ve Arayüz Düzenlemesi

**Tarih:** 11.10.2026
**Alan:** Kitsugi Android — Eklentiler & Kaynaklar
**Durum:** Uygulandı; Android derlemesi bu çalışma ortamında doğrulanamadı.

## Amaç

Eklentiler ve kaynaklar ekranını Kitsugi'nin seçili vurgu rengi/gradyanıyla tutarlı hale getirmek; Torrent & Akış, Video Kaynakları ve Manga Kaynakları bölümlerini daha kompakt, modern ve kolay taranır bir görsel dilde birleştirmek. Mevcut kurulum, etkinleştirme, kaldırma ve güncelleme davranışları korunur.

## Yapılanlar

- Ana eklenti sekmelerinin seçili durumu özel vurgu fırçasını kullanacak şekilde güncellendi; repo/kurulu alt sekmeleri için Video ve Manga'da ortak, kompakt bir seçici oluşturuldu.
- P2P ve eklenti anahtarları, bölüm ikonları, güncelleme aksiyonları ve durum vurguları seçili tema rengi/gradyanıyla hizalandı.
- Eklenti/repo kartlarına tutarlı kenarlıklar eklendi; kart içi boşluklar, ikon ölçüleri ve bazı satır yükseklikleri sıkılaştırıldı.
- Eklenti ikonu alınamadığında sarı emoji yerine tema vurgulu bir yedek ikon kullanıldı.
- Hata ve silme gibi semantik durum renkleri korundu; hata bildirimi sabit renk kodları yerine Material tema hata rengine bağlandı.

## Değişen kaynak dosyaları

1. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiAddonsSettingsDialog.kt`
2. `app/src/main/java/com/kitsugi/animelist/ui/components/addons/CloudstreamAddonShared.kt`
3. `app/src/main/java/com/kitsugi/animelist/ui/components/addons/CloudstreamExtensionTab.kt`
4. `app/src/main/java/com/kitsugi/animelist/ui/components/addons/CsInstalledPluginRow.kt`
5. `app/src/main/java/com/kitsugi/animelist/ui/components/addons/CsPluginRow.kt`
6. `app/src/main/java/com/kitsugi/animelist/ui/components/addons/CsRepoCard.kt`
7. `app/src/main/java/com/kitsugi/animelist/ui/components/addons/MangaExtensionTab.kt`
8. `app/src/main/java/com/kitsugi/animelist/ui/components/addons/StremioAddonTab.kt`
9. `app/src/main/java/com/kitsugi/animelist/ui/components/p2p/P2pSettingsSection.kt`

## Kabul / regresyon kontrol listesi

- [ ] Düz vurgu rengiyle üst sekmeler, repo/kurulu seçicileri, anahtarlar ve eylemler okunaklı görünür.
- [ ] Özel iki renkli gradyan açıkken seçili sekme, seçili alt sekme, anahtar ve vurgu ikonlarında gradyan görünür.
- [ ] Açık, koyu ve AMOLED paletlerinde kart yüzeyleri ve metin kontrastı korunur.
- [ ] Repo ekleme, eklenti kurma/güncelleme, açma-kapama ve kaldırma davranışları değişmeden çalışır.
- [x] `git diff --check` temiz geçti.
- [ ] `:app:compileGmsDebugKotlin` veya eşdeğer Android derlemesi başarılı olur.

## Doğrulama notu

Bu sandbox'ta `java` bulunmadığı ve `JAVA_HOME` tanımlı olmadığı için Gradle/Kotlin derlemesi çalıştırılamadı. Derleme, Android SDK/JDK kurulu geliştirme ortamında tamamlanmalıdır.
