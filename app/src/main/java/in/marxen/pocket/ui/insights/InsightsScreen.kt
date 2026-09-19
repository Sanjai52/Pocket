package `in`.marxen.pocket.ui.insights

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.marxen.pocket.ui.theme.CatBills
import `in`.marxen.pocket.ui.theme.CatFood
import `in`.marxen.pocket.ui.theme.CatOther
import `in`.marxen.pocket.ui.theme.CatShopping
import `in`.marxen.pocket.ui.theme.CatTransport
import `in`.marxen.pocket.ui.theme.PocketBeige
import `in`.marxen.pocket.ui.theme.PocketGreen
import `in`.marxen.pocket.ui.theme.PocketText
import `in`.marxen.pocket.ui.theme.PocketTextSecondary
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val donutColors = listOf(CatFood, CatBills, CatShopping, CatTransport, CatOther)

@Composable
fun InsightsScreen(
    viewModel: InsightsViewModel = viewModel(
        factory = InsightsViewModel.Factory(
            (LocalContext.current.applicationContext as `in`.marxen.pocket.PocketApplication).container.repository,
        ),
    ),
) {
    val state by viewModel.uiState.collectAsState()
    val tabs = listOf("Spending", "Income", "Trends")
    var showMonthPicker by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(PocketBeige)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Insights",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = PocketText,
                )
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .clickable { showMonthPicker = 1 },
                ) {
                    Text(
                        text = state.month.month.name + " " + state.month.year,
                        style = MaterialTheme.typography.bodyLarge,
                        color = PocketText,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "\u25BE",
                        style = MaterialTheme.typography.bodyLarge,
                        color = PocketText,
                    )
                }
            }

            item {
                TabRow(selectedTabIndex = state.selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = state.selectedTab == index,
                            onClick = { viewModel.selectTab(index) },
                            text = {
                                Text(
                                    text = title,
                                    color = if (state.selectedTab == index) PocketGreen else PocketTextSecondary,
                                )
                            },
                        )
                    }
                }
            }

            item {
                when (state.selectedTab) {
                    0 -> SpendingTab(
                        state = state,
                        onSeeAllClick = { viewModel.selectTab(2) },
                    )
                    1 -> IncomeTab(state)
                    2 -> TrendsTab(state)
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

@Composable
private fun SpendingTab(
    state: InsightsUiState,
    onSeeAllClick: () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = PocketGreen),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Total Spending",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.totalSpentFormatted,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Text(
                    text = "\uD83C\uDF3F",
                    modifier = Modifier.size(48.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.displaySmall,
                )
            }
        }

        if (state.categoryBreakdown.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "By Category",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PocketText,
                )
                Text(
                    text = "See all",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PocketGreen,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DonutChart(
                    categoryBreakdown = state.categoryBreakdown,
                    totalFormatted = state.totalSpentFormatted,
                    modifier = Modifier.size(180.dp),
                )

                Column(
                    modifier = Modifier.padding(start = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.categoryBreakdown.take(5).forEachIndexed { index, category ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(donutColors[index % donutColors.size]),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${category.name} ${(category.percentage * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = PocketText,
                            )
                        }
                    }
                }
            }
        }

        if (state.monthlyTrend.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Monthly Trend",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PocketText,
                )
                Text(
                    text = "See all",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PocketGreen,
                    modifier = Modifier.clickable { onSeeAllClick() },
                )
            }

            MonthlyTrendChart(
                monthlyTrend = state.monthlyTrend,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "💡",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Insight",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = PocketGreen,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "You spent 34% more on food during weekends.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PocketText,
                    )
                }
            }
        }
    }
}

@Composable
private fun DonutChart(
    categoryBreakdown: List<CategoryBreakdown>,
    totalFormatted: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 32.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2
            val center = Offset(size.width / 2, size.height / 2)

            var startAngle = -90f
            categoryBreakdown.take(5).forEachIndexed { index, category ->
                val sweepAngle = category.percentage * 360f
                drawArc(
                    color = donutColors[index % donutColors.size],
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
                startAngle += sweepAngle
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = totalFormatted,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PocketText,
            )
            Text(
                text = "Total",
                style = MaterialTheme.typography.bodySmall,
                color = PocketTextSecondary,
            )
        }
    }
}

@Composable
private fun MonthlyTrendChart(
    monthlyTrend: List<MonthTotal>,
    modifier: Modifier = Modifier,
) {
    if (monthlyTrend.isEmpty()) return

    val maxValue = monthlyTrend.maxOfOrNull { it.total } ?: 1L
    var selectedBar by remember { mutableIntStateOf(-1) }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (selectedBar in monthlyTrend.indices) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = PocketGreen.copy(alpha = 0.1f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = monthlyTrend[selectedBar].label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = PocketText,
                        )
                        Text(
                            text = monthlyTrend[selectedBar].formattedTotal,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = PocketGreen,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom,
            ) {
                monthlyTrend.forEachIndexed { index, monthTotal ->
                    val barHeight = if (maxValue > 0) {
                        (monthTotal.total.toFloat() / maxValue.toFloat()) * 100f
                    } else {
                        0f
                    }
                    val isSelected = selectedBar == index

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectedBar = if (selectedBar == index) -1 else index
                            },
                    ) {
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height(barHeight.coerceAtLeast(4f).dp)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(if (isSelected) PocketGreen else PocketGreen.copy(alpha = 0.6f)),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = monthTotal.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) PocketGreen else PocketTextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IncomeTab(state: InsightsUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PocketGreen),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Total Income",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.8f),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = state.totalIncomeFormatted,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun TrendsTab(state: InsightsUiState) {
    if (state.monthlyTrend.isEmpty()) {
        Text(
            text = "Loading trends...",
            style = MaterialTheme.typography.bodyMedium,
            color = PocketTextSecondary,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Monthly Spending Trend",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = PocketText,
        )

        MonthlyTrendChart(
            monthlyTrend = state.monthlyTrend,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
        )
    }
}
