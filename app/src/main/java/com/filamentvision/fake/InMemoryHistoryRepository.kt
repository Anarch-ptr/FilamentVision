package com.filamentvision.fake

import com.filamentvision.data.HistoryRepository
import com.filamentvision.model.MonitoringSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class InMemoryHistoryRepository : HistoryRepository {
    private val mutableSessions = MutableStateFlow<List<MonitoringSession>>(emptyList())

    override val sessions: StateFlow<List<MonitoringSession>> = mutableSessions.asStateFlow()

    override fun save(session: MonitoringSession) {
        mutableSessions.update { current ->
            listOf(session) + current.filterNot { it.id == session.id }
        }
    }

    override fun getById(id: String): MonitoringSession? =
        mutableSessions.value.firstOrNull { it.id == id }
}
