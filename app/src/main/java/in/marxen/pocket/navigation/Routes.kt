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
    const val CATEGORY_LIST = "insights/categories"
    const val CATEGORY_DETAIL = "insights/categories/{categoryId}?name={name}"

    fun editRoute(transactionId: Long) = "expense/edit/$transactionId"
    fun categoryDetailRoute(categoryId: Long, categoryName: String) = "insights/categories/$categoryId?name=${android.net.Uri.encode(categoryName)}"
}
