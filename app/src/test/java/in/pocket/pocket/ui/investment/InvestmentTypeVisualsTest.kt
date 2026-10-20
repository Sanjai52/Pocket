package `in`.marxen.pocket.ui.investment

import androidx.compose.ui.graphics.Color
import `in`.marxen.pocket.R
import `in`.marxen.pocket.ui.theme.PocketGreen
import org.junit.Assert.assertEquals
import org.junit.Test

class InvestmentTypeVisualsTest {
    @Test
    fun savingsRows_useMoveArrowsAndDirectionColors() {
        val incoming = investmentRowVisual("To secondary", isSavings = true, isOut = false)
        assertEquals(R.drawable.ic_move_in, incoming.iconRes)
        assertEquals(PocketGreen, incoming.color)

        val outgoing = investmentRowVisual("From secondary", isSavings = true, isOut = true)
        assertEquals(R.drawable.ic_move_out, outgoing.iconRes)
        assertEquals(Color(0xFFFF9800), outgoing.color)
    }

    @Test
    fun goldType_hasDedicatedIconAndGoldTint() {
        val visual = investmentRowVisual("Gold", isSavings = false, isOut = false)
        assertEquals(R.drawable.ic_inv_gold, visual.iconRes)
        assertEquals(Color(0xFFD4A017), visual.color)
    }

    @Test
    fun builtInTypes_keepTheirExistingIcons() {
        val expected = mapOf(
            "Stocks" to R.drawable.ic_inv_stocks,
            "IPO" to R.drawable.ic_inv_ipo,
            "Mutual fund" to R.drawable.ic_inv_mutual_fund,
            "SIP" to R.drawable.ic_inv_sip,
        )
        expected.forEach { (title, iconRes) ->
            assertEquals(iconRes, investmentRowVisual(title, isSavings = false, isOut = false).iconRes)
        }
    }

    @Test
    fun otherType_usesOtherIconAndGreyTint() {
        val visual = investmentRowVisual("Other", isSavings = false, isOut = false)
        assertEquals(R.drawable.ic_other, visual.iconRes)
        assertEquals(Color(0xFF607D8B), visual.color)
    }

    @Test
    fun customType_usesCustomIconAndGoldTint() {
        val visual = investmentRowVisual("Real estate", isSavings = false, isOut = false)
        assertEquals(R.drawable.ic_inv_custom, visual.iconRes)
        assertEquals(Color(0xFFD4A017), visual.color)
    }

    @Test
    fun goldLookup_ignoresCaseAndSurroundingWhitespace() {
        val visual = investmentRowVisual("  GOLD ", isSavings = false, isOut = false)
        assertEquals(R.drawable.ic_inv_gold, visual.iconRes)
    }
}
