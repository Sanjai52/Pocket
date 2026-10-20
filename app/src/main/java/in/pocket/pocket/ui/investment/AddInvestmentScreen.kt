package `in`.marxen.pocket.ui.investment

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.marxen.pocket.R
import `in`.marxen.pocket.ui.theme.PocketBeige
import `in`.marxen.pocket.ui.theme.PocketGreen
import `in`.marxen.pocket.ui.theme.PocketText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddInvestmentScreen(
    entryId: Long? = null,
    onBack: () -> Unit = {},
    onSaved: () -> Unit = {},
    viewModel: AddInvestmentViewModel = viewModel(
        factory = AddInvestmentViewModel.Factory(
            (LocalContext.current.applicationContext as `in`.marxen.pocket.PocketApplication).container.investmentRepository,
        ),
    ),
) {
    val state by viewModel.uiState.collectAsState()
    val types by viewModel.types.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(entryId) {
        if (entryId != null && entryId > 0) {
            viewModel.loadEntry(entryId)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.saved.collect { onSaved() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PocketBeige)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = "Back",
                tint = PocketText,
                modifier = Modifier
                    .size(28.dp)
                    .clickable { onBack() },
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (state.isEditing) "Edit Investment" else "Add Investment",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = PocketText,
            )
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.size(28.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            listOf("SAVING", "INVESTMENT").forEach { type ->
                FilterChip(
                    selected = state.kind == type,
                    onClick = { viewModel.updateKind(type) },
                    label = { Text(if (type == "SAVING") "Savings" else "Investments") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = when {
                            type == "SAVING" -> PocketGreen
                            type == "INVESTMENT" -> Color(0xFF2E7D32)
                            else -> PocketBeige
                        },
                        selectedLabelColor = Color.White,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = state.amount,
            onValueChange = { viewModel.updateAmount(it) },
            label = { Text("Amount") },
            prefix = { Text("\u20B9 ", fontWeight = FontWeight.Bold) },
            textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = state.amountError != null,
            supportingText = state.amountError?.let { { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (state.kind == "SAVING") "Move" else "Type",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = PocketText,
        )

        if (state.typeError != null) {
            Text(
                text = state.typeError!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (state.kind == "SAVING") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val moveTiles = listOf(
                    MoveTile("To secondary", "IN", R.drawable.ic_move_in, PocketGreen),
                    MoveTile("From secondary", "OUT", R.drawable.ic_move_out, Color(0xFFFF9800)),
                )
                moveTiles.forEach { tile ->
                    val selected = state.direction == tile.direction
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.selectDirection(tile.direction) }
                            .then(
                                if (selected) Modifier.border(2.dp, PocketGreen, RoundedCornerShape(12.dp))
                                else Modifier
                            )
                            .padding(8.dp)
                            .weight(1f),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(tile.tint.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(tile.iconRes),
                                contentDescription = tile.label,
                                tint = tile.tint,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = tile.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = PocketText,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
        } else if (types.isEmpty()) {
            OutlinedButton(
                onClick = { viewModel.showAddTypeDialog() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add type")
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                types.chunked(3).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        row.forEach { type ->
                            val selected = state.selectedTypeId == type.id
                            val typeKey = type.name.lowercase()
                            val isOther = typeKey == "other"
                            val iconRes = investmentTypeIcons[typeKey] ?: investmentCustomTypeIcon
                            val iconColor = investmentTypeColors[typeKey]
                                ?: if (isOther) investmentOtherTypeColor else investmentCustomTypeColor

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { viewModel.selectType(type.id) }
                                    .then(
                                        if (selected) Modifier.border(2.dp, PocketGreen, RoundedCornerShape(12.dp))
                                        else Modifier
                                    )
                                    .padding(8.dp)
                                    .weight(1f),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(iconColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(iconRes),
                                        contentDescription = type.name,
                                        tint = iconColor,
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        text = type.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PocketText,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                    )
                                    if (isOther) {
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Icon(
                                            Icons.Filled.Add,
                                            contentDescription = "Add custom type",
                                            tint = PocketGreen,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { viewModel.showAddTypeDialog() },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { showDatePicker = true },
            shape = RoundedCornerShape(12.dp),
            color = PocketBeige,
            border = BorderStroke(1.dp, PocketText.copy(alpha = 0.2f)),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "\uD83D\uDCC5",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = state.date.format(DateTimeFormatter.ofPattern("d MMMM yyyy")),
                    style = MaterialTheme.typography.bodyLarge,
                    color = PocketText,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = PocketText,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { viewModel.toggleDetails() }
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "More details (optional)",
                style = MaterialTheme.typography.bodyMedium,
                color = PocketText.copy(alpha = 0.6f),
            )
            Icon(
                if (state.detailsExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = PocketText.copy(alpha = 0.6f),
            )
        }

        AnimatedVisibility(visible = state.detailsExpanded) {
            Column {
                OutlinedTextField(
                    value = state.note,
                    onValueChange = { viewModel.updateNote(it) },
                    label = { Text("Note") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.save() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PocketGreen),
        ) {
            Text(
                text = if (state.isEditing) "Update Investment" else "Save Investment",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.of("Asia/Kolkata")).toLocalDate()
                        viewModel.updateDate(date)
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (state.showAddTypeDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAddTypeDialog() },
            title = { Text("Add Custom Type") },
            text = {
                OutlinedTextField(
                    value = state.customTypeName,
                    onValueChange = { viewModel.updateCustomTypeName(it) },
                    label = { Text("Type name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.addCustomType() }) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAddTypeDialog() }) {
                    Text("Cancel")
                }
            },
        )
    }
}

private data class MoveTile(
    val label: String,
    val direction: String,
    val iconRes: Int,
    val tint: Color,
)
