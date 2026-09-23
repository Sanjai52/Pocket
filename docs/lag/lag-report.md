# Lag-plan implementation report

## Branch and scope

- Working branch: `fixing-lag`
- No commit was created.
- Nothing was pushed.
- Existing worktree changes from the prior `feat/subcategory` work were preserved and not reverted.

## Confirmed findings

1. `CategoryListScreen` was creating `InsightsViewModel`, which started unrelated Insights flows and six sequential monthly total queries.
2. `InsightsViewModel` loaded six months with six serial database calls.
3. `CalendarViewModel` repeatedly filtered/grouped transaction data inside the 42-cell calendar calculation.
4. `HomeViewModel` rebuilt monthly data when only the selected date changed, because the selected date was part of the large combined flow.
5. Category detail loaded all category totals and searched for one category in memory.
6. Transaction query patterns use date/type/category filters that were not fully indexed.

## Fixes implemented

### Category list

Added `CategoryListViewModel`, which loads only the current month's total, category totals, and active categories. `CategoryListScreen` now uses it and adds stable lazy-list keys.

### Insights trends

Added a grouped Room projection query that returns monthly expense totals for the six-month window. The ViewModel now fills missing months with zeroes from that single query instead of executing six serial queries.

### Calendar

Monthly transaction data is grouped once into a date map. Each of the 42 calendar cells performs a map lookup and uses the precomputed totals. Selected-date changes now reuse the monthly aggregation rather than rebuilding it.

### Home

Monthly transaction/category/subcategory state is separated from selected-date state. Selected-date changes only rebuild the selected-day portion of the UI model. The larger transformations are scheduled on `Dispatchers.Default`.

### Category detail

Category detail now queries the selected category's aggregate directly instead of loading every category total and searching in Kotlin.

### Database

Added indexes for `(type, transaction_date)` and `(category_id, transaction_date, type)` with a Room 2→3 migration. Existing indexes and date semantics were preserved.

### Flow transformation dispatcher

Calendar, Home, and Insights aggregation pipelines use `flowOn(Dispatchers.Default)` so CPU-heavy state construction is not unnecessarily performed on the UI collector thread.

## Audited but intentionally unchanged

- Bottom navigation already uses `saveState`, `restoreState`, and `launchSingleTop`; no scoping change was made without a runtime trace proving recreation was the dominant cost.
- The 150 ms navigation fade remains unchanged. It is a measurable visual cost, but shortening it alone would mask rather than fix destination work.
- Splash animation/delay remains unchanged because it is startup behavior, not normal screen-transition lag.
- Canvas charts were not rewritten without evidence that drawing was a bottleneck.

## Verification

Passed on `fixing-lag`:

- `./gradlew.bat :app:assembleDebug`
- `./gradlew.bat test lint`
- `git diff --check` (no whitespace errors; only Git line-ending notices)

The debug APK was generated successfully at `app/build/outputs/apk/debug/app-debug.apk`.

## Remaining limitation

No before/after Perfetto or emulator frame-timing trace was captured in this implementation pass, so this report does not claim a numeric latency improvement. The code-level causes were verified from the current source and the changes compile, test, and lint successfully. A follow-up run should compare navigation-to-first-frame and first-useful-content timings on empty and large datasets.
