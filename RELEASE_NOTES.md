# Kitsugi-Beta — Sürüm Notları / Release Notes

---

## 🇹🇷 Türkçe (v2.4.149)

### ⚙️ Aniyomi Tarzı Tam Sayfa Ayarlar Mimarisi & Oynatıcı İyileştirmeleri

- **Aniyomi Navigasyon Felsefesi:** Ayarlar sayfası, modal açılır pencereler (dialog/bottom sheet) yerine Aniyomi tarzı hiyerarşik tam sayfa gezinme (push/pop sub-page) mimarisine kavuşturuldu.
- **Akıcı Sayfa Geçişleri (AnimatedContent):** Kategori kartlarına tıklandığında sağdan kayarak açılan tam ekran alt sayfalar ve geri tuşuna basıldığında sola kayarak dönen pürüzsüz geçiş animasyonları entegre edildi.
- **Gömülü Alt Sayfalar (Embedded Mode):** Hesap Bağlantıları, Görünüm & Tercihler, Oynatıcı Ayarları, Eklentiler & Akış Kaynakları, Harici Entegrasyonlar, Veri & Yedekleme, İndirmeler, Hakkında ve Geri Bildirim sayfaları çift başlık ve modal kısıtlamalarından arındırılarak gömülü tam sayfa olarak yeniden uyarlandı.
- **Eksiksiz Ayar Korunumu:** Mevcut hiçbir ayar seçeneği, yapılandırma anahtarı veya parametre silinmedi; tüm işlevler %100 korundu.
- **Hiyerarşik Geri Tuşu (BackHandler):** Hem üst başlıktaki geri (`<-`) butonu hem de Android sistem geri tuşu, alt kategorilerden (örn. *Oynatıcı > Dahili Oynatıcı > Kod Çözücü*) ana ayarlara kusursuz bir sırayla geri dönecek şekilde bağlandı.
- **Oynatıcı 2x Hız Basılı Tutma Düzeltmesi:** Oynatıcıda basılı tutarak geçici 2x hızlandırma jesti kullanıldığında, parmak bırakıldığında hızın sabit 1.0x'e sıfırlanması sorunu giderildi; oynatıcı artık jest öncesinde seçili olan orijinal oynatma hızına dönüyor.
- **Oynatıcı Çift Yükleme Göstergesi (Double Spinner) Giderildi:** Oynatıcı tamponlamada iken hem dahili yükleme göstergesinin hem de ek bileşenin aynı anda dönmesi sorunu çözüldü.

---

## 🇬🇧 English (v2.4.149)

### ⚙️ Aniyomi-Style Full-Page Settings Architecture & Player Improvements

- **Aniyomi Navigation Architecture:** Refactored settings from modal dialogs and bottom sheets to Aniyomi's hierarchical full-screen push/pop sub-page navigation model.
- **Smooth Screen Transitions (AnimatedContent):** Engaging horizontal slide and fade animations when navigating forward into setting categories and backward to the root page.
- **Full Embedded Sub-Screens:** Account Connections, Appearance & Preferences, Player Settings, Addons & Streaming Sources, External Integrations, Data & Backup, Downloads, About, and Feedback now render as native full pages without redundant dialog wrappers or duplicate headers.
- **100% Settings Preservation:** All existing preferences, parameters, database configurations, and UI options have been meticulously preserved without loss.
- **Hierarchical Back Navigation (BackHandler):** Both the app bar back button and the device's hardware/gesture back trigger natural push-pop screen unwinding through multi-level submenus.
- **Player 2x Hold Speed Restoration Fix:** Releasing the hold-to-boost gesture now properly restores the user's previously configured playback speed (e.g. 1.25x or 1.5x) instead of incorrectly resetting to 1.0x.
- **Player Double Spinner Fix:** Eliminated redundant dual buffering indicators during playback rebuffering.
