package `in`.marxen.pocket.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "recurring_transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index("category_id")],
)
data class RecurringTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    @ColumnInfo(name = "payment_method_id") val paymentMethodId: Long? = null,
    val frequency: String,
    @ColumnInfo(name = "next_occurrence") val nextOccurrence: LocalDate,
    val merchant: String? = null,
    val note: String? = null,
    val enabled: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now(),
    @ColumnInfo(name = "updated_at") val updatedAt: Instant = Instant.now(),
)
