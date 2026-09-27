@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.prasbin.shadowmoney.presentation.screen.goals

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import com.prasbin.shadowmoney.data.SecretTargetStore
import com.prasbin.shadowmoney.data.ShadowMoneyDatabase
import com.prasbin.shadowmoney.data.model.Goal
import com.prasbin.shadowmoney.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val goalDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(navController: androidx.navigation.NavHostController? = null) {
    val context = LocalContext.current
    val database = remember { ShadowMoneyDatabase.getInstance(context.applicationContext) }
    val viewModel: GoalsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return GoalsViewModel(
                    com.prasbin.shadowmoney.data.GoalRepository(
                        goalDao = database.goalDao(),
                        accountDao = database.accountDao(),
                        transactionDao = database.transactionDao()
                    )
                ) as T
            }
        }
    )
    val secretViewModel: SecretTargetViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SecretTargetViewModel(SecretTargetStore.create(context)) as T
            }
        }
    )
    val state by viewModel.uiState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val secretTarget by secretViewModel.target.collectAsState()

    var showForm by remember { mutableStateOf(false) }
    var editingGoal by remember { mutableStateOf<Goal?>(null) }
    var archiveTarget by remember { mutableStateOf<Goal?>(null) }
    var deleteTarget by remember { mutableStateOf<Goal?>(null) }
    var showSecretForm by remember { mutableStateOf(false) }
    var secretClearTarget by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Goals") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        },
        floatingActionButton = {
            if (state is GoalsUiState.Content || state is GoalsUiState.Empty) {
                FloatingActionButton(
                    onClick = {
                        editingGoal = null
                        showForm = true
                    },
                    containerColor = NeonCyan,
                    contentColor = DarkPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add goal")
                }
            }
        }
    ) { innerPadding ->
        when (val currentState = state) {
            is GoalsUiState.Loading -> GoalsLoading(modifier = Modifier.padding(innerPadding))
            is GoalsUiState.Error -> GoalsError(
                message = currentState.message,
                modifier = Modifier.padding(innerPadding)
            )
            is GoalsUiState.Empty -> GoalsEmpty(
                onAdd = {
                    editingGoal = null
                    showForm = true
                },
                modifier = Modifier.padding(innerPadding)
            )
            is GoalsUiState.Content -> GoalsContent(
                state = currentState,
                onEdit = { goal ->
                    editingGoal = goal
                    showForm = true
                },
                onArchive = { goal -> archiveTarget = goal },
                onDelete = { goal -> deleteTarget = goal },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }

    SecretTargetPanel(
        target = secretTarget,
        onEdit = { showSecretForm = true },
        onClear = { secretClearTarget = true },
        modifier = Modifier.padding(horizontal = 16.dp)
    )

    if (showForm) {
        GoalFormDialog(
            initialGoal = editingGoal,
            accounts = database.accountDao(),
            onDismiss = { showForm = false },
            onSave = { name, targetAmountMinor, accountId, deadlineTimestamp, existing ->
                if (existing == null) {
                    viewModel.create(name, targetAmountMinor, accountId, deadlineTimestamp)
                } else {
                    viewModel.update(existing, name, targetAmountMinor, accountId, deadlineTimestamp)
                }
                showForm = false
            }
        )
    }

    if (showSecretForm) {
        SecretTargetDialog(
            initialAmountMinor = secretTarget,
            onDismiss = { showSecretForm = false },
            onSave = { amountMinor ->
                secretViewModel.setTarget(amountMinor)
                showSecretForm = false
            }
        )
    }

    archiveTarget?.let { goal ->
        AlertDialog(
            onDismissRequest = { archiveTarget = null },
            containerColor = DarkSurfaceVariant,
            title = { Text("Archive goal", color = NeonCyan) },
            text = { Text("Archive \"${goal.name}\"? It will be hidden from the active list.", color = DarkOnSurface) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.archive(goal.id)
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

    deleteTarget?.let { goal ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = DarkSurfaceVariant,
            title = { Text("Delete goal", color = ErrorRed) },
            text = {
                Text(
                    "Delete \"${goal.name}\"? This only removes the goal record — financial transactions are never deleted.",
                    color = DarkOnSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.delete(goal)
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

    secretClearTarget.let { show ->
        if (show) {
            AlertDialog(
                onDismissRequest = { secretClearTarget = false },
                containerColor = DarkSurfaceVariant,
                title = { Text("Clear Secret Target", color = WarningAmber) },
                text = { Text("Remove the private target? This cannot be undone.", color = DarkOnSurface) },
                confirmButton = {
                    Button(
                        onClick = {
                            secretViewModel.clear()
                            secretClearTarget = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = DarkOnSurface)
                    ) {
                        Text("Clear")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { secretClearTarget = false }) {
                        Text("Cancel", color = DarkOnSurfaceVariant)
                    }
                }
            )
        }
    }

    errorMessage?.let { message ->
        LaunchedEffect(message) {
            viewModel.clearErrorMessage()
        }
    }
}

@Composable
private fun GoalsLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = NeonCyan)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Loading goals…",
            style = MaterialTheme.typography.bodyMedium,
            color = DarkOnSurfaceVariant
        )
    }
}

@Composable
private fun GoalsEmpty(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No goals yet",
            style = MaterialTheme.typography.headlineMedium,
            color = NeonCyan
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Create a goal and link an account to track progress from actual records.",
            style = MaterialTheme.typography.bodyMedium,
            color = DarkOnSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onAdd,
            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkPrimary)
        ) {
            Text("Add goal")
        }
    }
}

@Composable
private fun GoalsError(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Goals unavailable",
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
private fun GoalsContent(
    state: GoalsUiState.Content,
    onEdit: (Goal) -> Unit,
    onArchive: (Goal) -> Unit,
    onDelete: (Goal) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(state.goals, key = { it.goal.id }) { view ->
            GoalCard(
                view = view,
                onEdit = { onEdit(view.goal) },
                onArchive = { onArchive(view.goal) },
                onDelete = { onDelete(view.goal) }
            )
        }
    }
}

@Composable
private fun GoalCard(
    view: com.prasbin.shadowmoney.data.GoalView,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    val goal = view.goal
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = CardColor,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = goal.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = DarkOnSurface,
                    modifier = Modifier.weight(1f)
                )
                if (goal.isCompleted) {
                    Text(
                        text = "COMPLETED",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = NeonGreen,
                        fontWeight = FontWeight.Bold
                    )
                } else if (!goal.isActive) {
                    Text(
                        text = "ARCHIVED",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = DarkOnSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit goal",
                        tint = DarkOnSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onArchive, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Archive,
                        contentDescription = "Archive goal",
                        tint = WarningAmber,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete goal",
                        tint = ErrorRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (view.accountName != null) "Progress from ${view.accountName}" else "No account linked — progress unavailable",
                style = MaterialTheme.typography.labelSmall,
                color = if (view.accountName != null) DarkOnSurfaceVariant else WarningAmber
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${Money.formatNpr(view.currentBalanceMinor)} of ${Money.formatNpr(goal.targetAmountMinor)}",
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Monospace,
                color = NeonCyan
            )
            Text(
                text = "Remaining ${Money.formatNpr(view.remainingMinor)} · ${view.percentUsed}% used",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = DarkOnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { (view.percentUsed / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = if (view.percentUsed >= 100) NeonGreen else NeonCyan,
                trackColor = BorderColor
            )
            if (goal.deadlineTimestamp > 0L) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Deadline ${goalDateFormat.format(Date(goal.deadlineTimestamp))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkOnSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SecretTargetPanel(
    target: Long?,
    onEdit: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    var revealed by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = DarkSurfaceElevated,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Secret Target",
                    style = MaterialTheme.typography.titleLarge,
                    color = AccentGold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { revealed = !revealed }) {
                    Icon(
                        if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (revealed) "Hide secret target" else "Reveal secret target",
                        tint = AccentGold
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit secret target", tint = DarkOnSurfaceVariant)
                }
                if (target != null) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear secret target", tint = ErrorRed)
                    }
                }
            }
            Text(
                text = "Private personal target. Never shown on the Dashboard, never included in backups, exports, logs, or analytics.",
                style = MaterialTheme.typography.labelSmall,
                color = DarkOnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (target == null) {
                    "No secret target set"
                } else if (revealed) {
                    Money.formatNpr(target)
                } else {
                    "••••••••"
                },
                style = MaterialTheme.typography.displayMedium,
                fontFamily = FontFamily.Monospace,
                color = if (target == null) DarkOnSurfaceVariant else AccentGold
            )
            if (target != null && !revealed) {
                Text(
                    text = "Tap the eye icon to reveal",
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkOnSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalFormDialog(
    initialGoal: Goal?,
    accounts: com.prasbin.shadowmoney.data.AccountDao,
    onDismiss: () -> Unit,
    onSave: (name: String, targetAmountMinor: Long, accountId: Long?, deadlineTimestamp: Long, existing: Goal?) -> Unit
) {
    val allAccounts by accounts.getAll().collectAsState(initial = emptyList())

    var name by remember { mutableStateOf(initialGoal?.name ?: "") }
    var amountText by remember {
        mutableStateOf(
            initialGoal?.let { Money.formatNpr(it.targetAmountMinor).removePrefix("NPR ") } ?: ""
        )
    }
    var selectedAccountId by remember { mutableStateOf(initialGoal?.accountId) }
    var deadlineText by remember {
        mutableStateOf(
            initialGoal?.deadlineTimestamp?.let { goalDateFormat.format(Date(it)) } ?: ""
        )
    }
    var nameError by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }
    var deadlineError by remember { mutableStateOf(false) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val selectedAccountName = allAccounts.firstOrNull { it.id == selectedAccountId }?.name ?: "No account linked"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceVariant,
        title = {
            Text(
                text = if (initialGoal == null) "Add Goal" else "Edit Goal",
                color = NeonCyan
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = false
                    },
                    label = { Text("Name") },
                    isError = nameError,
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
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        amountError = false
                    },
                    label = { Text("Target amount (NPR)") },
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
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedAccountName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Linked account (optional)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = DarkOnSurface,
                            unfocusedTextColor = DarkOnSurface,
                            focusedLabelColor = NeonCyan,
                            unfocusedLabelColor = DarkOnSurfaceVariant,
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = BorderColor
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("No account linked") },
                            onClick = {
                                selectedAccountId = null
                                dropdownExpanded = false
                            }
                        )
                        allAccounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = {
                                    selectedAccountId = account.id
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
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
                if (nameError) {
                    Text("Name is required", style = MaterialTheme.typography.labelSmall, color = ErrorRed)
                }
                if (amountError) {
                    Text("Enter a valid positive target amount", style = MaterialTheme.typography.labelSmall, color = ErrorRed)
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
                    val deadlineMillis = parseGoalDate(deadlineText) ?: 0L
                    if (name.isBlank()) nameError = true
                    if (amountText.isBlank() || amountMinor <= 0L) amountError = true
                    if (deadlineText.isNotBlank() && parseGoalDate(deadlineText) == null) deadlineError = true
                    if (name.isNotBlank() && !amountError && !deadlineError) {
                        onSave(name, amountMinor, selectedAccountId, deadlineMillis, initialGoal)
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

private fun parseGoalDate(input: String): Long? {
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
private fun SecretTargetDialog(
    initialAmountMinor: Long?,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit
) {
    var amountText by remember {
        mutableStateOf(
            initialAmountMinor?.let { Money.formatNpr(it).removePrefix("NPR ") } ?: ""
        )
    }
    var amountError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceVariant,
        title = { Text("Secret Target", color = AccentGold) },
        text = {
            Column {
                Text(
                    text = "Stored privately in app storage. Never shown on the Dashboard, never included in backups or exports.",
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkOnSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        amountError = false
                    },
                    label = { Text("Target amount (NPR)") },
                    isError = amountError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DarkOnSurface,
                        unfocusedTextColor = DarkOnSurface,
                        focusedLabelColor = AccentGold,
                        unfocusedLabelColor = DarkOnSurfaceVariant,
                        focusedBorderColor = AccentGold,
                        unfocusedBorderColor = BorderColor,
                        errorBorderColor = ErrorRed
                    )
                )
                if (amountError) {
                    Text("Enter a valid non-negative amount", style = MaterialTheme.typography.labelSmall, color = ErrorRed)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amountMinor = com.prasbin.shadowmoney.presentation.screen.budgets.parseNprToMinor(amountText) ?: 0L
                    if (amountText.isNotBlank() && amountMinor >= 0L) {
                        onSave(amountMinor)
                    } else {
                        amountError = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentGold, contentColor = DarkPrimary)
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
