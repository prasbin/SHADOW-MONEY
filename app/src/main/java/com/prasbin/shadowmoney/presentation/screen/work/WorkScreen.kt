@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.prasbin.shadowmoney.presentation.screen.work

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.prasbin.shadowmoney.data.Money
import com.prasbin.shadowmoney.data.ShadowMoneyDatabase
import com.prasbin.shadowmoney.data.WorkMath
import com.prasbin.shadowmoney.data.WorkRepository
import com.prasbin.shadowmoney.data.model.WORK_STATUS_ACTIVE
import com.prasbin.shadowmoney.data.model.WORK_STATUS_ARCHIVED
import com.prasbin.shadowmoney.data.model.WORK_STATUS_COMPLETED
import com.prasbin.shadowmoney.data.model.WORK_STATUS_PAUSED
import com.prasbin.shadowmoney.data.model.WorkItem
import com.prasbin.shadowmoney.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val workDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

private fun parseDateToMillis(input: String): Long? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return 0L
    return try {
        val date = java.time.LocalDate.parse(trimmed)
        date.atStartOfDay(com.prasbin.shadowmoney.data.BudgetCalendar.KATHMANDU_ZONE)
            .toInstant()
            .toEpochMilli()
    } catch (error: java.time.format.DateTimeParseException) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkScreen(navController: NavHostController) {
    val context = LocalContext.current
    val viewModel: WorkViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = ShadowMoneyDatabase.getInstance(context.applicationContext)
                return WorkViewModel(
                    WorkRepository(
                        workItemDao = database.workItemDao(),
                        transactionDao = database.transactionDao(),
                        openHelper = database.openHelper
                    )
                ) as T
            }
        }
    )
    val state by viewModel.uiState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var showForm by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<WorkItem?>(null) }
    var archiveTarget by remember { mutableStateOf<WorkItem?>(null) }
    var deleteTarget by remember { mutableStateOf<WorkItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Work Tracker") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        },
        floatingActionButton = {
            if (state is WorkUiState.Content || state is WorkUiState.Empty) {
                FloatingActionButton(
                    onClick = {
                        editingItem = null
                        showForm = true
                    },
                    containerColor = NeonCyan,
                    contentColor = DarkPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add work item")
                }
            }
        }
    ) { innerPadding ->
        when (val currentState = state) {
            is WorkUiState.Loading -> WorkLoading(modifier = Modifier.padding(innerPadding))
            is WorkUiState.Error -> WorkError(
                message = currentState.message,
                modifier = Modifier.padding(innerPadding)
            )
            is WorkUiState.Empty -> WorkEmpty(
                onAdd = {
                    editingItem = null
                    showForm = true
                },
                modifier = Modifier.padding(innerPadding)
            )
            is WorkUiState.Content -> WorkContent(
                state = currentState,
                viewModel = viewModel,
                onOpen = { item -> navController.navigate("work/${item.id}") },
                onEdit = { item ->
                    editingItem = item
                    showForm = true
                },
                onArchive = { item -> archiveTarget = item },
                onDelete = { item -> deleteTarget = item },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }

    if (showForm) {
        WorkFormDialog(
            initialItem = editingItem,
            onDismiss = { showForm = false },
            onSave = { title, description, expectedAmountMinor, deadlineTimestamp, client, existing ->
                if (existing == null) {
                    viewModel.create(title, description, expectedAmountMinor, deadlineTimestamp, client)
                } else {
                    viewModel.update(existing, title, description, expectedAmountMinor, deadlineTimestamp, client)
                }
                showForm = false
            }
        )
    }

    archiveTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { archiveTarget = null },
            containerColor = DarkSurfaceVariant,
            title = { Text("Archive work item", color = NeonCyan) },
            text = { Text("Archive \"${item.title}\"? You can still view it under Archived.", color = DarkOnSurface) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.archive(item.id)
                        archiveTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WarningAmber, contentColor = DarkPrimary)
                ) {
                    Text("Archive")
                }
            },
            dismissButton = {
                TextButton(onClick = { archiveTarget = null }) {
                    Text("Cancel", color = DarkOnSurfaceVariant)
                }
            }
        )
    }

    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = DarkSurfaceVariant,
            title = { Text("Delete work item", color = ErrorRed) },
            text = {
                Text(
                    "Delete \"${item.title}\"? Linked transactions remain as financial records with no work item.",
                    color = DarkOnSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.delete(item)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = DarkOnSurface)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel", color = DarkOnSurfaceVariant)
                }
            }
        )
    }

    errorMessage?.let { message ->
        LaunchedEffect(message) {
            viewModel.clearErrorMessage()
        }
    }
}

@Composable
private fun WorkLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = NeonCyan)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Loading work items…",
            style = MaterialTheme.typography.bodyMedium,
            color = DarkOnSurfaceVariant
        )
    }
}

@Composable
private fun WorkEmpty(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No work items found",
            style = MaterialTheme.typography.headlineMedium,
            color = NeonCyan
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Track jobs, freelance work, and projects. Expected amounts stay separate from actual income.",
            style = MaterialTheme.typography.bodyMedium,
            color = DarkOnSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onAdd,
            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkPrimary)
        ) {
            Text("Add work item")
        }
    }
}

@Composable
private fun WorkError(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Work tracker unavailable",
            style = MaterialTheme.typography.headlineMedium,
            color = ErrorRed
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = DarkOnSurfaceVariant
        )
    }
}

@Composable
private fun WorkContent(
    state: WorkUiState.Content,
    viewModel: WorkViewModel,
    onOpen: (WorkItem) -> Unit,
    onEdit: (WorkItem) -> Unit,
    onArchive: (WorkItem) -> Unit,
    onDelete: (WorkItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(searchQuery, statusFilter) {
        viewModel.setSearchQuery(searchQuery)
        viewModel.setStatusFilter(statusFilter)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search by title") },
                singleLine = true,
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
        }
        item {
            StatusFilterChips(selected = statusFilter, onSelect = { statusFilter = it })
        }
        items(state.items, key = { it.workItem.id }) { view ->
            WorkCard(
                view = view,
                onOpen = { onOpen(view.workItem) },
                onEdit = { onEdit(view.workItem) },
                onArchive = { onArchive(view.workItem) },
                onDelete = { onDelete(view.workItem) }
            )
        }
    }
}

@Composable
private fun StatusFilterChips(selected: Int?, onSelect: (Int?) -> Unit) {
    val options = listOf(
        null to "All",
        WORK_STATUS_ACTIVE to "Active",
        WORK_STATUS_PAUSED to "Paused",
        WORK_STATUS_COMPLETED to "Completed",
        WORK_STATUS_ARCHIVED to "Archived"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NeonCyan,
                    selectedLabelColor = DarkPrimary
                )
            )
        }
    }
}

@Composable
private fun WorkCard(
    view: com.prasbin.shadowmoney.data.WorkItemView,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    val item = view.workItem
    val statusColor = when (item.status) {
        WORK_STATUS_ACTIVE -> NeonGreen
        WORK_STATUS_PAUSED -> WarningAmber
        WORK_STATUS_COMPLETED -> NeonCyan
        WORK_STATUS_ARCHIVED -> DarkOnSurfaceVariant
        else -> DarkOnSurfaceVariant
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onOpen() },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = CardColor,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = DarkOnSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = WorkMath.statusLabel(item.status),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = DarkOnSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onArchive, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Archive,
                        contentDescription = "Archive",
                        tint = WarningAmber,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = ErrorRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (item.description.isNotBlank()) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Text(
                    text = "Expected ${Money.formatNpr(item.expectedAmountMinor)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = DarkOnSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Received ${Money.formatNpr(view.receivedMinor)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = NeonGreen
                )
            }
            Text(
                text = "Remaining expected ${Money.formatNpr(view.remainingExpectedMinor)}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = if (view.remainingExpectedMinor < 0) WarningAmber else DarkOnSurfaceVariant
            )
            Text(
                text = if (item.deadlineTimestamp > 0L) {
                    "Deadline ${workDateFormat.format(Date(item.deadlineTimestamp))} · ${WorkMath.deadlineLabel(view.deadlineStatus)}"
                } else {
                    "No deadline"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (view.deadlineStatus == com.prasbin.shadowmoney.data.DeadlineStatus.OVERDUE) ErrorRed else DarkOnSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkFormDialog(
    initialItem: WorkItem?,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String, expectedAmountMinor: Long, deadlineTimestamp: Long, client: String, existing: WorkItem?) -> Unit
) {
    var title by remember { mutableStateOf(initialItem?.title ?: "") }
    var description by remember { mutableStateOf(initialItem?.description ?: "") }
    var amountText by remember {
        mutableStateOf(
            initialItem?.let { Money.formatNpr(it.expectedAmountMinor).removePrefix("NPR ") } ?: ""
        )
    }
    var deadlineText by remember {
        mutableStateOf(
            initialItem?.deadlineTimestamp?.let { workDateFormat.format(Date(it)) } ?: ""
        )
    }
    var client by remember { mutableStateOf(initialItem?.client ?: "") }
    var titleError by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }
    var deadlineError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceVariant,
        title = {
            Text(
                text = if (initialItem == null) "Add Work Item" else "Edit Work Item",
                color = NeonCyan
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        titleError = false
                    },
                    label = { Text("Title") },
                    isError = titleError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DarkOnSurface,
                        unfocusedTextColor = DarkOnSurface,
                        focusedLabelColor = NeonCyan,
                        unfocusedLabelColor = DarkOnSurfaceVariant,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = BorderColor,
                        errorBorderColor = ErrorRed
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DarkOnSurface,
                        unfocusedTextColor = DarkOnSurface,
                        focusedLabelColor = NeonCyan,
                        unfocusedLabelColor = DarkOnSurfaceVariant,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = BorderColor
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        amountError = false
                    },
                    label = { Text("Expected amount (NPR)") },
                    isError = amountError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DarkOnSurface,
                        unfocusedTextColor = DarkOnSurface,
                        focusedLabelColor = NeonCyan,
                        unfocusedLabelColor = DarkOnSurfaceVariant,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = BorderColor,
                        errorBorderColor = ErrorRed
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = deadlineText,
                    onValueChange = {
                        deadlineText = it
                        deadlineError = false
                    },
                    label = { Text("Deadline (yyyy-MM-dd, optional)") },
                    isError = deadlineError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DarkOnSurface,
                        unfocusedTextColor = DarkOnSurface,
                        focusedLabelColor = NeonCyan,
                        unfocusedLabelColor = DarkOnSurfaceVariant,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = BorderColor,
                        errorBorderColor = ErrorRed
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = client,
                    onValueChange = { client = it },
                    label = { Text("Client / source (optional)") },
                    singleLine = true,
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
                if (titleError) {
                    Text("Title is required", style = MaterialTheme.typography.labelSmall, color = ErrorRed)
                }
                if (amountError) {
                    Text("Enter a valid non-negative amount", style = MaterialTheme.typography.labelSmall, color = ErrorRed)
                }
                if (deadlineError) {
                    Text("Enter a valid date (yyyy-MM-dd) or leave empty", style = MaterialTheme.typography.labelSmall, color = ErrorRed)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amountMinor = com.prasbin.shadowmoney.presentation.screen.budgets.parseNprToMinor(amountText) ?: 0L
                    val deadlineMillis = parseDateToMillis(deadlineText) ?: 0L
                    if (title.isBlank()) titleError = true
                    if (amountMinor < 0L || (amountText.isNotBlank() && com.prasbin.shadowmoney.presentation.screen.budgets.parseNprToMinor(amountText) == null)) {
                        amountError = true
                    }
                    if (deadlineText.isNotBlank() && parseDateToMillis(deadlineText) == null) deadlineError = true
                    if (!title.isBlank() && !amountError && !deadlineError) {
                        onSave(title, description, amountMinor, deadlineMillis, client, initialItem)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkPrimary)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = DarkOnSurfaceVariant)
            }
        }
    )
}
