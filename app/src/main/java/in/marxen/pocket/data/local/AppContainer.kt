package `in`.marxen.pocket.data.local

import android.content.Context
import `in`.marxen.pocket.data.prefs.PocketPrefs
import `in`.marxen.pocket.data.repository.TransactionRepository

class AppContainer(context: Context) {
    val database = PocketDatabase.getInstance(context)
    val repository = TransactionRepository(
        transactionDao = database.transactionDao(),
        categoryDao = database.categoryDao(),
        paymentAttemptDao = database.paymentAttemptDao(),
        merchantCategoryMemoryDao = database.merchantCategoryMemoryDao(),
    )
    val prefs = PocketPrefs(context)
}
