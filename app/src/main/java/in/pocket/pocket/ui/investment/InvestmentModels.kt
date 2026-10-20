package `in`.marxen.pocket.ui.investment

import `in`.marxen.pocket.core.money.formatPaiseCompact
import `in`.marxen.pocket.data.local.dao.InvestmentEntryWithType
import `in`.marxen.pocket.data.local.entity.InvestmentTypeEntity
import java.time.format.DateTimeFormatter
import java.util.Locale

data class InvestmentRowUi(
    val id: Long,
    val kind: String,
    val isOut: Boolean,
    val title: String,
    val subtitle: String,
    val amountFormatted: String,
)

private val rowDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

fun InvestmentEntryWithType.toRowUi(): InvestmentRowUi {
    val isSavings = entry.kind == "SAVING"
    val isOut = isSavings && entry.direction == "OUT"
    val title = if (isSavings) {
        if (isOut) "From secondary" else "To secondary"
    } else {
        typeName?.takeIf { it.isNotBlank() } ?: "Other"
    }
    val subtitle = listOfNotNull(
        entry.entryDate.format(rowDateFormatter),
        entry.note?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    val sign = if (isOut) "\u2212" else "+"
    return InvestmentRowUi(
        id = entry.id,
        kind = entry.kind,
        isOut = isOut,
        title = title,
        subtitle = subtitle,
        amountFormatted = sign + formatPaiseCompact(entry.amountPaise),
    )
}

/**
 * Display order for investment types: built-ins and custom types by [InvestmentTypeEntity.sortOrder],
 * with the "Other" tile always last so it stays the add-your-own entry point.
 */
fun List<InvestmentTypeEntity>.withOtherLast(): List<InvestmentTypeEntity> =
    sortedWith(compareBy({ it.name.equals("Other", ignoreCase = true) }, { it.sortOrder }, { it.id }))
