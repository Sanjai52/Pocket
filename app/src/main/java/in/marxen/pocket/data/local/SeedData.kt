package `in`.marxen.pocket.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        val categories = listOf(
            "Food", "Groceries", "Transport", "Shopping", "Bills",
            "Entertainment", "Health", "Education", "Travel", "Personal", "Other",
        )
        categories.forEach { name ->
            db.execSQL(
                "INSERT INTO categories (name, icon, color, is_hidden, created_at, updated_at) VALUES (?, NULL, NULL, 0, ${System.currentTimeMillis()}, ${System.currentTimeMillis()})",
                arrayOf(name),
            )
        }
    }
}
