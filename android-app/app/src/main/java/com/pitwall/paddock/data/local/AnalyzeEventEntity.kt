package com.pitwall.paddock.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "analyze_events")
data class AnalyzeEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val coaching: String,
    val burstId: Int,
    val source: String,
    val createdAtEpochMs: Long,
)
