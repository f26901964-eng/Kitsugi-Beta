# Kitsugi Beta v2.4.333

## 🇹🇷 Türkçe

### 🎬 Küçük Video Oynatıcı Zaman Çizgisi Kontrolü & YouTube Önceliği
- **00:00 Siyah Ekran Yedek Motor Geçişi (`InlinePlayerSupport.kt`):**
  - Motorun sadece `READY` durumuna geçmesi yeterli kabul edilmez; video zaman çizgisi 18 saniye boyunca ilerlemezse (`positionMs == 0`) oynatıcı siyah ekranda takılı kalmaz ve otomatik olarak alternatif dahili motora geçer.
- **YouTube İçin Media3 İlk Tercihi:**
  - YouTube videoları tam ekran oynatıcı hattıyla uyumlu olarak önce Media3 ile açılır. Doğrudan OP/ED video dosyalarında ise kullanıcının MPV/Media3 tercihi korunur.

### 🖼️ Bangumi TMDB & Fanart Eşleşme İyileştirmesi
- **Genişletilmiş Infobox Alternatif Başlıkları (`KitsugiBangumiDetailClient.kt`):**
  - Bangumi infobox'ındaki tüm alternatif ve İngilizce adlar arama kümesine dahil edildi.
  - Latin harfli alternatifler önceliklendirildi ve aranabilir başlık sayısı 3'ten 8'e çıkarıldı.
  - Böylece Bangumi kayıtlarının TMDB ve Fanart.tv üzerindeki afiş, arka plan ve görsel galerilerine bağlanma başarı oranı artırıldı.

---

## 🇬🇧 English

### 🎬 Inline Video Player Timeline Progress & YouTube Default Engine
- **00:00 Black Screen Progress Fallback (`InlinePlayerSupport.kt`):**
  - `READY` engine state alone is no longer considered active playback. If timeline progress remains stagnant at `00:00` for 18s, the player automatically triggers engine failover instead of hanging indefinitely on a black screen.
- **Media3 Priority for YouTube:**
  - YouTube video playback aligns with the fullscreen player pipeline by selecting Media3 by default, while direct OP/ED media continues to respect user engine preferences.

### 🖼️ Bangumi TMDB & Fanart Match Accuracy
- **Infobox Alternative Titles Extraction (`KitsugiBangumiDetailClient.kt`):**
  - All infobox alias titles and localized keys are extracted into candidate search sets.
  - Prioritizes Latin title candidates and raises search query limit from 3 to 8, significantly improving TMDB and Fanart.tv image/metadata cross-matching.
