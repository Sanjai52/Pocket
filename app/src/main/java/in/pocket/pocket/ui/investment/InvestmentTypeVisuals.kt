package `in`.marxen.pocket.ui.investment

import androidx.compose.ui.graphics.Color
import `in`.marxen.pocket.R
import `in`.marxen.pocket.ui.theme.PocketGreen

/** Icon/color per built-in investment type, shared by the Add Investment grid, the Investment tab and the ledger. */
internal val investmentTypeIcons = mapOf(
    "stocks" to R.drawable.ic_inv_stocks,
    "ipo" to R.drawable.ic_inv_ipo,
    "mutual fund" to R.drawable.ic_inv_mutual_fund,
    "sip" to R.drawable.ic_inv_sip,
    "gold" to R.drawable.ic_inv_gold,
    "other" to R.drawable.ic_other,
)

internal val investmentTypeColors = mapOf(
    "stocks" to Color(0xFF2196F3),
    "ipo" to Color(0xFF9C27B0),
    "mutual fund" to Color(0xFF00897B),
    "sip" to Color(0xFFFF9800),
    "gold" to Color(0xFFD4A017),
)

/** Fallback visuals for custom (user-added) types. */
internal val investmentCustomTypeIcon = R.drawable.ic_inv_custom
internal val investmentCustomTypeColor = Color(0xFFD4A017)
internal val investmentOtherTypeColor = Color(0xFF607D8B)

internal data class InvestmentRowVisual(val iconRes: Int, val color: Color)

/** Icon + tint for one investment/savings row, used by both the tab and the ledger screens. */
internal fun investmentRowVisual(title: String, isSavings: Boolean, isOut: Boolean): InvestmentRowVisual {
    if (isSavings) {
        return if (isOut) {
            InvestmentRowVisual(R.drawable.ic_move_out, Color(0xFFFF9800))
        } else {
            InvestmentRowVisual(R.drawable.ic_move_in, PocketGreen)
        }
    }
    val key = title.trim().lowercase()
    val color = investmentTypeColors[key]
        ?: if (key == "other") investmentOtherTypeColor else investmentCustomTypeColor
    return InvestmentRowVisual(investmentTypeIcons[key] ?: investmentCustomTypeIcon, color)
}
