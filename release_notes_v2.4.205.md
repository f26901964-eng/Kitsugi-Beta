## 🚀 Kitsugi Beta v2.4.205

### 1. ⚡ MAL Detay Sekmeleri & Hız İyileştirmeleri
- **Slot Tabanlı Hız Sınırlayıcı:** Jikan çağrılarını tamamen kitleyen global `Mutex` kaldırıldı; istekler arasında 450 ms bırakan hafif slot rezervasyon sistemine geçildi.
- **Doğru Bölüm İndeksi:** Bölümler sekmesi indeksi 8 olarak düzeltildi (MAL, kütüphane VM ve TV detay ekranı). Sezon değişiminde ve açılışta bölümlerin spinner'da takılması engellendi.
- **Tekil Uçuş Koruması & Zaman Sınırları:** Sekme istekleri tekilleştirildi; her sekme için 25 sn (Bölümler için 90 sn) süre sınırı getirildi. Zaman aşımında sekme güvenli hata durumuna geçer ve tekrar seçildiğinde yeniden dener.
- **Açılış Hızlandırması & İptal Koruması:** Detay zenginleştirmeleri için 6 sn tavan süre belirlendi; eski arama/navigasyon işleri yeni sayfaya müdahale etmeyecek şekilde iptal ediliyor. `Retry-After` üst sınırı 30 sn'den 5 sn'ye indirildi.

### 2. 👥 Kitsu Karakterler & Seslendirmeler Düzeltildi
- **Hızlı Karakter Yükleme:** "your name." (11614) gibi Kitsu içeriklerinde karakterler, Jikan/AniList seslendirme birleştirmesini beklemeden anında ekrana gelir.
- **Sayfalama & Yedek Endpoint:** Kitsu `anime-characters` sayfalama döngüsü düzeltilerek kopya kayıtlar engellendi; veri dönmediğinde `anime/{id}/characters` yedek uç noktasına düşülmesi sağlandı.

### 3. 🔍 Shikimori Arama Filtresi & İyileştirme
- **Gürültü Filtresi:** Kullanıcı tür seçmediğinde metin aramalarına varsayılan olarak `tv,movie,ova,ona,special,tv_special` filtresi uygulandı; müzik, PV ve reklam içeriklerinin sonuçları işgal etmesi önlendi.
- **Genişletilmiş Çoklu Arama:** Çoklu platform arama sonuçlarında Shikimori satırı 10 yerine 24 öğe gösterecek şekilde genişletildi.

---

### 1. ⚡ MAL Detail Tabs & Performance Fixes
- **Slot-Based Rate Limiting:** Removed the heavy global `Mutex` blocking Jikan calls, replacing it with a 450ms slot allocation system for high responsiveness.
- **Fixed Episode Tab Index:** Corrected Episode tab indexing to 8 across mobile and TV layouts, resolving endless spinners during season switching and page opens.
- **Single-Flight & Fetch Timeouts:** Prevents redundant concurrent requests; bounded tab fetching to 25s (90s for multi-page episodes) with automatic retry capability.
- **Fast Page Launches:** Detail enrichments capped at 6s; old tab jobs canceled cleanly upon new result selection. `Retry-After` cap decreased to 5s.

### 2. 👥 Kitsu Characters & Voice Actors Fix
- **Instant Character Display:** Resolved blank character tabs (e.g. "your name." 11614) by displaying native Kitsu character data immediately while voice actors merge concurrently (max 8s timeout).
- **Pagination & Fallback Endpoints:** Fixed pagination duplicate bugs and added fallback to `anime/{id}/characters` when primary endpoint is empty.

### 3. 🔍 Shikimori Search Noise Reduction
- **Clean Results:** Applied default media filter (`tv,movie,ova,ona,special,tv_special`) on text queries to exclude music clips, PVs, and commercials.
- **Expanded Row Capacity:** Multi-source search row now displays up to 24 Shikimori entries (previously capped at 10).
