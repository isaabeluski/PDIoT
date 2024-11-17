package com.specknet.pdiotapp.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Database(entities = [ActivityRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun activityRecordDao(): ActivityRecordDao

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
                    // Add this line to log or perform actions when the database is created
                    .addCallback(DatabaseCallback())
                    // Uncomment the line below for destructive migration during schema changes
                    // .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        // Room Database Callback (optional)
        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Log or perform actions when the database is created
                println("Database has been created successfully!")
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                // Actions every time the database is opened
                println("Database has been opened!")
            }
        }
    }
}

