package com.findle.ruaiunlocker.domain

import com.findle.ruaiunlocker.data.repository.HostsRepository
import com.findle.ruaiunlocker.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateResult(
    val hasUpdate: Boolean,
    val currentDate: String,
    val remoteDate: String,
    val remoteSha: String
)

@Singleton
class UpdateChecker @Inject constructor(
    private val hostsRepository: HostsRepository,
    private val hostsParser: HostsParser,
    private val settingsRepository: SettingsRepository
) {
    suspend fun checkForUpdate(): Result<UpdateResult> = runCatching {
        val localContent = hostsRepository.getLocalHosts().getOrDefault("")
        val localInfo = hostsParser.parseHostsContent(localContent)
        var currentDate = localInfo.lastUpdate
        if (currentDate.isBlank()) {
            currentDate = settingsRepository.lastKnownDateFlow.first()
        }

        val remoteContent = hostsRepository.downloadRemoteHosts().getOrThrow()
        val remoteInfo = hostsParser.parseHostsContent(remoteContent)
        val remoteDate = remoteInfo.lastUpdate

        val remoteCommit = hostsRepository.getRemoteHostsCommit().getOrNull()
        val remoteSha = remoteCommit?.sha ?: ""
        val savedSha = settingsRepository.lastKnownShaFlow.first()

        val hasUpdate = when {
            remoteDate.isNotBlank() && currentDate.isNotBlank() && remoteDate != currentDate -> true
            savedSha.isNotBlank() && remoteSha.isNotBlank() && savedSha != remoteSha -> true
            else -> false
        }

        UpdateResult(
            hasUpdate = hasUpdate,
            currentDate = currentDate,
            remoteDate = remoteDate,
            remoteSha = remoteSha
        )
    }

    suspend fun performUpdate(): Result<String> = runCatching {
        val remoteContent = hostsRepository.downloadRemoteHosts().getOrThrow()
        val remoteInfo = hostsParser.parseHostsContent(remoteContent)

        hostsRepository.writeLocalHostsFromBytes(remoteContent.toByteArray()).getOrThrow()

        if (remoteInfo.lastUpdate.isNotBlank()) {
            settingsRepository.setLastKnownDate(remoteInfo.lastUpdate)
        }

        val remoteCommit = hostsRepository.getRemoteHostsCommit().getOrNull()
        if (remoteCommit != null) {
            settingsRepository.setLastKnownSha(remoteCommit.sha)
        }

        remoteInfo.lastUpdate.ifBlank { "успешно" }
    }
}
