package `in`.marxen.pocket.navigation

import java.time.YearMonth

object Routes {
    const val SPLASH = "splash"
    const val WELCOME = "welcome"
    const val HOME = "home"
    const val CALENDAR = "calendar"
    const val ADD = "expense/add"
    const val EDIT = "expense/edit/{transactionId}"
    const val INSIGHTS = "insights"
    const val SETTINGS = "settings"
    const val CATEGORY_LIST = "insights/categories?month={month}"
    const val CATEGORY_DETAIL = "insights/categories/{categoryId}?name={name}&month={month}"

    fun editRoute(transactionId: Long) = "expense/edit/$transactionId"
    fun categoryListRoute(month: YearMonth = YearMonth.now()) = "insights/categories?month=$month"
    fun categoryDetailRoute(categoryId: Long, categoryName: String, month: YearMonth = YearMonth.now()) =
        "insights/categories/$categoryId?name=${android.net.Uri.encode(categoryName)}&month=$month"
}
