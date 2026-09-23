package `in`.marxen.pocket.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import `in`.marxen.pocket.core.money.formatPaiseAsRupees
import `in`.marxen.pocket.data.local.entity.CategoryEntity
import `in`.marxen.pocket.data.repository.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

data class CategoryListItem(
    val categoryId: Long,
    val name: String,
    val color: Long?,
    val amount: Long,
    val formattedAmount: String,
    val percentage: Float,
)

data class CategoryListUiState(
    val month: YearMonth = YearMonth.now(),
    val totalSpent: Long = 0,
    val totalSpentFormatted: String = "₹0",
    val categoryItems: List<CategoryListItem> = emptyList(),
)

class CategoryListViewModel(private val repository: TransactionRepository) : ViewModel() {
    private val month = YearMonth.now()

    val uiState: StateFlow<CategoryListUiState> = combine(
        repository.getTotalExpenses(month),
        repository.getCategoryTotals(month),
        repository.getActiveCategories(),
    ) { expenses, categoryTotals, categories ->
        val categoryMap = categories.associateBy(CategoryEntity::id)
        CategoryListUiState(
            month = month,
            totalSpent = expenses,
            totalSpentFormatted = formatPaiseAsRupees(expenses),
            categoryItems = categoryTotals.map { total ->
                val category = categoryMap[total.categoryId]
                CategoryListItem(
                    categoryId = total.categoryId,
                    name = category?.name ?: "Unknown",
                    color = category?.color,
                    amount = total.total,
                    formattedAmount = formatPaiseAsRupees(total.total),
                    percentage = if (expenses > 0) total.total.toFloat() / expenses.toFloat() else 0f,
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategoryListUiState())

    class Factory(private val repository: TransactionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CategoryListViewModel(repository) as T
    }
}
