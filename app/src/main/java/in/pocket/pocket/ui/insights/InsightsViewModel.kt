package `in`.marxen.pocket.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import `in`.marxen.pocket.core.money.formatPaiseAsRupees
import `in`.marxen.pocket.core.money.formatPaiseCompact
import `in`.marxen.pocket.core.money.formatPaiseShort
import `in`.marxen.pocket.data.local.dao.InvestmentEntryWithType
import `in`.marxen.pocket.data.local.entity.InvestmentEntryEntity
import `in`.marxen.pocket.data.repository.InvestmentRepository
import `in`.marxen.pocket.data.repository.TransactionRepository
import `in`.marxen.pocket.ui.investment.InvestmentRowUi
import `in`.marxen.pocket.ui.investment.toRowUi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

private data class InvestmentData(
    val entries: List<InvestmentEntryWithType>,
    val savingsBalance: Long,
    val investedTotal: Long,
)

data class InsightsUiState(
    val month: YearMonth = YearMonth.now(),
    val totalSpent: Long = 0,
    val totalIncome: Long = 0,
    val totalSpentFormatted: String = "₹0",
    val totalIncomeFormatted: String = "₹0",
    val categoryBreakdown: List<CategoryBreakdown> = emptyList(),
    val monthlyTrend: List<MonthTotal> = emptyList(),
    val savingsBalanceFormatted: String = "₹0",
    val investedTotalFormatted: String = "₹0",
    val investmentRows: List<InvestmentRowUi> = emptyList(),
)

class InsightsViewModel(
    private val repository: TransactionRepository,
    private val investmentRepository: InvestmentRepository,
) : ViewModel() {
    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    private val _monthlyTrends = MutableStateFlow<List<MonthTotal>>(emptyList())

    private val investmentData = _selectedMonth.flatMapLatest { month ->
        combine(
            investmentRepository.getEntries(month),
            investmentRepository.getSavingsBalanceAsOf(month),
            investmentRepository.getInvestedTotalAsOf(month),
        ) { entries, savingsBalance, investedTotal ->
            InvestmentData(entries, savingsBalance, investedTotal)
        }
    }

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
        investmentData,
    ) { expenses, income, categoryTotals, categories, investment ->
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
            savingsBalanceFormatted = formatPaiseShort(investment.savingsBalance),
            investedTotalFormatted = formatPaiseShort(investment.investedTotal),
            investmentRows = investment.entries.map { it.toRowUi() },
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

    fun selectMonth(month: YearMonth) {
        _selectedMonth.value = month
        loadMonthlyTrends()
    }

    private var lastDeletedInvestment: InvestmentEntryEntity? = null

    private val _investmentDeletedEvent = MutableSharedFlow<Unit>()
    val investmentDeletedEvent: SharedFlow<Unit> = _investmentDeletedEvent.asSharedFlow()

    fun deleteInvestmentEntry(id: Long) {
        viewModelScope.launch {
            val entry = investmentRepository.getEntry(id) ?: return@launch
            lastDeletedInvestment = entry
            investmentRepository.deleteEntry(entry)
            _investmentDeletedEvent.emit(Unit)
        }
    }

    fun undoInvestmentDelete() {
        val entry = lastDeletedInvestment ?: return
        lastDeletedInvestment = null
        viewModelScope.launch { investmentRepository.insertEntry(entry.copy(id = 0)) }
    }

    class Factory(
        private val repository: TransactionRepository,
        private val investmentRepository: InvestmentRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return InsightsViewModel(repository, investmentRepository) as T
        }
    }
}
