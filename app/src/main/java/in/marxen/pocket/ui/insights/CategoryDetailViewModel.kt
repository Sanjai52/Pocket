package `in`.marxen.pocket.ui.insights

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import `in`.marxen.pocket.core.money.formatPaiseAsRupees
import `in`.marxen.pocket.data.repository.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

data class SubcategoryBreakdown(
    val name: String,
    val color: Color,
    val amount: Long,
    val formattedAmount: String,
    val percentage: Float,
)

data class CategoryDetailUiState(
    val categoryTotal: Long = 0,
    val categoryTotalFormatted: String = "₹0",
    val subcategoryBreakdown: List<SubcategoryBreakdown> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryDetailViewModel(
    private val repository: TransactionRepository,
    private val categoryId: Long,
    initialMonth: YearMonth,
) : ViewModel() {

    private val _selectedMonth = MutableStateFlow(initialMonth)

    private val subcategoryColors = listOf(
        Color(0xFFFF9800),
        Color(0xFF4CAF50),
        Color(0xFF2196F3),
        Color(0xFF9C27B0),
        Color(0xFFF44336),
        Color(0xFFE91E63),
        Color(0xFF00BCD4),
        Color(0xFFFF5722),
    )

    val uiState: StateFlow<CategoryDetailUiState> = _selectedMonth
        .flatMapLatest { month ->
            val categoryTotalFlow = repository.getCategoryTotal(categoryId, month)
            val subcategoryTotalsFlow = repository.getSubcategoryTotals(categoryId, month)
            val subcategoriesFlow = repository.getAllSubcategoriesByCategoryId(categoryId)

            kotlinx.coroutines.flow.combine(
                categoryTotalFlow,
                subcategoryTotalsFlow,
                subcategoriesFlow,
            ) { catTotal, subcategoryTotals, subcategories ->
                val subMap = subcategories.associateBy { it.id }

                val totalSubAmount = subcategoryTotals.sumOf { it.total }
                val unassigned = catTotal - totalSubAmount

                val breakdown = mutableListOf<SubcategoryBreakdown>()

                subcategoryTotals.forEachIndexed { index, st ->
                    val sub = subMap[st.subcategoryId]
                    breakdown.add(
                        SubcategoryBreakdown(
                            name = sub?.name ?: "Unknown",
                            color = subcategoryColors[index % subcategoryColors.size],
                            amount = st.total,
                            formattedAmount = formatPaiseAsRupees(st.total),
                            percentage = if (catTotal > 0) st.total.toFloat() / catTotal.toFloat() else 0f,
                        )
                    )
                }

                if (unassigned > 0) {
                    breakdown.add(
                        SubcategoryBreakdown(
                            name = "Uncategorized",
                            color = Color(0xFF607D8B),
                            amount = unassigned,
                            formattedAmount = formatPaiseAsRupees(unassigned),
                            percentage = if (catTotal > 0) unassigned.toFloat() / catTotal.toFloat() else 0f,
                        )
                    )
                }

                CategoryDetailUiState(
                    categoryTotal = catTotal,
                    categoryTotalFormatted = formatPaiseAsRupees(catTotal),
                    subcategoryBreakdown = breakdown,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategoryDetailUiState())

    class Factory(
        private val repository: TransactionRepository,
        private val categoryId: Long,
        private val initialMonth: YearMonth,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CategoryDetailViewModel(repository, categoryId, initialMonth) as T
        }
    }
}
