package com.specknet.pdiotapp.history

import com.specknet.pdiotapp.R
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import android.text.Editable
import android.text.TextWatcher

class HistoryActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sleep_analysis)

        database = AppDatabase.getDatabase(this)

        val historyTextView: TextView = findViewById(R.id.history_text_view)
        val searchBar: EditText = findViewById(R.id.search_bar)

        // Fetch and display all history initially
        fetchAndDisplayHistory()

        // Add search functionality
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString()
                if (query.isNotEmpty()) {
                    filterHistoryByDate(query, historyTextView)
                } else {
                    fetchAndDisplayHistory() // Reset to all history if search bar is empty
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun fetchAndDisplayHistory() {
        val historyTextView: TextView = findViewById(R.id.history_text_view)

        lifecycleScope.launch {
            try {
                val historyData = database.activityRecordDao().getAllActivities()

                if (historyData.isNotEmpty()) {
                    val groupedData = historyData.groupBy { record ->
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        dateFormat.format(record.timestamp)
                    }

                    val displayText = StringBuilder()
                    for ((date, activities) in groupedData) {
                        displayText.append("<b>Date: $date</b><br>")

                        val activitySummary = activities.groupBy { it.activityLabel }
                            .mapValues { (_, records) -> records.size }

                        for ((activity, count) in activitySummary) {
                            val formattedActivity = activity.split("_").joinToString(" ") {
                                it.lowercase().replaceFirstChar { char -> char.uppercase() }
                            }
                            val hours = count / 3600
                            val minutes = (count % 3600) / 60
                            val seconds = count % 60

                            // Format as "00h:00min:00s"
                            displayText.append("$formattedActivity: ${hours.toString().padStart(2, '0')}h:" +
                                    "${minutes.toString().padStart(2, '0')}min:" +
                                    "${seconds.toString().padStart(2, '0')}s<br>")
                        }

                        displayText.append("<hr><br>")
                    }

                    historyTextView.text = android.text.Html.fromHtml(displayText.toString())
                } else {
                    historyTextView.text = "No activity data available."
                }
            } catch (e: Exception) {
                historyTextView.text = "Error loading history."
                e.printStackTrace()
            }
        }
    }

    private fun filterHistoryByDate(date: String, historyTextView: TextView) {
        lifecycleScope.launch {
            try {
                val historyData = database.activityRecordDao().getAllActivities()
                val filteredData = historyData.filter { record ->
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val recordDate = dateFormat.format(record.timestamp)
                    recordDate == date
                }

                if (filteredData.isNotEmpty()) {
                    val groupedData = filteredData.groupBy { record ->
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        dateFormat.format(record.timestamp)
                    }

                    val displayText = StringBuilder()
                    for ((date, activities) in groupedData) {
                        displayText.append("<b>Date: $date</b><br>")

                        val activitySummary = activities.groupBy { it.activityLabel }
                            .mapValues { (_, records) -> records.size }

                        for ((activity, count) in activitySummary) {
                            val formattedActivity = activity.split("_").joinToString(" ") {
                                it.lowercase().replaceFirstChar { char -> char.uppercase() }
                            }
                            val minutes = count / 60
                            val seconds = count % 60
                            displayText.append("$formattedActivity: $minutes minutes and $seconds seconds<br>")
                        }

                        displayText.append("<br>")
                    }

                    historyTextView.text = android.text.Html.fromHtml(displayText.toString())
                } else {
                    historyTextView.text = "No data found for $date."
                }
            } catch (e: Exception) {
                historyTextView.text = "Error loading filtered history."
                e.printStackTrace()
            }
        }
    }
}

