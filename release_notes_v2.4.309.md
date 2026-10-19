# Kitsugi Beta v2.4.309

## 🇹🇷 Türkçe

### 🛡️ Oynatıcı & Eklenti Detaylı Hata Yakalayıcı (Diagnostics & Copy)
- **Kapsamlı Hata Ayıklama & Log Aynalama (`KitsugiCdnJwExtractor`):** Close ve Rapid yerleşik çözücüleri (`closeload.*`, `rapidrame.*`, `rplayer.*`) artık hiçbir hatayı yutmuyor. URL, origin, HTTP durum kodu ve stacktrace detayları `CsTrace.error` ile kaydediliyor; boş HTML veya boş master listesi uyarı olarak düşüyor. Close hataları `Kekik_CloseLoad` + `HDCH`, Rapid hataları ise `Rapidrame` + `HDCH` tag'lerine aynalanarak logcat filtrelerinin dolu kalması sağlandı.
- **Akış Çözücü Zaman Aşımı & Hata Takibi (`CsStreamRunner`):** `loadExtractor` çağrılarındaki zaman aşımları ve istisnalar URL ve hata nedeni (`cause`) ile birlikte `CsTrace` ve etiket loglarına işleniyor.
- **Zengin Hata Mesajları & İpuçları (`Media3PlayerEngine`):** Oynatıcı tek satırlık "oynatıcı hatası" yerine HTTP 403, 404, 5004 kodlarını, iç içe hata nedenlerini, URL/host bilgisini ve çözüm ipuçlarını (403 → VPN dene, 5004 → Origin hatası, kodek → MPV motoruna geçiş) kullanıcıya ve tanı raporuna bildirir.
- **Kopyalanabilir Hata Ekranı (`PlayerErrorView`):** Oynatıcı hata ekranına seçilebilir metin, tek tıkla panoya **Kopyala** butonu ve "Detay Ayarlar > Tanı Raporu'na yazıldı" bilgilendirmesi eklendi. Kullanıcı hatayı anında kopyalayıp paylaşabilir.

---

## 🇬🇧 English

### 🛡️ Player & Addon Detailed Error Interceptor & Diagnostics
- **Comprehensive Error Logging & Logcat Tag Mirroring (`KitsugiCdnJwExtractor`):** Native Close/Rapid extractors no longer swallow parsing exceptions. Every failure records full stacktrace, URL, origin, and HTTP status code into `CsTrace.error`. Close errors are mirrored to `Kekik_CloseLoad` + `HDCH`, and Rapid errors to `Rapidrame` + `HDCH` tags.
- **Extractor Timeout & Stream Tracking (`CsStreamRunner`):** `loadExtractor` timeouts and uncaught exceptions now capture the target stream URL and cause chain directly into diagnostic logs.
- **Rich Diagnostic Error Messages & Hints (`Media3PlayerEngine`):** Instead of an opaque single-line "player error", the engine generates detailed diagnostic summaries including HTTP codes (403/404/5004), URL, target host, root causes, and actionable hints (403 → Try VPN, 5004 → Origin mismatch, Codec → MPV engine recommended).
- **Copyable In-Player Error Screen (`PlayerErrorView`):** The player error UI now features selectable text, a one-tap **Copy** button, and guidance directing users to Settings > Diagnostics Report.
