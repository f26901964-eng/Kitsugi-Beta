# Çözüm Raporu — Aktivite Sil Butonu, Biyografi Otomatik Çevirisi, Takip Metni, Rozet Köşeleri

**Tarih:** 2026-10-21 · **Sürüm:** v2.4.337 · **Durum:** Kod uygulandı; derleme/cihaz testi yapılamadı

## 1. Aktivite kartındaki açılır panel (ekran görüntüsü 1)

### Kök neden
`ProfileActivityComponents.kt` → `ActivityCard`, tek bir işlem ("Sil") için `MoreVert` ikonu + `KitsugiDropdownMenu` + `KitsugiMenuItem` üçlüsünü kullanıyordu. Menü, kartın içinde geniş bir yüzey olarak açıldığı için hem gereksiz bir adım hem de görsel olarak kartın üstünü kapatan bir panel oluşturuyordu.

### Çözüm
- Üç nokta ve menü tamamen kaldırıldı.
- Yerine kartın sağ üstünde 30dp'lik `IconButton` + `DeleteOutline` (18dp, kırmızı) eklendi; tıklama doğrudan `onDeleteClick(activityId)` çağırıyor.
- Silme onayı zaten çağrı yerinde (`KitsugiProfileScreen` → `AlertDialog`) olduğu için kullanıcı koruması kaybolmadı.
- `showMenu` durumu ve `KitsugiDropdownMenu` / `KitsugiMenuItem` içe aktarımları temizlendi.

## 2. Biyografi/açıklama otomatik çevirisi (ekran görüntüsü 2)

### Kök neden / eksik
Profil "Hakkında" kartlarındaki çeviri ikonu yalnızca metni **harici** çeviri uygulamasına gönderiyordu. Ayarlardaki "Otomatik çeviri" açık olsa bile biyografi uygulama içinde çevrilmiyordu; wiki/detay sayfalarındaki otomatik çeviri altyapısı profil ekranlarına hiç bağlanmamıştı.

Ayrıca biyografiler HTML geldiği için düz `translate()` çağrısı etiketleri bozardı.

### Çözüm
- **`utils/KitsugiHtmlTranslation.kt`:** HTML'i etiket/metin olarak ayırır; yalnızca metin düğümlerini çevirir, `rebuild` ile aynı sırayla geri yerleştirir.
  - Atlananlar: `<script>`, `<style>`, `<code>`, `<pre>`, yalnızca URL, yalnızca HTML entity, 1 karakterlik parçalar.
  - Kısmi başarı: çevrilemeyen düğüm orijinal kalır. Hiçbir düğüm çevrilemezse `null` → çeviri uygulanmaz (ekranda yarım/bozuk sayfa yok).
- **`ui/components/KitsugiAutoTranslation.kt`:** Wiki mantığının ortak hâli.
  - Sadece `autoTranslateEnabled` açıkken çalışır (`TranslationManager`).
  - Çeviri bellekte tutulur, ekran yeniden çizildiğinde tekrar istek atılmaz.
  - `isLoading`, `hasTranslation`, `showingOriginal`, `toggleOriginal` durumları UI'a verilir.
- **Bağlanan yüzeyler:** AniList kendi profili, AniList kullanıcı profili, Kitsu, Shikimori, Simkl, MyAnimeList (düz metin + genişletilmiş HTML).
- **UI:** Çeviri ikonu çeviri sürerken galeri butonuyla aynı nabız + halka animasyonunu gösterir (`KitsugiTranslateIconButton(isLoading = …)`); çeviri hazır olduğunda yanında compact `KitsugiOriginalToggleChip` ("Orijinal" / "Çeviri") çipi belirir. İkonun harici çeviri uygulaması davranışı wiki ile aynı bırakıldı.
- **Kritik yan onarım — `KitsugiHtmlWebView.kt`:** `AndroidView` yalnızca `factory` içinde `loadDataWithBaseURL` çağırıyordu; `update` bloğu olmadığı için HTML değiştiğinde WebView eski içeriği göstermeye devam ediyordu. Bu yüzden otomatik çeviri, WebView içeren biyografilerde ekrana hiç yansımazdı. `update` + son yüklenen HTML takibi eklendi (durum yazımı composition dışında tutulması için `arrayOfNulls` tutucu kullanıldı).

## 3. "Takip Ediliyorsun" metni (ekran görüntüleri 3 ve 4)

### Kök neden
`profile_following_button` metni "Takip Ediliyorsun" idi. Bu edilgen çatı, "sen takip ediliyorsun" (biri seni takip ediyor) anlamına gelir; buton ise kullanıcının karşı tarafı takip ettiğini gösterir.

### Çözüm
- Türkçe metin **"Takiptesin"** olarak düzeltildi.
- İngilizce "Following" doğru olduğu için değişmedi.
- Profil başlığındaki `profile_follows_you` = "Sizi takip ediyor" (karşı taraf sizi takip ediyor) bilgisi doğru olduğu için korundu.

## 4. Rozetlerin köşelere yapıştırılması (ekran görüntüsü 5)

### Kök neden
- Durum ve puan rozetleri yuvarlak "pill" biçiminde ve poster kenarından 5dp içeride duruyordu; köşeye ait değil, görselin üzerinde yüzen etiketler gibi görünüyordu.
- Rozetlerin yazı boyutu/padding'i büyüktü ve genişlik sınırı yoktu; dar posterlerde görselin ciddi bir bölümünü kaplıyor, uzun etiketler taşabiliyordu.
- Satır düzenlerinde 22dp kaynak logosu 50dp posterin neredeyse yarısını kaplıyordu.

### Çözüm
- **`PosterCornerTag`** ortak bileşeni: 9sp yazı, dar padding, `widthIn(max = 108.dp)`, tek satır + ellipsis, yarı saydam koyu zemin.
- **Köşe biçimleri:** `TopEndCornerShape` (topEnd = 0) ve `BottomEndCornerShape` (bottomEnd = 0) ile rozetin dış köşesi düz; rozet postere tam yapışır, posterin yuvarlak köşesi rozeti doğal biçimde keser. İç köşeler 6dp yumuşak kalır.
- **Yerleşim:** izgara, büyük (yatay/dikey) görünümlerde durum = sağ üst, puan = sağ alt (yapışık), favori yıldızı = sol üst (yapışık), kaynak logosu = sol alt (değişmedi).
- **Küçük posterler:** kaynak rozeti 50–76dp posterlerde 16–18dp, 22dp yalnızca geniş posterlerde.
- Artık kullanılmayan `PosterBadgeInset` sabiti kaldırıldı.

## 5. Değişen dosyalar

| Dosya | Değişiklik |
|---|---|
| `ui/screens/profile/ProfileActivityComponents.kt` | Açılır menü → doğrudan sil düğmesi |
| `ui/components/KitsugiAutoTranslation.kt` | **Yeni** — ortak otomatik çeviri durumu |
| `utils/KitsugiHtmlTranslation.kt` | **Yeni** — HTML metin düğümü çevirisi |
| `ui/components/KitsugiHtmlWebView.kt` | `update` ile canlı HTML yenileme |
| `ui/screens/profile/tabs/AniListInfoTab.kt` | Otomatik çeviri + orijinal geçişi |
| `ui/screens/profile/tabs/UserProfileInfoTab.kt` | Aynı |
| `ui/screens/profile/KitsugiUserProfileScreen.kt` | Ayarları geçirme |
| `ui/screens/profile/AniListProfileContent.kt` | Ayarları geçirme |
| `ui/screens/profile/KitsuProfileContent.kt` | Biyografi otomatik çevirisi |
| `ui/screens/profile/ShikimoriProfileContent.kt` | Biyografi otomatik çevirisi |
| `ui/screens/profile/SimklProfileContent.kt` | Biyografi otomatik çevirisi |
| `ui/screens/profile/MalProfileExtras.kt` + `MalProfileContent.kt` | "Hakkında" otomatik çevirisi |
| `ui/components/KitsugiMediaEntryCard.kt` | Köşeye yapışık kompakt rozetler |
| `res/values/strings.xml` | "Takiptesin" |
| `app/build.gradle.kts` | 2.4.337 |
| `app/src/test/.../KitsugiHtmlTranslationTest.kt` | **Yeni** — 9 birim testi |

## 6. Doğrulama durumu
- Ortamda JDK/Android SDK bulunmadığı için `./gradlew test` ve `assembleDebug` **çalıştırılamadı**; testler yazıldı ancak geçtiği doğrulanmadı.
- Kotlin dosyalarında süslü parantez/parantez dengesi betikle kontrol edildi (hepsi dengeli).
- Cihazda doğrulanması gerekenler: açık/koyu tema ve farklı vurgu renkleriyle rozet görünümü, TV odağında rozet kesimi, uzun durum etiketlerinde taşma, WebView çeviri geçişinde yükseklik davranışı, otomatik çeviri kapalıyken hiç istek atılmadığı.
