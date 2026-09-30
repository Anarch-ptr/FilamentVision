package com.filamentvision.data.repository

import com.filamentvision.model.MonitoringSession
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    val sessions: Flow<List<MonitoringSession>>

    suspend fun createActive(session: MonitoringSession)
    suspend fun finalize(session: MonitoringSession)
    suspend fun getById(sessionId: String): MonitoringSession?
    suspend fun delete(sessionId: String)
    suspend fun recoverInterruptedSessions(): Int
    suspend fun recoverInterruptedSessionIds(): List<String>? = null
}
