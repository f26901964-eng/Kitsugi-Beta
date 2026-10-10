-- Kitsugi hesap senkronizasyonu şeması
-- Supabase Dashboard → SQL Editor'da bir kez çalıştırılır.
-- Ardından migrations/20261009_vault_compare_and_swap.sql uygulanmalıdır;
-- güncel istemci kasa yazmalarında bu migration içindeki RPC'yi kullanır.
--
-- Tasarım:
--  * Her kullanıcının verisi, anahtar (key) bazında JSON olarak tutulur:
--      search_history, installed_plugins, settings, linked_accounts, ...
--  * Yeni anahtar eklemek için şema değişikliği gerekmez.
--  * Erişim Row Level Security ile yalnızca satır sahibine açıktır.
--  * Düz metin token/şifre yazılmaz. vault_meta: şifreyle sarılmış kasa anahtarı;
--    linked_accounts_vault: AES-GCM şifreli token ve taşınabilir ayar yedeği.
--    search_history şifreli değildir; RLS ile korunur.

create table if not exists public.user_data (
    user_id    uuid        not null references auth.users(id) on delete cascade,
    key        text        not null check (char_length(key) between 1 and 64),
    value      jsonb       not null default '{}'::jsonb,
    updated_at timestamptz not null default now(),
    deleted    boolean     not null default false,
    primary key (user_id, key)
);

-- PostgreSQL tablo izinleri RLS'den ayrıdır. RLS satır sahibini sınırlar;
-- bu GRANT'ler ise PostgREST'in authenticated rolüyle tabloya ulaşmasını sağlar.
-- Özellikle mevcut Supabase projelerinde varsayılan izinler kaldırılmış olabilir.
grant usage on schema public to authenticated;
grant select, insert, update, delete on table public.user_data to authenticated;

-- Güncelleme zamanını sunucu tarafında garanti et (çakışma çözümü için)
create or replace function public.set_user_data_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists user_data_set_updated_at on public.user_data;
create trigger user_data_set_updated_at
    before insert or update on public.user_data
    for each row execute function public.set_user_data_updated_at();

-- RLS
alter table public.user_data enable row level security;

drop policy if exists "user_data_select_own" on public.user_data;
create policy "user_data_select_own" on public.user_data
    for select to authenticated using (auth.uid() = user_id);

drop policy if exists "user_data_insert_own" on public.user_data;
create policy "user_data_insert_own" on public.user_data
    for insert to authenticated with check (auth.uid() = user_id);

drop policy if exists "user_data_update_own" on public.user_data;
create policy "user_data_update_own" on public.user_data
    for update to authenticated
    using (auth.uid() = user_id) with check (auth.uid() = user_id);

drop policy if exists "user_data_delete_own" on public.user_data;
create policy "user_data_delete_own" on public.user_data
    for delete to authenticated using (auth.uid() = user_id);

-- Anonim (giriş yapmamış) kullanıcılar hiçbir şeye erişemez: politika yok = erişim yok.
