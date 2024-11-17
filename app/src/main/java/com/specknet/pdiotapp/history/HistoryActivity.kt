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
                val activityData = database.activityRecordDao().getAllActivities()
                val socialSignData = database.socialSignRecordDao().getAllSocialSigns()

                if (activityData.isNotEmpty() || socialSignData.isNotEmpty()) {
                    val groupedActivities = activityData.groupBy { record ->
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        dateFormat.format(record.timestamp)
                    }

                    val groupedSocialSigns = socialSignData.groupBy { record ->
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        dateFormat.format(record.timestamp)
                    }

                    val displayText = StringBuilder()

                    for (date in groupedActivities.keys.union(groupedSocialSigns.keys)) {
                        displayText.append("<b><big>Date: $date</big></b><br>")

                        // Display activities for this date
                        groupedActivities[date]?.let { activities ->
                            displayText.append("<b>Activities:</b><br>")
                            val activitySummary = activities.groupBy { it.activityLabel }
                                .mapValues { (_, records) -> records.size }

                            for ((activity, count) in activitySummary) {
                                val formattedActivity = activity.split("_").joinToString(" ") {
                                    it.lowercase().replaceFirstChar { char -> char.uppercase() }
                                }
                                val hours = count / 3600
                                val minutes = (count % 3600) / 60
                                val seconds = count % 60
                                displayText.append("$formattedActivity: ${hours.toString().padStart(2, '0')}h:" +
                                        "${minutes.toString().padStart(2, '0')}min:" +
                                        "${seconds.toString().padStart(2, '0')}s<br>")
                            }

                            displayText.append("<br>")
                        }

                        // Display social signs for this date
                        groupedSocialSigns[date]?.let { socialSigns ->
                            displayText.append("<b>Social Signs:</b><br>")
                            val socialSignSummary = socialSigns.groupBy { it.socialSignLabel }
                                .mapValues { (_, records) -> records.size }

                            for ((socialSign, count) in socialSignSummary) {
                                val hours = count / 3600
                                val minutes = (count % 3600) / 60
                                val seconds = count % 60
                                displayText.append("$socialSign: ${hours.toString().padStart(2, '0')}h:" +
                                        "${minutes.toString().padStart(2, '0')}min:" +
                                        "${seconds.toString().padStart(2, '0')}s<br>")
                            }
                        }

                       // displayText.append("<hr><br>")
                       // displayText.append("—".repeat(27) + "<br><br>")
                        displayText.append("\u2500".repeat(31) + "<br><br>") // 50 is the approximate line length

                    }

                    historyTextView.text = android.text.Html.fromHtml(displayText.toString())
                } else {
                    historyTextView.text = "No activity or social sign data available."
                }
            } catch (e: Exception) {
                historyTextView.text = "Error loading history."
                e.printStackTrace()
            }
        }
    }

    private fun filterHistoryByDate(query: String, historyTextView: TextView) {
        lifecycleScope.launch {
            try {
                val activityData = database.activityRecordDao().getAllActivities()
                val socialSignData = database.socialSignRecordDao().getAllSocialSigns()

                // Filter activities by partial match in the date
                val filteredActivities = activityData.filter { record ->
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val recordDate = dateFormat.format(record.timestamp)
                    recordDate.contains(query, ignoreCase = true) // Check if the query is in the date
                }

                // Filter social signs by partial match in the date
                val filteredSocialSigns = socialSignData.filter { record ->
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val recordDate = dateFormat.format(record.timestamp)
                    recordDate.contains(query, ignoreCase = true) // Check if the query is in the date
                }

                if (filteredActivities.isNotEmpty() || filteredSocialSigns.isNotEmpty()) {
                    val groupedActivities = filteredActivities.groupBy { record ->
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        dateFormat.format(record.timestamp)
                    }

                    val groupedSocialSigns = filteredSocialSigns.groupBy { record ->
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        dateFormat.format(record.timestamp)
                    }

                    val displayText = StringBuilder()
                    for (date in groupedActivities.keys.union(groupedSocialSigns.keys)) {
                        // Date display
                        displayText.append("<b><font size='6'>Date: $date</font></b><br>")

                        // Display activities for this date
                        groupedActivities[date]?.let { activities ->
                            displayText.append("<b>Activities:</b><br>")
                            val activitySummary = activities.groupBy { it.activityLabel }
                                .mapValues { (_, records) -> records.size }

                            for ((activity, count) in activitySummary) {
                                val formattedActivity = activity.split("_").joinToString(" ") {
                                    it.lowercase().replaceFirstChar { char -> char.uppercase() }
                                }
                                val hours = count / 3600
                                val minutes = (count % 3600) / 60
                                val seconds = count % 60
                                displayText.append(
                                    "$formattedActivity: ${hours.toString().padStart(2, '0')}h:" +
                                            "${minutes.toString().padStart(2, '0')}min:" +
                                            "${seconds.toString().padStart(2, '0')}s<br>"
                                )
                            }

                            displayText.append("<br>")
                        }

                        // Display social signs for this date
                        groupedSocialSigns[date]?.let { socialSigns ->
                            displayText.append("<b>Social Signs:</b><br>")
                            val socialSignSummary = socialSigns.groupBy { it.socialSignLabel }
                                .mapValues { (_, records) -> records.size }

                            for ((socialSign, count) in socialSignSummary) {
                                val hours = count / 3600
                                val minutes = (count % 3600) / 60
                                val seconds = count % 60
                                displayText.append(
                                    "$socialSign: ${hours.toString().padStart(2, '0')}h:" +
                                            "${minutes.toString().padStart(2, '0')}min:" +
                                            "${seconds.toString().padStart(2, '0')}s<br>"
                                )
                            }
                        }

                        displayText.append("\u2500".repeat(31) + "<br><br>")
                    }

                    historyTextView.text = android.text.Html.fromHtml(displayText.toString())
                } else {
                    historyTextView.text = "No data found matching '$query'."
                }
            } catch (e: Exception) {
                historyTextView.text = "Error loading filtered history."
                e.printStackTrace()
            }
        }
    }
}

