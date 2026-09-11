package com.findle.ruaiunlocker.ui.screens.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.findle.ruaiunlocker.data.repository.SettingsRepository
import com.findle.ruaiunlocker.worker.UpdateCheckWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class SettingsUiState(
    val updateIntervalHours: Int = 12,
    val autoUpdate: Boolean = false,
    val maxBackups: Int = 5,
    val themeMode: String = "dark",
    val appVersion: String = "1.0.0"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val application: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            launch {
                settingsRepository.updateIntervalFlow.collect { hours ->
                    _uiState.update { it.copy(updateIntervalHours = hours) }
                }
            }
            launch {
                settingsRepository.autoUpdateFlow.collect { auto ->
                    _uiState.update { it.copy(autoUpdate = auto) }
                }
            }
            launch {
                settingsRepository.maxBackupsFlow.collect { max ->
                    _uiState.update { it.copy(maxBackups = max) }
                }
            }
            launch {
                settingsRepository.themeModeFlow.collect { mode ->
                    _uiState.update { it.copy(themeMode = mode) }
                }
            }
        }
    }

    fun setUpdateInterval(hours: Int) {
        viewModelScope.launch {
            settingsRepository.setUpdateInterval(hours)
            rescheduleUpdateCheck(hours)
        }
    }

    fun setAutoUpdate(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoUpdate(enabled)
        }
    }

    fun setMaxBackups(count: Int) {
        viewModelScope.launch {
            settingsRepository.setMaxBackups(count)
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    private fun rescheduleUpdateCheck(intervalHours: Int) {
        val workManager = WorkManager.getInstance(application)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
            intervalHours.toLong(), TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "hosts_update_check",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }
}
