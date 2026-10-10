# Plan / Task — Türkçe manga kaynakları ve okuyucu

Tarih: 2026-10-10
Dal: arena/74b08ba2-kitsugi-beta
Başlangıç commit: 4968d7191e552dd46add743b84f6077ca3f332ce

## Hedef

Oku aksiyonunu İzle gibi kompakt göstermek; Türkçe kaynaklarda doğru eser arama → detay → bölüm listesi → bölüm içeriği zincirini güvenilir hale getirmek. Manga/manhwa görsel okuyucusu ile light novel metin okuyucusunun farklı gereksinimleri olduğunu dikkate almak.

## Durum açıklaması

Aşağıda işaretli maddeler kod değişikliğinin yazıldığını belirtir; cihazda doğrulanmış başarı anlamına gelmez. Bu çalışma henüz tüm kaynakların sorunsuz çalıştığı bir sürüm değildir. APK üretilmedi. Java/Android ortamı olmadığı için Gradle testleri başlayamadı. Manga sitelerine canlı erişim olmadığı için kaynak bazında uçtan uca test yapılmadı.

## 1. Yazılan ortak düzeltmeler

- [x] API ve kayıtlı medya detayındaki Oku butonundan tam genişlik zorlamasını kaldır.
- [x] Sayfa durumunu Compose tarafından gözlemlenebilir hale getir.
- [x] Prefetch işlerinin kayıt/bitiş yarışını azalt; eski işin yeni iş kaydını silmesini engelle.
- [x] Bölüm geçişinde eski sayfa listesi isteğini iptal et; kayıtlı sayfayı sınırla ve ilk yüklemeyi başlat.
- [x] Mihon resim yüklemesini HttpSource.getImage üzerinden kaynağın kendi istek zincirine bağla.
- [x] Mihon baseUrl erişimini reflection yerine public sözleşmeden yap.
- [x] Kotatsu ağına çerez desteği ve source tag üzerinden parser interceptor yönlendirmesi ekle.
- [x] Kotatsu preview adresini gerçek görsel yerine kullanmayı bırak; getPageUrl çözümlemesini çalıştır.
- [x] Kotatsu manga/bölüm/sayfa metadata'sını sınırlı önbelleklerde koru.
- [x] Kotatsu bölüm sırasını okuyucu sözleşmesine uyarla; durum/tür bilgisini taşı.
- [x] Ardışık Kotatsu sayfalama isteklerinde gerçek sonuç sayısına göre offset sakla.
- [x] Kaynak hatalarının boş başarılı sonuç gibi gizlenmesini ilgili adapter yollarında kaldır.
- [x] Arama durumlarını stableSourceKey ile ayır; kaynak başına zaman bütçesi ekle.
- [x] Sonuç kartını gerçek kaynak nesnesiyle aç; görünen addan kaynak tahminini kaldır.
- [x] Manga açıklamalarındaki HTML'yi düz metne çevir.
- [x] Önbellekte boş/HTML/decode edilemeyen görselleri reddet; yazma hatalarını ilet.
- [x] Sağlık kontrolünde URL varlığı yerine ilk görselin indirilebilir/decode edilebilir olmasını kontrol et.

## 2. Araştırma ve test hazırlığı

- [x] Uygulamanın Kotatsu pin'i f287c414a6 üzerinden 121 Türkçe parser bildirimini statik envanterle.
- [x] MangaParser, MangaPage, MangaChapter, OkHttpWebClient, Madara, MangaReader ve paginator sözleşmelerini incele.
- [x] Envanteri yeniden üreten scripts/audit_manga_parsers.py dosyasını ekle.
- [x] MangaPageStateTest JVM regresyon testlerini ekle.
- [x] MangaCacheTest Android regresyon testlerini ekle.
- [x] Değişen Kotlin dosyaları ve yeni testlerde sözdizimi kontrolü yap: 14 dosya, parse hatası yok. Bu derleme/typecheck değildir.
- [x] git diff --check çalıştır: geçti.
- [ ] JVM testlerini çalıştır ve sonuçlarını doğrula: Java eksikliği nedeniyle başlayamadı.
- [ ] Android instrumentation testlerini çalıştır.
- [ ] Debug APK derle; telefon/tablet/TV üzerinde test et.

## 3. Eksik altyapı — öncelikli

- [ ] Kotatsu JS değerlendirme desteğini gerçek bir uygulamayla tamamla; zaman aşımı, iptal ve kaynak temizliği testlerini ekle.
- [ ] WebView request/URL interception desteğini uygula; doğrulama gerektiren kaynaklarda kullanıcıya açık akış sun.
- [ ] Görsel yeniden çizim/şifre çözme isteyen parser'lar için gerçek bitmap dönüşüm desteğini uygula.
- [ ] Kapak görsellerinin Coil isteklerinde kaynağa özel header/Referer/çerez gereksinimlerini tamamla.
- [ ] Svelte/Inertia yedek motorlarında bölüm ve sayfa paritesini doğrula.
- [ ] Kotatsu metadata cache dışında kalan nesnelerde stub fallback davranışını doğrula/iyileştir.
- [ ] Rastgele sayfa atlama ve tek sayfalı kaynaklarda offset/son sayfa davranışını doğrula.
- [ ] Android BitmapFactory'nin desteklemediği formatlar için ek decoder ihtiyacını değerlendir.
- [ ] Light novel desteğini ayrı incele: metin bölüm çıkarma, biçimlendirme, okuyucu ve ilerleme kaydı. Bu patch light novel desteğini tamamlamaz.

## 4. Kaynak bazında doğrulama matrisi

Envanterdeki her kaynak için, o kaynakta gerçekten bulunan bir eserle aşağıdaki sonuçları kaydet:

| Kaynak / sürüm | Alan adı | Test eseri | Arama ve doğru URL | Bölüm sayısı/sırası | İlk/orta/son içerik | Kapak | Engel/hata | Sonuç |
|---|---|---|---|---|---|---|---|---|
| TortugaCeviri | canlı doğrulanacak | Berserk (mevcudiyeti doğrulanacak) | bekliyor | bekliyor | bekliyor | bekliyor | bekliyor | doğrulanmadı |
| Diğer 120 Kotatsu TR bildirimi | envantere bak | kaynakta mevcut örnek seçilecek | bekliyor | bekliyor | bekliyor | bekliyor | bekliyor | doğrulanmadı |
| Cihazdaki Mihon APK kaynakları | sürüm ve liste cihazdan alınacak | kaynakta mevcut örnek seçilecek | bekliyor | bekliyor | bekliyor | bekliyor | bekliyor | doğrulanmadı |

- [ ] Kaynakların güncel alan adlarını ve yönlendirmelerini doğrula.
- [ ] Eser adı varyasyonları, Türkçe karakterler ve alternatif adlar için fixture/test ekle.
- [ ] Yanlış eşleşme ve kaynak karışması olmadığını doğrula.
- [ ] Bölüm sayısını/sırasını web sitesindeki listeyle karşılaştır.
- [ ] İlk, orta ve son bölümde gerçek sayfa/metin içeriğini kontrol et.
- [ ] 403/429, captcha, timeout, HTML challenge, bozuk görsel ve çevrimdışı durumları test et.
- [ ] Hızlı sorgu/bölüm değişimi, kaydırıp geri dönme, retry ve cache'ten tekrar açmayı test et.
- [ ] Sonuçları çalışıyor / kısmi / engelli / bozuk olarak açıkça kaydet; boş aramayı tek başına kaynak bozukluğu kanıtı sayma.

## Kabul kriteri

Bir kaynak ancak doğru eser → gerçek bölüm listesi → okunabilir içerik zinciri cihazda doğrulandığında çalışıyor olarak işaretlenir. Her eserin her kaynakta bulunması beklenmez. Desteklenmeyen veya erişilemeyen kaynaklar sonsuz yükleme/boş ekran yerine açıklayıcı hata göstermelidir. Tüm kaynaklar test edilmeden genel sorunsuzluk iddiası yapılmaz.

## Paket ve kullanım

ZIP'teki app/, scripts/, docs/ ve bu plan dosyası depo köküne göre aynı yolları korur. Bu paket tam proje veya APK değildir; değişen/yeni dosyaların mevcut tam içerikleridir. Kendi çalışmanızı yedekleyip diff karşılaştırması yaptıktan sonra dosyaları uygulayın. ZIP içindeki PACKAGE_MANIFEST.json dosya listesini ve SHA-256 değerlerini içerir.

Ayrıntılı analiz: docs/MANGA_PIPELINE_AUDIT_2026-10-10.md
Kaynak envanteri: docs/MANGA_TR_PARSER_INVENTORY.md
