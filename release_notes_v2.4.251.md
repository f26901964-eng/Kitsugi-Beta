# Kitsugi-Beta v2.4.251 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🇹🇷 1. TMDB Türkçe Başlık Varyantı (TR-Only Boşluğu Kapandı)

- **Kök neden (v2.4.250'de belgelendi):** Site SADECE Türkçe başlık taşıyorsa (`Örümcek Adam Eve Dönüş Yok`), detay sayfasının EN/Romaji/Native başlıklarıyla eşleşme imkânsızdı → `EŞLEŞME YOK`.
- **Düzeltme:** `runGetStreams`, çözümlediği TMDB kimliğiyle `tr-TR` başlığı çekip (`TmdbTitleLocalizer`, yeni dosya) arama varyantlarına ve eşleştirici adaylarına ekliyor. Önbellekli: 164 eklenti tek TMDB isteğini paylaşır; hata/eksik durumda normal listeyle sessizce devam edilir (negatif sonuçlar oturumluk önbelleğe alınır, fırtına yok).
- **Kanıt (simülasyon V8):** aynı vaka TR varyantla `1.25 MATCH` (önce `EŞLEŞME YOK`).
- **Dosyalar:** `TmdbTitleLocalizer.kt` (yeni), `CsStreamRunner.kt` (`effectiveAlts`, 13 kullanım noktası).

## 🇬🇧 English

### 🇹🇷 1. TMDB Turkish Title Variant (TR-Only Gap Closed)

- **Root cause (documented in v2.4.250):** Sites carrying ONLY a Turkish title (`Örümcek Adam Eve Dönüş Yok`) could never match EN/Romaji/Native detail titles → `EŞLEŞME YOK`.
- **Fix:** `runGetStreams` fetches the `tr-TR` title for the resolved TMDB id (new `TmdbTitleLocalizer`, cached — 164 plugins share one TMDB request, negatives cached session-wide) and adds it to query variants and matcher candidates. Silent fallback to the normal list on error.
- **Proof (simulation V8):** same case now `1.25 MATCH` (was `EŞLEŞME YOK`).
