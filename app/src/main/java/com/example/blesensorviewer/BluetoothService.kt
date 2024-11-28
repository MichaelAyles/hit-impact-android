package com.example.blesensorviewer

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Observer
import java.util.concurrent.TimeUnit

class BluetoothService : Service() {
    private lateinit var bluetooth: Bluetooth
    private val channelId = "BluetoothServiceChannel"
    private val notificationId = 1
    private val TAG = "BluetoothService"
    
    private var connectionStartTime: Long = 0
    private var isConnected: Boolean = false
    
    private val handler = Handler(Looper.getMainLooper())
    private val notificationUpdateRunnable = object : Runnable {
        override fun run() {
            val actuallyConnected = bluetooth.isConnected()
            if (isConnected != actuallyConnected) {
                serviceConnectionHandler(actuallyConnected)
            }
            updateNotification()
            handler.postDelayed(this, 1000) // Update every second
        }
    }

    private val sensorDataObserver = Observer<Bluetooth.SensorData> { sensorData ->
        updateNotification()
    }

    private val serviceConnectionHandler: (Boolean) -> Unit = { connected ->
        Log.d(TAG, "Service handling connection state: $connected")
        isConnected = connected
        if (connected) {
            connectionStartTime = System.currentTimeMillis()
            startNotificationUpdates()
            enableAvailableNotifications()
        } else {
            updateNotification() // Immediate update for disconnect state
        }
    }

    private fun enableAvailableNotifications() {
        Log.d(TAG, "Enabling available notifications")
        bluetooth.getConnectedDeviceServices()?.forEach { service ->
            Log.d(TAG, "Checking service: ${service.uuid}")
            service.characteristics.forEach { characteristic ->
                if (characteristic.properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                    Log.d(TAG, "Enabling notifications for characteristic: ${characteristic.uuid}")
                    bluetooth.setCharacteristicNotification(characteristic, true)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        createNotificationChannel()
        bluetooth = Bluetooth.getInstance()
        
        isConnected = bluetooth.isConnected()
        if (isConnected) {
            Log.d(TAG, "Starting with active connection")
            connectionStartTime = System.currentTimeMillis()
            startNotificationUpdates()
            enableAvailableNotifications()
        }

        val originalConnectionCallback = bluetooth.onConnectionStateChange
        bluetooth.onConnectionStateChange = { connected ->
            serviceConnectionHandler(connected)
            originalConnectionCallback?.invoke(connected)
        }

        bluetooth.sensorData.observeForever(sensorDataObserver)

        startNotificationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service starting")
        val notification = createNotification()
        startForeground(notificationId, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Bluetooth Service"
            val descriptionText = "Maintains Bluetooth connection and shows sensor data"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
                enableLights(false)
                enableVibration(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun formatDuration(millis: Long): String {
        return String.format("%02d:%02d:%02d",
            TimeUnit.MILLISECONDS.toHours(millis),
            TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1),
            TimeUnit.MILLISECONDS.toSeconds(millis) % TimeUnit.MINUTES.toSeconds(1)
        )
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val connectionStatus = if (isConnected) "Connected" else "Disconnected"
        val connectionTime = if (isConnected) {
            formatDuration(System.currentTimeMillis() - connectionStartTime)
        } else {
            "00:00:00"
        }

        val sensorData = bluetooth.sensorData.value ?: Bluetooth.SensorData()
        
        Log.d(TAG, "Creating notification - Connected: $isConnected, Samples: ${sensorData.sampleCount}, Last: ${sensorData.currentValue}, Peak: ${sensorData.peakValue}")

        val contentText = StringBuilder()
            .append("• Status: $connectionStatus\n")
            .append("• Duration: $connectionTime\n")
            .append("• Samples: ${sensorData.sampleCount}\n")
            .append(String.format("• Current: %.0f\n", sensorData.currentValue))
            .append(String.format("• Peak: %.0f", sensorData.peakValue))
            .toString()

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("BLE Sensor Viewer")
            .setContentText("Status: $connectionStatus")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(contentText)
                .setBigContentTitle("BLE Sensor Viewer")
                .setSummaryText(connectionTime))
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    private fun startNotificationUpdates() {
        Log.d(TAG, "Starting notification updates")
        handler.post(notificationUpdateRunnable)
    }

    private fun stopNotificationUpdates() {
        Log.d(TAG, "Stopping notification updates")
        handler.removeCallbacks(notificationUpdateRunnable)
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, createNotification())
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service being destroyed")
        stopNotificationUpdates()
        bluetooth.sensorData.removeObserver(sensorDataObserver)
    }
}
