package `in`.marxen.pocket.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.marxen.pocket.R
import `in`.marxen.pocket.ui.home.TransactionUi
import `in`.marxen.pocket.ui.theme.PocketBeige
import `in`.marxen.pocket.ui.theme.PocketGreen
import `in`.marxen.pocket.ui.theme.PocketText
import `in`.marxen.pocket.ui.theme.PocketTextSecondary
import java.time.format.TextStyle
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
fun CalendarScreen(
    onTransactionClick: (Long) -> Unit = {},
    onAddExpense: () -> Unit = {},
    viewModel: CalendarViewModel = viewModel(
        factory = CalendarViewModel.Factory(
            (LocalContext.current.applicationContext as `in`.marxen.pocket.PocketApplication).container.repository,
        ),
    ),
) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PocketBeige)
            .padding(horizontal = 16.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { viewModel.previousMonth() }) {
                    Icon(
                        Icons.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous month",
                        tint = PocketText,
                    )
                }
                Text(
                    text = state.month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + state.month.year,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = PocketText,
                )
                IconButton(onClick = { viewModel.nextMonth() }) {
                    Icon(
                        Icons.Filled.KeyboardArrowRight,
                        contentDescription = "Next month",
                        tint = PocketText,
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { day ->
                    Text(
                        text = day,
                        style = MaterialTheme.typography.labelSmall,
                        color = PocketTextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.height(300.dp),
                userScrollEnabled = false,
            ) {
                items(state.days) { day ->
                    DayCell(
                        day = day,
                        onClick = { viewModel.selectDate(day.date) },
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${state.selectedDate.dayOfMonth} ${state.selectedDate.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${state.selectedDate.year}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PocketText,
                )
                Icon(
                    Icons.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = PocketTextSecondary,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${state.selectedDayTotal} spent",
                style = MaterialTheme.typography.bodyMedium,
                color = PocketTextSecondary,
            )
        }

        if (state.selectedDayTransactions.isEmpty()) {
            item {
                Text(
                    text = "No transactions on this day",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PocketTextSecondary,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        } else {
            items(state.selectedDayTransactions, key = { it.entity.id }) { txn ->
                TransactionRow(txn = txn, onClick = { onTransactionClick(txn.entity.id) })
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
}

@Composable
private fun DayCell(day: DaySummary, onClick: () -> Unit) {
    val isSelected = day.isSelected
    val isToday = day.isToday

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .then(
                if (isSelected) {
                    Modifier
                        .clip(CircleShape)
                        .background(PocketGreen)
                } else if (isToday) {
                    Modifier
                        .clip(CircleShape)
                        .border(1.5.dp, PocketGreen, CircleShape)
                } else {
                    Modifier
                }
            )
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    isSelected -> Color.White
                    !day.isCurrentMonth -> PocketTextSecondary.copy(alpha = 0.4f)
                    else -> PocketText
                },
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
            )
            if (day.hasTransactions && day.isCurrentMonth) {
                val dotColor = if (isSelected) {
                    Color.White
                } else {
                    day.dotColor?.let { Color(it.toULong()) } ?: PocketGreen
                }
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
            }
        }
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
