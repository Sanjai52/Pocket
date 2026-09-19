package `in`.marxen.pocket.navigation

object Routes {
    const val SPLASH = "splash"
    const val WELCOME = "welcome"
    const val HOME = "home"
    const val CALENDAR = "calendar"
    const val ADD = "expense/add"
    const val EDIT = "expense/edit/{transactionId}"
    const val INSIGHTS = "insights"
    const val SETTINGS = "settings"

    fun editRoute(transactionId: Long) = "expense/edit/$transactionId"
}
