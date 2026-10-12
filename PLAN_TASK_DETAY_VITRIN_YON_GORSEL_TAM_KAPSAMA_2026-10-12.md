# PLAN/TASK — Detay Vitrini: Yön ⇄ Görsel Otomatik Eşleşmesi ve TAM Kapsama

Tarih: 2026-10-12
Dal: `arena/ff18ddb8-kitsugi-beta`
Etkilenen dosyalar:
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailHero.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailHeroSection.kt`

## Sorun (ekran görüntülerinden)

1. **Yatay mod:** Sol paneldeki dar-uzun görsel alanına **yatay arka plan** basılıyordu;
   görsel iki yandan "burnundan" kırpılıyor, alanın şekli görselle uyuşmuyordu.
2. **Dikey mod:** Geniş-kısa banda bazen oran uyumsuzluğu nedeniyle "tam görünür +
   bulanık dolgu" (CONTAIN_WITH_FILL) uygulanıyor, görsel alanı TAM kaplamıyordu.
3. Eski kural her iki yönde de **aynı** görseli (önce yatay arka plan) seçiyordu; oysa bir
   yapımın görsel çifti sabittir: dikey-uzun-dar **POSTER** + yatay-geniş-kısa **ARKA PLAN**.
   Yön değişince gösterilen görsel de otomatik olarak eşini göstermeli.

## Yeni Kural

| Ekran yönü | Gösterilen görsel (öncelik) | Görsel alanının şekli |
|------------|-----------------------------|------------------------|
| YATAY      | Dikey POSTER (TMDB/Fanart kalite sırası → kaynak poster → dikey galeri) | 2:3 uzun-dar kutu |
| DİKEY      | Yatay ARKA PLAN (TMDB/Fanart kalite sırası → kaynak backdrop → yatay galeri) | 16:9 geniş-kısa bant |

- Görsel alanının en-boy oranı, yönün varsayılan oranıyla başlar; görsel yüklenince
  `onImageAspectResolved` ile **gerçek orana kilitlenir** → kutu, gösterdiği görselle
  birebir aynı şekle gelir.
- Güvenli aralık: yatay modda oran `[0.5, 1.6]`, dikey modda `[1.0, 2.2]` (yedeğe düşen
  görsel tipi için taşmayı önler).
- Yükseklik tavanı: yatay modda ekran yüksekliği, dikey modda ekran yüksekliğinin %62'si.
- Görsel **daima `ContentScale.Crop` ile alanı TAM kaplar**; "bulanık dolgu / boşluk"
  (CONTAIN_WITH_FILL çift katmanı) detay vitrinlerinden KALDIRILDI. Kutu oranı görsele
  kilitlendiği için kırpma kaybı pratikte sıfırdır.
- Eşlerden biri yoksa (ör. bazı manga kayıtlarında backdrop yok) diğeri gösterilir.

## Teknik Notlar

- `heroImageCandidates` / `heroArtworkFit` / `heroBandHeightDp` (ana sayfa vitrini ve
  birim testleri) DEĞİŞMEDİ; yalnızca detay sayfası hero'ları yeni kurala geçti.
- `DETAIL_HERO_BANNER_RATIO` sabiti kaldırıldı (artık tek sabit oran yok, yön+gerçek oran var).
- `HeroImageSelectionTest` dokunulmadan geçerli kalır (detay hero planını test etmez).

## Doğrulama

- Sandbox'ta Java/Gradle bulunmadığı için derleme çalıştırılamadı; bunun yerine:
  - Her iki dosyada ayraç/dizi dengesi ve kullanılmayan import/bağımlılık taraması yapıldı
    (kaldırılan `heroArtworkFit`, `BiasAlignment`, `alpha` vb. hiçbir yerde referanssız).
  - Düzenlenen bölgeler satır satır gözden geçirildi; `Dp / Float`, `coerceIn`, `remember`
    anahtarları ve paket-içi `internal` yardımcılar (`isVerticalGalleryItem` vb.) doğrulandı.
- Elle test senaryosu: Frieren detayı → dikeyde 16:9 bantta backdrop tam kaplama;
  yatayda 2:3 kutuda poster tam kaplama; yön değiştirince görsel otomatik eşine geçer.
