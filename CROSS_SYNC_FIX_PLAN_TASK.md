# Kitsugi Cross-Sync Fix — Plan & Task Record

**Date:** 2026-10-07  
**Scope:** Cross-platform bulk list synchronization, progress reporting, cancellation, and diagnostic report export.

## User-reported issue

The cross-platform sync dialog had finished fetching several provider libraries, then remained at 0% while displaying “İçerikler çapraz eşleştiriliyor…”. The user also needed an easy way to export/share the accumulated sync errors and a more useful progress/debug screen.

## Root cause addressed

During cross-platform grouping, each incoming record scanned every previously created unified group twice (candidate matching and conflicting-ID safety checks). This quadratic scan could become very expensive on multi-thousand-record libraries. The grouping phase also left `totalItems` at zero, so the UI could not show progress and displayed an unhelpful 0% state.

## Implementation tasks completed

- [x] Add an identity/title index for candidate groups, while retaining `MediaIdentity.sameMedia()` as the final match decision and preserving conflicting-ID safety checks.
- [x] Report source-record progress during grouping and yield/check coroutine cancellation periodically.
- [x] Show indeterminate progress during network/preparation/verification stages instead of a false 0% determinate bar.
- [x] Track elapsed/phase time, update freshness, platform counters, issue counts, and provide a long-stall warning.
- [x] Add confirmed cancellation. Completed remote writes are not rolled back; the partial run is logged and saved.
- [x] Expand the sync screen with per-platform counters, issue filters, expandable technical log details, and separate “last report” vs “start new sync” behavior.
- [x] Provide save-to-file and Android share-sheet actions while a run is active (partial report) and after it finishes (full report).
- [x] Include app/device environment details in reports and redact common bearer, token, secret, password, API-key, and query-string credential formats.
- [x] Add unit tests for index candidate coverage/scoping and report credential redaction.

## Safety and behavior notes

- Sync remains additive/update-only; this change does not add deletion behavior.
- Candidate indexing only narrows the possible groups. It does not create matches on its own; the existing media identity check still validates candidates.
- Cancellation is cooperative around blocking provider calls. An in-flight synchronous network call may need to return or hit its configured timeout before cancellation completes.
- A report shared during a run is explicitly partial; the final report includes the full retained run history and mapping diagnostics.

## Verification

- `git diff --check`: passed.
- Kotlin syntax parse: passed for the 13 changed Kotlin source/test files.
- Android Gradle unit tests: not run; this sandbox has no `java` executable or `JAVA_HOME` configured.

## Included changed files

1. `app/src/main/java/com/kitsugi/animelist/AppRootSettingsExtras.kt`
2. `app/src/main/java/com/kitsugi/animelist/data/auth/CrossSyncCandidateIndex.kt`
3. `app/src/main/java/com/kitsugi/animelist/data/auth/CrossSyncReportFormatter.kt`
4. `app/src/main/java/com/kitsugi/animelist/data/auth/CrossSyncReportStore.kt`
5. `app/src/main/java/com/kitsugi/animelist/model/CrossSyncState.kt`
6. `app/src/main/java/com/kitsugi/animelist/model/MediaIdentity.kt`
7. `app/src/main/java/com/kitsugi/animelist/ui/app/AuthViewModel.kt`
8. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiAccountConnectionsDialog.kt`
9. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiCrossSyncDialog.kt`
10. `app/src/main/java/com/kitsugi/animelist/ui/screens/settings/AccountSettingsSubPages.kt`
11. `app/src/main/java/com/kitsugi/animelist/ui/screens/settings/SettingsScreenParameters.kt`
12. `app/src/test/java/com/kitsugi/animelist/data/auth/CrossSyncCandidateIndexTest.kt`
13. `app/src/test/java/com/kitsugi/animelist/data/auth/CrossSyncReportFormatterTest.kt`
