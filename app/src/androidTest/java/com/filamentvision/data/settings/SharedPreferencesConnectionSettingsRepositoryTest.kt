package com.filamentvision.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.filamentvision.model.BluetoothConnectionConfig
import com.filamentvision.model.ConnectionType
import com.filamentvision.model.WifiConnectionConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedPreferencesConnectionSettingsRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    @After
    fun clearPreferences() {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun wifiAndBluetoothProfilesSurviveRepositoryRecreation() {
        val repository = SharedPreferencesConnectionSettingsRepository(context)
        val wifi = WifiConnectionConfig("wifi-device", "10.0.0.8", 5105)
        val bluetooth = BluetoothConnectionConfig(
            deviceUid = "ble-device",
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            serviceUuid = "0000181a-0000-1000-8000-00805f9b34fb",
            measurementCharacteristicUuid = "00002a6e-0000-1000-8000-00805f9b34fb",
            commandCharacteristicUuid = "00002a58-0000-1000-8000-00805f9b34fb",
        )
        repository.save(wifi)
        repository.save(bluetooth)

        val restored = SharedPreferencesConnectionSettingsRepository(context).settings.value

        assertEquals(ConnectionType.BLUETOOTH, restored.selectedType)
        assertEquals(wifi, restored.wifi)
        assertEquals(bluetooth, restored.bluetooth)
    }
}
