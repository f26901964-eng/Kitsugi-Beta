# Kitsugi Beta v2.4.338

## 🇹🇷 Türkçe

### 🧩 Kurum · tür · etiket artık **hiçbir kaynakta kesilmiyor**

Bu sürüm, "hâlâ az geliyor" geri bildiriminin **ölçülebilir** nedenlerini tek tek kapatıyor.
Her maddenin karşısında kodda doğrulanan gerçek sayı var.

#### 1) Tavanlar kaldırıldı (en büyük kayıp buradaydı)

| Alan | Önce | Sonra |
|---|---|---|
| **Bangumi etiketleri** (ana kaynak) | **40** | **400** |
| Bangumi türleri (`类型`) | 12 | 64 |
| Birleşik tür | 48 | **160** |
| Birleşik etiket | 96 | **600** |
| Birleşik kurum (stüdyo/yapımcı/kanal/dergi) | 64 | **256** |
| Tema / hedef kitle | 24 / 12 | **64 / 32** |
| Etiket kartında görünen (daraltılmış) | **10** | **30** (genişletince tümü) |

Bangumi'nin 40 etiket tavanı en çok veri kaybettiren yerdi: 200+ etiketli uzun serilerde
kullanıcı etiketlerin yalnızca beşte birini görüyordu.

#### 2) Kurumlar artık **silinmiyor**

- `dropProducersThatAreStudios` kaldırıldı. Aynı şirket hem stüdyo hem yapımcı olduğunda
  (MAPPA, BONES, Kyoto Animation …) **yapımcı kaydı listeden siliniyordu**. Artık her rolde
  ayrı kayıt olarak duruyor — "hiçbiri eksik kalmasın".
- Tekilleştirme anahtarı **rol duyarlı** oldu (`rol + ad`). Önceden aynı ad farklı rolde
  (yapımcı + lisans veren) geldiğinde ikinci kayıt birincinin içine yutuluyordu.
- **Dergi / yayınevi** kayıtları "Yayıncı Ağlar" bölümüne karışmıyor; ayrı **"Dergi / Yayın"**
  bölümü açıldı (tek roldeyse "Dergiler" veya "Yayıncılar" başlığı).
- MAL `licensors` artık `LICENSOR` rolünde (önceden yapımcı sayılıyordu).

#### 3) Çapraz toplayıcı **4 → 6 kaynak**, ve Bangumi sayfaları da aynı havuzu kullanıyor

| Kaynak | Katkı |
|---|---|
| MAL/Jikan | tür · tema · hedef kitle · stüdyo · yapımcı · lisans veren |
| AniList | tür · etiket (rank + spoiler) · stüdyo |
| Shikimori | tür/tema/hedef kitle (`kind` ayrımıyla) · stüdyo |
| Kitsu | kategori · yapım şirketleri (rol ile) |
| **Bangumi (yeni)** | 400'e kadar etiket · infobox kurumları (动画制作 · 製作 · 製作委員会 · 制作協力 · レーベル · 配給 · 放送电视台 · 掲載誌) |
| **TMDB (yeni)** | tür · anahtar kelime · yapım şirketleri · kanallar |

- Bangumi, MAL kimliğiyle açılmadığı için **başlık eşleşmesi** ile bulunuyor; normalize edilmiş
  tam başlık eşleşmesi + yıl toleransı (±1) doğrulanmadan veri kullanılmıyor.
- **Bangumi sayfaları** da artık Shikimori + Kitsu + TMDB verisini alıyor; simetri tamam.
- Süre tavanı 6 → 8 sn (kaynak başına 5,5 sn); geciken kaynak atlanır, sayfa açılmaya devam eder.

#### 4) Cihazda doğrulama

```
adb logcat -s KitsugiTaxonomySources KitsugiDetailClient KitsugiBangumiDetailClient
```

Beklenen çıktı (aynı yapım için):

```
KitsugiTaxonomySources: Kaynak bazlı: jikan=5t/12e/4k | anilist=4t/38e/2k |
shikimori=6t/9e/1k | kitsu=6t/4e/3k | bangumi=5t/120e/9k | tmdb=4t/15e/7k
Taksonomi toplandı: tür=12 etiket=185 stüdyo=4 yapımcı=14
KitsugiDetailClient: Birleşik taksonomi: tür=12 etiket=185 stüdyo=4 yapımcı=14 kanal=3 dergi=1
```

Etiket sayısı 100'ün altındaysa o yapım gerçekten az etiketlidir; **tek bir kaynağın `yok`
yazması** ise kimlik çözümünün o kaynakta bulunamadığını gösterir (diğerleri çalışmaya devam eder).

#### 5) Otomatik koruma

`scripts/audit_taxonomy_sources.py` artık üç şeyi de denetliyor:
- 28 alanın okunması + **6 çapraz kaynağın** hepsinin çağrılması,
- tavanların altına inilmemesi (tür ≥160, etiket ≥600, kurum ≥256),
- `dropProducersThatAreStudios` gibi **kurum eksilten** çağrıların geri gelmemesi.

---

## 🇬🇧 English

### 🧩 Organizations · genres · tags are no longer **truncated by any source**

This release closes every measurable reason behind the "still too few" feedback. Each item lists
the verified number from the code.

#### 1) Caps removed (this was the biggest loss)

| Field | Before | After |
|---|---|---|
| **Bangumi tags** (primary source) | **40** | **400** |
| Bangumi genres (`类型`) | 12 | 64 |
| Merged genres | 48 | **160** |
| Merged tags | 96 | **600** |
| Merged organizations (studio/producer/network/magazine) | 64 | **256** |
| Themes / demographics | 24 / 12 | **64 / 32** |
| Tags visible in the collapsed card | **10** | **30** (expanded shows all) |

The 40-tag Bangumi cap caused the biggest loss: on long-running series with 200+ tags the user
saw only a fifth of them.

#### 2) Organizations are no longer **deleted**

- `dropProducersThatAreStudios` removed. When a company was both studio and producer
  (MAPPA, BONES, Kyoto Animation …) the producer entry was **dropped from the list**. Each role
  now keeps its own record.
- Dedup key is **role-aware** (`role + name`). Previously a second role (producer + licensor)
  was swallowed by the first record.
- **Magazines / publishers** no longer leak into the "Networks" section; they get their own
  **"Magazine / Publication"** section (labelled "Magazines" or "Publishers" when uniform).
- MAL `licensors` now carry the `LICENSOR` role (they were counted as producers).

#### 3) Cross-source collector **4 → 6 sources**, and Bangumi pages share the same pool

| Source | Contribution |
|---|---|
| MAL/Jikan | genres · themes · demographics · studios · producers · licensors |
| AniList | genres · tags (rank + spoiler) · studios |
| Shikimori | genre/theme/demographic (`kind`) · studios |
| Kitsu | categories · productions (with role) |
| **Bangumi (new)** | up to 400 tags · infobox organizations |
| **TMDB (new)** | genres · keywords · production companies · networks |

- Bangumi is not reachable by MAL id, so it is matched by **normalized exact title** plus a
  year tolerance of ±1; no data is used without that verification.
- **Bangumi pages** now also pull Shikimori + Kitsu + TMDB data — symmetry completed.
- Time budget 6 → 8 s (5.5 s per source); a slow source is skipped and the page still opens.

#### 4) Verifying on device

```
adb logcat -s KitsugiTaxonomySources KitsugiDetailClient KitsugiBangumiDetailClient
```

If a source prints `yok`, its id could not be resolved (the others keep working). A tag count
below 100 means the title genuinely has few tags.

#### 5) Automated guard

`scripts/audit_taxonomy_sources.py` now also verifies that all **6 cross sources** are called,
that the caps never drop below their floor, and that organization-shrinking calls such as
`dropProducersThatAreStudios` never come back.
