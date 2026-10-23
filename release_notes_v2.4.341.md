# Kitsugi Beta v2.4.341

## 🇹🇷 Türkçe

### 🎬 Oynatma Asla Engellenmiyor — Engellemeyen Arka Plan Göstergesi
- **"Sonraki Bölüm Kaynakları Aranıyor..." artık videoyu karartmıyor:** Tam ekran %85 siyah yüklenme katmanı kaldırıldı; yerine üstte küçük, dokunmatiği yutmayan bir durum çipi gösteriliyor. Video, arama sürerken kesintisiz oynamaya devam ediyor.
- **Sahte buffering dairesi giderildi:** Ortadaki yüklenme dairesi yalnızca GERÇEK arabelleğe alma (buffering) durumunda beliriyor; arka plan görevleri için artık belirmiyor.

### ⚡ Sonraki Bölüm Önbelleği (Prefetch) — Anında Bölüm Geçişi
- **İzlerken hazır tutma:** Bölüm listesi yüklendikten sonra sıradaki bölümlerin kaynak verisi, siz video izlerken ARKA PLANDA ve SIRAYLA çekilip önbellekleniyor.
- **Yalnızca izlediğiniz kaynak üzerinden:** Prefetch sadece o an izlediğiniz eklenti/addon'u sorgular — hızlı, eksiksiz ve diğer kaynaklara gereksiz yük bindirmez.
- **Arama fazı tümden atlanır:** Önbellek doluysa bölüm geçişinde "Sonraki Bölüm Kaynakları Aranıyor..." hiç görünmez; geçiş anında gerçekleşir.
- **Ön-çözümleme:** Bölümün son ~2 dakikasında sıradaki bölümün oynatma URL'si arka planda önceden çözümlenir; geçişte bekleme neredeyse sıfırlanır.
- **Güvenli önbellek:** Yalnızca meta veri tutulur (video indirilmez), kaynak listesi 30 dk / URL 10 dk TTL ile bayatlamaya karşı korunur; kaynak veya seri değişiminde önbellek otomatik geçersiz kılınır.

---

## 🇬 English

###  Playback Is Never Blocked — Non-Blocking Background Indicator
- **"Searching next episode sources" no longer dims the video:** The full-screen 85% black loading layer was replaced by a small, touch-transparent status chip at the top. Playback continues uninterrupted while the search runs.
- **Fake buffering spinner removed:** The center loading circle now appears only during REAL buffering, not for background tasks.

### ⚡ Next-Episode Prefetch Cache — Instant Episode Switching
- **Warm while you watch:** Once the episode list loads, upcoming episodes' source data is fetched in the background, sequentially, while you watch.
- **Only via the source you're watching:** Prefetch queries only your current addon/extension — fast, complete, and without burdening other sources.
- **Search phase skipped entirely:** When the cache is warm, episode switches happen instantly with no "Searching..." phase at all.
- **Pre-resolution:** In the last ~2 minutes of an episode, the next episode's playback URL is resolved in the background, making the switch near-instant.
- **Safe caching:** Metadata only (no video bytes), 30-min TTL for source lists / 10-min for URLs; cache auto-invalidates on source or series change.
