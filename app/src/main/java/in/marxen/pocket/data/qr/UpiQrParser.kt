package `in`.marxen.pocket.data.qr

import java.math.BigDecimal
import java.net.URLDecoder
import java.util.Locale

enum class QrKind { PERSONAL, MERCHANT_STATIC, MERCHANT_DYNAMIC }

enum class ParseReason {
    TOO_LONG, BAD_VPA, NON_INR, BAD_AMOUNT, DUPLICATE_PARAM, MALFORMED
}

sealed class ParseResult {
    data class Valid(val payment: ScannedUpiPayment) : ParseResult()
    data class Invalid(val reason: ParseReason) : ParseResult()
    data object NotUpi : ParseResult()
}

data class ScannedUpiPayment(
    val rawUri: String,
    val payeeVpa: String,
    val payeeVpaKey: String,
    val payeeName: String?,
    val merchantCategoryCode: String?,
    val qrTransactionRef: String?,
    val qrNote: String?,
    val qrReferenceUrl: String?,
    val qrAmountPaise: Long?,
    val minAmountPaise: Long?,
    val hasSignature: Boolean,
    val kind: QrKind,
)

internal data class QueryPair(val rawKey: String, val rawValue: String) {
    val key: String get() = decode(rawKey).lowercase(Locale.ROOT)
    val value: String get() = decode(rawValue)
    private fun decode(s: String) = URLDecoder.decode(s, "UTF-8")
}

internal fun splitQuery(rawQuery: String): List<QueryPair> =
    if (rawQuery.isEmpty()) emptyList()
    else rawQuery.split('&').filter { it.isNotEmpty() }.map {
        QueryPair(it.substringBefore('='), it.substringAfter('=', ""))
    }

object UpiQrParser {
    private const val MAX_PAYLOAD = 2048
    private val VPA = Regex("^[A-Za-z0-9._-]{2,256}@[A-Za-z][A-Za-z0-9.-]{1,63}$")
    private val AMOUNT = Regex("^\\d{1,9}(\\.\\d{1,2})?$")

    fun parse(payload: String): ParseResult {
        val raw = payload.trim()
        if (raw.isEmpty() || raw.length > MAX_PAYLOAD) return ParseResult.Invalid(ParseReason.TOO_LONG)
        if (!raw.substringBefore('?').equals("upi://pay", ignoreCase = true)) return ParseResult.NotUpi

        val pairs = try {
            splitQuery(raw.substringAfter('?', ""))
        } catch (e: IllegalArgumentException) {
            return ParseResult.Invalid(ParseReason.MALFORMED)
        }

        fun single(k: String): String? {
            val hits = pairs.filter { it.key == k }
            return if (hits.size > 1) throw DuplicateParam() else hits.firstOrNull()?.value
        }

        return try {
            val pa = single("pa")?.trim().orEmpty()
            if (!VPA.matches(pa)) return ParseResult.Invalid(ParseReason.BAD_VPA)

            val cu = single("cu")
            if (cu != null && !cu.equals("INR", ignoreCase = true))
                return ParseResult.Invalid(ParseReason.NON_INR)

            val amount = when (val am = single("am")?.trim()) {
                null, "" -> null
                else -> {
                    if (!AMOUNT.matches(am)) return ParseResult.Invalid(ParseReason.BAD_AMOUNT)
                    val paise = BigDecimal(am).movePointRight(2).longValueExact()
                    if (paise == 0L) null else paise
                }
            }

            val mamRaw = single("mam")?.trim()
            val mam = if (mamRaw.isNullOrBlank()) null else {
                if (!AMOUNT.matches(mamRaw)) return ParseResult.Invalid(ParseReason.BAD_AMOUNT)
                BigDecimal(mamRaw).movePointRight(2).longValueExact().takeIf { it > 0 }
            }

            val pn = single("pn")
            val mc = single("mc")
            val tr = single("tr")
            val tn = single("tn")
            val url = single("url")
            val sign = single("sign")

            val kind = when {
                mc.isNullOrBlank() || mc == "0000" -> QrKind.PERSONAL
                amount != null || sign != null -> QrKind.MERCHANT_DYNAMIC
                else -> QrKind.MERCHANT_STATIC
            }

            ParseResult.Valid(
                ScannedUpiPayment(
                    rawUri = raw,
                    payeeVpa = pa,
                    payeeVpaKey = pa.lowercase(Locale.ROOT),
                    payeeName = pn,
                    merchantCategoryCode = mc,
                    qrTransactionRef = tr,
                    qrNote = tn,
                    qrReferenceUrl = url,
                    qrAmountPaise = amount,
                    minAmountPaise = mam,
                    hasSignature = sign != null,
                    kind = kind,
                )
            )
        } catch (e: DuplicateParam) {
            ParseResult.Invalid(ParseReason.DUPLICATE_PARAM)
        } catch (e: ArithmeticException) {
            ParseResult.Invalid(ParseReason.BAD_AMOUNT)
        }
    }
}

private class DuplicateParam : Exception()
