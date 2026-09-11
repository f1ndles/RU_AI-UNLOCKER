package com.findle.ruaiunlocker.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findle.ruaiunlocker.data.model.BackupEntry
import com.findle.ruaiunlocker.data.model.HostsInfo
import com.findle.ruaiunlocker.data.model.ModuleState
import com.findle.ruaiunlocker.data.model.ModuleStatus
import com.findle.ruaiunlocker.data.repository.HostsRepository
import com.findle.ruaiunlocker.data.repository.SettingsRepository
import com.findle.ruaiunlocker.domain.AppUpdateChecker
import com.findle.ruaiunlocker.domain.BackupManager
import com.findle.ruaiunlocker.domain.HostsParser
import com.findle.ruaiunlocker.domain.ModuleManager
import com.findle.ruaiunlocker.domain.UpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val moduleStatus: ModuleStatus? = null,
    val hostsInfo: HostsInfo? = null,
    val hasUpdate: Boolean = false,
    val currentDate: String = "",
    val remoteDate: String = "",
    val hasAppUpdate: Boolean = false,
    val newAppVersion: String = "",
    val appChangelog: String = "",
    val appDownloadUrl: String = "",
    val appReleaseUrl: String = "",
    val isUpdating: Boolean = false,
    val isRollingBack: Boolean = false,
    val backups: List<BackupEntry> = emptyList(),
    val showBackupDialog: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val hasRoot: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val moduleManager: ModuleManager,
    private val updateChecker: UpdateChecker,
    private val appUpdateChecker: AppUpdateChecker,
    private val backupManager: BackupManager,
    private val hostsParser: HostsParser,
    private val hostsRepository: HostsRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val hasRoot = moduleManager.checkRoot()
            if (!hasRoot) {
                _uiState.update { it.copy(isLoading = false, hasRoot = false) }
                return@launch
            }

            val moduleStatus = moduleManager.getStatus()

            var hostsInfo: HostsInfo? = null
            hostsRepository.getLocalHosts().onSuccess { content ->
                hostsInfo = hostsParser.parseHostsContent(content)
            }

            var hasUpdate = false
            var currentDate = hostsInfo?.lastUpdate ?: ""
            var remoteDate = ""

            updateChecker.checkForUpdate().onSuccess { result ->
                hasUpdate = result.hasUpdate
                if (result.currentDate.isNotEmpty()) currentDate = result.currentDate
                remoteDate = result.remoteDate
            }

            val backups = backupManager.getBackups().getOrNull() ?: emptyList()

            val appUpdateRes = appUpdateChecker.checkForAppUpdate().getOrNull()
            val hasAppUpdate = appUpdateRes?.hasUpdate == true
            val newAppVersion = appUpdateRes?.remoteVersion ?: ""
            val appChangelog = appUpdateRes?.changelog ?: ""
            val appDownloadUrl = appUpdateRes?.downloadUrl ?: ""
            val appReleaseUrl = appUpdateRes?.htmlUrl ?: ""

            _uiState.update {
                it.copy(
                    isLoading = false,
                    hasRoot = true,
                    moduleStatus = moduleStatus,
                    hostsInfo = hostsInfo,
                    hasUpdate = hasUpdate,
                    currentDate = currentDate,
                    remoteDate = remoteDate,
                    hasAppUpdate = hasAppUpdate,
                    newAppVersion = newAppVersion,
                    appChangelog = appChangelog,
                    appDownloadUrl = appDownloadUrl,
                    appReleaseUrl = appReleaseUrl,
                    backups = backups
                )
            }
        }
    }

    fun dismissAppUpdate() {
        _uiState.update { it.copy(hasAppUpdate = false) }
    }

    fun updateHosts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }

            backupManager.createBackup()

            updateChecker.performUpdate()
                .onSuccess { newDate ->
                    _uiState.update {
                        it.copy(
                            isUpdating = false,
                            successMessage = "Hosts обновлён до версии от $newDate",
                            hasUpdate = false,
                            currentDate = newDate
                        )
                    }
                    loadData()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isUpdating = false,
                            errorMessage = error.message ?: "Ошибка обновления hosts"
                        )
                    }
                }
        }
    }

    fun rollbackHosts(backup: BackupEntry) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRollingBack = true, showBackupDialog = false) }

            backupManager.restoreBackup(backup)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isRollingBack = false,
                            successMessage = "Hosts откачен на версию от ${backup.date}"
                        )
                    }
                    loadData()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isRollingBack = false,
                            errorMessage = error.message
                        )
                    }
                }
        }
    }

    fun toggleModule() {
        viewModelScope.launch {
            val currentState = _uiState.value.moduleStatus?.state ?: return@launch
            val enable = currentState == ModuleState.DISABLED

            moduleManager.toggleModule(enable)
                .onSuccess {
                    val newState = if (enable) ModuleState.INSTALLED else ModuleState.DISABLED
                    _uiState.update {
                        it.copy(
                            moduleStatus = it.moduleStatus?.copy(state = newState),
                            successMessage = if (enable) "Модуль включён" else "Модуль отключён"
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message) }
                }
        }
    }

    fun showBackupDialog() {
        _uiState.update { it.copy(showBackupDialog = true) }
    }

    fun hideBackupDialog() {
        _uiState.update { it.copy(showBackupDialog = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }

    fun runActionScript() {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }
            hostsRepository.runActionScript()
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isUpdating = false,
                            successMessage = "action.sh выполнен успешно"
                        )
                    }
                    loadData()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isUpdating = false,
                            errorMessage = error.message ?: "Ошибка выполнения action.sh"
                        )
                    }
                }
        }
    }
}
