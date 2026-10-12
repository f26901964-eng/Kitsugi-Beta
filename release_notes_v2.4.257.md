# Kitsugi-Beta v2.4.257 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🌐 1. Güncellenmiş Domain Tablosu (57 → 88 Domain)
- `domain_fixes.json` tablosu eklenti deposundaki güncel tabloyla eşitlendi: 31 yeni domain eklendi, 7 domain güncellendi.
- **hdfilmcehennemi / hdfilmcehennemi2:** `.nl` yerine doğrudan `.biz` adreslerine güncellendi.
- **recTV:** URL sonundaki mükerrer `/api` eki temizlendi (`/api/api/...` hatası giderildi).
- Tablo doğrudan sunucudan dinamik çekildiği gibi APK içine de en güncel haliyle gömüldü.

### 🛡️ 2. HDFilmCehennemi2 Zorla Yönlendirme Düzeltmesi
- **Kök Neden:** `KNOWN_BROKEN_DOMAINS` ve `SAFE_NAME_SUFFIXES` içindeki `"2"` eki yüzünden HDFilmCehennemi2 (`hdfilmcehennemi2.site`), ana HDFC sitesine (`hdfilmcehennemi.biz` / `.nl`) zorla yönlendiriliyordu. Site yapısı farklı olduğu için sıfır sonuç dönüyor ve 75 saniyelik zaman aşımına (`KTS-CSP-9104`) yol açıyordu.
- **Çözüm:** `hdfilmcehennemi2.site` ölü domainler listesinden çıkarıldı ve numaralı eklentilerin ana eklenti domainini miras alması engellendi.

### 🔄 3. Eklenti Bazlı "Yeniden Dene" Butonu
- Yayın arama sonuçları ekranında hata veren CloudStream eklentileri için kartın üzerine **"Yeniden Dene"** butonu eklendi.
- Tüm aramayı baştan başlatmadan, yalnızca hata veren eklenti sıfırlanarak izole biçimde yeniden çalıştırılabilir.

### 🧪 4. Testler
- `CsStreamRunnerLogicTest` birim testleri güncellendi (`testKnownBrokenDomains_DoesNotContain_HdFilmCehennemi2Site`, `testResolveBuiltinDomain_NumberedVariant_DoesNotInheritParentDomain`).

---

## 🇬🇧 English

### 🌐 1. Expanded Domain Mapping (57 → 88 Domains)
- Synchronized `domain_fixes.json` with the latest plugin repository mapping: 31 new domains added, 7 domains updated.
- **HDFilmCehennemi / HDFilmCehennemi2:** Updated from `.nl` to `.biz` matching active provider sources.
- **recTV:** Removed duplicate trailing `/api` path suffix that caused `/api/api/...` malformed URLs.

### 🛡️ 2. HDFilmCehennemi2 Forced Domain Redirect Fix
- **Root Cause:** Due to `"2"` in `SAFE_NAME_SUFFIXES`, HDFilmCehennemi2 was mistakenly inheriting the parent HDFC domain, redirecting `.site` to `.nl`/`.biz`. The differing site structure caused 0 results and triggered provider timeout (`KTS-CSP-9104`).
- **Fix:** Removed `hdfilmcehennemi2.site` from `KNOWN_BROKEN_DOMAINS` and prevented numbered variant plugins from inheriting parent domains.

### 🔄 3. Per-Plugin "Retry" Button
- Added a dedicated **"Retry"** button on error cards for failed CloudStream providers in the stream screen.
- Allows retrying specific failed providers on demand without restarting the entire search for all plugins.

### 🧪 4. Tests
- Added unit tests in `CsStreamRunnerLogicTest` for domain resolution and numbered variant independence.
