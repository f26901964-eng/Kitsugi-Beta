-- Apply after schema.sql and 20261009_vault_compare_and_swap.sql.
-- Non-destructive: no user rows, sessions, or encrypted vaults are changed.
begin;

-- RLS does NOT protect TRUNCATE, REFERENCES or TRIGGER privileges. Supabase's
-- default grants can leave these enabled even when the owner-only policies exist.
revoke all on table public.user_data from public, anon, authenticated;
grant usage on schema public to authenticated;
grant select, insert, update, delete on table public.user_data to authenticated;
alter table public.user_data enable row level security;

-- A fixed search_path prevents future function changes from resolving objects
-- in a caller-controlled schema. Trigger invocation does not need public EXECUTE.
create or replace function public.set_user_data_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    new.updated_at = pg_catalog.now();
    return new;
end;
$$;
revoke all on function public.set_user_data_updated_at() from public, anon, authenticated;

-- Keep the security-definer RPC callable only by signed-in clients. The function
-- derives the owner from auth.uid(), never from a caller-supplied user ID.
revoke all on function public.kitsugi_vault_cas(text, jsonb, jsonb) from public, anon;
grant execute on function public.kitsugi_vault_cas(text, jsonb, jsonb) to authenticated;

notify pgrst, 'reload schema';
commit;
