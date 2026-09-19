package `in`.marxen.pocket.data.repository

import `in`.marxen.pocket.data.local.dao.CategoryDao
import `in`.marxen.pocket.data.local.dao.CategoryTotal
import `in`.marxen.pocket.data.local.dao.TransactionDao
import `in`.marxen.pocket.data.local.entity.CategoryEntity
import `in`.marxen.pocket.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
) {
    fun getTransactionsByDate(date: LocalDate): Flow<List<TransactionEntity>> =
        transactionDao.getByDate(date)

    fun getTransactionsByDateRange(start: LocalDate, end: LocalDate): Flow<List<TransactionEntity>> =
        transactionDao.getByDateRange(start, end)

    fun getTotalExpenses(month: YearMonth): Flow<Long> =
        transactionDao.totalExpensesByDateRange(month.atDay(1), month.atEndOfMonth())

    fun getTotalIncome(month: YearMonth): Flow<Long> =
        transactionDao.totalIncomeByDateRange(month.atDay(1), month.atEndOfMonth())

    suspend fun getTotalExpensesSync(month: YearMonth): Long =
        transactionDao.totalExpensesByDateRangeSync(month.atDay(1), month.atEndOfMonth())

    suspend fun getTotalIncomeSync(month: YearMonth): Long =
        transactionDao.totalIncomeByDateRangeSync(month.atDay(1), month.atEndOfMonth())

    fun getCategoryTotals(month: YearMonth): Flow<List<CategoryTotal>> =
        transactionDao.categoryTotals(month.atDay(1), month.atEndOfMonth())

    suspend fun getTransactionById(id: Long): TransactionEntity? =
        transactionDao.getById(id)

    suspend fun insertTransaction(transaction: TransactionEntity): Long =
        transactionDao.insert(transaction)

    suspend fun updateTransaction(transaction: TransactionEntity) =
        transactionDao.update(transaction)

    suspend fun deleteTransaction(transaction: TransactionEntity) =
        transactionDao.delete(transaction)

    fun getActiveCategories(): Flow<List<CategoryEntity>> =
        categoryDao.getActive()

    fun getAllCategories(): Flow<List<CategoryEntity>> =
        categoryDao.getAll()

    suspend fun getCategoryById(id: Long): CategoryEntity? =
        categoryDao.getById(id)

    suspend fun insertCategory(category: CategoryEntity): Long =
        categoryDao.insert(category)

    suspend fun updateCategory(category: CategoryEntity) =
        categoryDao.update(category)
}
