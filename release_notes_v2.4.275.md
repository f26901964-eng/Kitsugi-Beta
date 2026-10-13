# Kitsugi-Beta v2.4.275 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe (v2.4.275)

### 🗂️ "Listem → Tümü" Çift Kart ve Sayı Şişkinliği Düzeltmesi (Bangumi Kimlik Köprüsü)
- **Kök Neden Giderildi:** Bangumi satırları yalnızca kendi kaynak kimliğini (500M ofseti) ve Çince/Japonca katalog adını (`name_cn`) taşıdığı için, AniList/MAL kayıtlarıyla ID veya İngilizce başlık üzerinden eşleşemiyordu ve "Tümü" sekmesinde her Bangumi kaydı ayrı bir kart olarak sayılıyordu (1502 − 716 = 786 şişkinliği).
- **Sıfır Ağ İstekli Yerel Kimlik Köprüsü (`MyListIdentityCrosswalk`):** Çapraz eşitlemenin yerelde sakladığı `cross_provider_identity_v1` ve `bangumi_id_mapping` (MAL↔条目) kayıtları okunarak satırların uzayına dokunulmadan kimlik haritası kurulur. Sıfır ağ isteğiyle çalışır.
- **Konsolidasyon Turu & Union-Find (`MyListLibraryGrouping`):** Sıra bağımlılığı giderildi; üye üye doğrulama ve birleştirme turu eklendi. Kimlik anahtarları satır başına bir kez hesaplanarak binlerce satırlık listelerde gruplama performansı optimize edildi.
- **Çift Yönlü Kalıcı Öğrenme (`CrossProviderIdentity`):** Bangumi satırından MAL'e inildiğinde de MAL↔Bangumi eşlemesi yerel önbelleğe iki yönlü yazılır; içe aktarılmış kayıtlar sonraki turlarda ağa çıkmadan birleştirilir.
- **Zıplamasız Arayüz:** Kimlik köprüsü arka planda kurulur; eski liste korunarak zıplama olmadan akıcı bir şekilde birleşik kartlara dönüştürülür.

---

## 🇬🇧 English (v2.4.275)

### 🗂️ "My List → All" Duplicate Tile & Count Inflation Fix (Bangumi Identity Crosswalk)
- **Root Cause Eliminated:** Bangumi rows exclusively carry their own provider ID namespace (500M offset) and catalog name (`name_cn`), preventing them from intersecting with AniList/MAL records by ID or Latin title. Consequently, every Bangumi entry was rendered as an isolated duplicate tile in "All" tab (1502 − 716 = 786 row inflation).
- **Zero-Network Local Crosswalk (`MyListIdentityCrosswalk`):** Reads locally persisted cross-provider identity cache (`cross_provider_identity_v1`) and Bangumi mappings (`bangumi_id_mapping`, MAL↔Subject) to bridge provider IDs without modifying stored rows or issuing network requests.
- **Order-Independent Consolidation (`MyListLibraryGrouping`):** Replaced order-dependent grouping with a union-find consolidation pass that verifies and merges groups. Identity keys are memoized once per row to ensure optimal performance across large libraries.
- **Bidirectional Cache Learning (`CrossProviderIdentity`):** Bidirectionally persists MAL↔Bangumi mappings when resolving from Bangumi rows, permanently resolving future offline grouping.
- **Jitter-Free UI:** Builds identity extras on an IO dispatcher while maintaining previous states, avoiding layout jumps.
