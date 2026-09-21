package `in`.marxen.pocket.ui.qr

import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.marxen.pocket.core.money.formatPaiseAsRupees
import `in`.marxen.pocket.data.qr.SupportedUpiApps
import `in`.marxen.pocket.ui.theme.PocketGreen
import `in`.marxen.pocket.ui.theme.PocketText
import `in`.marxen.pocket.ui.theme.PocketTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentAppOverlay(
    amountPaise: Long,
    payeeName: String?,
    onAppSelected: (packageName: String, setAsDefault: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current

    var setAsDefault = remember { androidx.compose.runtime.mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(PocketTextSecondary.copy(alpha = 0.3f)),
                )
            }

            Text(
                text = "Pay ${formatPaiseAsRupees(amountPaise)} to ${payeeName ?: "Unknown"}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PocketText,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "You'll complete the payment in your UPI app.",
                style = MaterialTheme.typography.bodySmall,
                color = PocketTextSecondary,
            )

            Spacer(modifier = Modifier.height(20.dp))

            SupportedUpiApps.all.forEach { app ->
                val isInstalled = remember(app.packageName) {
                    try {
                        context.packageManager.getPackageInfo(app.packageName, 0)
                        true
                    } catch (_: PackageManager.NameNotFoundException) {
                        false
                    }
                }

                UpiAppRow(
                    appName = app.displayName,
                    packageName = app.packageName,
                    isInstalled = isInstalled,
                    isBlocked = app.blocked,
                    onClick = {
                        if (isInstalled && !app.blocked) {
                            onAppSelected(app.packageName, setAsDefault.value)
                        }
                    },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = setAsDefault.value,
                    onCheckedChange = { setAsDefault.value = it },
                    colors = CheckboxDefaults.colors(checkedColor = PocketGreen),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Set as default payment app",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PocketText,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Other UPI app",
                style = MaterialTheme.typography.bodySmall,
                color = PocketGreen,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("upi://pay"),
                        )
                        context.startActivity(android.content.Intent.createChooser(intent, "Pay with UPI app"))
                    }
                    .padding(vertical = 8.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun UpiAppRow(
    appName: String,
    packageName: String,
    isInstalled: Boolean,
    isBlocked: Boolean,
    onClick: () -> Unit,
) {
    val isAvailable = isInstalled && !isBlocked

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = isAvailable, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PocketTextSecondary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = appName.first().toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PocketGreen,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = appName,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isAvailable) PocketText else PocketTextSecondary,
            )
            if (!isInstalled) {
                Text(
                    text = "Not installed",
                    style = MaterialTheme.typography.bodySmall,
                    color = PocketTextSecondary,
                )
            } else if (isBlocked) {
                Text(
                    text = "Can't be used with Pocket right now",
                    style = MaterialTheme.typography.bodySmall,
                    color = PocketTextSecondary,
                )
            }
        }
    }
}
