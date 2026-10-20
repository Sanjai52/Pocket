package `in`.marxen.pocket.core.money

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

fun rupeesToPaise(input: String): Long {
    val cleaned = input.replace(",", "").replace("₹", "").trim()
    return BigDecimal(cleaned).setScale(2, RoundingMode.HALF_EVEN)
        .movePointRight(2).longValueExact()
}

fun paiseToRupees(paise: Long): String {
    return BigDecimal(paise).movePointLeft(2)
        .setScale(2, RoundingMode.UNNECESSARY).toPlainString()
}

fun formatPaiseAsRupees(paise: Long): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    formatter.currency = Currency.getInstance("INR")
    return formatter.format(BigDecimal(paise).movePointLeft(2))
}

/** Like [formatPaiseAsRupees] but drops insignificant trailing zeros: ₹4,900, ₹4,900.5, ₹4,900.55. */
fun formatPaiseCompact(paise: Long): String {
    val formatted = formatPaiseAsRupees(paise)
    if (!formatted.contains('.')) return formatted
    return formatted.trimEnd('0').trimEnd('.')
}

/**
 * Compact total for narrow summary cards, capped at roughly seven characters so it never
 * ellipsizes next to the chevron: exact below ₹1 lakh (paise dropped when they would make
 * the string too long), then ₹1.34L / ₹1.23Cr style for larger sums.
 */
fun formatPaiseShort(paise: Long): String {
    val exact = formatPaiseCompact(paise)
    if (exact.length <= SHORT_MAX_LENGTH) return exact

    val sign = if (paise < 0) "-" else ""
    val absPaise = kotlin.math.abs(paise)
    return when {
        absPaise >= CRORE_IN_PAISE -> sign + "\u20B9" + unitValue(absPaise, 9) + "Cr"
        absPaise >= LAKH_IN_PAISE -> sign + "\u20B9" + unitValue(absPaise, 7) + "L"
        else -> {
            // Below ₹1 lakh only the paise made the string long; show whole rupees.
            val wholeRupeePaise = absPaise / 100 * 100
            if (sign.isEmpty()) formatPaiseCompact(wholeRupeePaise)
            else sign + formatPaiseCompact(wholeRupeePaise)
        }
    }
}

private const val SHORT_MAX_LENGTH = 7
private const val LAKH_IN_PAISE = 10_000_000L
private const val CRORE_IN_PAISE = 1_000_000_000L

/** [paise] as a unit value (lakh when [scale] is 7, crore when 9) with at most two decimals. */
private fun unitValue(paise: Long, scale: Int): String {
    val units = BigDecimal(paise).movePointLeft(scale)
    val decimals = if (units < BigDecimal.TEN) 2 else 1
    return units.setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}
