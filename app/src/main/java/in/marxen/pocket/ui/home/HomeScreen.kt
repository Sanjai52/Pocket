package `in`.marxen.pocket.ui.home

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.marxen.pocket.R
import `in`.marxen.pocket.core.date.asiaKolkataToday
import `in`.marxen.pocket.ui.theme.PocketGreen
import `in`.marxen.pocket.ui.theme.PocketBeige
import `in`.marxen.pocket.ui.theme.PocketText
import `in`.marxen.pocket.ui.theme.PocketTextSecondary
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val categoryIcons = mapOf(
    "food" to R.drawable.ic_food,
    "groceries" to R.drawable.ic_groceries,
    "transport" to R.drawable.ic_transport,
    "shopping" to R.drawable.ic_shopping,
    "bills" to R.drawable.ic_bills,
    "entertainment" to R.drawable.ic_entertainment,
    "health" to R.drawable.ic_health,
    "education" to R.drawable.ic_education,
    "other" to R.drawable.ic_other,
)

@Composable
fun HomeScreen(
    onTransactionClick: (Long) -> Unit = {},
    onAddExpense: () -> Unit = {},
    onSeeAll: () -> Unit = {},
    onMonthClick: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(
            (LocalContext.current.applicationContext as `in`.marxen.pocket.PocketApplication).container.repository,
            (LocalContext.current.applicationContext as `in`.marxen.pocket.PocketApplication).container.prefs,
        ),
    ),
) {
    val state by viewModel.uiState.collectAsState()
    val today = asiaKolkataToday()
    var showMonthPicker by remember { mutableIntStateOf(0) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PocketBeige)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Good ${timeOfDay()}, \uD83D\uDC4B",
                    style = MaterialTheme.typography.bodyLarge,
                    color = PocketText,
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(PocketGreen),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "S",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
        }

        item {
                Text(
                    text = state.userName.ifBlank { "User" }.split(" ").joinToString(" ") { word ->
                        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = PocketText,
            )
        }

        item {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showMonthPicker = 1 }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${state.month.month.name} ${state.month.year}",
                    style = MaterialTheme.typography.titleMedium,
                    color = PocketText,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "\u25BE",
                    style = MaterialTheme.typography.titleMedium,
                    color = PocketText,
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PocketGreen),
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = state.totalExpensesFormatted,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            text = "spent this month",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(
                                    text = state.totalIncomeFormatted,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                )
                                Text(
                                    text = "Income",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }
                    Text(
                        text = "\uD83C\uDF3F",
                        style = MaterialTheme.typography.displayLarge,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 16.dp),
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Today \u00B7 ${today.dayOfMonth} ${today.month.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PocketText,
                )
                Text(
                    text = "See all",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PocketGreen,
                    modifier = Modifier.clickable { onSeeAll() },
                )
            }
        }

        if (state.todayTransactions.isEmpty()) {
            item {
                Text(
                    text = "No transactions today",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PocketTextSecondary,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        } else {
            items(state.todayTransactions, key = { it.entity.id }) { txn ->
                TransactionRow(
                    txn = txn,
                    onClick = { onTransactionClick(txn.entity.id) },
                )
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+ Add Expense",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = PocketGreen,
                    modifier = Modifier.clickable { onAddExpense() },
                )
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }

    if (showMonthPicker > 0) {
        MonthPickerDialog(
            currentMonth = state.month,
            onMonthSelected = { selectedMonth ->
                viewModel.selectMonth(selectedMonth)
                showMonthPicker = 0
            },
            onDismiss = { showMonthPicker = 0 },
        )
    }
}

@Composable
private fun TransactionRow(txn: TransactionUi, onClick: () -> Unit) {
    val catColor = txn.categoryColor?.let { Color(it.toULong()) } ?: PocketGreen
    val iconRes = categoryIcons[txn.categoryIcon?.lowercase()] ?: categoryIcons[txn.categoryName.lowercase()]

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(catColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = txn.categoryName,
                    tint = catColor,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Text(
                    text = txn.categoryName.first().toString(),
                    style = MaterialTheme.typography.titleSmall,
                    color = catColor,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = txn.categoryName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = PocketText,
            )
            if (txn.entity.merchant != null) {
                Text(
                    text = txn.entity.merchant,
                    style = MaterialTheme.typography.bodySmall,
                    color = PocketTextSecondary,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = txn.formattedAmount,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = PocketText,
            )
            Text(
                text = txn.entity.note ?: "-",
                style = MaterialTheme.typography.bodySmall,
                color = PocketTextSecondary,
            )
        }
    }
}

private fun timeOfDay(): String {
    val hour = java.time.LocalTime.now(java.time.ZoneId.of("Asia/Kolkata")).hour
    return when {
        hour < 12 -> "morning"
        hour < 17 -> "afternoon"
        hour < 21 -> "evening"
        else -> "night"
    }
}

@Composable
private fun MonthPickerDialog(
    currentMonth: YearMonth,
    onMonthSelected: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
) {
    val months = remember {
        val now = YearMonth.now()
        (0L..11L).map { now.minusMonths(it) }
    }
    val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Select Month", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn {
                items(months) { month ->
                    Text(
                        text = month.format(formatter),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onMonthSelected(month) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        color = if (month == currentMonth) PocketGreen else PocketText,
                        fontWeight = if (month == currentMonth) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Text(
                text = "Cancel",
                modifier = Modifier.clickable(onClick = onDismiss),
                color = PocketGreen,
            )
        },
    )
}
