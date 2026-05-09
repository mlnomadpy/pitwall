package com.pitwall.parallel.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AnalyzeEventEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ParallelDatabase : RoomDatabase() {
    abstract fun analyzeEventDao(): AnalyzeEventDao

    companion object {
        @Volatile
        private var instance: ParallelDatabase? = null

        fun get(context: Context): ParallelDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ParallelDatabase::class.java,
                    "pitwall_parallel_shell.db",
                ).fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
