package com.findle.ruaiunlocker.data.repository

import android.content.Context
import com.findle.ruaiunlocker.data.api.GitHubApiService
import com.findle.ruaiunlocker.data.model.GitHubCommit
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HostsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gitHubApiService: GitHubApiService,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        const val MODULE_HOSTS_PATH = "/data/adb/modules/unlocker_zrpb/system/etc/hosts"
        const val SYSTEM_HOSTS_PATH = "/system/etc/hosts"
        const val ACTION_SCRIPT_PATH = "/data/adb/modules/unlocker_zrpb/action.sh"
    }

    suspend fun getRemoteHostsCommit(): Result<GitHubCommit> = withContext(Dispatchers.IO) {
        runCatching {
            gitHubApiService.getHostsCommits().first()
        }
    }

    suspend fun downloadRemoteHosts(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(GitHubApiService.HOSTS_RAW_URL)
                .build()
            val response = runCatching { okHttpClient.newCall(request).execute() }.getOrNull()
            if (response != null && response.isSuccessful) {
                val body = response.body?.string()
                if (!body.isNullOrBlank() && body.contains("#")) {
                    return@runCatching body
                }
            }

            val tmpFile = "/data/local/tmp/hosts_dl.tmp"
            val dlResult = Shell.cmd(
                "if command -v curl >/dev/null 2>&1; then",
                "    curl -k -L -o '$tmpFile' '${GitHubApiService.HOSTS_RAW_URL}'",
                "elif command -v wget >/dev/null 2>&1; then",
                "    wget --no-check-certificate -O '$tmpFile' '${GitHubApiService.HOSTS_RAW_URL}'",
                "else",
                "    busybox wget -O '$tmpFile' '${GitHubApiService.HOSTS_RAW_URL}'",
                "fi",
                "cat '$tmpFile' && rm -f '$tmpFile'"
            ).exec()

            if (dlResult.isSuccess && dlResult.out.isNotEmpty()) {
                val content = dlResult.out.joinToString("\n")
                if (content.isNotBlank()) {
                    return@runCatching content
                }
            }

            throw IllegalStateException("Не удалось скачать hosts")
        }
    }

    suspend fun getLocalHosts(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val result = Shell.cmd("cat $MODULE_HOSTS_PATH").exec()
            if (result.isSuccess && result.out.isNotEmpty()) {
                result.out.joinToString("\n")
            } else {
                val sysResult = Shell.cmd("cat $SYSTEM_HOSTS_PATH").exec()
                if (sysResult.isSuccess) {
                    sysResult.out.joinToString("\n")
                } else {
                    throw IllegalStateException(result.err.joinToString("\n"))
                }
            }
        }
    }

    suspend fun writeLocalHosts(content: String): Result<Unit> = withContext(Dispatchers.IO) {
        writeLocalHostsFromBytes(content.toByteArray())
    }

    suspend fun writeLocalHostsFromBytes(content: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cacheFile = File(context.cacheDir, "hosts_update.tmp")
            cacheFile.writeBytes(content)

            val cmd = Shell.cmd(
                "mkdir -p /data/adb/modules/unlocker_zrpb/system/etc",
                "chmod 666 '${cacheFile.absolutePath}'",
                "cat '${cacheFile.absolutePath}' > $MODULE_HOSTS_PATH",
                "chmod 644 $MODULE_HOSTS_PATH",
                "cat '${cacheFile.absolutePath}' > $SYSTEM_HOSTS_PATH",
                "chmod 644 $SYSTEM_HOSTS_PATH",
                "echo \"[$(date '+%Y-%m-%d %H:%M:%S')] Hosts успешно обновлён через приложение (${content.size} байт)\" >> /data/adb/modules/unlocker_zrpb/logs.txt"
            ).exec()

            cacheFile.delete()

            if (!cmd.isSuccess) {
                throw IllegalStateException(cmd.err.joinToString("\n"))
            }
            Unit
        }
    }

    suspend fun runActionScript(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val check = Shell.cmd("[ -f $ACTION_SCRIPT_PATH ] && echo 'exists'").exec()
            if (check.out.firstOrNull()?.trim() != "exists") {
                throw IllegalStateException("Скрипт action.sh не найден")
            }
            val result = Shell.cmd("sh $ACTION_SCRIPT_PATH").exec()
            if (!result.isSuccess) {
                throw IllegalStateException(result.err.joinToString("\n"))
            }
            result.out.joinToString("\n")
        }
    }

    suspend fun getLocalHostsSize(): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val result = Shell.cmd("stat -c%s $MODULE_HOSTS_PATH").exec()
            if (result.isSuccess) {
                result.out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
            } else {
                0L
            }
        }
    }
}
