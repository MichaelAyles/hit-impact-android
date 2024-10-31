package com.example.blesensorviewer

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

class BluetoothService : Service() {
    private lateinit var bluetooth: Bluetooth
    private val channelId = "BluetoothServiceChannel"
    private val notificationId = 1

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        bluetooth = Bluetooth.getInstance()
        bluetooth.initialize(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(notificationId, notification)

        // Reconnect if we have a last known device
        bluetooth.manualReconnect(this)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Bluetooth Service"
            val descriptionText = "Maintains Bluetooth connection in background"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("BLE Sensor Viewer")
            .setContentText("Running in background")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetooth.disconnect()
    }
}
