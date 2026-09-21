package `in`.marxen.pocket.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import `in`.marxen.pocket.data.local.entity.MerchantCategoryMemoryEntity

@Dao
interface MerchantCategoryMemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: MerchantCategoryMemoryEntity)

    @Query("SELECT category_id FROM merchant_category_memory WHERE payee_vpa_key = :vpaKey")
    suspend fun getCategoryForVpa(vpaKey: String): Long?
}
