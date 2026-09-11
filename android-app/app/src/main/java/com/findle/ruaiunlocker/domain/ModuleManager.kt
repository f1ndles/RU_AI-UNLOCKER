package com.findle.ruaiunlocker.domain

import com.findle.ruaiunlocker.data.model.ModuleStatus
import com.findle.ruaiunlocker.data.repository.ModuleRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModuleManager @Inject constructor(
    private val moduleRepository: ModuleRepository
) {
    suspend fun getStatus(): ModuleStatus {
        return moduleRepository.getModuleStatus()
    }

    suspend fun toggleModule(enable: Boolean): Result<Unit> {
        return if (enable) {
            moduleRepository.enableModule()
        } else {
            moduleRepository.disableModule()
        }
    }

    suspend fun getLogs(): Result<String> {
        return moduleRepository.getModuleLogs()
    }

    suspend fun clearLogs(): Result<Unit> {
        return moduleRepository.clearModuleLogs()
    }

    suspend fun checkRoot(): Boolean {
        return moduleRepository.isRootAvailable()
    }
}
