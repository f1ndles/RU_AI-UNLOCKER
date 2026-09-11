package com.findle.ruaiunlocker.ui.screens.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findle.ruaiunlocker.domain.ModuleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LogsUiState(
    val isLoading: Boolean = true,
    val logContent: String = "",
    val isEmpty: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val moduleManager: ModuleManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogsUiState())
    val uiState: StateFlow<LogsUiState> = _uiState.asStateFlow()

    init {
        loadLogs()
    }

    fun loadLogs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            moduleManager.getLogs()
                .onSuccess { content ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            logContent = content,
                            isEmpty = content.isBlank()
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message
                        )
                    }
                }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            moduleManager.clearLogs()
                .onSuccess {
                    _uiState.update {
                        it.copy(logContent = "", isEmpty = true)
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message) }
                }
        }
    }

    fun getLogContent(): String {
        return _uiState.value.logContent
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
