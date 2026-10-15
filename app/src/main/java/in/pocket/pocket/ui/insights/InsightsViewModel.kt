package `in`.marxen.pocket.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import `in`.marxen.pocket.core.money.formatPaiseAsRupees
import `in`.marxen.pocket.data.repository.TransactionRepository
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

data class CategoryBreakdown(
    val categoryId: Long,
    val name: String,
    val color: Long?,
    val amount: Long,
    val formattedAmount: String,
    val percentage: Float,
)

data class MonthTotal(
    val label: String,
    val total: Long,
    val formattedTotal: String,
)

data class InsightsUiState(
    val month: YearMonth = YearMonth.now(),
    val totalSpent: Long = 0,
    val totalIncome: Long = 0,
    val totalSpentFormatted: String = "₹0",
    val totalIncomeFormatted: String = "₹0",
    val categoryBreakdown: List<CategoryBreakdown> = emptyList(),
    val monthlyTrend: List<MonthTotal> = emptyList(),
    val selectedTab: Int = 0,
)

class InsightsViewModel(private val repository: TransactionRepository) : ViewModel() {
    private val _selectedTab = MutableStateFlow(0)
    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    private val _monthlyTrends = MutableStateFlow<List<MonthTotal>>(emptyList())

    val uiState: StateFlow<InsightsUiState> = combine(
        _selectedMonth.flatMapLatest { m ->
            repository.getTotalExpenses(m)
        },
        _selectedMonth.flatMapLatest { m ->
            repository.getTotalIncome(m)
        },
        _selectedMonth.flatMapLatest { m ->
            repository.getCategoryTotals(m)
        },
        repository.getActiveCategories(),
        _selectedTab,
    ) { expenses, income, categoryTotals, categories, selectedTab ->
        val selectedMonth = _selectedMonth.value
        val catMap = categories.associateBy { it.id }
        val breakdown = categoryTotals.map { ct ->
            val cat = catMap[ct.categoryId]
            CategoryBreakdown(
                categoryId = ct.categoryId,
                name = cat?.name ?: "Unknown",
                color = cat?.color,
                amount = ct.total,
                formattedAmount = formatPaiseAsRupees(ct.total),
                percentage = if (expenses > 0) ct.total.toFloat() / expenses.toFloat() else 0f,
            )
        }

        InsightsUiState(
            month = selectedMonth,
            totalSpent = expenses,
            totalIncome = income,
            totalSpentFormatted = formatPaiseAsRupees(expenses),
            totalIncomeFormatted = formatPaiseAsRupees(income),
            categoryBreakdown = breakdown,
            monthlyTrend = emptyList(),
            selectedTab = selectedTab,
        )
    }.combine(_monthlyTrends) { state, trends ->
        state.copy(monthlyTrend = trends)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InsightsUiState())

    init {
        loadMonthlyTrends()
    }

    private fun loadMonthlyTrends() {
        viewModelScope.launch {
            val currentMonth = _selectedMonth.value
            val firstMonth = currentMonth.minusMonths(5)
            val totalsByMonth = repository.getMonthlyExpenseTotals(
                firstMonth.atDay(1),
                currentMonth.atEndOfMonth(),
            ).associate { java.time.YearMonth.of(it.year, it.month) to it.totalPaise }
            _monthlyTrends.value = (0..5).map { offset ->
                val month = firstMonth.plusMonths(offset.toLong())
                val expenses = totalsByMonth[month] ?: 0L
                MonthTotal(
                    label = month.month.name.take(3).replaceFirstChar { it.titlecase() },
                    total = expenses,
                    formattedTotal = formatPaiseAsRupees(expenses),
                )
            }
        }
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun selectMonth(month: YearMonth) {
        _selectedMonth.value = month
        loadMonthlyTrends()
    }

    class Factory(private val repository: TransactionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return InsightsViewModel(repository) as T
        }
    }
}
