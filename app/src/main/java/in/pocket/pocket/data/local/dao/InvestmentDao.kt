package `in`.marxen.pocket.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import `in`.marxen.pocket.data.local.entity.InvestmentEntryEntity
import `in`.marxen.pocket.data.local.entity.InvestmentTypeEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface InvestmentDao {
    @Insert
    suspend fun insertEntry(entry: InvestmentEntryEntity): Long

    @Update
    suspend fun updateEntry(entry: InvestmentEntryEntity)

    @Query("SELECT * FROM investment_entries WHERE id = :id")
    suspend fun getEntryById(id: Long): InvestmentEntryEntity?

    @Query("DELETE FROM investment_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Long)

    @Query("SELECT * FROM investment_entries")
    suspend fun getAllEntriesSync(): List<InvestmentEntryEntity>

    @Query("DELETE FROM investment_entries")
    suspend fun deleteAllEntries()

    @Query("SELECT e.*, t.name AS type_name FROM investment_entries e LEFT JOIN investment_types t ON e.type_id = t.id WHERE e.entry_date BETWEEN :start AND :end ORDER BY e.entry_date DESC, e.created_at DESC")
    fun entriesBetween(start: LocalDate, end: LocalDate): Flow<List<InvestmentEntryWithType>>

    @Query("SELECT e.*, t.name AS type_name FROM investment_entries e LEFT JOIN investment_types t ON e.type_id = t.id WHERE e.kind = :kind AND e.entry_date BETWEEN :start AND :end ORDER BY e.entry_date DESC, e.created_at DESC")
    fun entriesBetweenByKind(kind: String, start: LocalDate, end: LocalDate): Flow<List<InvestmentEntryWithType>>

    @Query("SELECT COALESCE(SUM(CASE WHEN direction = 'IN' THEN amount_paise ELSE -amount_paise END), 0) FROM investment_entries WHERE kind = 'SAVING' AND entry_date <= :asOf")
    fun savingsBalanceAsOf(asOf: LocalDate): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount_paise), 0) FROM investment_entries WHERE kind = 'INVESTMENT' AND entry_date <= :asOf")
    fun investedTotalAsOf(asOf: LocalDate): Flow<Long>

    @Insert
    suspend fun insertType(type: InvestmentTypeEntity): Long

    @Query("SELECT * FROM investment_types ORDER BY sort_order ASC, id ASC")
    fun allTypes(): Flow<List<InvestmentTypeEntity>>

    @Query("SELECT * FROM investment_types ORDER BY sort_order ASC, id ASC")
    suspend fun allTypesSync(): List<InvestmentTypeEntity>

    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM investment_types")
    suspend fun maxSortOrder(): Int

    @Query("DELETE FROM investment_types")
    suspend fun deleteAllTypes()
}

data class InvestmentEntryWithType(
    @Embedded val entry: InvestmentEntryEntity,
    @ColumnInfo(name = "type_name") val typeName: String?,
)
