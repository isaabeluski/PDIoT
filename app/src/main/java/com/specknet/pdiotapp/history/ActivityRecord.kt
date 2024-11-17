package com.specknet.pdiotapp.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activity_records")
data class ActivityRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0, // Auto-generated ID
    val activityLabel: String, // The activity nam
    val timestamp: Long // When the activity occurred
)