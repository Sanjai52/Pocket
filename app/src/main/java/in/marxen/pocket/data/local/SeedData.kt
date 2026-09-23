package `in`.marxen.pocket.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

class SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        val categories = listOf(
            "Food", "Groceries", "Transport", "Shopping", "Bills",
            "Entertainment", "Health", "Education", "Travel", "Personal", "Other",
        )
        val now = System.currentTimeMillis()
        categories.forEach { name ->
            db.execSQL(
                "INSERT INTO categories (name, icon, color, is_hidden, created_at, updated_at) VALUES (?, NULL, NULL, 0, $now, $now)",
                arrayOf(name),
            )
        }

        val subcategories = mapOf(
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
        subcategories.forEach { (categoryName, items) ->
            items.forEach { subName ->
                db.execSQL(
                    """INSERT INTO subcategories (name, category_id, is_hidden, created_at, updated_at)
                       VALUES (?, (SELECT id FROM categories WHERE name = ?), 0, $now, $now)""",
                    arrayOf(subName, categoryName),
                )
            }
        }
    }
}
