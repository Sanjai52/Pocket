package `in`.marxen.pocket.data.backup

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class LegacyBackupDecodeTest {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun preInvestmentBackup_decodesWithEmptyInvestmentTables() {
        val legacy = """
            {
              "schemaVersion": 1,
              "createdAt": "2026-09-26T10:00:00Z",
              "transactions": [],
              "categories": [],
              "paymentMethods": [],
              "budgets": [],
              "recurringTransactions": []
            }
        """.trimIndent()

        val backup = json.decodeFromString(PocketBackup.serializer(), legacy)

        assertEquals(1, backup.schemaVersion)
        assertTrue(backup.investmentTypes.isEmpty())
        assertTrue(backup.investmentEntries.isEmpty())
        assertTrue(backup.subcategories.isEmpty())
    }

    @Test
    fun legacyBackup_decodesTransactionWithoutSubcategoryKey() {
        val legacy = """
            {
              "schemaVersion": 1,
              "createdAt": "2026-09-26T10:00:00Z",
              "transactions": [
                {
                  "id": 1,
                  "type": "EXPENSE",
                  "amountPaise": 123456,
                  "categoryId": 2,
                  "paymentMethodId": 1,
                  "transactionDate": "2026-09-15",
                  "merchant": "BigBasket",
                  "note": "sep groceries",
                  "createdAt": 1789430400000,
                  "updatedAt": 1789430400000
                }
              ],
              "categories": [],
              "paymentMethods": [],
              "budgets": [],
              "recurringTransactions": []
            }
        """.trimIndent()

        val backup = json.decodeFromString(PocketBackup.serializer(), legacy)

        assertNull(backup.transactions.single().subcategoryId)
    }

    @Test
    fun currentBackup_encodesInvestmentAndSubcategoryKeys() {
        val backup = PocketBackup(
            schemaVersion = 1,
            createdAt = "2026-10-20T00:00:00Z",
            transactions = emptyList(),
            categories = emptyList(),
            paymentMethods = emptyList(),
            budgets = emptyList(),
            recurringTransactions = emptyList(),
        )

        val encoded = json.encodeToString(PocketBackup.serializer(), backup)

        assertTrue("investmentTypes" in encoded)
        assertTrue("investmentEntries" in encoded)
        assertTrue("subcategories" in encoded)
    }

    @Test
    fun backupWithSubcategoriesAndLinks_roundTrips() {
        val instant = Instant.ofEpochMilli(1_792_000_000_000)
        val backup = PocketBackup(
            schemaVersion = 1,
            createdAt = "2026-10-20T00:00:00Z",
            transactions = listOf(
                SerializableTransactionEntity(
                    id = 1,
                    type = "EXPENSE",
                    amountPaise = 45000,
                    categoryId = 3,
                    subcategoryId = 12,
                    paymentMethodId = null,
                    transactionDate = LocalDate.parse("2026-10-05"),
                    merchant = null,
                    note = null,
                    createdAt = instant,
                    updatedAt = instant,
                ),
            ),
            categories = emptyList(),
            subcategories = listOf(
                SerializableSubcategoryEntity(
                    id = 12,
                    name = "Auto",
                    categoryId = 3,
                    systemKey = null,
                    isHidden = false,
                    createdAt = instant,
                    updatedAt = instant,
                ),
            ),
            paymentMethods = emptyList(),
            budgets = emptyList(),
            recurringTransactions = emptyList(),
        )

        val encoded = json.encodeToString(PocketBackup.serializer(), backup)
        val decoded = json.decodeFromString(PocketBackup.serializer(), encoded)

        assertTrue("subcategoryId" in encoded)
        assertEquals(backup.subcategories, decoded.subcategories)
        assertEquals(12L, decoded.transactions.single().subcategoryId)
    }
}
