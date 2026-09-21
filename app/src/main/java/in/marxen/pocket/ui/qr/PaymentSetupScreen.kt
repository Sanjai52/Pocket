package `in`.marxen.pocket.ui.qr

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.marxen.pocket.ui.theme.PocketBeige
import `in`.marxen.pocket.ui.theme.PocketGreen
import `in`.marxen.pocket.ui.theme.PocketText
import `in`.marxen.pocket.ui.theme.PocketTextSecondary

@Composable
fun PaymentSetupScreen(
    scannedPaymentUri: String,
    onBack: () -> Unit,
    onPaymentLaunched: () -> Unit,
    appContainer: `in`.marxen.pocket.data.local.AppContainer,
    viewModel: PaymentSetupViewModel = viewModel(
        factory = PaymentSetupViewModel.Factory(scannedPaymentUri, appContainer.repository, appContainer.prefs),
    ),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val upiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.onUpiResult(result.resultCode, result.data)
    }

    LaunchedEffect(uiState.launchIntent) {
        uiState.launchIntent?.let { intent ->
            try {
                upiLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "No UPI app found", Toast.LENGTH_SHORT).show()
            }
            viewModel.clearLaunchIntent()
        }
    }

    LaunchedEffect(uiState.navigateToHome) {
        if (uiState.navigateToHome) {
            onPaymentLaunched()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PocketBeige),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Pay",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = PocketText,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MerchantInfoCard(uiState)
            CategorySection(uiState, viewModel)
        }

        BottomCTA(uiState, viewModel)
    }

    if (uiState.showAppOverlay) {
        PaymentAppOverlay(
            amountPaise = 0L,
            payeeName = uiState.payeeName,
            onAppSelected = { pkg, setDefault -> viewModel.onAppSelected(pkg, setDefault) },
            onDismiss = { viewModel.dismissAppOverlay() },
        )
    }

    if (uiState.showAmountDialog) {
        PostPaymentAmountDialog(
            payeeName = uiState.payeeName,
            onAmountConfirmed = { amountPaise ->
                viewModel.onAmountEntered(amountPaise)
            },
            onDismiss = { viewModel.dismissAmountDialog() },
        )
    }
}

@Composable
private fun PostPaymentAmountDialog(
    payeeName: String?,
    onAmountConfirmed: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var amountInput by remember { mutableStateOf("") }
    var amountError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "Payment to ${payeeName ?: "Unknown"}",
                fontWeight = FontWeight.Bold,
                color = PocketText,
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter the amount you paid",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PocketTextSecondary,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { raw ->
                        val cleaned = raw.replace(",", "").replace("\u20B9", "")
                        amountInput = cleaned
                        amountError = null
                    },
                    label = { Text("Amount") },
                    prefix = { Text("\u20B9") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = amountError != null,
                    supportingText = amountError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val paise = try {
                        val bd = java.math.BigDecimal(amountInput)
                        if (bd.scale() > 2) {
                            amountError = "At most 2 decimal places"
                            return@TextButton
                        }
                        bd.movePointRight(2).longValueExact()
                    } catch (e: Exception) {
                        amountError = "Invalid amount"
                        return@TextButton
                    }
                    if (paise <= 0) {
                        amountError = "Amount must be greater than 0"
                        return@TextButton
                    }
                    onAmountConfirmed(paise)
                },
            ) {
                Text("Confirm", fontWeight = FontWeight.Bold, color = PocketGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = PocketTextSecondary)
            }
        },
    )
}

@Composable
private fun MerchantInfoCard(uiState: PaymentSetupUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(PocketGreen.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (uiState.payeeName ?: "U").first().toString().uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = PocketGreen,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Paying to",
            style = MaterialTheme.typography.bodyMedium,
            color = PocketTextSecondary,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = uiState.payeeName ?: "Unknown",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = PocketText,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = uiState.payeeVpa,
            style = MaterialTheme.typography.bodyMedium,
            color = PocketTextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CategorySection(uiState: PaymentSetupUiState, viewModel: PaymentSetupViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(20.dp),
    ) {
        Text(
            text = "Category",
            style = MaterialTheme.typography.bodyMedium,
            color = PocketTextSecondary,
        )
        Spacer(modifier = Modifier.height(12.dp))

        val categories = listOf(
            "Food" to "\uD83C\uDF5C", "Groceries" to "\uD83E\uDD66", "Transport" to "\uD83D\uDE97",
            "Shopping" to "\uD83D\uDECD\uFE0F", "Bills" to "\uD83D\uDCCB", "Other" to "\uD83D\uDCCC",
        )

        val rows = categories.chunked(3)
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { (label, emoji) ->
                    val isSelected = uiState.selectedCategory == label

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) PocketGreen.copy(alpha = 0.1f) else PocketBeige)
                            .clickable { viewModel.selectCategory(label) }
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = emoji, fontSize = 24.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isSelected) PocketGreen else PocketText,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun BottomCTA(uiState: PaymentSetupUiState, viewModel: PaymentSetupViewModel) {
    val context = LocalContext.current
    val defaultPackage = uiState.defaultUpiPackage

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = { viewModel.onPayClicked(context) },
            enabled = uiState.isReadyToPay,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PocketGreen),
        ) {
            Text(
                text = "Pay with UPI",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }

        if (defaultPackage != null) {
            val appName = when (defaultPackage) {
                "com.google.android.apps.nbu.paisa.user" -> "Google Pay"
                "net.one97.paytm" -> "Paytm"
                "com.phonepe.app" -> "PhonePe"
                else -> defaultPackage
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Text(
                    text = "Opening $appName \u00B7 ",
                    style = MaterialTheme.typography.bodySmall,
                    color = PocketTextSecondary,
                )
                Text(
                    text = "Change",
                    style = MaterialTheme.typography.bodySmall,
                    color = PocketGreen,
                    modifier = Modifier.clickable { viewModel.openAppOverlay() },
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Enter amount after payment",
            style = MaterialTheme.typography.bodySmall,
            color = PocketTextSecondary,
        )
    }
}
