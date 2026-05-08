package com.pitwall.paddock.data

import android.content.Context
import com.pitwall.paddock.data.local.AppDatabase
import com.pitwall.paddock.data.local.SaveSlotEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class SaveSlot(
    val driverName: String,
    val driverLevel: String,
    val preferredCoach: String
)

object SaveSlotStore {
    fun getActiveSlot(context: Context): Flow<SaveSlot?> {
        val db = AppDatabase.getDatabase(context)
        return db.saveSlotDao().getActiveSlotFlow().map { entity ->
            entity?.let {
                SaveSlot(it.driverName, it.driverLevel, it.preferredCoach)
            }
        }
    }

    suspend fun saveActiveSlot(context: Context, slot: SaveSlot) {
        val db = AppDatabase.getDatabase(context)
        val dao = db.saveSlotDao()
        dao.deactivateAll()
        dao.insertSlot(
            SaveSlotEntity(
                driverName = slot.driverName,
                driverLevel = slot.driverLevel,
                preferredCoach = slot.preferredCoach,
                isActive = true
            )
        )
    }

    suspend fun clearActiveSlot(context: Context) {
        val db = AppDatabase.getDatabase(context)
        db.saveSlotDao().deactivateAll()
    }
}
