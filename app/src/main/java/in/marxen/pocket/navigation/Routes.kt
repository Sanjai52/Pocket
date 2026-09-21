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
    const val QR_SCANNER = "qr/scanner"
    const val PAYMENT_SETUP = "qr/setup/{scannedPaymentUri}"
    const val PAYMENTS_TO_CONFIRM = "qr/pending"
    const val DEFAULT_PAYMENT_APP = "settings/default-payment-app"
    fun editRoute(transactionId: Long) = "expense/edit/$transactionId"
    fun paymentSetupRoute(scannedPaymentUri: String) = "qr/setup/${java.net.URLEncoder.encode(scannedPaymentUri, "UTF-8")}"
}
