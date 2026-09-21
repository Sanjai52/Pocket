package `in`.marxen.pocket.data.qr

data class UpiAppProfile(
    val packageName: String,
    val displayName: String,
    val blocked: Boolean = false,
    val autoRecordTrusted: Boolean = false,
)

object SupportedUpiApps {
    val all: List<UpiAppProfile> = listOf(
        UpiAppProfile("com.google.android.apps.nbu.paisa.user", "Google Pay"),
        UpiAppProfile("com.phonepe.app", "PhonePe"),
        UpiAppProfile("net.one97.paytm", "Paytm"),
        UpiAppProfile("in.org.npci.upiapp", "BHIM"),
    )

    fun byPackage(packageName: String): UpiAppProfile? =
        all.find { it.packageName == packageName }
}
