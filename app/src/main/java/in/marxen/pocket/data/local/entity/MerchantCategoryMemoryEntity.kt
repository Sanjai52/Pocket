package `in`.marxen.pocket.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "merchant_category_memory")
data class MerchantCategoryMemoryEntity(
    @ColumnInfo(name = "payee_vpa_key") @androidx.room.PrimaryKey val payeeVpaKey: String,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
