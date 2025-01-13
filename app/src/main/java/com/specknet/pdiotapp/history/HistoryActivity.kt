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
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter

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
        val barChart: BarChart = findViewById(R.id.activity_bar_chart) // Add BarChart reference

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

                    val barEntries = mutableListOf<BarEntry>() // Entries for BarChart
                    val activityLabels = mutableListOf<String>() // Labels for activities
                    var index = 0

                    if (filteredActivities.isNotEmpty()) {
                        displayText.append("<b>Activities:</b><br>")
                        val activitySummary = filteredActivities.groupBy { it.activityLabel }
                            .mapValues { (_, records) -> records.size }

                        for ((activity, count) in activitySummary) {
                            val formattedActivity = activity.split("_").joinToString(" ") {
                                it.lowercase().replaceFirstChar { char -> char.uppercase() }
                            }
                            val count_seconds = count / 25
                            val hours = count_seconds / 3600
                            val minutes = (count_seconds % 3600) / 60
                            val seconds = count_seconds % 60

                            displayText.append(
                                "$formattedActivity: ${hours.toString().padStart(2, '0')}h:" +
                                        "${minutes.toString().padStart(2, '0')}min:" +
                                        "${seconds.toString().padStart(2, '0')}s<br>"
                            )

                            // Add data to BarChart
                            val totalMinutes = (hours * 60) + minutes + (seconds / 60.0).toFloat()
                            barEntries.add(BarEntry(index.toFloat(), totalMinutes))
                            activityLabels.add(formattedActivity)
                            index++
                        }
                        displayText.append("<br>")
                    }

                    if (filteredSocialSigns.isNotEmpty()) {
                        displayText.append("<b>Social Signs:</b><br>")
                        val socialSignSummary = filteredSocialSigns.groupBy { it.socialSignLabel }
                            .mapValues { (_, records) -> records.size }

                        for ((socialSign, count) in socialSignSummary) {
                            val count_seconds = count / 25
                            val hours = count_seconds / 3600
                            val minutes = (count_seconds % 3600) / 60
                            val seconds = count_seconds % 60
                            displayText.append(
                                "$socialSign: ${hours.toString().padStart(2, '0')}h:" +
                                        "${minutes.toString().padStart(2, '0')}min:" +
                                        "${seconds.toString().padStart(2, '0')}s<br>"
                            )
                        }
                    }

                    historyTextView.text = android.text.Html.fromHtml(displayText.toString())

                    // Set up BarChart
                    if (barEntries.isNotEmpty()) {
                        val dataSet = BarDataSet(barEntries, "Activity Durations")
                        dataSet.color = resources.getColor(R.color.teal_700, theme) // Set bar color

                        val barData = BarData(dataSet)
                        barData.barWidth = 0.9f // Set bar width

                        barChart.data = barData
                        val xAxis = barChart.xAxis
                        xAxis.position = XAxis.XAxisPosition.BOTTOM
                        xAxis.valueFormatter = IndexAxisValueFormatter(activityLabels)
                        xAxis.granularity = 1f
                        xAxis.isGranularityEnabled = true
                        xAxis.labelRotationAngle = 90f // Rotate labels for readability

                        // Configure Y-Axis
                        barChart.axisLeft.axisMinimum = 0f
                        barChart.axisRight.isEnabled = false

                        barChart.data.setDrawValues(false)

                        // Remove legend
                        barChart.legend.isEnabled = false

                        // Additional configurations
                        barChart.description.isEnabled = false
                        barChart.setFitBars(true)
                        barChart.invalidate() // Refresh chart
                    } else {
                        barChart.clear()
                    }
                } else {
                    historyTextView.text = "No data available for $date."
                    barChart.clear()
                }
            } catch (e: Exception) {
                historyTextView.text = "Error loading history."
                e.printStackTrace()
                barChart.clear()
            }
        }
    }
}