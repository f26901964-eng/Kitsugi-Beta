# PLAN_TASK: Bangumi Yayıncı Ağlar Tıklanabilirlik + Karakter/Yapım Adları Yerelleştirme

**Tarih:** 2026-10-10
**Branch:** `arena/d601d8b1-kitsugi-beta`
**Commit:** `6b63f1f`

---

## 1. SORUN RAPORU

### Sorun 1: Yayıncı Ağlar (Networks) çipleri tıklanamıyor
Kullanıcı Bangumi anime detay sayfasında (örn. Clannad: After Story):
- **Yapımcılar** (ポニーキャニオン, ムービック vb.) çiplerine tıklayınca detay sayfası açılmıyor
- **Yayıncı Ağlar** (TBS, MBS, RKB, CBC, BS-i, BS-TBS) çiplerine tıklayınca hiçbir şey olmuyor

### Sorun 2: Kişi detay sayfasında karakter/yapım adları yerelleştirilmiyor
Bangumi seslendirmen/kişi detay sayfasında (örn. Nakamura Yūichi):
- **Karakterler** sekmesinde karakter adları CJK (Japonca/Çince) gösteriliyor: 岡崎朋也, グラハム・エーカー, 天狐空幻
- **Yapımlar** sekmesinde yapım başlıkları CJK gösteriliyor: アルカナ・ファミリア, 懺・さよなら絶望先生, 東京エンカウント式

Kullanıcı beklentisi: Uygulama dili (Türkçe/İngilizce/Romaji) seçildiğinde bu adlar o dile uygun gösterilmeli. İngilizce seçiliyse İngilizce, yoksa en azından romaji (Latin) görünmeli.

---

## 2. KÖK NEDEN ANALİZİ

### Sorun 1: Networks tıklanamıyor
| Katman | Sorun |
|--------|-------|
| `KitsugiBangumiDetailClient.buildNativeDetail()` | Networks `id = 0` ile oluşturuluyor (infobox'tan sadece isim var, kimlik yok) |
| `KitsugiBangumiDetailClient.withStudioPersonIds()` | Sadece `studios + producers` zenginleştiriliyor, `networks` dahil değil |
| `KitsugiDetailComponents.KitsugiStudiosCard()` | Networks çipleri için `onClick = null` hard-coded |

### Sorun 2: Karakter/Yapım adları CJK kalıyor
| Katman | Sorun |
|--------|-------|
| `KitsugiBangumiCreditsClient.fetchPersonDetail()` | v0 API (`/v0/persons/{id}/characters`, `/v0/persons/{id}/subjects`) infobox döndürmüyor |
| `BangumiNameLocalizer.entity()` | Sadece `name` + `name_cn` ile çalışıyor, infobox olmadığı için romaji/İngilizce çıkaramıyor |
| `KitsugiStaffClient.fetchStaffDetail()` | Bangumi kaynağı için任何 bir enrichment (zenginleştirme) yapılmıyor |
| Sonuç | `characterRomanizedName`, `characterEnglishName`, `mediaTitleRomaji`, `mediaTitleEnglish` alanları null kalıyor → CJK gösteriliyor |

---

## 3. ÇÖZÜM TASARIMI

### Çözüm 1: Networks Tıklanabilir Hale Getirme

**Adım 1:** `withStudioPersonIds()` fonksiyonunu networks'ü de içerecek şekilde güncelle
```kotlin
// ÖNCE:
val candidates = (detail.studios + detail.producers)
// SONRA:
val candidates = (detail.studios + detail.producers + detail.networks)
// ve:
return detail.copy(
    studios = detail.studios.map(::apply),
    producers = detail.producers.map(::apply),
    networks = detail.networks.map(::apply)  // YENİ
)
```

**Adım 2:** `KitsugiStudiosCard`'a `onNetworkClick` parametresi ekle
```kotlin
fun KitsugiStudiosCard(
    ...,
    networks: List<KitsugiStudio> = emptyList(),
    onNetworkClick: (KitsugiStudio) -> Unit = onProducerClick  // varsayılan
)
```

**Adım 3:** Networks çiplerini tıklanabilir yap
```kotlin
onClick = if (network.id > 0 || !network.source.equals("bangumi", ignoreCase = true)) {
    { onNetworkClick(network) }
} else null
```

### Çözüm 2: Karakter/Yapım Adları Latin Enrichment

Yeni `enrichStaffDetailNames()` fonksiyonu:

**Adım 1:** Karakter adları için `/v0/characters/{id}` detaylarını çek
- `fetchEntityLocalizedNames()` ile batch istek (max 60, 6.5s timeout)
- infobox'tan romaji/İngilizce çıkarılıyor
- `characterRomanizedName`, `characterEnglishName` dolduruluyor

**Adım 2:** Yapım başlıkları için iki aşamalı yaklaşım
- Önce `BangumiTitleCache` önbelleğine bak (ağ isteği yok)
- Yoksa `/v0/subjects/{id}` detayını çek (infobox'tan romaji/İngilizce)
- `mediaTitle`, `mediaTitleRomaji`, `mediaTitleEnglish`, `titleRomaji`, `titleEnglish` dolduruluyor
- `BangumiTitleCache.put()` ile önbelleğe yaz (sonraki ziyaretlerde ağ isteği gerekmesin)

**Adım 3:** `KitsugiStaffClient.fetchStaffDetail()` Bangumi kaynağı için bu fonksiyonu çağırıyor
```kotlin
"bangumi", "bgm" -> {
    val raw = KitsugiBangumiCreditsClient.fetchPersonDetail(staffId, name)
        ?: KitsugiBangumiDetailClient.fetchStaffDetail(staffId)
    raw?.let { KitsugiBangumiDetailClient.enrichStaffDetailNames(it) }
}
```

**UI katmanında gösterim:**
- `displayPersonName()` ve `getDisplayTitle()`/`getDisplayMediaTitle()` fonksiyonları zaten zenginleştirilmiş alanları kullanıyor
- İngilizce seçiliyse → İngilizce, yoksa romaji, o da yoksa orijinal (CJK) gösterilir

---

## 4. DEĞİŞEN DOSYALAR

| Dosya | Değişiklik | Satır |
|-------|-----------|-------|
| `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiBangumiDetailClient.kt` | `enrichStaffDetailNames()` eklendi + `withStudioPersonIds()` networks desteği | +108 |
| `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiStaffClient.kt` | Bangumi staff detail enrichment çağrısı | +4 |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailComponents.kt` | `onNetworkClick` parametresi + tıklanabilir ağ çipleri | +8 |

**Toplam:** 3 dosya, +124 satır, -5 satır

---

## 5. TEST SENARYOLARI

### Senaryo 1: Networks Tıklanabilirlik
1. Bangumi'den bir anime aç (örn. Clannad: After Story)
2. "Yayıncı Ağlar" bölümündeki TBS çipine tıkla
3. **Beklenen:** TBS kurum detay sayfası açılır (Bangumi kişi detay şablonu)

### Senaryo 2: Karakter Adları Yerelleştirme
1. Bangumi'den bir seslendirmen aç (örn. Nakamura Yūichi)
2. "Karakterler" sekmesine geç
3. **Beklenen:** Karakter adları romaji/İngilizce gösterilir (岡崎朋也 → Okazaki Tomoya)

### Senaryo 3: Yapım Başlıkları Yerelleştirme
1. Aynı kişi detay sayfasında "Yapımlar" sekmesine geç
2. **Beklenen:** Yapım başlıkları romaji/İngilizce gösterilir (東京エンカウント式 → Tokyo Encount)

### Senaryo 4: Fallback (Ağ Hatası)
1. Ağ bağlantısını kes veya API rate limit'e takıl
2. Kişi detay sayfasını aç
3. **Beklenen:** Orijinal CJK adlar gösterilir (ekran boş kalmaz)

---

## 6. RİSKLER VE SINIRLAMALAR

| Risk | Etki | Azaltma |
|------|------|---------|
| Ek ağ istekleri (N+1) | Yavaş yükleme | Max 60 istek, 6.5s batch timeout, 3.5s tek istek timeout, Semaphore(8) |
| Bangumi API rate limit | Başarısız istekler | Önbellek kullanımı, zaman aşımı ile erken çıkış |
| infobox'ta romaji yok | Hâlâ CJK gösterilir | Kabul edilebilir fallback, kullanıcı deneyimi bozulmaz |
| Cache kirliliği | Eski veriler | BoundedCache (max 1500), TTL korumalı |

---

## 7. SONRAKI ADIMLAR (İyileştirme Fırsatları)

1. **Karakter önbelleği:** `BangumiTitleCache` benzeri bir karakter önbelleği oluşturulabilir
2. **Otomatik önbellek doldurma:** Popüler karakter/yapımlar için arka planda önbellek doldurma
3. **Çok kaynak desteği:** Jikan/MAL API'den de romaji çekme (cross-source enrichment)
4. **Kullanıcı geri bildirimi:** "Bu ad yanlış" raporlama özelliği

---

## 8. ONAY

- [x] Kod değişiklikleri tamamlandı
- [x] Commit yapıldı (`6b63f1f`)
- [x] Branch push edildi (`arena/d601d8b1-kitsugi-beta`)
- [ ] Manuel test (cihazda test edilmeli)
- [ ] PR açılacak
