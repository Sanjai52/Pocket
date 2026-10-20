package `in`.marxen.pocket.data.backup

import androidx.room.withTransaction
import `in`.marxen.pocket.core.money.paiseToRupees
import `in`.marxen.pocket.data.local.InvestmentDefaults
import `in`.marxen.pocket.data.local.PocketDatabase
import `in`.marxen.pocket.data.local.SubcategoryDefaults
import `in`.marxen.pocket.data.local.entity.BudgetEntity
import `in`.marxen.pocket.data.local.entity.CategoryEntity
import `in`.marxen.pocket.data.local.entity.InvestmentEntryEntity
import `in`.marxen.pocket.data.local.entity.InvestmentTypeEntity
import `in`.marxen.pocket.data.local.entity.PaymentMethodEntity
import `in`.marxen.pocket.data.local.entity.RecurringTransactionEntity
import `in`.marxen.pocket.data.local.entity.SubcategoryEntity
import `in`.marxen.pocket.data.local.entity.TransactionEntity
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class BackupManager {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun createBackup(database: PocketDatabase): String {
        val transactions = database.transactionDao().getAll().map { it.toSerializable() }
        val categories = database.categoryDao().getAllSync().map { it.toSerializable() }
        val subcategories = database.subcategoryDao().getAll().map { it.toSerializable() }
        val paymentMethods = database.paymentMethodDao().getAll().map { it.toSerializable() }
        val budgets = database.budgetDao().getAll().map { it.toSerializable() }
        val recurringTransactions = database.recurringTransactionDao().getAll().map { it.toSerializable() }
        val investmentTypes = database.investmentDao().allTypesSync().map { it.toSerializable() }
        val investmentEntries = database.investmentDao().getAllEntriesSync().map { it.toSerializable() }

        val backup = PocketBackup(
            schemaVersion = SCHEMA_VERSION,
            createdAt = Instant.now().toString(),
            transactions = transactions,
            categories = categories,
            subcategories = subcategories,
            paymentMethods = paymentMethods,
            budgets = budgets,
            recurringTransactions = recurringTransactions,
            investmentTypes = investmentTypes,
            investmentEntries = investmentEntries,
        )

        return json.encodeToString(PocketBackup.serializer(), backup)
    }

    suspend fun restoreBackup(database: PocketDatabase, json: String) {
        val backup = this.json.decodeFromString(PocketBackup.serializer(), json)

        require(backup.schemaVersion == SCHEMA_VERSION) {
            "Unsupported schema version: ${backup.schemaVersion} (expected $SCHEMA_VERSION)"
        }

        database.withTransaction {
            database.recurringTransactionDao().deleteAll()
            database.budgetDao().deleteAll()
            database.transactionDao().deleteAll()
            database.investmentDao().deleteAllEntries()
            database.investmentDao().deleteAllTypes()
            database.paymentMethodDao().deleteAll()
            database.subcategoryDao().deleteAll()
            database.categoryDao().deleteAll()

            database.categoryDao().insertAll(backup.categories.map { it.toEntity() })
            database.subcategoryDao().insertAll(backup.subcategories.map { it.toEntity() })
            // A backup created before subcategories were included carries none. Restore the
            // built-in set (original ids) so the subcategory pickers stay populated afterwards.
            SubcategoryDefaults.seedSystemSetIfEmpty(
                database.subcategoryDao(),
                backup.categories.associate { it.name to it.id },
            )
            database.paymentMethodDao().insertAll(backup.paymentMethods.map { it.toEntity() })
            database.transactionDao().insertAll(backup.transactions.map { it.toEntity() })
            database.budgetDao().insertAll(backup.budgets.map { it.toEntity() })
            database.recurringTransactionDao().insertAll(backup.recurringTransactions.map { it.toEntity() })
            backup.investmentTypes.forEach { database.investmentDao().insertType(it.toEntity()) }
            backup.investmentEntries.forEach { database.investmentDao().insertEntry(it.toEntity()) }
            // A pre-v4 backup carries no types, and a backup from before Gold carries the
            // older five. Insert any missing built-ins so Investments stays usable.
            InvestmentDefaults.seedMissingDefaults(database.investmentDao())
        }
    }

    fun exportCsv(
        transactions: List<TransactionEntity>,
        categories: Map<Long, String>,
        subcategories: Map<Long, String> = emptyMap(),
        paymentMethods: Map<Long, String> = emptyMap(),
    ): String = buildString {
        appendLine("Date,Type,Amount,Category,Subcategory,Payment Method,Merchant,Note,Created At")
        for (t in transactions) {
            val category = escapeCsv(categories[t.categoryId].orEmpty())
            val subcategory = escapeCsv(t.subcategoryId?.let { subcategories[it] }.orEmpty())
            val paymentMethod = escapeCsv(t.paymentMethodId?.let { paymentMethods[it] }.orEmpty())
            val merchant = t.merchant?.let { escapeCsv(it) }.orEmpty()
            val note = t.note?.let { escapeCsv(it) }.orEmpty()
            appendLine(
                "${t.transactionDate},${t.type},${paiseToRupees(t.amountPaise)}," +
                    "$category,$subcategory,$paymentMethod,$merchant,$note,${t.createdAt}",
            )
        }
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun TransactionEntity.toSerializable() = SerializableTransactionEntity(
        id = id,
        type = type,
        amountPaise = amountPaise,
        categoryId = categoryId,
        subcategoryId = subcategoryId,
        paymentMethodId = paymentMethodId,
        transactionDate = transactionDate,
        merchant = merchant,
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun CategoryEntity.toSerializable() = SerializableCategoryEntity(
        id = id,
        name = name,
        icon = icon,
        color = color,
        isHidden = isHidden,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun SubcategoryEntity.toSerializable() = SerializableSubcategoryEntity(
        id = id,
        name = name,
        categoryId = categoryId,
        systemKey = systemKey,
        isHidden = isHidden,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun PaymentMethodEntity.toSerializable() = SerializablePaymentMethodEntity(
        id = id,
        name = name,
        icon = icon,
        isHidden = isHidden,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun BudgetEntity.toSerializable() = SerializableBudgetEntity(
        id = id,
        month = month,
        categoryId = categoryId,
        limitPaise = limitPaise,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun RecurringTransactionEntity.toSerializable() = SerializableRecurringTransactionEntity(
        id = id,
        type = type,
        amountPaise = amountPaise,
        categoryId = categoryId,
        paymentMethodId = paymentMethodId,
        frequency = frequency,
        nextOccurrence = nextOccurrence,
        merchant = merchant,
        note = note,
        enabled = enabled,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun InvestmentTypeEntity.toSerializable() = SerializableInvestmentTypeEntity(
        id = id,
        name = name,
        sortOrder = sortOrder,
        createdAt = createdAt,
    )

    private fun InvestmentEntryEntity.toSerializable() = SerializableInvestmentEntryEntity(
        id = id,
        kind = kind,
        direction = direction,
        typeId = typeId,
        amountPaise = amountPaise,
        entryDate = entryDate,
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun SerializableTransactionEntity.toEntity() = TransactionEntity(
        id = id,
        type = type,
        amountPaise = amountPaise,
        categoryId = categoryId,
        subcategoryId = subcategoryId,
        paymentMethodId = paymentMethodId,
        transactionDate = transactionDate,
        merchant = merchant,
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun SerializableCategoryEntity.toEntity() = CategoryEntity(
        id = id,
        name = name,
        icon = icon,
        color = color,
        isHidden = isHidden,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun SerializableSubcategoryEntity.toEntity() = SubcategoryEntity(
        id = id,
        name = name,
        categoryId = categoryId,
        systemKey = systemKey,
        isHidden = isHidden,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun SerializablePaymentMethodEntity.toEntity() = PaymentMethodEntity(
        id = id,
        name = name,
        icon = icon,
        isHidden = isHidden,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun SerializableBudgetEntity.toEntity() = BudgetEntity(
        id = id,
        month = month,
        categoryId = categoryId,
        limitPaise = limitPaise,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun SerializableRecurringTransactionEntity.toEntity() = RecurringTransactionEntity(
        id = id,
        type = type,
        amountPaise = amountPaise,
        categoryId = categoryId,
        paymentMethodId = paymentMethodId,
        frequency = frequency,
        nextOccurrence = nextOccurrence,
        merchant = merchant,
        note = note,
        enabled = enabled,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun SerializableInvestmentTypeEntity.toEntity() = InvestmentTypeEntity(
        id = id,
        name = name,
        sortOrder = sortOrder,
        createdAt = createdAt,
    )

    private fun SerializableInvestmentEntryEntity.toEntity() = InvestmentEntryEntity(
        id = id,
        kind = kind,
        direction = direction,
        typeId = typeId,
        amountPaise = amountPaise,
        entryDate = entryDate,
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        private const val SCHEMA_VERSION = 1
    }
}
