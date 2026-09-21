package `in`.marxen.pocket.core.money

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {
    @Test
    fun paiseRoundTrip() {
        assertEquals(25050L, rupeesToPaise("250.50"))
        assertEquals("250.50", paiseToRupees(25050L))
    }

    @Test
    fun wholeRupees() {
        assertEquals(25000L, rupeesToPaise("250"))
        assertEquals("250.00", paiseToRupees(25000L))
    }

    @Test
    fun largeAmount() {
        assertEquals(12345678L, rupeesToPaise("123456.78"))
        assertEquals("123456.78", paiseToRupees(12345678L))
    }

    @Test
    fun zero() {
        assertEquals(0L, rupeesToPaise("0"))
        assertEquals("0.00", paiseToRupees(0L))
    }
}
