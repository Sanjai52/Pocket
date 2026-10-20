# Investment Page (Trends → Investment) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the redundant Insights "Trends" tab with an "Investment" tab that keeps its own ledger — savings moves between a primary and secondary account, and stock/market investments — with a dedicated add screen that reuses the Add Expense design word-for-word, all-time balances, and month-scoped drill-downs. Nothing in this ledger can ever appear on Home or in spending.

**Architecture:** Two new Room tables (`investment_types`, `investment_entries`) with a v3→v4 migration, a new DAO and repository, fully separate from `transactions` — so Home, Calendar, Insights balances, charts and CSV cannot include these entries by construction. The tab renders inside `InsightsScreen` (its selected-tab state is hoisted to `PocketNavHost` so the FAB can branch). Balances are all-time SUM queries; the entry list is a month-scoped Flow; tapping a balance pushes a parameterized ledger screen mirroring `SubcategoryTransactionsScreen`. The add/edit screen mirrors `AddExpenseScreen` 1:1 with only the words swapped. The JSON backup learns both new tables; CSV stays expense/income-only.

**Tech Stack:** Kotlin 2.4.20, Jetpack Compose + Material 3, Room 2.8.5 (KSP), navigation-compose 2.8.9, Coroutines Flow. No new dependencies.

**Spec:** User walkthrough 2026-10-18/19 (chat) + approved mockups `.superpowers/brainstorm/5031-1792428782/content/investment-first-look.html` (round 1) and `investment-v2.html` (round 2). Wording locked by the user's browser click: **"Add Investment" / "Save Investment"** (option A). Where this plan says "mirror X", copy X's structure and values verbatim, changing only the listed wording.

## Global Constraints

- Files live under `app/src/main/java/in/pocket/pocket/...` but every package line stays `` `in`.marxen.pocket.… `` — copy the header of a neighboring file exactly.
- Money is `Long` paise; format with `formatPaiseAsRupees`; "today" is `asiaKolkataToday()`; local times use `pocketZoneId()`.
- Colors: PocketGreen `#1A5C38`, PocketBeige `#F5F0E6`, PocketText `#1A1A1A`, PocketTextSecondary `#666666`, PocketTextTertiary `#999999`. Type tints: Stocks `#2196F3`, IPO `#9C27B0`, Mutual fund `#00897B`, SIP `#FF9800`, Other `#607D8B`, To-secondary `#1A5C38`, From-secondary `#FF9800`.
- Wording is locked: tab "Investment"; screen "Add Investment" / "Edit Investment"; button "Save Investment" / "Update Investment"; toggle "Savings" / "Investments" (Savings first); card labels "Secondary account" / "Invested"; moves "To secondary" / "From secondary"; types "Stocks", "IPO", "Mutual fund", "SIP", "Other"; dialog "Add Custom Type". No "all-time" caption anywhere.
- Investment entries must never appear in transactions, Home, Calendar, Insights Spending/Income, charts, or CSV export — they live only in the new tables.
- Do not modify `AddExpenseScreen.kt` / `AddExpenseViewModel.kt` behavior. No new Gradle dependencies. No version bump or release work.
- The migration DDL must match Room's expected schema exactly (names, columns, indices) — a mismatch crashes the app on open for upgraded installs.
- Repo rule: never commit/push. End each task with a build and a `git diff --stat` staging check only.

## Review Focus

1. Upgraded install (existing v3 data) — old expenses intact, the 5 types seeded, Investment tab reads ₹0 / ₹0.
2. Month change on the Investment tab — balances stay put, only the list re-scopes; the drill-down shows that selected month only.
3. Savings "out" exceeding the balance — the balance renders negative (e.g. −₹500) everywhere without a crash.
4. Empty states — fresh install (₹0 / ₹0 + empty text), an empty month, and an empty drill-down all show friendly text, never a blank.
5. Isolation + FAB context — no investment row or total ever changes Home/spending; the FAB opens Add Expense on Spending/Income and Add Investment on the Investment tab.

Each item is pinned as an explicit assertion in Task 9.

---

### Task 1: Schema — entities, DAO, migration 3→4, seed

**Files:**
- Create: `app/src/main/java/in/pocket/pocket/data/local/entity/InvestmentTypeEntity.kt`
- Create: `app/src/main/java/in/pocket/pocket/data/local/entity/InvestmentEntryEntity.kt`
- Create: `app/src/main/java/in/pocket/pocket/data/local/dao/InvestmentDao.kt`
- Modify: `app/src/main/java/in/pocket/pocket/data/local/PocketDatabase.kt`
- Modify: `app/src/main/java/in/pocket/pocket/data/local/SeedData.kt`
- Test: build here; migration verified on device in Task 9 (Review Focus 1)

**Interfaces:**
- Produces: `InvestmentTypeEntity`, `InvestmentEntryEntity`, `InvestmentDao` (all used by Tasks 2, 4, 5, 7). `kind` values are the strings `"SAVING"` / `"INVESTMENT"`; `direction` values `"IN"` / `"OUT"`.

- [ ] **Step 1: Create `InvestmentTypeEntity.kt`**

```kotlin
package `in`.marxen.pocket.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "investment_types")
data class InvestmentTypeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now(),
)
```

- [ ] **Step 2: Create `InvestmentEntryEntity.kt`**

```kotlin
package `in`.marxen.pocket.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "investment_entries",
    foreignKeys = [
        ForeignKey(
            entity = InvestmentTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["type_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index("entry_date"),
        Index("type_id"),
        Index("kind", "entry_date"),
    ],
)
data class InvestmentEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val direction: String,
    @ColumnInfo(name = "type_id") val typeId: Long? = null,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    @ColumnInfo(name = "entry_date") val entryDate: LocalDate,
    val note: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now(),
    @ColumnInfo(name = "updated_at") val updatedAt: Instant = Instant.now(),
)
```

- [ ] **Step 3: Create `InvestmentDao.kt`**

```kotlin
package `in`.marxen.pocket.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import `in`.marxen.pocket.data.local.entity.InvestmentEntryEntity
import `in`.marxen.pocket.data.local.entity.InvestmentTypeEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface InvestmentDao {
    @Insert
    suspend fun insertEntry(entry: InvestmentEntryEntity): Long

    @Update
    suspend fun updateEntry(entry: InvestmentEntryEntity)

    @Query("SELECT * FROM investment_entries WHERE id = :id")
    suspend fun getEntryById(id: Long): InvestmentEntryEntity?

    @Query("SELECT * FROM investment_entries")
    suspend fun getAllEntriesSync(): List<InvestmentEntryEntity>

    @Query("DELETE FROM investment_entries")
    suspend fun deleteAllEntries()

    @Query("SELECT e.*, t.name AS type_name FROM investment_entries e LEFT JOIN investment_types t ON e.type_id = t.id WHERE e.entry_date BETWEEN :start AND :end ORDER BY e.entry_date DESC, e.created_at DESC")
    fun entriesBetween(start: LocalDate, end: LocalDate): Flow<List<InvestmentEntryWithType>>

    @Query("SELECT e.*, t.name AS type_name FROM investment_entries e LEFT JOIN investment_types t ON e.type_id = t.id WHERE e.kind = :kind AND e.entry_date BETWEEN :start AND :end ORDER BY e.entry_date DESC, e.created_at DESC")
    fun entriesBetweenByKind(kind: String, start: LocalDate, end: LocalDate): Flow<List<InvestmentEntryWithType>>

    @Query("SELECT COALESCE(SUM(CASE WHEN direction = 'IN' THEN amount_paise ELSE -amount_paise END), 0) FROM investment_entries WHERE kind = 'SAVING'")
    fun savingsBalance(): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount_paise), 0) FROM investment_entries WHERE kind = 'INVESTMENT'")
    fun investedTotal(): Flow<Long>

    @Insert
    suspend fun insertType(type: InvestmentTypeEntity): Long

    @Query("SELECT * FROM investment_types ORDER BY sort_order ASC, id ASC")
    fun allTypes(): Flow<List<InvestmentTypeEntity>>

    @Query("SELECT * FROM investment_types ORDER BY sort_order ASC, id ASC")
    suspend fun allTypesSync(): List<InvestmentTypeEntity>

    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM investment_types")
    suspend fun maxSortOrder(): Int

    @Query("DELETE FROM investment_types")
    suspend fun deleteAllTypes()
}

data class InvestmentEntryWithType(
    @Embedded val entry: InvestmentEntryEntity,
    @ColumnInfo(name = "type_name") val typeName: String?,
)
```

- [ ] **Step 4: Wire `PocketDatabase.kt` — version 4 + migration**

Add both entities to the `@Database(entities = [...])` list, bump `version = 4`, add `abstract fun investmentDao(): InvestmentDao`, and add:

```kotlin
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS investment_types (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                sort_order INTEGER NOT NULL,
                created_at INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS investment_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                kind TEXT NOT NULL,
                direction TEXT NOT NULL,
                type_id INTEGER,
                amount_paise INTEGER NOT NULL,
                entry_date TEXT NOT NULL,
                note TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                FOREIGN KEY (type_id) REFERENCES investment_types(id) ON DELETE NO_ACTION
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_investment_entries_entry_date ON investment_entries(entry_date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_investment_entries_type_id ON investment_entries(type_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_investment_entries_kind_entry_date ON investment_entries(kind, entry_date)")
        val now = System.currentTimeMillis()
        listOf("Stocks", "IPO", "Mutual fund", "SIP", "Other").forEachIndexed { order, name ->
            db.execSQL(
                "INSERT INTO investment_types (name, sort_order, created_at) VALUES (?, $order, $now)",
                arrayOf(name),
            )
        }
    }
}
```

Register it: `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)`.

- [ ] **Step 5: Seed fresh installs — `SeedData.kt`**

Append to `onCreate` (same `now` variable):

```kotlin
val investmentTypes = listOf("Stocks", "IPO", "Mutual fund", "SIP", "Other")
investmentTypes.forEachIndexed { order, name ->
    db.execSQL(
        "INSERT INTO investment_types (name, sort_order, created_at) VALUES (?, ?, $now)",
        arrayOf(name, order),
    )
}
```

- [ ] **Step 6: Build**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Stage check**

Run: `git diff --stat`
Expected: exactly the 5 files above touched/created.

---

### Task 2: Repository + container wiring

**Files:**
- Create: `app/src/main/java/in/pocket/pocket/data/repository/InvestmentRepository.kt`
- Modify: `app/src/main/java/in/pocket/pocket/data/local/AppContainer.kt`
- Test: none beyond build (pure pass-through)

**Interfaces:**
- Consumes: `InvestmentDao` from Task 1.
- Produces: `InvestmentRepository` with the exact members below (used by Tasks 3, 5, 7); `AppContainer.investmentRepository`.

- [ ] **Step 1: Create `InvestmentRepository.kt`**

```kotlin
package `in`.marxen.pocket.data.repository

import `in`.marxen.pocket.data.local.dao.InvestmentDao
import `in`.marxen.pocket.data.local.dao.InvestmentEntryWithType
import `in`.marxen.pocket.data.local.entity.InvestmentEntryEntity
import `in`.marxen.pocket.data.local.entity.InvestmentTypeEntity
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

class InvestmentRepository(private val dao: InvestmentDao) {
    fun getEntries(month: YearMonth): Flow<List<InvestmentEntryWithType>> =
        dao.entriesBetween(month.atDay(1), month.atEndOfMonth())

    fun getEntries(kind: String, month: YearMonth): Flow<List<InvestmentEntryWithType>> =
        dao.entriesBetweenByKind(kind, month.atDay(1), month.atEndOfMonth())

    fun getSavingsBalance(): Flow<Long> = dao.savingsBalance()

    fun getInvestedTotal(): Flow<Long> = dao.investedTotal()

    fun getTypes(): Flow<List<InvestmentTypeEntity>> = dao.allTypes()

    suspend fun getTypesSync(): List<InvestmentTypeEntity> = dao.allTypesSync()

    suspend fun getAllEntriesSync(): List<InvestmentEntryEntity> = dao.getAllEntriesSync()

    suspend fun addCustomType(name: String): Long =
        dao.insertType(InvestmentTypeEntity(name = name, sortOrder = dao.maxSortOrder() + 1))

    suspend fun getEntry(id: Long): InvestmentEntryEntity? = dao.getEntryById(id)

    suspend fun insertEntry(entry: InvestmentEntryEntity): Long = dao.insertEntry(entry)

    suspend fun updateEntry(entry: InvestmentEntryEntity) = dao.updateEntry(entry)
}
```

- [ ] **Step 2: Wire `AppContainer.kt`**

Add below `repository`:

```kotlin
val investmentRepository = InvestmentRepository(database.investmentDao())
```

(plus the import).

- [ ] **Step 3: Build + existing tests**

Run: `./gradlew assembleDebug :app:testDebugUnitTest --tests "*.BackupManagerCsvTest"`
Expected: `BUILD SUCCESSFUL`, 4/4 pass.

- [ ] **Step 4: Stage check**

Run: `git diff --stat`
Expected: only the 2 files above.

---

### Task 3: Icons + Add/Edit Investment screen + ViewModel

**Files:**
- Create: `app/src/main/res/drawable/ic_inv_stocks.xml`, `ic_inv_ipo.xml`, `ic_inv_mutual_fund.xml`, `ic_inv_sip.xml`, `ic_move_in.xml`, `ic_move_out.xml`
- Create: `app/src/main/java/in/pocket/pocket/ui/investment/AddInvestmentViewModel.kt`
- Create: `app/src/main/java/in/pocket/pocket/ui/investment/AddInvestmentScreen.kt`
- Test: build now; emulator drive in Task 9

**Interfaces:**
- Consumes: `InvestmentRepository`; mirror sources `AddExpenseScreen.kt` / `AddExpenseViewModel.kt` (read them first — copy structure, paddings, shapes, sizes 1:1).
- Produces: `AddInvestmentViewModel(entryId: Long? = null, onBack, onSaved)` screen used by Task 8. `AddInvestmentUiState` fields exactly as Step 2.

- [ ] **Step 1: Create the 6 vector drawables**

All six use this template (24dp viewport; stroke style, matching the mockups — `Icon(tint=…)` recolor overrides these baked colors):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:strokeColor="#2196F3"
        android:strokeWidth="1.8"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:fillColor="#00000000"
        android:pathData="M3.5,17.5 L9,12 L13,16 L20.5,8.5 M15.5,8.5 L20.5,8.5 L20.5,13.5"/>
</vector>
```

Per file — color and `pathData` (one `pathData` string per file; combine sub-paths with `M`):

| file | strokeColor | pathData |
|---|---|---|
| `ic_inv_stocks.xml` | `#2196F3` | `M3.5,17.5 L9,12 L13,16 L20.5,8.5 M15.5,8.5 L20.5,8.5 L20.5,13.5` |
| `ic_inv_ipo.xml` | `#9C27B0` | `M4,20.5 L20,20.5 M6,20.5 L6,11.5 M10,20.5 L10,11.5 M14,20.5 L14,11.5 M18,20.5 L18,11.5 M12,4 L20,9.5 L4,9.5 Z` |
| `ic_inv_mutual_fund.xml` | `#00897B` | `M12,3.5 A8.5,8.5 0 1 0 12,20.5 A8.5,8.5 0 1 0 12,3.5 M12,12 L12,3.5 M12,12 L19.5,16` |
| `ic_inv_sip.xml` | `#FF9800` | `M17,2.5 L21,6.5 L17,10.5 M3,11.5 L3,10.5 A4,4 0 0 1 7,6.5 L21,6.5 M7,21.5 L3,17.5 L7,13.5 M21,12.5 L21,13.5 A4,4 0 0 1 17,17.5 L3,17.5` |
| `ic_move_in.xml` | `#1A5C38` | `M12,3.5 A8.5,8.5 0 1 0 12,20.5 A8.5,8.5 0 1 0 12,3.5 M12,7.5 L12,16.5 M8.5,13 L12,16.5 L15.5,13` |
| `ic_move_out.xml` | `#FF9800` | `M12,3.5 A8.5,8.5 0 1 0 12,20.5 A8.5,8.5 0 1 0 12,3.5 M12,16.5 L12,7.5 M8.5,11 L12,7.5 L15.5,11` |

- [ ] **Step 2: Create `AddInvestmentViewModel.kt`**

```kotlin
data class AddInvestmentUiState(
    val amount: String = "",
    val kind: String = "SAVING",            // "SAVING" | "INVESTMENT"
    val direction: String = "IN",           // savings only: "IN" | "OUT"
    val selectedTypeId: Long? = null,
    val date: LocalDate = asiaKolkataToday(),
    val note: String = "",
    val isEditing: Boolean = false,
    val editingId: Long? = null,
    val amountError: String? = null,
    val typeError: String? = null,
    val detailsExpanded: Boolean = false,
    val showAddTypeDialog: Boolean = false,
    val customTypeName: String = "",
)
```

`class AddInvestmentViewModel(private val repository: InvestmentRepository)`:
- `uiState: StateFlow<AddInvestmentUiState>`, `types: StateFlow<List<InvestmentTypeEntity>>` = `repository.getTypes().stateIn(...)`, `saved: SharedFlow<Boolean>`.
- `updateAmount` — copy AddExpense's digit/one-dot/≤12 filter verbatim.
- `updateKind(kind)` — switching clears selection: `it.copy(kind = kind, selectedTypeId = null, typeError = null)`; direction stays.
- `selectDirection(direction)`, `selectType(id)` (clears `typeError`), `updateDate`, `updateNote`, `toggleDetails`.
- `showAddTypeDialog` / `dismissAddTypeDialog` / `updateCustomTypeName` — copy AddExpense's category-dialog trio.
- `addCustomType()` — copy AddExpense's `addCustomCategory` shape but call `repository.addCustomType(name)`; on success set `selectedTypeId = id`, close dialog, clear name.
- `loadEntry(id: Long)` — `repository.getEntry(id)`; prefill: `amount = (e.amountPaise / 100.0).toString()`, `kind`, `direction`, `selectedTypeId = e.typeId`, `date = e.entryDate`, `note = e.note ?: ""`, `isEditing = true`, `editingId = e.id`.
- `save()` — validate amount exactly like AddExpense (3 messages); if `kind == "INVESTMENT" && selectedTypeId == null` → `typeError = "Select a type"`; then build `InvestmentEntryEntity(kind, direction = if (kind == "SAVING") direction else "IN", typeId = if (kind == "INVESTMENT") selectedTypeId else null, amountPaise = rupeesToPaise(amount), entryDate = date, note = note.ifBlank { null })`; for edit: `getEntry(editingId)` then `updateEntry(existing.copy(... same fields ..., updatedAt = Instant.now()))`; else `insertEntry`; emit `saved`.
- `class Factory(private val repository: InvestmentRepository)` mirroring the others.

- [ ] **Step 3: Create `AddInvestmentScreen.kt`**

Signature: `fun AddInvestmentScreen(entryId: Long? = null, onBack: () -> Unit = {}, onSaved: () -> Unit = {}, viewModel: AddInvestmentViewModel = viewModel(factory = AddInvestmentViewModel.Factory((LocalContext.current.applicationContext as `in`.marxen.pocket.PocketApplication).container.investmentRepository)))`.

Mirror `AddExpenseScreen` block-for-block (same paddings, `PocketBeige` bg, `verticalScroll`, 20.dp horizontal), with these exact deltas:

1. Title: `if (state.isEditing) "Edit Investment" else "Add Investment"`.
2. Toggle over `listOf("SAVING", "INVESTMENT")`; label `if (type == "SAVING") "Savings" else "Investments"`; selected colors `PocketGreen` for SAVING, `Color(0xFF2E7D32)` for INVESTMENT (same pair as Expense/Income); `onClick = { viewModel.updateKind(type) }`.
3. Section label: `if (state.kind == "SAVING") "Move" else "Type"`; render `state.typeError` under it exactly like `categoryError` (bodySmall, `colorScheme.error`).
4. SAVING mode — replace the category grid with a `Row` of 2 tiles (`weight(1f)`, 12.dp spacing), same tile anatomy as the category tile (56.dp box, rounded 16, tinted `alpha 0.15f`, 28.dp icon, `labelSmall`, selected = 2.dp `PocketGreen` border):
   - "To secondary" — `R.drawable.ic_move_in`, tint `PocketGreen`; onClick `selectDirection("IN")`.
   - "From secondary" — `R.drawable.ic_move_out`, tint `Color(0xFFFF9800)`; onClick `selectDirection("OUT")`.
   - Selected check: `state.direction == "IN"` / `"OUT"`.
5. INVESTMENT mode — grid over `types` (chunked(3), copy the category-grid code) with local maps:
   - icons: `"stocks"→ic_inv_stocks`, `"ipo"→ic_inv_ipo`, `"mutual fund"→ic_inv_mutual_fund`, `"sip"→ic_inv_sip`, `"other"→ic_other`, else `ic_other`.
   - tints: `"stocks"→Color(0xFF2196F3)`, `"ipo"→Color(0xFF9C27B0)`, `"mutual fund"→Color(0xFF00897B)`, `"sip"→Color(0xFFFF9800)`, else `Color(0xFF607D8B)`.
   - selected = `state.selectedTypeId == type.id` (2.dp PocketGreen border), onClick `selectType(type.id)`.
   - `isOther = type.name.lowercase() == "other"` → after the label add the small `Icons.Filled.Add` (16.dp, `PocketGreen`, contentDescription "Add custom type") opening `viewModel.showAddTypeDialog()` — identical to the category "+".
6. No subcategory row.
7. Date row + `DatePickerDialog` + "More details (optional)" + `Note` field + button: identical code; button text `if (state.isEditing) "Update Investment" else "Save Investment"`.
8. `LaunchedEffect(entryId)` → `viewModel.loadEntry(id)` when `> 0`; `LaunchedEffect(Unit)` → collect `saved` → `onSaved()`.
9. Dialog: title "Add Custom Type", field label "Type name", buttons Add / Cancel — mirror the category dialog.

- [ ] **Step 4: Build**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Stage check**

Run: `git diff --stat`
Expected: only the 6 drawables + 2 Kotlin files.

---

### Task 4: Backup — investment tables in the JSON backup/restore

**Files:**
- Modify: `app/src/main/java/in/pocket/pocket/data/backup/BackupModels.kt`
- Modify: `app/src/main/java/in/pocket/pocket/data/backup/BackupManager.kt`
- Test: build; manual SAF round-trip noted for Task 9

**Interfaces:**
- Consumes: `InvestmentRepository.getAllEntriesSync()/getTypesSync()` (or the DAO directly) and `InvestmentDao.deleteAllEntries()/deleteAllTypes()/insertAll`-equivalents from Task 1.
- Produces: `PocketBackup.investmentTypes` / `PocketBackup.investmentEntries` (defaulted, so old backups still restore).

- [ ] **Step 1: Extend `BackupModels.kt`**

Add to `PocketBackup` (with defaults so schemaVersion stays 1 and old backups decode):

```kotlin
val investmentTypes: List<SerializableInvestmentTypeEntity> = emptyList(),
val investmentEntries: List<SerializableInvestmentEntryEntity> = emptyList(),
```

and the serializables:

```kotlin
@Serializable
data class SerializableInvestmentTypeEntity(
    val id: Long,
    val name: String,
    val sortOrder: Int,
    @Serializable(with = InstantSerializer::class)
    val createdAt: java.time.Instant,
)

@Serializable
data class SerializableInvestmentEntryEntity(
    val id: Long,
    val kind: String,
    val direction: String,
    val typeId: Long?,
    val amountPaise: Long,
    @Serializable(with = LocalDateSerializer::class)
    val entryDate: java.time.LocalDate,
    val note: String?,
    @Serializable(with = InstantSerializer::class)
    val createdAt: java.time.Instant,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: java.time.Instant,
)
```

- [ ] **Step 2: Extend `BackupManager.kt`**

- `createBackup`: fetch `database.investmentDao().allTypesSync()` and `getAllEntriesSync()`, map with new `toSerializable()` helpers (mirror the existing ones), pass into `PocketBackup`.
- `restoreBackup`, inside the existing `withTransaction`: after `transactionDao().deleteAll()` add `investmentDao().deleteAllEntries()` then `investmentDao().deleteAllTypes()`; after inserting the existing lists add `investmentDao().insertType(...)` for each `backup.investmentTypes`, then `insertEntry(...)` for each `backup.investmentEntries` (types before entries — FK order).
- Add the four `toSerializable()` / `toEntity()` helpers for the two new serializables (mirror existing style).

- [ ] **Step 3: Build + existing tests**

Run: `./gradlew assembleDebug :app:testDebugUnitTest --tests "*.BackupManagerCsvTest"`
Expected: `BUILD SUCCESSFUL`, 4/4 pass.

- [ ] **Step 4: Stage check**

Run: `git diff --stat`
Expected: only the 2 files above.

---

### Task 5: Row model (unit-tested) + Insights data plumbing

**Files:**
- Create: `app/src/test/java/in/pocket/pocket/ui/investment/InvestmentModelsTest.kt`
- Create: `app/src/main/java/in/pocket/pocket/ui/investment/InvestmentModels.kt`
- Modify: `app/src/main/java/in/pocket/pocket/ui/insights/InsightsViewModel.kt`
- Modify: `app/src/main/java/in/pocket/pocket/ui/insights/InsightsScreen.kt` (viewModel factory args only, lines 65–69)
- Test: `InvestmentModelsTest` (new)

**Interfaces:**
- Consumes: `InvestmentRepository`, `InvestmentEntryWithType`.
- Produces: `InvestmentRowUi` + `InvestmentEntryWithType.toRowUi()` (used by Tasks 6, 7); `InsightsUiState.savingsBalanceFormatted`, `.investedTotalFormatted`, `.investmentRows`; `InsightsViewModel.Factory(repository, investmentRepository)`.

- [ ] **Step 1: Write the failing test `InvestmentModelsTest.kt`**

```kotlin
package `in`.marxen.pocket.ui.investment

import `in`.marxen.pocket.data.local.dao.InvestmentEntryWithType
import `in`.marxen.pocket.data.local.entity.InvestmentEntryEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InvestmentModelsTest {
    private fun row(
        kind: String = "SAVING",
        direction: String = "IN",
        typeName: String? = null,
        note: String? = null,
    ) = InvestmentEntryWithType(
        entry = InvestmentEntryEntity(
            kind = kind,
            direction = direction,
            typeId = if (typeName != null) 1L else null,
            amountPaise = 120000,
            entryDate = LocalDate.of(2026, 10, 12),
            note = note,
        ),
        typeName = typeName,
    ).toRowUi()

    @Test
    fun savingsIn_isToSecondaryWithPlus() {
        val r = row(direction = "IN")
        assertEquals("To secondary", r.title)
        assertEquals("+₹1,200.00", r.amountFormatted)
        assertFalse(r.isOut)
    }

    @Test
    fun savingsOut_isFromSecondaryWithMinus() {
        val r = row(direction = "OUT")
        assertEquals("From secondary", r.title)
        assertTrue(r.isOut)
    }

    @Test
    fun investment_usesTypeName_fallsBackToOther() {
        assertEquals("Stocks", row(kind = "INVESTMENT", typeName = "Stocks").title)
        assertEquals("Other", row(kind = "INVESTMENT", typeName = null).title)
    }

    @Test
    fun subtitle_joinsDateAndNote_skipsBlankNote() {
        assertEquals("12 Oct 2026 · Tata Motors", row(note = "Tata Motors").subtitle)
        assertEquals("12 Oct 2026", row(note = "  ").subtitle)
    }
}
```

Note: these two money strings pin the sign prefix; `formatPaiseAsRupees` (en-IN INR) prints two decimals and is the source of truth — if a run shows a slightly different exact string, copy the helper's exact output into the two amount assertions and keep going.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*.InvestmentModelsTest"`
Expected: FAIL (unresolved reference `toRowUi` / `InvestmentRowUi`).

- [ ] **Step 3: Implement `InvestmentModels.kt`**

```kotlin
package `in`.marxen.pocket.ui.investment

import `in`.marxen.pocket.core.money.formatPaiseAsRupees
import `in`.marxen.pocket.data.local.dao.InvestmentEntryWithType
import java.time.format.DateTimeFormatter
import java.util.Locale

data class InvestmentRowUi(
    val id: Long,
    val kind: String,
    val isOut: Boolean,
    val title: String,
    val subtitle: String,
    val amountFormatted: String,
)

private val rowDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

fun InvestmentEntryWithType.toRowUi(): InvestmentRowUi {
    val isSavings = entry.kind == "SAVING"
    val isOut = isSavings && entry.direction == "OUT"
    val title = if (isSavings) {
        if (isOut) "From secondary" else "To secondary"
    } else {
        typeName?.takeIf { it.isNotBlank() } ?: "Other"
    }
    val subtitle = listOfNotNull(
        entry.entryDate.format(rowDateFormatter),
        entry.note?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    val sign = if (isOut) "\u2212" else "+"
    return InvestmentRowUi(
        id = entry.id,
        kind = entry.kind,
        isOut = isOut,
        title = title,
        subtitle = subtitle,
        amountFormatted = sign + formatPaiseAsRupees(entry.amountPaise),
    )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*.InvestmentModelsTest"`
Expected: PASS (4/4; the two money strings must match the helper's exact output).

- [ ] **Step 5: Extend `InsightsViewModel.kt`**

- `InsightsUiState` += `savingsBalanceFormatted: String = "₹0"`, `investedTotalFormatted: String = "₹0"`, `investmentRows: List<InvestmentRowUi> = emptyList()`. (Keep `selectedTab` for now — it moves to the nav host in Task 6.)
- Add a private data class `InvestmentData(val entries: List<InvestmentEntryWithType>, val savingsBalance: Long, val investedTotal: Long)` and:

```kotlin
private val investmentData = combine(
    _selectedMonth.flatMapLatest { m -> investmentRepository.getEntries(m) },
    investmentRepository.getSavingsBalance(),
    investmentRepository.getInvestedTotal(),
) { entries, savingsBalance, investedTotal -> InvestmentData(entries, savingsBalance, investedTotal) }
```

- Add `investmentRepository` as the 2nd constructor param + Factory param. Swap the main `combine`'s 5th argument from `_selectedTab` to `investmentData` (this keeps the typed 5-flow overload and keeps the build green), drop `selectedTab = selectedTab` from the main builder, and move the tab into the existing secondary combine: `.combine(_monthlyTrends, _selectedTab) { state, trends, tab -> state.copy(monthlyTrend = trends, selectedTab = tab) }`. Fill the three new state fields (`formatPaiseAsRupees` for both balances, `entries.map { it.toRowUi() }` for rows).

- [ ] **Step 6: Update `InsightsScreen.kt` factory call site**

Add `(LocalContext.current.applicationContext as `in`.marxen.pocket.PocketApplication).container.investmentRepository` as the second factory argument.

- [ ] **Step 7: Build + tests**

Run: `./gradlew assembleDebug :app:testDebugUnitTest --tests "*.InvestmentModelsTest"`
Expected: `BUILD SUCCESSFUL`, 4/4 pass.

- [ ] **Step 8: Stage check**

Run: `git diff --stat`
Expected: only the 4 files above.

---

### Task 6: Investment tab UI + hoist the selected tab

**Files:**
- Create: `app/src/main/java/in/pocket/pocket/ui/investment/InvestmentTab.kt`
- Modify: `app/src/main/java/in/pocket/pocket/ui/insights/InsightsScreen.kt`
- Modify: `app/src/main/java/in/pocket/pocket/ui/insights/InsightsViewModel.kt`
- Modify: `app/src/main/java/in/pocket/pocket/navigation/PocketNavHost.kt` (call site only — FAB logic is Task 8)
- Test: emulator drive in Task 9 (Review Focus 2, 4)

**Interfaces:**
- Consumes: `InvestmentRowUi`, `InsightsUiState` fields (Task 5).
- Produces: `InvestmentTab(state, onBalanceClick: (String) -> Unit, onEntryClick: (Long) -> Unit)`; `InsightsScreen(onSeeAllCategories, selectedTab: Int = 0, onSelectTab: (Int) -> Unit = {}, onInvestmentLedgerClick: (String, YearMonth) -> Unit = { _, _ -> }, onInvestmentEntryClick: (Long) -> Unit = {}, viewModel = …)`.

- [ ] **Step 1: Create `InvestmentTab.kt`**

`@Composable fun InvestmentTab(state: InsightsUiState, onBalanceClick: (String) -> Unit = {}, onEntryClick: (Long) -> Unit = {})` — a `Column(verticalArrangement = Arrangement.spacedBy(12.dp))`:

1. Balance card: `Card` fillMaxWidth, `RoundedCornerShape(16.dp)`, `containerColor = PocketGreen`, `Row(padding 20.dp)`:
   - Left `Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onBalanceClick("SAVING") })`: `"Secondary account"` (`labelLarge`, white 0.8f); value `state.savingsBalanceFormatted` (`displaySmall`, Bold, White) with a trailing `Icon(painterResource(R.drawable.ic_chevron_right), tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))` on the same Row.
   - `Box(Modifier.width(1.dp).height(44.dp).background(Color.White.copy(alpha = 0.25f)))`.
   - Right `Column` the same way: `"Invested"`, `state.investedTotalFormatted`, `onBalanceClick("INVESTMENT")`.
2. Month label: `state.month.month.name.uppercase(Locale.ENGLISH)` — `labelSmall`, `PocketTextTertiary`, `letterSpacing = 1.sp`.
3. Rows — for each `state.investmentRows`, a full-width Row `.clickable { onEntryClick(row.id) }`:
   - 36.dp rounded-12 tinted box (`color.copy(alpha = 0.15f)`) + 20.dp icon: SAVING → `ic_move_in` (PocketGreen) / `ic_move_out` (0xFFFF9800); INVESTMENT → the Task-3 icon/tint map keyed by `row.title.lowercase()` (fallback `ic_other` / 0xFF607D8B).
   - `Column(weight(1f))`: `row.title` (`bodyMedium`, SemiBold, `PocketText`), `row.subtitle` (`bodySmall`, `PocketTextSecondary`).
   - Amount right-aligned: `row.amountFormatted` (`bodyMedium`, SemiBold, color = `if (row.isOut) Color(0xFFFF9800) else PocketGreen`).
4. Empty: when `investmentRows.isEmpty()` → `Text("No entries this month", bodyMedium, PocketTextSecondary, Modifier.padding(vertical = 16.dp))`.

- [ ] **Step 2: Rework `InsightsScreen.kt`**

- Signature as in **Interfaces**; `tabs = listOf("Spending", "Income", "Investment")`.
- `TabRow(selectedTabIndex = selectedTab)`; `Tab(selected = selectedTab == index, onClick = { onSelectTab(index) }, …)`; the `when (selectedTab)` branch: `2 -> InvestmentTab(state = state, onBalanceClick = { kind -> onInvestmentLedgerClick(kind, state.month) }, onEntryClick = onInvestmentEntryClick)`.
- Delete `TrendsTab` (lines 490–517) and delete the "See all" `Text` on the Monthly Trend block (lines 309–314) plus the now-unused `onSeeAllClick` parameter (lines 204, 313). Keep the `MonthlyTrendChart` on the Spending tab untouched.
- Remove `selectedTab` from every `state.` read in this file (use the param).

- [ ] **Step 3: Drop `selectedTab` from `InsightsViewModel.kt`**

Remove the `selectedTab` field from `InsightsUiState`, `_selectedTab`, `selectTab()`, and the `_selectedTab` slot in the secondary combine — collapse it back to the two-argument form `.combine(_monthlyTrends) { state, trends -> state.copy(monthlyTrend = trends) }`.

- [ ] **Step 4: Wire the hoisted state in `PocketNavHost.kt`**

In `PocketNavHost` add `var insightsTab by rememberSaveable { mutableIntStateOf(0) }` (imports `androidx.compose.runtime.saveable.rememberSaveable`, `androidx.compose.runtime.mutableIntStateOf`, `setValue`), and update the INSIGHTS composable:

```kotlin
InsightsScreen(
    onSeeAllCategories = { month -> navController.navigate(Routes.categoryListRoute(month)) },
    selectedTab = insightsTab,
    onSelectTab = { insightsTab = it },
)
```

(The two new investment callbacks stay at their default no-ops until Task 8.)

- [ ] **Step 5: Build**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Stage check**

Run: `git diff --stat`
Expected: only the 4 files above.

---

### Task 7: Drill-down ledger screen + ViewModel

**Files:**
- Create: `app/src/main/java/in/pocket/pocket/ui/investment/InvestmentLedgerViewModel.kt`
- Create: `app/src/main/java/in/pocket/pocket/ui/investment/InvestmentLedgerScreen.kt`
- Test: emulator drive in Task 9 (Review Focus 2, 3, 4)

**Interfaces:**
- Consumes: `InvestmentRepository`, `toRowUi()`.
- Produces: `InvestmentLedgerScreen(kind: String, month: YearMonth, onBack, onEntryClick: (Long) -> Unit = {}, viewModel = …)` used by Task 8.

- [ ] **Step 1: Create `InvestmentLedgerViewModel.kt`**

```kotlin
data class InvestmentLedgerUiState(
    val title: String = "",
    val totalLabel: String = "",
    val totalFormatted: String = "₹0",
    val monthLine: String = "",
    val rows: List<InvestmentRowUi> = emptyList(),
)

class InvestmentLedgerViewModel(
    repository: InvestmentRepository,
    kind: String,
    initialMonth: YearMonth,
) : ViewModel() {
    val uiState: StateFlow<InvestmentLedgerUiState> = combine(
        if (kind == "SAVING") repository.getSavingsBalance() else repository.getInvestedTotal(),
        repository.getEntries(kind, initialMonth),
    ) { total, entries ->
        val monthSum = entries.sumOf { e ->
            if (e.entry.kind == "SAVING" && e.entry.direction == "OUT") -e.entry.amountPaise else e.entry.amountPaise
        }
        val monthName = initialMonth.month.name.replaceFirstChar { it.titlecase() }
        InvestmentLedgerUiState(
            title = if (kind == "SAVING") "Secondary account" else "Investments",
            totalLabel = if (kind == "SAVING") "Balance" else "Invested",
            totalFormatted = formatPaiseAsRupees(total),
            monthLine = if (kind == "SAVING") {
                "$monthName · net ${if (monthSum < 0) "\u2212" else "+"}${formatPaiseAsRupees(kotlin.math.abs(monthSum))}"
            } else {
                "$monthName · ${formatPaiseAsRupees(monthSum)} added"
            },
            rows = entries.map { it.toRowUi() },
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InvestmentLedgerUiState())
    // + Factory(repository, kind, initialMonth), mirroring SubcategoryTransactionsViewModel
}
```

- [ ] **Step 2: Create `InvestmentLedgerScreen.kt`**

Mirror `SubcategoryTransactionsScreen` block-for-block (LazyColumn, 16.dp horizontal padding, back icon + centered bold title, sub-line, green `Card` with `padding(20.dp)`, rows with title/subtitle left + amount right, 80.dp bottom spacer) with these values:
- Title = `state.title`; sub-line = `month.month.name + " " + month.year` (`bodySmall`/`bodyLarge` gray, centered — same as the subcategory screen).
- Card: `state.totalLabel` (`labelLarge`, white 0.8f), `state.totalFormatted` (`displaySmall`, Bold, White), `state.monthLine` below (`bodySmall`, white 0.75f).
- Rows: `Card(colors = Transparent, onClick = { onEntryClick(row.id) })` → `row.title` / `row.subtitle` / `row.amountFormatted` colored `if (row.isOut) Color(0xFFFF9800) else PocketGreen`.
- Empty text: `"No savings moves this month"` for SAVING, `"No investments this month"` for INVESTMENT.
- ViewModel factory call mirrors the subcategory screen (container.investmentRepository).
- Every text gets `maxLines = 1` + `TextOverflow.Ellipsis` where the subcategory screen has it.

- [ ] **Step 3: Build**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Stage check**

Run: `git diff --stat`
Expected: only the 2 files above.

---

### Task 8: Navigation + FAB branching

**Files:**
- Modify: `app/src/main/java/in/pocket/pocket/navigation/Routes.kt`
- Modify: `app/src/main/java/in/pocket/pocket/navigation/PocketNavHost.kt`
- Test: emulator drive in Task 9 (Review Focus 5)

**Interfaces:**
- Consumes: `AddInvestmentScreen`, `InvestmentLedgerScreen` (Tasks 3, 7); `insightsTab` (Task 6).
- Produces: complete user-facing flow.

- [ ] **Step 1: Add routes in `Routes.kt`**

```kotlin
const val ADD_INVESTMENT = "investment/add"
const val EDIT_INVESTMENT = "investment/edit/{entryId}"
const val INVESTMENT_LEDGER = "investment/ledger/{kind}?month={month}"

fun investmentEditRoute(entryId: Long) = "investment/edit/$entryId"
fun investmentLedgerRoute(kind: String, month: YearMonth = YearMonth.now()) =
    "investment/ledger/$kind?month=$month"
```

- [ ] **Step 2: Branch the FAB in `PocketNavHost.kt`**

```kotlin
floatingActionButton = {
    if (showBottomBar) {
        val investmentFab = currentRoute == Routes.INSIGHTS && insightsTab == 2
        FloatingActionButton(
            onClick = {
                navController.navigate(if (investmentFab) Routes.ADD_INVESTMENT else Routes.ADD)
            },
            containerColor = MaterialTheme.colorScheme.primary,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = if (investmentFab) "Add Investment" else "Add Expense",
                tint = Color.White,
            )
        }
    }
},
```

- [ ] **Step 3: Wire the Insights screen callbacks**

```kotlin
InsightsScreen(
    onSeeAllCategories = { month -> navController.navigate(Routes.categoryListRoute(month)) },
    selectedTab = insightsTab,
    onSelectTab = { insightsTab = it },
    onInvestmentLedgerClick = { kind, month ->
        navController.navigate(Routes.investmentLedgerRoute(kind, month))
    },
    onInvestmentEntryClick = { id -> navController.navigate(Routes.investmentEditRoute(id)) },
)
```

- [ ] **Step 4: Register the three composables (after `SUBCATEGORY_TRANSACTIONS`, keep default 150 ms push fades)**

```kotlin
composable(Routes.ADD_INVESTMENT) {
    AddInvestmentScreen(
        onBack = { navController.popBackStack() },
        onSaved = { navController.popBackStack() },
    )
}
composable(
    route = Routes.EDIT_INVESTMENT,
    arguments = listOf(navArgument("entryId") { type = NavType.LongType }),
) { backStackEntry ->
    AddInvestmentScreen(
        entryId = backStackEntry.arguments?.getLong("entryId"),
        onBack = { navController.popBackStack() },
        onSaved = { navController.popBackStack() },
    )
}
composable(
    route = Routes.INVESTMENT_LEDGER,
    arguments = listOf(
        navArgument("kind") { type = NavType.StringType; defaultValue = "SAVING" },
        navArgument("month") { type = NavType.StringType; defaultValue = java.time.YearMonth.now().toString() },
    ),
) { backStackEntry ->
    val kind = backStackEntry.arguments?.getString("kind") ?: "SAVING"
    val month = backStackEntry.arguments?.getString("month")?.let { java.time.YearMonth.parse(it) }
        ?: java.time.YearMonth.now()
    InvestmentLedgerScreen(
        kind = kind,
        month = month,
        onBack = { navController.popBackStack() },
        onEntryClick = { id -> navController.navigate(Routes.investmentEditRoute(id)) },
    )
}
```

plus the two screen imports.

- [ ] **Step 5: Build**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Stage check**

Run: `git diff --stat`
Expected: only the 2 files above.

---

### Task 9: Verify-every-change pipeline (AGENTS.md)

**Files:** none (device ops only). Emulator `emulator-5554`; taps must be `adb -s emulator-5554 shell input touchscreen tap X Y`; locate every target from a fresh `uiautomator dump`; assert text after each action.

- [ ] **Step 1: Upgrade check (Review Focus 1)**

If the app is already installed: `./gradlew installDebug`, launch, and assert from dumps — old expenses still on Home with correct totals, Insights → Investment shows ₹0 / ₹0 and "No entries this month". (This proves MIGRATION_3_4 against real v3 data.) If not installed, note it and continue.

- [ ] **Step 2: Fresh install + pass Welcome**

`adb -s emulator-5554 uninstall in.marxen.pocket`; `./gradlew installDebug`; `am start -n in.marxen.pocket/.MainActivity`; Skip via tap (540,1790).

- [ ] **Step 3: Empty state first (Review Focus 4)**

Insights (tab 675,2278) → Investment tab → assert ₹0 / ₹0 card and the empty-month text; open September via the month dropdown → drill into "Secondary account" → assert empty text. Back out.

- [ ] **Step 4: Seed the ledger — savings first**

FAB → assert title "Add Investment", chips "Savings"/"Investments", tiles "To secondary"/"From secondary". Add To secondary ₹4000 (leave the date as today), assert balance ₹4,000; add From secondary ₹500, assert ₹3,500 — after each save the Insights tab must still show Investment selected.

- [ ] **Step 5: Seed — investments**

FAB → Investments chip → assert tiles Stocks / IPO / Mutual fund / SIP / Other+. Add Stocks ₹1,200 with note "Tata Motors"; add Mutual fund ₹1,000; add IPO ₹2,300. Assert "Invested" = ₹4,500 and rows show type names + notes.

- [ ] **Step 6: Month dropdown + drill-downs (Review Focus 2)**

Switch to September → list empty, balances unchanged (₹3,500 / ₹4,500); switch back. Tap "Secondary account" → assert title "Secondary account", sub-line October 2026, card "Balance ₹3,500", rows only savings (2), amber −₹500; back. Tap "Invested" → assert rows only investments (3); back.

- [ ] **Step 7: Edit + negative balance (Review Focus 3)**

Tap a drill row → assert "Edit Investment" prefilled → change amount → "Update Investment" → totals update. Then add From secondary ₹4,000 → assert balance shows −₹500 on card and tab; no crash.

- [ ] **Step 8: Isolation + FAB context (Review Focus 5)**

Add one normal expense via Add Expense (e.g. Food ₹250) → Home total counts only that expense; Calendar shows no investment rows. Insights → Spending tab → FAB opens "Add Expense"; Income tab → same; Investment tab → FAB opens "Add Investment"; back → Investment still selected. Home/Calendar/Settings FABs unchanged.

- [ ] **Step 9: Rendering check**

`adb -s emulator-5554 shell dumpsys gfxinfo in.marxen.pocket reset`, switch tabs / open add screen / scroll lists, then confirm `Total frames rendered` freezes within a few frames and `Slow UI thread` stays 0.

- [ ] **Step 10: Safety sweep**

Settings → Manage Data: open the delete dialog and **cancel** (never confirm destructive). Settings → Backup: open the SAF sheet to confirm it launches, then cancel (full JSON round-trip is a manual check — the code paths are covered by Task 4's build + review). Long-note row (paste a long note on one entry) wraps without overlap.

---

## Non-goals (conscious, from the walkthrough)

- CSV export stays expense/income-only; investments ship in the JSON backup only.
- No Home/Calendar/transaction changes; no "money sent to other people" ledger (the "From secondary" move carries it for now).
- No version bump / release chores in this plan.
