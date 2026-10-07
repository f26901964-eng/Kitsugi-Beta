# Keşfet / Tümü — Plan ve Görev Takibi

Tarih: 7 Ekim 2026
Depo: f26901964-eng/Kitsugi-Beta
Çalışma dalı: arena/9d72e96a-kitsugi-beta
Başlangıç commit'i: 33a90012bfc38edf88e3775c667125c8f40ae6c6

## Hedef ve kabul edilen tasarım

Keşfet kaynak seçicisine Tümü görünümü eklemek; AniList, MyAnimeList, TMDB, Simkl, Kitsu ve Shikimori içeriklerini aynı sayfada göstermek.

Kullanıcının son tasarım yönlendirmesi: Kaynakların kategorileri birbirine karıştırılmayacak. Her kaynak ayrı, logolu bir başlığa sahip olacak; o kaynağın trendleri, en iyileri, popülerleri, sezon içerikleri ve diğer desteklenen kategorileri kendi başlığının altında yer alacak. İlk düşünülen kaynaklar arası dönüşümlü şerit sıralaması terk edildi.

## 1. İnceleme ve tasarım

- [x] Mevcut kaynak seçicisini, ViewModel'i, kategori alanlarını ve gezinme akışını incele.
- [x] Tümü modunu altı gerçek API kaynağından ayır.
- [x] Platformlar arasında farklı anlamlara gelen payload alanlarını kaynak bazında eşle.
- [x] Kaynaklara ve kategorilere hızlı geçiş, kaynak alanını daraltma ve bağımsız hata gösterimi tasarla.

## 2. Veri ve yükleme

- [x] Altı kaynağı bağımsız işlerle yükle; tamamlanan kaynakları diğerlerini beklemeden göster.
- [x] Kaynak başına zaman sınırı ve hata/boş yanıt kontrolü ekle.
- [x] Başarılı bellek önbelleğini yeniden kullan; yenilemede mevcut içeriği koru.
- [x] Başarısız kaynak için bellek/disk önbelleğine dön; kaynak bazlı yeniden deneme sağla.
- [x] Kaynak değiştirme ve yenilemede eski işleri iptal et; eski yanıtların yeni duruma yazmasını önle.
- [x] Ayar değişikliklerinde birleşik kaynak durumunu temizle.
- [x] AniList'in kendi trend, sezon ve anime filmi kategorilerini yükle.
- [x] AniList ve Kitsu için gerçek puan sıralamalarını popülerlik sıralamalarından ayır.
- [x] Tümü modunda AniList → Kitsu fallback'ini kapat; eski fallback kayıtlarını kaynak sahipliğine göre filtrele.
- [x] Kaynak + medya türü + ID ile kimlik çakışmalarını önle.
- [x] Şerit, vitrin ve rastgele seçimde yetişkin içerik filtresini uygula.

## 3. Arayüz ve gezinme

- [x] Kaynak seçiciye Tümü seçeneğini ekle; tek kaynak seçimlerini koru.
- [x] Yedi seçenekli seçim penceresini kaydırılabilir yap.
- [x] Her kaynak için logo, renk vurgusu, açıklama, kategori/içerik sayısı içeren başlık oluştur.
- [x] Her kaynağın kategorilerini kendi başlığı altında grupla.
- [x] Sabit kaynak kısayolları, kategori kısayolları ve sayfanın başına dönüş ekle.
- [x] Kaynak alanlarını ayrı ayrı daralt/genişlet; daraltma durumunu saved state ile koru.
- [x] Kaynak başlığında yükleme, hata ve önbellek durumlarını göster.
- [x] Tümünü Gör işleminde kategoriyle birlikte gerçek kaynak bilgisini taşı.
- [x] Shikimori yayındaki anime devam sayfasının yanlışlıkla Kitsu'ya gitmesini düzelt.
- [x] Sayfasız Simkl/Kitsu trend listelerinin tekrar tekrar eklenmesini engelle.
- [x] TMDB'nin en iyi dizi kategorisinde anime sezon filtresinin açılmasını engelle.
- [x] TV için ortak kaynak alanlarını kullanan Tümü görünümünü bağla.

## 4. Tamamlanan doğrulamalar

- [x] 18 çekirdek mantık testi ekle.
- [x] Kotlin 2.0.21 ile izole JVM test çalıştırması: 18/18 başarılı.
- [x] Değiştirilen/yeni Kotlin dosyalarında sözdizimi kontrolü: yeni sözdizimi hatası bulunmadı.
- [x] git diff --check kontrolü: başarılı.
- [x] Teknik açıklamalar ve cihaz kontrol listesini docs/explore-all-sources.md dosyasına yaz.

Not: JVM kontrolü üretimdeki saf model/yükleme kodunu ve aynı test gövdelerini çalıştırdı. Android bağımlı model dosyalarından gerçek JikanSearchResult/MediaType tanımları alındı; JUnit assertion'ları Kotlin test assertion'larına uyarlandı. Bu, tam Android/Compose derlemesi veya cihaz testi değildir.

## 5. Açık görevler / yayın öncesi kapılar

- [ ] Android geliştirme ortamında standart Gradle birim testlerini çalıştır.
- [ ] Tam Android/Compose derlemesini tamamla ve APK üret.
- [ ] Telefon ve tablette dikey/yatay düzeni görsel olarak doğrula.
- [ ] TV'de D-pad odak, kaynak atlama, daraltma/genişletme ve geri dönüşü doğrula.
- [ ] Altı gerçek sağlayıcıdan canlı veri gelişini ve kategori sıralamalarını kontrol et.
- [ ] Geçerli kullanıcı TMDB API anahtarıyla Keşfet ve devam sayfalarını uçtan uca kontrol et.
- [ ] Uçak modu, bozuk/boş yanıt, tek kaynak hatası ve yeniden deneme senaryolarını cihazda doğrula.
- [ ] Hızlı kaynak değiştirme, yenileme ve yetişkin içerik ayarı değişikliklerini cihazda doğrula.
- [ ] Uzun listede kaynak/kategori kısayollarının hedef konumlarını ve kaydırma performansını kontrol et.

Engel: Sandbox'ta tam derleme denendi. Gradle 8.13 dağıtımının services.gradle.org üzerinden indirilmesi TLS/erişim hatasıyla durdu. Android SDK/emülatör ve canlı sağlayıcı erişimi bu ortamda doğrulanamadı. Paket APK içermez; yayınlanmaya hazır olduğu iddia edilmez.

Geliştirme ortamında çalıştırılacak komutlar:

```sh
./gradlew :app:testFossDebugUnitTest --tests '*AllSourcesExploreTest'
./gradlew :app:assembleFossDebug
```

## 6. Teslim paketi

- [x] Değişen ve yeni dosyaları Git durumundan belirle.
- [x] Bu plan/görev dosyasını pakete dahil et.
- Paketleme/yükleme doğrulaması teslim yanıtında bildirilir.

ZIP yalnızca değişiklik dosyalarını ve teslim notlarını içerir; tam proje yedeği değildir. Dosyalar repo köküne göre aynı dizin yollarıyla saklanır. Önce mevcut projenin yedeğini alın; özellikle AppRoot.kt gibi büyük dosyalarda yerel değişiklikler varsa otomatik üzerine yazmak yerine karşılaştırarak birleştirin. Eksiksiz dosya listesi, boyutlar ve SHA-256 değerleri ZIP içindeki MANIFEST.json dosyasındadır.
