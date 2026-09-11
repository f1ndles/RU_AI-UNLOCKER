package com.findle.ruaiunlocker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.findle.ruaiunlocker.R
import com.findle.ruaiunlocker.data.model.BackupEntry

@Composable
fun BackupDialog(
    backups: List<BackupEntry>,
    onSelect: (BackupEntry) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.select_backup))
        },
        text = {
            if (backups.isEmpty()) {
                Text(stringResource(R.string.no_backups))
            } else {
                LazyColumn {
                    items(backups) { backup ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(backup) }
                                .padding(vertical = 12.dp)
                        ) {
                            Text(
                                text = backup.date,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "${backup.size / 1024} KB",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
