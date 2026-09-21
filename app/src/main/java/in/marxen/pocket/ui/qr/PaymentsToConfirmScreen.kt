package `in`.marxen.pocket.ui.qr

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.marxen.pocket.core.money.formatPaiseAsRupees
import `in`.marxen.pocket.ui.theme.PocketBeige
import `in`.marxen.pocket.ui.theme.PocketGreen
import `in`.marxen.pocket.ui.theme.PocketText
import `in`.marxen.pocket.ui.theme.PocketTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PaymentsToConfirmScreen(
    onBack: () -> Unit,
    viewModel: PaymentsToConfirmViewModel,
) {
    val unresolved by viewModel.unresolvedAttempts.collectAsState(initial = emptyList())
    var attemptToConfirm by remember { mutableStateOf<`in`.marxen.pocket.data.local.entity.PaymentAttemptEntity?>(null) }

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
                text = "Payments to Confirm",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = PocketText,
            )
        }

        if (unresolved.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No pending payments",
                    style = MaterialTheme.typography.bodyLarge,
                    color = PocketTextSecondary,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(unresolved) { attempt ->
                    UnresolvedAttemptRow(
                        payeeName = attempt.payeeName ?: attempt.payeeVpa,
                        amountPaise = attempt.amountPaise,
                        createdAt = attempt.createdAt,
                        onClick = { attemptToConfirm = attempt },
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    attemptToConfirm?.let { attempt ->
        UnresolvedResultSheet(
            amountPaise = attempt.amountPaise,
            payeeName = attempt.payeeName,
            onConfirmPaid = {
                viewModel.confirmPaid(attempt.id)
                attemptToConfirm = null
            },
            onDeny = {
                viewModel.discardAttempt(attempt.id)
                attemptToConfirm = null
            },
            onNotSure = {
                attemptToConfirm = null
            },
            onDismiss = { attemptToConfirm = null },
        )
    }
}

@Composable
private fun UnresolvedAttemptRow(
    payeeName: String,
    amountPaise: Long,
    createdAt: Long,
    onClick: () -> Unit,
) {
    val timeFormat = remember { SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = payeeName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = PocketText,
            )
            Text(
                text = timeFormat.format(Date(createdAt)),
                style = MaterialTheme.typography.bodySmall,
                color = PocketTextSecondary,
            )
        }
        Text(
            text = formatPaiseAsRupees(amountPaise),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = PocketText,
        )
    }
}
