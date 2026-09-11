package com.findle.ruaiunlocker.domain

import com.findle.ruaiunlocker.data.model.BackupEntry
import com.findle.ruaiunlocker.data.repository.HostsRepository
import com.findle.ruaiunlocker.data.repository.SettingsRepository
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    private val hostsRepository: HostsRepository,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        const val BACKUP_DIR = "/data/adb/modules/unlocker_zrpb/backups"
    }

    suspend fun createBackup(): Result<BackupEntry> = withContext(Dispatchers.IO) {
        runCatching {
            Shell.cmd("mkdir -p $BACKUP_DIR").exec()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val backupName = "hosts_$timestamp"
            val backupPath = "$BACKUP_DIR/$backupName"

            Shell.cmd("cp -f ${HostsRepository.MODULE_HOSTS_PATH} $backupPath").exec()
            Shell.cmd("chmod 644 $backupPath").exec()

            val sizeResult = Shell.cmd("stat -c%s $backupPath").exec()
            val size = sizeResult.out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L

            cleanOldBackups()

            BackupEntry(
                fileName = backupName,
                date = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date()),
                size = size,
                path = backupPath
            )
        }
    }

    suspend fun getBackups(): Result<List<BackupEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val result = Shell.cmd("ls -la $BACKUP_DIR/ 2>/dev/null").exec()
            if (!result.isSuccess) return@runCatching emptyList()

            result.out
                .filter { it.contains("hosts_") }
                .mapNotNull { line ->
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size >= 8) {
                        val fileName = parts.last()
                        val size = parts.getOrNull(4)?.toLongOrNull() ?: 0L
                        val dateStr = fileName.removePrefix("hosts_")
                            .replace("_", " ")

                        val formattedDate = try {
                            val parsed = SimpleDateFormat("yyyyMMdd HHmmss", Locale.getDefault()).parse(dateStr)
                            SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(parsed!!)
                        } catch (e: Exception) {
                            dateStr
                        }

                        BackupEntry(
                            fileName = fileName,
                            date = formattedDate,
                            size = size,
                            path = "$BACKUP_DIR/$fileName"
                        )
                    } else null
                }
                .sortedByDescending { it.fileName }
        }
    }

    suspend fun restoreBackup(backup: BackupEntry): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val content = Shell.cmd("cat ${backup.path}").exec()
            if (content.isSuccess) {
                val hostsContent = content.out.joinToString("\n")
                hostsRepository.writeLocalHostsFromBytes(hostsContent.toByteArray()).getOrThrow()
            } else {
                throw IllegalStateException("Failed to read backup")
            }
        }
    }

    private suspend fun cleanOldBackups() {
        val maxBackups = settingsRepository.maxBackupsFlow.first()
        val backups = getBackups().getOrNull() ?: return

        if (backups.size > maxBackups) {
            backups.drop(maxBackups).forEach { backup ->
                Shell.cmd("rm -f ${backup.path}").exec()
            }
        }
    }
}
