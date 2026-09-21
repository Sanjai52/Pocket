package `in`.marxen.pocket.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import `in`.marxen.pocket.data.local.entity.PaymentAttemptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentAttemptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attempt: PaymentAttemptEntity): Long

    @Update
    suspend fun update(attempt: PaymentAttemptEntity)

    @Query("SELECT * FROM payment_attempts WHERE id = :id")
    suspend fun getById(id: String): PaymentAttemptEntity?

    @Query("SELECT * FROM payment_attempts WHERE status = 'UNRESOLVED' ORDER BY created_at DESC")
    fun getUnresolved(): Flow<List<PaymentAttemptEntity>>

    @Query("SELECT * FROM payment_attempts WHERE status = 'UNRESOLVED'")
    suspend fun getUnresolvedSync(): List<PaymentAttemptEntity>

    @Query("SELECT * FROM payment_attempts WHERE payee_vpa_key = :vpaKey AND status = 'UNRESOLVED' AND created_at > :since")
    suspend fun getUnresolvedForPayeeSince(vpaKey: String, since: Long): List<PaymentAttemptEntity>

    @Query("SELECT * FROM payment_attempts WHERE payee_vpa_key = :vpaKey AND amount_paise = :amount AND status = 'RECORDED' AND created_at > :since")
    suspend fun getRecordedForPayeeAmountSince(vpaKey: String, amount: Long, since: Long): PaymentAttemptEntity?

    @Query("SELECT * FROM payment_attempts ORDER BY created_at DESC")
    fun getAll(): Flow<List<PaymentAttemptEntity>>

    @Query("UPDATE payment_attempts SET status = :newStatus, updated_at = :now WHERE id = :id")
    suspend fun updateStatus(id: String, newStatus: String, now: Long): Int

    @Query("DELETE FROM payment_attempts WHERE status IN ('RECORDED', 'FAILED', 'DISCARDED', 'EXPIRED') AND created_at < :before")
    suspend fun purgeOld(before: Long)
}
