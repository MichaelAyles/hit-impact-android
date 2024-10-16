package com.example.blesensorviewer

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class RawDataActivity : AppCompatActivity() {

    private lateinit var characteristicValueTextView: TextView
    private lateinit var debugInfoTextView: TextView
    private lateinit var discoverButton: Button
    private lateinit var readCharacteristicButton: Button
    private lateinit var toggleContinuousButton: Button
    private lateinit var serviceSpinner: Spinner
    private lateinit var characteristicSpinner: Spinner
    private lateinit var bluetooth: Bluetooth

    private var services: List<BluetoothGattService> = listOf()
    private var selectedService: BluetoothGattService? = null
    private var selectedCharacteristic: BluetoothGattCharacteristic? = null
    private var isContinuousReading = false
    private val handler = Handler(Looper.getMainLooper())
    private val readInterval = 1000L // 1 second

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_raw_data)

        characteristicValueTextView = findViewById(R.id.characteristicValueTextView)
        debugInfoTextView = findViewById(R.id.debugInfoTextView)
        discoverButton = findViewById(R.id.discoverButton)
        readCharacteristicButton = findViewById(R.id.readCharacteristicButton)
        toggleContinuousButton = findViewById(R.id.toggleContinuousButton)
        serviceSpinner = findViewById(R.id.serviceSpinner)
        characteristicSpinner = findViewById(R.id.characteristicSpinner)
        bluetooth = Bluetooth.getInstance()

        discoverButton.setOnClickListener {
            discoverServices()
        }

        readCharacteristicButton.setOnClickListener {
            readCharacteristic()
        }

        toggleContinuousButton.setOnClickListener {
            toggleContinuousReading()
        }

        serviceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedService = services[position]
                updateCharacteristicSpinner()
                updateDebugInfo()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedService = null
                updateCharacteristicSpinner()
                updateDebugInfo()
            }
        }

        characteristicSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedCharacteristic = selectedService?.characteristics?.get(position)
                readCharacteristicButton.isEnabled = selectedCharacteristic != null
                toggleContinuousButton.isEnabled = selectedCharacteristic != null
                updateDebugInfo()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedCharacteristic = null
                readCharacteristicButton.isEnabled = false
                toggleContinuousButton.isEnabled = false
                updateDebugInfo()
            }
        }

        bluetooth.onServicesDiscovered = { discoveredServices ->
            runOnUiThread {
                services = discoveredServices
                updateServiceSpinner()
            }
        }

        bluetooth.onCharacteristicRead = { characteristic, value ->
            runOnUiThread {
                displayCharacteristicValue(characteristic, value)
            }
        }

        bluetooth.onCharacteristicChanged = { characteristic ->
            runOnUiThread {
                displayCharacteristicValue(characteristic, characteristic.value)
            }
        }

        Handler(Looper.getMainLooper()).postDelayed({
            checkConnectionStatus()
        }, 500)
    }

    private fun checkConnectionStatus() {
        if (bluetooth.isConnected()) {
            val services = bluetooth.getConnectedDeviceServices()
            if (services != null && services.isNotEmpty()) {
                this.services = services
                updateServiceSpinner()
            } else {
                characteristicValueTextView.text = "Connected, but no services discovered yet. Press 'Discover Services'."
            }
        } else {
            characteristicValueTextView.text = "Not connected to any device. Please connect first."
            discoverButton.isEnabled = false
            readCharacteristicButton.isEnabled = false
            toggleContinuousButton.isEnabled = false
        }
    }

    private fun discoverServices() {
        characteristicValueTextView.text = "Discovering services..."
        discoverButton.isEnabled = false
        readCharacteristicButton.isEnabled = false
        toggleContinuousButton.isEnabled = false
        bluetooth.discoverServices()
    }

    private fun updateServiceSpinner() {
        val serviceNames = services.mapIndexed { index, service ->
            "Service ${index + 1}: ${service.uuid}"
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, serviceNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        serviceSpinner.adapter = adapter

        if (services.isNotEmpty()) {
            serviceSpinner.setSelection(0)
            selectedService = services[0]
            updateCharacteristicSpinner()
        }

        discoverButton.isEnabled = true
        updateDebugInfo()
    }

    private fun updateCharacteristicSpinner() {
        val characteristics = selectedService?.characteristics ?: listOf()
        val characteristicNames = characteristics.mapIndexed { index, characteristic ->
            "Characteristic ${index + 1}: ${characteristic.uuid}"
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, characteristicNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        characteristicSpinner.adapter = adapter

        if (characteristics.isNotEmpty()) {
            characteristicSpinner.setSelection(0)
            selectedCharacteristic = characteristics[0]
            readCharacteristicButton.isEnabled = true
            toggleContinuousButton.isEnabled = true
        } else {
            readCharacteristicButton.isEnabled = false
            toggleContinuousButton.isEnabled = false
        }
        updateDebugInfo()
    }

    private fun readCharacteristic() {
        selectedCharacteristic?.let { characteristic ->
            bluetooth.readCharacteristic(characteristic)
            Toast.makeText(this, "Reading characteristic...", Toast.LENGTH_SHORT).show()
        } ?: run {
            Toast.makeText(this, "No characteristic selected", Toast.LENGTH_SHORT).show()
        }
    }

    private fun displayCharacteristicValue(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        val hexString = value.joinToString(separator = " ") { String.format("%02X", it) }
        val asciiString = value.map { it.toInt().toChar() }.joinToString("")
        val decimalString = value.joinToString(separator = " ") { it.toUByte().toString() }
        val message = "Characteristic ${characteristic.uuid}\n\nHex: $hexString\n\nASCII: $asciiString\n\nDecimal: $decimalString"
        characteristicValueTextView.text = message
    }

    private fun updateDebugInfo() {
        val debugInfo = StringBuilder()
        debugInfo.append("Selected Service: ${selectedService?.uuid}\n")
        debugInfo.append("Selected Characteristic: ${selectedCharacteristic?.uuid}\n")
        selectedCharacteristic?.let { characteristic ->
            debugInfo.append("Properties: ${getCharacteristicProperties(characteristic)}\n")
            debugInfo.append("Permissions: ${getCharacteristicPermissions(characteristic)}\n")
        }
        debugInfoTextView.text = debugInfo.toString()
    }

    private fun getCharacteristicProperties(characteristic: BluetoothGattCharacteristic): String {
        val props = mutableListOf<String>()
        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) props.add("Read")
        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) props.add("Write")
        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) props.add("Notify")
        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) props.add("Indicate")
        return props.joinToString(", ")
    }

    private fun getCharacteristicPermissions(characteristic: BluetoothGattCharacteristic): String {
        val perms = mutableListOf<String>()
        if (characteristic.permissions and BluetoothGattCharacteristic.PERMISSION_READ != 0) perms.add("Read")
        if (characteristic.permissions and BluetoothGattCharacteristic.PERMISSION_WRITE != 0) perms.add("Write")
        return perms.joinToString(", ")
    }

    private fun toggleContinuousReading() {
        isContinuousReading = !isContinuousReading
        if (isContinuousReading) {
            toggleContinuousButton.text = "Stop Continuous"
            startContinuousReading()
        } else {
            toggleContinuousButton.text = "Start Continuous"
            stopContinuousReading()
        }
    }

    private fun startContinuousReading() {
        selectedCharacteristic?.let { characteristic ->
            if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                bluetooth.setCharacteristicNotification(characteristic, true)
            } else {
                // Fall back to polling if notifications are not supported
                handler.post(object : Runnable {
                    override fun run() {
                        if (isContinuousReading) {
                            bluetooth.readCharacteristic(characteristic)
                            handler.postDelayed(this, readInterval)
                        }
                    }
                })
            }
        }
    }

    private fun stopContinuousReading() {
        selectedCharacteristic?.let { characteristic ->
            if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                bluetooth.setCharacteristicNotification(characteristic, false)
            } else {
                handler.removeCallbacksAndMessages(null)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopContinuousReading()
    }
}
