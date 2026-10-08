# Görev — Çapraz Eşitleme: Deneysel Uyarılar, Tekil Seçim Güvenliği ve Rapor Etiketleri

Tarih: 2026-10-08 · Branch: `arena/629d07e5-kitsugi-beta` · Taban commit: `498ae91`

## 1. İstek

Kullanıcı, çapraz eşitleme (Cross-Sync) ekranlarına sorumluluk reddi niteliğinde kısa ve ayrıntılı uyarılar eklenmesini istedi.
Ayrıca 2026-10-08 tarihli eşitleme raporu (`CROSS_SYNC_REPORT_...`, Drive) incelenip eşitleme mekaniğinin tutarlılığı kontrol edildi.

## 2. Yapılanlar

### 2.1 Yasal ve deneysel uyarılar (commit `45242ba`)

Ortak metinler tek kaynakta: `app/src/main/java/com/kitsugi/animelist/ui/components/CrossSyncDisclaimer.kt`
(`CrossSyncDisclaimer.SHORT`, `CrossSyncDisclaimer.FULL`, `CrossSyncDisclaimerText` composable).

| Ekran | Dosya | Uyarı |
|---|---|---|
| Hesap bağlantıları → Cross-Sync satırı | `ui/screens/settings/AccountSettingsSubPages.kt` | "Deneysel ·" etiketi |
| Hesap bağlantıları → Çok Yönlü Eşitleme | `ui/components/KitsugiAccountConnectionsDialog.kt` | Kısa uyarı + "Deneysel ·" |
| Cross-Sync ayar sayfası (bilgi kartı) | `ui/screens/settings/AccountSettingsSubPages.kt` | Ayrıntılı uyarı |
| Cross-Sync ayar sayfası (Hızlı İşlem altı) | `ui/screens/settings/AccountSettingsSubPages.kt` | Kısa uyarı |
| Eşitleme penceresi (alt kısım) | `ui/components/KitsugiCrossSyncDialog.kt` (`SafetyNote`) | Ayrıntılı uyarı |

### 2.2 Tekil seçim güvenliği (bu commit)

Sorun: Birden fazla aday grup varken "tekil ortak kimlik" veya "tam başlık" ile seçilen grup, başlık ve yıl kontrolünden
geçmeden birleştiriliyordu. Tek aday dalındaki kontrol (`suspiciousMatches`) bu dala uygulanmıyordu.
Güncellemeler de grup doğruluğuna bağlı olduğu için yanlış birleşme, başka bir hesaptaki doğru kaydın durum/ilerleme/puanını değiştirebilirdi.

Düzeltme (`ui/app/AuthViewModel.kt`, `clusterEntry` → `else` dalı):
- Seçilen grubun her adayı için: yıl uyumsuzsa **veya** ortak kimlikli aday başlıkça akraba değilse → `identityConflictedCandidates` dolu.
- Dolu ise grup seçilmez; kayıt `identityReviewRequired = true` ile ayrılır. Hiçbir hesaba yazılmaz, sebep günlüğe düşer.
- Yıl kuralı: `CrossSyncIdentityGuard.yearsCompatible` (en fazla 1 yıl fark; bilinmeyen yıl uyumlu sayılır).

Beklenen etki: Yanlış birleşmeler azalır; bazı gerçek eşleşmeler (ör. 2 yıldan fazla fark olan sezon kayıtları) artık atlanır.
Atlanan sayısı bu değişiklikle artabilir; bu bilinçli bir güvenlik tercihidir.

### 2.3 Rapor etiketleri

`crossSyncIdentityDiagnostic()` içinde `malId` alanı artık ad alanına göre etiketlenir (`describeMalIdField`):
- `300_000_001..399_999_999` → "iç Kitsu kimliği, MAL değil: Kitsu #N"
- `100_000_001..299_999_999` → "iç AniList kimliği, MAL değil: AniList #N"
- Diğer → "(MAL)"

Amaç: 2026-10-08 raporundaki `malId=300003901` gibi değerlerin MAL kimliği sanılmasını önlemek.

## 3. Bilinçli olarak yapılmayanlar

- **İlk kullanım onayı ("Kabul ediyorum")**: Kullanıcı onayı gerektirir (UX değişikliği); ayrı görev olarak bekliyor.
- **Kitsu kapsam boşluğu** (aynı başlık+yıl ama ortak kimlik yok → gruba bağlanmıyor): Eşleştirme kurallarını genişletmek
  yanlış eşleşme riskini artırır; ayrı analiz gerektirir.
- **Simkl "kısmi işlem" ayrıntısı** (hangi alanın yazılamadığı): Rapor formatı değişikliği; sonraki görev.
- **Çift doğrulamada bağımsızlık kontrolü** (`holderSources.size >= 2` → Trusted): Üst kaynak ortaklığı tespiti gerektirir; ayrı görev.

## 4. Doğrulama

- `git diff --check`: temiz (commit öncesi kontrol edilecek).
- Gradle/JUnit: Bu ortamda JDK yok; derleme ve testler çalıştırılamadı. **Cihaz veya CI'da derleme ve `CrossSync*Test` testleri çalıştırılmalı.**
- Yasal metinler hukuki danışmanlık değildir; yayın öncesi bir hukukçu tarafından gözden geçirilmelidir.

## 5. Açık riskler / sonraki adımlar

1. Derleme doğrulaması (JDK ile `./gradlew :app:compileDebugKotlin` ve testler).
2. Yıl kuralının atlama oranını etkisi: yeni eşitleme raporunda `Eşleştirme` uyarılarını karşılaştır.
3. Raporu test eden ekibin Kitsu 1 yazma hatasının ayrıntısını (`Hatalar (1)`) doğrulaması.
4. Test edilen sürümün (rapor: 2.4.205-beta) bu değişiklikleri içermediğine dikkat et; yeni sürümle yeniden çalıştır.
