package `in`.marxen.pocket.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {
    @Test
    fun homeIsRoot() {
        assertEquals("home", Routes.HOME)
    }

    @Test
    fun allRoutesDefined() {
        assertEquals("splash", Routes.SPLASH)
        assertEquals("home", Routes.HOME)
        assertEquals("calendar", Routes.CALENDAR)
        assertEquals("expense/add", Routes.ADD)
        assertEquals("insights", Routes.INSIGHTS)
        assertEquals("settings", Routes.SETTINGS)
    }
}
