# Kitsugi Beta v2.4.281

### 🌟 Fandom Wiki Keşif, Film Türü & Çoklu Kimlik Doğrulamaları

- **Film Formatı ve Tür İyileştirmesi:** Medya detaylarından gelen `format`, `rawFormat`, alt başlık ve film başlığı sinyalleri (örn. *The Movie*, *Reze Arc*, *劇場版*, *映画*, *Film*) tür belirlemeye dahil edildi. Böylece TMDB/Wikidata aramasında dizi (`P4983`) yerine film (`P4947`) kimlikleri önceliklendirildi.
- **Karakter & Ekip Görünümlerinde Tür Normalizasyonu:** Karakter, seslendirmen ve yapım ekibi görünümlerindeki yapım başlıklarına da aynı sinyal tabanlı tür normalizasyonu uygulandı (`normalizedMediaKind()`).
- **Makale Doğrulaması (Article Proof) ve Akışa Aktarım:** Wiki adayları, yapımın Fandom makalesi ve sağlayıcı dış bağlantılarıyla güçlü doğrulamadan (`FandomMediaIdentityProof.hasStrongArticleProof`) geçiriliyor. Eşleşen makale başlığı doğrudan sonraki yükleme akışına aktarılıyor.
- **Genişletilmiş Kimlik Bağlantıları (10 URL Sınırı):** Birebir kimlik bağlantısı arama limiti 10 URL'ye yükseltildi. Bangumi (`bgm.tv/subject/`) ve Simkl (`simkl.com/`) dahil tüm sağlayıcı kimlikleri liste dışı kalmadan taranabiliyor.
- **Wikidata Arama Dayanıklılığı:** Wikidata üzerindeki tek bir sağlayıcı sorgusu (örn. zaman aşımı veya hata) diğer tüm kimliklerin taranmasını engellemiyor; tüm aramalar başarısız olduğunda sonuç geçici hata olarak korunuyor.
- **MediaWiki Dış Bağlantı Kapsamı:** `list=exturlusage` sorgusunda protokol kısıtlaması esnetilerek hem HTTP hem HTTPS bağlantıları eksiksiz taranıyor.
- **Regresyon & Birim Testleri:** Film türü çözümlemesi, karakter/ekip görünüm normalizasyonu, güçlü makale kanıtı ve tüm katalog sağlayıcı kimlik bağlantıları için kapsamlı testler eklendi.

---

### 🌟 Fandom Wiki Discovery, Movie Format Resolution & Multi-Provider Identity Links

- **Movie Format & Kind Resolution:** Media detail format, rawFormat, subtitle, and movie title signals (e.g. *The Movie*, *Reze Arc*, *劇場版*, *映画*, *Film*) now participate in media kind determination. Movie identifiers in TMDB (`P4947`) and Wikidata are correctly prioritized over TV series (`P4983`).
- **Normalized Media Kinds for Character & Staff Appearances:** Character, staff, and voice actor appearance queries apply the same signal-based normalization (`normalizedMediaKind()`).
- **Article Match Proof & Downstream Passthrough:** Candidate wikis are verified using strong article proof (`FandomMediaIdentityProof.hasStrongArticleProof`) matching provider external links, passing the verified article title into the downstream page loading flow.
- **Expanded Identity Links (10 URLs Limit):** Exact external link lookup boundary increased to 10 URLs, ensuring Bangumi (`bgm.tv/subject/`) and Simkl (`simkl.com/`) identity links are fully evaluated.
- **Resilient Wikidata Lookups:** A failure or timeout on a single provider property lookup does not abort remaining lookups; transient negative errors are cached only if all lookups fail.
- **MediaWiki External Link Protocol Flexibility:** Removed protocol restriction in `list=exturlusage` queries so both HTTP and HTTPS legacy links are matched reliably.
- **Unit & Regression Tests:** Added comprehensive test coverage for theatrical work normalizations, multi-source external identity links, and article proof matching.
