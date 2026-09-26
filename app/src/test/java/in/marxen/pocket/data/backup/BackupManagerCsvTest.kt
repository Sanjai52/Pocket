package `in`.marxen.pocket.data.backup

import `in`.marxen.pocket.data.local.entity.TransactionEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupManagerCsvTest {
    private val manager = BackupManager()

    private fun txn(
        amountPaise: Long = 12000,
        categoryId: Long = 1,
        subcategoryId: Long? = 2,
        paymentMethodId: Long? = 3,
        merchant: String? = null,
        note: String? = null,
    ) = TransactionEntity(
        type = "EXPENSE",
        amountPaise = amountPaise,
        categoryId = categoryId,
        subcategoryId = subcategoryId,
        paymentMethodId = paymentMethodId,
        transactionDate = LocalDate.of(2026, 9, 23),
        merchant = merchant,
        note = note,
    )

    @Test
    fun header_listsAllColumns() {
        val csv = manager.exportCsv(emptyList(), emptyMap())
        assertEquals(
            "Date,Type,Amount,Category,Subcategory,Payment Method,Merchant,Note,Created At\n",
            csv,
        )
    }

    @Test
    fun amount_isRupeesNotPaise() {
        val csv = manager.exportCsv(
            listOf(txn(amountPaise = 12000)),
            mapOf(1L to "Food"),
            mapOf(2L to "Lunch"),
            mapOf(3L to "Cash"),
        )
        val row = csv.lines()[1]
        assertTrue("expected 120.00 in: $row", ",120.00," in row)
    }

    @Test
    fun resolvesNamesAndBlanksMissing() {
        val csv = manager.exportCsv(
            listOf(txn(subcategoryId = null, paymentMethodId = null)),
            mapOf(1L to "Food"),
        )
        val row = csv.lines()[1]
        assertTrue("expected Food in: $row", ",Food,," in row)
    }

    @Test
    fun escapesCommasAndQuotes() {
        val csv = manager.exportCsv(
            listOf(txn(note = "lunch, \"office\"")),
            mapOf(1L to "Food"),
            emptyMap(),
            emptyMap(),
        )
        val row = csv.lines()[1]
        assertTrue("expected escaped note in: $row", "\"lunch, \"\"office\"\"\"" in row)
    }
}
