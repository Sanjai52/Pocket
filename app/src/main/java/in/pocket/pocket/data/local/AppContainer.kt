package `in`.marxen.pocket.data.local

import android.content.Context
import `in`.marxen.pocket.data.prefs.PocketPrefs
import `in`.marxen.pocket.data.repository.InvestmentRepository
import `in`.marxen.pocket.data.repository.TransactionRepository

class AppContainer(context: Context) {
    val database = PocketDatabase.getInstance(context)
    val repository = TransactionRepository(
        database.transactionDao(),
        database.categoryDao(),
        database.subcategoryDao(),
    )
    val investmentRepository = InvestmentRepository(database.investmentDao())
    val prefs = PocketPrefs(context)
}
