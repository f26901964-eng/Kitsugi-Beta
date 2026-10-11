# Eklenti güncellemeleri uygulamada görünmüyor — kök neden + düzeltme (2026-10-12)

**Şikâyet:** "Kitsugi-Plugins deposunda kendi eklentilerimi güncelliyorum ama uygulama
güncellemeleri görmüyor."

**Sonuç:** Sorun tek bir yerde değil, **iki katmanda** birden. Zincirin ilk halkası depo
tarafında (yayın hiç yapılmıyor), ikinci halkası uygulama tarafında (yayın yapılsa bile
sürüm numarası artırılmadıysa güncelleme tespit edilemiyor + depo engellendiğinde bayat
gömülü liste "güncel" sanılıyor).

---

## 1) Depo tarafı: `builds` dalı 5 Ekim'den beri güncellenmiyor (asıl neden)

Uygulama **yalnızca** `f26901964-eng/Kitsugi-Plugins` deposunun **`builds`** dalını okur:

```
https://raw.githubusercontent.com/f26901964-eng/Kitsugi-Plugins/builds/repo.json
  → pluginLists: .../builds/plugins.json
```

### Kanıt 1 — `builds` dalının son commit'i
| dal | son commit (commit tarihi) | mesaj |
|---|---|---|
| `builds` | **2026-10-05 10:08** | "fix: remove all 63 adult/NSFW plugins, update plugins.json and repo.json to 166 verified clean plugins" |
| `main` | 2026-10-07 22:58 | "fix: derlenmemis 3 eklentiyi tetikle, WFilmİzle adini duzelt, manifest dogrulamasi ekle" |
| `arena/d4c5fe64-kitsugi-plugins` (geçici) | **2026-10-11 22:27** | "fix(RoketDizi): arama coklu-varyant + cerez isitma (v19) ..." |

### Kanıt 2 — yayınlanan manifest ile kaynak kod ayrışmış
| | sürüm |
|---|---|
| `builds/plugins.json` → `RoketDizi` | **15** (`fileHash: sha256-41d194bf…`) |
| `main/RoketDizi/build.gradle.kts` | 15 |
| `arena…/RoketDizi/build.gradle.kts` | **19** |

Yani 4 sürümlük iş **hiç yayınlanmadı**. Uygulama 15'i gördüğü için "güncelleme yok" diyor —
teknik olarak haklı, çünkü yayınlanan tabloda 15 yazıyor.

### Kanıt 3 — CI'da yayın adımı atlanıyor/boş çıkıyor
`CloudStream Derleyici` iş akışının (run no → sonuç):

| run | olay | dal | "Derlemeleri Yükle" adımı |
|---|---|---|---|
| 15, 16 | `push` | `arena/d4c5fe64-kitsugi-plugins` | **skipped** |
| 2 | `push` | `main` (7 Ekim) | success ama stage'de değişiklik yok → `git diff --staged --quiet` → push yok |

Atlanma nedeni, o commit'teki (556ee341) iş akışı koşulu:

```yaml
      - name: Derlemeleri Yukle
        if: github.ref == 'refs/heads/main' && github.event.inputs.teshis != '1'
```

Geçici `arena/...` dalına yapılan 12+ push'un derleme çıktıları runner'da kaldı ve
`builds` dalına hiç ulaşmadı.

### Kanıt 4 — "derlenen" adımı erken çıkıyor, prebuilt dosyaları hiç kopyalanmıyor
`Derleyici.yml` içindeki "Degisen Eklentileri Bul ve Derle" adımı:

```bash
PLUGINS_TO_BUILD=""
for dir in $CHANGED; do
  if [ -f "$dir/build.gradle.kts" ]; then PLUGINS_TO_BUILD="$PLUGINS_TO_BUILD :$dir:make"; fi
done

if [ -z "$PLUGINS_TO_BUILD" ]; then
  echo "Hicbir plugin degismemis, islem atlaniyor."
  exit 0          # ← ADIM BURADA BİTİYOR
fi
...
./gradlew $PLUGINS_TO_BUILD makePluginsJson
cp "$dir/build/$dir.cs3" $GITHUB_WORKSPACE/builds/      # prebuilt kopyaları
cp prebuilt/*.cs3 $GITHUB_WORKSPACE/builds/prebuilt/    # bu satırlar exit'ten SONRA
python3 merge_plugins.py
cp build/plugins.json $GITHUB_WORKSPACE/builds
```

Kullanıcının kendi eklentileri **`prebuilt/`** klasöründen yayınlanıyor
(`plugins.json` → `.../builds/prebuilt/<Ad>.cs3`). `prebuilt/` bir Gradle modülü olmadığı
için `PLUGINS_TO_BUILD` boş kalıyor → adım `exit 0` ile bitiyor → **hiçbir `.cs3` ve
`plugins.json` güncellenmiyor**. 7 Ekim'deki `main` push'unda görülen tablo tam olarak budur:
yayın adımı "success" ama içeride "Değişiklik yok, push atlanıyor".

### Hazır iş akışı yaması (Kitsugi-Plugins → `.github/workflows/Derleyici.yml`)

Aşağıdaki iki parçayı uygulamak, yukarıdaki 4 kanıtın tamamını kapatır:

```yaml
      - name: Degisen Eklentileri Bul ve Derle
        run: |
          cd $GITHUB_WORKSPACE/src

          MANUAL="${{ github.event.inputs.plugins }}"
          if [ -n "$MANUAL" ]; then
            CHANGED="$MANUAL"
          else
            CHANGED=$(git diff --name-only HEAD~1 HEAD | grep -oP '^[^/]+' | sort -u)
          fi
          echo "Degisen klasorler: $CHANGED"

          PLUGINS_TO_BUILD=""
          for dir in $CHANGED; do
            if [ -f "$dir/build.gradle.kts" ]; then
              PLUGINS_TO_BUILD="$PLUGINS_TO_BUILD :$dir:make"
            fi
          done

          chmod +x gradlew || true
          if [ -n "$PLUGINS_TO_BUILD" ]; then
            ./gradlew $PLUGINS_TO_BUILD makePluginsJson
            for dir in $CHANGED; do
              CS3="$dir/build/$dir.cs3"
              [ -f "$CS3" ] && cp "$CS3" $GITHUB_WORKSPACE/builds/
            done
          else
            echo "Derlenecek Gradle modulu yok — prebuilt/manifest adimina geciliyor."
          fi

          # prebuilt kopyalari ve manifest artik HER ZAMAN calisir
          mkdir -p $GITHUB_WORKSPACE/builds/prebuilt
          cp prebuilt/*.cs3 $GITHUB_WORKSPACE/builds/prebuilt/ 2>/dev/null || true
          cp prebuilt/*.cs3 $GITHUB_WORKSPACE/builds/ 2>/dev/null || true
          python3 merge_plugins.py
          cp build/plugins.json $GITHUB_WORKSPACE/builds

      - name: Derlemeleri Yukle
        if: ${{ github.event.inputs.teshis != '1' }}
        run: |
          cd $GITHUB_WORKSPACE/builds
          git config --local user.email "actions@github.com"
          git config --local user.name "GitHub Actions"
          git add .
          if git diff --staged --quiet; then
            echo "Degisiklik yok, push atlaniyor."
            exit 0
          fi
          git commit -am "Derleme: ${GITHUB_SHA}"
          git push origin HEAD:builds
```

Notlar:
- `git push origin HEAD:builds` (force yerine normal push) geçmişi korur; force gerekliyse
  `git push --force origin HEAD:builds` kullanılabilir.
- Yayın adımı artık `main`'e bağlı değil: hangi dalda çalışırsa `builds` dalını günceller.
- Geçici teşhis adımları ve `teshis-cikti` yayını kaldırılmalıdır.

### Depo tarafı için önerilen düzeltme (Kitsugi-Plugins)
1. Yayın adımının koşulunu `main` dışındaki çalışma dallarına da aç (geçici dal dahil) —
   `arena/d4c5fe64-kitsugi-plugins` sürümünde bu yapılmış durumda, kalıcı olarak `main`'e taşınmalı.
2. "Degisen Eklentileri Bul ve Derle" adımını ikiye böl: erken `exit 0` **yalnızca Gradle
   derlemesini** atlamalı; `prebuilt/*.cs3` kopyalama + `merge_plugins.py` **her zaman**
   çalışmalı.
3. `plugins.json`'a yazılan `fileHash`/`fileSize` alanlarını koru (uygulama artık
   içerik karşılaştırmasını bunlarla yapıyor).
4. Yayın sonrası doğrulama: `builds/plugins.json` içindeki `version` değeri ile kaynaktaki
   `build.gradle.kts` `version` değerini karşılaştıran küçük bir kontrol ekle (uyarı yeterli).

---

## 2) Uygulama tarafı: güncelleme tespiti sürüm numarasına bağımlıydı

Depo tarafı düzelse bile ikinci bir tuzak vardı: **sürüm numarası artırılmadan yapılan
yeniden derlemeler görünmüyordu.**

- `plugins.json` her kayıt için `fileHash` (SHA-256) ve `fileSize` taşıyor.
- Uygulama ise 6 ayrı yerde kopyalanmış `latest.version > installed.version` kontrolü
  yapıyordu (kurulu liste, depo kartı, eklenti satırı, otomatik güncelleme, toplu güncelleme,
  "Yenile" denetimi).
- Kaynak sürümü elle artırılmayan bir derleme (ör. yalnızca domain düzeltmesi) yayınlandığında
  dosya değişir, `version` aynı kalır → uygulama "güncel" der.

### Yapılan düzeltmeler (bu depo)

| Dosya | Değişiklik |
|---|---|
| `data/cloudstream/CsPluginUpdateChecker.kt` **(yeni)** | Güncelleme kararının tek merkezi: `Reason.VERSION` / `Reason.CONTENT` / `NONE`. Sürüm eşitse kurulu dosyanın SHA-256'sı manifestteki `fileHash` ile karşılaştırılır. Yedek kaynaktan gelen kayıtlar karar dışı bırakılır. |
| `data/cloudstream/CsInstalledFingerprint.kt` **(yeni)** | Kurulu `.cs3` dosyalarının SHA-256 parmak izi; `boyut:lastModified:hash` anahtarıyla önbelleklenir, indirme/kaldırma sonrası geçersiz kılınır. |
| `data/cloudstream/CsPluginLoader.kt` | İndirme tamamlandığında parmak izi geçersiz kılınır (aksi halde yeni dosya "eski" sanılıp güncelleme tekrar tekrar denenecekti). Ayrıca `api.github.com` adaylarında `Accept: application/vnd.github.raw` başlığı gönderilir (GitHub aksi halde base64 JSON döner). |
| `data/remote/CloudstreamRepoClient.kt` | Ayna zinciri: birincil → `api.github.com` muadili → jsDelivr → (Kitsugi için) Codeberg. Yedek kaynaktan gelen yanıtlar `fromOfflineFallback` ile işaretlenir. **Hata düzeltmesi:** resmi depo URL'si `Kitsugi-Plugins` (tireli) olduğu hâlde yedek kontroller yalnızca `KitsugiPlugins`/`codeberg.org` arıyordu; bu yüzden engelleme durumunda Codeberg yedeği hiç denenmiyor, doğrudan bayat gömülü listeye düşülüyordu. Ayna denemeleri 20 sn bütçeyle sınırlandı. |
| `data/repository/CloudstreamRepoRepository.kt` | Eşitleme zamanı artık **kalıcı** (SharedPreferences): canlı veri gelirse 15 dk, çevrimdışıysa 2 dk sonra yeniden dener. Otomatik güncelleme kararı `CsPluginUpdateChecker` üzerinden. Yedek kaynakla yapılan denetim "eşitlendi" sayılmaz. |
| `data/cloudstream/CsAutoUpdateBackoff.kt` | Bekleme anahtarı `id@sürüm@hash` oldu: aynı sürüm numarasıyla yeni dosya yayınlanırsa bekleme atlanmaz, hemen denenir. |
| `ui/app/AddonViewModel.kt` | Güncelleme sayımı/sayımı mesajları yeni karar merkezini kullanıyor; canlı depoya ulaşılamadığında "✅ Tüm eklentiler güncel" yerine **"⚠️ Eklenti deposuna ulaşılamadı — güncellemeler denetlenemedi"** yazıyor. İçerik güncellemeleri ayrıca bildirilir. |
| `ui/components/addons/*` | Kurulu eklenti satırı, depo kartı ve eklenti satırı aynı karar merkezini kullanıyor. İçerik değişikliğinde rozet **"⟳ Yeniden derlendi (v15)"**, düğme **"⟳ Yeniden kur (v15)"** olur. |
| `utils/CloudstreamUrlHelper.kt` | İndirme adaylarına `api.github.com/.../contents/prebuilt/<Ad>.cs3?ref=builds` aynası eklendi. |
| `app/src/test/.../CsPluginUpdateCheckerTest.kt` **(yeni)** | Sürüm/içerik kararları, hash normalizasyonu, manifest eklentisi ayrımı. |
| `app/src/test/.../CloudstreamRepoMirrorTest.kt` **(yeni)** | Tireli depo adı tanıma, `api.github.com` dönüşümü, ayna sırası. |

### Beklenen davranış (düzeltme sonrası)
1. Kullanıcı eklentiyi güncelleyip `builds` dalına yayınlar (sürüm artırılmış **veya**
   artırılmamış — fark etmez).
2. Uygulama açılışında / "Yenile"de manifestler canlı kaynaktan çekilir (raw → api.github.com
   → jsDelivr → Codeberg).
3. Sürüm numarası arttıysa **veya** dosyanın SHA-256'sı kurulu dosyadan farklıysa eklenti
   "güncellenebilir" görünür ve otomatik güncelleme indirir.
4. Depoya hiç ulaşılamazsa uygulama bunu **açıkça** söyler; bayat gömülü listeyle
   "her şey güncel" demez.

---

---

## Ek: hazır sürüm notu (RELEASE_NOTES.md'ye eklenecek blok)

```markdown
## 🇹🇷 Türkçe (v2.4.253)

### 🔄 1. Eklenti Güncellemelerinin Görünmemesi Düzeltildi
- **Kök Neden (depo tarafı):** Uygulama yalnızca `Kitsugi-Plugins` deposunun `builds` dalını
  okur; bu dal 5 Ekim'den beri güncellenmiyordu (yayında `RoketDizi v15`, kaynakta `v19`).
  CI'daki yayın adımı yalnızca `main` dalında çalışıyordu ve prebuilt kopyalama/manifest
  üretimi erken `exit 0` ile atlanıyordu.
- **Kök Neden (uygulama tarafı):** Güncelleme kararı 6 ayrı yerde yalnızca
  `version > version` karşılaştırmasıyla veriliyordu; sürüm numarası artırılmadan yeniden
  derlenen eklentiler görünmüyordu. Ayrıca depoya ulaşılamadığında APK içindeki bayat liste
  sessizce "canlı" sanılıp "Tüm eklentiler güncel" deniyordu.
- **Çözüm:** `CsPluginUpdateChecker` + `CsInstalledFingerprint` ile sürüm **veya** dosya
  içerik parmak izi (SHA-256) farkı güncelleme sayılıyor. Manifest çekiminde
  raw → api.github.com → jsDelivr → Codeberg ayna zinciri (20 sn bütçe) kullanılıyor,
  yedek kaynaktan gelen veriler işaretleniyor ve "denetlenemedi" uyarısı gösteriliyor.
  Tireli depo adı (`Kitsugi-Plugins`) kontrolü düzeltildi (yedekler artık gerçekten deneniyor).
- **UI:** İçerik güncellemesinde "⟳ Yeniden derlendi (v15)" / "⟳ Yeniden kur (v15)".
- **Testler:** `CsPluginUpdateCheckerTest`, `CloudstreamRepoMirrorTest`.

## 🇬🇧 English (v2.4.253)

### 🔄 1. Plugin Updates Not Appearing — Fixed
- **Root cause (repo side):** the app only reads the `builds` branch of `Kitsugi-Plugins`,
  which had not been updated since Oct 5 (published `RoketDizi v15` vs source `v19`): the CI
  publish step ran on `main` only, and prebuilt copying/manifest generation were skipped by an
  early `exit 0`.
- **Root cause (app side):** update detection was copy-pasted `version > version` in six
  places, so rebuilds without a version bump were invisible; and when the repo was unreachable
  the bundled (frozen) plugin list was treated as live, reporting "everything up to date".
- **Fix:** `CsPluginUpdateChecker` + `CsInstalledFingerprint` treat a VERSION or a CONTENT
  (SHA-256) mismatch as an update. Manifest fetching uses a mirror chain
  (raw → api.github.com → jsDelivr → Codeberg, 20 s budget); fallback data is flagged and the
  UI reports "could not check for updates" instead of "all up to date". The hyphenated repo
  name (`Kitsugi-Plugins`) check was fixed so the fallbacks actually run.
- **UI:** content-only updates show "⟳ Rebuilt (v15)" / "⟳ Reinstall (v15)".
- **Tests:** `CsPluginUpdateCheckerTest`, `CloudstreamRepoMirrorTest`.
```

## 3) Kullanıcının yapması gerekenler
1. **Şimdi bir kez:** Kitsugi-Plugins'te `Derleyici` iş akışını `main`'e (veya istediğin dala)
   push ile çalıştır ve `builds` dalının gerçekten güncellendiğini gör
   (`builds/plugins.json` içindeki `RoketDizi.version` = 19 olmalı). Aksi halde uygulama tarafı
   düzeltmeleri ne kadar iyi olursa olsun 19'u göremez.
2. Geçici `arena/d4c5fe64-kitsugi-plugins` dalındaki işi `main`'e al; iş akışındaki
   "geçici teshis" adımlarını ve `teshis-cikti` yayınını temizle.
3. Bundan sonra sürüm numarasını artırmak yine iyi bir alışkanlık (kullanıcıya "v15 → v16"
   demek için), ama **artık zorunlu değil**: uygulama içerik değişikliğini kendisi yakalıyor.
