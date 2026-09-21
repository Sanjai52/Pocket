# Pocket — Subcategory Feature

## 1. Feature Overview

Pocket currently supports a flat category system:

    Food
    Groceries
    Transport
    Shopping
    Bills
    Entertainment
    etc.

This feature introduces an optional second level:

    Food
      ├── Restaurants
      ├── Coffee
      ├── Snacks
      ├── Fast Food
      └── Other

    Transport
      ├── Fuel
      ├── Bus
      ├── Metro
      ├── Cab
      ├── Parking
      └── Other

    Shopping
      ├── Clothing
      ├── Electronics
      ├── Household
      ├── Personal
      └── Other

The feature is intentionally designed to add analytical depth without
adding friction to normal expense entry.

Core principle:

    Capture quickly. Analyze deeply.

Rules:

    Category       = required
    Subcategory    = optional
    Maximum depth  = 2 levels

A transaction can therefore be:

    Category = Food
    Subcategory = NULL

or:

    Category = Food
    Subcategory = Coffee


---

# 2. Updated UX Direction

The subcategory system follows the existing Pocket visual language.

Important UI decisions:

1. Keep the existing square-box category selector.
2. Do NOT replace the existing category UI with a new list-based UI.
3. Double-tapping a selected category opens the subcategory overlay.
4. The subcategory overlay is contextual to the selected category.
5. Selecting a subcategory returns the user to the Add Expense screen.
6. "New subcategory" is available directly inside the subcategory overlay.
7. Creating a new subcategory uses a small secondary overlay.
8. The new subcategory is immediately selected after creation.
9. Merchant field is removed from the Add Expense screen.
10. Note remains available as the optional free-text field.
11. Insights use a dedicated category-level drill-down screen.
12. Category Insights display:
       - total category amount
       - bar chart
       - subcategory list
       - amount per subcategory
       - percentage per subcategory
13. Tapping a subcategory opens its transaction list.

The overall experience should feel like an extension of Pocket,
not a new feature bolted onto the application.


---

# 3. Final Add Expense UX

The Add Expense screen remains visually based on the existing
Pocket square-box category design.

Conceptual screen:

    ┌─────────────────────────────────────┐
    │ ←             Add Expense           │
    │                                     │
    │ Amount                              │
    │ ₹250                                │
    │                                     │
    │ ┌─────────────────────────────────┐ │
    │ │ 🍔  Food                     ›  │ │
    │ │     Double tap for subcategory  │ │
    │ └─────────────────────────────────┘ │
    │                                     │
    │ Payment Method                      │
    │ ┌─────────────────────────────────┐ │
    │ │ 💳  UPI                      ›  │ │
    │ └─────────────────────────────────┘ │
    │                                     │
    │ Date                                │
    │ ┌─────────────────────────────────┐ │
    │ │ 📅 21 Sep 2026               › │ │
    │ └─────────────────────────────────┘ │
    │                                     │
    │ Note (Optional)                     │
    │ ┌─────────────────────────────────┐ │
    │ │ Add a note...                   │ │
    │ └─────────────────────────────────┘ │
    │                                     │
    │ ┌─────────────────────────────────┐ │
    │ │          Save Expense            │ │
    │ └─────────────────────────────────┘ │
    └─────────────────────────────────────┘

There is NO merchant field.

The transaction still supports:

    Amount
    Category
    Subcategory
    Payment Method
    Date
    Note


---

# 4. Category Selection

The existing square-box category selector remains unchanged.

Example:

    ┌────────────┐  ┌────────────┐
    │    🍔      │  │    🛒      │
    │   Food     │  │ Groceries  │
    └────────────┘  └────────────┘

    ┌────────────┐  ┌────────────┐
    │    🚗      │  │    🛍      │
    │ Transport  │  │ Shopping   │
    └────────────┘  └────────────┘

    ┌────────────┐  ┌────────────┐
    │    🧾      │  │    🎬      │
    │   Bills    │  │Entertainment│
    └────────────┘  └────────────┘

Do not redesign this into a different component.

The existing category selection interaction remains the primary
interaction.


---

# 5. Double-Tap Subcategory Interaction

After a category has been selected:

    Food

the category square remains selected.

A double tap on the selected category opens:

    Select Subcategory

This prevents the subcategory system from adding a permanent extra
field to every expense.

Conceptual behavior:

    Single tap
        ↓
    Select Food

    Double tap on Food
        ↓
    Open Food subcategory overlay


The UI should visually communicate that the selected category can
be double-tapped for more detail.

Use lightweight helper text only if necessary:

    Double tap for subcategory


Do not add intrusive tutorials or dialogs.


---

# 6. Subcategory Overlay

When the user double-taps:

    Food

open a modal bottom-sheet/overlay.

Example:

    ┌─────────────────────────────────────┐
    │ ×       Select Subcategory          │
    │                                     │
    │ 🍔 Food                             │
    │    Choose a subcategory             │
    │                                     │
    │ ┌─────────────────────────────────┐ │
    │ │ 🎉 No subcategory            ○  │ │
    │ └─────────────────────────────────┘ │
    │                                     │
    │ 🍽 Restaurants                   ○  │
    │ ☕ Coffee                         ● │
    │ 🍿 Snacks                         ○ │
    │ 🍕 Fast Food                      ○ │
    │ ⋯  Other                           ○ │
    │                                     │
    │ ┌─────────────────────────────────┐ │
    │ │ + New subcategory               │ │
    │ └─────────────────────────────────┘ │
    │                                     │
    │ ┌─────────────────────────────────┐ │
    │ │              Done               │ │
    │ └─────────────────────────────────┘ │
    └─────────────────────────────────────┘

The selected subcategory is shown with a clear selected state.


---

# 7. "No Subcategory"

The overlay MUST contain:

    No subcategory

This represents:

    subcategory_id = NULL

It is NOT the same as:

    Other


Difference:

    No subcategory
        = user intentionally did not provide additional detail.

    Other
        = an actual subcategory.


If the user selects:

    No subcategory

then:

    selectedSubcategoryId = null


The overlay closes after confirmation.


---

# 8. New Subcategory

The subcategory overlay contains:

    + New subcategory

Tapping it opens a smaller secondary overlay.

Example:

    ┌─────────────────────────────────┐
    │ ×       New Subcategory         │
    │                                 │
    │ Parent category                 │
    │ ┌─────────────────────────────┐ │
    │ │ 🍔 Food                     │ │
    │ └─────────────────────────────┘ │
    │                                 │
    │ Name                            │
    │ ┌─────────────────────────────┐ │
    │ │ Coffee                      │ │
    │ └─────────────────────────────┘ │
    │                                 │
    │ Icon                            │
    │                                 │
    │ ☕   🍽   🍕   🍿   🛍   ⋯     │
    │                                 │
    │                                 │
    │ ┌─────────────┐ ┌─────────────┐ │
    │ │   Cancel    │ │   Create    │ │
    │ └─────────────┘ └─────────────┘ │
    └─────────────────────────────────┘

The parent category is already known.

The user does NOT select the parent again.


---

# 9. New Subcategory Creation Flow

Flow:

    Add Expense
        ↓
    Select Food
        ↓
    Double tap Food
        ↓
    Subcategory overlay
        ↓
    + New subcategory
        ↓
    Small creation overlay
        ↓
    Enter name
        ↓
    Select icon
        ↓
    Create
        ↓
    Subcategory is inserted
        ↓
    Return to subcategory overlay
        ↓
    Newly created subcategory is selected
        ↓
    Done
        ↓
    Add Expense


Example:

    User creates:

        Coffee ☕

    under:

        Food


After creation:

    Food
      └── Coffee ✓


The Add Expense screen should then show:

    Food
    Coffee


No second manual selection should be required.


---

# 10. New Subcategory Icon

Every newly created subcategory should receive an icon.

The user can choose from Pocket's existing icon set.

If the user does not explicitly select an icon:

    use a sensible default icon.

Do not generate arbitrary icons dynamically.

The icon must be stored with the category:

    icon = "coffee"


The UI should render the icon consistently throughout:

    Add Expense
    Category Management
    Insights
    Transaction List
    Filters


---

# 11. New Subcategory Color

Subcategory colors should be optional.

The preferred visual model is:

    Parent category
        ↓
    Subcategory uses a related visual identity


Do not require the user to create a completely new color system.

If no custom color is selected:

    inherit or derive a subtle variation from the parent category.


---

# 12. Category Management

Settings → Categories continues to provide full category management.

Example:

    Categories

    Expense

    ┌──────────────────────────────────┐
    │ 🍔 Food                       ⋮ │
    │    Restaurants                   │
    │    Coffee                        │
    │    Snacks                        │
    │    Fast Food                     │
    └──────────────────────────────────┘

    ┌──────────────────────────────────┐
    │ 🚗 Transport                  ⋮ │
    │    Fuel                          │
    │    Bus                           │
    │    Metro                         │
    │    Cab                           │
    └──────────────────────────────────┘

Top-level category menu:

    Add subcategory
    Edit
    Hide


Subcategory menu:

    Edit
    Hide


A subcategory cannot have another subcategory.


---

# 13. Category Data Model

Use one `categories` table.

Do NOT create a separate `subcategories` table.

Schema:

    categories

    id
    type
    name
    icon
    color
    parent_category_id
    is_hidden
    created_at
    updated_at


Relationship:

    parent_category_id
            ↓
        categories.id


Root category:

    parent_category_id = NULL


Subcategory:

    parent_category_id = parent category ID


Maximum depth:

    2


---

# 14. Transaction Data Model

Transaction:

    id
    type
    amount_paise
    category_id
    subcategory_id
    payment_method_id
    transaction_at
    note
    created_at
    updated_at


Important:

    category_id       = required
    subcategory_id    = nullable
    payment_method_id = nullable
    note              = nullable


Merchant is REMOVED from the Pocket transaction model for this
feature's Add Expense UI.

If the existing database contains merchant data from previous
versions, do not destructively delete historical data unless
there is a separate migration decision.

The new Add Expense UI simply does not expose a merchant field.


---

# 15. Category/Subcategory Validation

A transaction is valid only if:

    categoryId != null

and:

    subcategoryId == null

OR:

    subcategory.parentCategoryId == categoryId


Invalid:

    category = Transport
    subcategory = Coffee


because:

    Coffee.parentCategory = Food


Validation must happen in the repository/domain layer.

Never rely only on Compose UI validation.


---

# 16. Category Type Validation

Parent and child must have the same type.

Valid:

    EXPENSE
      Food
        Coffee


Invalid:

    EXPENSE
      Food
        Salary


Also invalid:

    INCOME
      Salary
        Groceries


---

# 17. Maximum Hierarchy

Pocket supports:

    Level 1
        Category

    Level 2
        Subcategory


No:

    Level 3
        Sub-subcategory


Example:

    Food
      Coffee
        Starbucks

is NOT allowed.


---

# 18. Database Migration

Existing categories:

    parent_category_id = NULL


Existing transactions:

    subcategory_id = NULL


No historical transaction receives an automatically generated
subcategory.

No existing category IDs should change.

No existing transaction IDs should change.


---

# 19. Room Entity

Conceptual:

    @Entity(
        tableName = "categories",
        foreignKeys = [
            ForeignKey(
                entity = CategoryEntity::class,
                parentColumns = ["id"],
                childColumns = ["parent_category_id"],
                onDelete = ForeignKey.RESTRICT
            )
        ],
        indices = [
            Index(value = ["parent_category_id"])
        ]
    )
    data class CategoryEntity(
        @PrimaryKey
        val id: String,

        val type: CategoryType,

        val name: String,

        val icon: String,

        val color: String,

        @ColumnInfo(name = "parent_category_id")
        val parentCategoryId: String?,

        @ColumnInfo(name = "is_hidden")
        val isHidden: Boolean,

        @ColumnInfo(name = "created_at")
        val createdAt: Instant,

        @ColumnInfo(name = "updated_at")
        val updatedAt: Instant
    )


Transaction:

    @ColumnInfo(name = "subcategory_id")
    val subcategoryId: String?


---

# 20. DAO

Category DAO:

    observeRootCategories(type)

    observeSubcategories(parentId)

    getCategory(id)

    insertCategory(category)

    updateCategory(category)

    hideCategory(id)

    restoreCategory(id)

    hasChildren(id)

    hasTransactions(id)


Transaction DAO:

    getTransactionsByCategory(categoryId)

    getTransactionsBySubcategory(subcategoryId)

    getTransactionsByCategoryAndSubcategory(
        categoryId,
        subcategoryId
    )


Analytics:

    getSpendingByCategory()

    getSpendingBySubcategory(categoryId)


---

# 21. Repository

CategoryRepository:

    observeRootCategories(type)

    observeSubcategories(parentId)

    createCategory(...)

    createSubcategory(...)

    updateCategory(...)

    updateSubcategory(...)

    hideCategory(id)

    restoreCategory(id)

    validateHierarchy(...)


TransactionRepository:

    createTransaction(...)

    updateTransaction(...)

    getTransaction(id)

    validateCategorySelection(...)


The repository is responsible for validating:

    parent exists
    parent is root
    parent/child types match
    maximum depth
    duplicate names
    transaction category/subcategory relationship


---

# 22. Add Expense ViewModel

State:

    amount
    selectedCategoryId
    selectedSubcategoryId
    paymentMethodId
    transactionDate
    note


Functions:

    selectCategory(id)

    openSubcategorySelector()

    selectSubcategory(id)

    clearSubcategory()

    createSubcategory(...)

    saveTransaction()


Critical behavior:

    selectCategory(newCategoryId)

must execute:

    selectedCategoryId = newCategoryId
    selectedSubcategoryId = null


This prevents:

    Transport + Coffee


---

# 23. Add Expense Data Flow

    AddExpenseScreen
            ↓
    AddExpenseViewModel
            ↓
    selectedCategoryId
            ↓
    double tap
            ↓
    SubcategoryOverlay
            ↓
    selectedSubcategoryId
            ↓
    Save
            ↓
    TransactionRepository
            ↓
    validate
            ↓
    Room DAO
            ↓
    SQLite


---

# 24. Insights Design

The category-specific Insights page is the primary subcategory
analytics experience.

Example:

    ← Food

    September 2026

              ₹5,900
          Total Food Spending

    ┌─────────────────────────────────┐
    │        BAR CHART                 │
    │                                 │
    │       █                         │
    │   █   █       █                 │
    │   █   █   █   █   █             │
    │   █   █   █   █   █             │
    │   ─────────────────────         │
    │    R   C   S   F   N            │
    └─────────────────────────────────┘


Then list:

    Restaurants              ₹2,400
    Coffee                   ₹1,450
    Snacks                     ₹850
    Fast Food                  ₹700
    No subcategory             ₹500


Each row may show:

    icon
    name
    amount
    percentage
    progress/bar indicator


---

# 25. Category Insights Page

Route:

    insights/category/{categoryId}


Example:

    Food

    Total:
    ₹5,900

    Category share:
    32% of total spending


Bar chart:

    Restaurants
    Coffee
    Snacks
    Fast Food
    No subcategory


The chart must represent the same values shown in the list.

Do not maintain a separate analytics dataset.


---

# 26. Subcategory Insight Interaction

Tapping:

    Coffee
    ₹1,450


opens:

    Coffee

    Total
    ₹1,450


Then:

    20 Sep
    Starbucks
    ₹250

    18 Sep
    Cafe
    ₹180

    16 Sep
    Coffee Shop
    ₹220


Route:

    insights/category/{categoryId}/subcategory/{subcategoryId}


---

# 27. "No Subcategory" Insights

The category page must include:

    No subcategory


if transactions exist with:

    subcategory_id = NULL


Example:

    Restaurants        ₹2,400
    Coffee             ₹1,450
    Snacks               ₹850
    Fast Food            ₹700
    No subcategory       ₹500


Do NOT combine NULL with:

    Other


---

# 28. Insights Data Flow

    Room
      ↓
    TransactionDao
      ↓
    Repository
      ↓
    InsightsViewModel
      ↓
    CategoryInsightsScreen
      ↓
    Category total
      ↓
    Subcategory aggregation
      ↓
    Bar chart + list
      ↓
    Selected subcategory
      ↓
    Transaction list


Category total must equal:

    sum(all subcategory amounts)
    +
    sum(no-subcategory amounts)


---

# 29. QR Payment Integration

The subcategory feature must work with the QR payment flow.

Flow:

    Scan QR
        ↓
    Merchant / VPA detected
        ↓
    Select category
        ↓
    Optional subcategory
        ↓
    Payment handoff
        ↓
    Payment result
        ↓
    Create transaction


Before leaving Pocket, persist:

    categoryId
    subcategoryId


inside:

    PaymentAttempt


Do not rely on Compose state surviving the external payment-app
handoff.


---

# 30. QR Category Selection

Keep QR selection fast.

Show the existing square category UI.

Example:

    Food
    Groceries
    Transport
    Shopping
    Bills
    Entertainment


After selecting a category, the user can double tap it to open:

    Subcategory overlay


The selected subcategory is stored in:

    PaymentAttempt.subcategoryId


---

# 31. QR Payment Transaction Creation

After a successful payment:

    PaymentAttempt
        ↓
    categoryId
    subcategoryId
    returnedAmountPaise
        ↓
    TransactionRepository
        ↓
    Transaction


Example:

    Category:
        Food

    Subcategory:
        Coffee

    Amount:
        ₹347


Stored:

    category_id = food
    subcategory_id = coffee
    amount_paise = 34700


---

# 32. Payment Amount

The subcategory feature does not own payment amount collection.

The payment architecture determines the amount.

If the external payment flow returns:

    ₹347


then the created transaction is:

    Food
      Coffee
        ₹347


The user must not manually re-enter the amount after payment if the
payment flow successfully provides the amount.


---

# 33. Note Instead of Merchant

The Add Expense screen removes:

    Merchant


and retains:

    Note (Optional)


Example:

    Note:
    Coffee with team


This keeps the form compact.

The transaction UI therefore becomes:

    Amount
    Category
    Optional Subcategory
    Payment Method
    Date
    Note


---

# 34. Search

When search is introduced, match:

    category name
    subcategory name
    note


Example:

    Coffee


should find:

    Food → Coffee


Do not search merchant for newly created transactions because
merchant is no longer part of the Add Expense UI.


---

# 35. Filters

Filters should support:

    Category
    Subcategory


Selecting:

    Category = Food


includes:

    Food
    Food → Coffee
    Food → Restaurants
    Food → Snacks


Selecting:

    Food → Coffee


returns only:

    Coffee transactions


---

# 36. Category Management Seed Data

Recommended initial subcategories:

    Food
      Restaurants
      Coffee
      Snacks
      Fast Food
      Other

    Groceries
      Vegetables
      Fruits
      Household
      Daily Essentials
      Other

    Transport
      Fuel
      Bus
      Metro
      Cab
      Parking
      Other

    Shopping
      Clothing
      Electronics
      Household
      Personal
      Other

    Bills
      Electricity
      Internet
      Mobile
      Subscriptions
      Other

    Entertainment
      Movies
      Games
      Events
      Streaming
      Other


Do not force users to create these manually.


---

# 37. Custom Subcategory Creation

Custom subcategories are created from the subcategory overlay.

Example:

    Food
      ↓
    + New subcategory
      ↓
    Name: Brunch
    Icon: 🍳
      ↓
    Create


Result:

    Food
      Restaurants
      Coffee
      Snacks
      Fast Food
      Brunch
      Other


The newly created subcategory should immediately become selected.


---

# 38. Duplicate Prevention

Prevent:

    Food
      Coffee
      Coffee


But allow:

    Food
      Coffee

    Transport
      Coffee


Duplicate comparison should be within:

    type + parent + name


---

# 39. Hide Instead of Delete

Prefer:

    Hide


instead of destructive deletion.

Hidden subcategories:

- cannot be selected for new transactions
- remain attached to historical transactions
- remain visible in historical Insights
- remain available for backup/restore


If a parent category has children, hiding the parent should
prevent selection of the entire hierarchy for new transactions
while preserving historical data.


---

# 40. Backup / Restore

JSON backup must contain:

    category.parent_category_id


Transactions:

    category_id
    subcategory_id


Example:

    {
      "id": "food_coffee",
      "type": "EXPENSE",
      "name": "Coffee",
      "icon": "coffee",
      "color": "...",
      "parent_category_id": "food"
    }


Restore validation:

    parent exists
    parent is root
    type matches
    depth <= 2
    transaction references are valid


---

# 41. CSV Export

CSV should contain:

    Date
    Amount
    Type
    Category
    Subcategory
    Payment Method
    Note


Example:

    2026-09-20,
    250,
    EXPENSE,
    Food,
    Coffee,
    UPI,
    Coffee with team


For NULL:

    Food,,UPI,Quick lunch


---

# 42. UI Component Structure

Recommended:

    feature/category/

        CategoryManagementScreen.kt
        CategoryManagementViewModel.kt

        CategoryGrid.kt
        CategoryTile.kt

        SubcategoryOverlay.kt
        NewSubcategoryOverlay.kt

        CategoryManagementRow.kt
        SubcategoryRow.kt


    feature/transaction/

        AddTransactionScreen.kt
        EditTransactionScreen.kt
        AddTransactionViewModel.kt

        CategoryGrid.kt
        SubcategoryOverlay.kt
        NewSubcategoryOverlay.kt


Avoid duplicating components.

Prefer shared components:

    CategoryGrid
    CategoryTile
    SubcategoryOverlay
    NewSubcategoryOverlay


---

# 43. Category Tile

The existing square category tile remains the primary component.

Conceptual:

    ┌───────────────┐
    │               │
    │      🍔       │
    │               │
    │     Food      │
    │               │
    └───────────────┘


Selected:

    ┌───────────────┐
    │       ✓       │
    │      🍔       │
    │     Food      │
    └───────────────┘


Double tap:

    open SubcategoryOverlay


Do not replace the square-box design.


---

# 44. Subcategory Overlay Component

Reusable:

    SubcategoryOverlay(
        parentCategory,
        subcategories,
        selectedSubcategoryId,
        onSelect,
        onCreateNew,
        onDismiss
    )


Responsibilities:

- display parent category
- display subcategories
- display No subcategory
- display New subcategory
- maintain selected state
- return selection


---

# 45. New Subcategory Overlay Component

Reusable:

    NewSubcategoryOverlay(
        parentCategory,
        onCreated,
        onCancel
    )


Fields:

    name
    icon
    optional color


On success:

    create category
    return created category ID


The parent category must be read-only.


---

# 46. Compose State

Add Expense:

    var selectedCategoryId by rememberSaveable
    var selectedSubcategoryId by rememberSaveable


When category changes:

    selectedSubcategoryId = null


When the subcategory overlay is dismissed without selection:

    retain previous subcategory if the user did not explicitly
    clear/change it.


When selecting:

    No subcategory


set:

    selectedSubcategoryId = null


---

# 47. ViewModel State

Prefer a single immutable UI state:

    data class AddExpenseUiState(
        val amount: String = "",
        val selectedCategoryId: String? = null,
        val selectedSubcategoryId: String? = null,
        val availableSubcategories: List<CategoryUiModel> = emptyList(),
        val paymentMethodId: String? = null,
        val transactionDate: Instant,
        val note: String = "",
        val isSaving: Boolean = false,
        val error: String? = null
    )


Events:

    OnAmountChanged
    OnCategorySelected
    OnCategoryDoubleTapped
    OnSubcategorySelected
    OnNoSubcategorySelected
    OnCreateSubcategory
    OnPaymentMethodSelected
    OnDateSelected
    OnNoteChanged
    OnSave


---

# 48. Category Selection State Machine

Initial:

    Category = NULL
    Subcategory = NULL


Single tap Food:

    Category = Food
    Subcategory = NULL


Double tap Food:

    Open Food subcategory overlay


Select Coffee:

    Category = Food
    Subcategory = Coffee


Select No subcategory:

    Category = Food
    Subcategory = NULL


Change Food → Transport:

    Category = Transport
    Subcategory = NULL


This state machine must be enforced by the ViewModel.


---

# 49. Insights UI State

    data class CategoryInsightsUiState(
        val category: CategoryUiModel?,
        val totalPaise: Long,
        val subcategories: List<SubcategoryInsightUiModel>,
        val isLoading: Boolean,
        val error: String?
    )


Subcategory insight:

    data class SubcategoryInsightUiModel(
        val id: String?,
        val name: String,
        val icon: String,
        val amountPaise: Long,
        val percentage: Float
    )


For NULL:

    id = null
    name = "No subcategory"


---

# 50. Insights Bar Chart

The category Insights screen should show one bar per subcategory.

Example:

    Food — ₹5,900

    Restaurants  ████████████████████ ₹2,400
    Coffee       ████████████         ₹1,450
    Snacks       ███████               ₹850
    Fast Food    ██████                ₹700
    No category  ████                  ₹500


Use the existing Pocket chart styling.

The chart and list must use the same data source.


---

# 51. Transaction Drill Down

Tapping:

    Coffee


opens:

    Coffee Transactions


Each transaction displays:

    date
    amount
    payment method
    note


Example:

    20 Sep 2026
    ₹250
    UPI · 10:24 AM
    Coffee with team


No merchant field is shown.


---

# 52. Accessibility

Category tile:

    "Food category"


Subcategory:

    "Coffee subcategory of Food"


Double-tap behavior should have an accessible alternative.

Do not make double tap the ONLY way for accessibility users to
reach subcategories.

For example, provide:

    More
    or
    Subcategories


through an accessible semantic action where required.


---

# 53. Animation

Keep animations subtle.

When subcategory overlay opens:

    slide/fade in


When newly created subcategory is selected:

    selected state animation


When returning to Add Expense:

    show selected subcategory smoothly


Do not use excessive animations.


---

# 54. Offline Behavior

Everything must work offline.

No network calls.

No server dependency.

No analytics event.

No remote category generation.

Creating:

    Coffee


must immediately write to:

    Room / SQLite


and become available throughout the application.


---

# 55. Testing

## Database

Test:

    existing categories survive migration

    existing transactions survive migration

    parent_category_id defaults to NULL

    subcategory_id defaults to NULL


## Validation

Test:

    valid parent/child

    wrong parent

    wrong type

    third-level category

    duplicate subcategory

    hidden parent

    invalid transaction relationship


## Add Expense

Test:

    category only

    category + subcategory

    double tap opens overlay

    No subcategory works

    new subcategory creation works

    created subcategory is automatically selected

    changing category clears subcategory


## Insights

Test:

    category total

    subcategory aggregation

    NULL aggregation

    percentages

    bar chart data

    subcategory transaction drill-down


## QR

Test:

    category retained

    subcategory retained

    PaymentAttempt stores both

    transaction receives both


---

# 56. Migration Compatibility

Before migration:

    Food
      Transaction A


After migration:

    Food
      Transaction A
        subcategory = NULL


No historical transaction should be silently reclassified.


---

# 57. Performance

Category counts will be small.

Use:

    Room Flow

for reactive updates.

Index:

    parent_category_id

and:

    subcategory_id


Do not introduce a recursive tree engine.

Maximum depth is only two levels.


---

# 58. Final Architecture

UI:

    Compose
       ↓
    ViewModel
       ↓
    Repository
       ↓
    DAO
       ↓
    Room
       ↓
    SQLite


Category hierarchy:

    Category
       │
       └── Subcategory


Transaction:

    Transaction
       │
       ├── categoryId
       └── subcategoryId?


Insights:

    All Spending
        ↓
    Category
        ↓
    Subcategory
        ↓
    Transactions


QR:

    Scan QR
        ↓
    Category
        ↓
    Optional Subcategory
        ↓
    PaymentAttempt
        ↓
    External payment flow
        ↓
    Payment response
        ↓
    Transaction


---

# 59. Final UX Rules

The implementation MUST follow these rules:

1. Category remains mandatory.
2. Subcategory remains optional.
3. Existing square category UI remains.
4. Double-tapping a selected category opens its subcategory overlay.
5. Subcategory overlay is contextual to the selected category.
6. "No subcategory" is available.
7. "Other" is a real subcategory and is not equivalent to NULL.
8. New subcategory can be created directly from the overlay.
9. New subcategory uses a name and icon.
10. Parent category is automatically known.
11. Newly created subcategory is automatically selected.
12. Maximum hierarchy depth is two.
13. Merchant is removed from Add Expense.
14. Note remains available.
15. Category Insights gets its own detailed screen.
16. Category Insights displays a bar chart.
17. Category Insights lists every subcategory with amount.
18. Category Insights shows "No subcategory" separately.
19. Tapping a subcategory opens its transactions.
20. QR flow preserves category + subcategory.
21. Existing data remains valid.
22. No network dependency is introduced.


# 60. Definition of Done

[ ] Existing square category UI retained.

[ ] Category selection works.

[ ] Double tap opens subcategory overlay.

[ ] Subcategory overlay displays mapped children.

[ ] No subcategory option exists.

[ ] New subcategory action exists.

[ ] New subcategory overlay exists.

[ ] Name input works.

[ ] Icon selection works.

[ ] New subcategory is created under correct parent.

[ ] New subcategory is automatically selected.

[ ] Category change clears subcategory.

[ ] Merchant removed from Add Expense UI.

[ ] Note remains available.

[ ] Category Insights screen implemented.

[ ] Bar chart implemented.

[ ] Subcategories listed with amounts.

[ ] Percentages displayed.

[ ] No subcategory displayed separately.

[ ] Subcategory transaction drill-down implemented.

[ ] Room schema migrated.

[ ] Existing transactions preserved.

[ ] Category/subcategory validation implemented.

[ ] QR PaymentAttempt stores category and subcategory.

[ ] Backup/restore supports hierarchy.

[ ] CSV supports subcategory.

[ ] Search/filter supports subcategory.

[ ] UI tests pass.

[ ] Migration tests pass.

[ ] Feature works completely offline.