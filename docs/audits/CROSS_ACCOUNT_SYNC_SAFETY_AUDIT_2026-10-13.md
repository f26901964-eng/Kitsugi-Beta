# Cross-account sync safety audit — 2026-10-13

## Status

**Fail-closed hardening implemented; build, unit tests, and live API verification remain outstanding.** The implementation now requires an authoritative identity path before cross-account writes, and deliberately disables cross-account Bangumi mutations until a trustworthy crosswalk is available. This is not a claim of mathematically zero risk: provider catalog data can itself be wrong, and the changed code has not yet been compiled or exercised against live accounts.

## Scope and material reviewed

The review followed the import → identity normalization → candidate clustering → authorization → target lookup → add/update/delete path for AniList, MyAnimeList (MAL), Kitsu, Shikimori, Simkl, and Bangumi. Relevant code reviewed includes:

- `AuthViewModel.syncPlatforms` and its candidate clustering, merge authorization, status/progress/score aggregation, per-provider add/update branches, and Simkl batch queue.
- `ExternalListSyncManager.syncEntry` / `deleteEntry`, including source-provider routing and cross-account fan-out.
- `CrossSyncMediaIdResolver`, `CrossSyncIdentityGuard`, `CrossSyncEntryFactory`, and `MediaIdentity`.
- Each service mutation manager/client: `AniListSyncManager`, `MalSyncManager`, `KitsuSyncManager` / `KitsuApiClient`, `ShikimoriSyncManager` / `ShikimoriApiClient`, `SimklSyncManager` / `SimklApiClient`, `BangumiSyncManager` / `BangumiApiClient`.
- Provider import and identity construction paths, including AniList, Kitsu, Shikimori, Simkl, and Bangumi importers.
- The legacy `SimklSyncRepository` outbox write path and all in-repository call sites for account mutations.

The supplied `Kitsugi_CrossSync_Report_20261013_002923_745.txt` was not available in the workspace, so its claims could not be cross-checked. Reattach it if the audit needs to address its individual findings.

## Authorization invariant now enforced

A cross-account candidate is not authorized merely because titles look similar, numeric IDs happen to match, two providers agree, or an ARM/Jikan heuristic returns an ID. A new cross-account target requires all of the following:

1. Resolve a real MAL ID using the source provider's declared namespace and official provider crosswalk; never reinterpret a Kitsu, Shikimori, Bangumi, or Simkl ID as MAL by numeric range.
2. Verify the source record against the authoritative MAL catalog: exact compatible media type, strict title identity, and a known positive release year matching exactly. MAL catalog failure, missing title/year, ambiguity, or mismatch means **no write**. Jikan is not an authorization fallback.
3. When clustering two providers, prove each record resolves to the same MAL ID and independently passes identity checks. Conflicting, ambiguous, or title-only candidates enter identity review and are not written.
4. Resolve/re-fetch the destination's own native media ID and verify its provider ID, media type, title, year, and (where relevant) MAL crosswalk immediately before mutation. List-entry/user-rate handles are not treated as media identity.
5. Build new destination rows from a source-scoped copy containing only the verified MAL ID; clear source/destination provider handles and IDs. Existing-target updates preserve the destination's own namespace and handle, then revalidate them in the manager.

Strict title/year checks intentionally accept false negatives: if evidence is incomplete, syncing fewer records is preferable to modifying the wrong account entry.

## Provider-by-provider result

| Service | Source identity and cross-account gate | Destination mutation guard | Fail-closed cases |
|---|---|---|---|
| **AniList** | Native AniList media ID is checked against AniList type/title/year. Any imported row with an AniList list-entry ID—whether its stored `malId` is real or an AniList synthetic stable ID—uses `resolveVerifiedMalIdFromAniListEntry`, which checks `Viewer`, list-entry ID and owner, attached media ID, `idMal`, type, titles, and year. | Existing list-entry handles are re-fetched and checked against the authenticated viewer and expected media before update/delete. New rows are resolved by verified MAL/native ID and their AniList media is checked before writing. | Missing token/ID, wrong owner, stale list handle, mismatched type/title/year/MAL ID, or failed lookup.
| **MyAnimeList** | A MAL ID is accepted only from a MAL/manual contract or after another provider's verified crosswalk. | `MalSyncManager` re-verifies the MAL catalog identity immediately before update/delete; it writes only the real MAL ID. | Wrong ID, unsupported media type, missing year/title, catalog mismatch/offline.
| **Kitsu** | Kitsu native ID is re-fetched; the official Kitsu anime/manga resource must match ID/type/title/year and expose exactly one MAL mapping. Mapping relationship association is checked so a linked mapping that points at a different Kitsu resource is rejected. | Cached, ARM, MAL-mapping, and title-search candidates are re-fetched and checked against strict identity evidence. User library entry is looked up for the authenticated Kitsu user and the verified media ID before update/delete. | Multiple or absent MAL mappings, unlinked/wrong-resource mapping, missing year, stale user-library handle, mismatched type/title.
| **Shikimori** | Native `target_id` is treated only as a Shikimori ID. Shikimori's own media response must return the same target ID/type, strict title/year, and official `myanimelist_id`. | MAL-to-Shikimori resolution is followed by a fresh Shikimori media check, including exact MAL ID, type/title/year. Deletes fetch the authenticated user's rates and delete only the single exact target/type match; ambiguous duplicates skip. | Numeric ID coincidence, ARM mapping alone, mismatched crosswalk/title/year/type, missing user identity, or duplicate target rates.
| **Simkl** | Native Simkl IDs are revalidated. Crosswalk lookup requires the requested MAL ID to be echoed by Simkl with exact type/title/year; title-only/TMDB/AniList guesses do not authorize a write. | Batch, single-entry, progress, rating, and delete paths use only the verified Simkl ID. The legacy ID-only outbox now requires title/year/MAL evidence and revalidates both MAL and Simkl identity at execution time. | Missing MAL crosswalk for cross-account writes, stale/mismatched Simkl ID, missing year/title, unsupported manga/TV progress.
| **Bangumi** | Native Bangumi subject IDs are used only for same-Bangumi-account operations. Subject ID, supported subject type, type/platform, strict title, and exact year are re-fetched and checked. | Every cross-account add, update, and delete-like write is blocked. Bangumi's fallback delete operation (“dropped” + zero rating) is also blocked for cross-account callers. | No official MAL crosswalk in the supported path; no heuristic title/year mapping or learned cache may authorize a cross-account mutation.

## Mutation-path findings and changes

### Clustering and field propagation

`AuthViewModel` previously had several sources of potential identity ambiguity: provider-native values share the legacy `malId` field; title aliases can collide across seasons/remakes; provider mappings can be stale; and an uncertain group could otherwise propagate a status, progress, or score. The current clustering path now requires exact type, known/equal year, strict title identity, a common verified MAL ID, and per-record MAL catalog verification before joining groups. Ambiguous and unsafe matches are isolated for review and skipped. Numeric provider IDs remain namespaced by source in `MediaIdentity`.

Merged status/progress/score values are only considered after the group passes identity authorization. Empty scores are omitted rather than clearing another account's rating. Provider-specific target managers perform a final identity check before the payload is sent. Simkl aggregate TV progress remains intentionally unsupported rather than guessing a season/episode mapping; Kitsu retains its media-length guard.

### Direct sync and deletion

`ExternalListSyncManager` now derives cross-account targets from a source-aware resolver, verifies the resulting MAL identity, and passes a sanitized target copy. Same-provider edits use that provider's own native ID and manager-side revalidation. Delete paths follow the same identity gates; deleting a wrong record is treated as equally unsafe as adding or updating one. Bangumi-origin rows are routed only to Bangumi, not fanned out using a Bangumi subject ID.

### Bangumi

The removed `resolveBangumiId`/`BangumiLocalMappingCache` behavior could learn a MAL↔Bangumi association from heuristic title matches and reuse it later as mutation authority. That path is removed. Bangumi cross-account add/update/delete now return an unresolved safe-skip result, including updates to an existing Bangumi target. Same-account native edits remain available after re-fetching and comparing the subject ID, supported subject type, media type, title, and exact year. User-facing sync reporting classifies this as a skip, not success or an API failure. The older Bangumi plan document is marked as historical/superseded.

### ID namespaces and mapping parsers

Kitsu and Bangumi stable-ID conversions now use bounded arithmetic that rejects out-of-range values without integer overflow; Kitsu imports reject IDs outside the stable namespace. Kitsu mapping parsing rejects a mapping whose linked resource points at another Kitsu media record. Cross-target construction clears unrelated provider-native IDs/handles.

### Legacy Simkl outbox

The repository search found no active in-repository instantiation or enqueue call sites for `SimklSyncRepository`, but its worker could previously write by bare Simkl ID. The outbox now requires a nonblank title, positive year, and real MAL ID when enqueueing, then rechecks the MAL catalog and Simkl-native record before any queued status/progress/rating mutation. It must not be reconnected to a cross-sync caller without supplying and preserving that evidence.

## Regression coverage added or extended

- `CrossSyncMediaIdResolverTest`: unique Kitsu mapping and native identity checks; Shikimori identity checks; Bangumi cross-ID rejection.
- `KitsuMediaIdentityTest`: rejects wrong-type, unlinked, and linked-but-wrong-resource mappings.
- `KitsuIdNamespaceTest` and `BangumiIdNamespaceTest`: bounded stable-ID conversion and overflow rejection.
- `CrossSyncEntryFactoryTest`: keeps only the verified MAL identity for new targets and preserves destination-scoped identity on updates.
- `CrossSyncIdentityGuardTest`, `MediaIdentityDisambiguationTest`, and `SimklSyncContractTest` extend strict title/year/type, namespace, and Simkl ID-contract coverage.

**None of the tests or a build have been run.** This environment has no `java`, `kotlinc`, or `gradle` executable, the Gradle wrapper is not executable, and `JAVA_HOME` is unset.

## Residual validation risks / next steps

1. Verify the GraphQL query fields used by the AniList list-entry ownership check against a live authorized account, then test both owner-match and owner-mismatch cases.
2. Verify the Simkl `GET /search/id?simkl={id}` contract and returned JSON against official current documentation or a controlled integration test. The code fails closed on unexpected/malformed data, but endpoint behavior/schema has not been independently verified.
3. Run Android compilation and the full relevant unit-test suite in a Java/Gradle-equipped environment; the current workspace could only receive static review and diff/resource checks.
4. Add controlled integration tests for provider crosswalk mismatch, stale destination handles, and account ownership before calling this fully validated.
5. Reattach the supplied cross-sync report if its individual claims need explicit disposition.

Until those checks pass, treat the changes as **safety hardening awaiting validation**, not a production certification.