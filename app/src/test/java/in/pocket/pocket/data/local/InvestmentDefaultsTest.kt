package `in`.marxen.pocket.data.local

import `in`.marxen.pocket.data.local.dao.InvestmentDao
import `in`.marxen.pocket.data.local.dao.InvestmentEntryWithType
import `in`.marxen.pocket.data.local.entity.InvestmentEntryEntity
import `in`.marxen.pocket.data.local.entity.InvestmentTypeEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InvestmentDefaultsTest {
    @Test
    fun types_matchSeededNamesInDisplayOrder() {
        val types = InvestmentDefaults.types()
        assertEquals(
            listOf("Stocks", "IPO", "Mutual fund", "SIP", "Gold", "Other"),
            types.map { it.name },
        )
        assertEquals(listOf(0, 1, 2, 3, 4, 5), types.map { it.sortOrder })
    }

    @Test
    fun seedMissingDefaults_insertsAllDefaultsInOrderWhenTableIsEmpty() {
        val dao = FakeInvestmentDao()

        runBlocking { InvestmentDefaults.seedMissingDefaults(dao) }

        assertEquals(InvestmentDefaults.TYPE_NAMES, dao.inserted.map { it.name })
        assertEquals(listOf(0, 1, 2, 3, 4, 5), dao.inserted.map { it.sortOrder })
    }

    @Test
    fun seedMissingDefaults_addsGoldToTypeListFromBeforeGold() {
        val dao = FakeInvestmentDao(
            existing = listOf(
                InvestmentTypeEntity(id = 1, name = "Stocks", sortOrder = 0),
                InvestmentTypeEntity(id = 2, name = "IPO", sortOrder = 1),
                InvestmentTypeEntity(id = 3, name = "Mutual fund", sortOrder = 2),
                InvestmentTypeEntity(id = 4, name = "SIP", sortOrder = 3),
                InvestmentTypeEntity(id = 5, name = "Other", sortOrder = 4),
            ),
        )

        runBlocking { InvestmentDefaults.seedMissingDefaults(dao) }

        assertEquals(listOf("Gold"), dao.inserted.map { it.name })
        assertEquals(4, dao.inserted.single().sortOrder)
    }

    @Test
    fun seedMissingDefaults_matchesExistingNamesCaseInsensitively() {
        val dao = FakeInvestmentDao(
            existing = listOf(InvestmentTypeEntity(id = 1, name = "GOLD", sortOrder = 4)),
        )

        runBlocking { InvestmentDefaults.seedMissingDefaults(dao) }

        assertTrue(dao.inserted.none { it.name.equals("Gold", ignoreCase = true) })
    }

    @Test
    fun seedMissingDefaults_leavesCompleteTypeListUntouched() {
        val dao = FakeInvestmentDao(
            existing = InvestmentDefaults.types().mapIndexed { index, type ->
                type.copy(id = index + 1L)
            },
        )

        runBlocking { InvestmentDefaults.seedMissingDefaults(dao) }

        assertTrue(dao.inserted.isEmpty())
    }

    private class FakeInvestmentDao(
        private val existing: List<InvestmentTypeEntity> = emptyList(),
    ) : InvestmentDao {
        val inserted = mutableListOf<InvestmentTypeEntity>()

        override suspend fun insertType(type: InvestmentTypeEntity): Long {
            inserted += type
            return inserted.size.toLong()
        }

        override suspend fun allTypesSync(): List<InvestmentTypeEntity> = existing

        override suspend fun insertEntry(entry: InvestmentEntryEntity): Long = error("unused")
        override suspend fun updateEntry(entry: InvestmentEntryEntity) = error("unused")
        override suspend fun getEntryById(id: Long): InvestmentEntryEntity? = error("unused")
        override suspend fun getAllEntriesSync(): List<InvestmentEntryEntity> = error("unused")
        override suspend fun deleteAllEntries() = error("unused")
        override suspend fun deleteEntryById(id: Long) = error("unused")
        override suspend fun maxSortOrder(): Int = error("unused")
        override suspend fun deleteAllTypes() = error("unused")

        override fun entriesBetween(
            start: LocalDate,
            end: LocalDate,
        ): Flow<List<InvestmentEntryWithType>> = error("unused")

        override fun entriesBetweenByKind(
            kind: String,
            start: LocalDate,
            end: LocalDate,
        ): Flow<List<InvestmentEntryWithType>> = error("unused")

        override fun savingsBalanceAsOf(asOf: LocalDate): Flow<Long> = error("unused")
        override fun investedTotalAsOf(asOf: LocalDate): Flow<Long> = error("unused")
        override fun allTypes(): Flow<List<InvestmentTypeEntity>> = error("unused")
    }
}
