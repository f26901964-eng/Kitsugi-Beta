-- Apply AFTER schema.sql, before installing the new APK. Safe to rerun.
-- Older clients may read their vault, but cannot bypass conflict checks with direct writes.
begin;
create or replace function public.kitsugi_vault_cas(p_key text, p_expected jsonb, p_value jsonb)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_current jsonb;
    v_exists boolean;
begin
    if v_uid is null then raise exception 'Authentication required' using errcode = '42501'; end if;
    if p_key not in ('vault_meta', 'linked_accounts_vault') or p_key is null then
        raise exception 'Invalid vault key' using errcode = '22023';
    end if;
    if p_value is null or jsonb_typeof(p_value) <> 'object' then
        raise exception 'Invalid encrypted envelope' using errcode = '22023';
    end if;
    -- Also serializes initial inserts, where SELECT FOR UPDATE alone cannot lock a missing row.
    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_uid::text || ':' || p_key, 0));
    select value into v_current from public.user_data where user_id = v_uid and key = p_key for update;
    v_exists := found;
    if (v_exists and v_current is distinct from nullif(p_expected, 'null'::jsonb))
       or (not v_exists and nullif(p_expected, 'null'::jsonb) is not null) then
        return false;
    end if;
    if p_key = 'linked_accounts_vault' and not exists (
        select 1 from public.user_data where user_id = v_uid and key = 'vault_meta'
    ) then raise exception 'Missing vault metadata'; end if;
    insert into public.user_data(user_id, key, value) values (v_uid, p_key, p_value)
    on conflict(user_id, key) do update set value = excluded.value;
    return true;
end;
$$;
revoke all on function public.kitsugi_vault_cas(text, jsonb, jsonb) from public, anon;
grant execute on function public.kitsugi_vault_cas(text, jsonb, jsonb) to authenticated;

-- Restrictive policies also guard against any existing permissive owner-write policy.
drop policy if exists vault_rpc_insert_only on public.user_data;
create policy vault_rpc_insert_only on public.user_data as restrictive for insert to authenticated
    with check (key not in ('vault_meta', 'linked_accounts_vault'));
drop policy if exists vault_rpc_update_only on public.user_data;
create policy vault_rpc_update_only on public.user_data as restrictive for update to authenticated
    using (key not in ('vault_meta', 'linked_accounts_vault'))
    with check (key not in ('vault_meta', 'linked_accounts_vault'));
drop policy if exists vault_rpc_delete_only on public.user_data;
create policy vault_rpc_delete_only on public.user_data as restrictive for delete to authenticated
    using (key not in ('vault_meta', 'linked_accounts_vault'));
commit;
