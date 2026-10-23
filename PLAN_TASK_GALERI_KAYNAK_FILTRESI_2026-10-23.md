# PLAN & TASK — Galeri Kaynak (Source) Filtresi · 2026-10-23

**Dal:** `arena/16cec2c0-kitsugi-beta` · **Commit:** `4fa39b5`

## Amaç

Açılır resim galerisinin iki bölümünde kaynak (API) bilgisini görünür ve **filtrelenebilir** yapmak:

1. Yatay mod → sağ panel → **"Diğer Resimler"** sekmesi
2. Dikey mod → **ⓘ bilgi** butonu → "Resim Detayları" levhası

Referans görünüm: detay sayfasındaki "Resimler" levhasının `Kaynak` bölümü
(logo + kaynak adı + görsel sayısı, en üstte "Tümü").

## Görevler

- [x] `KitsugiImageGalleryDialog` → `initialSource: String?` parametresi
- [x] Kaynak gruplama (`availableSources`): `ignoreCase`, alfabetik, adet sayımı
- [x] `selectedSourceFilter` durumu + `sourceScopedItems` zinciri
- [x] Kategori sekmeleri ve sayıları kaynak kapsamına göre yeniden hesaplama
- [x] Geçersiz kategori → otomatik "Tümü"ye dönüş (boş pager koruması)
- [x] Sayfa 0'a dönüş efektini kategori **+** kaynak değişimine bağlama
- [x] Yatay panel "Diğer Resimler": Kaynak listesi + Diğer Resimler başlığı + görseller
- [x] Dikey detay levhası: Kaynak listesi (seçimde levha kapanır, filtre uygulanır)
- [x] Etkin kaynak çipi (logo + ad + ✕) — kategori çubuğunun başında
- [x] Tek kaynaklı galerilerde listeyi gizleme
- [x] `MediaEntryDetailPage` / `ApiResultDetailPage`: `activeGallerySource` + `initialSource`
      (kaynak seçicisinden açılınca tüm galeri verilir, galeri içinden kaynak değiştirilebilir)
- [x] Galeri kapanınca `activeGallerySource = null`
- [x] i18n: `gallery_source_all`, `gallery_clear_source_filter` (TR + EN)
- [x] Rapor: `COZUM_RAPORU_GALERI_KAYNAK_FILTRESI_2026-10-23.md`

## Değişen dosyalar

```
app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiImageGalleryDialog.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/detail/MediaEntryDetailPage.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailPage.kt
app/src/main/res/values/strings.xml
app/src/main/res/values-en/strings.xml
```

## Doğrulama

- Parantez/süslü parantez dengesi betikle kontrol edildi (3 Kotlin dosyası) — OK
- `strings.xml` / `values-en/strings.xml` XML olarak ayrıştırıldı, yinelenen anahtar yok — OK
- `assembleDebug`: bu ortamda **Android SDK/JDK bulunmadığı için çalıştırılamadı**
  → telefonda derleme sonrası hata çıkarsa log ile düzeltilecek

## Sonraki adım (opsiyonel)

- [ ] Detay levhasına `Tür` (kategori) bölümü eklenmesi (şu an kategori çipleri kullanılıyor)
- [ ] Kaynak satırlarına uzun basınca o kaynağın tüm görsellerini gösterme kısayolu
- [ ] Kaynak filtresinin `rememberSaveable` ile ekran döndürmede korunması
