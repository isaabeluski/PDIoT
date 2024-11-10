package com.specknet.pdiotapp.live

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.AssetFileDescriptor
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.specknet.pdiotapp.R
import com.specknet.pdiotapp.utils.Constants
import com.specknet.pdiotapp.utils.RESpeckLiveData
import com.specknet.pdiotapp.utils.ThingyLiveData
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.collections.ArrayList
import org.tensorflow.lite.Interpreter


class LiveDataActivity : AppCompatActivity() {


    // global graph variables
    lateinit var dataSet_res_accel_x: LineDataSet
    lateinit var dataSet_res_accel_y: LineDataSet
    lateinit var dataSet_res_accel_z: LineDataSet

    lateinit var dataSet_thingy_accel_x: LineDataSet
    lateinit var dataSet_thingy_accel_y: LineDataSet
    lateinit var dataSet_thingy_accel_z: LineDataSet

    private lateinit var activityDisplayTextView: TextView
    private lateinit var activityIcon: ImageView

    var time = 0f
    lateinit var allRespeckData: LineData

    lateinit var allThingyData: LineData

    lateinit var respeckChart: LineChart
    lateinit var thingyChart: LineChart

    // global broadcast receiver so we can unregister it
    lateinit var respeckLiveUpdateReceiver: BroadcastReceiver
    lateinit var thingyLiveUpdateReceiver: BroadcastReceiver
    lateinit var looperRespeck: Looper
    lateinit var looperThingy: Looper

    private var lastUpdateTime = System.currentTimeMillis()
    private val updateInterval = 1000L

    val filterTestRespeck = IntentFilter(Constants.ACTION_RESPECK_LIVE_BROADCAST)
    val filterTestThingy = IntentFilter(Constants.ACTION_THINGY_BROADCAST)

    val respeckBuffer = ArrayList<FloatArray>()  // Buffer for Respeck data
    val thingyBuffer = ArrayList<FloatArray>()   // Buffer for Thingy data

    val WINDOW_SIZE = 100  // Define the window size as 50

    val activities = mapOf(
        0 to "Ascending stairs",
        1 to "Descending stairs",
        2 to "Lying down back",
        3 to "Lying down left",
        4 to "Lying down right ",
        5 to "Lying down stomach",
        6 to "Miscellaneous",
        7 to "Walking",
        8 to "Running",
        9 to "Shuffle walking",
        10 to "Sitting / Standing"
    )

    val respiratoryConditions = mapOf(
        0 to "Normal",
        1 to "Coughing",
        2 to "Hyperventilation",
        3 to "Other"
    )

    lateinit var interpreterActivity: Interpreter
    lateinit var interpreterRespiratory: Interpreter

    // Function to load the model file taken the model file name as input
    fun loadModelFile(modelFileName: String): MappedByteBuffer {
        val fileDescriptor: AssetFileDescriptor = assets.openFd(modelFileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel: FileChannel = inputStream.channel
        val startOffset: Long = fileDescriptor.startOffset
        val declaredLength: Long = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun getDetectedActivityIndex(output: FloatArray): Int {
        var maxProbability = -1f
        var activityIndex = -1
        for (i in output.indices) {
            if (output[i] > maxProbability) {
                maxProbability = output[i]
                activityIndex = i
            }
        }
        return activityIndex // Returns the index of the highest probability
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_data)

        activityDisplayTextView = findViewById(R.id.activity_display)
        activityIcon = findViewById(R.id.standing_icon)
        Log.d("IconUpdate", "activityIcon initialized: ${activityIcon != null}")
        Log.d("IconUpdate", "Current drawable resource ID: ${activityIcon.drawable}")


        setupCharts()

        val modelActivity = loadModelFile("respeck_6_100epochs_100windowsize_4layers.tflite")
        interpreterActivity = Interpreter(modelActivity) // Initialize interpreter here
        val modelRespiratory = loadModelFile("model_respiratory.tflite")
        interpreterRespiratory = Interpreter(modelRespiratory) // Initialize interpreter here

        // set up the broadcast receiver
        respeckLiveUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                Log.i("thread", "I am running on thread = " + Thread.currentThread().name)

                val action = intent.action

                if (action == Constants.ACTION_RESPECK_LIVE_BROADCAST) {

                    val liveData =
                        intent.getSerializableExtra(Constants.RESPECK_LIVE_DATA) as RESpeckLiveData
                    Log.d("Live", "onReceive: liveData = " + liveData)

                    // get all relevant intent contents
                    val x = liveData.accelX
                    val y = liveData.accelY
                    val z = liveData.accelZ

                    time += 1
                    updateGraph("respeck", x, y, z)

                    val respeckData = floatArrayOf(x, y, z)
                    respeckBuffer.add(respeckData)

                    Log.d("Live", "onReceive: respeckBuffer = " + respeckBuffer.size)

                    if (respeckBuffer.size >= WINDOW_SIZE) {
                        // Currently, buffer is of size (50, 3)
                        // We need to convert it to (1, 50, 3)

                        // Create the input and output arrays
                        val input = Array(1) { Array(WINDOW_SIZE) { FloatArray(3) } }

                        // Convert the buffer to the input array
                        for (i in 0 until WINDOW_SIZE) {
                            input[0][i][0] = respeckBuffer[i][0]
                            input[0][i][1] = respeckBuffer[i][1]
                            input[0][i][2] = respeckBuffer[i][2]
                        }

                        // Create the output array([ 1, 11], dtype=int32)
                        val outputActivity = Array(1) { FloatArray(11) }
                        val outputRespiratory = Array(1) { FloatArray(4) }

                        // Run the model
                        interpreterActivity.run(input, outputActivity)
                        interpreterRespiratory.run(input, outputRespiratory)

                        // Get the detected activity index
                        val detectedActivityIndex = getDetectedActivityIndex(outputActivity[0])
                        val detectedActivityLabel = activities[detectedActivityIndex] ?: "Unknown Activity"

                        val detectedRespiratoryIndex = getDetectedActivityIndex(outputRespiratory[0])
                        val detectedRespiratoryLabel = respiratoryConditions[detectedRespiratoryIndex] ?: "Unknown Respiratory Condition"
                        // TODO need to display the respiratory condition in the app @Aloia

                        // Remove the first element from the buffer
                        respeckBuffer.removeAt(0)


                        displayDetectedActivity(detectedActivityLabel)
                        updateIconBasedOnActivity(detectedActivityLabel)
                        // Print the detected activity
                        Log.d("Detected Activity", detectedActivityIndex.toString())
                        Log.d("Detected Activity", detectedActivityLabel)
                        Log.d("Detected Respiratory", detectedRespiratoryIndex.toString())
                        Log.d("Detected Respiratory", detectedRespiratoryLabel)
                    }
                }
            }
        }

        // register receiver on another thread
        val handlerThreadRespeck = HandlerThread("bgThreadRespeckLive")
        handlerThreadRespeck.start()
        looperRespeck = handlerThreadRespeck.looper
        val handlerRespeck = Handler(looperRespeck)
        this.registerReceiver(respeckLiveUpdateReceiver, filterTestRespeck, null, handlerRespeck)


        // set up the broadcast receiver
        thingyLiveUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                Log.i("thread", "I am running on thread = " + Thread.currentThread().name)

                val action = intent.action

                if (action == Constants.ACTION_THINGY_BROADCAST) {

                    val liveData =
                        intent.getSerializableExtra(Constants.THINGY_LIVE_DATA) as ThingyLiveData
                    Log.d("Live", "onReceive: liveData = " + liveData)

                    // get all relevant intent contents
                    val x = liveData.accelX
                    val y = liveData.accelY
                    val z = liveData.accelZ

                    time += 1
                    updateGraph("thingy", x, y, z)


                    val thingyData = floatArrayOf(x, y, z)
                    thingyBuffer.add(thingyData)

                    Log.d("Live", "onReceive: thingyBuffer = " + thingyBuffer.size)

                    if (thingyBuffer.size >= WINDOW_SIZE) {
                        // Currently, buffer is of size (50, 3)
                        // We need to convert it to (1, 50, 3)

                        // Create the input and output arrays
                        val input = Array(1) { Array(WINDOW_SIZE) { FloatArray(3) } }

                        // Convert the buffer to the input array
                        for (i in 0 until WINDOW_SIZE) {
                            input[0][i][0] = thingyBuffer[i][0]
                            input[0][i][1] = thingyBuffer[i][1]
                            input[0][i][2] = thingyBuffer[i][2]
                        }

                        // Create the output array([ 1, 11], dtype=int32)
                        val output = Array(1) { FloatArray(11) }

                        // Run the model
                        interpreterActivity.run(input, output)

                        // Get the detected activity index
                        val detectedActivityIndex = getDetectedActivityIndex(output[0])
                        val detectedActivityLabel = activities[detectedActivityIndex] ?: "Unknown Activity"

                        // Remove the first element from the buffer
                        thingyBuffer.removeAt(0)

                        displayDetectedActivity(detectedActivityLabel)
//                        updateIconBasedOnActivity(detectedActivityLabel)
//
//                        // Print the detected activity
                        Log.d("Detected Activity Thingy", detectedActivityIndex.toString())
                        Log.d("Detected Activity Thingy", detectedActivityLabel)
                    }
                }
            }
        }

        // register receiver on another thread
        val handlerThreadThingy = HandlerThread("bgThreadThingyLive")
        handlerThreadThingy.start()
        looperThingy = handlerThreadThingy.looper
        val handlerThingy = Handler(looperThingy)
        this.registerReceiver(thingyLiveUpdateReceiver, filterTestThingy, null, handlerThingy)

    }


    private fun displayDetectedActivity(activityLabel: String) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime >= updateInterval) {
            runOnUiThread {
                activityDisplayTextView.text = "Current Activity: $activityLabel"
            }
            lastUpdateTime = currentTime
        }
    }

    private var lastActivity = ""

    private fun updateIconBasedOnActivity(activity: String) {
        val currentTime = System.currentTimeMillis()
        Log.d("IconUpdate", "Updating icon for activity: $activity")

        Log.d("IconUpdate", "Checking condition: activity != lastActivity -> ${activity != lastActivity}")
        //Log.d("IconUpdate", "Checking condition: currentTime - lastUpdateTime >= updateInterval -> ${currentTime - lastUpdateTime >= updateInterval}")
        // Only update if activity changes or if at least 1 second has passed since the last update
        if (activity != lastActivity) {
            runOnUiThread {
                Log.d("IconUpdate", "Setting icon for: $activity")
                val drawable = when (activity) {
                    "sitting_standing" -> ContextCompat.getDrawable(this, R.drawable.sitting)
                    "normal_walking" -> ContextCompat.getDrawable(this, R.drawable.walking)
                    "running_normal" -> ContextCompat.getDrawable(this, R.drawable.running)
                    //"standing" -> ContextCompat.getDrawable(this, R.drawable.man_standing)
                    "ascending_stairs" -> ContextCompat.getDrawable(this, R.drawable.ascending_stairs)
                    "descending_stairs" -> ContextCompat.getDrawable(this, R.drawable.descending_stairs)
                    "lying_down_back" -> ContextCompat.getDrawable(this, R.drawable.lying_down_on_back)
                    "lying_down_left" -> ContextCompat.getDrawable(this, R.drawable.lying_down_left)
                    "lying_down_right" -> ContextCompat.getDrawable(this, R.drawable.lying_down_on_right)
                    "lying_down_stomach" -> ContextCompat.getDrawable(this, R.drawable.lying_down_stomach)
                    "misc_movement" -> ContextCompat.getDrawable(this, R.drawable.miscellaneous)
                    "shuffle_walking" -> ContextCompat.getDrawable(this, R.drawable.shuffle_walking)
                    else -> ContextCompat.getDrawable(this, R.drawable.man_standing) // Default drawable
                }

                if (drawable != null) {
                    activityIcon.setImageDrawable(drawable)
                    activityIcon.invalidate()  // Force redraw
                    activityIcon.requestLayout() // Request a layout pass
                }

                Log.d("IconUpdate", "Drawable constant state after update: ${activityIcon.drawable?.constantState}")
                // Update tracking variables
                lastActivity = activity
                lastUpdateTime = currentTime
            }
        }
    }

    fun setupCharts() {
        respeckChart = findViewById(R.id.respeck_chart)
        thingyChart = findViewById(R.id.thingy_chart)

        // Respeck

        time = 0f
        val entries_res_accel_x = ArrayList<Entry>()
        val entries_res_accel_y = ArrayList<Entry>()
        val entries_res_accel_z = ArrayList<Entry>()

        dataSet_res_accel_x = LineDataSet(entries_res_accel_x, "Accel X")
        dataSet_res_accel_y = LineDataSet(entries_res_accel_y, "Accel Y")
        dataSet_res_accel_z = LineDataSet(entries_res_accel_z, "Accel Z")

        dataSet_res_accel_x.setDrawCircles(false)
        dataSet_res_accel_y.setDrawCircles(false)
        dataSet_res_accel_z.setDrawCircles(false)

        dataSet_res_accel_x.setColor(
            ContextCompat.getColor(
                this,
                R.color.red
            )
        )
        dataSet_res_accel_y.setColor(
            ContextCompat.getColor(
                this,
                R.color.green
            )
        )
        dataSet_res_accel_z.setColor(
            ContextCompat.getColor(
                this,
                R.color.blue
            )
        )

        val dataSetsRes = ArrayList<ILineDataSet>()
        dataSetsRes.add(dataSet_res_accel_x)
        dataSetsRes.add(dataSet_res_accel_y)
        dataSetsRes.add(dataSet_res_accel_z)

        allRespeckData = LineData(dataSetsRes)
        respeckChart.data = allRespeckData
        respeckChart.invalidate()

        // Thingy

        time = 0f
        val entries_thingy_accel_x = ArrayList<Entry>()
        val entries_thingy_accel_y = ArrayList<Entry>()
        val entries_thingy_accel_z = ArrayList<Entry>()

        dataSet_thingy_accel_x = LineDataSet(entries_thingy_accel_x, "Accel X")
        dataSet_thingy_accel_y = LineDataSet(entries_thingy_accel_y, "Accel Y")
        dataSet_thingy_accel_z = LineDataSet(entries_thingy_accel_z, "Accel Z")

        dataSet_thingy_accel_x.setDrawCircles(false)
        dataSet_thingy_accel_y.setDrawCircles(false)
        dataSet_thingy_accel_z.setDrawCircles(false)

        dataSet_thingy_accel_x.setColor(
            ContextCompat.getColor(
                this,
                R.color.red
            )
        )
        dataSet_thingy_accel_y.setColor(
            ContextCompat.getColor(
                this,
                R.color.green
            )
        )
        dataSet_thingy_accel_z.setColor(
            ContextCompat.getColor(
                this,
                R.color.blue
            )
        )

        val dataSetsThingy = ArrayList<ILineDataSet>()
        dataSetsThingy.add(dataSet_thingy_accel_x)
        dataSetsThingy.add(dataSet_thingy_accel_y)
        dataSetsThingy.add(dataSet_thingy_accel_z)

        allThingyData = LineData(dataSetsThingy)
        thingyChart.data = allThingyData
        thingyChart.invalidate()
    }

    fun updateGraph(graph: String, x: Float, y: Float, z: Float) {
        // take the first element from the queue
        // and update the graph with it
        if (graph == "respeck") {
            dataSet_res_accel_x.addEntry(Entry(time, x))
            dataSet_res_accel_y.addEntry(Entry(time, y))
            dataSet_res_accel_z.addEntry(Entry(time, z))

            runOnUiThread {
                allRespeckData.notifyDataChanged()
                respeckChart.notifyDataSetChanged()
                respeckChart.invalidate()
                respeckChart.setVisibleXRangeMaximum(150f)
                respeckChart.moveViewToX(respeckChart.lowestVisibleX + 40)
            }
        } else if (graph == "thingy") {
            dataSet_thingy_accel_x.addEntry(Entry(time, x))
            dataSet_thingy_accel_y.addEntry(Entry(time, y))
            dataSet_thingy_accel_z.addEntry(Entry(time, z))

            runOnUiThread {
                allThingyData.notifyDataChanged()
                thingyChart.notifyDataSetChanged()
                thingyChart.invalidate()
                thingyChart.setVisibleXRangeMaximum(150f)
                thingyChart.moveViewToX(thingyChart.lowestVisibleX + 40)
            }
        }


    }


    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(respeckLiveUpdateReceiver)
        unregisterReceiver(thingyLiveUpdateReceiver)
        looperRespeck.quit()
        looperThingy.quit()
    }
}