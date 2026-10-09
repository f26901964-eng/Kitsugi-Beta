# PLAN & TASK — Gezinti Kaynaklı Bellek Şişmesi Çökme Düzeltmesi

Tarih: 2026-10-09 · Dal: `arena/1ab31e5f-kitsugi-beta` · Commit: `49dcb6c`

## Belirti
Uygulama içinde bir süre gezildikten sonra (detay sayfaları, karakterler, galeriler…) uygulama
kapanıyor. Sistem süreci rapor bırakmadan öldürüyor (OOM / LMKD).

## Kök neden
1. **Sınırsız bellek önbellekleri:** 18 dosyada, toplam 30'dan fazla `object` seviyesinde
   `ConcurrentHashMap` / `synchronizedMap` önbelleği vardı. Hiçbirinin boyut sınırı yoktu ve hiçbiri
   silinmiyordu. En büyüğü `DetailCache`: 20 harita, açılan her içeriğin detay, karakter, personel,
   yorum, bölüm, galeri ve çeviri verisini süreç ölene kadar tutuyordu.
2. **Bellek uyarıları dinlenmiyordu:** `onTrimMemory` / `onLowMemory` hiçbir yerde ele alınmıyordu.
   Android'in "bellek azaldı" uyarısına cevap vermeyen süreç LMKD tarafından sessizce öldürülür.
3. **Ek hata:** `DetailTrailerFallback`, `ConcurrentHashMap<String, String?>` içine `null` yazıyordu.
   Bu yazma her seferinde NPE fırlatıyordu. Çağıran taraf `try/catch` ile yakaladığı için uygulama
   çökmüyordu ama "fragman yok" sonucu hiç önbelleğe alınamıyor, arama her seferinde baştan yapılıyordu.

## Plan
- Boyutu sınırlı, thread-safe, `MutableMap` uyumlu bir LRU önbellek yaz. Mevcut kodda yalnızca
  tanım satırı değişsin.
- Bellek baskısı yöneticisi yaz: tüm önbellekleri kaydetsin, `onTrimMemory` seviyesine göre küçültsün
  veya boşaltsın, ayrıca heap'i periyodik ölçüp kendiliğinden temizlik yapsın.
- Coil görsel bellek önbelleğini de bu yöneticiye bağla.
- Çökme raporuna önbellek doluluk özetini ekle.

## Görevler
- [x] `core/memory/BoundedCache.kt` (yeni): erişim sıralı LRU, `null` değer desteği, `keys` /
      `values` / `entries` anlık kopya döner (ConcurrentModificationException yok),
      bellek baskısında `trimTo(fraction)`.
- [x] `core/memory/KitsugiMemoryGuard.kt` (yeni):
      - `onTrimMemory` / `onLowMemory`: seviyeye göre önbellekleri boşaltır veya küçültür (%50 / %25).
      - `watchdogTick()`: heap %80'i geçince önbelleklerin yarısını, %90'ı geçince hepsini boşaltır
        (en az 5 sn arayla).
      - `describe()`: teşhis için önbellek doluluk özeti.
- [x] `KitsugiApplication.kt`: 15 sn'de bir çalışan heap bekçisi, `onTrimMemory`, `onLowMemory`;
      Coil memory cache'i `registerClearer("coil.memory")` ile bekçiye bağlandı.
- [x] `KitsugiCrashLogger.kt`: "BELLEK DURUMU" bölümüne önbellek özeti eklendi.
- [x] Sınırlı önbelleğe geçirilen dosyalar:
  - `data/remote/DetailCache.kt` (20 harita)
  - `data/auth/CrossSyncIdentityGuard.kt`
  - `data/cloudstream/CsStreamRunner.kt` (mediaProbeCache)
  - `data/cloudstream/embed/WebViewMediaSniffer.kt`
  - `data/remote/AniSkipClient.kt`, `AnimeSkipClient.kt`
  - `data/remote/KitsuEntryIdResolver.kt`, `KitsugiIdResolver.kt`
  - `data/remote/KitsugiAniListPersonBridge.kt`
  - `data/remote/KitsugiBangumiDetailClient.kt` (4 harita)
  - `data/remote/MdbListClient.kt`, `TmdbDiscoverClient.kt`
  - `data/remote/ShikimoriPosterResolver.kt`, `ShikimoriTitleResolver.kt`
  - `data/repository/StreamProbe.kt`
  - `data/trailer/DetailTrailerFallback.kt` (null-NPE hatası da çözüldü), `TrailerService.kt`
  - `ui/screens/search/components/AddonExploreDialog.kt`

## Kapsam dışı bırakılanlar (bilinçli)
- In-flight `Deferred` haritaları, eklenti durum ve engel listeleri (küçük, işlevsel durum tutar).
- ViewModel önbellekleri (ViewModel ile birlikte yok olurlar).
- Native çökmeler (oynatıcı, RenderThread). Ayrı konu.

## Test (kullanıcı)
- [ ] Projeyi derle (sandbox'ta Android araçları yoktu, derlenmedi).
- [ ] Uzun gezinti senaryosunda çökme tekrar ediyor mu kontrol et.
- [ ] Logcat'te `KitsugiMemoryGuard` etiketiyle "Bellek temizliği" satırları çıkıyor mu bak.
- [ ] Önbellekten silinen içerik tekrar açılınca internetten yeniden yükleniyor mu kontrol et
      (küçük bir yükleme görünmesi beklenen davranış).
- [ ] Yeni çökme olursa raporu Google Drive'a yükle. Raporda "Önbellekler:" satırı da yer alacak.
