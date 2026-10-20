# Stream selection: adaptive card layout + smooth top bar (v2.4.319)

## What changed in v2.4.319 (supersedes parts of v2.4.318)

| Area | v2.4.318 | v2.4.319 |
|---|---|---|
| Card artwork | Removed entirely | 74×104 dp (2:3) cover per card; provider thumbnail → media poster fallback → gradient placeholder |
| Card metadata | Hidden behind an "Ayrıntılar" accordion | Always visible chips: quality (on artwork), audio kind, size, audio/subtitle languages, Debrid/P2P, source host |
| Top bar | `AnimatedVisibility` toggled at `firstVisibleItemIndex >= 2` inside a sticky header (height jump) | Bar translates 1:1 with scroll via `NestedScrollConnection` + `graphicsLayer`; returns at list top. No sticky header height change |
| Playback failure | Silent / uncaught | `startWithStreamUrls` returns `Boolean`, screen shows a reason dialog; player `onCreate` is guarded |
| Intent payload | Up to 250 KB + 450 KB backup JSON (~1.4 MB) | 48 KB + 96 KB, plus a no-extras retry on `TransactionTooLargeException` |

## Layout rules

- **One card implementation for both orientations.** The card is a `Row`:
  `[artwork 74×104] [title / addon · host / chip flow] [play 46 dp, download 38 dp]`.
  Portrait list width ≈ 360 dp → artwork + two action columns + ~180 dp of text.
  Landscape list keeps the same card; the extra width goes to the text column.
- **Portrait:** collapsible top bar → scrolling list with `search → source picker → sort → summary → groups`.
- **Landscape:** collapsible top bar → `Row(control rail 240–300 dp, results list)`.
  The rail scrolls independently; both rail and list feed the same nested-scroll connection,
  so the bar behaves identically.
- Content padding top = bar height + 8 dp, so the first row never hides under the bar.

## Top bar scroll model

```
offset ∈ [0, barHeightPx]      0 = visible, barHeightPx = hidden
onPreScroll(dy):  target = clamp(offset - dy)
                  consume (0, -(target - offset))   // bar eats the scroll first
graphicsLayer { translationY = -offset; alpha = 1 - 0.25 * progress }
```

Because the offset is a plain `MutableFloatState` read inside `graphicsLayer`, scrolling
does **not** trigger recomposition — only a layer update. `snapshotFlow` resets the offset
to 0 when the list returns to the very top.

## Playback hardening (this release)

1. `KitsugiStreamScreen.launchPlayer`: history write wrapped in `runCatching`; launch result
   checked; `Throwable` caught → dialog instead of an uncaught main-thread exception.
2. `KitsugiFullscreenPlayerActivity.startWithStreamUrls`: returns `Boolean`, catches
   `TransactionTooLargeException` separately and retries without extras, catches `Throwable`,
   logs with class name.
3. `KitsugiFullscreenPlayerActivity.onCreate`: body moved into `bootstrapPlayer()` and called
   inside `runCatching`; missing `videoUrl`/`videoId` → toast + `finish()` (never a blank player).
4. `pipReceiverRegistered` flag + `runCatching` around `unregisterReceiver` / `KeepAliveService`.

## Validation status

- Kotlin syntax parsed with tree-sitter-kotlin (changed files: no errors).
- `values/strings.xml` + `values-en/strings.xml` XML parse OK, no duplicate names;
  `scripts/check_localization_resource_parity.py` → **in sync (7743 strings, 4 arrays)**.
- Android compilation / device run **NOT performed** (no Java/Android SDK in this environment).

## Device acceptance checklist

- Portrait 360 dp + landscape 640×360 dp: card artwork visible, no clipped text, chips wrap.
- Scroll down/up: top bar follows the finger smoothly, returns at the top; no list jump.
- 125-source list: play a source from Anizium (or similar), confirm the player opens;
  if it fails, confirm the "Oynatıcı açılamadı" dialog appears with a reason.
- Rotate with query/tag/source/sort set; values persist.
- Adult-content blur setting still hides card artwork.
- Fast fling: probes must stay limited to visible cards.
