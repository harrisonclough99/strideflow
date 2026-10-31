package com.strideflow.app.service

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class BleStatus {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED
}

data class DiscoveredBleDevice(
    val name: String,
    val address: String,
    val rssi: Int = 0,
    val isPaired: Boolean = false,
    val isSmartwatch: Boolean = false
)

class BleHeartRateManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter
    private var bluetoothGatt: BluetoothGatt? = null

    private val _status = MutableStateFlow(BleStatus.DISCONNECTED)
    val status = _status.asStateFlow()

    private val _currentHeartRate = MutableStateFlow(0)
    val currentHeartRate = _currentHeartRate.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredBleDevice>>(emptyList())
    val discoveredDevices = _discoveredDevices.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            val device = result.device ?: return
            val name = device.name ?: "Smartwatch / HRM"
            val address = device.address ?: return
            val rssi = result.rssi

            val current = _discoveredDevices.value.toMutableList()
            if (current.none { it.address == address }) {
                val isWatch = isLikelySmartwatch(device, name)
                current.add(DiscoveredBleDevice(name = name, address = address, rssi = rssi, isPaired = false, isSmartwatch = isWatch))
                _discoveredDevices.value = current
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _status.value = BleStatus.DISCONNECTED
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _status.value = BleStatus.CONNECTING
                gatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _status.value = BleStatus.DISCONNECTED
                _currentHeartRate.value = 0
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt?.getService(HEART_RATE_SERVICE_UUID)
                val characteristic = service?.getCharacteristic(HEART_RATE_MEASUREMENT_CHAR_UUID)
                if (characteristic != null) {
                    gatt.setCharacteristicNotification(characteristic, true)
                    val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                    if (descriptor != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        } else {
                            @Suppress("DEPRECATION")
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            gatt.writeDescriptor(descriptor)
                        }
                    }
                    _status.value = BleStatus.CONNECTED
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == HEART_RATE_MEASUREMENT_CHAR_UUID) {
                processHeartRateBytes(value)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            if (characteristic?.uuid == HEART_RATE_MEASUREMENT_CHAR_UUID) {
                @Suppress("DEPRECATION")
                val value = characteristic.value ?: return
                processHeartRateBytes(value)
            }
        }
    }

    private fun processHeartRateBytes(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val flag = bytes[0].toInt()
        val is16Bit = (flag and 0x01) != 0
        val heartRate = if (is16Bit && bytes.size >= 3) {
            (bytes[1].toInt() and 0xFF) or ((bytes[2].toInt() and 0xFF) shl 8)
        } else if (bytes.size >= 2) {
            bytes[1].toInt() and 0xFF
        } else 0

        if (heartRate in 30..250) {
            _currentHeartRate.value = heartRate
        }
    }

    /**
     * Inspects already paired / bonded devices on the phone without over-the-air BLE scanning.
     */
    @SuppressLint("MissingPermission")
    fun getPairedSmartwatches(): List<DiscoveredBleDevice> {
        val adapter = bluetoothAdapter ?: return emptyList()
        return try {
            adapter.bondedDevices.map { device ->
                val name = device.name ?: "Paired Watch / HRM"
                val address = device.address
                val isWatch = isLikelySmartwatch(device, name)
                DiscoveredBleDevice(
                    name = name,
                    address = address,
                    rssi = 0,
                    isPaired = true,
                    isSmartwatch = isWatch
                )
            }.sortedByDescending { it.isSmartwatch }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Directly connects to an already bonded smartwatch, prioritizing user-saved preference or auto-detection.
     */
    @SuppressLint("MissingPermission")
    fun autoConnectPairedSmartwatch(preferredAddress: String? = null): Boolean {
        val paired = getPairedSmartwatches()
        if (paired.isEmpty()) return false

        if (!preferredAddress.isNullOrBlank()) {
            val found = paired.find { it.address.equals(preferredAddress, ignoreCase = true) }
            if (found != null) {
                connect(found.address)
                return true
            }
        }

        val bestWatch = paired.firstOrNull { it.isSmartwatch } ?: paired.firstOrNull()
        if (bestWatch != null) {
            connect(bestWatch.address)
            return true
        }
        return false
    }

    private fun isLikelySmartwatch(device: BluetoothDevice, name: String): Boolean {
        val lower = name.lowercase()
        val keywords = listOf(
            "watch", "pixel", "galaxy", "garmin", "fitbit", "hr", "band",
            "polar", "whoop", "coros", "suunto", "amazfit", "sense", "versa",
            "venu", "forerunner", "fenix", "instinct", "wear", "ticwatch",
            "apple", "huawei", "honor", "strap", "tracker", "pulse"
        )
        if (keywords.any { lower.contains(it) }) return true

        val devClass = try { device.bluetoothClass?.deviceClass } catch (e: Exception) { null }
        val majorClass = try { device.bluetoothClass?.majorDeviceClass } catch (e: Exception) { null }

        if (devClass == BluetoothClass.Device.WEARABLE_WRIST_WATCH ||
            devClass == BluetoothClass.Device.HEALTH_PULSE_OXIMETER ||
            devClass == BluetoothClass.Device.HEALTH_DATA_DISPLAY ||
            majorClass == BluetoothClass.Device.Major.WEARABLE ||
            majorClass == BluetoothClass.Device.Major.HEALTH) {
            return true
        }
        return false
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        _discoveredDevices.value = emptyList()
        _status.value = BleStatus.SCANNING

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(HEART_RATE_SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(listOf(filter), settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        try {
            scanner.stopScan(scanCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (_status.value == BleStatus.SCANNING) {
            _status.value = BleStatus.DISCONNECTED
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        stopScan()
        disconnect()

        val device = bluetoothAdapter?.getRemoteDevice(address) ?: return
        _status.value = BleStatus.CONNECTING
        bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        bluetoothGatt = null
        _status.value = BleStatus.DISCONNECTED
        _currentHeartRate.value = 0
    }

    companion object {
        val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT_CHAR_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
