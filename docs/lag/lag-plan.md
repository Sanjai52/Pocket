Pocket — Deep Screen Transition Lag Investigation, Profiling & Complete Fix

Objective

Act as a senior Android performance engineer and Kotlin/Jetpack Compose architect.

Deeply investigate and completely fix screen-transition lag in the Pocket Android app.

Do not assume the previous analysis is fully correct. Treat it as a set of hypotheses. Inspect the actual codebase, verify each finding, discover additional bottlenecks, implement the fixes, test them, and re-profile.

The goal is not merely to make transitions appear faster. The goal is to reduce the actual work performed during:

User tap
→ NavController
→ destination lifecycle
→ composition
→ ViewModel creation
→ Flow subscription
→ Room/SQLite
→ Flow transformations
→ UI state
→ recomposition
→ Lazy layout / Canvas
→ animation
→ first useful frame

1. Existing Performance Analysis — Starting Hypotheses

The previous investigation concluded that lag is primarily caused by work beginning when a destination is first composed rather than by the navigation call itself.

Known findings to verify:

Every route has a roughly 150 ms fade transition.

Screen-scoped ViewModels are created during composition.

SharingStarted.WhileSubscribed(5000) starts Room-backed work when the first collector appears.

Home combines several Room/DataStore flows and performs in-memory filtering/mapping.

Insights performs six sequential monthly database queries during initialization.

CategoryListScreen creates InsightsViewModel, causing unrelated Insights work.

Category Detail combines multiple database flows and performs in-memory joins.

Calendar repeatedly scans/groups the monthly transaction list for 42 calendar cells.

Insights and Category Detail mount Canvas charts during destination entry.

Splash intentionally waits about 1.8 seconds, which affects startup but is separate from normal navigation.

Transaction queries may need indexes based on actual date/type/category access patterns.

Previous recommended fix order:

Create a dedicated CategoryListViewModel.

Replace six monthly trend queries with one grouped query.

Improve ViewModel/state lifetime across bottom-navigation tabs.

Reduce/disable navigation animation while measuring.

Precompute Calendar day summaries.

Reduce unnecessary Compose recomposition and large-state rebuilding.

Add database indexes based on actual query patterns.

Do not blindly implement these. Verify them first.

2. Phase 1 — Full Codebase Performance Audit

Before modifying code, inspect the complete relevant architecture.

Inspect at minimum:

app/src/main/java/in/marxen/pocket/navigation/PocketNavHost.kt

HomeScreen.kt
HomeViewModel.kt

CalendarScreen.kt
CalendarViewModel.kt

InsightsScreen.kt
InsightsViewModel.kt

CategoryListScreen.kt
CategoryDetailScreen.kt
CategoryDetailViewModel.kt

AddExpenseScreen.kt
SettingsScreen.kt

TransactionDao.kt
TransactionEntity.kt
CategoryDao.kt
CategoryEntity.kt

Subcategory-related entities/DAOs/repositories

All repository classes

Room database configuration

DataStore usage

Navigation routes

All Flow/StateFlow/SharedFlow usage

All Compose state collection

All LazyColumn/LazyRow implementations

All Canvas/chart implementations

Search the repository for:

viewModel(
hiltViewModel(
collectAsState
collectAsStateWithLifecycle
LaunchedEffect
DisposableEffect
remember
derivedStateOf
produceState
snapshotFlow
combine(
flatMapLatest(
stateIn(
shareIn(
WhileSubscribed
flowOn
withContext
Dispatchers.IO
Dispatchers.Default
Dispatchers.Main
SUM(
GROUP BY
ORDER BY
filter(
groupBy(
map(
sortedBy
sortedByDescending
Canvas
draw
LazyColumn
LazyRow
AnimatedContent
AnimatedVisibility
fadeIn
fadeOut

Look for hidden performance costs, not only the already-known issues.

3. Phase 2 — Build a Route-by-Route Performance Model

For every destination, determine:

When its ViewModel is created.

How many times it can be created.

What happens inside init.

Which flows start.

Which DAO queries start.

Which transformations execute.

Which work is performed on Main.

Which work is performed on IO/Default.

How much data is loaded.

How much in-memory aggregation occurs.

What causes the first useful UI state.

What causes later recompositions.

What happens when returning after a database write.

Audit:

Home

Calendar

Insights

Category List

Category Detail

Add/Edit Expense

Settings

Transaction/drill-down screens

QR-related destinations if they participate in normal navigation transitions

Create a route table:

Route

ViewModel

Queries

Flow starts

CPU aggregation

Compose-heavy work

Animation

Main-thread risk

Likely impact

Separate findings into:

Confirmed

Likely

Ruled out

Do not call something confirmed without evidence from source inspection or profiling.

4. Phase 3 — Trace Navigation to First Useful Frame

Do not assume navigation is slow.

Determine how much time is spent in:

navigate()
→ NavController
→ destination composition
→ ViewModel creation
→ Flow collection
→ DAO invocation
→ Room query
→ mapping/transformation
→ UI state emission
→ Compose recomposition
→ chart drawing
→ LazyColumn/LazyRow composition
→ animation

For each laggy route answer:

What starts first?

What blocks?

What runs on Main?

What runs on IO?

What waits for what?

What causes the first frame?

What causes the first useful content?

What causes the fully populated state?

5. Phase 4 — Main-Thread Audit

Search for expensive work that may execute on the main thread.

Inspect:

Room suspend functions.

Room Flow queries.

Flow transformations after Room emits.

combine.

map.

flatMapLatest.

onEach.

stateIn.

ViewModel initialization.

Compose callbacks.

remember.

LaunchedEffect.

For every expensive operation determine its actual dispatcher.

Do not assume that because Room performs the query off the main thread, all downstream transformations are also off the main thread.

If expensive CPU aggregation happens after a Room emission and before UI state emission, determine whether it should:

move to Dispatchers.Default,

move into SQL,

be reduced,

or be split into smaller state.

Do not blindly add Dispatchers.IO everywhere.

6. Phase 5 — ViewModel Creation and Lifetime

Inspect:

HomeScreen
CalendarScreen
InsightsScreen
CategoryListScreen
CategoryDetailScreen
AddExpenseScreen
SettingsScreen

Determine:

When is the ViewModel created?
What NavBackStackEntry scopes it?
Can navigation recreate it?
What happens in init?
What database work begins immediately?

Pay special attention to bottom navigation.

Determine whether:

Home → Insights → Home

causes Home's ViewModel and Flow graph to restart.

Do not change ViewModel scoping blindly. First understand the current Navigation hierarchy.

7. Required Fix — Category List

Verify whether:

CategoryListScreen
→ InsightsViewModel

still exists.

If yes, replace it.

Create:

CategoryListViewModel

with only the state required by Category List.

It must not load:

monthly trends,

unrelated chart data,

unrelated transaction lists,

unnecessary Insights state,

category-detail state.

The target architecture is:

CategoryListScreen
    ↓
CategoryListViewModel
    ↓
Category DAO / Repository
    ↓
Only category totals required by the screen

Do not reuse InsightsViewModel simply to avoid creating a new ViewModel.

8. Required Fix — Insights Monthly Trend Query

Verify the current six sequential queries.

If they exist, replace them with one grouped SQL query where appropriate.

Preferred architecture:

One grouped SQL query
        ↓
Lightweight monthly projection
        ↓
Six-month UI model

Conceptually:

SELECT
    year,
    month,
    SUM(amount)
FROM transactions
WHERE ...
GROUP BY year, month
ORDER BY year, month;

Adapt this to the actual schema and SQLite/Room capabilities.

Do not invent unsupported SQL syntax.

Prefer a lightweight projection such as:

data class MonthlyExpenseTotal(
    val year: Int,
    val month: Int,
    val totalPaise: Long
)

Do not load complete transaction entities if only totals are required.

If one grouped query is not appropriate, document why and use the next-best design. Do not retain six serial queries without justification.

9. Full Room/DAO Audit

For every relevant query determine:

Does it select unnecessary columns?

Does it load full entities when only aggregates are needed?

Does it scan the whole transaction table?

Does it repeatedly execute equivalent work?

Does it perform unnecessary sorting?

Does it have appropriate indexes?

Does it restart due to Flow subscriptions?

Does it recreate queries unnecessarily on month/date changes?

Can SQL aggregation replace Kotlin aggregation?

Create:

DAO/query

Purpose

Caller

Frequency

Rows returned

Index

Issue

Fix

10. Database Index Audit

Inspect the actual schema and query patterns.

Relevant fields may include:

transaction_date
transaction_at
type
category_id
subcategory_id
payment_attempt_id

Use the actual schema rather than assuming field names.

For recurring patterns such as:

WHERE type = ?
AND transaction_date BETWEEN ? AND ?

evaluate whether a composite index is appropriate.

For patterns involving category and date, evaluate whether:

(category_id, transaction_date)

or another ordering is appropriate.

Do not add redundant indexes.

Consider:

query selectivity,

index size,

write cost,

existing indexes,

actual query plans where available.

Create a migration if required.

11. Home Deep Audit

Inspect the entire HomeViewModel.

Known work includes:

monthly expense total,

monthly income total,

full monthly transaction list,

active categories,

selected date,

active subcategories,

user-name DataStore flow.

Determine:

How many DB queries?
How many Flow emissions?
How many list traversals?
How many allocations?
What happens when selectedDate changes?
What happens when one transaction changes?

Pay particular attention to:

full transaction list
→ filter
→ filter again
→ map
→ build UI objects

Determine whether changing selected date unnecessarily rebuilds unrelated monthly state.

Where justified, separate:

MonthlySummaryState
SelectedDayState
CategoryState
UserState

Do not split state purely for theoretical purity. Use evidence.

12. Calendar Deep Audit

Verify whether the current implementation does:

42 calendar cells
×
scan/filter/group full monthly transaction list

If yes, replace it with one preprocessing pass:

Monthly transactions
      ↓
one pass
      ↓
Map<LocalDate, DaySummary>
      ↓
42 O(1) lookups

Also ensure changing only the selected date does not rebuild the entire monthly aggregation.

Separate monthly day summaries from selected-day transactions where appropriate.

Preserve all date semantics, especially Asia/Kolkata.

13. Category Detail Deep Audit

Inspect:

CategoryDetailViewModel
CategoryDetailScreen

Audit:

category total

subcategory totals

no-subcategory bucket

all-subcategory loading

in-memory maps

chart calculations

month changes

Determine whether SQL aggregation can replace unnecessary Kotlin joins.

A suitable conceptual query may be:

GROUP BY category_id, subcategory_id

but adapt it to the actual schema.

Preserve:

No subcategory = NULL

as a distinct bucket.

14. Compose Recomposition Audit

Do a dedicated Compose performance pass.

Inspect:

LazyColumn
LazyRow
items
itemsIndexed
Canvas
remember
derivedStateOf
collectAsStateWithLifecycle
key
State

Look for:

Unstable parameters

Objects recreated on every recomposition.

Oversized state

One large UI state causing unrelated UI to recompose.

Work inside composition

Examples:

transactions.filter(...)
transactions.map(...)
transactions.groupBy(...)
transactions.sortedBy(...)

inside composables.

Missing LazyList keys

Use stable keys where appropriate.

Repeated chart calculations

Ensure chart points/bars are not recalculated unnecessarily.

Canvas

Determine whether Canvas is actually a bottleneck through profiling rather than assuming it is.

Do not rewrite charts unless evidence supports doing so.

15. Search for Expensive Work Inside Composables

Search all affected screens for:

filter(
map(
groupBy(
sortedBy(
sortedByDescending(
sumOf(
calculate...

inside composable bodies.

Move expensive deterministic work to:

ViewModel,

DAO/SQL,

repository,

remember with correct keys,

or appropriate derived state,

depending on lifecycle and invalidation requirements.

Do not blindly wrap everything in remember.

16. Audit remember and derivedStateOf

For every significant use:

Are the keys correct?

Is memoization actually useful?

Is a large object recreated anyway?

Could stale UI result?

Is derivedStateOf solving a real recomposition problem?

Only keep optimizations that have a clear lifecycle/data dependency.

17. Flow Lifecycle Audit

Search for:

SharingStarted.WhileSubscribed(5000)
stateIn
shareIn
combine
flatMapLatest

Determine:

When subscription starts
When it stops
Whether navigation causes restart
Whether multiple collectors exist
Whether the same upstream query is observed multiple times

Specifically investigate:

Screen leaves
→ Flow stops
→ screen returns
→ Flow restarts
→ DAO query runs again
→ transformations run again

Do not automatically change WhileSubscribed to Eagerly.

Keeping expensive flows alive permanently can increase total resource usage.

Choose lifecycle behavior deliberately.

18. Bottom Navigation Audit

Inspect actual NavHost configuration.

Check:

saveState
restoreState
launchSingleTop
back-stack behavior
NavBackStackEntry scoping

Determine whether bottom-navigation destinations are unnecessarily recreated.

Goal:

Home → Insights → Home

should not unnecessarily restart Home's entire data pipeline.

Preserve state where appropriate without:

memory leaks,

duplicated ViewModels,

broken back navigation,

incorrect state restoration.

19. Navigation Animation Audit

Inspect:

PocketNavHost.kt

and all:

fadeIn
fadeOut
AnimatedContent
AnimatedVisibility

For measurement:

Phase A

Temporarily disable navigation animations.

Phase B

Measure with the normal animation.

Determine how much of perceived lag is:

actual rendering/data loading

versus:

animation duration

Do not use animation to hide slow rendering.

20. Splash Screen Audit

Inspect SplashScreen.kt.

Verify the reported:

~800 ms alpha animation
+
~1000 ms delay

Treat this as startup behavior, not normal screen-transition lag.

Only change it if safe and justified.

21. Dataset Scaling Tests

Test with:

0 transactions
100 transactions
1,000 transactions
5,000+ transactions

Use deterministic test data if practical.

Measure:

Home

Calendar

Insights

Category List

Category Detail

Determine which costs are constant and which scale with transaction count.

22. Performance Instrumentation

Before and after major changes, add temporary instrumentation for:

ViewModel initialization
Flow collection start
DAO query start/end
aggregation start/end
first UI state emission

Example:

[PERF] InsightsViewModel created
[PERF] Insights monthly flow started
[PERF] Monthly trends query started
[PERF] Monthly trends query finished: X ms
[PERF] First InsightsUiState emitted

Do not log sensitive information.

Remove or gate temporary instrumentation after verification.

23. Profiling

Use available Android profiling tools:

Perfetto/System Trace

Android Studio Profiler

Compose recomposition tools

Macrobenchmark if practical

Measure:

navigate()
→ first frame
→ first useful content
→ fully populated destination

Do not rely only on subjective impressions.

24. First Frame vs First Useful Content

Measure both.

A destination can show immediately while still being slow because it displays a loading state.

The preferred behavior is:

Navigation
    ↓
Immediate stable screen shell
    ↓
Small required state
    ↓
Progressive completion

Do not block the entire destination on unrelated data.

25. Avoid Fake Fixes

Do NOT consider these complete solutions:

- Arbitrary delays
- Hiding loading states without reducing work
- Only shortening animations
- Moving everything to Dispatchers.IO
- GlobalScope
- Uncontrolled async launches
- Keeping every ViewModel alive forever
- Caching everything indefinitely
- Disabling all animations globally
- Adding indexes without query analysis
- Using remember everywhere

Optimize actual work.

26. Screen-Specific Targets

Home

Reduce:

unnecessary monthly recomputation,

repeated transaction traversal,

unrelated recompositions,

unnecessary query results.

Changing selected date should not recompute unrelated monthly state.

Calendar

Use:

monthly transactions
→ one day-summary preprocessing pass
→ 42 map lookups

Changing selected date should not rebuild monthly aggregation.

Insights

Use:

one grouped monthly query

instead of six sequential queries where appropriate.

Audit chart and category breakdown recomposition.

Category List

Must not create InsightsViewModel.

Use a dedicated CategoryListViewModel.

Category Detail

Audit category/subcategory queries and in-memory joins.

Preserve the No subcategory bucket.

Add Expense

Keep extremely lightweight.

It should only load the category/subcategory/edit data it actually needs.

It must not subscribe to unrelated Insights/Home/Calendar flows.

Settings

Keep lightweight and DataStore-focused.

27. Preserve Pocket's Existing Product Behavior

This is a performance task.

Do not redesign:

expense model,

category semantics,

subcategory UX,

QR payment architecture,

Insights information architecture,

navigation structure,

unless a change is directly required for performance.

Preserve:

INR/paise handling,

Asia/Kolkata date semantics,

income/expense separation,

category hierarchy,

nullable subcategory_id,

No subcategory semantics.

28. Implementation Workflow

Follow this exact workflow:

Phase 1 — Inspect

Do not edit.

Phase 2 — Profile/Trace

Establish where time is actually spent.

Phase 3 — Root-Cause Report

Document:

Finding
Evidence
Impact
Root cause
Proposed fix
Risk
Verification

Phase 4 — Implementation Plan

Order fixes by:

Impact
Confidence
Risk
Effort

Phase 5 — Implement

Apply the fixes completely.

Phase 6 — Test

Run relevant unit/instrumentation/UI tests.

Phase 7 — Re-profile

Measure the same routes again.

Phase 8 — Remaining Bottlenecks

If meaningful lag remains, investigate again instead of declaring completion.

Phase 9 — Final Report

Provide before/after evidence and all remaining limitations.

29. Required Fixes Unless Proven Already Fixed

Address these unless source inspection proves they are already solved or technically inappropriate:

CategoryListScreen must not create InsightsViewModel.

Insights six-query monthly trend loading must be consolidated where appropriate.

Calendar's repeated 42-cell transaction scanning must be optimized.

Bottom-navigation ViewModel/state lifetime must be audited.

Compose recomposition must be audited.

Database indexes must be audited against actual queries.

Navigation animation must be measured separately.

Main-thread CPU work must be audited.

Flow restart behavior must be audited.

Home and Category Detail must be audited for unnecessary recomputation.

30. Testing After Changes

After each major group:

./gradlew test
./gradlew lint
./gradlew assembleDebug

Use the project's actual Gradle tasks if they differ.

Fix compilation/test failures immediately.

Do not leave the repository partially migrated.

31. Regression Tests

Add/update tests for:

Insights

Monthly totals remain correct.

Category List

Category totals remain correct.

Category Detail

Subcategory totals remain correct.

No subcategory remains separate.

Calendar

Daily totals remain correct.

Navigation

State survives where intended.

Database

Migrations and indexes work correctly.

Do not trade correctness for speed.

32. Data Correctness

Verify that optimization does not alter:

INR amounts,

paise calculations,

category totals,

subcategory totals,

no-subcategory totals,

date boundaries,

IST handling,

income/expense separation,

monthly totals,

calendar totals.

Pay particular attention to:

Asia/Kolkata

and month/day boundaries.

33. Final Performance Report

Create a final report containing:

Root Causes Found

For every confirmed issue:

Issue:
File:
Code/location:
Root cause:
Impact:
Evidence:

Separate:

Confirmed
Likely
Ruled out

Fixes Implemented

List every architectural/code change.

Before vs After

For each major route:

Route

First frame

First useful content

Fully loaded

Notes

Only report actual measurements.

Do not fabricate numbers.

Database Changes

List:

added queries,

removed queries,

changed queries,

indexes,

migrations.

Compose Changes

List:

recomposition fixes,

stable state changes,

LazyList key changes,

chart changes.

Navigation Changes

List:

ViewModel scoping,

state restoration,

back-stack behavior,

animation changes.

Remaining Bottlenecks

If anything remains:

What:
Why:
Measured impact:
Whether further optimization is worthwhile:

Do not claim "zero lag" without evidence.

34. Definition of Done

The task is complete only when:

CategoryList no longer creates InsightsViewModel.

CategoryList has an appropriately scoped ViewModel/state source.

Insights no longer performs six serial monthly DB queries unless there is a documented reason.

Calendar no longer repeatedly scans the entire transaction list for each calendar cell.

Home has been audited for unnecessary recomputation.

CategoryDetail has been audited for unnecessary queries/joins.

Add Expense remains lightweight.

Bottom-navigation state/ViewModel lifetime has been audited.

Flow restart behavior has been audited.

Main-thread expensive work has been identified and corrected where necessary.

Compose recomposition hotspots have been audited.

Lazy lists have appropriate stable keys.

Expensive calculations are not unnecessarily performed inside composition.

Database indexes match actual query patterns.

Navigation animation has been measured independently.

Splash delay is understood separately from navigation lag.

Empty and large datasets have been considered/tested.

Unit tests pass.

Android tests pass where available.

Lint passes or unrelated existing failures are documented.

Debug APK builds successfully.

No functional regression is introduced.

Temporary performance instrumentation is removed or appropriately gated.

Final report identifies confirmed root causes.

Final report contains actual verification evidence.

Remaining bottlenecks are explicitly documented.

35. Most Important Instruction

Do not stop after fixing only:

CategoryList → wrong ViewModel
Insights → six queries

Those are already-known findings.

Go one level deeper.

Trace the complete lifecycle:

USER TAP
   ↓
NavController
   ↓
Destination lifecycle
   ↓
Composition
   ↓
ViewModel creation
   ↓
Flow subscription
   ↓
Room
   ↓
SQLite
   ↓
DAO result
   ↓
Flow transformation
   ↓
ViewModel state
   ↓
Compose snapshot
   ↓
Recomposition
   ↓
Lazy layout
   ↓
Canvas/chart
   ↓
Animation
   ↓
FIRST USEFUL FRAME

Find where the actual time is spent.

Then fix the root causes rather than masking symptoms.

The final goal is:

Pocket's navigation and screen rendering pipeline should perform only the work necessary for the destination being entered, with expensive aggregation pushed to efficient SQL or appropriate background computation, reactive state scoped correctly, unnecessary recomputation removed, and Compose rendering kept stable and incremental.