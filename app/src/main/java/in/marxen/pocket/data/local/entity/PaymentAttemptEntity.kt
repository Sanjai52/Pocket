package `in`.marxen.pocket.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "payment_attempts",
    indices = [
        Index("status"),
        Index("payee_vpa_key"),
        Index("created_at"),
    ],
)
data class PaymentAttemptEntity(
    @androidx.room.PrimaryKey val id: String,
    @ColumnInfo(name = "payee_vpa") val payeeVpa: String,
    @ColumnInfo(name = "payee_vpa_key") val payeeVpaKey: String,
    @ColumnInfo(name = "payee_name") val payeeName: String?,
    @ColumnInfo(name = "merchant_category_code") val merchantCategoryCode: String?,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    val currency: String,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    @ColumnInfo(name = "local_note") val localNote: String?,
    @ColumnInfo(name = "launch_plan") val launchPlan: String,
    @ColumnInfo(name = "sent_txn_ref") val sentTxnRef: String?,
    @ColumnInfo(name = "qr_kind") val qrKind: String,
    @ColumnInfo(name = "raw_upi_uri") val rawUpiUri: String?,
    val provider: String,
    @ColumnInfo(name = "provider_package") val providerPackage: String?,
    val status: String,
    @ColumnInfo(name = "result_hint") val resultHint: String,
    @ColumnInfo(name = "result_code") val resultCode: Int?,
    @ColumnInfo(name = "app_status") val appStatus: String?,
    @ColumnInfo(name = "app_txn_id") val appTxnId: String?,
    @ColumnInfo(name = "app_approval_ref") val appApprovalRef: String?,
    @ColumnInfo(name = "app_response_code") val appResponseCode: String?,
    @ColumnInfo(name = "recorded_source") val recordedSource: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "launched_at") val launchedAt: Long?,
    @ColumnInfo(name = "resolved_at") val resolvedAt: Long?,
    @ColumnInfo(name = "last_prompted_at") val lastPromptedAt: Long?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
