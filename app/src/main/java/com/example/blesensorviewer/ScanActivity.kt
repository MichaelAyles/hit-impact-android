package com.example.blesensorviewer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class ScanActivity : AppCompatActivity() {

    private lateinit var bluetooth: Bluetooth
    private lateinit var scanButton: Button
    private lateinit var stopScanButton: Button
    private lateinit var deviceListLayout: LinearLayout
    private lateinit var filterEditText: EditText

    private val PERMISSION_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        bluetooth = Bluetooth.getInstance()

        scanButton = findViewById(R.id.scanButton)
        stopScanButton = findViewById(R.id.stopScanButton)
        deviceListLayout = findViewById(R.id.deviceListLayout)
        filterEditText = findViewById(R.id.filterEditText)

        filterEditText.setText(bluetooth.deviceNameFilter)

        scanButton.setOnClickListener {
            if (hasRequiredPermissions()) {
                startScan()
            } else {
                requestPermissions()
            }
        }

        stopScanButton.setOnClickListener {
            stopScan()
        }

        bluetooth.onDeviceFound = { devices ->
            runOnUiThread {
                updateDeviceList(devices)
            }
        }

        bluetooth.onConnectionStateChange = { connected ->
            runOnUiThread {
                if (connected) {
                    Toast.makeText(this, "Connected successfully", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    Toast.makeText(this, "Connection lost, attempting to reconnect...", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Start scanning immediately if we have permissions
        if (hasRequiredPermissions()) {
            startScan()
        } else {
            requestPermissions()
        }
    }

    private fun startScan() {
        scanButton.isEnabled = false
        stopScanButton.isEnabled = true
        deviceListLayout.removeAllViews()
        bluetooth.deviceNameFilter = filterEditText.text.toString()
        bluetooth.startScan()
    }

    private fun stopScan() {
        bluetooth.stopScan()
        scanButton.isEnabled = true
        stopScanButton.isEnabled = false
        Toast.makeText(this, "Scan stopped", Toast.LENGTH_SHORT).show()
    }

    private fun updateDeviceList(devices: List<Bluetooth.BleDevice>) {
        deviceListLayout.removeAllViews()
        devices.forEach { device ->
            val deviceView = TextView(this).apply {
                text = "Name: ${device.name ?: "Unknown"}\nMAC: ${device.address}\nRSSI: ${device.rssi} dBm"
                setPadding(0, 0, 0, 16)
                setOnClickListener {
                    stopScan()
                    bluetooth.connectToDevice(this@ScanActivity, device.address)
                    Toast.makeText(this@ScanActivity, "Connecting to ${device.name ?: "Unknown Device"}...", Toast.LENGTH_SHORT).show()
                }
            }
            deviceListLayout.addView(deviceView)
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val basePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val fgsPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            arrayOf(Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE)
        } else {
            emptyArray()
        }

        val allPermissions = basePermissions + fgsPermission
        
        return allPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val basePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val fgsPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            arrayOf(Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE)
        } else {
            emptyArray()
        }

        val allPermissions = basePermissions + fgsPermission

        ActivityCompat.requestPermissions(this, allPermissions, PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startScan()
            } else {
                Toast.makeText(this, "Permissions are required to scan for devices", Toast.LENGTH_LONG).show()
            }
        }
    }
}
