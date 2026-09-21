package `in`.marxen.pocket.core.date

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class IstDateTest {
    @Test
    fun zoneIsKolkata() {
        assertEquals(ZoneId.of("Asia/Kolkata"), pocketZoneId())
    }

    @Test
    fun todayReturnsLocalDate() {
        val today = asiaKolkataToday()
        assertEquals(LocalDate::class, today::class)
        assertEquals(LocalDate.now(ZoneId.of("Asia/Kolkata")), today)
    }
}
