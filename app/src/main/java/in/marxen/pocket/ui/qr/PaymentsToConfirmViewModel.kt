package `in`.marxen.pocket.ui.qr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import `in`.marxen.pocket.data.local.entity.PaymentAttemptEntity
import `in`.marxen.pocket.data.repository.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PaymentsToConfirmViewModel(
    private val repository: TransactionRepository,
) : ViewModel() {

    val unresolvedAttempts = repository.getUnresolvedAttempts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun confirmPaid(attemptId: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val attempt = repository.getPaymentAttemptById(attemptId) ?: return@launch

            repository.updatePaymentAttemptStatus(attemptId, "RECORDED", now)

            val today = LocalDate.now(ZoneId.of("Asia/Kolkata"))
            repository.insertTransaction(
                `in`.marxen.pocket.data.local.entity.TransactionEntity(
                    type = "EXPENSE",
                    amountPaise = attempt.amountPaise,
                    categoryId = attempt.categoryId,
                    transactionDate = today,
                    merchant = attempt.payeeName ?: attempt.payeeVpa,
                    note = attempt.localNote,
                    paymentAttemptId = attemptId,
                )
            )
        }
    }

    fun discardAttempt(attemptId: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.updatePaymentAttemptStatus(attemptId, "DISCARDED", now)
        }
    }

    class Factory(private val repository: TransactionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PaymentsToConfirmViewModel(repository) as T
        }
    }
}
