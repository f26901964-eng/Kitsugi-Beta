# 📋 PLAN / TASK — Simkl Stüdyo/Yapımcı Çip Açılışı + Seslendirmen Sheet'i Tam Liste Düzeltmesi

**Tarih:** 2026-10-10 · **Dal:** `arena/d171ec6e-kitsugi-beta` · **Commit:** `5a0e020`
**Durum:** Kod değişiklikleri TAMAM; sandbox'ta JDK/Gradle olmadığı için derleme yapılamadı → doğrulama kullanıcı tarafında (APK).

---

## 1. Şikâyet (kullanıcı raporu, 3 ekran görüntüsüyle)

1. **Simkl kaynağında stüdyo ve yapımcı detay sayfalarına tıklanınca** sayfa "galiba AniList üzerinden" açılmaya çalışıyor ve **neredeyse hiçbiri açılmıyor** (1. görsel: Frieren / Simkl detayında `Stüdyolar: Madhouse`, `Yapımcılar: TOHO, Shogakukan, Nippon Television Network Corporation, Shogakukan-Shueisha Productions, dentsu, Aniplex` çipleri).
2. **Karakterler sekmesindeki seslendirmen butonu** (mikrofon ikonlu buton) hangi kaynakta olursa olsun seslendirmenlerin **tamamını göstermiyor** (2. görsel: "Frieren - Seslendirmenler" sheet'inde yalnızca *Atsumi Tanezaki*), oysa karakter detay sayfası tam çok-dilli listeyi gösteriyor (3. görsel: Japonca, İngilizce, Fransızca, Portekizce, İspanyolca, Almanca, İtalyanca, Tayca).

---

## 2. Kök Nedenler

| # | Kök neden | Etki |
|---|---|---|
| A | Simkl'in kendi stüdyo/yapımcı ucu **yok**; detaydaki çipler TMDB zenginleştirmesinden geliyor. Eski kalıcı önbellek satırları (`PersistentDetailCache`) çipleri **kaynak etiketi olmadan** (`source=""`) saklıyordu. | `resolveStudioClickSource` kaynaksız çipi medya türüne göre körlükle `jikan`'a çözüyor; TMDB şirket ID'si MAL/Jikan uzayında ya 404 oluyor ya da başka bir şirkete denk geliyordu → "açılmıyor / yanlış açılıyor". |
| B | `KitsugiStudioClient.fetchStudioDetail` birincil sağlayıcı boş döndüğünde **kurtarma yolu yoktu**; `else -> null` doğrudan "Stüdyo detayları yüklenemedi" hatası üretiyordu. | Sağlayıcı/hız limiti/uzay uyuşmazlığında çip ölü uç. |
| C | Karakterler sekmesi sheet'i, liste düzeyindeki `character.voiceActors` alanını olduğu gibi basıyordu. Liste verisi çoğu kaynakta tek seslendirmen taşır (Jikan/MAL yalnızca Japonca; TMDB yalnızca oyuncu). | Sheet tek kişi gösteriyor; tam liste yalnızca karakter detay sayfasında (`fetchCharacterDetail`) mevcuttu. |

---

## 3. Çözüm (değişen dosyalar)

| Dosya | Değişiklik |
|---|---|
| `data/remote/KitsugiStudioClient.kt` | **Sağlayıcılar arası isim kurtarması:** birincil arama `null` dönerse veya dönen şirket adı tıklanan çiple uyuşmazsa şirket adıyla **Jikan → AniList → TMDB** zinciriyle doğru kimlik aranır (`recoverStudioByName`); birincil sonuç ad eşleşmesiyle sağlamsa ek istek atılmaz. Yeni `searchTmdbCompanyId` (TMDB `/search/company`) eklendi. `studioId <= 0` erken dönüşü artık yalnızca ad da yoksa geçerli (adsız kimliksiz çip hariç isimle kurtarma mümkün). |
| `data/remote/KitsugiDetailClient.kt` | `detailCacheKey` içine **`simkl` → `${base}_sm1`** sürüm eki: eski kaynaksız çipli Simkl önbellek satırları geçersiz; detay bir sonraki açılışta kaynak etiketli (`source="tmdb"`) çiplerle yeniden çekilir. |
| `ui/screens/detail/CharactersTab.kt` | Seslendirmen sheet'i açılışta **zenginleştirme** yapıyor: önce `DetailCache.getCharacterDetail(char.source, char.id)` (karakter detayı daha önce açıldıysa tam liste anında), yoksa `KitsugiCharacterClient.fetchCharacterDetail` ile tam çok-dilli liste çekilip `putCharacterDetail` ile önbelleğe yazılıyor; liste mevcut listeden uzunsa sheet'e dökülüyor (başlık dili `displayPersonName` ile korunur). Yüklenirken küçük `CircularProgressIndicator`. Buton tıklaması listeyi önceden tohumlayıp boş ilk kareyi önlüyor. Tüm kaynaklarda geçerli (karakterin kendi `source`/ID uzayıyla sorgulanır). |

---

## 4. Davranış Notları

- **Kurtarma zinciri asla geriletmez:** birincil sonuç geçerliyken ek istek atılmaz; kurtarma yalnızca `null`/ad-uyuşmazlığı durumunda devreye girer ve bulamazsa birincil sonuç korunur.
- **Jikan/MAL dalı** zaten iç isim doğrulamasına sahipti; yeni zincir `anilist`, `tmdb`, `shikimori`, `bangumi` ve kaynaksız (`else`) dalları da kapsar.
- Sheet zenginleştirmesi karakterin **kendi kaynak kimliğiyle** yapılır: `jikan`→MAL karakter, `anilist`→AniList karakter, `tmdb`→TMDB kişi (canlı aktör kadrosu; tek "oyuncu" kalır, doğru davranış). AniList görsel eşleştirmesiyle kimliği değişen karakterler `source="anilist"` taşıdığı için uzay karışmaz.
- `_sm1` anahtarı yalnızca Simkl önbelleğini geçersiz kılar; diğer kaynakların önbelleği etkilenmez.

---

## 5. Doğrulama

- Sandbox'ta JDK/Gradle **yok** → derleme/birim test çalıştırılamadı (önceki görevlerle aynı kısıt). Sözdizimi: dosya bazında parantez/dizi/kaşeli denge kontrolü + satır satır gözden geçirme (3 dosya, temiz).
- Kullanıcı tarafında kontrol listesi:
  1. Simkl → Frieren detayı → `Madhouse`, `TOHO`, `dentsu`, `Aniplex`, `Shogakukan`, `Nippon Television Network Corporation`, `Shogakukan-Shueisha Productions` çiplerinin her biri kendi şirket sayfasını açmalı (eski önbellekli kurulumda ilk açılışta detay `_sm1` anahtarıyla yenilenir).
  2. Herhangi bir kaynakta Karakterler sekmesi → mikrofon butonu → sheet önce mevcut listeyi, ardından tam çok-dilli seslendirmen listesini göstermeli (3. görseldeki gibi).
  3. Stüdyo sayfası bir sağlayıcıda hız limitine takılsa bile ad kurtarması sayesinde diğer sağlayıcıdan açılmalı.
