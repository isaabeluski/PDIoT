package com.specknet.pdiotapp.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ActivityRecordDao {
    // Insert a new activity record
    @Insert
    suspend fun insertActivity(record: ActivityRecord)

    // Retrieve all activity records sorted by timestamp
    @Query("SELECT * FROM activity_records ORDER BY timestamp DESC")
    suspend fun getAllActivities(): List<ActivityRecord>
}
