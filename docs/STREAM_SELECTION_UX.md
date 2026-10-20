# Stream selection: adaptive compact layout

## Implemented
- Portrait: removed duplicate media summary/poster; dense results with grouped source headings.
- Landscape: bounded, independently scrollable control rail (240–300 dp) replaces large poster; results use remaining width and independent lazy scrolling.
- Stream cards: no repeated posters; play and download use 48 dp targets. Metadata expands inline, without launching playback. Unknown quality/language is not inferred.
- Source picker: searchable add-on names, source counts and loading/error/verification status; return to all sources.
- Source-order / descending-quality toggle. Quality ordering is **within each add-on**, unknown quality last; no automatic playback or claim of reliability.
- Query, tag, sort and selected add-on survive recreation through rememberSaveable.
- Probe requests still restricted to visible cards with existing settle delay.
- Existing player resolution, downloads, retry and verification callbacks preserved.

## Validation
- Kotlin syntax parsed using tree-sitter-kotlin; changed Kotlin files have no syntax errors.
- Turkish and English XML parsed, resource names checked for duplicates.
- git diff --check.
- Android compile, emulator, real-device layout and accessibility tests NOT performed: environment has no Java/Android SDK.

## Device acceptance checklist
- Portrait 360 dp; landscape 640×360 dp; tablet; large font: controls remain reachable via rail scrolling.
- 30+ add-ons: picker search, empty query results, loading, verification, retry, source clear.
- Filter with no videos: clear action restores results; 4K and 1080p+ work.
- Sort switches within each source; unknown quality remains last.
- Rotate with query/tag/source/sort set; confirm saved values.
- Expand card, play, download; metadata taps must not start playback.
- Scroll quickly; off-screen probes must remain cancelled.
