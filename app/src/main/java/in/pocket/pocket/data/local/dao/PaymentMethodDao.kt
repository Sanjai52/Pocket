package `in`.marxen.pocket.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import `in`.marxen.pocket.data.local.entity.PaymentMethodEntity

@Dao
interface PaymentMethodDao {
    @Query("SELECT * FROM payment_methods")
    suspend fun getAll(): List<PaymentMethodEntity>

    @Insert
    suspend fun insertAll(methods: List<PaymentMethodEntity>)

    @Query("DELETE FROM payment_methods")
    suspend fun deleteAll()
}
