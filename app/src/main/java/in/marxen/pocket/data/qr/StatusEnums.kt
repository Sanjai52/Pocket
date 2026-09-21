package `in`.marxen.pocket.data.qr

enum class PaymentAttemptStatus {
    CREATED, LAUNCHED, UNRESOLVED, RECORDED, FAILED, DISCARDED, EXPIRED
}

enum class ResultHint {
    NONE, APP_SUBMITTED, APP_SUCCESS_UNVERIFIED, MISMATCH,
    CANCELED_NO_DATA, NO_STATUS, NO_RETURN_DATA, RESTORED, LAUNCH_ERROR
}

enum class RecordedSource { APP_REPORTED, USER_CONFIRMED }

enum class LaunchPlan { PASS_THROUGH, REBUILT, MANUAL }

enum class PaymentProvider { DIRECT_APP, CHOOSER }
