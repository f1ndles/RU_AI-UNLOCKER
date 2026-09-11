package com.findle.ruaiunlocker.ui.screens.hosts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.findle.ruaiunlocker.data.model.HostEntry
import com.findle.ruaiunlocker.data.model.HostsInfo
import com.findle.ruaiunlocker.data.repository.HostsRepository
import com.findle.ruaiunlocker.domain.HostsParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HostsUiState(
    val isLoading: Boolean = true,
    val hostsInfo: HostsInfo? = null,
    val searchQuery: String = "",
    val filteredEntries: List<HostEntry> = emptyList(),
    val showAddDialog: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class HostsViewModel @Inject constructor(
    private val hostsRepository: HostsRepository,
    private val hostsParser: HostsParser
) : ViewModel() {

    private val _uiState = MutableStateFlow(HostsUiState())
    val uiState: StateFlow<HostsUiState> = _uiState.asStateFlow()

    private var rawContent: String = ""

    init {
        loadHosts()
    }

    fun loadHosts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            hostsRepository.getLocalHosts()
                .onSuccess { content ->
                    rawContent = content
                    val info = hostsParser.parseHostsContent(content)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            hostsInfo = info,
                            filteredEntries = info.entries
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

    fun search(query: String) {
        _uiState.update { state ->
            val filtered = if (query.isBlank()) {
                state.hostsInfo?.entries ?: emptyList()
            } else {
                state.hostsInfo?.entries?.filter { entry ->
                    entry.domain.contains(query, ignoreCase = true) ||
                        entry.ip.contains(query, ignoreCase = true)
                } ?: emptyList()
            }
            state.copy(searchQuery = query, filteredEntries = filtered)
        }
    }

    fun addEntry(ip: String, domain: String) {
        viewModelScope.launch {
            val newContent = hostsParser.addEntry(rawContent, ip, domain)
            hostsRepository.writeLocalHostsFromBytes(newContent.toByteArray())
                .onSuccess {
                    _uiState.update { it.copy(showAddDialog = false, successMessage = "Запись добавлена") }
                    loadHosts()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message) }
                }
        }
    }

    fun deleteEntry(domain: String) {
        viewModelScope.launch {
            val newContent = hostsParser.removeEntry(rawContent, domain)
            hostsRepository.writeLocalHostsFromBytes(newContent.toByteArray())
                .onSuccess {
                    _uiState.update { it.copy(successMessage = "Запись удалена") }
                    loadHosts()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message) }
                }
        }
    }

    fun showAddDialog() {
        _uiState.update { it.copy(showAddDialog = true) }
    }

    fun hideAddDialog() {
        _uiState.update { it.copy(showAddDialog = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }
}
