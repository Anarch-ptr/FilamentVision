package com.filamentvision.data.settings

import android.content.Context
import com.filamentvision.model.BluetoothConnectionConfig
import com.filamentvision.model.ConnectionConfig
import com.filamentvision.model.ConnectionSettings
import com.filamentvision.model.ConnectionType
import com.filamentvision.model.DEFAULT_BLUETOOTH_CONNECTION
import com.filamentvision.model.DEFAULT_WIFI_CONNECTION
import com.filamentvision.model.WifiConnectionConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val PREFERENCES_NAME = "connection_settings"

class SharedPreferencesConnectionSettingsRepository(context: Context) : ConnectionSettingsRepository {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableSettings = MutableStateFlow(readSettings())
    override val settings: StateFlow<ConnectionSettings> = mutableSettings.asStateFlow()

    override fun save(config: ConnectionConfig) {
        val updated = when (config) {
            is WifiConnectionConfig -> mutableSettings.value.copy(selectedType = ConnectionType.WIFI, wifi = config)
            is BluetoothConnectionConfig -> mutableSettings.value.copy(
                selectedType = ConnectionType.BLUETOOTH,
                bluetooth = config,
            )
        }
        writeSettings(updated)
        mutableSettings.value = updated
    }

    private fun readSettings(): ConnectionSettings {
        val wifi = WifiConnectionConfig(
            deviceUid = preferences.getString(KEY_WIFI_UID, DEFAULT_WIFI_CONNECTION.deviceUid).orEmpty(),
            host = preferences.getString(KEY_WIFI_HOST, DEFAULT_WIFI_CONNECTION.host).orEmpty(),
            port = preferences.getInt(KEY_WIFI_PORT, DEFAULT_WIFI_CONNECTION.port),
        )
        val bluetooth = BluetoothConnectionConfig(
            deviceUid = preferences.getString(KEY_BLE_UID, DEFAULT_BLUETOOTH_CONNECTION.deviceUid).orEmpty(),
            deviceAddress = preferences.getString(KEY_BLE_ADDRESS, DEFAULT_BLUETOOTH_CONNECTION.deviceAddress).orEmpty(),
            serviceUuid = preferences.getString(KEY_SERVICE_UUID, DEFAULT_BLUETOOTH_CONNECTION.serviceUuid).orEmpty(),
            measurementCharacteristicUuid = preferences.getString(
                KEY_MEASUREMENT_UUID,
                DEFAULT_BLUETOOTH_CONNECTION.measurementCharacteristicUuid,
            ).orEmpty(),
            commandCharacteristicUuid = preferences.getString(
                KEY_COMMAND_UUID,
                DEFAULT_BLUETOOTH_CONNECTION.commandCharacteristicUuid,
            ).orEmpty(),
        )
        val selected = runCatching {
            ConnectionType.valueOf(preferences.getString(KEY_SELECTED_TYPE, ConnectionType.WIFI.name).orEmpty())
        }.getOrDefault(ConnectionType.WIFI)
        return ConnectionSettings(selected, wifi, bluetooth)
    }

    private fun writeSettings(settings: ConnectionSettings) {
        preferences.edit()
            .putString(KEY_SELECTED_TYPE, settings.selectedType.name)
            .putString(KEY_WIFI_UID, settings.wifi.deviceUid)
            .putString(KEY_WIFI_HOST, settings.wifi.host)
            .putInt(KEY_WIFI_PORT, settings.wifi.port)
            .putString(KEY_BLE_UID, settings.bluetooth.deviceUid)
            .putString(KEY_BLE_ADDRESS, settings.bluetooth.deviceAddress)
            .putString(KEY_SERVICE_UUID, settings.bluetooth.serviceUuid)
            .putString(KEY_MEASUREMENT_UUID, settings.bluetooth.measurementCharacteristicUuid)
            .putString(KEY_COMMAND_UUID, settings.bluetooth.commandCharacteristicUuid)
            .apply()
    }

    private companion object {
        const val KEY_SELECTED_TYPE = "selected_type"
        const val KEY_WIFI_UID = "wifi_device_uid"
        const val KEY_WIFI_HOST = "wifi_host"
        const val KEY_WIFI_PORT = "wifi_port"
        const val KEY_BLE_UID = "ble_device_uid"
        const val KEY_BLE_ADDRESS = "ble_device_address"
        const val KEY_SERVICE_UUID = "ble_service_uuid"
        const val KEY_MEASUREMENT_UUID = "ble_measurement_uuid"
        const val KEY_COMMAND_UUID = "ble_command_uuid"
    }
}
