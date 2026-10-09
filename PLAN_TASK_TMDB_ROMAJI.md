# PLAN / TASK: TMDB kişi adlarında romaji (Latin) gösterimi

Tarih: 2026-10-09
Branch: `arena/978d0f0c-kitsugi-beta`

## Sorun
- TMDB kişi adları (`name`) çoğunlukla Japonca (CJK) gelir. Örnek: `元永慶太郎`.
- Ekip sekmesi, oyuncu listesi ve kişi detay başlığı bu Japonca adı gösteriyordu; romaji/İngilizce karşılık kullanılmıyordu.
- "Diğer İsimler" bölümünde romaji adlar (`also_known_as`) zaten görünüyordu, ama ana ad bunlardan türetilmiyordu.
- Bilgiler kartında iş tanımı İngilizce kalıyordu (`Directing`).

## Yapılan değişiklikler
1. `TmdbCreditsClient.kt`
   - `pickLatinAlias()`: `also_known_as` listesinden Latin ad seçer (boşluk içerenler öncelikli).
   - `fetchPersonAliases()`: TMDB person endpoint'inden `also_known_as` alır, oturum boyunca `personAliasCache` ile önbelleğe alır.
   - `resolvePersonRomaji()`: CJK adı olan kişiler için paralel ve en fazla 12 kişiyle sınırlı çözüm yapar.
   - `fetchCredits()`: Oyuncu (`VoiceActor`) ve ekip (`KitsugiStaff`) kayıtlarına `romanizedName` ekler.
   - `fetchPersonStaffDetail()` / `fetchPersonCharacterDetail()`: Detay modellerine `romanizedName` ekler.
2. `res/values/strings.xml`
   - `staff_role_directing` = "Yönetmenlik" eklendi.

## Kabul kriterleri
- [ ] Ekip sekmesinde CJK adlı kişiler romaji olarak görünür (ROMAJI ayarında).
- [ ] Kişi detay başlığı romaji gösterir; NATIVE ayarında Japonca kalır.
- [ ] `also_known_as` içinde Latin ad yoksa ad Japonca olarak kalır (fallback).
- [ ] Credits çağrısı başına en fazla 12 ek TMDB person isteği yapılır; önbellek ikinci açılışta istek üretmez.
- [ ] "Directing" Bilgiler kartında "Yönetmenlik" olarak görünür.

## Bilinen sınırlamalar
- TMDB'de Latin alias yoksa AniList gibi ek bir eşleme yapılmaz.
- Detay sayfasındaki "Memleket" (Japonca yer adı) çevrilmedi.
- Derleme ve cihaz testi bu ortamda yapılamadı; Android Studio'da derleme gerekli.
