package `in`.marxen.pocket.ui.qr

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import `in`.marxen.pocket.data.local.entity.MerchantCategoryMemoryEntity
import `in`.marxen.pocket.data.local.entity.PaymentAttemptEntity
import `in`.marxen.pocket.data.local.entity.TransactionEntity
import `in`.marxen.pocket.data.prefs.PocketPrefs
import `in`.marxen.pocket.data.qr.LaunchPlan
import `in`.marxen.pocket.data.qr.ParseResult
import `in`.marxen.pocket.data.qr.PaymentProvider
import `in`.marxen.pocket.data.qr.ScannedUpiPayment
import `in`.marxen.pocket.data.qr.UpiQrParser
import `in`.marxen.pocket.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class PaymentSetupUiState(
    val payeeVpa: String = "",
    val payeeName: String? = null,
    val selectedCategory: String? = null,
    val categorySource: String? = null,
    val defaultUpiPackage: String? = null,
    val showAppOverlay: Boolean = false,
    val launchIntent: Intent? = null,
    val navigateToHome: Boolean = false,
    val attemptId: String? = null,
    val scannedPayment: ScannedUpiPayment? = null,
    val showAmountDialog: Boolean = false,
) {
    val isReadyToPay: Boolean
        get() = selectedCategory != null
}

class PaymentSetupViewModel(
    private val scannedPaymentUri: String,
    private val repository: TransactionRepository,
    private val prefs: PocketPrefs,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaymentSetupUiState())
    val uiState: StateFlow<PaymentSetupUiState> = _uiState.asStateFlow()

    init {
        val result = UpiQrParser.parse(scannedPaymentUri)
        if (result is ParseResult.Valid) {
            val payment = result.payment
            _uiState.update {
                it.copy(
                    payeeVpa = payment.payeeVpa,
                    payeeName = payment.payeeName,
                    scannedPayment = payment,
                )
            }
            viewModelScope.launch {
                val defaultPkg = prefs.defaultUpiPackage.first()
                _uiState.update { it.copy(defaultUpiPackage = defaultPkg) }

                val memCatId = repository.getMerchantCategoryId(payment.payeeVpaKey)
                if (memCatId != null) {
                    val cat = repository.getCategoryById(memCatId)
                    if (cat != null) {
                        _uiState.update { it.copy(selectedCategory = cat.name, categorySource = "memory") }
                    }
                }

                if (_uiState.value.selectedCategory == null) {
                    _uiState.update { it.copy(selectedCategory = "Other") }
                }
            }
        }
    }

    fun selectCategory(label: String) {
        _uiState.update { it.copy(selectedCategory = label) }
    }

    fun openAppOverlay() {
        _uiState.update { it.copy(showAppOverlay = true) }
    }

    fun dismissAppOverlay() {
        _uiState.update { it.copy(showAppOverlay = false) }
    }

    fun onAppSelected(packageName: String, setAsDefault: Boolean) {
        viewModelScope.launch {
            if (setAsDefault) {
                prefs.setDefaultUpiPackage(packageName)
                _uiState.update { it.copy(defaultUpiPackage = packageName, showAppOverlay = false) }
            } else {
                _uiState.update { it.copy(showAppOverlay = false) }
            }
            launchPayment(packageName)
        }
    }

    fun onPayClicked(context: Context) {
        val state = _uiState.value
        if (!state.isReadyToPay) return
        if (state.showAppOverlay) return

        val defaultPkg = state.defaultUpiPackage
        if (defaultPkg != null && isPackageInstalled(context, defaultPkg)) {
            viewModelScope.launch {
                launchPayment(defaultPkg)
            }
        } else {
            _uiState.update { it.copy(showAppOverlay = true) }
        }
    }

    fun dismissAmountDialog() {
        _uiState.update { it.copy(showAmountDialog = false) }
    }

    fun onAmountEntered(amountPaise: Long) {
        viewModelScope.launch {
            val state = _uiState.value
            val payment = state.scannedPayment ?: return@launch
            val attemptId = state.attemptId ?: UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            val categoryId = resolveCategoryId(state.selectedCategory)

            val attempt = PaymentAttemptEntity(
                id = attemptId,
                payeeVpa = payment.payeeVpa,
                payeeVpaKey = payment.payeeVpaKey,
                payeeName = payment.payeeName,
                merchantCategoryCode = payment.merchantCategoryCode,
                amountPaise = amountPaise,
                currency = "INR",
                categoryId = categoryId,
                localNote = null,
                launchPlan = LaunchPlan.REBUILT.name,
                sentTxnRef = null,
                qrKind = payment.kind.name,
                rawUpiUri = payment.rawUri.ifBlank { null },
                provider = PaymentProvider.DIRECT_APP.name,
                providerPackage = _uiState.value.defaultUpiPackage,
                status = "SUCCESS",
                resultHint = "MANUAL_AMOUNT",
                resultCode = null,
                appStatus = null,
                appTxnId = null,
                appApprovalRef = null,
                appResponseCode = null,
                recordedSource = "qr_scan",
                createdAt = now,
                launchedAt = now,
                resolvedAt = now,
                lastPromptedAt = null,
                updatedAt = now,
            )
            repository.insertPaymentAttempt(attempt)

            val transaction = TransactionEntity(
                type = "expense",
                amountPaise = amountPaise,
                categoryId = categoryId,
                merchant = payment.payeeName ?: payment.payeeVpa,
                note = null,
                paymentAttemptId = attemptId,
                transactionDate = LocalDate.now(),
                createdAt = Instant.ofEpochMilli(now),
                updatedAt = Instant.ofEpochMilli(now),
            )
            repository.insertTransaction(transaction)

            if (state.selectedCategory != null) {
                repository.upsertMerchantCategoryMemory(
                    MerchantCategoryMemoryEntity(
                        payeeVpaKey = payment.payeeVpaKey,
                        categoryId = categoryId,
                        updatedAt = now,
                    )
                )
            }

            _uiState.update {
                it.copy(
                    showAmountDialog = false,
                    navigateToHome = true,
                )
            }
        }
    }

    fun clearLaunchIntent() {
        _uiState.update { it.copy(launchIntent = null) }
    }

    fun onUpiResult(resultCode: Int, data: Intent?) {
        val attemptId = _uiState.value.attemptId ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()

            val extras = data?.extras
            val status = extras?.getString("Status")
            val txnRef = extras?.getString("txnRef")

            val appStatus = when {
                resultCode == android.app.Activity.RESULT_OK && status?.uppercase() == "SUCCESS" -> "SUCCESS"
                resultCode == android.app.Activity.RESULT_OK && status?.uppercase() == "FAILURE" -> "FAILURE"
                resultCode == android.app.Activity.RESULT_OK -> "RETURNED"
                resultCode == android.app.Activity.RESULT_CANCELED -> "CANCELED"
                else -> "UNKNOWN"
            }

            repository.updatePaymentAttemptStatus(attemptId, appStatus, now)

            _uiState.update { it.copy(showAmountDialog = true) }
        }
    }

    private suspend fun launchPayment(packageName: String) {
        val state = _uiState.value
        val payment = state.scannedPayment ?: return

        val attemptId = UUID.randomUUID().toString()
        val txnRef = "PKT-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(4).uppercase()}"
        val now = System.currentTimeMillis()

        val categoryId = resolveCategoryId(state.selectedCategory)

        val attempt = PaymentAttemptEntity(
            id = attemptId,
            payeeVpa = payment.payeeVpa,
            payeeVpaKey = payment.payeeVpaKey,
            payeeName = payment.payeeName,
            merchantCategoryCode = payment.merchantCategoryCode,
            amountPaise = 0L,
            currency = "INR",
            categoryId = categoryId,
            localNote = null,
            launchPlan = LaunchPlan.REBUILT.name,
            sentTxnRef = txnRef,
            qrKind = payment.kind.name,
            rawUpiUri = payment.rawUri.ifBlank { null },
            provider = PaymentProvider.DIRECT_APP.name,
            providerPackage = packageName,
            status = "LAUNCHED",
            resultHint = "NONE",
            resultCode = null,
            appStatus = null,
            appTxnId = null,
            appApprovalRef = null,
            appResponseCode = null,
            recordedSource = null,
            createdAt = now,
            launchedAt = now,
            resolvedAt = null,
            lastPromptedAt = null,
            updatedAt = now,
        )
        repository.insertPaymentAttempt(attempt)

        if (state.selectedCategory != null) {
            repository.upsertMerchantCategoryMemory(
                MerchantCategoryMemoryEntity(
                    payeeVpaKey = payment.payeeVpaKey,
                    categoryId = categoryId,
                    updatedAt = now,
                )
            )
        }

        val uriStr = buildString {
            append("upi://pay?")
            append("pa=").append(payment.payeeVpa)
            if (!payment.payeeName.isNullOrBlank()) {
                append("&pn=").append(payment.payeeName)
            }
            append("&am=1.00")
            append("&cu=INR")
            append("&mam=1")
            append("&tr=").append(txnRef)
        }

        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uriStr)).apply {
            setPackage(packageName)
        }

        _uiState.update { it.copy(attemptId = attemptId, launchIntent = intent) }
    }

    private suspend fun resolveCategoryId(categoryName: String?): Long {
        if (categoryName == null) return 1L
        val key = categoryName.lowercase()
        val cat = repository.getCategoryBySystemKeySync(key)
        return cat?.id ?: 1L
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    class Factory(
        private val scannedPaymentUri: String,
        private val repository: TransactionRepository,
        private val prefs: PocketPrefs,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PaymentSetupViewModel(scannedPaymentUri, repository, prefs) as T
        }
    }
}
