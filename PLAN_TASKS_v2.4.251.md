# Kitsugi CS Plan & Görevler — v2.4.251 (2026-10-11)

## ✅ TAMAMLANAN (bu oturum)

### Uygulama (Kitsugi-Beta)
- [x] v2.4.249: `CsTitleMatcher` 1.00 reddi düzeltildi (guard: bilinen başlığa ≥0.90 → reddedilemez)
- [x] v2.4.249: `proguard` Gson tam keep (Torrential `NoClassDefFoundError`)
- [x] v2.4.249: domain haritası 7 düzeltme (RecTV→psrectv80, SetFilmIzle→.ltd, SelcukFlix→.com, dizipal1214→3008, `dizipal.bid` tekrarı silindi, sinema.cv→sinemacc.com, dizi43/55→75)
- [x] v2.4.250: ID çözümleme fırtınası (`preResolvedIds` passthrough + `resolveIds` sonuç önbelleği)
- [x] v2.4.250: anime tip filtresi genişletildi (TvSeries/Movie kabul)
- [x] v2.4.250: tanı koşucusu faz 2g (gerçek-akış öz-tutarlılık testi + `Flow` sütunu)
- [x] v2.4.251: TMDB `tr-TR` başlık varyantı (`TmdbTitleLocalizer` + `effectiveAlts`)
- [x] Strateji simülasyonu V1–V8 (Python birebir port, hepsi geçti)
- [x] Tanı raporu + sürüm notları (249/250/251)

### Eklenti deposu (Kitsugi-Plugins) — analiz
- [x] 166 binary DEX okuma + soy haritası (byayzen 43, keyiflerolsun 42, kraptor 25, nikyokki 21…)
- [x] served==installed kanıtı; `prebuilt_plugins.json` yetim-metadata tespiti
- [x] DiziPal/SelcukFlix/RoketDizi/HDFilmCehennemi2/NeonSpor/XPrime kök nedenleri

## ⏳ KULLANICI / SONRAKİ ADIMLAR

### Kritik (kullanıcı)
- [ ] v2.4.251 APK **temiz kurulum** (kaldır → kur; stale-dex için şart)
- [ ] Ayarlar → Eklenti Tanı → testi çalıştır → `Flow: ❌` satırlarını ilet (2. tur sınıflandırma)
- [ ] Tam stack'li `.md` log raporunu paylaş (gri alanlar kapanacak)

### Eklenti deposu kaynak senkronu (Derleyici yeniden derler)
- [ ] DiziPal: Kekik-master arama/load portu (`/bg/searchcontent` + token) + `dizipal3008.com`
- [ ] SelcukFlix: master portu (`try/catch` + `/arama` yedeği)
- [ ] HDFilmCehennemi2: `mainUrl` `.biz` → `.site`
- [ ] RoketDizi: canlı yanıt yakala → ayrıştırıcı uyarla (repo==nik, ikisi de bozuk)
- [ ] XPrime: TMDB anahtar değişimi + manifest + yeniden derle
- [ ] NeonSpor: yeni M3U kaynağı veya listeden düşür (DMCA — ölü)
- [ ] FilmPalast: düşür (`com.example` iskeleti)
- [ ] Doğrula-veya-düşür: DiziGom, Filmİzlesene, FullHDFilmİzlede (§4.7 listesiyle birlikte)

### Uygulama backlog
- [ ] TMDB `tr-TR` varyantının canlı doğrulaması (TR-only site + gerçek tmdbId)
- [ ] Faz 2g sonuçlarına göre eşleştirici ince ayarı (2. tur)
- [ ] `prebuilt_plugins.json` yetim-metadata temizliği veya tüketiciye bağlama kararı
- [ ] SuperFilmGeldi güncel ayna tespiti (kullanıcıda doğrulanacak)

## 📦 PAKET İÇERİĞİ (bu zip)

Oturumda değişen/üretilen TÜM dosyalar (repo-yolu korunarak):
`app/build.gradle.kts`, `app/proguard-rules.pro`,
`data/cloudstream/{CsStreamRunner,CsTitleMatcher,CsPluginDiagnosticRunner,CsPluginTypeCompatibility,TmdbTitleLocalizer}.kt`,
`data/remote/KitsugiIdResolver.kt`, `ui/screens/stream/StreamViewModel.kt`,
`ui/screens/settings/CsPluginDiagnosticScreen.kt`,
`CS_EKLENTI_TANI_RAPORU_v2.4.249.md`, `release_notes_v2.4.{249,250,251}.md`,
`PLAN_TASKS_v2.4.251.md` (bu dosya).
