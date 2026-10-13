# PLAN / TASK: TMDB Keşfet başlıklarında dil önceliği (Türkçe → İngilizce → Romaji) tamiri

Tarih: 2026-10-13
Branch: `arena/8d121b15-kitsugi-beta`

## Sorun (kullanıcı şikayeti)
- "TMDB keşfet sayfası hâlâ düzgünce dile uymuyor. Türkçe seçtim ama isimler Türkçe gösterilmiyor.
  Türkçe yoksa bile en kötü ihtimalle İngilizce, o da yoksa son çare Romaji göstermesi lazım.
  Bu 3'ünden biri net olur — ama sıralama önemli."
- v2.4.201'de kurulan merkezî zincir (`MediaTitleResolver` + `TmdbTitleFallback`) mantık olarak
  doğruydu; sorun zincirin **çevresindeki tesisatta** (önbellekler + yeniden yükleme tetikleyicisi
  + zayıf yedek istek) idi.

## Kök nedenler (4 bağımsız delik)

### 1. Dil değişimi Keşfet'i yenilemiyordu (ana neden)
`ExploreViewModel.init` içindeki `tmdbChanged` kontrolü yalnızca
`tmdbEnabled` / `tmdbEnrichContinueWatching` / `tmdbUserApiKey` değişimlerini izliyordu.
**`tmdbLanguage` ("Dizi & Film Başlık Dili") değişimi izlenmiyordu.**

Sonuç: Kullanıcı ayarı Türkçe yaptığında bellek içi `platformCache` + `allSourceStates`
eski dilde çözümlenmiş başlıklarla yaşamaya devam ediyor; şeritler ancak elle
pull-to-refresh ya da uygulama yeniden başlatılınca yenileniyordu.

### 2. Takvim önbelleği dil körü
`KitsugiAiringCalendarClient.upcomingCache` anahtarı `"tmdb|anon"` idi — **dil yok**.
Dil değiştikten sonra TTL (90 sn) süresince "Yakında Yayında" şeridi eski dilde
servis ediliyordu.

### 3. en-US yedek başlık isteği zayıftı → CJK sızması
`fetchEnglishTitlesMap` beklemesiz (backoff'suz) 2 deneme yapıyordu. Keşfet açılışı
~12 şeridi paralel çekiyor; Türkçesi olmayan sayfalarda her biri ek bir `en-US` isteği
tetikliyor. Paylaşımlı API anahtarıyla bu seri 429/timeout'a takıldığında İngilizce
harita **boş** dönüyordu ve Türkçe çevirisi olmayan animelerin **Japonca (kanji)
başlıkları ekrana düşüyordu**. Hatalı payload ayrıca diske yazılıp ağ sorunlu
oturumlarda tekrar servis ediliyordu.

### 4. Kırılgan parse + bayat disk önbelleği
- `parseTmdbDiscoverList` / `parseTmdbDiscoverListAll` içindeki
  `results.getJSONObject(i)` tek bozuk öğede **tüm şeridi** bitiriyordu (dış catch).
- Disk önbelleği `MediaTitleResolver.VERSION = 2` anahtarıyla yazılmış eski/yanlış
  başlıkları barındırıyordu.

## Yapılan değişiklikler

1. **`ExploreViewModel.kt`**
   - `tmdbLanguageState` alanı eklendi; `tmdbChanged` koşuluna
     `tmdbLanguageChanged` dahil edildi.
   - Dil değişiminde mevcut `else` dalı zaten `platformCache.clear()` +
     `allSourceStates = emptyMap()` + `loadData(forceRefresh = true)` yapıyor →
     artık TMDB şeritleri seçilen yeni dile göre otomatik yeniden çekiliyor.

2. **`KitsugiAiringCalendarClient.kt`**
   - `fetchUpcomingSchedule` önbellek anahtarına TMDB aktif dili eklendi
     (`"tmdb|anon|tr-tr"` gibi). `preferredSource = "tmdb" | "all"` için geçerli;
     AniList anahtarı değişmedi (veri dile bağlı değil).

3. **`TmdbDiscoverClient.kt`**
   - `fetchEnglishTitlesMap`: `longArrayOf(0, 300, 700)` geri çekilme (backoff)
     delikleriyle **3 deneme**. 429/timeout anında art arda istek atmak yerine
     kısa bekleme yapılıyor → İngilizce haritanın alınma olasılığı ciddi biçimde artıyor,
     kanji sızması pratikte kapanıyor.
   - `parseTmdbDiscoverList` / `parseTmdbDiscoverListAll`:
     `getJSONObject(i)` → `optJSONObject(i) ?: continue` (tek bozuk öğe şeridi
     bitirmiyor; `parseTmdbDiscoverListAll`'daki mevcut desenle parite).
   - `kotlinx.coroutines.delay` import'u eklendi.

4. **`MediaTitleResolver.kt`**
   - `VERSION = 2 → 3`. Bellek içi + disk önbellek anahtarları sürümü içerdiği için
     v2 anahtarıyla yazılmış eski (yanlış dil / kanji) başlıklı payload'lar bir daha
     servis edilmez. Detay önbellekleri (`KitsugiDetailClient`) de aynı sürümü
     kullandığı için onlar da tazelenir.

## Doğrulama
- Zincir mantığı birebir Python simülasyonuyla 9 senaryoda doğrulandı
  (`scratch_title_chain.py` — çalışma ortamı dosyası, repoya alınmadı):
  - Film, TR başlık var → **"Başlangıç"** ✓
  - Anime, TR yok, kanji orijinal, en haritası var → **"Frieren: Beyond Journey's End"** ✓
  - Anime, TR yok, romaji orijinal, en haritası var → İngilizce (EN > Romaji sırası) ✓
  - Türk yapımı (orijinal dil == istek dili) → Türkçe ✓
  - Kore dizisi, TR yok, hangul → İngilizce ✓
  - TR yok + kanji + en haritası alınamadı → son çare kanji (beklenen, artık çok daha nadir) ✓
  - TR çeviri yok, TMDB orijinale düştü (aynı string) → İngilizce ✓
  - UI katmanı `getDisplayTitle(ROMAJI)` TMDB dalı → çözümlenmiş display (Türkçe) ✓
- Sandbox'ta Gradle bağımlılıkları indirilemediği için derleme yapılamadı;
  Android Studio'da derleme + cihaz testi gerekli.

## Kabul kriterleri
- [ ] "Dizi & Film Başlık Dili" Türkçe seçilince Keşfet TMDB şeritleri **elle
      yenileme gerektirmeden** yeni dile göre yeniden yüklenir.
- [ ] TMDB'de Türkçe başlığı olan her içerik Türkçe gösterilir.
- [ ] Türkçesi olmayan içerik İngilizce gösterilir (romaji değil — sıralama:
      Türkçe → İngilizce → Romaji).
- [ ] İngilizcesi de yoksa Latin/romaji orijinal gösterilir; hiçbiri yoksa
      (son çare) orijinal başlık — ekran asla boş kalmaz.
- [ ] Zayıf ağ / 429 altında bile kanji başlık pratikte görünmez (backoff'lu
      3 deneme).
- [ ] Dil değiştirildikten sonra "Yakında Yayında" şeridi 90 sn boyunca eski
      dilde kalmaz.

## Bilinen sınırlamalar
- TMDB'de hiç Türkçe çeviri yoksa ve en-US isteği 3 denemede de alınamazsa
  orijinal (CJK) başlık son çare olarak görünmeye devam eder — ekranı boş
  bırakmaktan iyidir; backoff bunu pratikte nadirleştirir.
- "Anime & Manga Başlık Dili" (ROMAJI/ENGLISH/NATIVE) TMDB öğelerinde kasıtlı
  olarak geçerli kalmaya devam eder: NATIVE isteyen kullanıcı orijinal CJK
  başlığı görür; bu ayrı bir tercihtir, "Dizi & Film Başlık Dili"nden bağımsızdır.
- Paylaşımlı (built-in) TMDB anahtarı kalabalık kullanımda 429'a açık; kişisel
  API anahtarı girilmesi önerilir.
