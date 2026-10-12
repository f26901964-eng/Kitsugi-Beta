# PLAN / TASK — HDFC2 Zorla Domain Yönlendirmesi + Eklenti Bazlı "Yeniden Dene"

**Tarih:** 2026-10-12
**Dal:** `arena/084730b8-kitsugi-beta`
**Commit:** `d93c473`
**Taban:** v2.4.255 (`2fcf39c`)
**Şikâyet:** Eklenti "hiç denemeden pat diye" `KTS-CSP-9104` (Zaman aşımı) gösteriyor. Eklenti tarafı HDFilmCehennemi2'yi `.site` domainine taşıdı, RoketDizi'yi `.live` → `.life` taşıdı; değişen bir şey olmadı.

---

## 0. Eklenti tarafı analizinin doğrulanması

| İddia | Kod kanıtı | Sonuç |
|---|---|---|
| (a) Boş sonuçta eski tracker kaydı gösteriliyor, yeniden denenmiyor | `CsStreamRunner.kt` "Zaman aşımı (75 sn)" yalnızca gerçek `withTimeoutOrNull(75s)` dolunca yazılır; `StreamViewModel.startFetch` her yeni bölümde `CsPluginStatusTracker.clear()` çağırır. Ama aynı ekranda CF dışı hatalar için **yeniden deneme yolu yoktu**. | Kısmen doğru |
| (b) "timeout" içeren her hata 9104'e eşleniyor | `CloudStreamErrorClassifier.classifyEvidence` | Doğru (yalnızca etiket) |
| (c) v2.4.240+ ölü domain yalnızca loglanıyor | `getStreams`/`safeSearch`/`safeLoad` yalnızca log yazar **AMA** `applyDomainFix` (her `ensurePluginReady`'de) listedeki domaini "ölü domain kurtarma" ile **zorla başka adrese çevirir**. | **Eksik / yanlış** |

---

## 1. Kök neden (HDFC2)

1. Eklenti `mainUrl = https://hdfilmcehennemi2.site`
2. `KNOWN_BROKEN_DOMAINS` içinde `hdfilmcehennemi2.site` → `isKnownBrokenDomainFor = true`
3. `applyDomainFix` → `rescueUrl = dynamicDomains["hdfilmcehennemi2"] (yok) ?: resolveBuiltinDomain("hdfilmcehennemi2")`
4. `SAFE_NAME_SUFFIXES` içinde `"2"` → `hdfilmcehennemi` + `2` eşleşir → **`https://www.hdfilmcehennemi.nl`** (ana HDFC sitesi)
5. Farklı site yapısı → 0 sonuç → `revertForcedDomain` bir kez `.site`'a döner → toplam süre 75 sn'yi aşar → `KTS-CSP-9104`

RoketDizi `.life`: `"roketdizi.life".contains("roketdizi.live") == false` → etkilenmez ✅

`domain_fixes.json` `blocked` listesinde `hdfilmcehennemi2` ve `roketdizi` var; v2.4.240'tan beri yalnızca gözlem (`CsTrace.info`), atlama yok → zararsız. Liste, günlük `update_domains.yml` / `scripts/check_domains.py` tarafından yeniden üretiliyor.

---

## 2. Görevler

- [x] T1 — `KNOWN_BROKEN_DOMAINS`'ten `hdfilmcehennemi2.site` çıkarıldı (yerine açıklayıcı not)
- [x] T2 — `SAFE_NAME_SUFFIXES`'ten `"2"` çıkarıldı: numaralı eklentiler ana eklentinin yerleşik domainini miras almaz
- [x] T3 — `KNOWN_BROKEN_DOMAINS` KDoc güncellendi: liste hâlâ `applyDomainFix` üzerinden etkili; eklenti deposu bir domaine taşırsa listeden çıkarılmalı
- [x] T4 — `StreamViewModel.retryPlugin(addonDisplayName)`: son `startFetch` parametreleri (`LastFetchParams`) ile yalnızca o eklentiyi yeniden çalıştırır; önce tracker kaydını (blok/mesaj/kod) siler
- [x] T5 — `onWebViewDismissed` ortak `rerunCsPlugin` yardımcısına taşındı (davranış aynı; eklenti bulunamazsa kart artık sonsuz yükleniyor kalmaz)
- [x] T6 — Telefon hata kartı (`StreamErrorCard`): CS eklentilerinde (`⚡ ` önekli) **Yeniden Dene** butonu (`R.string.action_retry`, TR/EN mevcut); `Doğrula` ile yan yana
- [x] T7 — `onRetryPlugin` parametresi `renderStreamResultsItems` → `StreamResultsList` → `StreamScreenContent` (Landscape + Portrait) → `KitsugiStreamScreen` zincirine bağlandı
- [x] T8 — JVM testleri (`CsStreamRunnerLogicTest`):
  - `testKnownBrokenDomains_DoesNotContain_HdFilmCehennemi2Site`
  - `testResolveBuiltinDomain_NumberedVariant_DoesNotInheritParentDomain`
- [ ] T9 — Kullanıcıda derleme + `./gradlew :app:testDebugUnitTest --tests "*CsStreamRunnerLogicTest*"` (sandbox'ta Android SDK / Google Maven yok, derlenemedi)
- [ ] T10 — Cihaz: HDFC2 ile bölüm aç → `adb logcat -s CsStreamRunner` içinde **`Boş/ölü domain kurtarıldı: '...hdfilmcehennemi2.site' -> '...hdfilmcehennemi.nl'` satırı ARTIK OLMAMALI**
- [ ] T11 — Cihaz: bir eklenti hata verdiğinde **Yeniden Dene** → kart spinner'a döner, yeni istek gider (`CsTrace` `retry` kaydı)
- [ ] T12 — (opsiyonel) Sürüm 2.4.256 + sürüm notları
- [ ] T13 — (opsiyonel) TV'de ayrı Yeniden Dene (şimdilik TV: karta tıkla → doğrula → kapat → otomatik yeniden dener)

---

## 3. Değişen dosyalar

| Dosya | Ne |
|---|---|
| `app/src/main/java/com/kitsugi/animelist/data/cloudstream/CsStreamRunner.kt` | T1, T2, T3 |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/stream/StreamViewModel.kt` | T4, T5 |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/stream/StreamResultsList.kt` | T6 |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/stream/StreamScreenContent.kt` | T7 |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/stream/KitsugiStreamScreen.kt` | T7 |
| `app/src/test/java/com/kitsugi/animelist/data/cloudstream/CsStreamRunnerLogicTest.kt` | T8 |
| `PLAN_TASK_HDFC2_DOMAIN_YONLENDIRME_VE_YENIDEN_DENE_2026-10-12.md` | bu dosya |

---

## 4. Eklenti tarafına not

- Bir eklentiyi yeni domaine taşımadan önce `CsStreamRunner.KNOWN_BROKEN_DOMAINS` listesine bakın; oradaysa uygulama tarafında da listeden çıkarılmalı.
- "Uygulamayı v2.4.254+ yap" HDFC2'yi **çözmez** — zorla yönlendirme v2.4.255'te de var; düzeltme bu commit'le gelen APK'da.
