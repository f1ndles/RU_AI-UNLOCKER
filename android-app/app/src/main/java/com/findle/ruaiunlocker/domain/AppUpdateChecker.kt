package com.findle.ruaiunlocker.domain

import com.findle.ruaiunlocker.BuildConfig
import com.findle.ruaiunlocker.data.api.GitHubApiService
import javax.inject.Inject
import javax.inject.Singleton

data class AppUpdateResult(
    val hasUpdate: Boolean,
    val currentVersion: String,
    val remoteVersion: String,
    val changelog: String,
    val downloadUrl: String,
    val htmlUrl: String
)

@Singleton
class AppUpdateChecker @Inject constructor(
    private val gitHubApiService: GitHubApiService
) {
    suspend fun checkForAppUpdate(): Result<AppUpdateResult> = runCatching {
        val currentVersion = BuildConfig.VERSION_NAME
        val response = gitHubApiService.getLatestAppRelease()
        if (!response.isSuccessful || response.body() == null) {
            return@runCatching AppUpdateResult(
                hasUpdate = false,
                currentVersion = currentVersion,
                remoteVersion = "",
                changelog = "",
                downloadUrl = "",
                htmlUrl = ""
            )
        }

        val release = response.body()!!
        val remoteVersion = release.tagName.trimStart('v', 'V')
        val apkAsset = release.assets.find { it.name.endsWith(".apk", ignoreCase = true) }
        val downloadUrl = apkAsset?.browserDownloadUrl ?: ""

        val hasUpdate = isNewerVersion(remoteVersion, currentVersion)

        AppUpdateResult(
            hasUpdate = hasUpdate,
            currentVersion = currentVersion,
            remoteVersion = remoteVersion,
            changelog = release.body,
            downloadUrl = downloadUrl,
            htmlUrl = release.htmlUrl
        )
    }

    private fun isNewerVersion(remote: String, current: String): Boolean {
        if (remote.isBlank()) return false
        val remoteParts = remote.split('.').mapNotNull { it.toIntOrNull() }
        val currentParts = current.split('.').mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return remote != current
    }
}
