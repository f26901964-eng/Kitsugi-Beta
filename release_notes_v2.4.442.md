# Kitsugi Beta v2.4.442 — Görünür değişiklikler

Bu sürüm, önceki denemede "hiçbir değişiklik göremedim" dediğiniz üç konuyu
**kurulu APK'nın geldiği kod hattı** (v2.4.441 / `bb47e8fd`) üzerine uygular.

## 1) Dil varsayılanları artık gerçekten İngilizce / İngilizce / Türkçe

Ayarlar → Görünüm → **Başlık Dilleri** bölümündeki üç satır:

| Ayar | Değer |
|---|---|
| Anime & Manga Başlık Dili | **İngilizce** |
| Ekip & Karakter Adı Dili | **İngilizce** |
| Dizi & Film Başlık Dili (TMDB) | **Türkçe** |

- v2.4.441'de `ROMAJI` değeri **yalnızca okunurken** İngilizceye çevriliyordu;
  diske hâlâ "ROMAJI" yazıyordu. Artık `language_defaults_v342_applied` bayrağıyla
  **tek seferlik bir göç** çalışıyor ve değerler DataStore'a kalıcı olarak yazılıyor
  (başlık → ENGLISH, personel → ENGLISH, TMDB → tr).
- Göç yalnızca bir kez çalışır: sonradan yaptığınız seçimler asla ezilmez.
- Romaji / Native / Japanese-Staff seçenekleri listede duruyor — sadece varsayılan
  artık İngilizce.

## 2) Manga eklentilerinde eklenti başına yenileme + otomatik güncelleme

Video (Cloudstream) eklentilerindeki parite:

- **Her manga eklentisi satırında** artık iki yeni kontrol var:
  - ⟳ **Yenile / Yeniden kur** — tek dokunuşla APK'yı yeniden indirip kurar
    (bozuk kurulum, eski sürüm, "hiç açılmıyor" durumları için).
  - **Oto** çipi — o eklentiye özel otomatik güncelleme anahtarı (açık/kapalı).
- **Repo kartlarında** yenile butonu artık **her zaman görünür**; yüklenirken
  spinner'a dönüşür (önceden yalnızca liste hiç yüklenmemişse görünüyordu).
- **Kurulu Kaynaklar** sekmesindeki otomatik güncelleme kartına **global ana
  anahtar** eklendi: "Otomatik güncelleme (tüm eklentiler) — AÇIK / KAPALI".
  Kapatırsanız hiçbir manga eklentisi kendiliğinden güncellenmez.
- Otomatik güncelleme motoru (`MangaExtensionAutoUpdater`) bu tercihlere **uyar**:
  - Global anahtar kapalıysa hiç ağ isteği yapılmaz.
  - Eklenti bazlı anahtar kapalıysa o eklenti atlanır ve sonuç metninde
    "otomatik güncelleme kapalı (N eklenti atlandı)" yazar.
- Zorla kontrol ("Şimdi Güncelle") her zaman tercihleri yok sayar — manuel
  kontrolü engelleyen hiçbir kilit yok.

## 3) Kurulum notu (imza)

Bu APK CI'da **Android debug anahtarıyla** imzalanır. Telefonunuzdaki sürüm
farklı bir anahtarla imzalandıysa Android "uygulama yüklenemedi / paket çakışması"
uyarısı verir. O durumda eski Kitsugi'yi kaldırıp bu APK'yı kurun; dil ayarları
zaten bu sürümde varsayılan olarak İngilizce/İngilizce/Türkçe gelecektir.

## Doğrulama

- `./gradlew :app:testFossDebugUnitTest` + `assembleFossDebug` +
  `assembleFossRelease` (R8) — GitHub Actions "Android Derleme Doğrulama" işi.
- Yeni kaynaklar: `values/strings.xml` ve `values-en/strings.xml`
  (`manga_extension_refresh`, `manga_extension_refresh_item`,
  `manga_auto_update_short`, `manga_auto_update_global_*`,
  `manga_auto_update_all_disabled`).
