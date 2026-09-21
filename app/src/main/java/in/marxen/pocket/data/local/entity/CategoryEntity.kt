package `in`.marxen.pocket.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String? = null,
    val color: Long? = null,
    @ColumnInfo(name = "system_key") val systemKey: String? = null,
    @ColumnInfo(name = "is_hidden") val isHidden: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Instant = Instant.now(),
    @ColumnInfo(name = "updated_at") val updatedAt: Instant = Instant.now(),
)
