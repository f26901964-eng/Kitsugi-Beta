# Kitsugi-Beta — Sürüm Notları / Release Notes

---

## 🇹🇷 Türkçe (v2.4.147)

### 🧩 Cloudstream Eklentileri & Anizium Kalıcı Çözümü

- **DEX Anti-Tamper & İmza Doğrulama Koruması Aşımı:** Anizium, Animeler, DiziPal, DiziBox, RecTV, FilmEkseni gibi Cloudstream Türk eklentilerinin bytecode (DEX) seviyesinde barındırdığı APK imza kontrolü (`isAllowedVersion`) kalıcı olarak bypass edildi. Eklentilerin Kitsugi imzasını geçersiz sayıp aramaları ve video bağlantılarını boş döndürmesi (`emptyList`) engellendi.
- **Canlı Domain Ön Yüklemesi (DomainListesi):** Eklentilerin 404 veren harici domain güncelleme adresine takılıp `mainUrl` değerini boş bırakması engellendi. Uygulama başlangıcında 67+ Türk eklentisinin en güncel canlı adresleri (Anizium: `api.anizium.co`, DiziPal: `dizipal3008.com` vb.) SharedPreferences'a önceden yazılarak 0ms gecikmeyle hazır hale getirildi.
- **Dinamik Domain Kurtarma (`ensurePluginReady`):** Arama veya video çekme öncesinde eklentilerin adresleri kontrol edilir; boş veya ölü mirror domainler otomatik olarak güncel canlı API adreslerine çekilir.
- **Büyük/Küçük Harf Bağımsız Eklenti Eşleme:** Eklenti kimliği (`anizium` / `Anizium`) ve API sağlayıcı adları arasındaki harf duyarlılığı kaldırılarak tüm kurulu eklentilerin küresel aramada eksiksiz listelenmesi sağlandı.

---

## 🇬🇧 English (v2.4.147)

### 🧩 Cloudstream Extensions & Anizium Permanent Fix

- **DEX Anti-Tamper & Signature Verification Bypass:** Resolved the runtime lockout mechanism in Cloudstream Turkish extensions (Anizium, Animeler, DiziPal, DiziBox, RecTV, FilmEkseni, etc.) where internal bytecode signature checks reset `isAllowedVersion` to `false`, causing all searches and video stream extractions to return empty results.
- **Pre-Seeded Live Provider Domains:** Fixed extensions getting stranded with blank `mainUrl` values due to dead 404 remote domain lists. Over 67+ verified Turkish provider domains (such as `api.anizium.co`, `dizipal3008.com`) are now pre-seeded directly into SharedPreferences upon app startup.
- **Dynamic Domain Recovery (`ensurePluginReady`):** Before executing search, details loading, or stream extraction, providers are verified and automatically repaired if their `mainUrl` is blank or pointing to an obsolete mirror.
- **Case-Insensitive Provider Matching:** Addon discovery and global search matching now operate case-insensitively, ensuring full provider responsiveness regardless of internal ID casing.
