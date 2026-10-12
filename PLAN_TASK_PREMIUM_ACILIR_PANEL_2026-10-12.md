# PLAN / TASK — Premium Açılır Panel: Listem Filtre Sheet'i + Ayarlar Dropdown Yenilemesi

**Tarih:** 2026-10-12
**Durum:** Kod değişiklikleri tamamlandı ve `arena/68732c90-kitsugi-beta` dalına push'landı (commit `e70d69e`). Android derlemesi bu ortamda Java/JDK ve Android SDK bulunmadığı için çalıştırılamadı; bunun yerine tüm değiştirilen Kotlin dosyaları tree-sitter Kotlin grameriyle parse edilip doğrulandı, strings.xml dosyaları XML olarak valide edildi.

## Kullanıcı isteği
"Listem" ekranında başlığın altında satır içi (inline) açılan filtre & sıralama alanının,
uygulama temasıyla uyumlu, modern, kullanıcı dostu, "basit olmayan / az egzotik açılan,
premium hissettiren" yeni bir açılır panele taşınması; bu yeni panel dilinin **ayarlar
sayfasındaki tüm sekmelerdeki açılır panelleri de** değiştirmesi.

## Tasarım kararları

### 1) `KitsugiPremiumSheet` (yeni ortak bileşen)
`app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiPremiumSheet.kt`

- **Açılış:** `Animatable` + `spring(dampingRatio = 0.8f, stiffness = StiffnessMediumLow)` —
  panel alttan yükselirken yay 1'i hafifçe aşar (overshoot) → küçük bir "bounce" ile
  egzotik/premium açılış. Kapanış `tween(220, FastOutLinearInEasing)` ile yumuşak.
- **Yüzey:** `KitsugiColors.SurfaceStrong` opak zemin + 32dp üst köşe; vurgu rengiyle
  dikey **gradyan kenarlık** (0.6 → 0.12 → transparent) ve `shadow(ambientColor/spotColor =
  accent)` ile **aurora glow**.
- **Detaylar:** gradyan sürükleme kulpu, gradyan ikon karosu + başlık + "N aktif" rozeti +
  yuvarlak kapatma butonu, ortası parlayan **aurora saç çizgisi** ayracı, kaydırılabilir içerik.
- **Etkileşim:** skrim dışına dokunma / geri tuşu / X → animasyonlu kapanış; çift kapanmaya
  karşı `isDismissing` koruması.
- **Android TV:** aynı yüzey diliyle ortalanmış dialog (scale+fade girişi, D-pad odak isteği).

### 2) `KitsugiMyListFilterSheet` (Listem filtre paneli)
`app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/components/KitsugiMyListFilterSheet.kt`

- Eski satır içi `RichMyListFilterPanel` alanı `MyListHeaderSection` içinden kaldırıldı;
  sağ üstteki filtre ikonu artık bu premium sheet'i açıyor (`showFilterPanel` durumu aynı
  kaldığı için MyListScreen ve KitsugiUserMediaListScreen davranışı korunur).
- Bölümler gradyan noktalı premium başlıklarla: İçerik Türü hapları (ortak
  `MyListContentTypeRow` olarak `KitsugiMyListFilterComponents.kt` içine çıkarıldı),
  Durum, Medya Türü, Puan Aralığı, Yayın Yılı, Ekstra/Özel, Sıralama.
- Alt eylemler: **Temizle** (tüm filtreleri + sıralamayı sıfırlar) ve gradyan **Bitti**
  (paneli kapatır). Filtreler dokunuldukça anında uygulanır.
- Başlık/baden/buton metinleri i18n'li: `mylist_filter_sheet_title`,
  `mylist_filter_active_count`, `mylist_filter_done` (TR + EN strings.xml).

### 3) Ayarlar açılır panellerinin toplu yükseltilmesi
`app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiSettingsComponents.kt`

- `KitsugiDropdownMenu` premium yüzeye geçirildi: 22dp köşe, spring "pop" giriş
  (`scaleIn(spring(0.72f, 700f), initialScale = 0.86f)`), gradyan kenarlık, renkli glow
  gölgesi ve içeriğin arkasında **aurora üst ışık bandı**.
- `KitsugiMenuItem` yenilendi: seçili satırda `accentBrushWithAlpha(0.16f)` zemin +
  solda yay animasyonlu **gradyan seçim çubuğu** + spring ile seken onay işareti
  (çubuk her zaman yer kapladığı için layout kayması yok).
- Ayarlar'daki TÜM sekmeler (Tercihler, Oynatıcı, Eklentiler/Stremio, P2P vb.) bu bileşeni
  kullandığı için hepsi tek elden yeni premium panele geçti; ham Material `DropdownMenu`
  kullanımı zaten yoktu (grep ile doğrulandı).

## Değişen dosyalar
| Dosya | Tür |
|---|---|
| `app/.../ui/components/KitsugiPremiumSheet.kt` | YENİ |
| `app/.../ui/screens/mylist/components/KitsugiMyListFilterSheet.kt` | YENİ |
| `app/.../ui/components/KitsugiSettingsComponents.kt` | Değişti |
| `app/.../ui/screens/mylist/MyListComponents.kt` | Değişti |
| `app/.../ui/screens/mylist/KitsugiMyListFilterComponents.kt` | Değişti |
| `app/src/main/res/values/strings.xml` | Değişti |
| `app/src/main/res/values-en/strings.xml` | Değişti |

## Doğrulama
- Bu sandbox'ta JDK/Android SDK yok; Gradle bağımlılıkları için ağ kısıtlı → **derleme
  çalıştırılamadı** (dürüst not).
- Bunun yerine: değişen 5 Kotlin dosyası tree-sitter (`@tree-sitter-grammars/tree-sitter-kotlin`)
  ile parse edildi → ERROR/MISSING düğümü yok; bozuk dosyayla pozitif kontrol yapıldı.
- `values/strings.xml` ve `values-en/strings.xml` XML parser ile valide edildi.
- Tüm yeni API kullanımları repodaki mevcut çağrı desenleriyle birebir karşılaştırıldı
  (`gradient.background/border`, `tvClickable`, `shadow(ambientColor=..)`, `DialogProperties
  (decorFitsSystemWindows=..)`, `requestFocusAfterFrames` vb.).

## Risk / takip
- İlk cihaz derlemesinde `Icons.Rounded.Tune` veya spring import'larında beklenmedik bir
  uyarı çıkarsa tek satırlık düzeltmelerle giderilir.
- İstenirse bir sonraki adım: sheet içerik bölümlerine kademeli (staggered) giriş animasyonu.
