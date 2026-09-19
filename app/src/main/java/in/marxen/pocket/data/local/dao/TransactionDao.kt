package `in`.marxen.pocket.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import `in`.marxen.pocket.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions")
    suspend fun getAll(): List<TransactionEntity>

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE transaction_date = :date ORDER BY created_at DESC")
    fun getByDate(date: LocalDate): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE transaction_date BETWEEN :start AND :end ORDER BY transaction_date DESC, created_at DESC")
    fun getByDateRange(start: LocalDate, end: LocalDate): Flow<List<TransactionEntity>>

    @Query("SELECT COALESCE(SUM(amount_paise), 0) FROM transactions WHERE type = 'EXPENSE' AND transaction_date BETWEEN :start AND :end")
    fun totalExpensesByDateRange(start: LocalDate, end: LocalDate): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount_paise), 0) FROM transactions WHERE type = 'INCOME' AND transaction_date BETWEEN :start AND :end")
    fun totalIncomeByDateRange(start: LocalDate, end: LocalDate): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount_paise), 0) FROM transactions WHERE type = 'EXPENSE' AND transaction_date BETWEEN :start AND :end")
    suspend fun totalExpensesByDateRangeSync(start: LocalDate, end: LocalDate): Long

    @Query("SELECT COALESCE(SUM(amount_paise), 0) FROM transactions WHERE type = 'INCOME' AND transaction_date BETWEEN :start AND :end")
    suspend fun totalIncomeByDateRangeSync(start: LocalDate, end: LocalDate): Long

    @Query("SELECT category_id, SUM(amount_paise) as total FROM transactions WHERE type = 'EXPENSE' AND transaction_date BETWEEN :start AND :end GROUP BY category_id ORDER BY total DESC")
    fun categoryTotals(start: LocalDate, end: LocalDate): Flow<List<CategoryTotal>>
}

data class CategoryTotal(
    @androidx.room.ColumnInfo(name = "category_id") val categoryId: Long,
    val total: Long,
)
