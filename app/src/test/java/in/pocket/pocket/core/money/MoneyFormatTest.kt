package `in`.marxen.pocket.core.money

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatTest {
    @Test
    fun compact_dropsWholeRupeeDecimals() {
        assertEquals("₹4,900", formatPaiseCompact(490000))
        assertEquals("₹0", formatPaiseCompact(0))
        assertEquals("₹10,000", formatPaiseCompact(1000000))
    }

    @Test
    fun compact_keepsSignificantDecimals() {
        assertEquals("₹4,900.5", formatPaiseCompact(490050))
        assertEquals("₹4,900.55", formatPaiseCompact(490055))
        assertEquals("₹4,900.05", formatPaiseCompact(490005))
    }

    @Test
    fun compact_handlesNegativeAndLargeAmounts() {
        assertEquals("-₹500", formatPaiseCompact(-50000))
        // Group separators for lakh/crore amounts differ between the JVM test runtime and Android's ICU,
        // so assert the trimming contract without depending on the grouping style.
        assertEquals(
            formatPaiseAsRupees(1000000000).removeSuffix(".00"),
            formatPaiseCompact(1000000000),
        )
    }

    @Test
    fun short_keepsExactValuesThatFitTheSummaryCard() {
        assertEquals("₹0", formatPaiseShort(0))
        assertEquals("₹450", formatPaiseShort(45000))
        assertEquals("₹999.55", formatPaiseShort(99955))
        assertEquals("₹10,000", formatPaiseShort(1000000))
        assertEquals("₹99,999", formatPaiseShort(9999900))
        assertEquals("-₹500", formatPaiseShort(-50000))
    }

    @Test
    fun short_dropsPaiseWhenTheExactStringWouldBeTooLong() {
        assertEquals("₹4,900", formatPaiseShort(490055))
        assertEquals("₹9,999", formatPaiseShort(999999))
        assertEquals("-₹999", formatPaiseShort(-99955))
    }

    @Test
    fun short_abbreviatesLakhAmounts() {
        assertEquals("₹1L", formatPaiseShort(10000000))
        assertEquals("₹1.34L", formatPaiseShort(13360600))
        assertEquals("₹1.25L", formatPaiseShort(12500000))
        assertEquals("₹99.9L", formatPaiseShort(999000000))
        assertEquals("-₹1.34L", formatPaiseShort(-13360600))
    }

    @Test
    fun short_abbreviatesCroreAmounts() {
        assertEquals("₹1Cr", formatPaiseShort(1000000000))
        assertEquals("₹1.23Cr", formatPaiseShort(1234567800))
    }
}
