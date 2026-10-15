package `in`.marxen.pocket.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import `in`.marxen.pocket.data.local.dao.BudgetDao
import `in`.marxen.pocket.data.local.dao.CategoryDao
import `in`.marxen.pocket.data.local.dao.PaymentMethodDao
import `in`.marxen.pocket.data.local.dao.RecurringTransactionDao
import `in`.marxen.pocket.data.local.dao.SubcategoryDao
import `in`.marxen.pocket.data.local.dao.TransactionDao
import `in`.marxen.pocket.data.local.entity.BudgetEntity
import `in`.marxen.pocket.data.local.entity.CategoryEntity
import `in`.marxen.pocket.data.local.entity.PaymentMethodEntity
import `in`.marxen.pocket.data.local.entity.RecurringTransactionEntity
import `in`.marxen.pocket.data.local.entity.SubcategoryEntity
import `in`.marxen.pocket.data.local.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        PaymentMethodEntity::class,
        BudgetEntity::class,
        RecurringTransactionEntity::class,
        SubcategoryEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class PocketDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun paymentMethodDao(): PaymentMethodDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringTransactionDao(): RecurringTransactionDao
    abstract fun subcategoryDao(): SubcategoryDao

    companion object {
        @Volatile
        private var INSTANCE: PocketDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN subcategory_id INTEGER")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS subcategories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        category_id INTEGER NOT NULL,
                        system_key TEXT,
                        is_hidden INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_subcategories_category_id ON subcategories(category_id)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_type_transaction_date ON transactions(type, transaction_date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_category_id_transaction_date_type ON transactions(category_id, transaction_date, type)")
            }
        }

        fun getInstance(context: Context): PocketDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PocketDatabase::class.java,
                    "pocket.db",
                ).addCallback(SeedCallback())
                    .addMigrations(MIGRATION_1_2)
                    .addMigrations(MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
