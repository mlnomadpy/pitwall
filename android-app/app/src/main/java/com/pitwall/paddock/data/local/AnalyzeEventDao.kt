package com.pitwall.paddock.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalyzeEventDao {
    @Insert
    suspend fun insert(entity: AnalyzeEventEntity): Long

    @Query("SELECT * FROM analyze_events ORDER BY id DESC LIMIT 50")
    fun observeRecent(): Flow<List<AnalyzeEventEntity>>
}
