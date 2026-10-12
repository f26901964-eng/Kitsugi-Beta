# PLAN / TASK — Bangumi Yazma Hatası: "Bilinmeyen hata" ve Hiçbir Kaydın Eklenememesi (v2.4.257)

**Tarih:** 2026-10-12
**Dal:** `arena/be1cb1b4-kitsugi-beta`
**Sürüm:** 2.4.256 → **2.4.257**
**Şikâyet:** "Bangumi'de hâlâ hata alıyorum, listeye hiçbir şey ekleyemiyorum."
Ekran görüntüsü: Steins;Gate detay sayfası → `Bangumi güncellenemedi (Steins;Gate): Bilinmeyen hata`.

---

## 0. Boru hattı (yazma yolu) — belgelendi

Detay **Listeye Ekle** / kayıt güncelleme → `BangumiSyncManager.syncEntryToBangumi`:

1. Tür kontrolü (`Anime/Manga/Movie/TvShow` dışı atlanır)
2. `BangumiAuthStore.getValidToken` (401 alınırsa `forceRefresh` + **bir kez** tekrar)
3. `resolveSubjectId` — stable ID → doğrulama (`getSubject`, tür + başlık + yıl±1) → başlık araması
4. `BangumiApiClient.upsertCollection` → `POST /v0/users/-/collections/{subject_id}`
5. Video türlerinde ilerleme > 0 ise `getSubject` + `setProgress` → `PATCH .../episodes`
6. Hata → `describeWriteFailure` → kullanıcı metni

Sunucu sözleşmesi: `req.JSON` (415) → `mw.NeedLogin` (401) → `mw.NeedScope("write:collection")` (403
`insufficient token scope`); başarı 202 boş gövde; 404 `subject not found` / `subject not collected`.

---

## 1. Kök nedenler

| # | Kök neden | Belirti |
|---|---|---|
| KN-1 | `upsertCollection` istisnayı `runCatching{…}.getOrElse{false}` ile **yutuyordu**; arayüz her koşulda sabit `account_sync_unknown_error` yazıyordu | 401/403/400/404/429 ekranda aynı: "Bilinmeyen hata" |
| KN-2 | Yazma uçlarına 2026-06-25'te kapsam denetimi eklendi (`bangumi/server` PR #1011). Yeni OAuth servisi kapsamı **JSON dizisi**, Go API ise yalnızca **JSON nesnesi** çözebiliyor → token "kapsamsız" sayılıp **403** | Okuma/arama çalışır, hiçbir yazma geçmez |
| KN-3 | 401'de token yenilenmiyordu; süre bilgisi olmayan (geri yüklenmiş) oturumda token sonsuz "geçerli" sayılıyordu | Tek seferlik hata kalıcı hâle gelir |
| KN-4 | Etiket/not gövdesi sunucu dam kurallarına uymuyor olabilirdi (boşluklu/1 harfli etiket > 400; boş `tags: []` mevcut etiketleri siler) | Bazı kayıtlarda 400, bazılarında istenmeyen veri kaybı |
| KN-5 | Bağlantı testi/sonda yoktu; kullanıcı sorunun istemcide mi token'da mı olduğunu ayıramıyordu | Her deneme aynı belirsiz hatayı verir |

---

## 2. Görevler

- [x] T1 — `upsertCollection` / `patchCollection` / `markEpisodes` artık `BangumiApiException` **fırlatır** (kod + sunucu `title`/`description`)
- [x] T2 — `describeWriteFailure`: 401/403-kapsam/403-yasak/404/400/429/5xx ayrı metinler + her durumda sunucunun ham metni
- [x] T3 — `writeWithAuthRetry`: 401 → `forceRefresh` → **bir kez** tekrar; başarısızda `Log.e`
- [x] T4 — `buildCollectionPayload` + `sanitizeTagsForWrite` + `sanitizeCommentForWrite` (≤10 etiket, ≥2 rune, boşluksuz, yazdırılabilir, ≤380 kod noktası); süzgeç boşalırsa `tags` alanı **hiç gönderilmez**
- [x] T5 — Yan etkisiz yazma sondası `probeCollectionWrite()`: `PATCH /v0/users/-/collections/999999999` boş gövde → 404 ise **yazma kapısı açık** (404 yalnızca gövdede `subject` geçtiğinde kabul edilir)
- [x] T6 — `BangumiAuthManager.diagnose()`: uygulama kimliği → oturum → token (`GET /v0/me`) → yazma kapısı satırları
- [x] T7 — **Kişisel Erişim Anahtarı (PAT)** yolu: `connectWithPersonalToken` (`GET /v0/me` ile doğrular), `saveManualToken` (refresh yok, 401'de yenileme denenmez), diyalogda "Anahtarla Bağlan" bölümü + `next.bgm.tv/demo/access-token/create` bağlantısı
- [x] T8 — Diyalogda "Bağlantıyı ve yazma iznini test et" bölümü; çift `emitBangumiSuccess()` çağrısı kaldırıldı
- [x] T9 — TR/EN metinler (`values/`, `values-en/`): yazma hatası, test satırları, PAT bölümü — anahtar sayısı iki dosyada eşit (6955/6955)
- [x] T10 — `AccountSettingsSubPages.kt` KDoc + `PLAN_BANGUMI_INTEGRATION.md` güncellendi; sürüm 2.4.257 + sürüm notları
- [ ] T11 — Kullanıcıda cihaz testi: **Ayarlar → Hesap Bağlantıları → Bangumi → Bağla → "Bağlantıyı ve yazma iznini test et"** → çıkan satırların paylaşılması (§5)
- [ ] T12 — Üst tarafa bildirim: `bangumi/server` PR #1011 sonrası dizi/nesne kapsam uyumsuzluğu (hazır metin: `BANGUMI_YAZMA_TESHISI_RAPORU_2026-10-12.md` §8)

---

## 3. Değişen dosyalar

| Dosya | Ne |
|---|---|
| `data/auth/BangumiApiClient.kt` | İstisna fırlatma, gövde süzgeçleri, `probeCollectionWrite`, `CollectionWriteProbe`, `ACCESS_TOKEN_CREATE_URL` |
| `data/auth/BangumiSyncManager.kt` | `writeWithAuthRetry`, `describeWriteFailure`, silme yolu |
| `data/auth/BangumiAuthManager.kt` | `connectWithPersonalToken`, `sanitizePersonalToken`, yeni `diagnose()` |
| `data/auth/BangumiAuthStore.kt` | `saveManualToken` / `isManualToken` (PAT oturumu) |
| `ui/components/KitsugiBangumiLoginDialog.kt` | PAT bölümü + bağlantı/yazma testi bölümü |
| `ui/screens/settings/AccountSettingsSubPages.kt` | KDoc: OAuth kaydı ve PAT yolu |
| `res/values/strings.xml`, `res/values-en/strings.xml` | Yeni TR/EN metinler |
| `PLAN_BANGUMI_INTEGRATION.md`, `RELEASE_NOTES.md`, `release_notes_v2.4.257.md` | Belgeler |
| `app/build.gradle.kts` | 2.4.257 |

---

## 4. Bilinçli olarak dokunulmayan

- **Kapsam (scope) kusuru istemciden düzeltilemez** — Bangumi sunucusunda düzeltilmeli; istemci tarafında PAT yolu sunuldu.
- **Silme ucu** — `v0` API'sinde koleksiyon silme yok; mevcut davranış (durum = Bırakıldı) korundu.
- **Gövde alan kümesi** — Mihon/Komikku ile aynı alanlar kullanılıyor; yeni alan eklenmedi.
- Otomatik token yenileme **PAT oturumlarında** devre dışı (PAT'ta `refresh_token` yoktur).

---

## 5. Doğrulama durumu ve cihazda önerilen kontroller

**Bu ortamda:** 5 Kotlin dosyası `kotlinc` ile ayrıştırıldı → **0 sözdizimi hatası** (yalnızca
Android/OkHttp bağımlılığı olmadığı için "unresolved reference" gürültüsü). `R.string` referansları ve
TR/EN anahtar eşitliği betikle doğrulandı. Android SDK bulunmadığı için Gradle derlemesi/APK üretimi
bu ortamda **yapılamadı**; derleme Java/Android ortamı olan makinede çalıştırılmalıdır.

**Cihazda:**

| Test satırı | Anlamı | Yapılacak |
|---|---|---|
| `Yazma kapısı AÇIK` | Kapsam geçti; sorun gövde veya başka bir yerde | Tekrar dene — artık gerçek 400 metni görünür |
| `HATA: … kapsam yüzünden reddetti (403 …)` | Token kapsamsız (KN-2) | Aynı ekrandan **Kişisel Erişim Anahtarı** ile bağlan |
| `HATA: token reddedildi (401)` | Token geçersiz/bayat | Bangumi hesabını yeniden bağla |
| `HATA: sunucu yazmayı reddetti (403) ama sebep kapsam değil` | Hesap yasaklı / UA reddi | Sunucu mesajını paylaş |
| `HATA: Bangumi'ye ulaşılamadı` | Ağ/DNS | Bağlantıyı kontrol edip yeniden dene |

1. Steins;Gate (subject 10380) detayından **Listeye Ekle** → hata metni artık HTTP kodunu ve sunucu
   cümlesini içermeli; `Bilinmeyen hata` görünmemeli.
2. PAT ile bağlanıp aynı ekleme işlemini tekrarla → kayıt Bangumi'de `İzliyorum` olarak görünmeli.
3. PAT oturumunda Ayarlar → Bangumi'de "Kişisel erişim anahtarı" oturum satırı görünmeli.
