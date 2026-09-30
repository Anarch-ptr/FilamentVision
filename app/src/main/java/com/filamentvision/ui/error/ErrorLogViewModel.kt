package com.filamentvision.ui.error

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.filamentvision.FilamentVisionApplication
import com.filamentvision.data.repository.ErrorSnapshotDiagnostics
import com.filamentvision.domain.error.ErrorEvent
import com.filamentvision.domain.error.ErrorSeverity
import com.filamentvision.domain.error.ErrorSnapshot
import com.filamentvision.domain.error.ErrorState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ErrorFilter { ALL, ACTIVE, CRITICAL }
data class ErrorLogUiState(val filter: ErrorFilter = ErrorFilter.ALL, val errors: List<ErrorEvent> = emptyList())
data class ErrorDetailUiState(val event: ErrorEvent? = null, val snapshots: List<ErrorSnapshot> = emptyList(), val loading: Boolean = false)

class ErrorLogViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as FilamentVisionApplication).errorRepository
    private val filter = MutableStateFlow(ErrorFilter.ALL)
    private val mutableDetail = MutableStateFlow(ErrorDetailUiState())
    private val mutableDiagnostics = MutableStateFlow(ErrorSnapshotDiagnostics())

    val state: StateFlow<ErrorLogUiState> = combine(repository.errors, filter) { errors, selected ->
        ErrorLogUiState(selected, errors.filter { event ->
            when (selected) {
                ErrorFilter.ALL -> true
                ErrorFilter.ACTIVE -> event.state == ErrorState.ACTIVE
                ErrorFilter.CRITICAL -> event.severity == ErrorSeverity.CRITICAL
            }
        })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), ErrorLogUiState())
    val detail: StateFlow<ErrorDetailUiState> = mutableDetail
    val diagnostics: StateFlow<ErrorSnapshotDiagnostics> = mutableDiagnostics

    fun selectFilter(value: ErrorFilter) { filter.value = value }
    fun loadError(id: Long) = viewModelScope.launch {
        mutableDetail.value = ErrorDetailUiState(loading = true)
        mutableDetail.value = withContext(Dispatchers.IO) {
            ErrorDetailUiState(repository.getById(id), repository.getSnapshots(id))
        }
    }
    fun deleteResolved(id: Long) = viewModelScope.launch {
        if (withContext(Dispatchers.IO) { repository.deleteResolved(id) } && mutableDetail.value.event?.id == id) {
            mutableDetail.value = ErrorDetailUiState()
        }
    }
    fun clearResolved() = viewModelScope.launch {
        withContext(Dispatchers.IO) { repository.clearResolved() }
        if (mutableDetail.value.event?.state == ErrorState.RESOLVED) mutableDetail.value = ErrorDetailUiState()
    }
    fun refreshDiagnostics() = viewModelScope.launch(Dispatchers.IO) { mutableDiagnostics.value = repository.snapshotDiagnostics() }
}
