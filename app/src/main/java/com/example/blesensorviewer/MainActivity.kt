package com.example.blesensorviewer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var connectButton: Button
    private lateinit var viewDataButton: Button
    private lateinit var impactDataButton: Button
    private lateinit var bluetooth: Bluetooth
    private var isConnected = false

    private val scanActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            isConnected = true
            updateButtonStates()
            if (hasNotificationPermission()) {
                startBluetoothService()
            } else {
                requestNotificationPermission()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bluetooth = Bluetooth.getInstance()
        bluetooth.initialize(this)

        connectButton = findViewById(R.id.connectButton)
        viewDataButton = findViewById(R.id.viewDataButton)
        impactDataButton = findViewById(R.id.impactDataButton)

        connectButton.setOnClickListener {
            if (!isConnected) {
                if (hasBluetoothPermissions()) {
                    scanActivityLauncher.launch(Intent(this, ScanActivity::class.java))
                } else {
                    requestBluetoothPermissions()
                }
            } else {
                bluetooth.disconnect()
                stopBluetoothService()
                isConnected = false
                updateButtonStates()
                Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show()
            }
        }

        viewDataButton.setOnClickListener {
            startActivity(Intent(this, RawDataActivity::class.java))
        }

        impactDataButton.setOnClickListener {
            startActivity(Intent(this, ImpactDataActivity::class.java))
        }

        updateButtonStates()

        bluetooth.onConnectionStateChange = { connected ->
            runOnUiThread {
                isConnected = connected
                updateButtonStates()
                if (connected && hasNotificationPermission()) {
                    startBluetoothService()
                } else if (!connected) {
                    stopBluetoothService()
                }
            }
        }

        // Check and request Bluetooth permissions on startup
        if (!hasBluetoothPermissions()) {
            requestBluetoothPermissions()
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermissions(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            )
        } else {
            hasPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        } else {
            true // Notification permission not required for older Android versions
        }
    }

    private fun hasPermissions(permissions: Array<String>): Boolean {
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBluetoothPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        ActivityCompat.requestPermissions(this, permissions, BLUETOOTH_PERMISSION_REQUEST_CODE)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun startBluetoothService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, BluetoothService::class.java))
        } else {
            startService(Intent(this, BluetoothService::class.java))
        }
    }

    private fun stopBluetoothService() {
        stopService(Intent(this, BluetoothService::class.java))
    }

    private fun updateButtonStates() {
        connectButton.text = if (isConnected) "Disconnect" else "Connect"
        viewDataButton.isEnabled = isConnected
        impactDataButton.isEnabled = isConnected
    }

    companion object {
        private const val BLUETOOTH_PERMISSION_REQUEST_CODE = 1
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 2
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            BLUETOOTH_PERMISSION_REQUEST_CODE -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    // Bluetooth permissions granted, proceed with scan
                    scanActivityLauncher.launch(Intent(this, ScanActivity::class.java))
                } else {
                    Toast.makeText(this, "Bluetooth permissions required for scanning", Toast.LENGTH_LONG).show()
                }
            }
            NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Notification permission granted, start service
                    if (isConnected) {
                        startBluetoothService()
                    }
                } else {
                    Toast.makeText(this, "Notifications will not be shown", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
