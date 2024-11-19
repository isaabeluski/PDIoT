package com.specknet.pdiotapp.history

import android.graphics.drawable.Drawable
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
import android.widget.CalendarView
import androidx.core.content.ContextCompat

class HistoryActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase
    private lateinit var calendarView: CalendarView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sleep_analysis)

        database = AppDatabase.getDatabase(this)

        val historyTextView: TextView = findViewById(R.id.history_text_view)
        calendarView = findViewById(R.id.calendar_view)

        // Set up CalendarView to fetch data for the selected date
        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val selectedDate = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
            fetchAndDisplayHistoryForDate(selectedDate, historyTextView)
        }

        // Display data for today's date by default
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        fetchAndDisplayHistoryForDate(today, historyTextView)
    }


    private fun fetchAndDisplayHistoryForDate(date: String, historyTextView: TextView) {
        lifecycleScope.launch {
            try {
                val activityData = database.activityRecordDao().getAllActivities()
                val socialSignData = database.socialSignRecordDao().getAllSocialSigns()

                // Filter data for the selected date
                val filteredActivities = activityData.filter {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    dateFormat.format(it.timestamp) == date
                }.map {
                    // Combine "sitting" and "standing" into "sitting_standing"
                    if (it.activityLabel == "sitting" || it.activityLabel == "standing") {
                        it.copy(activityLabel = "sitting_standing")
                    } else {
                        it
                    }
                }

                val filteredSocialSigns = socialSignData.filter {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    dateFormat.format(it.timestamp) == date
                }

                // Build display text
                if (filteredActivities.isNotEmpty() || filteredSocialSigns.isNotEmpty()) {
                    val displayText = StringBuilder()
                    displayText.append("<b><big>Date: $date</big></b><br>")

                    if (filteredActivities.isNotEmpty()) {
                        displayText.append("<b>Activities:</b><br>")
                        val activitySummary = filteredActivities.groupBy { it.activityLabel }
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

                    if (filteredSocialSigns.isNotEmpty()) {
                        displayText.append("<b>Social Signs:</b><br>")
                        val socialSignSummary = filteredSocialSigns.groupBy { it.socialSignLabel }
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

                    //displayText.append("\u2500".repeat(31) + "<br><br>") // Separator line
                    historyTextView.text = android.text.Html.fromHtml(displayText.toString())
                } else {
                    historyTextView.text = "No data available for $date."
                }
            } catch (e: Exception) {
                historyTextView.text = "Error loading history."
                e.printStackTrace()
            }
        }
    }
}