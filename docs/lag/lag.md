# Screen transition lag analysis

## Summary

The lag is primarily caused by work that starts when a destination is first composed, not by the navigation call itself. Every destination uses a new screen-scoped `ViewModel`; that ViewModel immediately starts Room-backed flows and performs data aggregation before the screen has useful content to render. The navigation graph also applies a fade animation to every transition, so the delay is visually amplified.

The highest-confidence issue is the category-list route: `CategoryListScreen` creates an `InsightsViewModel`, even though it only needs category totals. Creating that ViewModel starts six synchronous monthly database queries in sequence plus the normal insights flows.

## Evidence

### 1. Every route has a 150 ms fade transition

`app/src/main/java/in/marxen/pocket/navigation/PocketNavHost.kt:122-129` applies `fadeIn` and `fadeOut` animations to every forward and backward navigation. This is an intentional visual delay of up to roughly 150 ms per side and can make destination loading feel slower when the destination is also doing work.

### 2. Screen ViewModels start database work on first composition

The screens obtain their ViewModels inside the composable:

- `HomeScreen.kt:69-74`
- `CalendarScreen.kt:70-74`
- `InsightsScreen.kt:65-69`
- `CategoryListScreen.kt:72-76`
- `CategoryDetailScreen.kt:66-71`
- `AddExpenseScreen.kt:77-81`

Their `StateFlow`s use `SharingStarted.WhileSubscribed(5000)`, so the first collector created during navigation starts the database queries and transformations while the new destination is entering.

### 3. Home executes several independent Room flows and recomputes lists

`HomeViewModel.kt:68-107` combines:

- monthly expense total;
- monthly income total;
- the entire month's transactions;
- active categories;
- selected date;
- all active subcategories;
- the user-name DataStore flow.

It then filters the full transaction list twice and maps transactions into UI objects (`HomeViewModel.kt:82-102`). Any upstream emission can rebuild the complete `HomeUiState`. This is especially noticeable when returning to Home after a write because Room invalidates the observed queries.

### 4. Insights performs six sequential synchronous queries on entry

`InsightsViewModel.kt:91-113` calls `loadMonthlyTrends()` during initialization. The loop at lines 100-110 calls `getTotalExpensesSync()` once for each of the previous six months. Although these calls are suspend functions, they are awaited serially, so the trend data cannot be emitted until all six queries finish. At the same time, the main insights flow starts three more Room queries (`InsightsViewModel.kt:50-60`).

Changing the month repeats the six-query sequence (`InsightsViewModel.kt:119-122`).

### 5. CategoryListScreen loads the wrong ViewModel

`CategoryListScreen.kt:69-78` uses `InsightsViewModel`. This causes the category-list destination to run all of the Insights work, including the six sequential trend queries, even though the screen only reads `categoryBreakdown` and `totalSpent` (`CategoryListScreen.kt:118-203`). This is the clearest route-specific source of repeatable lag.

### 6. Category detail runs three database flows and does in-memory joins

`CategoryDetailViewModel.kt:50-101` combines category totals, subcategory totals, and all subcategories. On each month change, all three flows are recreated in `flatMapLatest` (`lines 50-60`). The result then builds maps and recalculates each breakdown (`lines 61-98`). This is acceptable for small data, but it adds work directly to the transition into category details.

### 7. Calendar repeatedly scans and groups the full month in memory

`CalendarViewModel.kt:45-103` loads all monthly transactions and, for each of 42 calendar cells, filters and groups that day's transactions (`lines 64-77`). With a large transaction history this can consume a noticeable amount of CPU during first composition and month changes. The repeated `filter` calls inside the 42-cell loop should be replaced with one precomputed per-day summary.

### 8. Heavy Compose content is mounted during the transition

Insights and category detail render custom `Canvas` charts (`InsightsScreen.kt:359-404`, `InsightsScreen.kt:406-486`, `CategoryDetailScreen.kt:218-246`) inside a `LazyColumn`. The chart itself is small, but it is composed at the same time as the database state arrives and the fade animation runs. This increases the chance of dropped frames on the first frame of a destination.

### 9. First-launch splash intentionally waits 1.8 seconds

`SplashScreen.kt:41-45` performs an 800 ms alpha animation followed by a 1000 ms delay before navigating. This affects startup navigation rather than normal screen-to-screen navigation, but it can be mistaken for general app lag.

## Likely cause by route

| Route | Main work during entry | Risk |
| --- | --- | --- |
| Home | 3 transaction/aggregate flows, category/subcategory joins, list mapping | Medium |
| Calendar | Full-month query plus 42-cell grouping | Medium |
| Insights | 3 reactive queries plus 6 serial trend queries | High |
| Category list | Incorrectly starts the full InsightsViewModel | Very high |
| Category detail | 3 reactive queries plus subcategory joins | Medium |
| Add/edit expense | Category and subcategory flows; edit query on entry | Low/medium |
| Settings | Mostly DataStore flows | Low |

## Recommended fix order

1. Create a dedicated `CategoryListViewModel` that exposes only category totals, or pass the already-loaded category data from Insights. Do not instantiate `InsightsViewModel` in `CategoryListScreen`.
2. Replace the six serial trend queries with one DAO query that returns monthly totals, or run the six requests concurrently and combine the results. Prefer one grouped SQL query for predictable performance.
3. Keep screen ViewModels alive across bottom-navigation tab changes where appropriate, or share a screen-level state holder so returning to a tab does not restart the full flow graph.
4. Reduce the transition animation to a single short animation, or temporarily disable it while measuring. The animation should not hide data-loading latency.
5. Precompute calendar day summaries once per transaction-list emission instead of filtering/grouping separately for each of 42 cells.
6. Split large screen state into stable, smaller state objects and avoid rebuilding unrelated UI when only the selected date/tab changes.
7. Add database indexes for the date and category access patterns if the transaction table can grow. The current queries repeatedly filter by `transaction_date`, `type`, and `category_id` (`TransactionDao.kt:36-58`).

## Verification plan

Measure before and after on the debug build:

1. Record a Perfetto/System Trace while navigating Home → Insights → Category list → Category detail.
2. Compare time from `navigate()` to the first composed frame and to the first non-empty state.
3. Check main-thread frame time for dropped frames during each transition.
4. Log each ViewModel initialization and each Room query to confirm that category-list navigation no longer starts trend loading.
5. Test with both an empty database and a large database, because the current in-memory grouping cost scales with transaction count.

## Conclusion

The lag is not one isolated animation problem. It is the combination of a transition fade, destination ViewModel initialization, repeated Room work, and synchronous/sequential trend loading. Fixing the incorrect ViewModel on the category-list route and batching the Insights trend query should provide the largest immediate improvement; the remaining aggregation and state-lifetime changes address lag that grows with data size and repeated navigation.
