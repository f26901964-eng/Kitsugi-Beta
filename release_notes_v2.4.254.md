# Kitsugi-Beta v2.4.254 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🔄 1. Eklenti Güncellemelerinin Görünmemesi Düzeltildi
- **Kök Neden (depo tarafı):** Uygulama yalnızca `Kitsugi-Plugins` deposunun `builds` dalını okur; bu dal 5 Ekim'den beri güncellenmiyordu (yayında `RoketDizi v15`, kaynakta `v19`). CI'daki yayın adımı yalnızca `main` dalında çalışıyordu ve prebuilt kopyalama/manifest üretimi erken `exit 0` ile atlanıyordu.
- **Kök Neden (uygulama tarafı):** Güncelleme kararı 6 ayrı yerde yalnızca `version > version` karşılaştırmasıyla veriliyordu; sürüm numarası artırılmadan yeniden derlenen eklentiler görünmüyordu. Ayrıca depoya ulaşılamadığında APK içindeki bayat liste sessizce "canlı" sanılıp "Tüm eklentiler güncel" deniyordu.
- **Çözüm:** `CsPluginUpdateChecker` + `CsInstalledFingerprint` ile sürüm **veya** dosya içerik parmak izi (SHA-256) farkı güncelleme sayılıyor. Manifest çekiminde raw → api.github.com → jsDelivr → Codeberg ayna zinciri (20 sn bütçe) kullanılıyor, yedek kaynaktan gelen veriler işaretleniyor ve "denetlenemedi" uyarısı gösteriliyor. Tireli depo adı (`Kitsugi-Plugins`) kontrolü düzeltildi (yedekler artık gerçekten deneniyor).
- **UI:** İçerik güncellemesinde rozet **"⟳ Yeniden derlendi (v15)"**, düğme **"⟳ Yeniden kur (v15)"** olarak gösterilir.
- **Testler:** `CsPluginUpdateCheckerTest`, `CloudstreamRepoMirrorTest`.

---

## 🇬🇧 English

### 🔄 1. Plugin Updates Not Appearing — Fixed
- **Root cause (repo side):** The app only reads the `builds` branch of `Kitsugi-Plugins`, which had not been updated since Oct 5 (published `RoketDizi v15` vs source `v19`): the CI publish step ran on `main` only, and prebuilt copying/manifest generation were skipped by an early `exit 0`.
- **Root cause (app side):** Update detection was copy-pasted `version > version` in six places, so rebuilds without a version bump were invisible; and when the repo was unreachable the bundled (frozen) plugin list was treated as live, falsely reporting "everything up to date".
- **Fix:** `CsPluginUpdateChecker` + `CsInstalledFingerprint` treat a VERSION or a CONTENT (SHA-256) mismatch as an update. Manifest fetching uses a robust mirror chain (raw → api.github.com → jsDelivr → Codeberg, 20 s budget); fallback data is flagged and the UI reports "could not check for updates" instead of "all up to date". The hyphenated repo name (`Kitsugi-Plugins`) check was fixed so the fallbacks actually run.
- **UI:** Content-only updates show "⟳ Rebuilt (v15)" / "⟳ Reinstall (v15)".
- **Tests:** `CsPluginUpdateCheckerTest`, `CloudstreamRepoMirrorTest`.
