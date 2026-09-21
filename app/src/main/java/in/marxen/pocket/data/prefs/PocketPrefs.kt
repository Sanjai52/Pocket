package `in`.marxen.pocket.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pocket_prefs")

class PocketPrefs(private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val LAST_SELECTED_CATEGORY = longPreferencesKey("last_selected_category")
        val FIRST_LAUNCH_COMPLETED = booleanPreferencesKey("first_launch_completed")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val HIDE_RECENT_PREVIEW = booleanPreferencesKey("hide_recent_preview")
        val USER_NAME = stringPreferencesKey("user_name")
        val DEFAULT_UPI_PACKAGE = stringPreferencesKey("default_upi_package")
    }

    val theme: Flow<String> = context.dataStore.data.map { it[Keys.THEME] ?: "light" }
    val lastSelectedCategory: Flow<Long> = context.dataStore.data.map { it[Keys.LAST_SELECTED_CATEGORY] ?: 0L }
    val firstLaunchCompleted: Flow<Boolean> = context.dataStore.data.map { it[Keys.FIRST_LAUNCH_COMPLETED] ?: false }
    val biometricEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.BIOMETRIC_ENABLED] ?: false }
    val hideRecentPreview: Flow<Boolean> = context.dataStore.data.map { it[Keys.HIDE_RECENT_PREVIEW] ?: false }
    val userName: Flow<String> = context.dataStore.data.map { it[Keys.USER_NAME] ?: "" }
    val defaultUpiPackage: Flow<String?> = context.dataStore.data.map { it[Keys.DEFAULT_UPI_PACKAGE] }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { it[Keys.THEME] = theme }
    }

    suspend fun setLastSelectedCategory(id: Long) {
        context.dataStore.edit { it[Keys.LAST_SELECTED_CATEGORY] = id }
    }

    suspend fun setFirstLaunchCompleted() {
        context.dataStore.edit { it[Keys.FIRST_LAUNCH_COMPLETED] = true }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setHideRecentPreview(hide: Boolean) {
        context.dataStore.edit { it[Keys.HIDE_RECENT_PREVIEW] = hide }
    }

    suspend fun setUserName(name: String) {
        context.dataStore.edit { it[Keys.USER_NAME] = name }
    }

    suspend fun setDefaultUpiPackage(packageName: String?) {
        context.dataStore.edit {
            if (packageName != null) it[Keys.DEFAULT_UPI_PACKAGE] = packageName
            else it.remove(Keys.DEFAULT_UPI_PACKAGE)
        }
    }
}
