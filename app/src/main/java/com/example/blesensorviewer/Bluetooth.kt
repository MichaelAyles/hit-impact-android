package com.example.blesensorviewer

import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class Bluetooth private constructor() {

    data class SensorData(
        val currentValue: Float = 0f,
        val peakValue: Float = 0f,
        val sampleCount: Int = 0,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val _sensorData = MutableLiveData(SensorData())
    val sensorData: LiveData<SensorData> = _sensorData

    companion object {
        @Volatile
        private var instance: Bluetooth? = null
        private const val TAG = "Bluetooth"
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val RECONNECT_DELAY = 10000L
        private const val CONNECTION_TIMEOUT = 30000L
        private const val MAX_BUFFER_SIZE = 10 * 1024 * 1024
        private const val TARGET_DEVICE_ID = "e5209eeb8b88"

        fun getInstance(): Bluetooth {
            return instance ?: synchronized(this) {
                instance ?: Bluetooth().also { instance = it }
            }
        }
    }

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private val handler = Handler(Looper.getMainLooper())

    private var scanning = false
    private val SCAN_PERIOD: Long = 10000

    private var isConnected = false
    private var bluetoothGatt: BluetoothGatt? = null
    private var reconnectAttempts = 0
    private var lastConnectedDeviceAddress: String? = null
    private var applicationContext: Context? = null
    private var autoReconnect = true

    private val dataBuffer = ConcurrentLinkedQueue<ByteArray>()
    private var bufferSize = 0

    data class BleDevice(val name: String?, val address: String, val rssi: Int)

    private val deviceList = mutableListOf<BleDevice>()
    var onDeviceFound: ((List<BleDevice>) -> Unit)? = null
    var onConnectionStateChange: ((Boolean) -> Unit)? = null
    var onServicesDiscovered: ((List<BluetoothGattService>) -> Unit)? = null
    var onCharacteristicRead: ((BluetoothGattCharacteristic, ByteArray) -> Unit)? = null
    var onCharacteristicChanged: ((BluetoothGattCharacteristic) -> Unit)? = null
    var onDataBufferFull: (() -> Unit)? = null

    var deviceNameFilter: String = "HIT"

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
        startScan()
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name ?: ""
            val address = device.address.replace(":", "").toLowerCase()
            
            if (address == TARGET_DEVICE_ID) {
                stopScan()
                applicationContext?.let { context ->
                    connectToDevice(context, device.address)
                }
                return
            }
            
            if (deviceName.contains(deviceNameFilter, ignoreCase = true)) {
                val existingDeviceIndex = deviceList.indexOfFirst { it.address == device.address }
                val bleDevice = BleDevice(device.name, device.address, result.rssi)

                if (existingDeviceIndex != -1) {
                    deviceList[existingDeviceIndex] = bleDevice
                } else {
                    deviceList.add(bleDevice)
                }
                onDeviceFound?.invoke(deviceList)
            }
        }
    }

    fun startScan() {
        if (!scanning) {
            deviceList.clear()
            handler.postDelayed({
                stopScan()
                if (!isConnected && lastConnectedDeviceAddress == null) {
                    handler.postDelayed({ startScan() }, RECONNECT_DELAY)
                }
            }, SCAN_PERIOD)
            scanning = true
            bluetoothLeScanner.startScan(scanCallback)
            Log.d(TAG, "Started BLE scan")
        }
    }

    fun stopScan() {
        if (scanning) {
            scanning = false
            bluetoothLeScanner.stopScan(scanCallback)
            Log.d(TAG, "Stopped BLE scan")
        }
    }

    fun connectToDevice(context: Context, address: String) {
        val device = bluetoothAdapter.getRemoteDevice(address)
        lastConnectedDeviceAddress = address
        autoReconnect = true
        bluetoothGatt = device.connectGatt(context, true, gattCallback, BluetoothDevice.TRANSPORT_LE)
        Log.d(TAG, "Attempting to connect to device: $address")

        handler.postDelayed({
            if (!isConnected) {
                Log.e(TAG, "Connection timeout. Disconnecting...")
                disconnect()
                onConnectionStateChange?.invoke(false)
                startScan()
            }
        }, CONNECTION_TIMEOUT)
    }

    fun disconnect() {
        autoReconnect = false
        bluetoothGatt?.let { gatt ->
            gatt.disconnect()
            gatt.close()
            bluetoothGatt = null
        }
        isConnected = false
        onConnectionStateChange?.invoke(false)
        _sensorData.postValue(SensorData()) // Reset sensor data on disconnect
        Log.d(TAG, "Disconnecting and cleaning up GATT resources")
    }

    fun discoverServices() {
        bluetoothGatt?.discoverServices()
        Log.d(TAG, "Discovering services")
    }

    fun isConnected(): Boolean {
        val deviceConnected = bluetoothGatt?.let { gatt ->
            bluetoothManager.getConnectionState(gatt.device, BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED
        } ?: false
        
        if (isConnected && !deviceConnected) {
            isConnected = false
            onConnectionStateChange?.invoke(false)
            _sensorData.postValue(SensorData()) // Reset sensor data on disconnect
        }
        
        return isConnected
    }

    fun readCharacteristic(characteristic: BluetoothGattCharacteristic) {
        bluetoothGatt?.readCharacteristic(characteristic)
        Log.d(TAG, "Reading characteristic: ${characteristic.uuid}")
    }

    fun getService(uuid: String): BluetoothGattService? {
        return bluetoothGatt?.getService(UUID.fromString(uuid))
    }

    fun setCharacteristicNotification(characteristic: BluetoothGattCharacteristic, enable: Boolean) {
        bluetoothGatt?.setCharacteristicNotification(characteristic, enable)
        val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
        descriptor?.value = if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        bluetoothGatt?.writeDescriptor(descriptor)
        Log.d(TAG, "Setting characteristic notification: ${characteristic.uuid}, enable: $enable")
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected = true
                reconnectAttempts = 0
                onConnectionStateChange?.invoke(true)
                Log.d(TAG, "Connected to GATT server")
                
                discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false
                onConnectionStateChange?.invoke(false)
                Log.d(TAG, "Disconnected from GATT server")
                
                gatt.close()
                bluetoothGatt = null
                _sensorData.postValue(SensorData()) // Reset sensor data on disconnect
                
                if (autoReconnect) {
                    applicationContext?.let { attemptReconnect(it) }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                onServicesDiscovered?.invoke(gatt.services)
                Log.d(TAG, "Services discovered")
                
                gatt.services.forEach { service ->
                    service.characteristics.forEach { characteristic ->
                        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                            setCharacteristicNotification(characteristic, true)
                            return@forEach
                        }
                    }
                }
            } else {
                Log.e(TAG, "Service discovery failed with status: $status")
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleIncomingData(characteristic.value)
                onCharacteristicRead?.invoke(characteristic, characteristic.value)
                Log.d(TAG, "Characteristic read: ${characteristic.uuid}")
            } else {
                Log.e(TAG, "Characteristic read failed for ${characteristic.uuid} with status: $status")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            handleIncomingData(characteristic.value)
            onCharacteristicChanged?.invoke(characteristic)
            Log.d(TAG, "Characteristic changed: ${characteristic.uuid}")
        }
    }

    private fun handleIncomingData(data: ByteArray) {
        if (bufferSize + data.size > MAX_BUFFER_SIZE) {
            Log.w(TAG, "Data buffer full. Dropping oldest data.")
            while (bufferSize + data.size > MAX_BUFFER_SIZE) {
                val removed = dataBuffer.poll()
                if (removed != null) {
                    bufferSize -= removed.size
                } else {
                    break
                }
            }
            onDataBufferFull?.invoke()
        }
        dataBuffer.offer(data)
        bufferSize += data.size

        // Process sensor data
        if (data.isNotEmpty()) {
            val currentValue = data[0].toUByte().toFloat()
            val currentData = _sensorData.value ?: SensorData()
            val newData = currentData.copy(
                currentValue = currentValue,
                peakValue = maxOf(currentValue, currentData.peakValue),
                sampleCount = currentData.sampleCount + 1,
                timestamp = System.currentTimeMillis()
            )
            _sensorData.postValue(newData)
        }
    }

    fun getBufferedData(): ByteArray {
        val result = ByteArray(bufferSize)
        var offset = 0
        while (dataBuffer.isNotEmpty()) {
            val chunk = dataBuffer.poll()
            System.arraycopy(chunk, 0, result, offset, chunk.size)
            offset += chunk.size
        }
        bufferSize = 0
        return result
    }

    fun getConnectedDeviceServices(): List<BluetoothGattService>? {
        return bluetoothGatt?.services
    }

    private fun attemptReconnect(context: Context) {
        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++
            Log.d(TAG, "Attempting to reconnect. Attempt $reconnectAttempts of $MAX_RECONNECT_ATTEMPTS")
            handler.postDelayed({
                lastConnectedDeviceAddress?.let { address ->
                    if (autoReconnect) {
                        connectToDevice(context, address)
                    }
                }
            }, RECONNECT_DELAY)
        } else {
            Log.e(TAG, "Max reconnection attempts reached")
            autoReconnect = false
            startScan()
        }
    }

    fun manualReconnect(context: Context) {
        reconnectAttempts = 0
        autoReconnect = true
        Log.d(TAG, "Manual reconnection triggered")
        lastConnectedDeviceAddress?.let { address ->
            connectToDevice(context, address)
        }
    }
}
