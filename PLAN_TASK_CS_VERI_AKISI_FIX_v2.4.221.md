# PLAN_TASK — CS Eklenti Video Veri Akışı Fix (v2.4.221)

**Tarih:** 2026-10-09
**Dal:** `arena/b58a7b59-kitsugi-beta` (temel: `2126157` / v2.4.220)
**Şikâyet:** Eklentilerin yarısından fazlası aktif ama neredeyse hiçbirinden video veri linki gelmiyor.
**Durum:** ✅ Kod düzeltmeleri uygulandı ve commit'lendi (`5be1926`). ⏳ Derleme + cihaz testi kullanıcıda.

---

## 1. Plan (Nasıl Teşhis Edildi)

| Adım | Kapsam | Sonuç |
|---|---|---|
| P1 | Önceki teşhis raporları (E2E 201 eklenti, plugin_audit, video_extractor, v2.4.210–220 raporları) | CF/WAF %79 + embed CDN kirliliği tablosu |
| P2 | `CsStreamRunner.kt` boru hattı denetimi (getStreams → runGetStreams → loadAndExtractStreams → extractStreamsFromEpisode → resolveEmbedUrl) | 5 kök neden (KN-1…KN-5) |
| P3 | `StreamViewModel` + `CsPluginStatusTracker` anahtar denetimi | plugin.id ↔ api.name uyumsuzluğu |
| P4 | Canlı depo doğrulaması (Kitsugi-Plugins `builds`, `domain_fixes.json` main) | 166/166 eklenti indirilebilir; `blocked` 176 kayıt artık engel DEĞİL |
| P5 | Düzeltmeler + statik doğrulama (parantez dengesi, çağrı zinciri) | ✅ |

## 2. Görevler (Task List)

### ✅ Yapıldı (bu oturum)
- [x] **KN-1:** Provider zaman aşımı artık bulunan kaynakları çöp atmıyor → `partialSink` ile kurtarma
- [x] **KN-2:** HEAD canlılık doğrulaması paralel (Semaphore 6 × 5 sn; eskiden sıralı × 8 sn)
- [x] **KN-3:** Arama varyantları 3'lü paralel gruplar + sert zaman kutusu (20 sn / CF 40 sn)
- [x] **KN-3b:** `PROVIDER_TIMEOUT_MS` 40→75 sn, `CF_PROVIDER_TIMEOUT_MS` 90→120 sn (iç aşamalarla uyumlu)
- [x] **KN-4:** "Doğrula" sonrası tekrar deneme: blok kontrolü + temizlik `plugin.id`, `plugin.name` ve tüm `api.name` anahtarlarıyla
- [x] **KN-5:** Gerçek sebep kayıtları (`recordSkip`): arama-sıfır / bölüm-bulunamadı / load-timeout / loadLinks-timeout / provider-timeout → UI'da görünür
- [x] CF tespiti `lastErr` okuması `recordSkip`'ten önceye alındı ("Doğrula" butonu korundu)
- [x] Rapor: `CS_EKLENTI_KOK_NEDEN_RAPORU_2026-10-09.md`
- [x] Commit: `5be1926` (3 dosya, +356/−104)

### ⏳ Bekleyen (kullanıcı tarafı)
- [ ] `build-apk.ps1` ile APK derle (bu ortamda JDK/Gradle/SDK yok → derlenemedi)
- [ ] Cihaz testi: sitesi açık eklentiler (FullHDFilmizlesene, DiziMom, Dizilla, SezonlukDizi, HDFilmCehennemi, AnimeciX) — kartlarda gerçek sebep yazdığını doğrula
- [ ] CF bloğu kartlarında "Doğrula" → temiz tekrar deneme kontrolü
- [ ] PR açıp `main`'e birleştir

### 📋 Opsiyonel Takip
- [ ] `domain_fixes.json` `blocked` listesini `main`'de boşalt (176 girdi — artık yalnızca gürültü)
- [ ] `findBestMatch` eşikleri (MIN_MATCH_SCORE 0.45 / token 0.75) — yanlış eşleşme riski bilinçli olarak GEVŞETİLMEDİ; gerekirse ayrı oturumda "çeviri başlık" fallback'i
- [ ] CF/WAF oranı yüksek kaldıkça warmup listesini domain tabanlı otomatikleştir

## 3. Değişen Dosyalar

| Dosya | Değişiklik |
|---|---|
| `app/src/main/java/com/kitsugi/animelist/data/cloudstream/CsStreamRunner.kt` | partialSink, paralel HEAD, arama zaman kutusu, timeout bütçeleri, recordSkip teşhis |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/stream/StreamViewModel.kt` | tracker anahtar uyumu (id+name+api.name), CF retry temizliği |
| `CS_EKLENTI_KOK_NEDEN_RAPORU_2026-10-09.md` | Kök neden raporu |

## 4. Doğrulama Notları
- Parantez/bracket dengesi `git show HEAD` ile karşılaştırmalı eşit (fark: 0)
- Çağrı zinciri `partialSink` ile tutarlı; `getStreamsForUrl` varsayılan parametreyle uyumlu
- Kotlin **derlenmedi** (ortamda JDK/Gradle/Android SDK yok) — derleme kullanıcida şart
