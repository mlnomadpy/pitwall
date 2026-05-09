package com.pitwall.parallel.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "parallel_analyze_events")
data class AnalyzeEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val coaching: String,
    val burstId: Int,
    val source: String,
    val createdAtEpochMs: Long,
)
