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
import com.specknet.pdiotapp.history.ActivityRecord
import com.specknet.pdiotapp.history.AppDatabase
import com.specknet.pdiotapp.utils.Constants
import com.specknet.pdiotapp.utils.RESpeckLiveData
import com.specknet.pdiotapp.utils.ThingyLiveData
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.collections.ArrayList
import org.tensorflow.lite.Interpreter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.specknet.pdiotapp.history.SocialSignRecord


class LiveDataActivity : AppCompatActivity() {


    // global graph variables
    lateinit var dataSet_res_accel_x: LineDataSet
    lateinit var dataSet_res_accel_y: LineDataSet
    lateinit var dataSet_res_accel_z: LineDataSet

    lateinit var dataSet_thingy_accel_x: LineDataSet
    lateinit var dataSet_thingy_accel_y: LineDataSet
    lateinit var dataSet_thingy_accel_z: LineDataSet

    private lateinit var activityDisplayTextView: TextView
    private lateinit var respiratoryConditionDisplayTextView: TextView
    private lateinit var activityIcon: ImageView
    private lateinit var respiratoryConditionIcon : ImageView

    // Room database
    private lateinit var database: AppDatabase


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

    private var lastActivity = ""
    private var lastRespiratoryCondition = ""
    private val updateInterval = 1000L
    private var lastActivityUpdateTime = System.currentTimeMillis()
    private var lastRespiratoryConditionUpdateTime = System.currentTimeMillis()

    val filterTestRespeck = IntentFilter(Constants.ACTION_RESPECK_LIVE_BROADCAST)
    val filterTestThingy = IntentFilter(Constants.ACTION_THINGY_BROADCAST)

    val respeckBufferActivity = ArrayList<FloatArray>()  // Buffer for Respeck data
    val respeckBufferRespiratory = ArrayList<FloatArray>()  // Buffer for Respeck data
    val thingyBuffer = ArrayList<FloatArray>()   // Buffer for Thingy data
    lateinit var detectedSittingStandingLabel: String
    lateinit var detectedActivityLabel: String
    private var lastThingyUpdateTime: Long = 0


    val WINDOW_SIZE_ACTIVITY = 100  // Define the window size as 50
    val WINDOW_SIZE_RESPIRATORY = 100  // Define the window size as 100
    val WINDOW_SIZE_THINGY = 50  // Define the window size as 50

    val activities = mapOf(
        0 to "Normal Walking",
        1 to "Descending Stairs",
        2 to "Lying Down on Back",
        3 to "Lying Down on Left",
        4 to "Lying Down on Right",
        5 to "Lying Down on Stomach",
        6 to "Miscellaneous",
        7 to "Normal Walking",
        8 to "Normal Running",
        9 to "Shuffle Walking",
        10 to "Sitting / Standing"
    )

//    val activities = mapOf(
//        0 to "Ascending Stairs",
//        1 to "Shuffle Walking",
//        2 to "Sitting / Standing",
//        3 to "Miscellaneous",
//        4 to "Normal Walking",
//        5 to "Normal Running",
//        6 to "Descending Stairs",
//        7 to "Lying Down on Right",
//        8 to "Lying Down on Left",
//        9 to "Lying Down on Stomach",
//        10 to "Lying Down on Back"
//    )

    val respiratoryConditions = mapOf(
        0 to "Normal",
        1 to "Coughing",
        2 to "Hyperventilation",
        3 to "Other"
    )

    val sittingStanding = mapOf(
        0 to "Sitting",
        1 to "Standing"
    )

    lateinit var interpreterActivity: Interpreter
    lateinit var interpreterRespiratory: Interpreter
    lateinit var interpreterSittingStanding: Interpreter

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

    private fun isThingyCollectingData(): Boolean {
        return System.currentTimeMillis() - lastThingyUpdateTime < 1000 // active if data is received in the last second
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_data)

        // Room database
        database = AppDatabase.getDatabase(this)

        activityDisplayTextView = findViewById(R.id.activity_display)
        respiratoryConditionDisplayTextView = findViewById(R.id.social_sign_display)
        activityIcon = findViewById(R.id.standing_icon)
        respiratoryConditionIcon = findViewById(R.id.normal_breathing_icon)
        Log.d("IconUpdate", "activityIcon initialized: ${activityIcon != null}")
        Log.d("IconUpdate", "Current drawable resource ID: ${activityIcon.drawable}")


        setupCharts()

        val modelActivity = loadModelFile("respeck_10_120epochs_100windowsize_2layers.tflite")
        interpreterActivity = Interpreter(modelActivity) // Initialize interpreter here
        val modelRespiratory = loadModelFile("model_respiratory.tflite")
        interpreterRespiratory = Interpreter(modelRespiratory) // Initialize interpreter here
        val modelSittingStanding = loadModelFile("thingy_3_sittingStanding_100epochs.tflite")
        interpreterSittingStanding = Interpreter(modelSittingStanding) // Initialize interpreter here

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
                    respeckBufferActivity.add(respeckData)
                    respeckBufferRespiratory.add(respeckData)

                    Log.d("Live", "onReceive: respeckBuffer = " + respeckBufferActivity.size)

                    if (respeckBufferActivity.size >= WINDOW_SIZE_ACTIVITY) {
                        // Currently, buffer is of size (50, 3)
                        // We need to convert it to (1, 50, 3)

                        // Create the input and output arrays
                        val inputActivity = Array(1) { Array(WINDOW_SIZE_ACTIVITY) { FloatArray(3) } }

                        // Convert the buffer to the input array
                        for (i in 0 until WINDOW_SIZE_ACTIVITY) {
                            inputActivity[0][i][0] = respeckBufferActivity[i][0]
                            inputActivity[0][i][1] = respeckBufferActivity[i][1]
                            inputActivity[0][i][2] = respeckBufferActivity[i][2]
                        }

                        // Create the output array([ 1, 11], dtype=int32)
                        val outputActivity = Array(1) { FloatArray(11) }

                        // Run the model
                        interpreterActivity.run(inputActivity, outputActivity)

                        // Get the detected activity index
                        val detectedActivityIndex = getDetectedActivityIndex(outputActivity[0])
                        detectedActivityLabel = activities[detectedActivityIndex] ?: "Unknown Activity"


                        // Remove the first element from the buffer
                        respeckBufferActivity.removeAt(0)


                        if(detectedActivityLabel == "Sitting / Standing" && isThingyCollectingData()) {

                            while (isThingyCollectingData()) {
                                if (thingyBuffer.size >= WINDOW_SIZE_THINGY) {

//                                if the thingy is connected, run the model
                                    Log.d(
                                        "Detected update",
                                        "Thingy buffer size: ${thingyBuffer.size}"
                                    )

                                    updateDetectedActivity(detectedSittingStandingLabel)

                                    // Print the detected activity
                                    //                                Log.d("Detected Sitting Standing", detectedSittingStandingIndex.toString())
                                    Log.d(
                                        "Detected Sitting Standing",
                                        "from Respeck site. Label: $detectedSittingStandingLabel"
                                    )

                                    break
                                }
                            }
                        }
                        else {
                            Log.d("Detected update", "Detected activity: $detectedActivityLabel")
                            updateDetectedActivity(detectedActivityLabel)
                        }

                        // Print the detected activity
                        Log.d("Detected Activity", detectedActivityIndex.toString())
                        Log.d("Detected Activity", detectedActivityLabel)

                    }

                    if (respeckBufferRespiratory.size >= WINDOW_SIZE_RESPIRATORY) {
                        // Currently, buffer is of size (100, 3)
                        // We need to convert it to (1, 100, 3)

//                        If the detected activity index matches with classes "Ascending Stairs", "Descending Stairs", "Normal Walking", "Normal Running", "Shuffle Walking" or "Miscellaneous", the respiratory label is "Normal". Else, run the model to detect the respiratory condition.
                        if(detectedActivityLabel == "Ascending Stairs" ||
                            detectedActivityLabel == "Descending Stairs" ||
                            detectedActivityLabel == "Normal Walking" ||
                            detectedActivityLabel == "Normal Running" ||
                            detectedActivityLabel == "Shuffle Walking" ||
                            detectedActivityLabel == "Miscellaneous") {
                            updateDetectedRespiratoryCondition("Normal")
                        }
                        else {

                            // Create the input and output arrays
                            val inputRespiratory =
                                Array(1) { Array(WINDOW_SIZE_RESPIRATORY) { FloatArray(3) } }

                            // Convert the buffer to the input array
                            for (i in 0 until WINDOW_SIZE_RESPIRATORY) {
                                inputRespiratory[0][i][0] = respeckBufferRespiratory[i][0]
                                inputRespiratory[0][i][1] = respeckBufferRespiratory[i][1]
                                inputRespiratory[0][i][2] = respeckBufferRespiratory[i][2]
                            }

                            // Create the output array([ 1, 4], dtype=int32)
                            val outputRespiratory = Array(1) { FloatArray(4) }

                            // Run the model
                            interpreterRespiratory.run(inputRespiratory, outputRespiratory)

                            // Get the detected respiratory condition index
                            val detectedRespiratoryIndex =
                                getDetectedActivityIndex(outputRespiratory[0])
                            val detectedRespiratoryLabel =
                                respiratoryConditions[detectedRespiratoryIndex]
                                    ?: "Unknown Respiratory Condition"

                            // Remove the first element from the buffer
                            for (i in 0 until 10) {
                            respeckBufferRespiratory.removeAt(0)
                            }
                            updateDetectedRespiratoryCondition(detectedRespiratoryLabel)

                            // Print the detected respiratory condition
                            Log.d(
                                "Detected Respiratory Condition",
                                detectedRespiratoryIndex.toString()
                            )
                            Log.d("Detected Respiratory Condition", detectedRespiratoryLabel)
                        }

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

                    lastThingyUpdateTime = System.currentTimeMillis()

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

                    if (thingyBuffer.size >= WINDOW_SIZE_THINGY) {
                        // Currently, buffer is of size (50, 3)
                        // We need to convert it to (1, 50, 3)

                        // Create the input and output arrays
                        val inputSittingStanding =
                            Array(1) { Array(WINDOW_SIZE_THINGY) { FloatArray(3) } }

                        // Convert the buffer to the input array
                        for (i in 0 until WINDOW_SIZE_THINGY) {
                            inputSittingStanding[0][i][0] = thingyBuffer[i][0]
                            inputSittingStanding[0][i][1] = thingyBuffer[i][1]
                            inputSittingStanding[0][i][2] = thingyBuffer[i][2]
                        }

                        // Create the output array([ 1, 2], dtype=int32)
                        val outputSittingStanding = Array(1) { FloatArray(2) }

                        // Run the model
                        interpreterSittingStanding.run(inputSittingStanding, outputSittingStanding)

                        // Get the detected activity index
                        val detectedSittingStandingIndex = getDetectedActivityIndex(outputSittingStanding[0])

                        detectedSittingStandingLabel = sittingStanding[detectedSittingStandingIndex] ?: "Unknown Sitting Standing"
//
//                        updateDetectedActivity(detectedSittingStandingLabel)

//                        Remove the first element from the buffer
                        thingyBuffer.removeAt(0)

//                         Print the detected activity
//                        Log.d("Detected Sitting Standing", "from Thingy site. Index: $detectedSittingStandingIndex")
//                        Log.d("Detected Sitting Standing", "from Thingy site. Label: $detectedSittingStandingLabel")

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


    private fun updateDetectedActivity(activityLabel: String) {
        val currentTime = System.currentTimeMillis()

        // room database
        val record = ActivityRecord(activityLabel = activityLabel, timestamp = currentTime)

        lifecycleScope.launch {
            database.activityRecordDao().insertActivity(record)
        }

        if (activityLabel != lastActivity && currentTime - lastActivityUpdateTime >= updateInterval) {
            runOnUiThread {
                activityDisplayTextView.text = "Current Activity: $activityLabel"

                // Update the icon based on the activity label
                val drawable = when (activityLabel) {
                    "Sitting / Standing" -> ContextCompat.getDrawable(this, R.drawable.sitting)
                    "Sitting" -> ContextCompat.getDrawable(this, R.drawable.sitting)
                    "Standing" -> ContextCompat.getDrawable(this, R.drawable.man_standing)
                    "Normal Walking" -> ContextCompat.getDrawable(this, R.drawable.walking)
                    "Normal Running" -> ContextCompat.getDrawable(this, R.drawable.running)
                    "Ascending Stairs" -> ContextCompat.getDrawable(this, R.drawable.ascending_stairs)
                    "Descending Stairs" -> ContextCompat.getDrawable(this, R.drawable.descending_stairs)
                    "Lying Down on Back" -> ContextCompat.getDrawable(this, R.drawable.lying_down_on_back)
                    "Lying Down on Left" -> ContextCompat.getDrawable(this, R.drawable.lying_down_left)
                    "Lying Down on Right" -> ContextCompat.getDrawable(this, R.drawable.lying_down_on_right)
                    "Lying Down on Stomach" -> ContextCompat.getDrawable(this, R.drawable.lying_down_stomach)
                    "Miscellaneous" -> ContextCompat.getDrawable(this, R.drawable.miscellaneous)
                    "Shuffle Walking" -> ContextCompat.getDrawable(this, R.drawable.shuffle_walking)
                    else -> ContextCompat.getDrawable(this, R.drawable.man_standing) // Default drawable
                }
                activityIcon.setImageDrawable(drawable)
                activityIcon.invalidate()
                activityIcon.requestLayout()
            }
            // Update tracking variables
            lastActivity = activityLabel
            lastActivityUpdateTime = currentTime
        }
    }

    private fun updateDetectedRespiratoryCondition(respiratoryConditionLabel: String) {
        val currentTime = System.currentTimeMillis()
        val socialSignRecord = SocialSignRecord(
            socialSignLabel = respiratoryConditionLabel,
            timestamp = currentTime
        )

        lifecycleScope.launch {
            database.socialSignRecordDao().insertSocialSign(socialSignRecord)
        }
        if (respiratoryConditionLabel != lastRespiratoryCondition && currentTime - lastRespiratoryConditionUpdateTime >= updateInterval) {
            runOnUiThread {
                respiratoryConditionDisplayTextView.text = "Current Respiratory Condition: $respiratoryConditionLabel"

                // Update the icon based on the respiratory condition label
                val drawable = when (respiratoryConditionLabel) {
                    "Coughing" -> ContextCompat.getDrawable(this, R.drawable.coughing)
                    "Hyperventilation" -> ContextCompat.getDrawable(this, R.drawable.hyperventilating)
                    "Other" -> ContextCompat.getDrawable(this, R.drawable.singing)
                    else -> ContextCompat.getDrawable(this, R.drawable.normal_breathing) // Default drawable
                }
                respiratoryConditionIcon.setImageDrawable(drawable)
                respiratoryConditionIcon.invalidate()
                respiratoryConditionIcon.requestLayout()
            }
            // Update tracking variables
            lastRespiratoryCondition = respiratoryConditionLabel
            lastRespiratoryConditionUpdateTime = currentTime
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