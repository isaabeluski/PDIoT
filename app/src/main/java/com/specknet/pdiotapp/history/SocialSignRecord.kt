package com.specknet.pdiotapp.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "social_signs")
data class SocialSignRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val socialSignLabel: String,
    val timestamp: Long
)
