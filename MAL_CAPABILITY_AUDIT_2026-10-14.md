# MAL capability audit — 2026-10-14

## Scope and confidence

This is a source-level audit of the current checkout, not a live MAL verification. I traced the OAuth profile loader, the Jikan-compatible fallback chain, the on-device MAL HTML adapter, the public-user profile repository, and the profile UI. No request to `myanimelist.net`, no real account, and no Android runtime were available in this session. “Wired in code” below therefore does not mean “confirmed against today's MAL response.”

The older `MAL_PROFIL_TAMAMLAMA_DURUMU_2026-10-12.md` and `MAL_PROFIL_TAMAMLAMA_PLANI_2026-10-12.md` describe an earlier intermediate state. They say profile UI/ViewModel work is pending, but the current source already has those integrations. Treat their TODO lists as historical, not as an accurate current gap list.

## Data paths — keep the sources distinct

| Capability | Source in the app | What the code does |
|---|---|---|
| Signed-in MAL account and core profile/statistics | Official MAL API v2 + OAuth bearer token | `KitsugiProfileViewModel.fetchMalProfile()` requests `/v2/users/@me` with account/profile/statistics fields. |
| Supplemental public profile data | Jikan-compatible gateway, not official MAL v2 | `JikanGateway` tries on-device MAL HTML (`MalWebSource`) first for supported user endpoints, then configured miribyou and Tenrai. The result may therefore be direct MAL-page-derived data or a Jikan-compatible fallback; do not attribute every returned field to a successful MAL HTML scrape. |
| Public profiles other than the signed-in user | Same Jikan-compatible gateway | `OtherUserProfileRepository.fetchMalUserState()` populates the shared MAL profile state used by `PlatformUserProfileScreen`. |
| Personal MAL notifications | MAL website session cookies/WebView, separate from OAuth | `MalNotificationsClient` reads the first-party `/notification` page; `MalSessionCoordinator` compares the web identity with the OAuth identity. MAL v2 is not used for this feed. |

The OAuth token is not sent to MAL website requests. Public HTML enrichment does not require the user's web cookies. The notification feed does require the website session, so the API and website identities remain separately checked.

## Profile capabilities already present in the current source

The `MalWebSource` allowlist and its parser/JSON mapping cover the following user-profile data paths:

- public profile fields, about HTML, external links, basic anime/manga statistics;
- favorites (anime, manga, characters, people) and recent anime/manga list updates;
- MAL friends and public watch/read history;
- detailed anime and manga statistics (scores, types, statuses, ratings, genres, studios; anime voice actors);
- reviews, recommendations, and clubs.

These fields are mapped into `MalProfileState`, loaded for both the signed-in profile and public MAL profiles, and exposed through `MalProfileContent` tabs/filters. MAL has a friends list in this integration; the code does not claim separate follower/following lists. A MAL profile banner is not parsed or rendered (the header uses the app gradient).

The review/recommendation/club HTML selectors and the site response shape still need a real-device fixture run. Existing JSON-mapper tests and parser unit tests check local examples, not a live current MAL page.

## Work in this audit

- Replaced the device adapter's constructed `?p=N` URLs for friends/reviews/recommendations/clubs with traversal of the same-path next link actually present in the previous MAL HTML page. The adapter emits pagination only when it observes a safe same-origin next link; it does not assert an undocumented page-query convention.
- Added pagination metadata for clubs and wired club loading through both signed-in and public-user profile screens.
- Fixed a concrete failure mode in friends/reviews/recommendations continuation: a transient request/parse failure no longer permanently clears `hasNextPage`. The page cursor stays put and the profile exposes a retry affordance. The same retry behavior covers clubs.
- Added unit cases for safe next-link resolution, explicit pagination JSON, and club pagination mapping. These tests have not been executed because this environment has no usable Java/`JAVA_HOME`.

## Confirmed gaps vs. unverified candidates

### Not currently implemented in this app

- MAL profile forum activity and profile comments are not wired into the profile UI or adapter.
- MAL website list/favorite paging beyond data the current profile endpoints return is not implemented as a separate profile feature.

These are app gaps only. This audit does not claim that a particular MAL route exists or that a page is publicly readable.

### Do not add until verified against MAL

- Forum/comments URL forms and their HTML/JSON structures.
- Whether clubs, history, or any remaining profile data have additional pages beyond the next-link traversal just added.
- Any banner/cover field on MAL profiles.
- Any separate MAL follower/following model.

No guessed `?page`, `?p`, `offset`, or `limit` website request is considered confirmed. The adapter should follow an actual same-origin next link or fall through to the Jikan-compatible providers.

## Verification status

- Static source inspection confirms the call paths and UI wiring described above.
- Added parser/mapper unit tests are present but unrun.
- Kotlin compilation, local JVM tests, Android UI behavior, current MAL HTML selectors, notification continuation, and real-account OAuth/web-session matching remain unverified in this environment.
- Earlier setup attempts found `./gradlew` non-executable and Java/`JAVA_HOME` unavailable; this turn did not establish a successful Gradle build.
