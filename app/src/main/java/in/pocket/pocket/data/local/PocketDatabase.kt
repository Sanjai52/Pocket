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
import `in`.marxen.pocket.data.local.dao.InvestmentDao
import `in`.marxen.pocket.data.local.dao.PaymentMethodDao
import `in`.marxen.pocket.data.local.dao.RecurringTransactionDao
import `in`.marxen.pocket.data.local.dao.SubcategoryDao
import `in`.marxen.pocket.data.local.dao.TransactionDao
import `in`.marxen.pocket.data.local.entity.BudgetEntity
import `in`.marxen.pocket.data.local.entity.CategoryEntity
import `in`.marxen.pocket.data.local.entity.InvestmentEntryEntity
import `in`.marxen.pocket.data.local.entity.InvestmentTypeEntity
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
        InvestmentTypeEntity::class,
        InvestmentEntryEntity::class,
    ],
    version = 5,
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
    abstract fun investmentDao(): InvestmentDao

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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS investment_types (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        sort_order INTEGER NOT NULL,
                        created_at INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS investment_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        kind TEXT NOT NULL,
                        direction TEXT NOT NULL,
                        type_id INTEGER,
                        amount_paise INTEGER NOT NULL,
                        entry_date TEXT NOT NULL,
                        note TEXT,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        FOREIGN KEY (type_id) REFERENCES investment_types(id) ON DELETE NO ACTION
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_investment_entries_entry_date ON investment_entries(entry_date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_investment_entries_type_id ON investment_entries(type_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_investment_entries_kind_entry_date ON investment_entries(kind, entry_date)")
                val now = System.currentTimeMillis()
                InvestmentDefaults.TYPE_NAMES.forEachIndexed { order, name ->
                    db.execSQL(
                        "INSERT INTO investment_types (name, sort_order, created_at) VALUES (?, $order, $now)",
                        arrayOf(name),
                    )
                }
            }
        }

        /**
         * Adds the "Gold" built-in type to databases created before it existed. Fresh
         * installs and 3->4 upgrades already seed the full list, so the insert is guarded
         * by name and becomes a no-op there. "Other" keeps its add-your-own spot last.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()
                db.execSQL(
                    "INSERT INTO investment_types (name, sort_order, created_at) " +
                        "SELECT 'Gold', 4, $now " +
                        "WHERE NOT EXISTS (SELECT 1 FROM investment_types WHERE LOWER(name) = 'gold')",
                )
                db.execSQL("UPDATE investment_types SET sort_order = 5 WHERE LOWER(name) = 'other'")
            }
        }

        fun getInstance(context: Context): PocketDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PocketDatabase::class.java,
                    "pocket.db",
                ).addCallback(SeedCallback())
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
