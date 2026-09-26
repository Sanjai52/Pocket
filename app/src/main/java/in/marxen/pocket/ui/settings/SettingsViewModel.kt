package `in`.marxen.pocket.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import `in`.marxen.pocket.data.backup.BackupManager
import `in`.marxen.pocket.data.local.PocketDatabase
import `in`.marxen.pocket.data.prefs.PocketPrefs
import `in`.marxen.pocket.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val prefs: PocketPrefs,
    private val repository: TransactionRepository,
    private val database: PocketDatabase,
    private val context: Context,
) : ViewModel() {

    private val backupManager = BackupManager()

    private val _backupMessage = MutableStateFlow<String?>(null)
    val backupMessage: StateFlow<String?> = _backupMessage.asStateFlow()

    val theme: StateFlow<String> = prefs.theme.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        "system",
    )

    val biometricEnabled: StateFlow<Boolean> = prefs.biometricEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false,
    )

    val hideRecentPreview: StateFlow<Boolean> = prefs.hideRecentPreview.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false,
    )

    fun setTheme(theme: String) {
        viewModelScope.launch { prefs.setTheme(theme) }
    }

    fun toggleBiometric(enabled: Boolean) {
        viewModelScope.launch { prefs.setBiometricEnabled(enabled) }
    }

    fun toggleHideRecentPreview(hide: Boolean) {
        viewModelScope.launch { prefs.setHideRecentPreview(hide) }
    }

    fun deleteAllData(onComplete: () -> Unit) {
        viewModelScope.launch {
            database.clearAllTables()
            onComplete()
        }
    }

    fun createBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = backupManager.createBackup(database)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray())
                }
                _backupMessage.value = "Backup created successfully"
            } catch (e: Exception) {
                _backupMessage.value = "Backup failed: ${e.message}"
            }
        }
    }

    fun restoreBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { inp ->
                    inp.readBytes().decodeToString()
                } ?: run {
                    _backupMessage.value = "Failed to read backup file"
                    return@launch
                }
                backupManager.restoreBackup(database, json)
                _backupMessage.value = "Restore completed successfully"
            } catch (e: Exception) {
                _backupMessage.value = "Restore failed: ${e.message}"
            }
        }
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            try {
                val transactions = database.transactionDao().getAll()
                val categoryNames = database.categoryDao().getAllSync().associate { it.id to it.name }
                val subcategoryNames = database.subcategoryDao().getAll().associate { it.id to it.name }
                val paymentMethodNames = database.paymentMethodDao().getAll().associate { it.id to it.name }
                val csv = backupManager.exportCsv(transactions, categoryNames, subcategoryNames, paymentMethodNames)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(csv.toByteArray())
                }
                _backupMessage.value = "CSV exported successfully"
            } catch (e: Exception) {
                _backupMessage.value = "Export failed: ${e.message}"
            }
        }
    }

    fun clearMessage() {
        _backupMessage.value = null
    }

    class Factory(
        private val prefs: PocketPrefs,
        private val repository: TransactionRepository,
        private val database: PocketDatabase,
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(prefs, repository, database, context) as T
        }
    }
}
