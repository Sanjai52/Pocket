# CSV Export — Paise Fix, Full Columns, Update Safety

Date: 2026-09-24. Status: implemented, unit-tested (4/4 pass), released in v1.1.

## 1. The amount bug

`BackupManager.exportCsv()` wrote `TransactionEntity.amountPaise` raw into the
Amount column, so ₹120 exported as `12000`. Root cause: the export path never
used the existing `core.money.paiseToRupees()` converter (which the rest of the
app uses everywhere). One-line class of bug, user-visible on every export.

Fix: Amount column now uses `paiseToRupees()` → `120.00` (plain number, no
currency symbol, so spreadsheets parse it directly).

## 2. Column expansion (analytics-ready)

Old header: `Date,Type,Amount,Category,Merchant,Note` — category-only, no
subcategory, no payment method, paise bug on top.

New header:

```text
Date,Type,Amount,Category,Subcategory,Payment Method,Merchant,Note,Created At
```

- Every field the app stores per transaction is now present except internal row
  IDs and `updatedAt` (noise for analytics; IDs stay in JSON backup).
- Names are resolved from IDs at export time (`CategoryDao.getAllSync`,
  new `SubcategoryDao.getAll()`, `PaymentMethodDao.getAll()`); missing links
  export as empty, never crash.
- All free-text/name fields go through the existing CSV escaper
  (commas/quotes/newlines); previously Category was escaped but the pattern
  is now uniform.
- Dates export in ISO form (`LocalDate` / `Instant` `toString`).

Files: `data/backup/BackupManager.kt` (`exportCsv` + `paiseToRupees` import),
`data/local/dao/SubcategoryDao.kt` (added suspend `getAll()`),
`ui/settings/SettingsViewModel.kt` (`exportCsv` builds the three name maps).
Test: `app/src/test/.../data/backup/BackupManagerCsvTest.kt` — header,
paise→rupees, name resolution/blanking, escaping (4 tests, 0 failures;
run `./gradlew :app:testDebugUnitTest --tests "*.BackupManagerCsvTest"`).

## 3. Sequential-update safety (the QA data-loss incident)

Reported incident: QA build → subcategory build update misconfigured, forcing
app delete + restore. Study of the current tree:

- DB is at version 3 with a complete chain (`MIGRATION_1_2` adds
  `subcategory_id` + `subcategories` table/index; `MIGRATION_2_3` adds the two
  composite indexes) and NO `fallbackToDestructiveMigration()`. Any v1 or v2
  database upgrades in place; Room validates the final schema.
- Seed runs in `SeedCallback.onCreate` only — updates never duplicate seed data.
- The v1.0→v1.1 path changes NO schema (CSV-only release), so no migration
  runs at all: zero migration risk by construction.
- Release hygiene for sequencing: `versionCode` bumped 1→2 and `versionName`
  1.0→1.1 in `app/build.gradle.kts` (Android requires a higher versionCode for
  an update to install over the previous build).

Residual risk (accepted): an unknown intermediate QA schema (e.g. a dev build
that bumped the version without its migration) can't be audited retroactively;
if an update ever crash-loops on launch with a Room migration exception, the
documented recovery is JSON Backup → reinstall → Restore (full fidelity, unlike
CSV). `getTotalExpensesSync`/`getTotalIncomeSync` in `TransactionRepository`
are now dead code (export no longer needs them); left in place, removal is a
trivial follow-up.
