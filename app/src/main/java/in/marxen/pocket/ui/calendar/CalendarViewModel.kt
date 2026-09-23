package `in`.marxen.pocket.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.Immutable
import `in`.marxen.pocket.core.date.asiaKolkataToday
import `in`.marxen.pocket.core.money.formatPaiseAsRupees
import `in`.marxen.pocket.data.local.entity.CategoryEntity
import `in`.marxen.pocket.data.local.entity.TransactionEntity
import `in`.marxen.pocket.data.repository.TransactionRepository
import `in`.marxen.pocket.ui.home.TransactionUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Immutable
data class DaySummary(
    val date: LocalDate,
    val totalExpenses: Long,
    val totalIncome: Long,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val isSelected: Boolean,
    val hasTransactions: Boolean = false,
    val dotColor: Long? = null,
)

data class CalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = asiaKolkataToday(),
    val days: List<DaySummary> = emptyList(),
    val selectedDayTransactions: List<TransactionUi> = emptyList(),
    val selectedDayTotal: String = "₹0",
)

class CalendarViewModel(private val repository: TransactionRepository) : ViewModel() {
    private val _selectedDate = MutableStateFlow(asiaKolkataToday())
    private val _month = MutableStateFlow(YearMonth.now())

    private data class MonthlyCalendarData(
        val month: YearMonth,
        val days: List<DaySummary>,
        val transactionsByDay: Map<LocalDate, List<TransactionEntity>>,
        val categories: Map<Long, CategoryEntity>,
    )

    private val monthlyData = combine(
        repository.getActiveCategories(),
        _month.flatMapLatest { month ->
            repository.getTransactionsByDateRange(month.atDay(1), month.atEndOfMonth())
        },
        _month,
    ) { categories, transactions, month ->
        val catMap = categories.associateBy { it.id }
        val today = asiaKolkataToday()
        val txnsByDay = transactions.groupBy { it.transactionDate }
        val startOfMonth = month.atDay(1)
        val firstDayOfWeek = startOfMonth.dayOfWeek
        val offset = (firstDayOfWeek.value % 7)
        val gridStart = startOfMonth.minusDays(offset.toLong())
        val days = (0..41).map { i ->
            val date = gridStart.plusDays(i.toLong())
            val dayTxns = txnsByDay[date] ?: emptyList()
            val totalsByCategory = mutableMapOf<Long, Long>()
            var totalExpenses = 0L
            var totalIncome = 0L
            dayTxns.forEach { transaction ->
                if (transaction.type == "EXPENSE") {
                    totalExpenses += transaction.amountPaise
                    totalsByCategory[transaction.categoryId] =
                        (totalsByCategory[transaction.categoryId] ?: 0L) + transaction.amountPaise
                } else if (transaction.type == "INCOME") {
                    totalIncome += transaction.amountPaise
                }
            }
            val topCategoryId = totalsByCategory.maxByOrNull { it.value }?.key
            val dotColor = topCategoryId?.let { catMap[it]?.color }
            DaySummary(
                date = date,
                totalExpenses = totalExpenses,
                totalIncome = totalIncome,
                isCurrentMonth = date.month == month.month && date.year == month.year,
                isToday = date == today,
                isSelected = false,
                hasTransactions = dayTxns.isNotEmpty(),
                dotColor = dotColor,
            )
        }
        MonthlyCalendarData(month, days, txnsByDay, catMap)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<CalendarUiState> = combine(monthlyData, _selectedDate) { data, selectedDate ->
        if (data == null) {
            CalendarUiState(selectedDate = selectedDate)
        } else {
            val selectedDayTxns = data.transactionsByDay[selectedDate] ?: emptyList()
            CalendarUiState(
                month = data.month,
                selectedDate = selectedDate,
                days = data.days.map { it.copy(isSelected = it.date == selectedDate) },
                selectedDayTransactions = selectedDayTxns.map { txn ->
                    val cat = data.categories[txn.categoryId]
                    TransactionUi(
                        entity = txn,
                        categoryName = cat?.name ?: "Other",
                        categoryColor = cat?.color,
                        categoryIcon = cat?.icon,
                        formattedAmount = formatPaiseAsRupees(txn.amountPaise),
                        timeLabel = formatTime(txn.createdAt),
                    )
                },
                selectedDayTotal = formatPaiseAsRupees(
                    selectedDayTxns.filter { it.type == "EXPENSE" }.sumOf { it.amountPaise },
                ),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CalendarUiState())

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun previousMonth() {
        _month.value = _month.value.minusMonths(1)
    }

    fun nextMonth() {
        _month.value = _month.value.plusMonths(1)
    }

    class Factory(private val repository: TransactionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CalendarViewModel(repository) as T
        }
    }
}

private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

private fun formatTime(instant: Instant): String {
    return instant.atZone(ZoneId.of("Asia/Kolkata")).format(timeFormatter)
}
