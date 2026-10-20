package `in`.marxen.pocket.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

class SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        val now = System.currentTimeMillis()

        SubcategoryDefaults.CATEGORY_NAMES.forEach { name ->
            db.execSQL(
                "INSERT INTO categories (name, icon, color, is_hidden, created_at, updated_at) VALUES (?, NULL, NULL, 0, $now, $now)",
                arrayOf(name),
            )
        }

        SubcategoryDefaults.BY_CATEGORY.forEach { (categoryName, items) ->
            items.forEach { subName ->
                db.execSQL(
                    """INSERT INTO subcategories (name, category_id, is_hidden, created_at, updated_at)
                       VALUES (?, (SELECT id FROM categories WHERE name = ?), 0, $now, $now)""",
                    arrayOf(subName, categoryName),
                )
            }
        }

        InvestmentDefaults.TYPE_NAMES.forEachIndexed { order, name ->
            db.execSQL(
                "INSERT INTO investment_types (name, sort_order, created_at) VALUES (?, ?, $now)",
                arrayOf<Any>(name, order),
            )
        }
    }
}
