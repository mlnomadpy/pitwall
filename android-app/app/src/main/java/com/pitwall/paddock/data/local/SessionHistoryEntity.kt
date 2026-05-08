package com.pitwall.paddock.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "session_history")
data class SessionHistoryEntity(
    @PrimaryKey val sessionId: String,
    val driverName: String,
    val trackName: String,
    val startTime: Long,
    val bestLapTimeS: Float,
    val lapCount: Int,
    val coachGrade: String,
    val sessionJson: String // Store raw response for detailed offline view
)

@Dao
interface SessionHistoryDao {
    @Query("SELECT * FROM session_history ORDER BY startTime DESC")
    fun getAllSessionsFlow(): Flow<List<SessionHistoryEntity>>

    @Query("SELECT * FROM session_history WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): SessionHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionHistoryEntity)

    @Query("DELETE FROM session_history")
    suspend fun deleteAll()
}
