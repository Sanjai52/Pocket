package `in`.marxen.pocket.ui.qr

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.marxen.pocket.core.money.formatPaiseAsRupees
import `in`.marxen.pocket.ui.theme.PocketGreen
import `in`.marxen.pocket.ui.theme.PocketText
import `in`.marxen.pocket.ui.theme.PocketTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuccessResultSheet(
    amountPaise: Long,
    payeeName: String?,
    onRecordExpense: () -> Unit,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "\u2705", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Payment completed",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = PocketText,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${formatPaiseAsRupees(amountPaise)} to ${payeeName ?: "Unknown"}",
                style = MaterialTheme.typography.bodyLarge,
                color = PocketTextSecondary,
            )
            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onRecordExpense,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PocketGreen),
            ) {
                Text("Record expense", fontWeight = FontWeight.Bold)
            }

            TextButton(onClick = onUndo) {
                Text("I didn't pay", color = PocketTextSecondary)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FailureResultSheet(
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "\u274C", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Payment failed",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = PocketText,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "No expense recorded. Nothing was charged.",
                style = MaterialTheme.typography.bodyMedium,
                color = PocketTextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(20.dp))

            TextButton(onClick = onDismiss) {
                Text("OK", color = PocketGreen)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnresolvedResultSheet(
    amountPaise: Long,
    payeeName: String?,
    onConfirmPaid: () -> Unit,
    onDeny: () -> Unit,
    onNotSure: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "\u2753", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Did this payment go through?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = PocketText,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${formatPaiseAsRupees(amountPaise)} to ${payeeName ?: "Unknown"}",
                style = MaterialTheme.typography.bodyLarge,
                color = PocketTextSecondary,
            )
            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onConfirmPaid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PocketGreen),
            ) {
                Text("Yes, record it", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onDeny,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD63031)),
            ) {
                Text("No, discard", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = onNotSure) {
                Text("Not sure", color = PocketTextSecondary)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun ConfirmRecordDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record expense?") },
        text = { Text("This will add the payment to your expense records. The payment will be marked as confirmed.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Record", color = PocketGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
