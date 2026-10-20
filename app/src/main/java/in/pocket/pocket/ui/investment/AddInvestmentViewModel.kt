package `in`.marxen.pocket.ui.investment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import `in`.marxen.pocket.core.date.asiaKolkataToday
import `in`.marxen.pocket.core.money.paiseToRupees
import `in`.marxen.pocket.core.money.rupeesToPaise
import `in`.marxen.pocket.data.local.entity.InvestmentEntryEntity
import `in`.marxen.pocket.data.local.entity.InvestmentTypeEntity
import `in`.marxen.pocket.data.repository.InvestmentRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

data class AddInvestmentUiState(
    val amount: String = "",
    val kind: String = "SAVING",            // "SAVING" | "INVESTMENT"
    val direction: String = "IN",           // savings only: "IN" | "OUT"
    val selectedTypeId: Long? = null,
    val date: LocalDate = asiaKolkataToday(),
    val note: String = "",
    val isEditing: Boolean = false,
    val editingId: Long? = null,
    val amountError: String? = null,
    val typeError: String? = null,
    val detailsExpanded: Boolean = false,
    val showAddTypeDialog: Boolean = false,
    val customTypeName: String = "",
)

class AddInvestmentViewModel(private val repository: InvestmentRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AddInvestmentUiState())
    val uiState: StateFlow<AddInvestmentUiState> = _uiState.asStateFlow()

    val types: StateFlow<List<InvestmentTypeEntity>> = repository.getTypes()
        .map { it.withOtherLast() }
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

    fun updateKind(kind: String) {
        if (_uiState.value.kind == kind) return
        _uiState.update { it.copy(kind = kind, selectedTypeId = null, typeError = null) }
    }

    fun selectDirection(direction: String) {
        _uiState.update { it.copy(direction = direction) }
    }

    fun selectType(id: Long) {
        _uiState.update { it.copy(selectedTypeId = id, typeError = null) }
    }

    fun updateDate(date: LocalDate) {
        _uiState.update { it.copy(date = date) }
    }

    fun updateNote(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    fun toggleDetails() {
        _uiState.update { it.copy(detailsExpanded = !it.detailsExpanded) }
    }

    fun showAddTypeDialog() {
        _uiState.update { it.copy(showAddTypeDialog = true, customTypeName = "") }
    }

    fun dismissAddTypeDialog() {
        _uiState.update { it.copy(showAddTypeDialog = false, customTypeName = "") }
    }

    fun updateCustomTypeName(name: String) {
        _uiState.update { it.copy(customTypeName = name) }
    }

    fun addCustomType() {
        val name = _uiState.value.customTypeName.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = repository.addCustomType(name)
            _uiState.update {
                it.copy(
                    selectedTypeId = id,
                    showAddTypeDialog = false,
                    customTypeName = "",
                    typeError = null,
                )
            }
        }
    }

    fun loadEntry(id: Long) {
        viewModelScope.launch {
            val entry = repository.getEntry(id) ?: return@launch
            _uiState.update {
                AddInvestmentUiState(
                    amount = paiseToRupees(entry.amountPaise),
                    kind = entry.kind,
                    direction = entry.direction,
                    selectedTypeId = entry.typeId,
                    date = entry.entryDate,
                    note = entry.note ?: "",
                    isEditing = true,
                    editingId = entry.id,
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

        if (state.kind == "INVESTMENT" && state.selectedTypeId == null) {
            _uiState.update { it.copy(typeError = "Select a type") }
            hasError = true
        }

        if (hasError) return

        viewModelScope.launch {
            val paise = rupeesToPaise(state.amount)
            if (state.isEditing && state.editingId != null) {
                val existing = repository.getEntry(state.editingId) ?: return@launch
                repository.updateEntry(
                    existing.copy(
                        kind = state.kind,
                        direction = if (state.kind == "SAVING") state.direction else "IN",
                        typeId = if (state.kind == "INVESTMENT") state.selectedTypeId else null,
                        amountPaise = paise,
                        entryDate = state.date,
                        note = state.note.ifBlank { null },
                        updatedAt = Instant.now(),
                    ),
                )
            } else {
                repository.insertEntry(
                    InvestmentEntryEntity(
                        kind = state.kind,
                        direction = if (state.kind == "SAVING") state.direction else "IN",
                        typeId = if (state.kind == "INVESTMENT") state.selectedTypeId else null,
                        amountPaise = paise,
                        entryDate = state.date,
                        note = state.note.ifBlank { null },
                    ),
                )
            }
            _saved.emit(true)
        }
    }

    class Factory(private val repository: InvestmentRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AddInvestmentViewModel(repository) as T
        }
    }
}
