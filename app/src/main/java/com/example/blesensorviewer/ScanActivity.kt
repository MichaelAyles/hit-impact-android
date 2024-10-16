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
                    Toast.makeText(this, "Failed to connect", Toast.LENGTH_SHORT).show()
                }
            }
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
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
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
