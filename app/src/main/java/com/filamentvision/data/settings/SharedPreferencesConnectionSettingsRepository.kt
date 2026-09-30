package com.filamentvision.data.settings

import android.content.Context
import com.filamentvision.domain.connection.ConnectionProfileValidator
import com.filamentvision.domain.connection.ProfileValidation
import com.filamentvision.model.ConnectionProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val PREFERENCES_NAME = "connection_settings"

class SharedPreferencesConnectionSettingsRepository(context: Context) : ConnectionSettingsRepository {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableProfile = MutableStateFlow(
        ConnectionProfileCodec.decodeOrDefault(preferences.getString(KEY_PROFILE, null)),
    )
    override val profile: StateFlow<ConnectionProfile> = mutableProfile.asStateFlow()
    private val mutableSaved = MutableStateFlow(
        SavedConnectionProfile(mutableProfile.value, preferences.getLong(KEY_REVISION, 0L)),
    )
    override val saved: StateFlow<SavedConnectionProfile> = mutableSaved.asStateFlow()

    override fun save(profile: ConnectionProfile): ProfileValidation {
        val validation = ConnectionProfileValidator.validate(profile)
        if (validation is ProfileValidation.Invalid) return validation
        val revision = mutableSaved.value.revision + 1L
        preferences.edit()
            .putString(KEY_PROFILE, ConnectionProfileCodec.encode(profile))
            .putLong(KEY_REVISION, revision)
            .apply()
        mutableProfile.value = profile
        mutableSaved.value = SavedConnectionProfile(profile, revision)
        return ProfileValidation.Valid
    }

    private companion object {
        const val KEY_PROFILE = "connection_profile_v1"
        const val KEY_REVISION = "connection_profile_revision"
    }
}
