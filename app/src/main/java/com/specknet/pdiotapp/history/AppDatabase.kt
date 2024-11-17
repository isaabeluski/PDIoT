package com.specknet.pdiotapp.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Add SocialSignRecord to the entities
@Database(entities = [ActivityRecord::class, SocialSignRecord::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun activityRecordDao(): ActivityRecordDao
    abstract fun socialSignRecordDao(): SocialSignRecordDao // Add DAO for Social Signs

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Create the database instance
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "activity_database"
                )
                    .addCallback(DatabaseCallback())
                    .fallbackToDestructiveMigration() // Allows database recreation during schema changes
                    .build()
                INSTANCE = instance
                instance
            }
        }

        // Room Database Callback (optional)
        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                println("Database has been created successfully!")
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                println("Database has been opened!")
            }
        }
    }
}


