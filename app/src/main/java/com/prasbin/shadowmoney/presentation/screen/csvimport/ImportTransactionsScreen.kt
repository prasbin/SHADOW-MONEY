@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.prasbin.shadowmoney.presentation.screen.csvimport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.prasbin.shadowmoney.data.Money
import com.prasbin.shadowmoney.data.ShadowMoneyDatabase
import com.prasbin.shadowmoney.data.imports.CsvStreamReader
import com.prasbin.shadowmoney.data.imports.ImportPreview
import com.prasbin.shadowmoney.data.imports.ImportRepository
import com.prasbin.shadowmoney.data.imports.ImportRow
import com.prasbin.shadowmoney.data.imports.ImportRowState
import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.data.model.Category
import com.prasbin.shadowmoney.data.statements.StatementSource
import com.prasbin.shadowmoney.data.statements.StatementSummary
import com.prasbin.shadowmoney.presentation.theme.*

/**
 * Local-first real statement ingestion: pick a statement file (CSV or PDF)
 * or paste CSV → content-based source detection confirmed by the user →
 * read-only preview → duplicate review → explicit confirmation → result.
 * The preview is read-only; no financial record exists until the user
 * confirms the final import. Nothing here ever shows "connected".
 */
@Composable
fun ImportTransactionsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val viewModel: ImportTransactionsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = ShadowMoneyDatabase.getInstance(context.applicationContext)
                return ImportTransactionsViewModel(ImportRepository(database)) as T
            }
        }
    )
    val state by viewModel.state.collectAsState()
    var showConfirmDialog by remember { mutableStateOf(false) }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val stream = context.contentResolver.openInputStream(uri)
            if (stream == null) {
                viewModel.reportSourceError("Could not open the selected file")
            } else {
                when (val result = CsvStreamReader.readBytesBounded(stream)) {
                    is CsvStreamReader.BytesReadResult.Bytes ->
                        viewModel.submitStatementBytes(
                            bytes = result.bytes,
                            documentName = statementDisplayName(context, uri)
                        )
                    is CsvStreamReader.BytesReadResult.TooLarge ->
                        viewModel.reportSourceError(
                            "File exceeds the ${CsvStreamReader.MAX_IMPORT_BYTES / (1024 * 1024)} MB import limit"
                        )
                    is CsvStreamReader.BytesReadResult.ReadError ->
                        viewModel.reportSourceError(result.reason)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Real Statement") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NeonCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        bottomBar = {
            val previewState = state as? ImportUiState.PreviewReady
            if (previewState != null) {
                ImportConfirmBar(
                    selectedCount = previewState.selectedImportableCount,
                    onConfirm = { showConfirmDialog = true },
                    onDiscard = { viewModel.startOver() }
                )
            }
        }
    ) { innerPadding ->
        when (val currentState = state) {
            is ImportUiState.Source -> ImportSourceStep(
                onParsePasted = { text -> viewModel.submitCsv(text) },
                onPickFile = {
                    csvLauncher.launch(
                        arrayOf(
                            "application/pdf",
                            "text/csv",
                            "text/comma-separated-values",
                            "text/plain",
                            "text/*",
                            "application/vnd.ms-excel"
                        )
                    )
                },
                modifier = Modifier.padding(innerPadding)
            )

            is ImportUiState.Loading -> ImportLoadingStep(
                message = "Reading and validating statement…",
                modifier = Modifier.padding(innerPadding)
            )

            is ImportUiState.SourceConfirm -> ImportSourceConfirmStep(
                state = currentState,
                onConfirmSource = { source -> viewModel.confirmSource(source) },
                onDiscard = { viewModel.startOver() },
                modifier = Modifier.padding(innerPadding)
            )

            is ImportUiState.PreviewReady -> ImportPreviewStep(
                state = currentState,
                onToggleRow = { rowNumber, selected -> viewModel.toggleRow(rowNumber, selected) },
                onDuplicateDecision = { rowNumber, importRow ->
                    viewModel.setDuplicateDecision(rowNumber, importRow)
                },
                onAccountMapping = { name, accountId -> viewModel.setAccountMapping(name, accountId) },
                onCategoryMapping = { name, categoryId -> viewModel.setCategoryMapping(name, categoryId) },
                onDefaultAccount = { accountId -> viewModel.setDefaultAccount(accountId) },
                modifier = Modifier.padding(innerPadding)
            )

            is ImportUiState.PreviewRejected -> ImportMessageStep(
                title = "Import rejected",
                message = currentState.reason,
                actionLabel = "Start over",
                onAction = { viewModel.startOver() },
                modifier = Modifier.padding(innerPadding)
            )

            is ImportUiState.EmptyInput -> ImportMessageStep(
                title = "Nothing to import",
                message = "No data rows were found in the supplied CSV input.",
                actionLabel = "Start over",
                onAction = { viewModel.startOver() },
                modifier = Modifier.padding(innerPadding)
            )

            is ImportUiState.Importing -> ImportLoadingStep(
                message = "Importing selected transactions…",
                modifier = Modifier.padding(innerPadding)
            )

            is ImportUiState.Success -> ImportSuccessStep(
                success = currentState,
                onDone = { viewModel.startOver() },
                onBack = { navController.popBackStack() },
                onReconciliation = { navController.navigate("connections") },
                modifier = Modifier.padding(innerPadding)
            )

            is ImportUiState.Error -> ImportMessageStep(
                title = "Error",
                message = currentState.message,
                actionLabel = "Start over",
                onAction = { viewModel.clearError() },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }

    if (showConfirmDialog && state is ImportUiState.PreviewReady) {
        val previewState = state as ImportUiState.PreviewReady
        ImportConfirmDialog(
            preview = previewState.preview,
            selectedCount = previewState.selectedImportableCount,
            onConfirm = {
                showConfirmDialog = false
                viewModel.confirmImport()
            },
            onDismiss = { showConfirmDialog = false }
        )
    }
}

@Composable
private fun ImportSourceStep(
    onParsePasted: (String) -> Unit,
    onPickFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pastedText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SystemPanel(title = "Statement source") {
            Text(
                text = "Import your own exported statement (CSV or PDF). " +
                    "You choose the file or paste the text — the app never " +
                    "accesses banks, wallets or any network service, and never " +
                    "stores the file itself.",
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onPickFile,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, NeonCyan)
            ) {
                Text("IMPORT REAL STATEMENT (CSV or PDF, device picker)", color = NeonCyan)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "— or paste CSV text —",
                style = MaterialTheme.typography.labelMedium,
                color = DarkOnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = pastedText,
                onValueChange = { pastedText = it },
                label = { Text("CSV text") },
                placeholder = { Text("date,description,amount,direction,account,category\n2026-09-01,Coffee,120.00,outflow,Wallet,Food") },
                minLines = 5,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = DarkOnSurface,
                    unfocusedTextColor = DarkOnSurface,
                    focusedLabelColor = NeonCyan,
                    unfocusedLabelColor = DarkOnSurfaceVariant,
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = BorderColor
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { onParsePasted(pastedText) },
                enabled = pastedText.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkPrimary)
            ) {
                Text("Preview import")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Maximum input size: ${CsvStreamReader.MAX_IMPORT_BYTES / (1024 * 1024)} MB. " +
                    "The content stays in memory only.",
                style = MaterialTheme.typography.labelSmall,
                color = DarkOnSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SystemPanel(title = "Flow") {
            ImportStepLine("1", "Select statement file (CSV or PDF) or paste CSV")
            ImportStepLine("2", "Confirm source — content detection is a suggestion")
            ImportStepLine("3", "Parse & validate — read-only preview")
            ImportStepLine("4", "Duplicate review — import or skip each match")
            ImportStepLine("5", "Confirmation — you choose what is written")
            ImportStepLine("6", "Result — imported rows become normal transactions")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No database write has happened yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = NeonGreen,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SystemPanel(title = "Required columns") {
            Text(
                text = "CSV required: date, description, amount, direction, account",
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurface
            )
            Text(
                text = "Optional: category, external reference (external_ref / reference / transaction_id), balance",
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurface
            )
            Text(
                text = "PDF statements need a clear table header with date, description " +
                    "and amount (or debit/credit). Lines that do not fit are kept for " +
                    "review — never dropped. Encrypted or image-only PDFs fail safely " +
                    "with a clear message instead of guessing.",
                style = MaterialTheme.typography.labelSmall,
                color = DarkOnSurfaceVariant
            )
        }
    }
}

@Composable
private fun ImportSourceConfirmStep(
    state: ImportUiState.SourceConfirm,
    onConfirmSource: (StatementSource) -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier
) {
    var chosen by remember(state.documentName) {
        mutableStateOf(
            if (state.detection.candidate.isKnown) state.detection.candidate
            else StatementSource.UNKNOWN
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SystemPanel(title = "Confirm statement source") {
            Text(
                text = "Which provider issued this statement? The suggestion below " +
                    "comes from the file's own content only — never the filename. " +
                    "Confirm or change it; nothing is ever labelled connected.",
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Document: ${state.documentName} (${state.format.storageName})",
                style = MaterialTheme.typography.labelMedium,
                color = DarkOnSurface
            )
            Text(
                text = "Content evidence: ${state.detection.evidence}",
                style = MaterialTheme.typography.labelSmall,
                color = if (state.detection.candidate.isKnown) NeonCyan else WarningAmber
            )

            Spacer(modifier = Modifier.height(12.dp))
            StatementSourceOptionRow(
                source = StatementSource.SANIMA,
                chosen = chosen,
                onChoose = { chosen = it }
            )
            StatementSourceOptionRow(
                source = StatementSource.GLOBAL_IME,
                chosen = chosen,
                onChoose = { chosen = it }
            )
            StatementSourceOptionRow(
                source = StatementSource.ESEWA,
                chosen = chosen,
                onChoose = { chosen = it }
            )
            StatementSourceOptionRow(
                source = StatementSource.UNKNOWN,
                chosen = chosen,
                onChoose = { chosen = it }
            )

            if (state.unparsedLines.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${state.unparsedLines.size} unrecognized line(s) will be kept " +
                        "for review in the preview — nothing is silently dropped.",
                    style = MaterialTheme.typography.labelSmall,
                    color = WarningAmber
                )
                state.unparsedLines.take(3).forEach { line ->
                    Text(
                        text = "· $line",
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkOnSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { onConfirmSource(chosen) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkPrimary)
            ) {
                Text(
                    text = if (chosen.isKnown) {
                        "Continue as ${chosen.label}"
                    } else {
                        "Continue — unknown source"
                    }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Imported rows will be recorded as IMPORTED / USER-PROVIDED " +
                    "evidence, never as a live or verified connection.",
                style = MaterialTheme.typography.labelSmall,
                color = DarkOnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onDiscard) {
                Text("Cancel", color = ErrorRed)
            }
        }
    }
}

@Composable
private fun StatementSourceOptionRow(
    source: StatementSource,
    chosen: StatementSource,
    onChoose: (StatementSource) -> Unit
) {
    val selected = chosen == source
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = { onChoose(source) })
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = source.label + if (source == StatementSource.UNKNOWN) " (ask me later)" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) NeonCyan else DarkOnSurface
        )
    }
}

private fun statementDisplayName(context: android.content.Context, uri: android.net.Uri): String {
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                cursor.getString(index)?.let { return it }
            }
        }
    } catch (_: Exception) {
        // fall through to the URI segment
    }
    return uri.lastPathSegment ?: "statement"
}

@Composable
private fun ImportStepLine(number: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = number,
            style = MaterialTheme.typography.labelLarge,
            color = NeonCyan,
            modifier = Modifier.width(24.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = DarkOnSurface
        )
    }
}

@Composable
private fun ImportLoadingStep(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = NeonCyan)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = DarkOnSurfaceVariant)
    }
}

@Composable
private fun ImportMessageStep(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = NeonCyan
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = DarkOnSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))
        OutlinedButton(onClick = onAction, border = BorderStroke(1.dp, NeonCyan)) {
            Text(actionLabel, color = NeonCyan)
        }
    }
}

@Composable
private fun ImportSuccessStep(
    success: ImportUiState.Success,
    onDone: () -> Unit,
    onBack: () -> Unit,
    onReconciliation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "Import complete", style = MaterialTheme.typography.headlineMedium, color = NeonGreen)
        Spacer(modifier = Modifier.height(16.dp))
        SystemPanel(title = "Result") {
            Text(
                text = "${success.importedCount} transactions imported",
                style = MaterialTheme.typography.headlineSmall,
                color = NeonCyan
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Rows in file: ${success.totalRows}",
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurfaceVariant
            )
            Text(
                text = "Imported: ${success.importedCount}",
                style = MaterialTheme.typography.bodySmall,
                color = NeonGreen
            )
            Text(
                text = "Not selected / skipped: ${success.skippedCount}",
                style = MaterialTheme.typography.bodySmall,
                color = WarningAmber
            )
            Text(
                text = "Invalid (never imported): ${success.invalidCount}",
                style = MaterialTheme.typography.bodySmall,
                color = ErrorRed
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Imported rows are ordinary transactions (source = IMPORT_FILE) " +
                    "and appear in normal transaction queries.",
                style = MaterialTheme.typography.labelSmall,
                color = DarkOnSurfaceVariant
            )
        }

        val documentName = success.statementDocumentName
        if (documentName != null) {
            Spacer(modifier = Modifier.height(12.dp))
            SystemPanel(title = "Statement evidence recorded") {
                Text(
                    text = "Document: $documentName",
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurface
                )
                Text(
                    text = "Source: ${success.statementProviderLabel ?: "Unknown source"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurface
                )
                Text(
                    text = "IMPORTED / USER-PROVIDED — recorded as evidence for " +
                        "reconciliation. Never shown as a connected or verified balance.",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        if (documentName != null) {
            Button(
                onClick = onReconciliation,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkPrimary)
            ) {
                Text("VIEW RECONCILIATION")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkPrimary)
        ) {
            Text("Import another file")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, border = BorderStroke(1.dp, BorderColor)) {
            Text("Back to app", color = DarkOnSurfaceVariant)
        }
    }
}

private fun formatStatementDate(epochMs: Long?): String {
    if (epochMs == null) return "unknown"
    return java.time.ZonedDateTime.ofInstant(
        java.time.Instant.ofEpochMilli(epochMs),
        com.prasbin.shadowmoney.data.BudgetCalendar.KATHMANDU_ZONE
    ).toLocalDate().toString()
}

@Composable
private fun ImportPreviewStep(
    state: ImportUiState.PreviewReady,
    onToggleRow: (Int, Boolean) -> Unit,
    onDuplicateDecision: (Int, Boolean) -> Unit,
    onAccountMapping: (String, Long?) -> Unit,
    onCategoryMapping: (String, Long?) -> Unit,
    onDefaultAccount: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val preview = state.preview
    val duplicateRows = preview.rows.filter { it.state == ImportRowState.POSSIBLE_DUPLICATE }
    val otherRows = preview.rows.filter { it.state != ImportRowState.POSSIBLE_DUPLICATE }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                color = DarkSurfaceElevated,
                border = BorderStroke(1.dp, NeonGreen)
            ) {
                Text(
                    text = "PREVIEW ONLY — no database write has happened yet.",
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonGreen,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        val statement = state.statement
        val summary = state.summary
        if (statement != null && summary != null) {
            item {
                SystemPanel(title = "Statement evidence (imported / user-provided)") {
                    Text(
                        text = "Document: ${statement.documentName} · ${statement.format.storageName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkOnSurface
                    )
                    Text(
                        text = "Source: ${state.statementProviderLabel ?: "Unknown source"} " +
                            "— ${statement.detection.evidence}; confirmed by you",
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Period: ${formatStatementDate(summary.periodStartMs)} → " +
                            formatStatementDate(summary.periodEndMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkOnSurface
                    )
                    Text(
                        text = "Rows: ${summary.totalRows} total · ${summary.importableRows} importable · " +
                            "${summary.invalidRows} invalid · ${summary.duplicateRows} possible duplicates",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkOnSurface
                    )
                    Text(
                        text = "Money in: ${Money.formatNpr(summary.moneyInMinor)} · " +
                            "Money out: ${Money.formatNpr(summary.moneyOutMinor)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkOnSurface
                    )
                    summary.endBalanceMinor?.let { balance ->
                        Text(
                            text = "IMPORTED / USER-PROVIDED BALANCE: " + Money.formatNpr(balance),
                            style = MaterialTheme.typography.bodySmall,
                            color = NeonCyan
                        )
                        Text(
                            text = "As of this statement's own period end — not a current or verified bank balance.",
                            style = MaterialTheme.typography.labelSmall,
                            color = DarkOnSurfaceVariant
                        )
                    } ?: Text(
                        text = "IMPORTED / USER-PROVIDED BALANCE: not stated in this document",
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkOnSurfaceVariant
                    )
                    if (summary.unparsedLines > 0) {
                        Text(
                            text = "${summary.unparsedLines} unrecognized line(s) kept for review — never dropped.",
                            style = MaterialTheme.typography.labelSmall,
                            color = WarningAmber
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        if (state.needsAccountAssignment) {
            item {
                SystemPanel(title = "Assign rows to an account") {
                    Text(
                        text = "This document has no account column. Choose which local " +
                            "account these rows belong to — rows stay visibly invalid until " +
                            "you do, and nothing is guessed.",
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ImportMappingDropdown(
                        label = "Statement account",
                        kind = "account (not assigned)",
                        options = state.accounts.map { it.id to it.name } +
                            (null to "Not assigned (rows stay invalid)"),
                        selectedId = state.defaultAccountId,
                        onSelect = { onDefaultAccount(it) }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        item {
            SystemPanel(title = "Summary") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImportStatBox("Total", preview.totalRows, DarkOnSurface, Modifier.weight(1f))
                    ImportStatBox("Valid", preview.validRows, NeonGreen, Modifier.weight(1f))
                    ImportStatBox("Invalid", preview.invalidRows, ErrorRed, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImportStatBox("New", preview.newRows, NeonCyan, Modifier.weight(1f))
                    ImportStatBox("Duplicates", preview.duplicateRows, WarningAmber, Modifier.weight(1f))
                    ImportStatBox("Importable", preview.importableRows, NeonPurple, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Unmatched accounts: " +
                        (preview.unmatchedAccountNames.ifEmpty { listOf("none") }.joinToString(", ")),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (preview.unmatchedAccountNames.isEmpty()) DarkOnSurfaceVariant else WarningAmber
                )
                Text(
                    text = "Unmatched categories: " +
                        (preview.unmatchedCategoryNames.ifEmpty { listOf("none") }.joinToString(", ")),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (preview.unmatchedCategoryNames.isEmpty()) DarkOnSurfaceVariant else WarningAmber
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        val accountKeys = state.accountLabels.keys
        val categoryKeys = state.categoryLabels.keys
        if (accountKeys.isNotEmpty() || categoryKeys.isNotEmpty()) {
            item {
                SystemPanel(title = "Unmatched references") {
                    Text(
                        text = "Nothing is auto-created. Map each name to an existing local " +
                            "record, or leave it unmapped to reject those rows.",
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    accountKeys.forEach { key ->
                        val label = state.accountLabels[key] ?: key
                        ImportMappingDropdown(
                            label = label,
                            kind = "account",
                            options = state.accounts.map { it.id to it.name } +
                                (null to "Reject rows with this account"),
                            selectedId = state.accountMappings[key],
                            onSelect = { onAccountMapping(label, it) }
                        )
                    }
                    categoryKeys.forEach { key ->
                        val label = state.categoryLabels[key] ?: key
                        ImportMappingDropdown(
                            label = label,
                            kind = "category",
                            options = state.categories.map { it.id to it.name } +
                                (null to "Reject rows with this category"),
                            selectedId = state.categoryMappings[key],
                            onSelect = { onCategoryMapping(label, it) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        if (duplicateRows.isNotEmpty()) {
            item {
                SystemPanel(title = "Duplicate review (${duplicateRows.size})") {
                    Text(
                        text = "Possible duplicates are never imported, merged or overwritten " +
                            "automatically. Choose Import or Skip for each.",
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkOnSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            items(duplicateRows, key = { "dup-${it.rowNumber}" }) { row ->
                ImportDuplicateRow(
                    row = row,
                    selected = state.selections[row.rowNumber] == true,
                    onDecision = { onDuplicateDecision(row.rowNumber, it) }
                )
                Divider(color = BorderColor)
            }
            item { Spacer(modifier = Modifier.height(12.dp)) }
        }

        if (otherRows.isNotEmpty()) {
            item {
                Text(
                    text = "Rows (${otherRows.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonCyan,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(otherRows, key = { "row-${it.rowNumber}" }) { row ->
                ImportRowItem(
                    row = row,
                    selected = state.selections[row.rowNumber] == true,
                    onToggle = { onToggleRow(row.rowNumber, it) }
                )
                Divider(color = BorderColor)
            }
        }
    }
}

@Composable
private fun ImportStatBox(label: String, value: Int, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        color = CardColor
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value.toString(), style = MaterialTheme.typography.titleLarge, color = color)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = DarkOnSurfaceVariant)
        }
    }
}

@Composable
private fun ImportMappingDropdown(
    label: String,
    kind: String,
    options: List<Pair<Long?, String>>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedId }?.second
        ?: "Not mapped (rows rejected)"

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = DarkOnSurface)
            Text(text = "unmatched $kind", style = MaterialTheme.typography.labelSmall, color = WarningAmber)
        }
        Spacer(modifier = Modifier.width(8.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.width(200.dp)
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                textStyle = MaterialTheme.typography.labelMedium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = DarkOnSurface,
                    unfocusedTextColor = DarkOnSurface,
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = BorderColor
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { (id, text) ->
                    DropdownMenuItem(
                        text = { Text(text) },
                        onClick = {
                            onSelect(id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportDuplicateRow(
    row: ImportRow,
    selected: Boolean,
    onDecision: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "#${row.rowNumber} · ${row.rawDate} · ${row.rawDescription}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DarkOnSurface
                )
                Text(
                    text = "${row.rawDirection} · ${row.rawAmount} · ${row.rawAccount}" +
                        (row.rawCategory?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkOnSurfaceVariant
                )
                Text(
                    text = row.duplicate?.reason ?: "Possible duplicate",
                    style = MaterialTheme.typography.labelSmall,
                    color = WarningAmber
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    onClick = { onDecision(true) },
                    border = BorderStroke(1.dp, if (selected) NeonGreen else BorderColor),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (selected) NeonGreen else DarkOnSurfaceVariant
                    )
                ) {
                    Text("Import")
                }
                OutlinedButton(
                    onClick = { onDecision(false) },
                    border = BorderStroke(1.dp, if (!selected) WarningAmber else BorderColor),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (!selected) WarningAmber else DarkOnSurfaceVariant
                    )
                ) {
                    Text("Skip")
                }
            }
        }
    }
}

@Composable
private fun ImportRowItem(
    row: ImportRow,
    selected: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (row.isImportable()) {
            Checkbox(checked = selected, onCheckedChange = onToggle)
        } else {
            Spacer(modifier = Modifier.width(48.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "#${row.rowNumber} · ${row.rawDate} · ${row.rawDescription}",
                style = MaterialTheme.typography.bodyMedium,
                color = DarkOnSurface
            )
            val amountLabel = row.amountMinor?.let { Money.formatNpr(it) } ?: row.rawAmount
            Text(
                text = "$amountLabel · ${row.rawDirection} · ${row.rawAccount}" +
                    (row.rawCategory?.let { " · $it" } ?: " · no category"),
                style = MaterialTheme.typography.labelSmall,
                color = DarkOnSurfaceVariant
            )
            if (row.unmatchedAccountName != null) {
                Text(
                    text = "Unmatched account: ${row.unmatchedAccountName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = WarningAmber
                )
            }
            if (row.unmatchedCategoryName != null) {
                Text(
                    text = "Unmatched category: ${row.unmatchedCategoryName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = WarningAmber
                )
            }
            row.invalidReasons.forEach { reason ->
                Text(
                    text = reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = ErrorRed
                )
            }
        }
        ImportStateChip(state = row.state)
    }
}

@Composable
private fun ImportStateChip(state: ImportRowState) {
    val (label, color) = when (state) {
        ImportRowState.NEW -> "NEW" to NeonGreen
        ImportRowState.POSSIBLE_DUPLICATE -> "DUPLICATE" to WarningAmber
        ImportRowState.INVALID -> "INVALID" to ErrorRed
    }
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
        color = DarkSurfaceElevated,
        border = BorderStroke(1.dp, color)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun ImportConfirmBar(
    selectedCount: Int,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DarkSurface,
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "No write yet — $selectedCount selected",
                style = MaterialTheme.typography.labelMedium,
                color = DarkOnSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDiscard) {
                Text("Discard", color = ErrorRed)
            }
            Button(
                onClick = onConfirm,
                enabled = selectedCount > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan,
                    contentColor = DarkPrimary,
                    disabledContainerColor = DarkSurfaceElevated,
                    disabledContentColor = DarkOnSurfaceVariant
                )
            ) {
                Text("Import selected ($selectedCount)")
            }
        }
    }
}

@Composable
private fun ImportConfirmDialog(
    preview: ImportPreview,
    selectedCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceVariant,
        title = { Text("Confirm import", color = NeonCyan) },
        text = {
            Column {
                ImportSummaryLine("Rows in file", preview.totalRows)
                ImportSummaryLine("Valid rows", preview.validRows)
                ImportSummaryLine("Invalid rows", preview.invalidRows)
                ImportSummaryLine("New rows", preview.newRows)
                ImportSummaryLine("Duplicate candidates", preview.duplicateRows)
                Spacer(modifier = Modifier.height(6.dp))
                ImportSummaryLine("Selected rows to import", selectedCount)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Only the selected valid rows become normal financial " +
                        "transactions. Nothing has been written yet.",
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkOnSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = selectedCount > 0,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkPrimary)
            ) {
                Text("Import selected transactions")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = DarkOnSurfaceVariant)
            }
        }
    )
}

@Composable
private fun ImportSummaryLine(label: String, value: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = DarkOnSurfaceVariant)
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = DarkOnSurface,
            fontWeight = FontWeight.Bold
        )
    }
}
