package `in`.marxen.pocket.data.local

import `in`.marxen.pocket.data.local.dao.SubcategoryDao
import `in`.marxen.pocket.data.local.entity.SubcategoryEntity
import java.time.Instant

/**
 * Single source of truth for the built-in categories and subcategories. Used by the
 * fresh-install seed and by the recovery when restoring a backup created before
 * subcategories were part of the backup format.
 *
 * Ids are pinned: every shipped database seeded these rows in exactly this order, so
 * categories run Food=1..Other=11 and system subcategories 1..41.
 */
object SubcategoryDefaults {
    /** Category names in canonical id order (Food=1 .. Other=11). */
    val CATEGORY_NAMES = listOf(
        "Food", "Groceries", "Transport", "Shopping", "Bills",
        "Entertainment", "Health", "Education", "Travel", "Personal", "Other",
    )

    /** System subcategories per category, in canonical id order (1..41 across all rows). */
    val BY_CATEGORY: Map<String, List<String>> = mapOf(
        "Food" to listOf("Snacks", "Breakfast", "Lunch", "Dinner", "Drinks"),
        "Groceries" to listOf("Vegetables", "Fruits", "Dairy", "Staples", "Household"),
        "Transport" to listOf("Fuel", "Auto", "Bus/Metro", "Taxi", "Parking"),
        "Shopping" to listOf("Clothes", "Electronics", "Accessories", "Gifts"),
        "Bills" to listOf("Electricity", "Water", "Internet", "Phone", "Rent"),
        "Entertainment" to listOf("Movies", "Games", "Subscriptions", "Outings"),
        "Health" to listOf("Medicine", "Doctor", "Gym", "Supplements"),
        "Education" to listOf("Books", "Courses", "Fees", "Stationery"),
        "Travel" to listOf("Flight", "Hotel", "Sightseeing", "Food"),
        "Other" to listOf("Miscellaneous"),
    )

    /**
     * The 41 built-in subcategories with their original ids. [categoryIdsByName] supplies each
     * category's id (pass the ids from the backup being restored). A category missing from the
     * map still consumes its ids so the numbering of later rows stays pinned.
     */
    fun systemSubcategories(
        categoryIdsByName: Map<String, Long>,
        now: Instant = Instant.now(),
    ): List<SubcategoryEntity> {
        val entities = mutableListOf<SubcategoryEntity>()
        var id = 1L
        for (categoryName in CATEGORY_NAMES) {
            val categoryId = categoryIdsByName[categoryName]
            BY_CATEGORY[categoryName].orEmpty().forEach { name ->
                if (categoryId != null) {
                    entities += SubcategoryEntity(
                        id = id,
                        name = name,
                        categoryId = categoryId,
                        createdAt = now,
                        updatedAt = now,
                    )
                }
                id++
            }
        }
        return entities
    }

    /**
     * Fills an empty subcategory table with the built-in set. Used after restoring a backup that
     * carried no subcategories (created before they were included); no-op when rows already
     * exist (a current-format backup was restored instead).
     */
    suspend fun seedSystemSetIfEmpty(dao: SubcategoryDao, categoryIdsByName: Map<String, Long>) {
        if (dao.getAll().isEmpty()) {
            dao.insertAll(systemSubcategories(categoryIdsByName))
        }
    }
}
