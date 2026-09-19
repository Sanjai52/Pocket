package `in`.marxen.pocket.data.backup

import androidx.room.withTransaction
import `in`.marxen.pocket.data.local.PocketDatabase
import `in`.marxen.pocket.data.local.entity.BudgetEntity
import `in`.marxen.pocket.data.local.entity.CategoryEntity
import `in`.marxen.pocket.data.local.entity.PaymentMethodEntity
import `in`.marxen.pocket.data.local.entity.RecurringTransactionEntity
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
        val paymentMethods = database.paymentMethodDao().getAll().map { it.toSerializable() }
        val budgets = database.budgetDao().getAll().map { it.toSerializable() }
        val recurringTransactions = database.recurringTransactionDao().getAll().map { it.toSerializable() }

        val backup = PocketBackup(
            schemaVersion = SCHEMA_VERSION,
            createdAt = Instant.now().toString(),
            transactions = transactions,
            categories = categories,
            paymentMethods = paymentMethods,
            budgets = budgets,
            recurringTransactions = recurringTransactions,
        )

        return json.encodeToString(PocketBackup.serializer(), backup)
    }

    suspend fun restoreBackup(database: PocketDatabase, json: String) {
        val backup = Json.decodeFromString(PocketBackup.serializer(), json)

        require(backup.schemaVersion == SCHEMA_VERSION) {
            "Unsupported schema version: ${backup.schemaVersion} (expected $SCHEMA_VERSION)"
        }

        database.withTransaction {
            database.recurringTransactionDao().deleteAll()
            database.budgetDao().deleteAll()
            database.transactionDao().deleteAll()
            database.paymentMethodDao().deleteAll()
            database.categoryDao().deleteAll()

            database.categoryDao().insertAll(backup.categories.map { it.toEntity() })
            database.paymentMethodDao().insertAll(backup.paymentMethods.map { it.toEntity() })
            database.transactionDao().insertAll(backup.transactions.map { it.toEntity() })
            database.budgetDao().insertAll(backup.budgets.map { it.toEntity() })
            database.recurringTransactionDao().insertAll(backup.recurringTransactions.map { it.toEntity() })
        }
    }

    fun exportCsv(
        transactions: List<TransactionEntity>,
        categories: Map<Long, String>,
    ): String = buildString {
        appendLine("Date,Type,Amount,Category,Merchant,Note")
        for (t in transactions) {
            val category = categories[t.categoryId].orEmpty()
            val merchant = t.merchant?.let { escapeCsv(it) }.orEmpty()
            val note = t.note?.let { escapeCsv(it) }.orEmpty()
            appendLine("${t.transactionDate},${t.type},${t.amountPaise},${escapeCsv(category)},$merchant,$note")
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

    private fun SerializableTransactionEntity.toEntity() = TransactionEntity(
        id = id,
        type = type,
        amountPaise = amountPaise,
        categoryId = categoryId,
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

    companion object {
        private const val SCHEMA_VERSION = 1
    }
}
