package com.pitwall.parallel.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalyzeEventDao {
    @Insert
    suspend fun insert(entity: AnalyzeEventEntity): Long

    @Query("SELECT * FROM parallel_analyze_events ORDER BY id DESC LIMIT 30")
    fun observeRecent(): Flow<List<AnalyzeEventEntity>>
}
