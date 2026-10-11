# PLAN & TASK — Video Eklentilerinde İsim Araması Sıfır Dönüyor (v2.4.240)

**Tarih:** 2026-10-11 · **Dal:** `arena/f7b6058d-kitsugi-beta` · **Temel:** `8478940` (v2.4.239)
**Kök neden raporu:** `KOK_NEDEN_RAPORU_EKLENTI_ISIM_ARAMASI_SIFIR_2026-10-11.md`

## 0. Şikâyet

> "İsim araması ile hiçbir video eklentisi çalışmıyor; sonuç döndürmeden kapanıyorlar.
> Sadece ID destekli kaynaklar (Torrentio, Anizium) çalışıyor. Eklenti veri bulma
> sayfasında ve detay sayfasındaki **İzle / Bölümler** akışında veri gelmiyor."

**Teşhis:** Aramanın iki yolu var. `tryNativeIdResolution()` (ID → `getLoadUrl`) ✅ çalışıyor;
`safeSearch()` (başlık → `api.search` → `safeLoad` → `loadLinks`) ❌ topluca sıfıra düşüyor.
Yani sorun sitelerde değil, **isim aramasının ortak darboğazında**.

---

## 1. Görev listesi

| # | Görev | Kök neden | Dosya | Durum |
|---|---|---|---|---|
| T1 | `safeSearch`/`safeLoad` aşama sürelerini CF-farkındalımlı yap (`stageTimeoutMs`), timeout'u iz bırakır hale getir | KN-A | `CsStreamRunner.kt` | ✅ |
| T2 | CF clearance gate'ini `safeSearch` içine taşı (kutunun **dışında**, eklenti başına 1 kez, 40 sn bütçe) | KN-C | `CsStreamRunner.kt` | ✅ |
| T3 | `DdosGuardKiller`: kanıt bazlı etkinleşme + `skipHosts` negatif önbelleği + host başına kilit + 8 sn probe tavanı + paylaşılan çerez haritası | KN-B | `DdosGuardKiller.kt` | ✅ |
| T4 | Tek normalize edilmiş etkin-eklenti eşleştirmesi: `isApiEnabledFor()` / `matchesEnabledPlugin()`; 4 çağrı noktasını bağla | KN-D, KN-X | `CsStreamRunner.kt`, `AddonSearchDialog.kt`, `PluginPickerBottomSheet.kt`, `PluginPickerScreen.kt` | ✅ |
| T5 | Eklenti arama yüzeylerinde oturum bloklarını sıfırla (`CsPluginStatusTracker.clear()` + `clearUnsupportedMethodsCache()`) | KN-D2 | 3 UI dosyası + `searchAllAddons` | ✅ |
| T6 | Arama bütçesinden `searchSemaphore` kuyruk bekleme süresini düşür; kapı 24 → 32 | KN-K | `CsStreamRunner.kt` | ✅ |
| T7 | Çıplak `safeSearch` yerine `searchWithCfRetry` (boş + challenge kanıtı → gate + sabırlı yeniden deneme) | KN-A/C | 2 UI dosyası | ✅ |
| T8 | KN-D için JVM unit testi (5 test, Android bağımlılığı yok) | — | `CsStreamRunnerLogicTest.kt` | ✅ |
| T9 | `appVersionName` 2.4.239 → 2.4.240 | — | `app/build.gradle.kts` | ✅ |
| T10 | `tree-sitter-kotlin` ile sözdizimi denetimi (926 dosya) + AAR/DEX ABI kontrolü | — | — | ✅ |
| T11 | **Kullanıcıda:** `build-apk.ps1` ile derleme + cihaz testi (bu ortamda JDK/Android SDK yok → derlenemedi) | — | — | ⬜ |
| T12 | **Kullanıcıda:** logcat doğrulaması (bkz. §3) ve hâlâ sıfır dönen tekil eklentiler için `domain_fixes.json`'a tek girdi (veri değişikliği, kod değişikliği değil) | — | `domain_fixes.json` | ⬜ |

## 2. Davranış değişikliği özeti

**Önce:** isim araması → CF/403 → `DdosGuardKiller` global kilit + sonsuz probe + 15 sn kutu →
çözüm hiç tamamlanamıyor → çerez hiç birikmiyor → her varyant aynı duvar → `?: emptyList()`
(zamanaşımı ile "içerik yok" aynı görünüyor) → kullanıcı: "eklenti sonuç vermeden kapandı".

**Sonra:** sağlıklı eklenti **eskisiyle aynı hızda** (gate 0 ms, ek istek yok; DdosGuard yalnızca
kanıtta devrede); CF'li eklenti arama **öncesi** clearance çözer, 120 sn'lik sabır penceresiyle
`search`/`load` zinciri tamamlanabilir; kapı boş kalırsa **sebbi görünür** (`search-timeout`,
`load-timeout`, `cf-gate` aşamaları) ve bir sonraki turda blokaj taşınmaz.

## 3. Cihazda doğrulama (kabul kriterleri)

```
adb logcat -s CsStreamRunner CS_SEARCH_ERR DdosGuardKiller CsCfWarmup PLUGIN_DIAG
```

1. Aynı içerikle (Türkçe dizi/anime + film) **İzle**'ye bas:
   - `⏱️ SEARCH TIMEOUT` satırları belirgin biçimde **azalmalı**.
   - CF'li kaynaklarda önce `🔐 safeSearch öncesi CF gate:`, sonra `search('…') → N sonuç` (**N>0**).
   - `[host] 403 yanıtı DDoS-Guard imzası taşımıyor — bypass atlandı.` satırı host başına **1 kez** görünmeli.
2. Eklenti arama sayfasında çıplak isimle dene: **0 API** durumu görülmemeli; engellenen eklenti
   varsa kartta **gerçek sebep** yazmalı.
3. TrAnimeci: WebView DOM taraması artık 45 sn'lik latch'ini tamamlayabiliyor olmalı (`DOM array length>0`).
4. Kalan tekil sıfırlar için: Ayarlar → Tanılama → Eklenti Canlı Testi; `search-timeout` /
   `load-timeout` / `cf-gate` satırları raporlandığından liste/kod değil **veri** sorunuysa
   `domain_fixes.json`'a tek girdi yeterli.

## 4. Kapsam dışı / bilinçli olarak dokunulmayan

- `applyDomainFix` + `revertForcedDomain` (v2.4.226'da CS davranışına hizalandı, değişmedi).
- `CsTitleMatcher.MIN_MATCH_SCORE = 0.45` ve `findBestMatch` token kapısı: gevşetmek yanlış
  sezon/eşleşmeyi geri getirir; önce timeout'lar ölçülsün, gerekirse tek ayrı görev olarak ele alınır.
- NiceHttp 0.4.4 ↔ eklentilerin 0.4.13'ü: `Requests.kt` diff'i yalnızca `callTimeout` ↔
  `connect/write/read` farkı gösteriyor → **ABI uyumsuzluğu yok**, dokunulmadı.
- `CsRuntimeInit` içindeki `Kraptor123/domainListesi` sahte yanıtı: format, eklenti deposundaki
  `eklenti_domainleri.txt` ile birebir aynı (`Ad:https://…|Ad:https://…`) → doğrulandı, değişmedi.
