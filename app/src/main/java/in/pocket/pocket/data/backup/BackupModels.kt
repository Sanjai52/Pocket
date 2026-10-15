package `in`.marxen.pocket.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class PocketBackup(
    val schemaVersion: Int,
    val createdAt: String,
    val transactions: List<SerializableTransactionEntity>,
    val categories: List<SerializableCategoryEntity>,
    val paymentMethods: List<SerializablePaymentMethodEntity>,
    val budgets: List<SerializableBudgetEntity>,
    val recurringTransactions: List<SerializableRecurringTransactionEntity>,
)

@Serializable
data class SerializableTransactionEntity(
    val id: Long,
    val type: String,
    val amountPaise: Long,
    val categoryId: Long,
    val paymentMethodId: Long?,
    @Serializable(with = LocalDateSerializer::class)
    val transactionDate: java.time.LocalDate,
    val merchant: String?,
    val note: String?,
    @Serializable(with = InstantSerializer::class)
    val createdAt: java.time.Instant,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: java.time.Instant,
)

@Serializable
data class SerializableCategoryEntity(
    val id: Long,
    val name: String,
    val icon: String?,
    val color: Long?,
    val isHidden: Boolean,
    @Serializable(with = InstantSerializer::class)
    val createdAt: java.time.Instant,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: java.time.Instant,
)

@Serializable
data class SerializablePaymentMethodEntity(
    val id: Long,
    val name: String,
    val icon: String?,
    val isHidden: Boolean,
    @Serializable(with = InstantSerializer::class)
    val createdAt: java.time.Instant,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: java.time.Instant,
)

@Serializable
data class SerializableBudgetEntity(
    val id: Long,
    @Serializable(with = YearMonthSerializer::class)
    val month: java.time.YearMonth,
    val categoryId: Long?,
    val limitPaise: Long,
    @Serializable(with = InstantSerializer::class)
    val createdAt: java.time.Instant,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: java.time.Instant,
)

@Serializable
data class SerializableRecurringTransactionEntity(
    val id: Long,
    val type: String,
    val amountPaise: Long,
    val categoryId: Long,
    val paymentMethodId: Long?,
    val frequency: String,
    @Serializable(with = LocalDateSerializer::class)
    val nextOccurrence: java.time.LocalDate,
    val merchant: String?,
    val note: String?,
    val enabled: Boolean,
    @Serializable(with = InstantSerializer::class)
    val createdAt: java.time.Instant,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: java.time.Instant,
)
