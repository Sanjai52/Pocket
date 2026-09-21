package `in`.marxen.pocket.ui.settings

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.marxen.pocket.data.qr.SupportedUpiApps
import `in`.marxen.pocket.ui.theme.PocketBeige
import `in`.marxen.pocket.ui.theme.PocketGreen
import `in`.marxen.pocket.ui.theme.PocketText
import `in`.marxen.pocket.ui.theme.PocketTextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultPaymentAppScreen(
    onBack: () -> Unit,
    prefs: `in`.marxen.pocket.data.prefs.PocketPrefs,
) {
    val defaultPackage by prefs.defaultUpiPackage.collectAsState(initial = null)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().background(PocketBeige)) {
        TopAppBar(
            title = { Text("Default payment app", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AppOptionRow(
                appName = "Ask me every time",
                isSelected = defaultPackage == null,
                isInstalled = true,
                onClick = {
                    scope.launch {
                        prefs.setDefaultUpiPackage(null)
                    }
                },
            )

            SupportedUpiApps.all.forEach { app ->
                val isInstalled = remember(app.packageName) {
                    try {
                        context.packageManager.getPackageInfo(app.packageName, 0)
                        true
                    } catch (_: PackageManager.NameNotFoundException) {
                        false
                    }
                }

                AppOptionRow(
                    appName = app.displayName,
                    isSelected = defaultPackage == app.packageName,
                    isInstalled = isInstalled,
                    onClick = {
                        if (isInstalled) {
                            scope.launch {
                                prefs.setDefaultUpiPackage(app.packageName)
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AppOptionRow(
    appName: String,
    isSelected: Boolean,
    isInstalled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = isInstalled, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = isSelected,
            onClick = if (isInstalled) onClick else null,
            colors = RadioButtonDefaults.colors(selectedColor = PocketGreen),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = appName,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isInstalled) PocketText else PocketTextSecondary,
            )
            if (!isInstalled) {
                Text(
                    text = "Not installed",
                    style = MaterialTheme.typography.bodySmall,
                    color = PocketTextSecondary,
                )
            }
        }
    }
}
