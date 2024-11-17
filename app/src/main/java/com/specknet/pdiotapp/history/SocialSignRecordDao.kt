package com.specknet.pdiotapp.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SocialSignRecordDao {
    @Insert
    suspend fun insertSocialSign(socialSignRecord: SocialSignRecord)

    @Query("SELECT * FROM social_signs ORDER BY timestamp DESC")
    suspend fun getAllSocialSigns(): List<SocialSignRecord>
}
