package `in`.marxen.pocket.ui.qr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import `in`.marxen.pocket.data.qr.ParseResult
import `in`.marxen.pocket.data.qr.UpiQrParser

data class QRScannerUiState(
    val isScanning: Boolean = true,
    val error: String? = null,
    val scannedPayment: ParseResult? = null,
)

class QRScannerViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(QRScannerUiState())
    val uiState: StateFlow<QRScannerUiState> = _uiState.asStateFlow()

    fun onBarcodeScanned(rawValue: String) {
        if (_uiState.value.scannedPayment != null) return

        val result = UpiQrParser.parse(rawValue)
        when (result) {
            is ParseResult.Valid -> {
                _uiState.update { it.copy(scannedPayment = result, isScanning = false) }
            }
            is ParseResult.Invalid -> {
                _uiState.update { it.copy(error = "Invalid QR: ${result.reason}") }
            }
            is ParseResult.NotUpi -> {
                _uiState.update { it.copy(error = "This isn't a UPI payment QR") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return QRScannerViewModel() as T
        }
    }
}
