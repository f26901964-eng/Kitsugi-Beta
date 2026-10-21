# Bangumi (bgm.tv) — Araştırma Notları (2026-10-21)

## 1. Altyapı: Cloudflare DDoS koruması = hızlı istekte 503
- Resmi repo issue'su bangumi/api #50: "api.bgm.tv Cloudflare arkasında; uygulamalar fazla hızlı istek atınca **DDoS korumasına çarpıp 503** alıyor; IP+Host-header çözümü öneriliyor":
  https://github.com/bangumi/api/issues/50
- Yerel uygulamalar bunu "kendi CDN intermediate" ile aşmaya çalışıyor (Zendot üçüncü-parti istemci yazısı): https://zendot.org/en/posts/czy0729-bangumi
- Veritabanı bağlantı tıkanmaları API timeout'larına yol açmışlığı var — bangumi/server #642: https://github.com/bangumi/server/issues/642

## 2. Rate limit: resmi sayı YOK, topluluk ölçümüyle yürüyor
- 2022'de resmi cevap: "yeni API'de henüz limit yok" (bangumi.tv development grubu): https://bangumi.tv/group/topic/371211
- Bugünkü topluluk gerçeği: **sık senkronda 429** (shirone docs "常见问题: 频繁同步被 Bangumi 限流"), pratik koruma: sayfalar arası **≥200ms gecikme**, bazı botlarda **1.1 sn/istek** throttle + "günlük ~1000 çağrı" korkusu (astrbot plugin notu: https://github.com/Amatsutsumi/astrbot_plugin_bangumi).
- **Zorunlu User-Agent** (resmi olmayan ama herkes yazıyor): UA'sız istekler reddediliyor-iddiası yaygın; shirone docs "所有请求必须携带合规的 User-Agent".
- AniList'e Bangumi'den büyük senkron: 90/dk (fiilen 30) AniList limitine takılır — ykozxy/bangumi-sync "已知限制": https://github.com/ykozxy/bangumi-sync

## 3. Domain / mirror labirenti
- Üç eşdeğer resmi domain (aynı veritabanı): **bgm.tv, bangumi.tv, chii.in** — tonkatsu_box #457 notu: https://github.com/hacan359/tonkatsu_box/issues/457
- API hostları: `api.bgm.tv` (public v0) + `next.bgm.tv/p1` (web'in kullandığı zengin private API — resmi-gizli, uyumluluk garantisi YOK; dev grubu notu: https://bangumi.tv/group/topic/413138).
- **Üçüncü-parti mirror hostlar dolaşımda**: `api.bgmapi.com` vb. → Kazumi #2679: Android uygulaması yanlış mirror + yerine konmamış kullanıcı-id (placeholder `-`) ile **kalıcı 404** üretti, "sunucu hatası" mesajıyla kullanıcıya yanlış yansıtıldı:
  https://github.com/Predidit/Kazumi/issues/2679
- Görseller `lain.bgm.tv` hotlink korumalı (Kitsugi notu: `BangumiApiClient` L142).

## 4. API kapsamı sınırları (topluluk yakınmaları)
- Eski development grubu: "API henüz açılmadığı için koleksiyon **ekleme/silme yapılamıyor**" (bangumi.app yazısı; v0 ile kısmen aşıldı — v0'da collection PUT/DELETE var, ama sınırlı geri-bildirim): https://bgm.tv/group/bangumi_app
- Üçüncü parti istemcilerde "delete collection yok" özellikleri hâlâ listeleniyor (Microsoft Store Bangumi istemcisi: "不支持删除收藏").
- Açık veri API (oanor pazar yeri) bile "2 req/sn, aylık 800 çağrı" satıyor — yani Bangumi trafiğinin paralı intermediar'ları var: https://www.oanor.com/api/bangumi-api
- Resmi doküman: https://bangumi.github.io/api/ (v0) ; https://github.com/bangumi/api

## 5. Kitsugi'deki karşılığı (kod denetimi 2026-10-21)
- `BangumiApiClient.API_BASE = https://api.bgm.tv` ✓ (resmi host, mirror yok — Kazumi sınıfı risk yok) + `NEXT_BASE = next.bgm.tv` (private API bilinçli).
- **401/403/404/429 ayrımı** `BangumiSyncManager` L221'de ("her koşulda Bilinmeyen hata yazıyordu" düzeltmesi) ✓.
- **Açık madde (yeni bulgu):** `BangumiApiClient` kendi `metadataClient.newCall`'larını kullanıyor → `KitsugiApiBase` host-bütçesi sisteminden **dışarıda** → `api.bgm.tv` için merkezi throttle yok. Cloudflare-503 sınıfına karşı savunmasız → bu araştırmadan çıkan somut sertleştirme: host-bütçesine `api.bgm.tv` eklendi (bkz. tur notu).
- DELETE koleksiyon yolunda silme = DROPPED + rate0 (resmi v0 semantiğine uygun yorum; wiki'ye göre tam-silme web'e kalıyor) → **açık madde olarak listelenir** (Bangumi'de "kayıt tamamen yok" yerine "DROPPED" görünebilir).
