package `in`.marxen.pocket.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import `in`.marxen.pocket.data.local.entity.BudgetEntity

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets")
    suspend fun getAll(): List<BudgetEntity>

    @Insert
    suspend fun insertAll(budgets: List<BudgetEntity>)

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()
}
