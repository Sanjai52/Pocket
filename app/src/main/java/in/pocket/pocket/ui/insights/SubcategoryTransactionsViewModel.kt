package `in`.marxen.pocket.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import `in`.marxen.pocket.core.date.pocketZoneId
import `in`.marxen.pocket.core.money.formatPaiseAsRupees
import `in`.marxen.pocket.data.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class LedgerRow(
    val id: Long,
    val amountFormatted: String,
    val title: String,
    val dateLabel: String,
    val timeLabel: String,
)

data class SubcategoryTransactionsUiState(
    val total: Long = 0,
    val totalFormatted: String = "₹0",
    val rows: List<LedgerRow> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class SubcategoryTransactionsViewModel(
    repository: TransactionRepository,
    categoryId: Long,
    subcategoryId: Long,
    initialMonth: YearMonth,
) : ViewModel() {

    private val _selectedMonth = MutableStateFlow(initialMonth)
    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    val uiState: StateFlow<SubcategoryTransactionsUiState> = _selectedMonth
        .flatMapLatest { month ->
            val flow: Flow<List<`in`.marxen.pocket.data.local.entity.TransactionEntity>> =
                repository.getTransactionsBySubcategory(categoryId, subcategoryId, month)
            flow.map { transactions ->
                val rows = transactions.map { tx ->
                    val title = tx.merchant?.takeIf { it.isNotBlank() }
                        ?: tx.note?.takeIf { it.isNotBlank() }
                        ?: "Expense"
                    LedgerRow(
                        id = tx.id,
                        amountFormatted = formatPaiseAsRupees(tx.amountPaise),
                        title = title,
                        dateLabel = tx.transactionDate.format(dateFormatter),
                        timeLabel = tx.createdAt.atZone(pocketZoneId()).format(timeFormatter),
                    )
                }
                val total = transactions.sumOf { it.amountPaise }
                SubcategoryTransactionsUiState(
                    total = total,
                    totalFormatted = formatPaiseAsRupees(total),
                    rows = rows,
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SubcategoryTransactionsUiState())

    class Factory(
        private val repository: TransactionRepository,
        private val categoryId: Long,
        private val subcategoryId: Long,
        private val initialMonth: YearMonth,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SubcategoryTransactionsViewModel(repository, categoryId, subcategoryId, initialMonth) as T
        }
    }
}
