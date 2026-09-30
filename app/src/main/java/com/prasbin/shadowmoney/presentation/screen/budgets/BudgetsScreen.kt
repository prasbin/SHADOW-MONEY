@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.prasbin.shadowmoney.presentation.screen.budgets

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import com.prasbin.shadowmoney.data.BudgetCalendar
import com.prasbin.shadowmoney.data.Money
import com.prasbin.shadowmoney.data.ShadowMoneyDatabase
import com.prasbin.shadowmoney.data.model.Budget
import com.prasbin.shadowmoney.data.model.Category
import com.prasbin.shadowmoney.presentation.theme.*
import kotlinx.coroutines.flow.first

internal fun parseNprToMinor(input: String): Long? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    val parts = trimmed.split(".")
    if (parts.size > 2) return null
    val whole = parts[0].toLongOrNull() ?: return null
    val fraction = if (parts.size == 2) {
        val frac = parts[1]
        if (frac.length > 2 || frac.isEmpty()) return null
        frac.padEnd(2, '0').toIntOrNull() ?: return null
    } else {
        0
    }
    return whole * 100L + fraction
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen() {
    val context = LocalContext.current
    val viewModel: BudgetsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = ShadowMoneyDatabase.getInstance(context.applicationContext)
                return BudgetsViewModel(
                    com.prasbin.shadowmoney.data.BudgetRepository(
                        budgetDao = database.budgetDao(),
                        transactionDao = database.transactionDao(),
                        categoryDao = database.categoryDao(),
                        openHelper = database.openHelper
                    )
                ) as T
            }
        }
    )
    val state by viewModel.uiState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var showDialog by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf<Budget?>(null) }

    errorMessage?.let { message ->
        LaunchedEffect(message) {
            viewModel.clearErrorMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Budgets") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        },
        floatingActionButton = {
            if (state is BudgetsUiState.Content) {
                FloatingActionButton(
                    onClick = {
                        editingBudget = null
                        showDialog = true
                    },
                    containerColor = NeonCyan,
                    contentColor = DarkPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add budget")
                }
            }
        }
    ) { innerPadding ->
        when (val currentState = state) {
            is BudgetsUiState.Loading -> BudgetsLoading(modifier = Modifier.padding(innerPadding))
            is BudgetsUiState.Empty -> BudgetsEmpty(
                onAdd = {
                    editingBudget = null
                    showDialog = true
                },
                modifier = Modifier.padding(innerPadding)
            )
            is BudgetsUiState.Error -> BudgetsError(
                message = currentState.message,
                modifier = Modifier.padding(innerPadding)
            )
            is BudgetsUiState.Content -> BudgetsContent(
                state = currentState,
                viewModel = viewModel,
                onEdit = { budget ->
                    editingBudget = budget
                    showDialog = true
                },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }

    if (showDialog) {
        BudgetDialog(
            initialBudget = editingBudget,
            initialMonthKey = (state as? BudgetsUiState.Content)?.month?.monthKey
                ?: com.prasbin.shadowmoney.data.BudgetCalendar.currentMonthKey(),
            onDismiss = { showDialog = false },
            onSave = { monthKey, categoryId, amountMinor, existingBudget ->
                if (existingBudget == null) {
                    if (categoryId == null) {
                        viewModel.createOverallBudget(monthKey, amountMinor)
                    } else {
                        viewModel.createCategoryBudget(monthKey, categoryId, amountMinor)
                    }
                } else {
                    viewModel.updateBudgetAmount(existingBudget, amountMinor)
                }
                showDialog = false
            }
        )
    }

    errorMessage?.let { message ->
        LaunchedEffect(message) {
            kotlinx.coroutines.delay(4_000)
            viewModel.clearErrorMessage()
        }
        Snackbar(
            modifier = Modifier.padding(16.dp),
            containerColor = DarkSurfaceVariant,
            contentColor = ErrorRed
        ) {
            Text(text = message)
        }
    }
}

@Composable
private fun BudgetsLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = NeonCyan)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Loading budgetsâ€¦",
            style = MaterialTheme.typography.bodyMedium,
            color = DarkOnSurfaceVariant
        )
    }
}

@Composable
private fun BudgetsEmpty(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No budgets for this month",
            style = MaterialTheme.typography.headlineMedium,
            color = NeonCyan
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Create an overall or category budget to plan monthly spending.",
            style = MaterialTheme.typography.bodyMedium,
            color = DarkOnSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onAdd,
            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkPrimary)
        ) {
            Text("Add budget")
        }
    }
}

@Composable
private fun BudgetsError(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Budgets unavailable",
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
private fun BudgetsContent(
    state: BudgetsUiState.Content,
    viewModel: BudgetsViewModel,
    onEdit: (Budget) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            MonthSelector(
                monthLabel = com.prasbin.shadowmoney.data.BudgetCalendar.monthLabel(state.month.monthKey),
                onPrevious = { viewModel.selectPreviousMonth() },
                onNext = { viewModel.selectNextMonth() }
            )
        }
        state.month.overall?.let { overall ->
            item {
                BudgetPanel(title = "Overall Budget", subtitle = "Total planned outflow for the month") {
                    BudgetRow(
                        view = overall,
                        onEdit = { onEdit(overall.budget) },
                        onDelete = { viewModel.deleteBudget(overall.budget) }
                    )
                }
            }
        }
        if (state.month.categoryBudgets.isNotEmpty()) {
            item {
                BudgetPanel(title = "Category Budgets", subtitle = "Sub-limits per category â€” not additional money") {
                    state.month.categoryBudgets.forEach { view ->
                        BudgetRow(
                            view = view,
                            onEdit = { onEdit(view.budget) },
                            onDelete = { viewModel.deleteBudget(view.budget) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthSelector(
    monthLabel: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month", tint = NeonCyan)
        }
        Text(
            text = monthLabel,
            style = MaterialTheme.typography.titleLarge,
            color = NeonCyan
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Next month", tint = NeonCyan)
        }
    }
}

@Composable
private fun BudgetPanel(
    title: String,
    subtitle: String,
    children: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().border(
            width = 1.dp,
            color = BorderColor,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = DarkSurfaceVariant,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = NeonCyan
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = DarkOnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            children()
        }
    }
}

@Composable
private fun BudgetRow(
    view: com.prasbin.shadowmoney.data.BudgetView,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val title = view.categoryName ?: "Overall"
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = DarkOnSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = budgetStatusLabel(view.status),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
            color = budgetStatusColor(view.status),
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit budget",
                    tint = DarkOnSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete budget",
                    tint = ErrorRed,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(
            text = "Spent ${Money.formatNpr(view.spentMinor)} of ${Money.formatNpr(view.budget.amountMinor)}",
            style = MaterialTheme.typography.bodySmall,
            color = DarkOnSurfaceVariant
        )
        Text(
            text = "Remaining ${Money.formatNpr(view.remainingMinor)} Â· ${view.percentUsed}% used",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = DarkOnSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (view.percentUsed / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
                color = budgetStatusColor(view.status),
            trackColor = BorderColor
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetDialog(
    initialBudget: Budget?,
    initialMonthKey: String,
    onDismiss: () -> Unit,
    onSave: (monthKey: String, categoryId: Long?, amountMinor: Long, existingBudget: Budget?) -> Unit
) {
    val context = LocalContext.current
    val database = remember { ShadowMoneyDatabase.getInstance(context.applicationContext) }
    val allCategories by database.categoryDao().getAll().collectAsState(initial = emptyList())

    var isCategory by remember { mutableStateOf(initialBudget?.categoryId != null) }
    var selectedCategoryId by remember { mutableStateOf(initialBudget?.categoryId) }
    var monthKey by remember { mutableStateOf(initialBudget?.monthKey ?: initialMonthKey) }
    var amountText by remember {
        mutableStateOf(
            initialBudget?.let { Money.formatNpr(it.amountMinor).removePrefix("NPR ") } ?: ""
        )
    }
    var categoryError by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }
    var monthError by remember { mutableStateOf(false) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val selectedCategoryName = allCategories.firstOrNull { it.id == selectedCategoryId }?.name
        ?: if (isCategory) "Select category" else ""

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceVariant,
        title = {
            Text(
                text = if (initialBudget == null) "Add Budget" else "Edit Budget",
                color = NeonCyan
            )
        },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = !isCategory,
                        onClick = {
                            isCategory = false
                            selectedCategoryId = null
                        },
                        label = { Text("Overall") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonCyan,
                            selectedLabelColor = DarkPrimary
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = isCategory,
                        onClick = { isCategory = true },
                        label = { Text("Category") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonCyan,
                            selectedLabelColor = DarkPrimary
                        )
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        monthKey = com.prasbin.shadowmoney.data.BudgetCalendar.shiftMonth(monthKey, -1)
                    }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month", tint = NeonCyan)
                    }
                    Text(
                        text = com.prasbin.shadowmoney.data.BudgetCalendar.monthLabel(monthKey),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (monthError) ErrorRed else DarkOnSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        monthKey = com.prasbin.shadowmoney.data.BudgetCalendar.shiftMonth(monthKey, 1)
                    }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next month", tint = NeonCyan)
                    }
                }
                if (isCategory) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedCategoryName,
                            onValueChange = {},
                            readOnly = true,
                            enabled = true,
                            label = { Text("Category") },
                            isError = categoryError,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
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
                        ExposedDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            allCategories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    onClick = {
                                        selectedCategoryId = category.id
                                        categoryError = false
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        amountError = false
                    },
                    label = { Text("Amount (NPR)") },
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
                        unfocusedBorderColor = BorderColor
                    )
                )
                if (categoryError) {
                    Text(
                        text = "Select a category",
                        style = MaterialTheme.typography.labelSmall,
                        color = ErrorRed
                    )
                }
                if (amountError) {
                    Text(
                        text = "Enter a valid positive amount",
                        style = MaterialTheme.typography.labelSmall,
                        color = ErrorRed
                    )
                }
                if (monthError) {
                    Text(
                        text = "Invalid month",
                        style = MaterialTheme.typography.labelSmall,
                        color = ErrorRed
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amountMinor = parseNprToMinor(amountText)
                    val monthValid = monthKey.matches(Regex("^\\d{4}-\\d{2}$"))
                    if (!monthValid) monthError = true
                    if (amountMinor == null || amountMinor <= 0L) amountError = true
                    if (isCategory && selectedCategoryId == null) categoryError = true
                    if (monthValid && amountMinor != null && amountMinor > 0L &&
                        (!isCategory || selectedCategoryId != null)
                    ) {
                        onSave(monthKey, if (isCategory) selectedCategoryId else null, amountMinor, initialBudget)
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
