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
