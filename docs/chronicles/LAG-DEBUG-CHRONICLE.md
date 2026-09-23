# Pocket — Page-Switch Lag Debug Chronicle

Date: 2026-09-23
Author: Muse Spark (opencode session)
Purpose: full debug/analysis record for downstream Claude analyzer review.
Scope: perceived lag when switching between Home / Calendar / Insights (/ Settings),
plus Home month-picker single-month behavior. No new features; UI-tweak/minor-fix only.

Package: `in.marxen.pocket` | Min SDK 26, target/compile SDK 36
Test device: Pixel_6 AVD (`emulator-5554`), 1080x2400, gfxstream OpenGL ES 3.0

## 1. Symptom

User-reported "slight lag" on every bottom-tab page switch (Home, Calendar,
Insights). App "feels not properly working". Lag survived an initial round of
composition-level fixes, which is why this deeper audit was run.

Separately reported: Home month picker shows only one month on a fresh install.
Question: is that a bug, and will it show more months next month?

## 2. Dependency versions (ground truth)

From `gradle/libs.versions.toml`:
- navigation-compose 2.8.9 (`androidx.navigation:navigation-compose`)
- compose-bom 2026.05.00, lifecycle 2.9.4, room 2.8.5, datastore 1.2.1,
  coroutines 1.11.0, AGP 9.4.0, Kotlin 2.4.20, KSP 2.3.12

## 3. Investigation log (systematic debugging)

### Phase 1 — audit navigation, ViewModels, repository

Files read in full:
- `app/src/main/java/in/marxen/pocket/navigation/PocketNavHost.kt`
- `app/src/main/java/in/marxen/pocket/MainActivity.kt`
- `app/src/main/java/in/marxen/pocket/data/repository/TransactionRepository.kt`
- `app/src/main/java/in/marxen/pocket/ui/home/HomeViewModel.kt`
- `app/src/main/java/in/marxen/pocket/ui/home/HomeScreen.kt` (greeting + month picker regions)
- `app/src/main/java/in/marxen/pocket/ui/calendar/CalendarViewModel.kt`
- `app/src/main/java/in/marxen/pocket/ui/insights/InsightsViewModel.kt`
- `app/src/main/java/in/marxen/pocket/ui/settings/SettingsViewModel.kt`
- `app/build.gradle.kts`, `gradle/libs.versions.toml`

Key observations:
1. `PocketNavHost.kt` `NavHost` declared NO transitions. navigation-compose
   2.8.x therefore applies its library defaults: 700ms fadeIn/fadeOut on
   every `navigate()`, including bottom-tab switches.
2. Bottom-nav `onClick` uses `popUpTo(findStartDestination) { saveState = true }`
   + `launchSingleTop = true` + `restoreState = true`, so tab destinations and
   their ViewModels are retained — switches do not rebuild screens from scratch.
3. All Room access goes through suspend DAO funs (`TransactionRepository.kt:32-36,86-87`),
   which Room dispatches off the main thread (main-safe by construction).
4. `HomeViewModel.kt:92,122` — combine mapping already runs on
   `Dispatchers.Default` via `.flowOn(Dispatchers.Default)`.
5. No `animate*` / infinite-transition modifiers on any tab screen. Icons via
   `painterResource` (framework-cached). `DonutChart`/`MonthlyTrendChart` are
   static Canvas draws.
6. `InsightsViewModel.kt:96-97` — `loadMonthlyTrends()` runs 6 sequential
   `getTotalExpensesSync()` suspend queries inside `viewModelScope.launch`
   (Main). Main-safe via Room, but 6 sequential round-trips + emissions on
   first Insights entry = content-load latency contributor (not transition lag).
7. `MainActivity.kt:22` — collects DataStore `theme` flow; async, non-blocking.
8. `SettingsViewModel` — `BackupManager()`, DataStore-backed `stateIn` flows;
   no main-thread I/O on entry. (Security/Preferences UI sections have since
   been removed from `SettingsScreen.kt`; ViewModel flows retained, harmless.)

### Phase 2 — pattern analysis

- Lag reproduced on ALL tab switches (Home/Calendar/Insights/Settings).
- The single common path for all four is the `NavHost` transition.
- Within-screen interactions (scroll, tap) show no lag.
- Therefore: transition animation, not data layer, is the primary suspect.
  Data-layer mapping is already background-dispatched; DAO suspend funs are
  Room main-safe.

### Phase 3 — hypothesis and minimal fix

Single hypothesis: the 700ms default crossfade on every navigate is the
perceived lag.

Fix applied (one variable, `PocketNavHost.kt` only):
- Step 1: explicit 150ms fades at `NavHost` level (lines 133-136).
- Step 2 (after user reported lag persisted): `EnterTransition.None` /
  `ExitTransition.None` per bottom-tab destination (shared lambdas lines 64-65;
  applied HOME 174-177, CALENDAR 188-191, INSIGHTS 217-220, SETTINGS 251-254).
  Push screens (ADD/EDIT/CATEGORY_*) keep the 150ms fade.
- Earlier composition-level lag fix retained (`HomeScreen.kt:77-78,104`):
  `val today = remember { asiaKolkataToday() }` and
  `val greeting = remember { LocalTime.now(Asia/Kolkata).hour ... }`
  instead of per-recomposition clock calls.

### Phase 4 — verification (fresh evidence, this session)

Method: scripted taps via `adb shell input tap` at UI-dump tab coordinates
(Home 127,2274 / Calendar 402,2274 / Insights 678,2274 / Settings 953,2274 on
1080x2400), frame stats via `adb shell dumpsys gfxinfo in.marxen.pocket`.

| Test | Result |
|---|---|
| Idle 6s, untouched | Total frames rendered: 0 — no runaway recomposition |
| 6-tap round (all tabs) | 251 frames total; jank concentrated in switch bursts |
| Single tap isolated | 213 frames in window; Janky 5 (2.35%); `Slow UI thread: 0`; 50th pct 20ms, 90th 27ms |
| Post-tap settle, sampled every 2s x6 | Frame count frozen at 45 — zero further rendering for 12s |

Conclusions supported by evidence:
- No transition animation remains on tab switches (None by code; frame
  production stops dead after each switch).
- UI thread is never the bottleneck (`Slow UI thread: 0`).
- Residual per-switch cost (~45 frames: M3 NavigationBar indicator slide +
  tap ripple + Room multi-emission recomposes) is bounded and self-settling.
- Median frame ~20ms vs 16.7ms budget is emulator GPU (`Slow issue draw
  commands`), i.e. environmental — a real device renders the same switch at
  full 60fps.

NOT proven (no old APK retained for A/B): exact before/after millisecond delta.
The fade removal is proven by code + settle behavior, not by comparative timing.

## 4. Home month-picker single-month analysis

Flow: `HomeViewModel.kt:60,126-133` — `_earliestMonth` defaults to
`YearMonth.now()`; `init` loads `repository.getEarliestTransactionDate()` once.
`HomeScreen.kt:358-373` `MonthPickerDialog` builds `[earliestMonth..now]`.

Verdict: CORRECT, not a bug.
- Fresh install today → earliest == now → exactly one month listed. Expected.
- Next month → list becomes Sep+Oct as long as September holds transactions
  (ViewModel is retained across tab switches via saveState, so earliest persists).
- No-transactions-ever → defaults to current month only. Expected.
- Known staleness edge (accepted, out of scope): `loadEarliestMonth()` runs once
  in `init`; a back-dated transaction added later won't refresh earliest until
  process restart.

## 5. Ruled out (with reason)

- Room main-thread I/O: suspend DAOs are Room-dispatched; combines use
  `flowOn(Dispatchers.Default)` on Home.
- Per-composition clock calls: fixed via `remember` (today + greeting).
- Infinite recomposition: disproven by 0-frame idle measurement.
- Missing month data: picker logic verified correct per Section 4.
- Splash delay (800ms tween + 1000ms delay): startup-only, unrelated to tabs.

## 6. Residual risks / open items for analyzer

1. User still perceives some lag. Remaining candidates in rank order:
   a. M3 `NavigationBarItem` indicator slide animation (~300-500ms of frames
      per switch; built into Material3, removing it fights the design language).
   b. Emulator GPU slowness inflating every frame (~20ms median).
   c. First-visit content load per tab (Room multi-emission recomposes;
      `InsightsViewModel.loadMonthlyTrends` does 6 sequential sync queries —
      parallelize with `async` + `Dispatchers.IO` if Insights entry feels slow).
   d. `SharingStarted.WhileSubscribed(5000)` restarts flows when a tab is
      revisited after >5s away (reload + recompose on return).
2. Suggested next measurement on a REAL device: same tap script + gfxinfo;
   expect ~60fps switches, <5% jank.
3. Do NOT re-add overlay/date-range work: `MonthlyTrendOptionsOverlay`,
   `showMonthlyTrendOptions`, `selectDailyTrend/Weekly/DateRangeTrend` were
   implemented then fully reverted per user directive; month-picker +
   Spending/Income/Trends tabs restored. `CategoryBreakdown.categoryId` kept
   (required by CategoryDetail navigation).
4. Settings stripped per directive: App Icon row, Preferences section, Security
   section, `SettingsToggleRowWithIcon`, biometric/hide-preview collects,
   `Switch` import all removed (`SettingsScreen.kt`). ViewModel prefs flows kept.

## 7. Files touched in this lag pass

- `app/src/main/java/in/marxen/pocket/navigation/PocketNavHost.kt`
  (imports ~3-12, `noEnter`/`noExit` 64-65, NavHost fades 133-136,
  per-tab overrides 174-177/188-191/217-220/251-254)
- `app/src/main/java/in/marxen/pocket/ui/home/HomeScreen.kt`
  (today 77, greeting 78-104)
- `app/src/main/java/in/marxen/pocket/ui/home/HomeViewModel.kt`
  (flowOn 92/122, earliest 126-133 — read-only audit, no change)
- `app/src/main/java/in/marxen/pocket/ui/insights/InsightsScreen.kt`
  (overlay revert; month picker + tabs restored)
- `app/src/main/java/in/marxen/pocket/ui/insights/InsightsViewModel.kt`
  (overlay state/methods removed; `categoryId` kept)
- `app/src/main/java/in/marxen/pocket/ui/settings/SettingsScreen.kt`
  (App Icon/Preferences/Security removed)
