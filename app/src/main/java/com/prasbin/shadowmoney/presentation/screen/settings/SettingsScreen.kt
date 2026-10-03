@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.prasbin.shadowmoney.presentation.screen.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prasbin.shadowmoney.data.ShadowMoneyDatabase
import com.prasbin.shadowmoney.data.backup.APP_SCHEMA_VERSION
import com.prasbin.shadowmoney.data.backup.BACKUP_CHECKSUM_ALGORITHM
import com.prasbin.shadowmoney.data.backup.BACKUP_FORMAT_NAME
import com.prasbin.shadowmoney.data.backup.BACKUP_FORMAT_VERSION
import com.prasbin.shadowmoney.data.backup.BackupRecordCounts
import com.prasbin.shadowmoney.data.backup.BackupRepository
import com.prasbin.shadowmoney.data.backup.SafBackupFileIo
import com.prasbin.shadowmoney.presentation.theme.DarkOnSurface
import com.prasbin.shadowmoney.presentation.theme.DarkOnSurfaceVariant
import com.prasbin.shadowmoney.presentation.theme.DarkSurface
import com.prasbin.shadowmoney.presentation.theme.ErrorRed
import com.prasbin.shadowmoney.presentation.theme.NeonCyan
import com.prasbin.shadowmoney.presentation.theme.NeonGreen
import com.prasbin.shadowmoney.presentation.theme.WarningAmber
import java.text.DateFormat
import java.util.Date

/**
 * Settings screen with the local Backup & Restore section: SAF export
 * (CreateDocument), SAF import (OpenDocument), a read-only validated preview
 * with record counts, the mandatory destructive-restore warning, and explicit
 * confirmation before any database write. The Secret Target is never part of
 * backups and is not referenced anywhere in this screen.
 */
@Composable
fun SettingsScreen(navController: androidx.navigation.NavHostController? = null) {
    val context = LocalContext.current
    val viewModel: BackupViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = ShadowMoneyDatabase.getInstance(context.applicationContext)
                return BackupViewModel(
                    BackupRepository(
                        database = database,
                        fileIo = SafBackupFileIo(context.contentResolver)
                    )
                ) as T
            }
        }
    )
    val state by viewModel.state.collectAsState()
    val infoCounts by viewModel.infoCounts.collectAsState()
    var showRestoreDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshInfo()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportTo(uri.toString())
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.prepareRestore(uri.toString())
        }
    }

    val preview = state as? BackupUiState.PreviewReady
    if (preview != null && showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Restore this backup?", color = NeonCyan) },
            text = {
                Column {
                    Text(
                        text = preview.warning,
                        color = ErrorRed,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    BackupCountsList(counts = preview.candidate.counts)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreDialog = false
                        viewModel.confirmRestore()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Replace records", color = androidx.compose.ui.graphics.Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showRestoreDialog = false
                        viewModel.reset()
                    }
                ) {
                    Text("Cancel", color = NeonCyan)
                }
            },
            containerColor = DarkSurface
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "SYSTEM",
                            color = NeonCyan,
                            style = MaterialTheme.typography.titleLarge,
                            letterSpacing = 2f.sp
                        )
                        Text(
                            "MODULES · DATA · PRIVACY",
                            color = DarkOnSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SHADOW MONEY v${com.prasbin.shadowmoney.BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.titleMedium,
                color = DarkOnSurface,
                letterSpacing = 0.5f.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Personal financial system · local-first · offline",
                style = MaterialTheme.typography.labelSmall,
                color = DarkOnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))

            com.prasbin.shadowmoney.presentation.theme.SystemSectionHeader("Financial system")
            com.prasbin.shadowmoney.presentation.theme.SystemListRow(
                title = "Budgets",
                subtitle = "Monthly limits and category spending",
                onClick = { navController?.navigate("budgets") }
            )
            com.prasbin.shadowmoney.presentation.theme.SystemListRow(
                title = "Goals",
                subtitle = "Targets, progress, Secret Target",
                onClick = { navController?.navigate("goals") }
            )
            com.prasbin.shadowmoney.presentation.theme.SystemListRow(
                title = "Telecom",
                subtitle = "SIMs, packages, recurring cost",
                onClick = { navController?.navigate("telecom") }
            )
            com.prasbin.shadowmoney.presentation.theme.SystemListRow(
                title = "Opportunities",
                subtitle = "Income-growth pipeline",
                onClick = { navController?.navigate("opportunities") }
            )
            com.prasbin.shadowmoney.presentation.theme.SystemListRow(
                title = "Connections",
                subtitle = "Official bank/wallet links · honest status",
                onClick = { navController?.navigate("connections") }
            )

            com.prasbin.shadowmoney.presentation.theme.SystemSectionHeader("Analysis")
            com.prasbin.shadowmoney.presentation.theme.SystemListRow(
                title = "Assistant",
                subtitle = "Ask about your financial records",
                onClick = { navController?.navigate("assistant") }
            )

            com.prasbin.shadowmoney.presentation.theme.SystemSectionHeader("Data")
            com.prasbin.shadowmoney.presentation.theme.SystemListRow(
                title = "Import transactions (CSV)",
                subtitle = "Preview, duplicate check, confirm",
                onClick = { navController?.navigate("import") }
            )
            Spacer(modifier = Modifier.height(8.dp))

            BackupSection(
                state = state,
                infoCounts = infoCounts,
                onExport = {
                    exportLauncher.launch("shadow-money-backup.json")
                },
                onRestore = {
                    restoreLauncher.launch(
                        arrayOf("application/json", "text/plain", "text/*", "application/octet-stream")
                    )
                },
                onConfirmRestore = { showRestoreDialog = true },
                onDismiss = { viewModel.reset() }
            )

            com.prasbin.shadowmoney.presentation.theme.SystemSectionHeader("Security & privacy")
            com.prasbin.shadowmoney.presentation.theme.SystemListRow(
                title = "Secret Target",
                subtitle = "Private target · managed in Goals",
                onClick = { navController?.navigate("goals") }
            )
            Text(
                text = "Zero app permissions · no network · no analytics · Secret Target never appears in backups or the assistant.",
                style = MaterialTheme.typography.labelSmall,
                color = DarkOnSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun BackupSection(
    state: BackupUiState,
    infoCounts: BackupRecordCounts?,
    onExport: () -> Unit,
    onRestore: () -> Unit,
    onConfirmRestore: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Backup & Restore",
                style = MaterialTheme.typography.titleMedium,
                color = NeonCyan
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Format: $BACKUP_FORMAT_NAME v$BACKUP_FORMAT_VERSION",
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurfaceVariant
            )
            Text(
                text = "Database schema: v$APP_SCHEMA_VERSION",
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurfaceVariant
            )
            Text(
                text = "Integrity: $BACKUP_CHECKSUM_ALGORITHM checksum, verified on restore",
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurfaceVariant
            )
            Text(
                text = "Backups are plain JSON files; they are not encrypted.",
                style = MaterialTheme.typography.bodySmall,
                color = WarningAmber
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (infoCounts != null) {
                BackupCountsList(counts = infoCounts)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onExport,
                    enabled = state !is BackupUiState.Exporting && state !is BackupUiState.Restoring
                ) {
                    Text("Export backup")
                }
                OutlinedButton(
                    onClick = onRestore,
                    enabled = state !is BackupUiState.Exporting && state !is BackupUiState.Restoring
                ) {
                    Text("Restore backup", color = WarningAmber)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            BackupStateMessage(state, onConfirmRestore, onDismiss)
        }
    }
}

@Composable
private fun BackupCountsList(counts: BackupRecordCounts) {
    val lines = listOf(
        "Accounts" to counts.accounts,
        "Categories" to counts.categories,
        "Transactions" to counts.transactions,
        "Goals" to counts.goals,
        "Budgets" to counts.budgets,
        "Work items" to counts.workItems,
        "Telecom SIMs" to counts.telecomSims,
        "Telecom packages" to counts.telecomPackages,
        "Telecom subscriptions" to counts.telecomSubscriptions,
        "Opportunities" to counts.opportunities
    )
    Column {
        if (counts.total == 0) {
            Text(
                text = "No records to back up",
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurfaceVariant
            )
        } else {
            lines.filter { it.second > 0 }.forEach { (label, count) ->
                Text(
                    text = "$label: $count",
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurfaceVariant
                )
            }
        }
        Text(
            text = "Total records: ${counts.total}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = NeonGreen
        )
    }
}

@Composable
private fun BackupStateMessage(
    state: BackupUiState,
    onConfirmRestore: () -> Unit,
    onDismiss: () -> Unit
) {
    when (state) {
        is BackupUiState.Idle -> Unit
        is BackupUiState.Exporting -> StatusText("Generating backup…", NeonCyan)
        is BackupUiState.ExportSuccess -> {
            StatusText(
                "Backup written. ${state.counts.total} records, created " +
                    DateFormat.getDateTimeInstance().format(Date(state.createdAtEpochMillis)),
                NeonGreen
            )
            Text(
                text = "Checksum: ${state.checksum}",
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )
            TextDismiss(onDismiss)
        }
        is BackupUiState.ExportError -> {
            StatusText("Export failed: ${state.message}", ErrorRed)
            TextDismiss(onDismiss)
        }
        is BackupUiState.Preparing -> StatusText("Validating backup…", NeonCyan)
        is BackupUiState.PreviewReady -> {
            StatusText(
                "Backup validated. Review the record counts, then confirm to replace records.",
                WarningAmber
            )
            Button(
                onClick = onConfirmRestore,
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
            ) {
                Text("Restore…", color = androidx.compose.ui.graphics.Color.White)
            }
        }
        is BackupUiState.PreviewInvalid -> {
            StatusText("Restore rejected: ${state.message}", ErrorRed)
            TextDismiss(onDismiss)
        }
        is BackupUiState.Restoring -> StatusText("Restoring (atomic replace)…", NeonCyan)
        is BackupUiState.RestoreSuccess -> {
            StatusText("Restore complete. ${state.counts.total} records are now active.", NeonGreen)
            TextDismiss(onDismiss)
        }
        is BackupUiState.RestoreError -> {
            StatusText(
                "Restore failed: ${state.message}. The existing records were not changed.",
                ErrorRed
            )
            TextDismiss(onDismiss)
        }
    }
}

@Composable
private fun StatusText(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun TextDismiss(onDismiss: () -> Unit) {
    TextButton(onClick = onDismiss) {
        Text("Dismiss", color = NeonCyan)
    }
}
