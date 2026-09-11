package com.findle.ruaiunlocker.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.findle.ruaiunlocker.R
import com.findle.ruaiunlocker.data.model.ModuleState
import com.findle.ruaiunlocker.ui.components.AppUpdateBanner
import com.findle.ruaiunlocker.ui.components.BackupDialog
import com.findle.ruaiunlocker.ui.components.StatusCard
import com.findle.ruaiunlocker.ui.components.UpdateBanner
import com.findle.ruaiunlocker.ui.theme.Green
import com.findle.ruaiunlocker.ui.theme.Orange
import com.findle.ruaiunlocker.ui.theme.Purple
import com.findle.ruaiunlocker.ui.theme.Red

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccess()
        }
    }

    if (state.showBackupDialog) {
        BackupDialog(
            backups = state.backups,
            onSelect = { viewModel.rollbackHosts(it) },
            onDismiss = { viewModel.hideBackupDialog() }
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !state.hasRoot) {
                viewModel.loadData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            )
        }
    ) { padding ->
        if (!state.hasRoot && !state.isLoading) {
            NoRootScreen(
                onRetry = { viewModel.loadData() },
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }

        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Purple)
            }
            return@Scaffold
        }

        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = { viewModel.loadData() },
            modifier = Modifier.padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                item {
                    val moduleState = state.moduleStatus?.state
                    val (statusText, statusIcon, statusColor) = when (moduleState) {
                        ModuleState.INSTALLED -> Triple(
                            stringResource(R.string.module_installed),
                            Icons.Filled.CheckCircle,
                            Green
                        )
                        ModuleState.DISABLED -> Triple(
                            stringResource(R.string.module_disabled),
                            Icons.Filled.Warning,
                            Orange
                        )
                        else -> Triple(
                            stringResource(R.string.module_not_installed),
                            Icons.Filled.Error,
                            Red
                        )
                    }
                    StatusCard(
                        title = stringResource(R.string.module_status),
                        value = statusText,
                        icon = statusIcon,
                        iconTint = statusColor
                    )
                }

                if (state.hasAppUpdate) {
                    item {
                        AppUpdateBanner(
                            newVersion = state.newAppVersion,
                            changelog = state.appChangelog,
                            downloadUrl = state.appDownloadUrl,
                            releaseUrl = state.appReleaseUrl,
                            onDismiss = { viewModel.dismissAppUpdate() }
                        )
                    }
                }

                if (state.hasUpdate) {
                    item {
                        UpdateBanner(
                            currentDate = state.currentDate,
                            remoteDate = state.remoteDate,
                            onUpdate = { viewModel.updateHosts() },
                            onDismiss = {}
                        )
                    }
                }

                item {
                    StatusCard(
                        title = stringResource(R.string.current_version),
                        value = state.currentDate.ifEmpty { "—" },
                        icon = Icons.Filled.CalendarMonth,
                        iconTint = Purple
                    )
                }

                item {
                    StatusCard(
                        title = stringResource(R.string.entries_count),
                        value = (state.hostsInfo?.entriesCount ?: 0).toString(),
                        icon = Icons.Filled.FormatListNumbered,
                        iconTint = Purple
                    )
                }

                if (state.moduleStatus?.state != ModuleState.NOT_INSTALLED) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (state.moduleStatus?.state == ModuleState.INSTALLED)
                                        stringResource(R.string.disable_module)
                                    else
                                        stringResource(R.string.enable_module),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Switch(
                                    checked = state.moduleStatus?.state == ModuleState.INSTALLED,
                                    onCheckedChange = { viewModel.toggleModule() }
                                )
                            }
                        }
                    }

                    if (state.moduleStatus?.state == ModuleState.DISABLED) {
                        item {
                            Text(
                                text = stringResource(R.string.reboot_required),
                                style = MaterialTheme.typography.labelMedium,
                                color = Orange,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { viewModel.updateHosts() },
                            modifier = Modifier.weight(1f),
                            enabled = !state.isUpdating && !state.isRollingBack,
                            colors = ButtonDefaults.buttonColors(containerColor = Purple)
                        ) {
                            if (state.isUpdating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Filled.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = "  " + stringResource(R.string.update_hosts),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        OutlinedButton(
                            onClick = { viewModel.showBackupDialog() },
                            modifier = Modifier.weight(1f),
                            enabled = !state.isUpdating && !state.isRollingBack
                        ) {
                            Icon(
                                Icons.Filled.History,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "  " + stringResource(R.string.rollback_hosts),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun NoRootScreen(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Red
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.no_root),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.no_root_desc),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = Purple)
        ) {
            Text(
                text = stringResource(R.string.retry),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
