# Subcategory Ledger (v1.2 → v1.3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the hardcoded weekend insight and add one deeper scrollable ledger: Category → Subcategory → Transactions.

**Architecture:** Reuse the existing Insights push-stack (`INSIGHTS → CATEGORY_LIST → CATEGORY_DETAIL`) with one new push route for subcategory transactions. Data comes from one new Room query (category + subcategory + month range) exposed as Flow through `TransactionRepository` into a new screen-scoped ViewModel. No schema change, no chart on the new screen, dynamic header from nav args.

**Tech Stack:** Kotlin 2.4.20, Jetpack Compose + Material 3, Room 2.8.5 (KSP), navigation-compose 2.8.9, Coroutines Flow.

**Spec:** User tweak request 2026-10-15 (chat) + brainstorming playback 2026-10-15: delete `You spent 34% more on food during weekends`, tappable subcategory rows (e.g. Bills → Savings ₹200 / Xerox ₹30), new ledger lists category name, subcategory name, amount, date (+ time where available), scrollable, no pie/donut, dynamic top navbar like `CategoryDetailScreen`.

## Global Constraints

- Money as integer paise (`Long`), never `Float`/`Double`; display via `formatPaiseAsRupees`.
- User-facing dates grouped by `Asia/Kolkata LocalDate`; `transaction_date` is source of truth, `created_at` Instant only for time display in IST.
- Single app module, package `in.marxen.pocket`; no `INTERNET` permission.
- No new dependencies, no schema version bump (reuse `transactions` table + existing indexes).
- Follow `AGENTS.md` verify-every-change pipeline after any app-code change (assembleDebug, uninstall/install emulator-5554, scripted taps via fresh uiautomator dump, gfxinfo settle).
- Never commit/push unless explicitly asked (standing rule overrides plan commit steps — stage for review, commit only on explicit go).

## Review Focus

- Empty subcategory month shows friendly empty state, not blank screen.
- Uncategorized bucket row is not tappable (has no `subcategoryId`).
- Long merchant/note strings wrap/truncate without breaking row layout under large font scale.
- Back stack `Ledger → CategoryDetail → CategoryList → Insights` returns intact via both app back and system back.
- Rapid taps on two subcategory rows open exactly one ledger for the last tap, no crash.

---

### Task 1: Remove hardcoded insight card

**Files:**
- Modify: `app/src/main/java/in/marxen/pocket/ui/insights/InsightsScreen.kt:325-354`
- Test: manual emulator check (no unit harness for Compose cards)

**Interfaces:**
- Consumes: nothing new.
- Produces: `SpendingTab` without `Insight` card; `DonutChart` + `MonthlyTrendChart` untouched.

- [ ] **Step 1: Delete the Insight `Card` block in `SpendingTab`**

Remove `app/src/main/java/in/marxen/pocket/ui/insights/InsightsScreen.kt:325-354` (`Card` with `containerColor 0xFFE8F5E9`, `💡`, `Insight`, `You spent 34% more on food during weekends.`). Keep surrounding `Column` spacing valid.

- [ ] **Step 2: Build to verify it compiles**

Run: `./gradlew assembleDebug` from repo root.
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Stage for review (no commit without explicit go)**

Run: `git diff --stat`
Expected: only `InsightsScreen.kt` touched.

### Task 2: Data layer — subcategory transaction query + IDs in breakdown

**Files:**
- Modify: `app/src/main/java/in/marxen/pocket/data/local/dao/TransactionDao.kt:63-64`
- Modify: `app/src/main/java/in/marxen/pocket/data/repository/TransactionRepository.kt:87-88`
- Modify: `app/src/main/java/in/marxen/pocket/ui/insights/CategoryDetailViewModel.kt:17-23,69-80`
- Test: `app/src/test/.../data/backup/BackupManagerCsvTest.kt` (regression guard, existing 4/4)

**Interfaces:**
- Consumes: existing `transactions(category_id, subcategory_id, transaction_date)` indexes.
- Produces: `TransactionDao.getByCategoryAndSubcategory(categoryId: Long, subcategoryId: Long, start: LocalDate, end: LocalDate): Flow<List<TransactionEntity>>`; `TransactionRepository.getTransactionsBySubcategory(...): Flow<List<TransactionEntity>>`; `SubcategoryBreakdown(subcategoryId: Long?, ...)` with `null` = Uncategorized bucket.

- [ ] **Step 1: Add DAO query after `subcategoryTotals`**

In `TransactionDao.kt` add:
```kotlin
fun getByCategoryAndSubcategory(categoryId: Long, subcategoryId: Long, start: LocalDate, end: LocalDate): Flow<List<TransactionEntity>>
```
backed by `@Query("SELECT * FROM transactions WHERE category_id = :categoryId AND subcategory_id = :subcategoryId AND transaction_date BETWEEN :start AND :end ORDER BY transaction_date DESC, created_at DESC")`.

- [ ] **Step 2: Expose repository passthrough**

In `TransactionRepository.kt` add `fun getTransactionsBySubcategory(categoryId: Long, subcategoryId: Long, month: YearMonth): Flow<List<TransactionEntity>>` delegating to DAO with `month.atDay(1), month.atEndOfMonth()`.

- [ ] **Step 3: Carry `subcategoryId` in `SubcategoryBreakdown`**

Add `val subcategoryId: Long?` to `CategoryDetailViewModel.kt:17-23`; set from `st.subcategoryId` in the loop, `null` for the `Uncategorized` bucket. No color/percentage logic change.

- [ ] **Step 4: Build + run existing unit tests**

Run: `./gradlew assembleDebug :app:testDebugUnitTest --tests "*.BackupManagerCsvTest"`
Expected: `BUILD SUCCESSFUL`, 4/4 pass.

- [ ] **Step 5: Stage for review**

Run: `git diff --stat`
Expected: only the 3 files above touched.

### Task 3: New ledger screen + ViewModel (scrollable, no chart, dynamic title)

**Files:**
- Create: `app/src/main/java/in/marxen/pocket/ui/insights/SubcategoryTransactionsViewModel.kt`
- Create: `app/src/main/java/in/marxen/pocket/ui/insights/SubcategoryTransactionsScreen.kt`
- Test: emulator drive (Task 5)

**Interfaces:**
- Consumes: `TransactionRepository.getTransactionsBySubcategory(categoryId, subcategoryId, month)`; nav args `categoryId: Long, categoryName: String, subcategoryId: Long, subcategoryName: String, month: YearMonth`.
- Produces: `SubcategoryTransactionsUiState(transactions: List<LedgerRow>, totalFormatted: String)`; `LedgerRow(id, amountFormatted, merchantOrNote, dateLabel, timeLabel?)`; `SubcategoryTransactionsScreen(..., onBack: () -> Unit, onTransactionClick: (Long) -> Unit = {})`.

- [ ] **Step 1: Implement `SubcategoryTransactionsViewModel` + `Factory`**

Mirror `CategoryDetailViewModel.Factory` pattern. `uiState: StateFlow` combines the subcategory-transactions Flow; maps each `TransactionEntity` to `LedgerRow` with `formatPaiseAsRupees(amountPaise)`, `transactionDate` formatted `d MMM yyyy`, time from `createdAt` in `Asia/Kolkata` (`HH:mm`) labeled as added-time (old rows have no true payment time — display only, never backfill). Sort is DAO order.

- [ ] **Step 2: Implement `SubcategoryTransactionsScreen`**

`LazyColumn`, `PocketBeige` bg, header row (back icon + `subcategoryName` centered Bold + month subtitle, same as `CategoryDetailScreen.kt:85-117`), green total `Card`, then ledger rows (merchant-or-note line, `Category • Subcategory` line, date/time line, amount right-aligned `PocketGreen` Semibold). Rows `clickable` → `onTransactionClick(id)` (edit route). Empty state `Card`: `No spending in <Subcategory> this month`. No `Canvas`/donut. Bottom `Spacer 80.dp`.

- [ ] **Step 3: Build to verify it compiles**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

### Task 4: Navigation — new route + tappable subcategory rows

**Files:**
- Modify: `app/src/main/java/in/marxen/pocket/navigation/Routes.kt:15-20`
- Modify: `app/src/main/java/in/marxen/pocket/navigation/PocketNavHost.kt:303-322`
- Modify: `app/src/main/java/in/marxen/pocket/ui/insights/CategoryDetailScreen.kt:62-75,170-198`
- Test: emulator drive (Task 5)

**Interfaces:**
- Consumes: `SubcategoryTransactionsScreen` + ViewModel from Task 3; existing `Routes.editRoute`.
- Produces: `Routes.SUBCATEGORY_TRANSACTIONS = "insights/categories/{categoryId}/subcategories/{subcategoryId}?categoryName={categoryName}&subcategoryName={subcategoryName}&month={month}"` + `subcategoryTransactionsRoute(...)` helper; `CategoryDetailScreen(onSubcategoryClick: (subcategoryId: Long, subcategoryName: String) -> Unit = { _, _ -> })`.

- [ ] **Step 1: Add route constant + helper in `Routes.kt`**

Add `SUBCATEGORY_TRANSACTIONS` + `fun subcategoryTransactionsRoute(categoryId, categoryName, subcategoryId, subcategoryName, month)` with `Uri.encode` on both names, mirroring `categoryDetailRoute`.

- [ ] **Step 2: Make subcategory rows tappable (skip `null` bucket)**

In `CategoryDetailScreen.kt:170-198` add `clickable(enabled = item.subcategoryId != null) { item.subcategoryId?.let { onSubcategoryClick(it, item.name) } }` to the row `Modifier`. Add `onSubcategoryClick` param to the composable signature with default no-op.

- [ ] **Step 3: Register composable in `PocketNavHost.kt` after `CATEGORY_DETAIL`**

Parse `categoryId Long, subcategoryId Long, categoryName/subcategoryName/month String` args, call `SubcategoryTransactionsScreen(..., onBack = popBackStack, onTransactionClick = { navigate(Routes.editRoute(it)) })`. Wire `CategoryDetailScreen.onSubcategoryClick` to `navigate(subcategoryTransactionsRoute(...))`. Keep 150ms push fades (not `noEnter`/`noExit`).

- [ ] **Step 4: Build to verify navigation compiles**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

### Task 5: Verify-every-change pipeline (AGENTS.md)

**Files:** none (device ops only).

- [ ] **Step 1: Fresh install + pass Welcome**

Run: `adb -s emulator-5554 uninstall in.marxen.pocket; ./gradlew installDebug; adb -s emulator-5554 shell am start -n in.marxen.pocket/.MainActivity`; Skip via `input tap 540 1790`.
Expected: Home with bottom bar, no crash.

- [ ] **Step 2: Drive Insights → See all → Food → Lunch/Savings → ledger**

Seed if needed via FAB (amount + category + subcategory). Assert from fresh `uiautomator dump` after each tap: `By Category` → `See all` → list → `Food` header centered → subcategory row tappable → ledger header = subcategory name, rows show amount + date, scrollable (`LazyColumn` scrolls), no chart text, empty state when month has none. Explicitly assert Review Focus: empty-month ledger shows empty `Card`; `Uncategorized` row has no click action; long merchant/note wraps without overlap (screencap on large font); rapid double-tap on two rows lands on exactly one ledger.

- [ ] **Step 3: Back-stack + rendering check**

System back + app back through ledger → detail → list → Insights with no blank/crash. `dumpsys gfxinfo in.marxen.pocket reset`, switch, confirm frames freeze within a few frames and `Slow UI thread` stays 0.
