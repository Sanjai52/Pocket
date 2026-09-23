# Production Test Pass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rigorously verify the Pocket app on emulator across every screen, CTA, and rendering path before production, and fix anything found.

**Architecture:** Fresh install on Pixel_6 AVD (`emulator-5554`), drive all UI via `adb shell input tap` / `input text` guided by `uiautomator` dumps, assert screen state from dump text/bounds after each action, and capture `dumpsys gfxinfo` settle stats per transition. Issues found loop back through fix → rebuild → reinstall → re-verify.

**Tech Stack:** Android SDK platform-tools (adb, uiautomator), `./gradlew installDebug`, package `in.marxen.pocket`.

**Spec:** User directive 2026-09-23: uninstall current, install latest, test every screen/button/CTA/rendering for lag and load issues (including Calendar load-once caching), follow up and redo on any issue found.

## Global Constraints

- NEVER commit, amend, or push (user standing rule overrides any commit steps).
- NEVER confirm destructive dialogs (Manage Data delete) — open and cancel only.
- Fresh-install state for pass 1; test data created mid-pass must be accounted for in later tasks.
- All shell commands run with `MSYS_NO_PATHCONV=1` (Git Bash path mangling breaks adb remote paths otherwise).
- UI dumps go to `C:/Users/sanja/AppData/Local/Temp/opencode/ui-<name>.xml`; parse with `grep -o 'text="..."[^>]*bounds="[^"]*"'`.
- Bottom-tab centers (custom bar, 1080x2400): Home (135,2278), Calendar (405,2278), Insights (675,2278), Settings (945,2278). Re-dump if layout shifts.

---

### Task 1: Environment setup (uninstall → install → launch → welcome)

**Files:** none (device ops only).

- [ ] **Step 1: Boot-check and uninstall**

```bash
adb devices
adb -s emulator-5554 shell getprop sys.boot_completed   # expect: 1
adb -s emulator-5554 uninstall in.marxen.pocket          # expect: Success
```

- [ ] **Step 2: Install current tree and launch**

```bash
./gradlew installDebug 2>&1 | tail -3   # expect: Installed on 1 device + BUILD SUCCESSFUL
adb -s emulator-5554 shell am start -n in.marxen.pocket/.MainActivity
```

- [ ] **Step 3: Pass welcome via Skip**

```bash
sleep 4
adb -s emulator-5554 shell input tap 540 1790   # "Skip for now" center
sleep 2
# dump UI, expect bottom-nav content-desc="Home" present
```

### Task 2: Splash screens (system + in-app)

**Files:** none (observe only).

- [ ] **Step 1: Cold-start system splash capture**

```bash
adb -s emulator-5554 shell am force-stop in.marxen.pocket
adb -s emulator-5554 shell am start -n in.marxen.pocket/.MainActivity
sleep 1
adb -s emulator-5554 exec-out screencap -p > C:/Users/sanja/AppData/Local/Temp/opencode/splash-prod.png
```

Expected: round wallet icon centered on beige (`#F5F0E6`), no square/lime box. View the PNG to confirm.

- [ ] **Step 2: In-app splash**

Wait out the splash (~2s), dump UI, expect `Pocket` title + `splash_wallet` image + taglines, then auto-advance to Home. Skip welcome again if shown.

### Task 3: Home screen (rendering, greeting, card, month picker, CTAs)

**Files (fix only if issues):** `app/src/main/java/in/marxen/pocket/ui/home/HomeScreen.kt`

- [ ] **Step 1: Greeting + month header**

Dump UI, expect: `Good <morning|afternoon|evening|night>,` in headlineSmall Bold (larger than the `SEPTEMBER 2026` titleMedium month text), user name headline, month row with `▾`.

- [ ] **Step 2: BalanceCard initial values**

On fresh DB expect `₹0.00` spent/income (no pop-in loop: values must settle, screen must go idle — confirm via `gfxinfo` settle ≤ ~10 frames after load).

- [ ] **Step 3: Month picker**

Tap month row, dump dialog, expect exactly one entry (current month) on fresh DB with Cancel. Dismiss via Cancel.

- [ ] **Step 4: CTAs**

Tap `See all` → expect Calendar screen. Navigate back via Home tab. Tap FAB (`Add Expense` content-desc) → expect AddExpense screen. (Full Add flow is Task 4; just verify navigation here, then back out.)

### Task 4: AddExpense (fields, validation, save)

**Files (fix only if issues):** `app/src/main/java/in/marxen/pocket/ui/expense/AddExpenseScreen.kt`, `AddExpenseViewModel.kt`

- [ ] **Step 1: Validation blocks empty save**

With amount empty, tap `Save Expense`, dump UI, expect: still on AddExpense (no navigation), amount error shown.

- [ ] **Step 2: Full save (Food/Lunch/250)**

Tap amount field, `adb shell input text 250`, close keyboard (`input keyevent 4`), tap Food category, tap Lunch subcategory chip, tap Save. Expect: returns to previous screen automatically.

- [ ] **Step 3: Data appears**

Go Home, dump UI, expect `₹250.00` (or test amount) in Today list with `Lunch` subcategory line.

### Task 5: Edit flow

- [ ] **Step 1: Open transaction for edit**

On Home Today list, tap the test transaction row, expect AddExpense screen in edit mode with amount prefilled `250`.

- [ ] **Step 2: Modify and save**

Change amount to `300` (tap field, clear via keyevents or select-all+type — if unreliable, note as manual), Save, expect Home shows updated amount. If field editing is flaky under automation, record exact behavior and flag for manual check instead of forcing it.

### Task 6: Calendar (render, month nav, date select, cache)

**Files (fix only if issues):** `app/src/main/java/in/marxen/pocket/ui/calendar/CalendarScreen.kt`, `CalendarViewModel.kt`

- [ ] **Step 1: Grid render**

Open Calendar tab, dump UI, expect: month title, 7 weekday headers, ~42 day cells, selected-day summary, `+ Add Expense` CTA. Screenshot-check for overlaps (view screencap if layout looks suspect in dump).

- [ ] **Step 2: Month prev/next**

Tap `<` arrow, expect previous month title; tap `>` twice, expect current month back. Each switch must settle (spot-check `gfxinfo` settle ≤ ~15 frames).

- [ ] **Step 3: Date select**

Tap a mid-month date cell, expect summary header + total update to that date and selection ring moves (dump: selected date text changes). Tap 3 different dates rapidly, expect no crash, final state matches last tap.

- [ ] **Step 4: Load-once cache (dwell test)**

Tap Home tab, sleep 8s, `gfxinfo reset`, tap Calendar tab, sample `Total frames rendered` every 1s x4. Expected: settles at ≤ ~10 frames (Eagerly cache — no cold-restart penalty; Phase 8 baseline was 70).

- [ ] **Step 5: `+ Add Expense` CTA**

Tap it, expect AddExpense screen opens. Back out without saving.

### Task 7: Insights (tabs, See-all chain, detail header)

**Files (fix only if issues):** `app/src/main/java/in/marxen/pocket/ui/insights/InsightsScreen.kt`, `CategoryListScreen.kt`, `CategoryDetailScreen.kt`

- [ ] **Step 1: Spending tab**

Open Insights, expect: Total Spending card = test total, `By Category` with Food row + donut, Monthly Trend bars, insight card.

- [ ] **Step 2: See-all chain**

Tap `By Category` → `See all` → expect `Spending by Category` header (back button inline + centered title). Tap Food row → expect detail header reading exactly `Food` (not `Category`), centered, with month subtitle; subcategory donut + `Lunch` row present.

- [ ] **Step 3: Back navigation**

Back button on detail → expect CategoryList. Back button on list → expect Insights. System back (`input keyevent 4`) from detail → expect CategoryList (no crash, no blank screen).

- [ ] **Step 4: Income + Trends tabs**

Tap Income tab, expect Total Income card. Tap Trends tab, expect Monthly Spending Trend chart. No empty/crash states.

- [ ] **Step 5: Insights month picker**

Tap month row, expect dialog listing last 6 months (or fewer if DB is younger), Cancel dismisses.

### Task 8: Settings (dialogs, stub rows gone, no dead CTAs)

**Files (fix only if issues):** `app/src/main/java/in/marxen/pocket/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Row inventory**

Dump Settings, expect ONLY: Theme, Backup & Restore (+Backup/Restore buttons), Export CSV, Manage Data, Version. Expect ABSENT: App Icon, Categories, Payment Methods, Recurring Expenses, Budgets, App Lock, Hide Amounts. Any stub row with no-op `onClick` is a FAIL.

- [ ] **Step 2: Theme dialog**

Tap Theme → expect System default/Light/Dark options. Select Dark → expect app theme changes (spot-check Home background darkens via screencap). Revert to Light afterward to leave clean state.

- [ ] **Step 3: Manage Data dialog (DO NOT CONFIRM)**

Tap Manage Data → expect `Delete all data?` dialog → tap Cancel → expect dialog gone, data intact (spot-check Home totals unchanged).

- [ ] **Step 4: Backup/Export launchers**

Tap Backup → expect system file-picker appears (SAF). Cancel it via system back. Same for Export CSV. (Full file round-trip is manual-only; launching + clean cancel is the automated bar.)

### Task 9: Performance sweep (settle per switch + round stats)

- [ ] **Step 1: Per-switch settle**

`gfxinfo reset` → tap each tab once → sample `Total frames rendered` 1s x3. Expected per switch: ≤ ~10 frames then frozen (custom bar era). Anything sustained = FAIL → diagnose per chronicle method.

- [ ] **Step 2: Full round stats**

Reset → 4-tab round (2s gaps) → dump `Total frames rendered|Janky frames|percentile|Slow UI thread`. Record numbers in follow-up notes. Emulator-absolute jank is expected (slow CPU); regression signal is frame COUNT growth vs the ~12-16 baseline, not jank%.

### Task 10: Follow-up loop

- [ ] **Step 1: Triage findings**

Every FAIL above gets: reproduce twice → root-cause (read code, no guessing) → minimal fix → rebuild → reinstall → re-run ONLY its task steps → record before/after.
- [ ] **Step 2: Update chronicles**

Append outcomes to `2026-09-23-page-switch-lag-followup-chronicle.md` (new `## 11. Phase 13 — production pass` section or dated equivalent).

## Self-Review

1. Spec coverage: reinstall (T1) ✓, every screen (T2 splash, T3 home, T4-5 expense, T6 calendar, T7 insights, T8 settings) ✓, every CTA enumerated with coordinates strategy ✓, rendering checks via dumps/screencaps ✓, lag/load via gfxinfo (T9) ✓, calendar cache (T6-Step 4) ✓, follow-up+redo loop (T10) ✓.
2. Placeholder scan: all commands exact; tap coordinates either exact or dump-derived with grep patterns; no TBD/TODO.
3. Type consistency: N/A (test plan, no code interfaces); adb package/activity IDs match manifest (`in.marxen.pocket/.MainActivity`).
