# PLAN / TASK — Global CloudStream Plugin Source Fetch Recovery

**Date:** 2026-10-10  
**Branch:** `arena/97de17a9-kitsugi-beta`  
**Status:** App-side changes staged in this bundle; Android build and device playback verification remain pending.

## User-visible problem

CloudStream plugins return no usable source data across popular series, films, and anime. Preserve the intended episodic flow: search with the plain title first, load the matching detail page, choose the requested season/episode, then extract streams.

## Findings and changes

1. **Diagnostic runs could disable installed plugins.** `CsPluginDiagnosticRunner` wrote `enabled=false` after selected failure statuses, while regular stream retrieval queries only enabled plugin rows. Transient network/provider failures could therefore leave all plugin categories with no providers to search. Diagnostic runs are now read-only with respect to plugin enabled state.
2. **Disabled-provider recovery and visibility.** The stream screen records installed/enabled counts and explains when every installed CloudStream plugin is disabled. The installed-provider settings list offers an explicit action to re-enable disabled plugins.
3. **Direct provider URLs no longer terminate the shared search.** A selected provider URL is attempted first. If the provider is unavailable or produces no stream, retrieval falls back to the common title-first search across enabled providers, preserving the same title, year, media kind, season, and episode.
4. **Fetch identity.** Provider API name is included in the fetch key and Compose effect dependencies to avoid treating a provider change as an identical fetch.
5. **Protection diagnostics.** Existing changes in this working tree distinguish explicit protection signatures from ordinary empty searches; an empty search alone is not counted as proof of Cloudflare/WAF.

## Acceptance checks

- Plain-title-first search remains intact.
- Direct URL success remains preferred; empty/failing direct routes fall through to all enabled plugins.
- Episodic fallback retains the requested season and episode.
- Diagnostics do not automatically disable installed plugins.
- If all installed providers are disabled, the user sees a clear explanation and can re-enable them.

## Files included

The archive includes every modified or untracked working-tree file at packaging time under `changed_files/`, plus the related existing task documents under `related_task_plans/`.

## Verification status

- `git diff --check`: passed.
- Android resource XML parsing and English/Turkish resource-name parity: passed.
- Gradle build/unit tests: not run; Java/JDK is unavailable in the workspace.
- On-device plugin retrieval/playback: not yet verified. The device's Room database state cannot be inspected from this repository checkout, so the diagnostic auto-disable path is a confirmed code hazard, not proof that it caused the current device state.
