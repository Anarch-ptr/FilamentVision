package com.filamentvision.data.settings

import com.filamentvision.domain.connection.ProfileValidation
import com.filamentvision.model.ConnectionProfile
import kotlinx.coroutines.flow.StateFlow

data class SavedConnectionProfile(
    val profile: ConnectionProfile,
    val revision: Long,
)

interface ConnectionSettingsRepository {
    val profile: StateFlow<ConnectionProfile>
    val saved: StateFlow<SavedConnectionProfile>
    fun save(profile: ConnectionProfile): ProfileValidation
}
