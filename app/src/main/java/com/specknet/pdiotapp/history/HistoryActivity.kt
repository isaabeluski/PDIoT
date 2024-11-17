package com.specknet.pdiotapp.history

import com.specknet.pdiotapp.R
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sleep_analysis)
        database = AppDatabase.getDatabase(this)

        val historyTextView: TextView = findViewById(R.id.history_text_view)

        // Fetch data from the database
        lifecycleScope.launch {
            val historyData = database.activityRecordDao().getAllActivities()

            // Display the data in the TextView
            if (historyData.isNotEmpty()) {
                val historyDisplay = historyData.joinToString("\n") {
                    "Activity: ${it.activityLabel}, Timestamp: ${it.timestamp}"
                }
                historyTextView.text = historyDisplay
            } else {
                historyTextView.text = "No activity data available."
            }
        }
    }

}