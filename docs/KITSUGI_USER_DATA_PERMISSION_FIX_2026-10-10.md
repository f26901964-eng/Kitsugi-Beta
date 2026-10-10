# Kitsugi hesap eşitlemesi — `user_data` izin düzeltmesi

## Belirti

Giriş başarılı olduğu hâlde arama geçmişi/kasa eşitlemesi şu hatayla bitiyordu:

```text
permission denied for table user_data
Grant the required privileges to the current role with:
GRANT SELECT ON public.user_data TO authenticated;
```

Bu bir e-posta veya şifre hatası değildir. Supabase kullanıcısı doğrulanıyor; ancak PostgREST'in kullandığı `authenticated` veritabanı rolüne tablo izni verilmediği için istemci `public.user_data` tablosunu okuyamıyor. RLS policy tanımlamak tek başına tablo izni vermez.

## Uygulama

Supabase Dashboard → **SQL Editor** bölümünde, Kitsugi projesinde migration'ları sırayla çalıştırın. İlk migration zaten uygulanmışsa doğrudan ikinciyi çalıştırmanız yeterlidir:

1. `supabase/migrations/20261009_vault_compare_and_swap.sql`
2. `supabase/migrations/20261010_user_data_authenticated_grants.sql`

Migration şu izinleri verir:

```sql
grant usage on schema public to authenticated;
grant select, insert, update, delete on table public.user_data to authenticated;
```

RLS politikaları hâlâ aktiftir; kullanıcı yalnızca kendi `user_id` satırlarına erişebilir. `vault_meta` ve `linked_accounts_vault` için doğrudan yazma ayrıca restrictive policy ile engellenir ve yalnızca güvenli RPC üzerinden yapılır.

Ardından uygulamada çıkış yapıp aynı hesapla tekrar giriş yapın. Yeni APK gerekmez; bu düzeltme Supabase tarafındadır. Ancak uygulama ekranındaki ham PostgREST hata metninin Authorization bearer token'ını göstermemesi için sonraki APK'da hata metni de güvenli biçimde maskelenmiştir.

## Güvenlik notu

Eski uygulama ham Supabase hata metnini ekrana koyabildiği için ekran görüntüsünde `Authorization: Bearer ...` görünebilir. Gerçek bir token ekran görüntüsünde paylaşıldıysa Supabase Dashboard'dan aktif oturumları sonlandırın veya uygulamadan çıkış yapıp yeniden giriş yapın; ekran görüntülerinde token ve URL paylaşmayın.
