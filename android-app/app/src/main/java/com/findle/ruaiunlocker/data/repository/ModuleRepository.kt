package com.findle.ruaiunlocker.data.repository

import com.findle.ruaiunlocker.data.model.ModuleState
import com.findle.ruaiunlocker.data.model.ModuleStatus
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModuleRepository @Inject constructor() {

    companion object {
        const val MODULE_PATH = "/data/adb/modules/unlocker_zrpb"
        const val MODULE_PROP_PATH = "$MODULE_PATH/module.prop"
        const val DISABLE_FILE_PATH = "$MODULE_PATH/disable"
        const val LOGS_PATH = "$MODULE_PATH/logs.txt"
    }

    suspend fun getModuleStatus(): ModuleStatus = withContext(Dispatchers.IO) {
        val existsResult = Shell.cmd("[ -d $MODULE_PATH ] && echo 'exists'").exec()
        if (!existsResult.isSuccess || existsResult.out.firstOrNull()?.trim() != "exists") {
            return@withContext ModuleStatus(
                state = ModuleState.NOT_INSTALLED,
                version = "",
                versionCode = 0
            )
        }

        val disableResult = Shell.cmd("[ -f $DISABLE_FILE_PATH ] && echo 'disabled'").exec()
        val isDisabled = disableResult.out.firstOrNull()?.trim() == "disabled"

        val propResult = Shell.cmd("cat $MODULE_PROP_PATH").exec()
        val props = propResult.out.joinToString("\n")

        val version = extractProp(props, "version") ?: ""
        val versionCode = extractProp(props, "versionCode")?.toIntOrNull() ?: 0

        ModuleStatus(
            state = if (isDisabled) ModuleState.DISABLED else ModuleState.INSTALLED,
            version = version,
            versionCode = versionCode
        )
    }

    suspend fun enableModule(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            Shell.cmd("rm -f $DISABLE_FILE_PATH").exec()
            Unit
        }
    }

    suspend fun disableModule(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            Shell.cmd("touch $DISABLE_FILE_PATH").exec()
            Unit
        }
    }

    suspend fun getModuleLogs(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val result = Shell.cmd("cat $LOGS_PATH 2>/dev/null || echo ''").exec()
            result.out.joinToString("\n")
        }
    }

    suspend fun clearModuleLogs(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            Shell.cmd("echo '' > $LOGS_PATH").exec()
            Unit
        }
    }

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val shell = Shell.getShell()
            if (shell.isRoot) {
                return@withContext true
            }
        } catch (e: Throwable) {
        }

        try {
            val result = Shell.cmd("id").exec()
            if (result.isSuccess && result.out.any { it.contains("uid=0") }) {
                return@withContext true
            }
        } catch (e: Throwable) {
        }

        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            if (output.contains("uid=0")) {
                return@withContext true
            }
        } catch (e: Throwable) {
        }

        false
    }

    private fun extractProp(props: String, key: String): String? {
        return props.lines()
            .firstOrNull { it.startsWith("$key=") }
            ?.substringAfter("=")
    }
}
