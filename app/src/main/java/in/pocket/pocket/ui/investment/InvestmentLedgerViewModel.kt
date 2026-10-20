package `in`.marxen.pocket.ui.investment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import `in`.marxen.pocket.core.money.formatPaiseCompact
import `in`.marxen.pocket.data.local.entity.InvestmentEntryEntity
import `in`.marxen.pocket.data.repository.InvestmentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

data class InvestmentLedgerUiState(
    val title: String = "",
    val totalLabel: String = "",
    val totalFormatted: String = "₹0",
    val monthLine: String = "",
    val rows: List<InvestmentRowUi> = emptyList(),
)

class InvestmentLedgerViewModel(
    private val repository: InvestmentRepository,
    kind: String,
    initialMonth: YearMonth,
) : ViewModel() {
    val uiState: StateFlow<InvestmentLedgerUiState> = combine(
        if (kind == "SAVING") repository.getSavingsBalanceAsOf(initialMonth) else repository.getInvestedTotalAsOf(initialMonth),
        repository.getEntries(kind, initialMonth),
    ) { total, entries ->
        val monthSum = entries.sumOf { e ->
            if (e.entry.kind == "SAVING" && e.entry.direction == "OUT") -e.entry.amountPaise else e.entry.amountPaise
        }
        val monthName = initialMonth.month.name.replaceFirstChar { it.titlecase() }
        InvestmentLedgerUiState(
            title = if (kind == "SAVING") "Savings" else "Investments",
            totalLabel = if (kind == "SAVING") "Balance" else "Invested",
            totalFormatted = formatPaiseCompact(total),
            monthLine = if (kind == "SAVING") {
                "$monthName · net ${if (monthSum < 0) "\u2212" else "+"}${formatPaiseCompact(kotlin.math.abs(monthSum))}"
            } else {
                "$monthName · ${formatPaiseCompact(monthSum)} added"
            },
            rows = entries.map { it.toRowUi() },
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InvestmentLedgerUiState())

    private var lastDeleted: InvestmentEntryEntity? = null

    private val _deletedEvent = MutableSharedFlow<Unit>()
    val deletedEvent: SharedFlow<Unit> = _deletedEvent.asSharedFlow()

    fun deleteEntry(id: Long) {
        viewModelScope.launch {
            val entry = repository.getEntry(id) ?: return@launch
            lastDeleted = entry
            repository.deleteEntry(entry)
            _deletedEvent.emit(Unit)
        }
    }

    fun undoDelete() {
        val entry = lastDeleted ?: return
        lastDeleted = null
        viewModelScope.launch { repository.insertEntry(entry.copy(id = 0)) }
    }

    class Factory(
        private val repository: InvestmentRepository,
        private val kind: String,
        private val initialMonth: YearMonth,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return InvestmentLedgerViewModel(repository, kind, initialMonth) as T
        }
    }
}
