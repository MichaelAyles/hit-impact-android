package com.example.blesensorviewer

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

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
                scanActivityLauncher.launch(Intent(this, ScanActivity::class.java))
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
                if (!connected) {
                    stopBluetoothService()
                }
            }
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
}
