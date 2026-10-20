package `in`.marxen.pocket.ui.investment

import `in`.marxen.pocket.data.local.dao.InvestmentEntryWithType
import `in`.marxen.pocket.data.local.entity.InvestmentEntryEntity
import `in`.marxen.pocket.data.local.entity.InvestmentTypeEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InvestmentModelsTest {
    private fun row(
        kind: String = "SAVING",
        direction: String = "IN",
        typeName: String? = null,
        note: String? = null,
    ) = InvestmentEntryWithType(
        entry = InvestmentEntryEntity(
            kind = kind,
            direction = direction,
            typeId = if (typeName != null) 1L else null,
            amountPaise = 120000,
            entryDate = LocalDate.of(2026, 10, 12),
            note = note,
        ),
        typeName = typeName,
    ).toRowUi()

    @Test
    fun savingsIn_isToSecondaryWithPlus() {
        val r = row(direction = "IN")
        assertEquals("To secondary", r.title)
        assertEquals("+₹1,200", r.amountFormatted)
        assertFalse(r.isOut)
    }

    @Test
    fun savingsOut_isFromSecondaryWithMinus() {
        val r = row(direction = "OUT")
        assertEquals("From secondary", r.title)
        assertEquals("\u2212\u20B91,200", r.amountFormatted)
        assertTrue(r.isOut)
    }

    @Test
    fun investment_usesTypeName_fallsBackToOther() {
        assertEquals("Stocks", row(kind = "INVESTMENT", typeName = "Stocks").title)
        assertEquals("Other", row(kind = "INVESTMENT", typeName = null).title)
    }

    @Test
    fun subtitle_joinsDateAndNote_skipsBlankNote() {
        assertEquals("12 Oct 2026 · Tata Motors", row(note = "Tata Motors").subtitle)
        assertEquals("12 Oct 2026", row(note = "  ").subtitle)
    }

    @Test
    fun withOtherLast_placesCustomTypesBeforeOther() {
        val types = listOf(
            InvestmentTypeEntity(name = "Stocks", sortOrder = 0),
            InvestmentTypeEntity(name = "IPO", sortOrder = 1),
            InvestmentTypeEntity(name = "Mutual fund", sortOrder = 2),
            InvestmentTypeEntity(name = "SIP", sortOrder = 3),
            InvestmentTypeEntity(name = "Other", sortOrder = 4),
            InvestmentTypeEntity(name = "Gold", sortOrder = 5),
        )
        assertEquals(
            listOf("Stocks", "IPO", "Mutual fund", "SIP", "Gold", "Other"),
            types.withOtherLast().map { it.name },
        )
    }

    @Test
    fun withOtherLast_matchesOtherCaseInsensitively() {
        val types = listOf(
            InvestmentTypeEntity(name = "Gold", sortOrder = 5),
            InvestmentTypeEntity(name = "OTHER", sortOrder = 4),
        )
        assertEquals(listOf("Gold", "OTHER"), types.withOtherLast().map { it.name })
    }

    @Test
    fun withOtherLast_handlesEmptyAndSingleType() {
        assertEquals(emptyList<InvestmentTypeEntity>(), emptyList<InvestmentTypeEntity>().withOtherLast())
        val only = listOf(InvestmentTypeEntity(name = "Gold", sortOrder = 0))
        assertEquals(listOf("Gold"), only.withOtherLast().map { it.name })
    }
}
