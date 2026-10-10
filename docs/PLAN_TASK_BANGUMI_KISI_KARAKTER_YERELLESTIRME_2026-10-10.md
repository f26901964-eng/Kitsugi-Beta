# PLAN_TASK: Bangumi Kişi / Karakter Detay Sayfaları — Uygulama Dilinde Başlık & Rol Yerelleştirmesi

**Tarih:** 2026-10-10
**Branch:** `arena/ce58ba93-kitsugi-beta`

---

## 1. SORUN RAPORU

Bangumi kaynaklı **kişi (seslendirmen/ekip)** ve **karakter** detay sayfalarında
"Karakterler" ve "Yapımlar" sekmelerindeki içerikler hâlâ **Japonca / Çince** görünüyordu:

- Karakter detayı → "Yapımlar": `魔法少女まどか☆マギカ`, `劇場版 魔法少女まどか☆マギカ …`
- Kişi detayı → "Karakterler": `機動戦士ガンダム00 • Ana Karakter`, `西洋骨董洋菓子店〜アンティーヌ〜 …`
- Kişi detayı → "Yapımlar": `プリンス・オブ・ストライド オルタナティブ`, `Rol: 主題歌演出`, `Rol: 配角`

Beklenen: uygulama dili İngilizce ise İngilizce, Türkçe ise Türkçe/Romaji gösterilmeli.

## 2. KÖK NEDEN ANALİZİ

| Katman | Sorun |
|---|---|
| `KitsugiBangumiCreditsClient.parseCharacterAppearances()` | `/v0/characters/{id}/subjects` yalnızca `name` (Japonca) + `name_cn` (Çince) döndürür; infobox LISTELERDE yoktur → romaji/İngilizce `null` kalır. |
| Karakter detayı | Staff detayının aksine **hiç** başlık zenginleştirmesi yapılmıyordu (`enrichStaffDetailNames` yalnızca kişi sayfasındaydı). |
| `enrichStaffDetailNames()` | Yalnızca `BangumiTitleCache` + v0 subject infobox kullanıyordu; Bangumi infobox'ında çoğu anime/manga için Latin ad YOK → CJK kalıyordu. AniList yedeği yoktu. |
| Rol etiketleri | `主角/配角/客串` ve `主題歌演出/原作/监督` gibi Çince/Japonca roller ya sabit Türkçe yazılıyor ya da ham geçiyordu → İngilizce arayüzde Türkçe/CJK sızıyordu. |

## 3. ÇÖZÜM

### 3.1 Ortak Latin başlık çözümleyicisi — `BangumiSubjectTitleResolver`
Yeni `data/remote/BangumiSubjectTitleResolver.kt` üç kademeli çalışır:
1. `BangumiTitleCache` (kalıcı) — daha önce çözülmüşse ağ isteği yok.
2. Bangumi v0 subject infobox (`英文名`/`罗马字`/`别名`) — sınırlı, önbellekli.
3. **AniList aliased toplu arama** — tek GraphQL isteğinde birden çok
   `Page(media(search:))`; adayın `title.native`/`synonyms` alanlarından biri Bangumi
   özgün adıyla **birebir (normalize)** eşleşirse romaji + İngilizce alınır.
   Yanlış eşleşme yerine "eşleşme yok" tercih edilir (uydurma başlık yok).

Tüm sonuçlar `BangumiTitleCache`'e kalıcı yazılır; çözülemeyenler olumsuz önbelleğe
alınır. `cacheOnly` modu ilk çizimi geciktirmez; ağ işini ViewModel arka plan geçi yapar.

### 3.2 Zenginleştirme hatları
- `KitsugiBangumiDetailClient.enrichCharacterDetailNames()` **yeni** — karakter detayı
  "Yapımlar" listesine romaji/İngilizce doldurur.
- `enrichStaffDetailNames()` artık ortak çözümleyiciyi kullanır.
- Senkron istemci geçi `cacheOnly=true` (hızlı), ViewModel arka plan geçi
  `background=true` (uzun filmografileri tamamlar, ekranı günceller).

### 3.3 Arayüz dili duyarlı rol/etiket sözlüğü — `BangumiRoleDictionary`
Yeni `utils/BangumiRoleDictionary.kt` (TR + EN) ve `KitsugiTranslations`'a
`toLocalizedCharacterRole()`, `toLocalizedStaffRole()`, `toLocalizedMediaTypeString()`
eklendi. Veri katmanındaki sabit Türkçe (`Ana Karakter`, `Ekip Üyesi`, `Erkek` …) artık
arayüz diline göre üretiliyor; İngilizce arayüzde İngilizce gösteriliyor.

## 4. DOĞRULAMA

- `BangumiSubjectTitleResolverTest` — AniList yanıtı birebir eşleşme / synonym / yanlış
  başlık reddi / null yanıt / alias hizalaması.
- `KitsugiTranslationsTest` — TR ve EN locale için rol + medya türü yerelleştirmesi.
- Canlı Bangumi/AniList çağrısı bu sandbox'tan yapılamadı; cihazda doğrulanmalı.
