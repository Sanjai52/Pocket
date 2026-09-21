package `in`.marxen.pocket.data.repository

import `in`.marxen.pocket.data.local.dao.CategoryDao
import `in`.marxen.pocket.data.local.dao.CategoryTotal
import `in`.marxen.pocket.data.local.dao.MerchantCategoryMemoryDao
import `in`.marxen.pocket.data.local.dao.PaymentAttemptDao
import `in`.marxen.pocket.data.local.dao.TransactionDao
import `in`.marxen.pocket.data.local.entity.CategoryEntity
import `in`.marxen.pocket.data.local.entity.MerchantCategoryMemoryEntity
import `in`.marxen.pocket.data.local.entity.PaymentAttemptEntity
import `in`.marxen.pocket.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val paymentAttemptDao: PaymentAttemptDao,
    private val merchantCategoryMemoryDao: MerchantCategoryMemoryDao,
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

    fun getSystemKeyCategory(systemKey: String): Flow<CategoryEntity?> =
        categoryDao.getBySystemKey(systemKey)

    fun getAllCategoriesSync(): Flow<List<CategoryEntity>> =
        categoryDao.getAll()

    suspend fun getCategoryBySystemKeySync(systemKey: String): CategoryEntity? =
        categoryDao.getBySystemKeySync(systemKey)

    // Payment attempts
    suspend fun insertPaymentAttempt(attempt: PaymentAttemptEntity) =
        paymentAttemptDao.insert(attempt)

    suspend fun updatePaymentAttempt(attempt: PaymentAttemptEntity) =
        paymentAttemptDao.update(attempt)

    suspend fun getPaymentAttemptById(id: String): PaymentAttemptEntity? =
        paymentAttemptDao.getById(id)

    fun getUnresolvedAttempts(): Flow<List<PaymentAttemptEntity>> =
        paymentAttemptDao.getUnresolved()

    suspend fun getUnresolvedAttemptsSync(): List<PaymentAttemptEntity> =
        paymentAttemptDao.getUnresolvedSync()

    suspend fun getUnresolvedForPayeeSince(vpaKey: String, since: Long): List<PaymentAttemptEntity> =
        paymentAttemptDao.getUnresolvedForPayeeSince(vpaKey, since)

    suspend fun getRecordedForPayeeAmountSince(vpaKey: String, amount: Long, since: Long): PaymentAttemptEntity? =
        paymentAttemptDao.getRecordedForPayeeAmountSince(vpaKey, amount, since)

    suspend fun updatePaymentAttemptStatus(id: String, newStatus: String, now: Long): Int =
        paymentAttemptDao.updateStatus(id, newStatus, now)

    // Merchant category memory
    suspend fun upsertMerchantCategoryMemory(memory: MerchantCategoryMemoryEntity) =
        merchantCategoryMemoryDao.upsert(memory)

    suspend fun getMerchantCategoryId(vpaKey: String): Long? =
        merchantCategoryMemoryDao.getCategoryForVpa(vpaKey)
}
