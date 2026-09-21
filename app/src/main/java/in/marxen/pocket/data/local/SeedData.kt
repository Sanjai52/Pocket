package `in`.marxen.pocket.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        val now = System.currentTimeMillis()
        val categories = listOf(
            "Food" to "food", "Groceries" to "groceries", "Transport" to "transport",
            "Shopping" to "shopping", "Bills" to "bills", "Entertainment" to null,
            "Health" to null, "Education" to null, "Travel" to null, "Personal" to null,
            "Other" to "other",
        )
        categories.forEach { (name, systemKey) ->
            db.execSQL(
                "INSERT INTO categories (name, icon, color, system_key, is_hidden, created_at, updated_at) VALUES (?, NULL, NULL, ?, 0, $now, $now)",
                arrayOf(name, systemKey),
            )
        }
    }
}
