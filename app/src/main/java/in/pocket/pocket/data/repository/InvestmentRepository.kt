package `in`.marxen.pocket.data.repository

import `in`.marxen.pocket.data.local.dao.InvestmentDao
import `in`.marxen.pocket.data.local.dao.InvestmentEntryWithType
import `in`.marxen.pocket.data.local.entity.InvestmentEntryEntity
import `in`.marxen.pocket.data.local.entity.InvestmentTypeEntity
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

class InvestmentRepository(private val dao: InvestmentDao) {
    fun getEntries(month: YearMonth): Flow<List<InvestmentEntryWithType>> =
        dao.entriesBetween(month.atDay(1), month.atEndOfMonth())

    fun getEntries(kind: String, month: YearMonth): Flow<List<InvestmentEntryWithType>> =
        dao.entriesBetweenByKind(kind, month.atDay(1), month.atEndOfMonth())

    fun getSavingsBalanceAsOf(month: YearMonth): Flow<Long> = dao.savingsBalanceAsOf(month.atEndOfMonth())

    fun getInvestedTotalAsOf(month: YearMonth): Flow<Long> = dao.investedTotalAsOf(month.atEndOfMonth())

    fun getTypes(): Flow<List<InvestmentTypeEntity>> = dao.allTypes()

    suspend fun addCustomType(name: String): Long =
        dao.insertType(InvestmentTypeEntity(name = name, sortOrder = dao.maxSortOrder() + 1))

    suspend fun getEntry(id: Long): InvestmentEntryEntity? = dao.getEntryById(id)

    suspend fun insertEntry(entry: InvestmentEntryEntity): Long = dao.insertEntry(entry)

    suspend fun updateEntry(entry: InvestmentEntryEntity) = dao.updateEntry(entry)

    suspend fun deleteEntry(entry: InvestmentEntryEntity) = dao.deleteEntryById(entry.id)
}
