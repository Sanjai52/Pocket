package `in`.marxen.pocket.data.local

import `in`.marxen.pocket.data.local.dao.SubcategoryDao
import `in`.marxen.pocket.data.local.entity.SubcategoryEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubcategoryDefaultsTest {
    private val categoryIds = SubcategoryDefaults.CATEGORY_NAMES
        .mapIndexed { index, name -> name to (index + 1).toLong() }
        .toMap()

    @Test
    fun systemSubcategories_areThe41BuiltInsWithPinnedIds() {
        val subs = SubcategoryDefaults.systemSubcategories(categoryIds, now = Instant.EPOCH)

        assertEquals(41, subs.size)
        assertEquals((1L..41L).toList(), subs.map { it.id })
        assertEquals("Snacks", subs.first().name)
        assertEquals(1L, subs.first().categoryId)
        assertEquals("Vegetables", subs.first { it.id == 6L }.name)
        assertEquals(2L, subs.first { it.id == 6L }.categoryId)
        assertEquals("Auto", subs.first { it.id == 12L }.name)
        assertEquals(3L, subs.first { it.id == 12L }.categoryId)
        assertEquals("Miscellaneous", subs.first { it.id == 41L }.name)
        assertEquals(11L, subs.first { it.id == 41L }.categoryId)
        assertTrue(subs.all { it.systemKey == null && !it.isHidden })
    }

    @Test
    fun systemSubcategories_missingCategoryKeepsIdNumbering() {
        val subs = SubcategoryDefaults.systemSubcategories(categoryIds - "Food", now = Instant.EPOCH)

        assertEquals(36, subs.size)
        assertEquals("Vegetables", subs.first().name)
        assertEquals(6L, subs.first().id)
    }

    @Test
    fun seedSystemSetIfEmpty_fillsEmptyTableWithPinnedIds() {
        val dao = FakeSubcategoryDao()

        runBlocking { SubcategoryDefaults.seedSystemSetIfEmpty(dao, categoryIds) }

        assertEquals(41, dao.inserted.size)
        assertEquals((1L..41L).toList(), dao.inserted.map { it.id })
    }

    @Test
    fun seedSystemSetIfEmpty_leavesExistingRowsUntouched() {
        val dao = FakeSubcategoryDao(existing = listOf(SubcategoryEntity(name = "Custom", categoryId = 1)))

        runBlocking { SubcategoryDefaults.seedSystemSetIfEmpty(dao, categoryIds) }

        assertTrue(dao.inserted.isEmpty())
    }

    private class FakeSubcategoryDao(
        private val existing: List<SubcategoryEntity> = emptyList(),
    ) : SubcategoryDao {
        val inserted = mutableListOf<SubcategoryEntity>()

        override suspend fun insert(subcategory: SubcategoryEntity): Long = error("unused")

        override suspend fun update(subcategory: SubcategoryEntity) = error("unused")

        override suspend fun delete(subcategory: SubcategoryEntity) = error("unused")

        override suspend fun insertAll(subcategories: List<SubcategoryEntity>) {
            inserted += subcategories
        }

        override suspend fun deleteAll() = error("unused")

        override suspend fun getById(id: Long): SubcategoryEntity? = error("unused")

        override fun getActiveByCategoryId(categoryId: Long): Flow<List<SubcategoryEntity>> = error("unused")

        override fun getAllByCategoryId(categoryId: Long): Flow<List<SubcategoryEntity>> = error("unused")

        override suspend fun getAll(): List<SubcategoryEntity> = existing

        override fun getActive(): Flow<List<SubcategoryEntity>> = error("unused")
    }
}
