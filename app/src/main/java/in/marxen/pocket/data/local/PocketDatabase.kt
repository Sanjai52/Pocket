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
import `in`.marxen.pocket.data.local.dao.MerchantCategoryMemoryDao
import `in`.marxen.pocket.data.local.dao.PaymentAttemptDao
import `in`.marxen.pocket.data.local.dao.PaymentMethodDao
import `in`.marxen.pocket.data.local.dao.RecurringTransactionDao
import `in`.marxen.pocket.data.local.dao.TransactionDao
import `in`.marxen.pocket.data.local.entity.BudgetEntity
import `in`.marxen.pocket.data.local.entity.CategoryEntity
import `in`.marxen.pocket.data.local.entity.MerchantCategoryMemoryEntity
import `in`.marxen.pocket.data.local.entity.PaymentAttemptEntity
import `in`.marxen.pocket.data.local.entity.PaymentMethodEntity
import `in`.marxen.pocket.data.local.entity.RecurringTransactionEntity
import `in`.marxen.pocket.data.local.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        PaymentMethodEntity::class,
        BudgetEntity::class,
        RecurringTransactionEntity::class,
        PaymentAttemptEntity::class,
        MerchantCategoryMemoryEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class PocketDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun paymentMethodDao(): PaymentMethodDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringTransactionDao(): RecurringTransactionDao
    abstract fun paymentAttemptDao(): PaymentAttemptDao
    abstract fun merchantCategoryMemoryDao(): MerchantCategoryMemoryDao

    companion object {
        @Volatile
        private var INSTANCE: PocketDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS payment_attempts (
                        id TEXT NOT NULL PRIMARY KEY,
                        payee_vpa TEXT NOT NULL,
                        payee_vpa_key TEXT NOT NULL,
                        payee_name TEXT,
                        merchant_category_code TEXT,
                        amount_paise INTEGER NOT NULL,
                        currency TEXT NOT NULL,
                        category_id INTEGER NOT NULL,
                        local_note TEXT,
                        launch_plan TEXT NOT NULL,
                        sent_txn_ref TEXT,
                        qr_kind TEXT NOT NULL,
                        raw_upi_uri TEXT,
                        provider TEXT NOT NULL,
                        provider_package TEXT,
                        status TEXT NOT NULL,
                        result_hint TEXT NOT NULL,
                        result_code INTEGER,
                        app_status TEXT,
                        app_txn_id TEXT,
                        app_approval_ref TEXT,
                        app_response_code TEXT,
                        recorded_source TEXT,
                        created_at INTEGER NOT NULL,
                        launched_at INTEGER,
                        resolved_at INTEGER,
                        last_prompted_at INTEGER,
                        updated_at INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_payment_attempts_status ON payment_attempts(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_payment_attempts_payee_vpa_key ON payment_attempts(payee_vpa_key)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_payment_attempts_created_at ON payment_attempts(created_at)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS merchant_category_memory (
                        payee_vpa_key TEXT NOT NULL PRIMARY KEY,
                        category_id INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )

                db.execSQL("ALTER TABLE transactions ADD COLUMN payment_attempt_id TEXT")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_payment_attempt_id ON transactions(payment_attempt_id)")
                db.execSQL("ALTER TABLE categories ADD COLUMN system_key TEXT")
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
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
