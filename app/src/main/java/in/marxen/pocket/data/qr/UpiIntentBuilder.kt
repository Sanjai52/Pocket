package `in`.marxen.pocket.data.qr

import android.content.Intent
import android.net.Uri
import java.math.BigDecimal
import java.net.URLEncoder

object UpiIntentBuilder {

    fun buildPayUri(
        payeeVpa: String,
        amountPaise: Long,
        payeeName: String? = null,
        transactionRef: String? = null,
        note: String? = null,
        merchantCode: String? = null,
        transactionId: String? = null,
    ): String {
        val sb = StringBuilder()
        sb.append("upi://pay?")
        sb.append("pa=").append(URLEncoder.encode(payeeVpa, "UTF-8"))
        sb.append("&am=").append(paiseToUpiAmount(amountPaise))
        sb.append("&cu=INR")
        sb.append("&mode=00")
        sb.append("&orgid=000000")

        if (!payeeName.isNullOrBlank()) {
            sb.append("&pn=").append(URLEncoder.encode(payeeName, "UTF-8"))
        }
        if (!merchantCode.isNullOrBlank()) {
            sb.append("&mc=").append(URLEncoder.encode(merchantCode, "UTF-8"))
        }
        if (!transactionRef.isNullOrBlank()) {
            sb.append("&tr=").append(URLEncoder.encode(transactionRef, "UTF-8"))
        }
        if (!transactionId.isNullOrBlank()) {
            sb.append("&tid=").append(URLEncoder.encode(transactionId, "UTF-8"))
        }
        if (!note.isNullOrBlank()) {
            sb.append("&tn=").append(URLEncoder.encode(note, "UTF-8"))
        }

        return sb.toString()
    }

    fun buildPassThroughIntent(rawUri: String, packageName: String?): Intent {
        var uriStr = rawUri
        if (!uriStr.contains("mode=")) {
            uriStr += "&mode=00"
        }
        if (!uriStr.contains("orgid=")) {
            uriStr += "&orgid=000000"
        }
        val uri = Uri.parse(uriStr)
        return Intent(Intent.ACTION_VIEW, uri).apply {
            if (!packageName.isNullOrBlank()) {
                setPackage(packageName)
            }
        }
    }

    fun buildDirectIntent(
        payeeVpa: String,
        amountPaise: Long,
        packageName: String,
        payeeName: String? = null,
        transactionRef: String? = null,
        note: String? = null,
        merchantCode: String? = null,
        transactionId: String? = null,
    ): Intent {
        val uriStr = buildPayUri(payeeVpa, amountPaise, payeeName, transactionRef, note, merchantCode, transactionId)
        val uri = Uri.parse(uriStr)
        return Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(packageName)
        }
    }

    fun buildChooserIntent(
        payeeVpa: String,
        amountPaise: Long,
        payeeName: String? = null,
        transactionRef: String? = null,
        note: String? = null,
        merchantCode: String? = null,
        transactionId: String? = null,
    ): Intent {
        val uriStr = buildPayUri(payeeVpa, amountPaise, payeeName, transactionRef, note, merchantCode, transactionId)
        val uri = Uri.parse(uriStr)
        val inner = Intent(Intent.ACTION_VIEW, uri)
        return Intent.createChooser(inner, "Pay with UPI app")
    }

    fun paiseToUpiAmount(paise: Long): String {
        require(paise > 0)
        return BigDecimal.valueOf(paise, 2).toPlainString()
    }
}
