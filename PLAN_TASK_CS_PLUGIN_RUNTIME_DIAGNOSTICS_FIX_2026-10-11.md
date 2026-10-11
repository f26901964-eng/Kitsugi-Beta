# PLAN_TASK — CloudStream Eklenti Runtime ve Tanı Düzeltmesi

**Tarih:** 2026-10-11  
**Sürüm:** 2.4.247  
**Dal:** `arena/05d4b0b3-kitsugi-beta`  
**Durum:** Kod değişiklikleri tamamlandı; Gradle testleri Java/JAVA_HOME bulunmadığı için çalıştırılamadı.

## Sorun / Bulgular

- Tanı raporlarında birden fazla eklentide `NoClassDefFoundError: kotlinx.serialization.KSerializer` görülüyordu.
- CS3 eklentileri ayrı DEX/classloader üzerinden çalıştığından R8, dinamik eklenti DEX'lerinden gelen referansları göremeyebilir; Serialization sınıfları küçültme/yeniden adlandırmadan etkilenebilirdi.
- Eklenti yükleme hataları tanı sonucuna tam aktarılmıyordu; HTTP 401 ve hesap isteyen kaynaklar boş arama gibi görünebiliyordu.
- Sağlayıcı alan adları, DNS erişilebilirliği, WAF durumu ve hesap gereksinimleri uzaktaki hizmetlere bağlıdır. Doğrulanmamış domain değişikliği yapılmamalıdır.

## Plan ve Uygulanan Görevler

- [x] Serialization Core'u host uygulamaya açık runtime bağımlılığı olarak ekle.
- [x] Serialization paketini R8'in silme/yeniden adlandırmasına karşı koru.
- [x] CS3 `PathClassLoader` parent'ını CloudStream `MainAPI`'yi tanımlayan host classloader'ına bağla.
- [x] Eklenti kodu çalışmadan önce `MainAPI`, `KSerializer` ve Serialization `Json` görünürlüğünü denetle; eksik sınıfları canlı tanı izine yaz.
- [x] Eklenti loader hatalarını in-app tanı sonucuna taşı.
- [x] Runtime/linkage, giriş/hesap, parser, generic HTTP 403 ve CF/WAF ayrımlarını iyileştir; yeni kodlar `KTS-CSP-9109` ve `KTS-CSP-9110`.
- [x] Tanı ekranı, Markdown raporu ve TR/EN metinlerini güncelle.
- [x] Unit testleri ekle; sürüm ve release notes'u `2.4.247` yap.
- [ ] JDK/JAVA_HOME bulunan ortamda `:app:testFossDebugUnitTest` ve release derlemesini çalıştır.
- [ ] Android cihazda CS3 eklenti yükleme/arama tanısını tekrar çalıştır ve `KSerializer` hatasının giderildiğini doğrula.

## Değişen Dosyalar

- `app/build.gradle.kts`
- `app/proguard-rules.pro`
- `app/src/main/java/com/kitsugi/animelist/data/cloudstream/CsPluginLoader.kt`
- `app/src/main/java/com/kitsugi/animelist/data/cloudstream/CloudStreamErrorClassifier.kt`
- `app/src/main/java/com/kitsugi/animelist/data/cloudstream/CsPluginDiagnosticRunner.kt`
- `app/src/main/java/com/kitsugi/animelist/data/diagnostics/PlatformErrorCatalog.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/settings/CsPluginDiagnosticScreen.kt`
- `app/src/main/res/values/platform_error_codes.xml`
- `app/src/main/res/values-en/platform_error_codes.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`
- `app/src/test/java/com/kitsugi/animelist/data/cloudstream/CloudStreamErrorClassifierTest.kt`
- `app/src/test/java/com/kitsugi/animelist/data/cloudstream/CsPluginDiagnosticRunnerTest.kt`
- `RELEASE_NOTES.md`
- `release_notes_v2.4.247.md`
- `PLAN_TASK_CS_PLUGIN_RUNTIME_DIAGNOSTICS_FIX_2026-10-11.md` (bu plan/task dosyası)

## Doğrulama

- `git diff --check`: başarılı.
- `scripts/check_localization_resource_parity.py`: başarılı; 6.417 string ve 4 array eşleşiyor.
- Değiştirilen dört Android XML kaynak dosyasının ayrıştırması ve yeni kod-resource referansları: başarılı.
- Gradle unit test/build: çalıştırılamadı; ortamda `java` komutu ve `JAVA_HOME` yok.

## Kapsam Dışı / Dış Bağımlılıklar

- Güncel upstream domain listesi isteği 404 döndüğünden yeni alan adları tahmin edilmedi.
- DNS/UnknownHost, sağlayıcının kapalı olması, WAF/Cloudflare engeli ve Exxen/Tabii gibi sağlayıcıların hesap bilgisi talepleri yalnızca uygulama koduyla garantiyle giderilemez. Uygulama bunları artık daha doğru sınıflandırır; geçerli domain/hesap bilgisi ayrıca gerekir.
