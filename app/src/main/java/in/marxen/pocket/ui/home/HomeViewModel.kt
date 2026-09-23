package `in`.marxen.pocket.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import `in`.marxen.pocket.core.date.asiaKolkataToday
import `in`.marxen.pocket.core.money.formatPaiseAsRupees
import `in`.marxen.pocket.data.local.entity.CategoryEntity
import `in`.marxen.pocket.data.local.entity.SubcategoryEntity
import `in`.marxen.pocket.data.local.entity.TransactionEntity
import `in`.marxen.pocket.data.prefs.PocketPrefs
import `in`.marxen.pocket.data.repository.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.time.YearMonth

data class TransactionUi(
    val entity: TransactionEntity,
    val categoryName: String,
    val categoryColor: Long?,
    val categoryIcon: String?,
    val subcategoryName: String? = null,
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
    val subcategories: Map<Long, SubcategoryEntity> = emptyMap(),
    val userName: String = "",
    val earliestMonth: YearMonth = YearMonth.now(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: TransactionRepository,
    private val prefs: PocketPrefs,
) : ViewModel() {
    private val _selectedDate = MutableStateFlow(asiaKolkataToday())
    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    private val _earliestMonth = MutableStateFlow(YearMonth.now())

    private data class MonthData(
        val expenses: Long,
        val income: Long,
        val transactions: List<TransactionEntity>,
        val categories: Map<Long, CategoryEntity>,
        val subcategories: Map<Long, SubcategoryEntity>,
    )

    private val monthlyData: StateFlow<MonthData> = combine(
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
    ) { expenses, income, transactions, categories ->
        MonthData(
            expenses = expenses,
            income = income,
            transactions = transactions,
            categories = categories.associateBy { it.id },
            subcategories = emptyMap(),
        )
    }.combine(repository.getAllActiveSubcategories()) { data, subcategories ->
        val subMap = subcategories.associateBy { it.id }
        data.copy(subcategories = subMap)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonthData(0, 0, emptyList(), emptyMap(), emptyMap()))

    val uiState: StateFlow<HomeUiState> = combine(
        monthlyData,
        _selectedDate,
        prefs.userName,
        _earliestMonth,
    ) { data, selectedDate, userName, earliestMonth ->
        val today = asiaKolkataToday()
        val todayTxns = data.transactions.filter { it.transactionDate == today }
        val selectedTxns = data.transactions.filter { it.transactionDate == selectedDate }

        HomeUiState(
            month = _selectedMonth.value,
            totalExpenses = data.expenses,
            totalIncome = data.income,
            balance = data.income - data.expenses,
            totalExpensesFormatted = formatPaiseAsRupees(data.expenses),
            totalIncomeFormatted = formatPaiseAsRupees(data.income),
            balanceFormatted = formatPaiseAsRupees(data.income - data.expenses),
            todayTransactions = todayTxns.map { it.toUi(data.categories, data.subcategories) },
            selectedDateTransactions = selectedTxns.map { it.toUi(data.categories, data.subcategories) },
            selectedDate = selectedDate,
            monthTransactions = data.transactions,
            categories = data.categories,
            subcategories = data.subcategories,
            userName = userName,
            earliestMonth = earliestMonth,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    init {
        loadEarliestMonth()
    }

    private fun loadEarliestMonth() {
        viewModelScope.launch {
            val earliest = repository.getEarliestTransactionDate()
            if (earliest != null) {
                _earliestMonth.value = YearMonth.from(earliest)
            }
        }
    }

    fun selectDate(date: java.time.LocalDate) {
        _selectedDate.value = date
    }

    fun selectMonth(month: YearMonth) {
        _selectedMonth.value = month
    }

    private fun TransactionEntity.toUi(cats: Map<Long, CategoryEntity>, subs: Map<Long, SubcategoryEntity>): TransactionUi {
        val cat = cats[categoryId]
        val sub = subcategoryId?.let { subs[it] }
        return TransactionUi(
            entity = this,
            categoryName = cat?.name ?: "Unknown",
            categoryColor = cat?.color,
            categoryIcon = cat?.icon,
            subcategoryName = sub?.name,
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
