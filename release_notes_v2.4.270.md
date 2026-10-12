# Kitsugi-Beta v2.4.270 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🔄 Çapraz Eşitleme Artık Altı Kaynakta: Kitsu · Shikimori · Bangumi Dahil
- **Tek Kimlik Köprüsü (`CrossProviderIdentityResolver`):** Bir kayıt hangi kaynaktan eklenirse eklensin (AniList, MAL, Simkl, Kitsu, Shikimori, Bangumi) altı kimlik birden çözülür: MAL, AniList, Kitsu, Shikimori, Bangumi `subject_id` ve TMDB/Simkl. Eski zincir yalnızca gerçek MAL ID’yi ve AniList offset’ini tanıyordu; Simkl (150M), Shikimori (400M) ve Bangumi (500M) satırlarında kimlik çözülemediği için Kitsu/Shikimori/Bangumi eşitlemesi sessizce atlanıyordu.
- **Bangumi’den Diğer Kaynaklara Yayılım:** Bangumi koleksiyonuna eklenen/düzenlenen kayıt, doğrulanmış MAL/AniList kimliği çözülebildiğinde artık AniList · MAL · Simkl · Kitsu · Shikimori listelerine de yazılır. Kimlik güvenle çözülemezse yalnızca Bangumi kalır ve gerekçe ekranda gösterilir — yanlış yapım başka listeye eklenmez.
- **„Atlandı” Artık Hata Değil:** Katalogda karşılık bulunamayan kaynak, işlemi başarısız saymıyor; bekleme kuyruğu sonsuz deneme döngüsüne girmiyor. Hata yalnızca ağ/oturum/sunucu reddinde sayılır.
- **Görünür Sonuç:** Snackbar ve rapor artık kaynak bazlı özet gösterir (ör. „AniList: eşitlendi · Kitsu: kimlik çözülemedi · Bangumi: hata”), „neden eşitlenmedi” sorusu raporu açmadan cevaplanır.
- **Kalıcı Eşleme Önbelleği:** Çözülen kimlikler `cross_provider_identity_v1` içinde saklanır; ilerleme her değiştiğinde yeniden katalog araması yapılmaz. MAL↔Bangumi ve MAL↔Kitsu köprüleri başarıdan sonra öğrenilir.
- **Yerel Satırın Kimliği Korunur:** Çözülen gerçek MAL ID yalnızca uzak çağrılarda kullanılır; Kitsu/Shikimori/Bangumi satırları kendi stableId’sini kaybetmediği için o kaynağın liste ekranında görünmeye devam eder.
- **Hız Sınırları ve Ekleme Kapısı:** Canlı eşitleme de `PlatformRateLimiter` kullanır (6 kaynak art arda yazıldığında 429’ları önler); „en az bir hesap bağlı” kontrolü artık Kitsu/Shikimori/Bangumi’yi de tanır, Keşfet’ten ekleme bu kaynaklardan da yapılabilir.
- **Doğrulama & Test:** 13 birim testi ile doğrulandı (`CrossProviderIdentityTest`); `R.string` anahtarları TR/EN için eklendi. Detaylar: `CROSS_SYNC_ALTI_KAYNAK_FIX_RAPORU_2026-10-12.md`.

---

## 🇬🇧 English

### 🔄 Cross-Sync Now Covers All Six Sources: Kitsu · Shikimori · Bangumi Included
- **Single Identity Bridge (`CrossProviderIdentityResolver`):** No matter which source an entry was added from (AniList, MAL, Simkl, Kitsu, Shikimori, Bangumi), all six IDs are resolved — MAL, AniList, Kitsu, Shikimori, Bangumi `subject_id` and TMDB/Simkl. The previous chain only understood a real MAL ID and the AniList offset, so Simkl (150M), Shikimori (400M) and Bangumi (500M) rows silently skipped Kitsu/Shikimori/Bangumi.
- **Fan-Out from Bangumi:** Entries added or edited in the Bangumi collection now also reach AniList · MAL · Simkl · Kitsu · Shikimori whenever a verified MAL/AniList identity can be resolved. If it cannot, only Bangumi is written and the reason is shown — a wrong title is never pushed into another list.
- **„Skipped” is No Longer an Error:** A provider without a catalogue match no longer fails the whole operation, so the pending queue stops retrying forever. Only network/session/server rejections count as errors.
- **Visible Outcome:** The snackbar and report now show a per-source summary (e.g. „AniList: synced · Kitsu: identity unresolved · Bangumi: failed”), answering „why was it not synced?” without opening logs.
- **Persistent Mapping Cache:** Resolved identities are stored in `cross_provider_identity_v1`; progress updates no longer trigger new catalogue searches. MAL↔Bangumi and MAL↔Kitsu bridges are learned after a successful write.
- **Local Row Identity Preserved:** The resolved real MAL ID is used only for remote calls, so Kitsu/Shikimori/Bangumi rows keep their own stableId and stay visible in that provider’s list screen.
- **Rate Limits and the Add Gate:** The live path now respects `PlatformRateLimiter` (prevents 429s when six writes fire back-to-back) and the „at least one account connected” check accepts Kitsu/Shikimori/Bangumi, so adding from Explore works with those sources alone.
- **Verification & Tests:** Verified by 13 unit tests (`CrossProviderIdentityTest`); `R.string` keys added for TR/EN. Details: `CROSS_SYNC_ALTI_KAYNAK_FIX_RAPORU_2026-10-12.md`.
