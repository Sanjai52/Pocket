# P2 Core Transactions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement add/edit/delete expenses, seed default categories, wire ViewModels, build Home summary + calendar day-list + add-expense form.

**Architecture:** Manual DI via PocketApplication (no Hilt). One ViewModel per screen using `viewModel()` composable. Repository flows observed by Compose via `collectAsStateWithLifecycle`.

**Tech Stack:** Kotlin 2.4.20, Compose BOM 2026.05.00, Room 2.8.5, DataStore 1.2.1, Navigation 2.8.9, Lifecycle 2.9.4.

**Spec:** [PRD.md](../../../PRD.md) sections 6-8 (Splash/Home/Add), 11-13 (Expense/Income/Categories), §29 (Architecture).

## Global Constraints

- Money as integer paise (`Long`), never Float/Double.
- User-facing dates grouped by `Asia/Kolkata LocalDate`.
- Single app module, package `in.marxen.pocket`.
- No INTERNET permission.
- Categories with historical transactions must be hidden, not deleted.
- Amount input: up to 2 decimal places, `en-IN` grouping.

---

### Task 1: Manual DI + Category Seed Data

**Files:**
- Modify: `app/src/main/java/in/marxen/pocket/PocketApplication.kt`
- Create: `app/src/main/java/in/marxen/pocket/data/local/SeedData.kt`

**Interfaces:**
- Consumes: `PocketDatabase`, `PocketPrefs`.
- Produces: `AppContainer` singleton providing `TransactionRepository` and `PocketPrefs`.

- [ ] **Step 1: Write AppContainer and seed callback**

```kotlin
// PocketApplication.kt
class PocketApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

// AppContainer.kt
class AppContainer(context: Context) {
    private val database = PocketDatabase.getInstance(context)
    val repository = TransactionRepository(database.transactionDao(), database.categoryDao())
    val prefs = PocketPrefs(context)
}
```

`SeedData.kt` contains a `RoomDatabase.Callback` that inserts 11 default categories on `onCreate`:
Food, Groceries, Transport, Shopping, Bills, Entertainment, Health, Education, Travel, Personal, Other.

- [ ] **Step 2: Register seed callback in PocketDatabase and verify build**

Add callback to `PocketDatabase.Builder`. Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/in/marxen/pocket/PocketApplication.kt app/src/main/java/in/marxen/pocket/data/local/AppContainer.kt app/src/main/java/in/marxen/pocket/data/local/SeedData.kt app/src/main/java/in/marxen/pocket/data/local/PocketDatabase.kt
git commit -m "feat: add manual DI container and seed default categories"
```

### Task 2: Home ViewModel + Summary

**Files:**
- Create: `app/src/main/java/in/marxen/pocket/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/in/marxen/pocket/ui/home/HomeScreen.kt`

**Interfaces:**
- Consumes: `TransactionRepository`, `PocketPrefs`.
- Produces: `HomeUiState` (month, totalExpenses, totalIncome, balance, todayTransactions, selectedDate).

- [ ] **Step 1: Write HomeViewModel with month totals**

ViewModel exposes `StateFlow<HomeUiState>` with current month totals and today's transactions list.

- [ ] **Step 2: Wire HomeViewModel into HomeScreen and verify**

HomeScreen shows: greeting, month label, ₹spent, ₹income, ₹remaining, today's transactions list with category icon + merchant + amount.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/in/marxen/pocket/ui/home/
git commit -m "feat: implement home screen with monthly summary"
```

### Task 3: Add Expense Form

**Files:**
- Create: `app/src/main/java/in/marxen/pocket/ui/expense/AddExpenseViewModel.kt`
- Modify: `app/src/main/java/in/marxen/pocket/ui/expense/AddExpenseScreen.kt`

**Interfaces:**
- Consumes: `TransactionRepository`, `PocketPrefs`.
- Produces: `AddExpenseUiState` (amount, categoryId, date, note, merchant, categories).

- [ ] **Step 1: Write AddExpenseViewModel**

ViewModel holds form state. Validate amount > 0 and category selected. `save()` calls `repository.insertTransaction()` then emits saved event.

- [ ] **Step 2: Write AddExpenseScreen composable**

Form: Amount text field (₹, decimal keyboard), category grid (3 columns, tap to select), date picker (defaults to selected calendar date or today), expandable "More details" (note + merchant), Save button.

- [ ] **Step 3: Wire navigation (add from FAB + edit existing)**

Add route `expense/edit/{id}` for editing. Pass transaction ID via nav args. ViewModel loads existing transaction by ID in edit mode.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/in/marxen/pocket/ui/expense/
git commit -m "feat: implement add/edit expense form"
```

### Task 4: Calendar Screen

**Files:**
- Create: `app/src/main/java/in/marxen/pocket/ui/calendar/CalendarViewModel.kt`
- Modify: `app/src/main/java/in/marxen/pocket/ui/calendar/CalendarScreen.kt`

**Interfaces:**
- Consumes: `TransactionRepository`.
- Produces: `CalendarUiState` (month, selectedDate, dayExpenses map, selectedDayTransactions).

- [ ] **Step 1: Write CalendarViewModel**

ViewModel loads monthly transaction data, groups by date, provides per-day totals.

- [ ] **Step 2: Write CalendarScreen composable**

Month grid calendar (7 columns, Sun-Sat), each day shows dot/amount if expenses exist, selected day highlighted. Below calendar: selected day's transaction list.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/in/marxen/pocket/ui/calendar/
git commit -m "feat: implement calendar screen with day-level expenses"
```

### Task 5: Transaction Edit + Delete

**Files:**
- Modify: `app/src/main/java/in/marxen/pocket/ui/home/HomeScreen.kt`
- Modify: `app/src/main/java/in/marxen/pocket/ui/calendar/CalendarScreen.kt`
- Modify: `app/src/main/java/in/marxen/pocket/navigation/PocketNavHost.kt`

**Interfaces:**
- Consumes: `HomeViewModel`, `CalendarViewModel`.
- Produces: Swipe-to-delete, tap-to-edit navigation.

- [ ] **Step 1: Add swipe-to-delete with confirmation**

Transaction list items support swipe-to-delete with undo snackbar. Confirm before permanent delete.

- [ ] **Step 2: Add tap-to-edit navigation**

Tapping a transaction navigates to `expense/edit/{id}`. AddExpenseViewModel loads by ID.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/in/marxen/pocket/ui/home/ app/src/main/java/in/marxen/pocket/ui/calendar/ app/src/main/java/in/marxen/pocket/navigation/
git commit -m "feat: add swipe-to-delete and tap-to-edit for transactions"
```

### Task 6: Wire Splash Navigation + Seed on First Launch

**Files:**
- Modify: `app/src/main/java/in/marxen/pocket/navigation/PocketNavHost.kt`

**Interfaces:**
- Consumes: `AppContainer.prefs`.
- Produces: Splash -> Home routing, first-launch category seeding.

- [ ] **Step 1: Fix splash start destination**

Splash is startDestination. Collect `firstLaunchCompleted` from prefs. On first launch, seed categories. Navigate to Home after init.

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/in/marxen/pocket/navigation/
git commit -m "feat: wire splash-to-home navigation with first-launch init"
```

### Task 7: Verify Build + All Tests

- [ ] **Step 1: Run full build + unit tests**

```bash
./gradlew assembleDebug testDebugUnitTest --no-daemon
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run lint**

```bash
./gradlew lintDebug --no-daemon
```
Expected: No errors.

- [ ] **Step 3: Final commit**

```bash
git add -A
git commit -m "chore: verify P2 build and lint"
```
