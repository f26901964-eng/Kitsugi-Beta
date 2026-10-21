import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { after, before, test } from 'node:test';
import { PGlite } from '@electric-sql/pglite';

// Isolated PostgreSQL; never contacts Supabase or reads real users/credentials.
const db = new PGlite();
const ownerA = '10000000-0000-4000-8000-000000000001';
const ownerB = '10000000-0000-4000-8000-000000000002';
const sql = async (path) => readFile(new URL(`../../${path}`, import.meta.url), 'utf8');
const envelope = (version) => ({ iv: 'test-only-iv', ct: `test-only-ciphertext-${version}` });

async function asUser(uid, action, role = 'authenticated') {
  await db.exec('begin');
  try {
    await db.query("select set_config('request.jwt.claim.sub', $1, true)", [uid ?? '']);
    await db.exec(`set local role ${role}`);
    const result = await action();
    await db.exec('commit');
    return result;
  } catch (error) {
    await db.exec('rollback');
    throw error;
  }
}
async function cas(key, expected, value) {
  const { rows } = await db.query(
    'select public.kitsugi_vault_cas($1, $2::jsonb, $3::jsonb) as accepted',
    [key, expected == null ? null : JSON.stringify(expected), value == null ? null : JSON.stringify(value)],
  );
  return rows[0].accepted;
}

before(async () => {
  await db.exec(`
    create role anon;
    create role authenticated;
    create schema auth;
    create table auth.users(id uuid primary key);
    insert into auth.users values ('${ownerA}'), ('${ownerB}');
    create function auth.uid() returns uuid language sql stable as
      $$ select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
    grant usage on schema auth to anon, authenticated;
    grant execute on function auth.uid() to anon, authenticated;
  `);
  await db.exec(await sql('supabase/schema.sql'));
  // Reproduce the over-broad live grants found during the audit.
  await db.exec('grant all on public.user_data to anon, authenticated');
  for (let pass = 0; pass < 2; pass++) {
    await db.exec(await sql('supabase/migrations/20261009_vault_compare_and_swap.sql'));
    await db.exec(await sql('supabase/migrations/20261010_user_data_authenticated_grants.sql'));
    await db.exec(await sql('supabase/migrations/20261021_account_privilege_hardening.sql'));
  }
});
after(() => db.close());

test('initial vault creation wins once, including JSON null expected values', async () => {
  await asUser(ownerA, async () => {
    assert.equal(await cas('vault_meta', null, envelope(1)), true);
    assert.equal(await cas('vault_meta', null, envelope(2)), false);
    const { rows } = await db.query("select public.kitsugi_vault_cas('vault_meta', 'null'::jsonb, $1::jsonb) as accepted", [JSON.stringify(envelope(2))]);
    assert.equal(rows[0].accepted, false);
  });
});

test('stale snapshots cannot overwrite a newer encrypted payload', async () => {
  await asUser(ownerA, async () => {
    assert.equal(await cas('linked_accounts_vault', null, envelope(1)), true);
    assert.equal(await cas('linked_accounts_vault', envelope(1), envelope(2)), true);
    assert.equal(await cas('linked_accounts_vault', envelope(1), envelope(3)), false);
  });
});

test('password wrap transitions preserve CAS ordering', async () => {
  const staged = { ...envelope(1), pending: envelope(2) };
  await asUser(ownerA, async () => {
    assert.equal(await cas('vault_meta', envelope(1), staged), true);
    assert.equal(await cas('vault_meta', envelope(1), envelope(3)), false);
    assert.equal(await cas('vault_meta', staged, envelope(2)), true);
    assert.equal(await cas('vault_meta', staged, envelope(3)), false);
  });
});

test('RLS hides another owner and rejects spoofed user IDs', async () => {
  await asUser(ownerB, async () => {
    const { rows } = await db.query('select * from public.user_data');
    assert.deepEqual(rows, []);
  });
  await assert.rejects(asUser(ownerB, () => db.query(
    "insert into public.user_data(user_id, key, value) values ($1, 'search_history', '[]')", [ownerA],
  )), /row-level security/);
});

test('RPC uses auth.uid(), never an arbitrary owner', async () => {
  await asUser(ownerB, async () => assert.equal(await cas('vault_meta', null, envelope('B')), true));
  await asUser(ownerA, async () => {
    const { rows } = await db.query("select value from public.user_data where key = 'vault_meta'");
    assert.deepEqual(rows[0].value, envelope(2));
  });
});

test('direct vault inserts are blocked even for the row owner', async () => {
  // Existing row or not, the restrictive INSERT policy must reject an upsert.
  await assert.rejects(asUser(ownerA, () => db.query(
    "insert into public.user_data(user_id,key,value) values ($1,'vault_meta','{}') on conflict(user_id,key) do update set value = excluded.value", [ownerA],
  )), /row-level security/);
});

test('direct vault updates and deletes cannot bypass CAS', async () => {
  await asUser(ownerA, async () => {
    const update = await db.query("update public.user_data set value = '{}' where key = 'vault_meta' returning key");
    const deletion = await db.query("delete from public.user_data where key = 'linked_accounts_vault' returning key");
    assert.equal(update.rows.length, 0);
    assert.equal(deletion.rows.length, 0);
  });
});

test('normal search history and notification CRUD still works for the owner', async () => {
  await asUser(ownerA, async () => {
    for (const key of ['search_history', 'notifications']) {
      await db.query('insert into public.user_data(user_id,key,value) values ($1,$2,$3)', [ownerA, key, '{}']);
      const { rows } = await db.query("update public.user_data set value = '{\"test\":true}' where key = $1 returning value", [key]);
      assert.equal(rows[0].value.test, true);
      assert.equal((await db.query('delete from public.user_data where key = $1 returning key', [key])).rows.length, 1);
    }
  });
});

test('a payload without metadata is rejected', async () => {
  // Test fixture only; never delete metadata in production.
  await db.query("delete from public.user_data where user_id = $1 and key = 'vault_meta'", [ownerB]);
  await assert.rejects(asUser(ownerB, () => cas('linked_accounts_vault', null, envelope(1))), /Missing vault metadata/);
});

test('invalid vault keys and non-object envelopes are rejected', async () => {
  for (const key of [null, 'search_history', 'notifications', 'other']) {
    await assert.rejects(asUser(ownerA, () => cas(key, null, envelope(1))), /Invalid vault key/);
  }
  for (const value of [null, [], 'plaintext', 42]) {
    await assert.rejects(asUser(ownerA, () => cas('vault_meta', envelope(2), value)), /Invalid encrypted envelope/);
  }
});

test('anonymous callers cannot access rows or execute the privileged RPC', async () => {
  await assert.rejects(asUser(null, () => db.query('select * from public.user_data'), 'anon'), /permission denied/);
  await assert.rejects(asUser(null, () => cas('vault_meta', null, envelope(1)), 'anon'), /permission denied/);
  await assert.rejects(asUser(null, () => cas('vault_meta', null, envelope(1))), /Authentication required/);
});

test('neither anon nor authenticated retains RLS-bypassing table privileges', async () => {
  for (const role of ['anon', 'authenticated']) {
    for (const permission of ['TRUNCATE', 'REFERENCES', 'TRIGGER']) {
      const { rows } = await db.query("select has_table_privilege($1, 'public.user_data', $2) as allowed", [role, permission]);
      assert.equal(rows[0].allowed, false, `${role}: ${permission}`);
    }
    await assert.rejects(asUser(ownerA, () => db.exec('truncate public.user_data'), role), /permission denied/);
  }
});

test('server timestamps cannot be supplied by the client', async () => {
  await asUser(ownerA, async () => {
    const { rows } = await db.query("insert into public.user_data(user_id,key,value,updated_at) values ($1,'search_history','[]','2000-01-01') returning updated_at > '2020-01-01' as server_time", [ownerA]);
    assert.equal(rows[0].server_time, true);
  });
});

test('reapplying migrations preserves existing rows and protections', async () => {
  const before = (await db.query('select * from public.user_data order by user_id,key')).rows;
  await db.exec(await sql('supabase/migrations/20261009_vault_compare_and_swap.sql'));
  await db.exec(await sql('supabase/migrations/20261021_account_privilege_hardening.sql'));
  assert.deepEqual((await db.query('select * from public.user_data order by user_id,key')).rows, before);
  const { rows } = await db.query("select prosecdef, proconfig from pg_proc where proname = 'kitsugi_vault_cas'");
  assert.equal(rows[0].prosecdef, true);
  assert.ok(rows[0].proconfig.some((entry) => entry.startsWith('search_path=')));
});
