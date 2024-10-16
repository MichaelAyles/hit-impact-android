package com.example.blesensorviewer

import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.max

class ImpactDataActivity : AppCompatActivity() {

    private lateinit var bluetooth: Bluetooth
    private lateinit var currentValueTextView: TextView
    private lateinit var peakValueTextView: TextView
    private lateinit var tbiWarningTextView: TextView
    private lateinit var toggleReadingButton: Button
    private lateinit var reconnectButton: Button
    private lateinit var logTextView: TextView
    private lateinit var exportButton: Button
    private lateinit var chart: LineChart

    private val serviceUUID = "2b187dea-3f94-41e0-9d41-7d18a47f38fe"
    private var selectedCharacteristic: BluetoothGattCharacteristic? = null

    private var isReading = false
    private val handler = Handler(Looper.getMainLooper())
    private val readInterval = 50L // 50ms
    private var lastReadTime = 0L
    private var peakValue = 0
    private var currentValue = 0
    private val tbiThreshold = 60
    private val logEntries = CopyOnWriteArrayList<Pair<Long, Int>>()

    private val chartUpdateInterval = 100L // 100ms
    private var lastChartUpdateTime = 0L

    companion object {
        private const val TAG = "ImpactDataActivity"
        private const val MAX_DATA_POINTS = 1000 // Maximum number of data points to show on the chart
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_impact_data)

        bluetooth = Bluetooth.getInstance()

        currentValueTextView = findViewById(R.id.currentValueTextView)
        peakValueTextView = findViewById(R.id.peakValueTextView)
        tbiWarningTextView = findViewById(R.id.tbiWarningTextView)
        toggleReadingButton = findViewById(R.id.toggleReadingButton)
        reconnectButton = findViewById(R.id.reconnectButton)
        logTextView = findViewById(R.id.logTextView)
        exportButton = findViewById(R.id.exportButton)
        chart = findViewById(R.id.chart)

        toggleReadingButton.setOnClickListener {
            toggleReading()
        }

        reconnectButton.setOnClickListener {
            reconnect()
        }

        exportButton.setOnClickListener {
            exportToCsv()
        }

        bluetooth.onCharacteristicChanged = { characteristic ->
            handleCharacteristicChange(characteristic)
        }

        bluetooth.onConnectionStateChange = { isConnected ->
            handleConnectionStateChange(isConnected)
        }

        initializeCharacteristic()
        setupChart()
    }

    private fun initializeCharacteristic() {
        val service = bluetooth.getService(serviceUUID)
        service?.let { gattService ->
            selectedCharacteristic = gattService.characteristics.firstOrNull()
            if (selectedCharacteristic != null) {
                toggleReadingButton.isEnabled = true
            } else {
                logTextView.append("No characteristic found for the service.\n")
            }
        } ?: run {
            logTextView.append("Service not found.\n")
        }
    }

    private fun toggleReading() {
        if (!bluetooth.isConnected()) {
            Toast.makeText(this, "Not connected to device", Toast.LENGTH_SHORT).show()
            return
        }

        isReading = !isReading
        if (isReading) {
            toggleReadingButton.text = "Stop Reading"
            startReading()
        } else {
            toggleReadingButton.text = "Start Reading"
            stopReading()
        }
    }

    private fun startReading() {
        selectedCharacteristic?.let { characteristic ->
            if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                bluetooth.setCharacteristicNotification(characteristic, true)
            } else {
                // Fall back to polling if notifications are not supported
                handler.post(object : Runnable {
                    override fun run() {
                        if (isReading && bluetooth.isConnected()) {
                            bluetooth.readCharacteristic(characteristic)
                            handler.postDelayed(this, readInterval)
                        }
                    }
                })
            }
        }
    }

    private fun stopReading() {
        selectedCharacteristic?.let { characteristic ->
            if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                bluetooth.setCharacteristicNotification(characteristic, false)
            } else {
                handler.removeCallbacksAndMessages(null)
            }
        }
    }

    private fun handleCharacteristicChange(characteristic: BluetoothGattCharacteristic) {
        val currentTime = System.currentTimeMillis()
        val value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0) ?: 0

        if (currentTime - lastReadTime > 50) {
            currentValue = 0
            logChange("Value", "0")
        } else {
            currentValue = value
            peakValue = max(peakValue, value)
            logChange("Value", value.toString())
        }

        lastReadTime = currentTime
        logEntries.add(Pair(currentTime, currentValue))

        // Trim logEntries if it exceeds MAX_DATA_POINTS
        while (logEntries.size > MAX_DATA_POINTS) {
            logEntries.removeAt(0)
        }

        runOnUiThread {
            updateUI()
            if (currentTime - lastChartUpdateTime >= chartUpdateInterval) {
                updateChart()
                lastChartUpdateTime = currentTime
            }
        }
    }

    private fun updateUI() {
        currentValueTextView.text = "Current Value: $currentValue"
        peakValueTextView.text = "Peak Value: $peakValue"

        val warningText = if (peakValue > tbiThreshold) "Warning" else "Safe"
        tbiWarningTextView.text = "TBI Warning: $warningText (Peak: $peakValue)"
    }

    private fun logChange(characteristicName: String, value: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val logEntry = "$timestamp - $characteristicName: $value\n"
        runOnUiThread {
            logTextView.append(logEntry)
        }
    }

    private fun setupChart() {
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
        chart.setDrawGridBackground(false)
        chart.setPinchZoom(true)
        chart.setBackgroundColor(Color.WHITE)

        val dataSet = LineDataSet(ArrayList(), "Impact Data")
        dataSet.color = Color.BLUE
        dataSet.setDrawCircles(false)
        dataSet.setDrawValues(false)
        dataSet.lineWidth = 2f

        chart.data = LineData(dataSet)
    }

    private fun updateChart() {
        val entries = logEntries.mapIndexed { index, (_, value) ->
            Entry(index.toFloat(), value.toFloat())
        }

        val dataSet = chart.data.getDataSetByIndex(0) as LineDataSet
        dataSet.values = entries
        chart.data.notifyDataChanged()
        chart.notifyDataSetChanged()
        chart.invalidate()
    }

    private fun exportToCsv() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "impact_data_$timestamp.csv"
        val file = File(getExternalFilesDir(null), fileName)

        try {
            FileWriter(file).use { writer ->
                writer.append("Timestamp,Value\n")
                for ((time, value) in logEntries) {
                    val formattedTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(time))
                    writer.append("$formattedTime,$value\n")
                }
            }
            runOnUiThread {
                logTextView.append("Data exported to $fileName\n")
            }
        } catch (e: Exception) {
            runOnUiThread {
                logTextView.append("Error exporting data: ${e.message}\n")
            }
        }
    }

    private fun handleConnectionStateChange(isConnected: Boolean) {
        runOnUiThread {
            if (isConnected) {
                Toast.makeText(this, "Connected to device", Toast.LENGTH_SHORT).show()
                toggleReadingButton.isEnabled = true
                reconnectButton.isEnabled = false
            } else {
                Toast.makeText(this, "Disconnected from device", Toast.LENGTH_SHORT).show()
                toggleReadingButton.isEnabled = false
                reconnectButton.isEnabled = true
                if (isReading) {
                    isReading = false
                    toggleReadingButton.text = "Start Reading"
                    stopReading()
                }
            }
        }
    }

    private fun reconnect() {
        Log.d(TAG, "Manual reconnection triggered")
        bluetooth.manualReconnect(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopReading()
        bluetooth.disconnect()
    }
}