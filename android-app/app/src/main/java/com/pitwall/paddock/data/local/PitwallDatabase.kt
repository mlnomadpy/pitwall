package com.pitwall.paddock.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AnalyzeEventEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class PitwallDatabase : RoomDatabase() {
    abstract fun analyzeEventDao(): AnalyzeEventDao

    companion object {
        @Volatile
        private var instance: PitwallDatabase? = null

        fun get(context: Context): PitwallDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PitwallDatabase::class.java,
                    "pitwall_parallel.db",
                ).fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
