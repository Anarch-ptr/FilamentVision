package com.filamentvision.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.filamentvision.model.CalibrationProfile
import com.filamentvision.model.CameraProfile
import com.filamentvision.model.ConnectionProfile
import com.filamentvision.model.ImageFormatProfile
import com.filamentvision.model.TransportProtocol
import com.filamentvision.model.WifiEndpointProfile
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
    fun completeConnectionProfileSurvivesRepositoryRecreation() {
        val repository = SharedPreferencesConnectionSettingsRepository(context)
        val endpoint = WifiEndpointProfile(
            endpointId = "wifi-camera",
            protocol = TransportProtocol.WIFI_TCP,
            host = "10.0.0.8",
            port = 5105,
        )
        val image = ImageFormatProfile(width = 640, height = 480)
        val calibration = CalibrationProfile(
            roiRight = 640,
            roiBottom = 480,
            minimumPixelWidth = 1,
            maximumPixelWidth = 200,
            mmPerPixel = 0.01,
        )
        val profile = ConnectionProfile(
            endpoints = listOf(endpoint),
            cameras = listOf(
                CameraProfile("A", endpoint.endpointId, imageFormat = image, calibration = calibration),
                CameraProfile("B", endpoint.endpointId, imageFormat = image, calibration = calibration),
            ),
            preferredEndpointId = endpoint.endpointId,
        )
        repository.save(profile)

        val restored = SharedPreferencesConnectionSettingsRepository(context).profile.value

        assertEquals(profile, restored)
    }
}
