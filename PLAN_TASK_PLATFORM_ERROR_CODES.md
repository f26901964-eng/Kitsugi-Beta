# Plan / Task — Stable Platform Error Codes

## Scope

Add stable, user-facing diagnostics for Manga sources and CloudStream plugins across Android and TV interfaces, with a searchable reference catalog and Turkish/English explanations.

## Code ranges

- **Manga:** `KTS-SRC-9001–9040`; currently mapped: timeout, rate limit, Cloudflare/CAPTCHA, not found, network/DNS, auth/session, parser/source changed, and unknown (`9001–9008`).
- **CloudStream:** `KTS-CSP-9101–9140`; currently mapped: protection/WAF, unsupported or blocked plugin, no streams, timeout, rate limit, network, parser, and unknown (`9101–9108`).
- Remaining IDs are reserved. Do not renumber IDs already published in support reports.

## Implementation checklist

- [x] Add a range-validating code generator and shared catalog/severity definitions.
- [x] Classify Manga and CloudStream failures using platform signatures, HTTP status, and error text.
- [x] Persist Manga error codes and cooldown timestamps; add Room migration 28→29.
- [x] Track CloudStream codes per plugin and add code counts to the diagnostic summary/report.
- [x] Add shared code chips and localized explanation blocks to Android and TV error surfaces.
- [x] Add a searchable Settings catalog with All, Manga, and CloudStream filters.
- [x] Add Turkish and English strings and extend the resource parity checker to scan all values XML files.
- [x] Add classifier and diagnostic-summary unit tests.
- [ ] Run Gradle tests/build in a Java-enabled environment.
- [ ] Generate and verify `app/schemas/com.kitsugi.animelist.data.local.KitsugiDatabase/29.json`.

## Verification notes

Localization parity passed for 4,302 strings and 4 arrays, and syntax parsing passed for the changed Kotlin files. Gradle tests and the Room v29 schema export remain unverified because this workspace has no Java/JDK installed. Suggested next step: run `bash ./gradlew testDebugUnitTest` in a Java-enabled checkout, then inspect and commit the exported Room v29 schema.
