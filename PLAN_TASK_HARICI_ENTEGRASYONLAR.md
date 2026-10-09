# Harici Entegrasyonlar — Plan ve Görev

**Tarih:** 2026-10-09  
**Kapsam:** TMDB, MDBList, Fanart.tv ve mevcut entegrasyon ayarlarının uygulamadaki kullanım yolları

## Amaç

Harici entegrasyon ayarlarının yalnızca ekranda görünmesini değil, ilgili özelliklerin gerçekten bu ayarlara uymasını sağlamak; kişisel API anahtarlarını anlaşılır biçimde doğrulatıp kaydetmek; gereksiz istekleri ve günlüklerde anahtar sızıntısını azaltmak.

## Kapsam ve kararlar

- TMDB ayrıntı, görsel, yayın ağı/izleme sağlayıcısı, fragman, oyuncu-ekip, koleksiyon ve benzer içerik yollarındaki ayar kapıları uygulandı.
- TMDB kişisel anahtarı ve Fanart.tv kişisel anahtarı, doğrulama başarılı olmadan kaydedilmez. Alanı temizlemek ayrı ve açık bir işlemdir; boş anahtar uygulamanın yerleşik anahtarına geri dönüş anlamına gelir.
- MDBList için IMDb kimliği önceliklidir; IMDb kimliği bulunmadığında pozitif TMDB ID ile yedek arama yapılır. Puan yanıtlarının yeni dizi ve eski üst-seviye alan biçimleri ele alınır.
- Ek puan sağlayıcısı eklenmedi: MDBList birden fazla kaynağın puanlarını zaten topluyor; ikinci, örtüşen servis kullanıcı ayarlarını ve bakım yükünü gereksiz artırır.
- AniSkip/kaydırma sınırı davranışı mevcut haliyle bırakıldı; bu değişiklik paketinde yeni servis veya AniSkip çalışma mantığı eklenmedi.
- API anahtarı/token içeren istek URL'leri günlüklerde maskelenir.

## Uygulanan görevler

- [x] TMDB ve Fanart.tv anahtar alanlarını taslak olarak tut; doğrula ve başarılıysa kaydet.
- [x] TMDB/Fanart.tv anahtarları için açık temizleme kontrolü ve istek sürerken alanları devre dışı bırakma.
- [x] MDBList anahtarını doğrulama ve kayıtta boşlukları kırpma.
- [x] MDBList URL'sinde parametreleri güvenli kodlama; IMDb → TMDB kimlik yedeği; yanıt ayrıştırma ve önbellek iyileştirmeleri.
- [x] TMDB özellik ayarlarını istek/sonuç alanlarına bağlama; keşfet sayfasında modern TMDB bölümünü ayarla anlık uyumlu gösterme.
- [x] TMDB televizyon ağlarını detay kartında gösterme.
- [x] İstek/günlük URL'lerinde hassas anahtarları maskeleme.
- [x] MDBList ayrıştırma/URL ve TMDB ayrıntı özellikleri için test kaynakları ekleme.
- [ ] Uygulama testleri ve manuel cihaz doğrulaması — kullanıcıya bırakıldı.

## Değişen dosya grupları

- **İstemciler ve istek güvenliği:** `FanartApiClient.kt`, `KitsugiApiBase.kt`, `SensitiveUrlRedactor.kt`, `MdbListClient.kt`, `TmdbApiClient.kt`, `TmdbMediaDetailClient.kt`.
- **Entegrasyon/ayar kapıları:** `KitsugiEpisodeRatingsRepository.kt`, `KitsugiMediaTabsClient.kt`, `SettingsDataStore.kt`, `KitsugiIntegrationsSettingsDialog.kt`.
- **Detay ve keşfet arayüzü:** `ApiDetailTabContents.kt`, `ApiResultDetailViewModel.kt`, `EntryDetailTabContents.kt`, `KitsugiDetailComponents.kt`, `MediaEntryDetailViewModel.kt`, `ExploreScreen.kt`, `ExploreViewModel.kt`.
- **Yerelleştirme ve test kaynakları:** `values/strings.xml`, `values-en/strings.xml`, `MdbListClientTest.kt`, `TmdbMediaDetailClientTest.kt`.

## Kullanıcı için önerilen doğrulama sırası

1. TMDB'de geçerli anahtarı doğrula/kaydet; hatalı anahtarın kaydedilmediğini ve alanı temizleyince yerleşik anahtara dönüldüğünü kontrol et.
2. TMDB ayrıntı, görsel, fragman, yapım şirketi/ağ, oyuncu-ekip, benzer içerik ve koleksiyon anahtarlarını ayrı ayrı kapatıp ilgili alan/isteklerin etkisini doğrula. Modern keşfet seçeneğinin sayfayı anında değiştirdiğini kontrol et.
3. MDBList'te geçerli ve geçersiz anahtarı dene; IMDb eşleşmesi olmayan ancak TMDB kimliği bulunan içerikte puanların geldiğini kontrol et.
4. Fanart.tv anahtarını doğrula, kaydet ve temizle; galeri/logo için Fanart.tv kapalıyken TMDB artwork ayarının bağımsız çalıştığını doğrula.
5. Detay sayfasında varsa yayıncı ağları ve MDBList puanlarını; günlüklerde API anahtarlarının görünmediğini kontrol et.
6. AniSkip/kaydırma sınırı kontrollerinin bu değişikliklerden etkilenmediğini doğrula.

## Doğrulama durumu

- `git diff --check` temiz.
- Türkçe ve İngilizce `strings.xml` XML olarak ayrıştırıldı; kaynak koddaki `R.string.*` başvurularında eksik anahtar bulunmadı.
- Gradle testleri/build bu turda çalıştırılmadı; kullanıcı testleri kendisi çalıştıracağını belirtti. Bu nedenle derleme ve çalışma zamanı sonucu henüz doğrulanmış sayılmaz.
