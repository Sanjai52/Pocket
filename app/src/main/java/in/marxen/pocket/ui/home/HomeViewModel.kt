package `in`.marxen.pocket.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import `in`.marxen.pocket.core.date.asiaKolkataToday
import `in`.marxen.pocket.core.money.formatPaiseAsRupees
import `in`.marxen.pocket.data.local.entity.CategoryEntity
import `in`.marxen.pocket.data.local.entity.TransactionEntity
import `in`.marxen.pocket.data.prefs.PocketPrefs
import `in`.marxen.pocket.data.repository.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

data class TransactionUi(
    val entity: TransactionEntity,
    val categoryName: String,
    val categoryColor: Long?,
    val categoryIcon: String?,
    val formattedAmount: String,
    val timeLabel: String,
)

data class HomeUiState(
    val month: YearMonth = YearMonth.now(),
    val totalExpenses: Long = 0,
    val totalIncome: Long = 0,
    val balance: Long = 0,
    val totalExpensesFormatted: String = "₹0",
    val totalIncomeFormatted: String = "₹0",
    val balanceFormatted: String = "₹0",
    val todayTransactions: List<TransactionUi> = emptyList(),
    val selectedDateTransactions: List<TransactionUi> = emptyList(),
    val selectedDate: java.time.LocalDate = asiaKolkataToday(),
    val monthTransactions: List<TransactionEntity> = emptyList(),
    val categories: Map<Long, CategoryEntity> = emptyMap(),
    val userName: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: TransactionRepository,
    private val prefs: PocketPrefs,
) : ViewModel() {
    private val _selectedDate = MutableStateFlow(asiaKolkataToday())
    private val _selectedMonth = MutableStateFlow(YearMonth.now())

    val uiState: StateFlow<HomeUiState> = combine(
        _selectedMonth.flatMapLatest { m ->
            repository.getTotalExpenses(m)
        },
        _selectedMonth.flatMapLatest { m ->
            repository.getTotalIncome(m)
        },
        _selectedMonth.flatMapLatest { m ->
            repository.getTransactionsByDateRange(m.atDay(1), m.atEndOfMonth())
        },
        repository.getActiveCategories(),
        _selectedDate,
    ) { expenses, income, transactions, categories, selectedDate ->
        val catMap = categories.associateBy { it.id }
        val today = asiaKolkataToday()
        val todayTxns = transactions.filter { it.transactionDate == today }
        val selectedTxns = transactions.filter { it.transactionDate == selectedDate }

        HomeUiState(
            month = _selectedMonth.value,
            totalExpenses = expenses,
            totalIncome = income,
            balance = income - expenses,
            totalExpensesFormatted = formatPaiseAsRupees(expenses),
            totalIncomeFormatted = formatPaiseAsRupees(income),
            balanceFormatted = formatPaiseAsRupees(income - expenses),
            todayTransactions = todayTxns.map { it.toUi(catMap) },
            selectedDateTransactions = selectedTxns.map { it.toUi(catMap) },
            selectedDate = selectedDate,
            monthTransactions = transactions,
            categories = catMap,
        )
    }.combine(prefs.userName) { state, userName ->
        state.copy(userName = userName)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun selectDate(date: java.time.LocalDate) {
        _selectedDate.value = date
    }

    fun selectMonth(month: YearMonth) {
        _selectedMonth.value = month
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }

    private fun TransactionEntity.toUi(cats: Map<Long, CategoryEntity>): TransactionUi {
        val cat = cats[categoryId]
        return TransactionUi(
            entity = this,
            categoryName = cat?.name ?: "Unknown",
            categoryColor = cat?.color,
            categoryIcon = cat?.icon,
            formattedAmount = formatPaiseAsRupees(amountPaise),
            timeLabel = transactionDate.toString(),
        )
    }

    class Factory(
        private val repository: TransactionRepository,
        private val prefs: PocketPrefs,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(repository, prefs) as T
        }
    }
}
