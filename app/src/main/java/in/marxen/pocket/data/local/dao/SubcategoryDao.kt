package `in`.marxen.pocket.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import `in`.marxen.pocket.data.local.entity.SubcategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubcategoryDao {
    @Insert
    suspend fun insert(subcategory: SubcategoryEntity): Long

    @Update
    suspend fun update(subcategory: SubcategoryEntity)

    @Delete
    suspend fun delete(subcategory: SubcategoryEntity)

    @Insert
    suspend fun insertAll(subcategories: List<SubcategoryEntity>)

    @Query("DELETE FROM subcategories")
    suspend fun deleteAll()

    @Query("SELECT * FROM subcategories WHERE id = :id")
    suspend fun getById(id: Long): SubcategoryEntity?

    @Query("SELECT * FROM subcategories WHERE category_id = :categoryId AND is_hidden = 0 ORDER BY name ASC")
    fun getActiveByCategoryId(categoryId: Long): Flow<List<SubcategoryEntity>>

    @Query("SELECT * FROM subcategories WHERE category_id = :categoryId ORDER BY name ASC")
    fun getAllByCategoryId(categoryId: Long): Flow<List<SubcategoryEntity>>

    @Query("SELECT * FROM subcategories ORDER BY name ASC")
    suspend fun getAll(): List<SubcategoryEntity>

    @Query("SELECT * FROM subcategories WHERE is_hidden = 0 ORDER BY name ASC")
    fun getActive(): Flow<List<SubcategoryEntity>>
}
