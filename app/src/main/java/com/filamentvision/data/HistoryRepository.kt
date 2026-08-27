package com.filamentvision.data

import com.filamentvision.model.MonitoringSession
import kotlinx.coroutines.flow.StateFlow

interface HistoryRepository {
    val sessions: StateFlow<List<MonitoringSession>>

    fun save(session: MonitoringSession)

    fun getById(id: String): MonitoringSession?
}
