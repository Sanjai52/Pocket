package `in`.marxen.pocket.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import `in`.marxen.pocket.data.local.entity.RecurringTransactionEntity

@Dao
interface RecurringTransactionDao {
    @Query("SELECT * FROM recurring_transactions")
    suspend fun getAll(): List<RecurringTransactionEntity>

    @Insert
    suspend fun insertAll(transactions: List<RecurringTransactionEntity>)

    @Query("DELETE FROM recurring_transactions")
    suspend fun deleteAll()
}
