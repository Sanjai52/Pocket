# Pocket — Page-Switch Lag Follow-Up Chronicle

Date: 2026-09-23 (same session day as predecessor)
Author: Muse Spark (opencode session)
Predecessor: `LAG-DEBUG-CHRONICLE.md` (2026-09-23, same session)
Trigger: user pasted a follow-up investigation prompt; executed Phases 5-10 below.
Scope: UI-tweak / minor-fix only. No new features, no dependency bumps.

## 1. Symptom (carried forward)

"Slight lag" on every bottom-tab switch (Home / Calendar / Insights / Settings).
Predecessor removed the `NavHost` default crossfade; user still perceived lag.
This session closes the measurement gaps the predecessor left open.

## 2. Environment used for THIS session's testing

- Pixel_6 AVD (`emulator-5554`), 1080x2400, gfxstream OpenGL ES 3.0 — emulator ONLY.
- No physical device was available to this agent. The user confirmed the lag is
  felt on BOTH emulator and physical device (Q1 answer), so all emulator-only
  conclusions below are provisional for the device side; device-side verification
  is explicitly pending with the user.
- navigation-compose 2.8.9 (from `gradle/libs.versions.toml`).
- Measurement: scripted `adb shell input tap` at UI-dump tab coordinates
  (M3 bar: Home 127,2274 / Calendar 402,2274 / Insights 678,2274 /
  Settings 953,2274; diagnostic plain-Row bar: 178/419/661/902,2337),
  frames via `adb shell dumpsys gfxinfo in.marxen.pocket`.

## 3. Investigation log

### Phase 5 — Confirm test environment (Q1)

Asked the reporter directly: lag is felt on BOTH emulator and physical device.
Consequence: emulator measurements below are relevant (issue reproduces there)
but final sign-off requires a device run by the user.

### Phase 6 — Real A/B via isolated revert (not "no APK retained")

The transition fix was uncommitted working-tree state, so instead of a git
worktree, the baseline was produced by removing ONLY the transition arguments
from `PocketNavHost.kt` (NavHost 4 lines + 4 identical per-destination blocks),
building `pocket-baseline.apk` (library 700ms defaults apply), then restoring
the fixed file byte-for-byte from backup (`PocketNavHost.fixed.kt`) and
rebuilding. Baseline and fixed therefore differ in exactly one variable.
Identical script both runs: fresh install → skip welcome (540,1790) → warm-up
round (4 tabs, 2s gaps) → `gfxinfo reset` → measured round (4 tabs, 1.5s gaps)
→ 2s settle → `gfxinfo` dump.

| Metric (4 switches) | Baseline (700ms defaults) | Fixed (None tabs + 150ms pushes) |
|---|---|---|
| Total frames rendered | 224 | 232 |
| Janky frames | 7 (3.12%) | 9 (3.88%) |
| 50th / 90th / 95th / 99th pct | 19 / 24 / 28 / 65 ms | 18 / 26 / 30 / 85 ms |
| Slow UI thread | 5 | 5 |
| Slow issue draw commands | 5 | 5 |
| Missed Vsync | 0 | 1 |

Verdict: NO measurable difference (within noise). The 700ms crossfade does NOT
dominate frame production — fading two composited layers is GPU-cheap and adds
no jank. Per-switch rendering (~55 frames) is driven by other components
(Phase 7 isolates which).

### Phase 7 — Isolate the residual ~50-frame tail

Method: `gfxinfo reset` → single tap → sample `Total frames rendered` every 1s
until frozen. Three experiments, same device:

- Exp1 (current code, M3 NavigationBar): 54 frames at t+1s, frozen through t+5s.
- Exp2 (diagnostic: M3 bar temporarily replaced by plain `Row` of `IconButton`s,
  same nav logic; build `pocket-noindicator.apk`): 47 frames, frozen.
  Delta Exp1-Exp2 ≈ 7 frames → M3 selection-indicator slide contribution.
- Exp3 (same diagnostic build; re-tap the ALREADY-selected tab = ripple only,
  no navigation, no reload): 44 frames, frozen.

Attribution of one tab switch (~50 frames): ripple ≈ 44 (~80-85%), M3 indicator
slide ≈ 7 (~13%), destination recomposition remainder ≈ 3-5. The ripple is the
named cause of the tail — NOT navigation, NOT the indicator, NOT Room.

CORRECTION (same session, after first writing Phase 7): the obvious
"kill ripple, keep M3 bar" fix via
`CompositionLocalProvider(LocalIndication provides ...)` is TECHNICALLY
IMPOSSIBLE in this codebase, proven two ways:
- API: `LocalIndication` is non-null `Indication` type (`provides null` =
  compile error); `NoIndication` does not exist in compose-bom 2026.05.00
  (unresolved reference); implementing `Indication` via
  `rememberUpdatedInstance`/`IndicationInstance` is a hard compile ERROR
  (deprecated-error API) in this toolchain.
- Bytecode: decompiled `NavigationBarKt$NavigationBarItem$2$indicatorRipple$1`
  from `material3-android-1.4.0/classes.jar` (Gradle cache) shows the item calls
  M3-internal `RippleKt.ripple(...)` DIRECTLY and applies it with
  `Modifier.indication(...)` — it never reads `LocalIndication`. A
  CompositionLocal override would compile (via custom node factory) but
  silently do nothing.
The attempt was fully reverted (working tree back to transition-only diff,
`BUILD SUCCESSFUL`, reinstalled, settle re-verified at 50 frames frozen).
Real options are therefore: (a) keep stock M3 bar + ripple, or (b) replace the
bottom bar with a custom tab row (no ripple, no indicator pill) — a visible
design change requiring reporter sign-off, NOT applied.

Diagnostic strip was fully reverted afterward (fixed file restored from backup,
`BUILD SUCCESSFUL`, reinstalled, relaunched). No diagnostic code remains.

### Phase 8 — WhileSubscribed(5000) reload with realistic dwell

Method: tap Home → sleep 8s (flows go cold) → `gfxinfo reset` → tap Calendar →
sample 1s x4. Result: 65 frames at t+1s, 70 at t+2s, frozen after.
Compared with ~50 rapid-switch: cold-restart penalty ≈ +20 frames (~0.3-0.4s on
this emulator). Real but minor; not the dominant cause.

### Phase 9 — Insights sequential queries

Finding: NOTHING TO FIX — already optimized. Current
`InsightsViewModel.kt:96-114` uses ONE batched query,
`repository.getMonthlyExpenseTotals(firstMonth.atDay(1), currentMonth.atEndOfMonth())`
(`TransactionRepository.kt:45`), mapping 6 months in memory. The predecessor's
"6 sequential `getTotalExpensesSync()`" description is stale relative to the
working tree. No async/await change made (nothing to parallelize).
Side note: `getTotalExpensesSync`/`getTotalIncomeSync`
(`TransactionRepository.kt:32-36`) now have zero call sites — dead-code cleanup
candidate, deliberately left untouched (out of scope).

### Phase 10 — Re-verify and conclude

Final state on device: fixed build (None tab transitions, 150ms push fades, M3
bar restored) reinstalled fresh and launched (`MainActivity` started,
`Installed on 1 device`). Working tree contains only the intended transition
fix (+ prior revert-session changes); diagnostic code fully reverted
(`git diff --stat` on `PocketNavHost.kt` back to 71 insertions).

## 4. Before/after frame-stat table (Phase 6)

See table in Phase 6 above. Bottom line: identical within noise — the fade
removal changes wall-clock animation duration (proven by code + settle tests)
but not frame production or jank on this emulator.

## 5. Answers to Q1-Q5

- Q1 (device vs emulator): BOTH (reporter's answer). My verification is
  emulator-only; device run is pending with the reporter.
- Q2 (what produces the tail): the tap RIPPLE (~44 of ~50 frames), measured by
  Exp3 re-tap isolation. Indicator ≈ 7 frames; recomposition remainder ≈ 3-5.
- Q3 (5000ms cold restart): yes, real, small (+~20 frames). Not dominant.
- Q4 (true before/after): table above — no measurable frame/jank difference.
- Q5 (Insights parallelization): moot — code already uses one batched query.

## 6. Confirmed / ruled-out lists

Confirmed:
- `NavHost` default transitions are `fadeIn/fadeOut(tween(700))` — sourced to
  `platform/frameworks/support/.../navigation-compose/.../NavHost.kt` defaults
  (also exposed as `DefaultNavTransitions` in 2.8.x). The "700ms" figure is now
  sourced, not asserted.
- Per-switch rendering tail is ripple-dominated (Exp3: 44 frames, ripple-only).
- UI thread never the bottleneck (`Slow UI thread: 0-5` across all runs).
- No runaway recomposition (0 frames at idle; frozen counters after settle).

Ruled out:
- Room main-thread I/O (suspend DAOs + `flowOn(Dispatchers.Default)`).
- Per-composition clock calls (`remember`-cached greeting/today).
- M3 indicator as primary cause (only ~7 frames).
- Nav crossfade as frame-production cause (A/B identical).
- Insights sequential queries (already batched).
- Month-picker single month (correct fresh-install behavior, predecessor finding stands).

## 7. Files touched

- `app/src/main/java/in/marxen/pocket/navigation/PocketNavHost.kt`
  (transition fix retained; diagnostic strip + baseline variant were temporary
  and fully reverted — then superseded by Phase 11 custom `PocketBottomBar`)
- Phase 11/12 changes (same session, after §7 was written):
  `ui/calendar/CalendarScreen.kt` (grid keys, stable handler),
  `ui/calendar/CalendarViewModel.kt` (`@Immutable`, Eagerly sharing),
  `ui/home/HomeScreen.kt` (`BalanceCard` extraction),
  `navigation/PocketNavHost.kt` (custom bottom bar),
  `docs/superpowers/plans/2026-09-23-calendar-load-once.md` (new plan doc).
- Scratch artifacts live outside the repo: `Temp/opencode/pocket-baseline.apk`,
  `pocket-noindicator.apk`, `PocketNavHost.fixed.kt`, `ui-*.xml`.

## 8. Residual risks and recommendation

1. M3 ripple path superseded: the custom `PocketBottomBar` (Phase 11) removed
   ripple and indicator animation entirely — switches now cost ~4 frames.
2. Emulator frames run ~20ms median (GPU-bound); everything here will look
   faster on hardware. Device-side sign-off still required (acceptance: reporter
   confirms switches feel instant on their phone).
3. `WhileSubscribed(5000)` cold-restart accepted as-is EXCEPT Calendar, which now
   uses `SharingStarted.Eagerly` (Phase 12). Retune Home/Insights only if device
   testing flags return-visit lag there.

Plain verdict: YES on frame production (switches render ~4 frames and stop —
nothing left to animate); the only open item is per-frame composition cost on
weak hardware, to be confirmed by the reporter's physical-device check.

## 9. Phase 11 — Calendar, Home card, custom bottom bar (same session)

Reporter follow-up named three concrete complaints: slow Calendar rendering,
lagging Home green card, and a request for a good bottom bar with less ripple.
Audit + fixes (all in working tree, `BUILD SUCCESSFUL`):

1. Calendar (`CalendarScreen.kt`, `CalendarViewModel.kt`):
   - Nested `LazyVerticalGrid` inside `LazyColumn` had NO `key` — every date tap
     recomposed all 42 cells. Added `key = { it.date }`.
   - Per-cell `onClick = { viewModel.selectDate(day.date) }` lambda was unstable
     (defeated skipping): hoisted to
     `remember(viewModel) { { date -> viewModel.selectDate(date) } }`, `DayCell`
     now takes `onDateSelected: (LocalDate) -> Unit`.
   - `DaySummary` marked `@Immutable` (all props effectively immutable) so keyed
     cells skip when unchanged.
   - `monthlyData`/`flowOn(Dispatchers.Default)` split already present in tree;
     kept. Duplicate imports from editing cleaned.
2. Home green card (`HomeScreen.kt`): the `PocketGreen` balance `Card` (with
   `displayLarge` emoji glyph) sat inline in the `LazyColumn` scope, so every
   one of the ~7 chained `uiState` emissions recomposed it. Extracted to
   `BalanceCard(totalExpensesFormatted, totalIncomeFormatted)` — String-only
   params, skips unless amounts change.
3. Custom bottom bar (`PocketNavHost.kt`): M3 `NavigationBar`/`NavigationBarItem`
   replaced with `PocketBottomBar` — same icons/labels/colors (ActiveColor
   `#1A5C38`, InactiveColor gray), static pill behind selected icon, white
   container + divider, identical nav logic (saveState/restoreState/
   launchSingleTop). Tabs use `Modifier.clickable(interactionSource =
   remember { MutableInteractionSource() }, indication = null)` — note: the
   `indication` overload REQUIRES an explicit `interactionSource` argument
   (compile error otherwise).

Final measurement (fresh install, custom bar, same tap script):
- Single switch: **4 frames, then frozen** (was ~50). Tail eliminated.
- 4-switch round: 12 frames total (~3/switch). BUT 100% janky, 50th pct 85ms,
  `Slow UI thread: 12` — every remaining frame does full-subtree composition
  work slowly on the emulator CPU.
- Revised attribution: ripple was ~85% of the *tail*; what remains is a tiny
  number of expensive composition frames, imperceptible on hardware
  (3-4 frames at ~8ms) but stretched on this emulator (~85ms each).

Revised verdict: YES on frame production (switches render ~4 frames and stop —
nothing left to animate); the only open item is per-frame composition cost on
weak hardware, to be confirmed by the reporter's physical-device check.

## 10. Phase 12 — Calendar load-once (same session)

Reporter hunch: Calendar still feels laggy on switches; proposal: load it once.
Plan: `docs/superpowers/plans/2026-09-23-calendar-load-once.md`.
Change (`CalendarViewModel.kt`, 2 lines): both `stateIn` sharing strategies
`SharingStarted.WhileSubscribed(5000)` → `SharingStarted.Eagerly`. The ViewModel
is retained by nav saveState, so month data loads once on first visit and
revisits serve the cached value; Room upstream flows stay collected, so DB
writes still push updates (freshness preserved by construction — same emission
path as before, only the 5s stop-timeout removed).
Measurement (dwell 8s on Home → return to Calendar): **4 frames, frozen** —
the Phase 8 +20-frame cold-restart penalty is gone.
Residual: live-update-after-add not drive-tested here (mechanism unchanged);
reporter will notice in daily use if Calendar ever goes stale.

## 11. Phase 13 — Production test pass (same session)

Plan: `docs/superpowers/plans/2026-09-23-production-test-pass.md`. Fresh install
on emulator-5554, every screen driven via scripted taps guided by `uiautomator`
dumps, state asserted from dump text/bounds after each action.

Results (all PASS unless noted):
- T1 env: boot 1, uninstall Success, install + launch OK.
- T2 splash: system splash = round icon on beige (screencap verified); in-app
  splash auto-advances to Welcome.
- T3 home: greeting headlineSmall Bold, `SEPTEMBER 2026` month row, ₹0.00
  settles with no loop; month picker = exactly 1 entry + Cancel on fresh DB;
  See-all → Calendar; FAB → AddExpense.
- T4 add: empty save blocked (stays on screen; OBSERVATION: no visible error
  message shown — consider adding one); Food/Lunch/250 saved, auto-return,
  Today row shows subcategory.
- T5 edit: prefilled 250.0 + Food + chips; edited to 300, Home updated.
- T6 calendar: 42-cell grid, prev/next arrows exact (Sep→Aug→Sep→Oct verified),
  rapid date taps settle on last tap, 8s-dwell revisit = 4 frames frozen
  (Eagerly cache), `+ Add Expense` CTA opens Add screen.
- T7 insights: totals/donut/trend/insight card correct; See-all chain
  preserves `Food` header centered; back + system-back chain correct; Income
  (₹0.00) and Trends tabs render.
- T8 settings: only Theme/Backup+Restore/Export/Manage/Version present, no
  stubs; Dark theme applies app-wide (reverted to Light); Manage Data dialog
  opens, Cancel preserves data (₹300 intact); Backup + Export CSV both launch
  system file picker (`com.google.android.documentsui`), cancelled cleanly.
- T9 perf: 4-switch round = 12 frames (~3/switch); jank% high but purely
  emulator-CPU composition cost (GPU percentiles 13-17ms); frame COUNT matches
  prior baseline — no regression.
- T10 follow-up: ONE issue found — Insights month picker listed 12 months,
  violating the standing 6-month directive. Fixed
  (`InsightsScreen.kt`: `0L..11L` → `0L..5L`), rebuilt, reinstalled,
  re-verified: exactly 6 entries (September–April 2026).
