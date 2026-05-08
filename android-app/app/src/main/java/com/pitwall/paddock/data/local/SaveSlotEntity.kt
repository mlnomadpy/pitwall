package com.pitwall.paddock.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "save_slots")
data class SaveSlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val driverName: String,
    val driverLevel: String,
    val preferredCoach: String,
    val isActive: Boolean = false,
    val lastPlayedAt: Long = System.currentTimeMillis()
)

@Dao
interface SaveSlotDao {
    @Query("SELECT * FROM save_slots WHERE isActive = 1 LIMIT 1")
    fun getActiveSlotFlow(): Flow<SaveSlotEntity?>

    @Query("SELECT * FROM save_slots WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveSlot(): SaveSlotEntity?

    @Query("SELECT * FROM save_slots ORDER BY lastPlayedAt DESC")
    suspend fun getAllSlots(): List<SaveSlotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlot(slot: SaveSlotEntity): Long

    @Query("UPDATE save_slots SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE save_slots SET isActive = 1, lastPlayedAt = :timestamp WHERE id = :id")
    suspend fun setActive(id: Int, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM save_slots")
    suspend fun deleteAll()
}
