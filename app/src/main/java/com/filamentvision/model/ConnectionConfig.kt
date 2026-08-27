package com.filamentvision.model

sealed interface ConnectionConfig {
    val deviceUid: String
    val type: ConnectionType
}

data class WifiConnectionConfig(
    override val deviceUid: String,
    val host: String,
    val port: Int,
) : ConnectionConfig {
    override val type: ConnectionType = ConnectionType.WIFI
}

data class BluetoothConnectionConfig(
    override val deviceUid: String,
    val deviceAddress: String,
    val serviceUuid: String,
    val measurementCharacteristicUuid: String,
    val commandCharacteristicUuid: String,
) : ConnectionConfig {
    override val type: ConnectionType = ConnectionType.BLUETOOTH
}

data class ConnectionSettings(
    val selectedType: ConnectionType = ConnectionType.WIFI,
    val wifi: WifiConnectionConfig = DEFAULT_WIFI_CONNECTION,
    val bluetooth: BluetoothConnectionConfig = DEFAULT_BLUETOOTH_CONNECTION,
) {
    val active: ConnectionConfig
        get() = when (selectedType) {
            ConnectionType.WIFI -> wifi
            ConnectionType.BLUETOOTH -> bluetooth
        }
}

val DEFAULT_WIFI_CONNECTION = WifiConnectionConfig(
    deviceUid = "FV-DEMO-001",
    host = "192.168.4.1",
    port = 5105,
)

val DEFAULT_BLUETOOTH_CONNECTION = BluetoothConnectionConfig(
    deviceUid = "FV-DEMO-001",
    deviceAddress = "AA:BB:CC:DD:EE:FF",
    serviceUuid = "0000181a-0000-1000-8000-00805f9b34fb",
    measurementCharacteristicUuid = "00002a6e-0000-1000-8000-00805f9b34fb",
    commandCharacteristicUuid = "00002a58-0000-1000-8000-00805f9b34fb",
)
