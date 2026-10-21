# PLAN & TASK — İndirilenler: Altyazı / Resim Silme Onarımı
**Sürüm:** v2.4.332 · **Tarih:** 2026-10-21 · **Durum:** ✅ Kod tamam — cihaz testi kullanıcıda
**İlgili rapor:** `KOK_NEDEN_RAPORU_INDIRMELER_SILME_2026-10-21.md`

---

## 1. Problem özeti
| Sekme | Eski davranış | Beklenen |
|---|---|---|
| Videolar | ✅ Siliniyor | — |
| Altyazılar | ❌ Silinmiyor (kart geri geliyor, geri bildirim yok) | Silinmeli + sonuç bildirilmeli |
| Resimler | ❌ Tekli/grup silme hiçbir şey yapmıyor | Silinmeli + liste anında güncellenmeli |

Kök neden: dosyalar `MediaStore` ile paylaşılan depolamaya yazılıyor; Android 10+
Scoped Storage'da bu dosyalar için `File.delete()` **istisna fırlatmadan `false` dönüyor**.

---

## 2. Görev listesi

### A. Altyapı
- [x] `core/storage/MediaStoreFileDeleter.kt` oluştur
  - [x] `DISPLAY_NAME` (+`RELATIVE_PATH`) ile kayıt arama (DATA sütunu kaldırıldı)
  - [x] `OWNER_PACKAGE_NAME` ile sahiplik denetimi
  - [x] Sahiplik varsa `ContentResolver.delete`
  - [x] Sahiplik yoksa `foreignUris` + `MediaStore.createDeleteRequest` (API 30+)
  - [x] `notifyChange` ile galeri/dosya yöneticisi tazeleme
  - [x] Hiçbir adımda istisna fırlatmama (`Outcome`)

### B. Görseller
- [x] `KitsugiImageDownloadHelper.deleteImageFile(context, file)`
  - [x] Index kaydı (MediaStore URI / SAF tree) üzerinden silme
  - [x] Aynı adlı tüm kopyaları silme (Downloads / Pictures / app-external)
  - [x] `File.delete()` başarısızsa MediaStore-by-name yedeği
  - [x] Sonuç doğrulaması (`file.exists()`)
  - [x] Index kaydını düşürme ("indirildi" rozeti kalkar)
- [x] `deleteLocalFile` → MediaStore yedeği + `RequiresSystemConsent(uris)`
- [x] `deleteViaMediaStoreByPath` → DATA sorgusundan arındırıldı
- [x] Toplu silmede N+1 sorgu önlemi (hayalet satır temizliği tek sefer)

### C. Altyazılar
- [x] `DownloadStorage.deleteSubtitle(context, file): SingleDeleteResult`
  - [x] Video kökleri + `Kitsugi/Subtitles` + `cacheDir/subtitles` tüm kopyalar
  - [x] `File.delete()` → MediaStore-by-name → sonuç doğrulama
  - [x] `consentUris` ile sistem onayı desteği
- [x] `DownloadStorage.clean(SUBTITLES)` yeni yolu kullanıyor (Ayarlar → Depolama)

### D. Arayüz (`DownloadsScreen`)
- [x] Görsel tekli kart silmesi → `deleteImageFiles`
- [x] Görsel grup silmesi → `deleteImageFiles` + **onay penceresi**
- [x] Altyazı silmesi → `deleteSubtitleItem` (başarısızsa öğe geri eklenir + neden toast'ı)
- [x] İyimser liste güncellemesi + her durumda diskle senkron tazeleme
- [x] Sistem onayı köprüsü (`StartIntentSenderForResult`)
- [x] İptal durumunda "Silme iptal edildi" bildirimi

### E. Galeri (`KitsugiImageGalleryDialog`)
- [x] `RequiresSystemConsent.uris` toplama
- [x] Sistem onayı sonrası `onItemsDeleted`
- [x] Onay açıldıysa gereksiz hata toast'ı bastırıldı

### F. Metinler / sürüm
- [x] Yeni string'ler (`values`, `values-en`):
      `downloads_delete_group_title`, `downloads_delete_group_message`,
      `downloads_delete_confirm_prompt`, `downloads_delete_cancelled`,
      `downloads_delete_permission_needed`, `downloads_subtitle_delete_failed`
- [x] `appVersionName` → `2.4.332`
- [x] `release_notes_v2.4.332.md`
- [x] `KOK_NEDEN_RAPORU_INDIRMELER_SILME_2026-10-21.md`

### G. Test (kullanıcıda)
- [ ] Android 11+ cihazda İndirilenler → **Resimler** → tekli sil
- [ ] İndirilenler → **Resimler** → grup sil (onay penceresi)
- [ ] İndirilenler → **Altyazılar** → tekli sil
- [ ] Galeri içinden sil (indirilen görsel)
- [ ] Uygulama yeniden kurulduktan sonra eski görsellerin silinmesi (sistem onayı diyaloğu)
- [ ] Ayarlar → Depolama → "Altyazıları sil" / "Görselleri sil"
- [ ] Silme sonrası uygulamayı kapat-aç: silinen öğeler geri gelmiyor mu?
- [ ] Android 10 (API 29) cihazda regresyon kontrolü

---

## 3. Dosya değişiklikleri
```
YENİ  app/src/main/java/com/kitsugi/animelist/core/storage/MediaStoreFileDeleter.kt
MOD   app/src/main/java/com/kitsugi/animelist/core/storage/DownloadStorage.kt
MOD   app/src/main/java/com/kitsugi/animelist/utils/KitsugiImageDownloadHelper.kt
MOD   app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadsScreen.kt
MOD   app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiImageGalleryDialog.kt
MOD   app/src/main/res/values/strings.xml
MOD   app/src/main/res/values-en/strings.xml
MOD   app/build.gradle.kts                      (2.4.331 → 2.4.332)
YENİ  KOK_NEDEN_RAPORU_INDIRMELER_SILME_2026-10-21.md
YENİ  release_notes_v2.4.332.md
YENİ  PLAN_TASK_INDIRMELER_SILME_FIX_2026-10-21.md
```

## 4. Riskler / geri alma
- Değişiklikler yalnızca silme akışında; indirme/oynatma koduna dokunulmadı.
- `MediaStoreFileDeleter` hata fırlatmaz → eski davranışa göre daha az çökme riski.
- Geri alma: `git revert <commit>` (tek commit).
