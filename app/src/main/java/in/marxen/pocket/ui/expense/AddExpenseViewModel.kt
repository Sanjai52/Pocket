package `in`.marxen.pocket.ui.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import `in`.marxen.pocket.core.date.asiaKolkataToday
import `in`.marxen.pocket.core.money.rupeesToPaise
import `in`.marxen.pocket.data.local.entity.CategoryEntity
import `in`.marxen.pocket.data.local.entity.TransactionEntity
import `in`.marxen.pocket.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

data class AddExpenseUiState(
    val amount: String = "",
    val type: String = "EXPENSE",
    val selectedCategoryId: Long? = null,
    val date: LocalDate = asiaKolkataToday(),
    val note: String = "",
    val merchant: String = "",
    val isEditing: Boolean = false,
    val editingId: Long? = null,
    val amountError: String? = null,
    val categoryError: String? = null,
    val detailsExpanded: Boolean = false,
    val showAddCategoryDialog: Boolean = false,
    val customCategoryName: String = "",
)

class AddExpenseViewModel(private val repository: TransactionRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AddExpenseUiState())
    val uiState: StateFlow<AddExpenseUiState> = _uiState.asStateFlow()

    val categories: StateFlow<List<CategoryEntity>> = repository.getActiveCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _saved = MutableSharedFlow<Boolean>()
    val saved: SharedFlow<Boolean> = _saved.asSharedFlow()

    fun updateAmount(amount: String) {
        val filtered = amount.filter { it.isDigit() || it == '.' }
        val parts = filtered.split(".")
        val cleaned = if (parts.size > 2) parts[0] + "." + parts.drop(1).joinToString("") else filtered
        if (cleaned.length <= 12) {
            _uiState.update { it.copy(amount = cleaned, amountError = null) }
        }
    }

    fun updateType(type: String) {
        _uiState.update { it.copy(type = type) }
    }

    fun selectCategory(id: Long) {
        _uiState.update { it.copy(selectedCategoryId = id, categoryError = null) }
    }

    fun updateDate(date: LocalDate) {
        _uiState.update { it.copy(date = date) }
    }

    fun updateNote(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    fun updateMerchant(merchant: String) {
        _uiState.update { it.copy(merchant = merchant) }
    }

    fun toggleDetails() {
        _uiState.update { it.copy(detailsExpanded = !it.detailsExpanded) }
    }

    fun showAddCategoryDialog() {
        _uiState.update { it.copy(showAddCategoryDialog = true, customCategoryName = "") }
    }

    fun dismissAddCategoryDialog() {
        _uiState.update { it.copy(showAddCategoryDialog = false, customCategoryName = "") }
    }

    fun updateCustomCategoryName(name: String) {
        _uiState.update { it.copy(customCategoryName = name) }
    }

    fun addCustomCategory() {
        val name = _uiState.value.customCategoryName.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = repository.insertCategory(
                CategoryEntity(name = name)
            )
            _uiState.update {
                it.copy(
                    selectedCategoryId = id,
                    showAddCategoryDialog = false,
                    customCategoryName = "",
                    categoryError = null,
                )
            }
        }
    }

    fun loadTransaction(id: Long) {
        viewModelScope.launch {
            val txn = repository.getTransactionById(id) ?: return@launch
            _uiState.update {
                AddExpenseUiState(
                    amount = (txn.amountPaise / 100.0).toString(),
                    type = txn.type,
                    selectedCategoryId = txn.categoryId,
                    date = txn.transactionDate,
                    note = txn.note ?: "",
                    merchant = txn.merchant ?: "",
                    isEditing = true,
                    editingId = txn.id,
                )
            }
        }
    }

    fun save() {
        val state = _uiState.value
        var hasError = false

        if (state.amount.isBlank()) {
            _uiState.update { it.copy(amountError = "Amount required") }
            hasError = true
        } else {
            try {
                val paise = rupeesToPaise(state.amount)
                if (paise <= 0) {
                    _uiState.update { it.copy(amountError = "Amount must be greater than 0") }
                    hasError = true
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(amountError = "Invalid amount") }
                hasError = true
            }
        }

        if (state.selectedCategoryId == null) {
            _uiState.update { it.copy(categoryError = "Select a category") }
            hasError = true
        }

        if (hasError) return

        viewModelScope.launch {
            val paise = rupeesToPaise(state.amount)
            if (state.isEditing && state.editingId != null) {
                val existing = repository.getTransactionById(state.editingId) ?: return@launch
                repository.updateTransaction(
                    existing.copy(
                        type = state.type,
                        amountPaise = paise,
                        categoryId = state.selectedCategoryId!!,
                        transactionDate = state.date,
                        note = state.note.ifBlank { null },
                        merchant = state.merchant.ifBlank { null },
                        updatedAt = Instant.now(),
                    ),
                )
            } else {
                repository.insertTransaction(
                    TransactionEntity(
                        type = state.type,
                        amountPaise = paise,
                        categoryId = state.selectedCategoryId!!,
                        transactionDate = state.date,
                        note = state.note.ifBlank { null },
                        merchant = state.merchant.ifBlank { null },
                    ),
                )
            }
            _saved.emit(true)
        }
    }

    class Factory(private val repository: TransactionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AddExpenseViewModel(repository) as T
        }
    }
}
