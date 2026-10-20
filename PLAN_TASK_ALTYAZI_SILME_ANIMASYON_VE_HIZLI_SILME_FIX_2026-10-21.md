# PLAN_TASK_ALTYAZI_SILME_ANIMASYON_VE_HIZLI_SILME_FIX_2026-10-21

##  Görev Özeti
İndirmeler ekranındaki altyazı silme butonundaki gereksiz animasyonu kaldırma ve hızlı silme durumunda oluşan state güncelleme bug'ını düzeltme.

## 🐛 Tespit Edilen Sorunlar

### 1. Gereksiz Animasyon
- Silme butonu tıklandığında genişleme animasyonu (yuvarlak → kapsül)
- Renk değişim animasyonu (koyu → kırmızı)
- "Sil" metni fadeIn/fadeOut animasyonu
- 2 aşamalı onay mekanizması (ilk tıklama genişlet, ikinci tıklama sil)
- 3.5 saniye otomatik kapanma
- **Kullanıcı Deneyimi:** Karmaşık ve yavaş, tek tıklamada silme bekleniyor

### 2. Hızlı Silme Bug'ı
- Kullanıcı hızlıca ardışık silme yaptığında dosya siliniyor ama ekranda görünmeye devam ediyor
- **Kök Neden:** `produceState` ile state yönetimi, hızlı güncellemelerde race condition
- `refreshSubsTrigger++` tetikleyicisi hızlı silme durumunda güvenilir değil
- Disk taraması (IO) tamamlanana kadar UI eski listeyi gösteriyor

## ✅ Çözüm

### Değişiklik 1: KitsugiAnimatedDeleteButton.kt
**Dosya:** `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiAnimatedDeleteButton.kt`

- ❌ Tüm animasyonları kaldır (genişleme, renk değişimi, metin animasyonu)
- ❌ 2 aşamalı onay mekanizmasını kaldır
- ❌ Hover/focus state animasyonlarını kaldır
- ✅ Basit `IconButton` kullan
- ✅ Tek tıklamada direkt silme
- ✅ Parametreleri basitleştir (`expandedWidth` ve `requireConfirm` kaldırıldı)

**Önceki:** 186 satır (animasyonlu, 2 aşamalı onay)
**Sonra:** 43 satır (basit, animasyonsuz)

### Değişiklik 2: DownloadsScreen.kt
**Dosya:** `app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadsScreen.kt`

#### State Yönetimi Değişikliği
```kotlin
// ÖNCEKI (produceState - yavaş, race condition)
val allSubtitles by produceState(
    initialValue = emptyList<DownloadedSubtitleItem>(),
    key1 = completedDownloadsKey,
    key2 = refreshSubsTrigger
) {
    val currentDownloadsSnapshot = AnimeDownloadManager.downloads.value
    value = withContext(Dispatchers.IO) {
        loadAllDownloadedSubtitles(context, currentDownloadsSnapshot)
    }
}

// SONRAKI (mutableStateOf + LaunchedEffect - hızlı, güvenilir)
var allSubtitles by remember { mutableStateOf(emptyList<DownloadedSubtitleItem>()) }

LaunchedEffect(completedDownloadsKey, refreshSubsTrigger) {
    val currentDownloadsSnapshot = AnimeDownloadManager.downloads.value
    allSubtitles = withContext(Dispatchers.IO) {
        loadAllDownloadedSubtitles(context, currentDownloadsSnapshot)
    }
}
```

#### Silme Callback Düzeltmesi
```kotlin
onDelete = {
    // 1. ANINDA listeden kaldır (UI hemen güncellenir)
    allSubtitles = allSubtitles.filter { it.file.absolutePath != subItem.file.absolutePath }
    
    scope.launch {
        val deleted = withContext(Dispatchers.IO) {
            subItem.file.exists() && subItem.file.delete()
        }
        if (deleted) {
            Toast.makeText(context, KitsugiStrings.get(R.string.altyazi_silindi_x, subItem.title), Toast.LENGTH_SHORT).show()
            // 2. Arka planda tam listeyi yenile (tutarlılık için)
            refreshSubsTrigger++
        } else {
            // 3. Silme başarısızsa rollback (öğeyi geri ekle)
            allSubtitles = allSubtitles + subItem
        }
    }
}
```

## 🎯 Sonuç

### Kullanıcı Deneyimi İyileştirmeleri
- ✅ Silme butonu animasyonsuz, hızlı ve basit
- ✅ Tek tıklamada direkt silme (2 aşamalı onay yok)
- ✅ Hızlıca ardışık silme yapıldığında UI anında güncellenir
- ✅ Dosya silindiği an ekrandan kaybolur (gecikme yok)
- ✅ Silme başarısız olursa öğe otomatik geri gelir (rollback)

### Performans İyileştirmeleri
- ✅ Animasyon hesaplama yükü kaldırıldı (animateDpAsState, animateColorAsState, AnimatedVisibility)
- ✅ Gereksiz state değişiklikleri azaldı (isExpanded, isHovered, isFocused)
- ✅ Pointer event dinleyicisi kaldırıldı
- ✅ LaunchedEffect iptal mekanizması ile tutarlılık korundu

### Kod Kalitesi
- ✅ 143 satır kod azaltıldı (KitsugiAnimatedDeleteButton)
- ✅ Daha basit ve anlaşılır state yönetimi
- ✅ Rollback mekanizması ile hata toleransı
- ✅ IO işlemleri Dispatchers.IO'da (ana thread bloke edilmez)

##  Test Senaryoları

1. **Tek Silme:** Bir altyazı sil → Anında listeden kaybolur ✓
2. **Hızlı Ardışık Silme:** 3-4 altyazıyı hızlıca sil → Hepsi anında kaybolur ✓
3. **Silme İptali:** Sil butonuna bas → Dosya silinir → UI güncellenir ✓
4. **Silme Başarısızlığı:** Dosya silinemezse → Öğe listede kalır ✓
5. **Video Silme:** Video sil → Altyazı listesi de güncellenir ✓
6. **Sayfa Yenileme:** Sekme değiştir → Liste yeniden yüklenir ✓

## 🔗 İlgili Dosyalar
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiAnimatedDeleteButton.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadsScreen.kt`

## 📅 Tarih
2026-10-21

## 🏷️ Etiketler
`altyazı`, `silme`, `animasyon`, `hızlı-silme`, `bug-fix`, `indirmeler`, `ui-iyileştirme`
