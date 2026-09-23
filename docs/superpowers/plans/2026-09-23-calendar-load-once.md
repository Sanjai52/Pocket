# Calendar Load-Once Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Calendar tab revisits instant by loading month data once per ViewModel lifetime instead of re-querying on every return.

**Architecture:** Change the two `stateIn` sharing strategies in `CalendarViewModel` from `SharingStarted.WhileSubscribed(5000)` to `SharingStarted.Eagerly`. The ViewModel is retained across tab switches by nav `saveState`/`restoreState`, so Eagerly = load once on first visit, serve cached value on every revisit. Room upstream flows still push updates on DB writes, so freshness is preserved. No signature, UI, or navigation changes.

**Tech Stack:** Kotlin, kotlinx-coroutines Flow (`SharingStarted`), Room Flow queries (unchanged), Jetpack Compose (untouched).

**Spec:** User hunch (2026-09-23 session): "calendar feels laggy when switching pages — can we just load the calendar once so there is no lag or load as well." Phase 8 measured a +~20-frame cold-restart penalty when returning after >5s dwell; this plan eliminates that path for Calendar.

## Global Constraints

- No new dependencies, no new files, no UI redesign.
- Month switching (`previousMonth`/`nextMonth`), date selection, and selected-day transactions must keep working exactly as now.
- New transactions added while on other tabs must still appear when returning to Calendar (Room re-emission, must verify by reasoning: Eagerly keeps collection alive, so yes).
- Do not change `HomeViewModel` / `InsightsViewModel` sharing (out of scope; separate decision).

---

### Task 1: Switch Calendar flows to Eagerly sharing

**Files:**
- Modify: `app/src/main/java/in/marxen/pocket/ui/calendar/CalendarViewModel.kt:103-104` (monthlyData `stateIn`)
- Modify: `app/src/main/java/in/marxen/pocket/ui/calendar/CalendarViewModel.kt:131` (uiState `stateIn`)

**Interfaces:**
- Consumes: nothing new; existing `viewModelScope`, `SharingStarted`, `MonthlyCalendarData`, `CalendarUiState` unchanged.
- Produces: same `StateFlow<MonthlyCalendarData?>` and `StateFlow<CalendarUiState>` types with identical initial values (`null`, `CalendarUiState()`); only collection lifetime changes.

- [x] **Step 1: Edit the two `stateIn` calls**

```kotlin
// monthlyData — BEFORE:
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
// monthlyData — AFTER:
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

// uiState — BEFORE:
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalendarUiState())
// uiState — AFTER:
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CalendarUiState())
```

- [x] **Step 2: Build to verify it compiles**

Run: `./gradlew assembleDebug` from repo root.
Expected: `BUILD SUCCESSFUL` (no API change, so no call-site breakage possible).

- [x] **Step 3: Install and measure (replaces unit test — see note)**

No JVM test harness exists for ViewModel StateFlow sharing in this repo (only
plain `junit` dep, zero existing tests; Room flows need instrumentation), so
verification is behavioral on the Pixel_6 AVD (`emulator-5554`):
1. `adb -s emulator-5554 uninstall in.marxen.pocket; ./gradlew installDebug`
2. Launch, skip welcome (tap 540,1790), warm-up all tabs.
3. Dwell test: tap Home (135,2278), sleep 8s, `gfxinfo reset`, tap Calendar
   (405,2278), sample `Total frames rendered` every 1s x4.
Expected: settles at ~50 frames like the rapid-switch case (Phase 8 baseline
was 70 after 8s dwell) — i.e. the +~20-frame cold-restart penalty is gone.

- [x] **Step 4: Freshness sanity check**

On emulator: add an expense dated in the currently shown Calendar month (via
FAB), return to Calendar, confirm the day dot/total appears without restart.
Expected: visible immediately (Eagerly keeps Room collection alive).

- [x] **Step 5: Append results to `2026-09-23-page-switch-lag-followup-chronicle.md`**

Record dwell-revisit frames before/after and freshness check outcome.

## Self-Review

1. Spec coverage: "load once, no lag/load on switches" → Eagerly sharing does
   exactly this for the ViewModel lifetime; composition re-entry still
   recomposes once (unavoidable, cheap with keys + `@Immutable` cells).
   Freshness requirement → covered by Step 4.
2. Placeholder scan: no TBD/TODO; exact lines, exact commands, exact expected values.
3. Type consistency: no signature changes anywhere; only two enum arguments change.
