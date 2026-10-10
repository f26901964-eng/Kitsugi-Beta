# PLAN & TASK: TR/EN Dil Paritesi (%100) + Öncelikli Ekranların Kaynak Dönüşümü

Tarih: 2026-10-10
Dal: `arena/0a884b64-kitsugi-beta`
Commit'ler: `9608b57`, `97e0d43`, `a277c29`, `4377192`
İlgili ekranlar: Ayarlar (mobil), Görünüm & Tercihler diyaloğu, Oynatıcı Ayarları (tüm sekmeler), TV Ayarları

---

## 1. Problem tanımı

### 1.1 İngilizce altyapı eksikti (kullanıcı şikâyeti)
- `res/values-en/strings.xml` dosyasında **310 anahtar eksikti** (türler `genre_*`, arama
  etiketleri `tag_*`, `staff_role_*`, `tab_profile`…). Android eksik anahtarda varsayılan
  (Türkçe) değere düştüğü için İngilizce modda arayüzün büyük bölümü Türkçe görünüyordu.
- `res/values-en/arrays.xml` **hiç yoktu** (CloudStream tampon/kod çözme dizileri yalnız Türkçe).
- `scripts/sync_bangumi_tag_strings.py --check` **zaten kırık** çıkıyordu: sözlükte olmayan
  yetim `bangumi_tag_ona` / `bangumi_tag_war` anahtarları ve sync bloğu SONRASINA yazılmış
  5 `notif_bangumi_*` kaynağı script'in "marker'dan dosya sonuna kadar blok" sözleşmesini bozuyordu.

### 1.2 Kod içi hardcoded Türkçe metinler
- Öncelikli ekranlarda (Ayarlar, tercihler diyaloğu, oynatıcı ayarları, TV ayarları) UI
  metinleri `string` literal olarak koddaydı; uygulama dili İngilizce olsa bile Türkçe kalıyordu.
- Bazı dosyalarda kaynaklar mevcuttu ancak kod bağlanmamıştı (örn. `settings_pref_title`).

---

## 2. Yapılan değişiklikler

### 2.1 Kaynak altyapısı (TR/EN %100 parite)
1. `values-en/strings.xml`: 310 eksik anahtar çevirileriyle eklendi; dosya TR ile **birebir
   anahtar sırası** korunarak yeniden üretildi (birleştirme scripti ile, mevcut EN çeviriler korundu).
2. `values-en/arrays.xml`: İngilizce dizi kaynakları oluşturuldu.
3. Ekran dönüşümleri için **~555 yeni TR/EN anahtarı** eklendi (`settings_*`, `player_*`,
   `sub_*`, `pd_*`, `tv_*` önekleri). Son durum: **2.195 anahtar, sıra ve küme eşit, duplikesiz**.
4. Sync scripti hijyeni: yetim `bangumi_tag_ona/war` kaldırıldı, `notif_bangumi_*` kaynakları
   marker ÖNCESİNE taşındı, dosya sonu boşlukları normalize edildi → `--check` artık **0** dönüyor.
5. Doğrulama (her adımda otomatik): anahtar paritesi/sırası, duplike kontrolü, Android kaçış
   kuralları (`&amp;`, `\'`, `\"`), `%n$s / %n$d` placeholder TR-EN eşitliği, EN dosyada Türkçe
   karakter sızıntısı taraması.

### 2.2 Kod dönüşümü (hardcoded → stringResource)
6. `SettingsScreen.kt` (mobil Ayarlar): rota başlıkları, ana menü başlık/açıklamaları,
   bölüm başlıkları, "Tüm liste silinsin mi?" diyaloğu, geri bildirim e-posta gövdesi
   (`feedback_mail_body` %1$s..%7$s şablonu) ve toast metni kaynaklara bağlandı.
7. `KitsugiPreferencesSettingsDialog.kt` (ekran görüntüsündeki diyalog): 58 literal
   (bölüm başlıkları, karanlık tema açıklamaları, çevirici seçenekleri, bildirim sıklıkları…).
8. `PlayerGeneralTab.kt`: ~158 literal (motor/AFR/en-boy/jest/MPV bölümleri, şablonlu
   metinler `stringResource(id, arg)` biçimine çevrildi).
9. `PlayerSubtitleAudioTab.kt`: ~70 literal (altyazı boyutu/renk/opaklık, dil bayrakları,
   rota gecikmeleri).
10. `KitsugiPlayerSettingsDialog.kt`: ~240 literal (sekmeler, alt bölümler, özel buton
    editörü, mpv.conf/input.conf bölümü, hesap satırları `${user.ifBlank { "Bağlı" }}` şablonları dahil).
11. `TvSettingsScreen.kt`: ~150 literal ("Şu anki: X" kalıpları `%1$s` placeholder'lı
    kaynaklara dönüştürüldü; hesap bağla/kes satırları dahil).
12. `LanguageUtils.kt`: `displayName()` artık aktif arayüz diline göre isim döner
    (TR: "Japonca", EN: "Japanese"); `LocaleCache.updateLocale` zaten `Locale.setDefault`
    uyguladığı için kontrol `Locale.getDefault()` ile tutarlı.

### 2.3 Bilinçli korunanlar
- Dil adlarının **yerel yazımları** ("Türkçe", "English", "日本語") her dilde aynı kalır (standart).
- Marka/adlar (AniList, MyAnimeList, TMDB, "MX Player"…) çevrilmez.
- Ölçü birimi etiketleri ("ms", "5s", "1.5x") dile bağlı değildir.

---

## 3. Doğrulama sonuçları

| Kontrol | Sonuç |
|---|---|
| TR/EN anahtar kümesi + sırası | 2.195 / 2.195 eşit |
| Duplike anahtar | yok |
| Android kaçış (`&`, `'`, `"`) | temiz |
| Placeholder (`%n$s/%n$d`) TR-EN | eşit |
| EN dosyada Türkçe karakter | yok |
| `sync_bangumi_tag_strings.py --check` | exit 0 |
| Dönüştürülen dosyalarda kalan TR literal | yalnız yorum satırları + yerel adlar |

Not: Ortamda Java/Android SDK bulunmadığı için derleme doğrulaması yapılamadı; tüm kod
değişiklikleri yalnız `stringResource(...)` / `context.getString(...)` çağrı eklemesi ve
import satırıdır (hepsi @Composable kapsam içinde doğrulandı).

---

## 4. Kalan iş (sonraki oturumlar)

Öncelikli ekranlar tamamlandı. Kalan ~3.900 hardcoded TR literal diğer ekranlarda:
arama filtre sheet'leri (`SourceEngineFilterSheet`, `SourceSpecificFilterChipsRow`),
`AccountSettingsSubPages`, `KitsugiSystemSettingsDialog`, `KitsugiAccountConnectionsDialog`,
detay/profil ekranları ve TV bileşenleri. Aynı yöntemle (mevcut anahtarları yeniden kullan +
yeni TR/EN anahtar ekle + script'li birebir değişim) ekran ekran taşınacak.
