package com.filamentvision.data.settings

import com.filamentvision.model.ConnectionConfig
import com.filamentvision.model.ConnectionSettings
import kotlinx.coroutines.flow.StateFlow

interface ConnectionSettingsRepository {
    val settings: StateFlow<ConnectionSettings>
    fun save(config: ConnectionConfig)
}
