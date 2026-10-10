-- Fix PostgREST access for existing Kitsugi installations.
-- Apply in Supabase Dashboard → SQL Editor after schema.sql.
-- Safe to rerun. RLS still limits every row to its authenticated owner.
begin;

grant usage on schema public to authenticated;
grant select, insert, update, delete on table public.user_data to authenticated;

commit;
