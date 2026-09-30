@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.prasbin.shadowmoney.presentation.screen.transactions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.prasbin.shadowmoney.data.Money
import com.prasbin.shadowmoney.data.ShadowMoneyDatabase
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_INCOME
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.presentation.navigation.Screen
import com.prasbin.shadowmoney.presentation.theme.BorderColor
import com.prasbin.shadowmoney.presentation.theme.DarkOnSurface
import com.prasbin.shadowmoney.presentation.theme.DarkOnSurfaceVariant
import com.prasbin.shadowmoney.presentation.theme.DarkPrimary
import com.prasbin.shadowmoney.presentation.theme.DarkSurface
import com.prasbin.shadowmoney.presentation.theme.DarkSurfaceVariant
import com.prasbin.shadowmoney.presentation.theme.ErrorRed
import com.prasbin.shadowmoney.presentation.theme.NeonCyan
import com.prasbin.shadowmoney.presentation.theme.NeonGreen
import com.prasbin.shadowmoney.presentation.theme.NeonPurple
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val transactionDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

@Composable
fun TransactionsScreen(navController: NavHostController, initialDirection: String? = null) {
    val context = LocalContext.current
    val viewModel: TransactionsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = ShadowMoneyDatabase.getInstance(context.applicationContext)
                return TransactionsViewModel(
                    transactionDao = database.transactionDao(),
                    accountDao = database.accountDao(),
                    categoryDao = database.categoryDao(),
                    workItemDao = database.workItemDao()
                ) as T
            }
        }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var showAdd by remember { mutableStateOf(initialDirection != null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "ACTIVITY",
                            color = NeonCyan,
                            style = MaterialTheme.typography.titleLarge,
                            letterSpacing = 2f.sp
                        )
                        Text(
                            "MONEY IN & OUT · ALL RECORDS",
                            color = DarkOnSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { navController.navigate(Screen.Import.route) }) {
                        Text("IMPORT CSV", color = NeonCyan, style = MaterialTheme.typography.labelSmall)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        floatingActionButton = {
            if (!state.isLoading) {
                FloatingActionButton(
                    onClick = { showAdd = true },
                    containerColor = NeonCyan
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add transaction", tint = DarkPrimary)
                }
            }
        }
    ) { innerPadding ->
        when {
            state.isLoading -> Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = NeonCyan)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Loading transactions…", color = DarkOnSurfaceVariant)
            }

            state.recent.isEmpty() -> Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                com.prasbin.shadowmoney.presentation.theme.SystemEmptyState(
                    title = if (state.accounts.isEmpty()) "Set up money first" else "No transactions yet",
                    message = if (state.accounts.isEmpty()) {
                        "Create an account, then record money in or out. Your activity will appear here."
                    } else {
                        "Record money in or out, or import a CSV file. Your activity will appear here."
                    },
                    actionLabel = if (state.accounts.isEmpty()) "Add account" else "Record money",
                    onAction = {
                        if (state.accounts.isEmpty()) {
                            navController.navigate(Screen.Money.route)
                        } else {
                            showAdd = true
                        }
                    },
                    secondaryLabel = "Import CSV",
                    onSecondary = { navController.navigate(Screen.Import.route) }
                )
            }

            else -> LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.recent, key = { it.transaction.id }) { view ->
                    TransactionCard(view)
                }
            }
        }

        if (showAdd) {
            AddTransactionDialog(
                state = state,
                defaultDateText = viewModel.defaultDateText,
                errorMessage = errorMessage,
                initialDirection = initialDirection,
                onDismiss = {
                    showAdd = false
                    viewModel.clearError()
                },
                onSave = { direction, amount, date, accountId, categoryId, workItemId, note ->
                    val saved = viewModel.addTransaction(
                        direction = direction,
                        amountText = amount,
                        dateText = date,
                        accountId = accountId,
                        categoryId = categoryId,
                        workItemId = workItemId,
                        note = note
                    )
                    if (saved) {
                        showAdd = false
                        viewModel.clearError()
                    }
                }
            )
        }
    }
}

@Composable
private fun TransactionCard(view: TransactionListView) {
    val transaction = view.transaction
    val isIncome = transaction.direction == TRANSACTION_DIRECTION_INCOME
    val description = if (transaction.note.isBlank()) view.categoryName else transaction.note
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.prasbin.shadowmoney.presentation.theme.SystemChip(
                text = if (isIncome) "IN" else "OUT",
                color = if (isIncome) NeonGreen else NeonPurple
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = DarkOnSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    "${view.categoryName} · ${view.accountName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkOnSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    transactionDateFormat.format(Date(transaction.transactionTimestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkOnSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (if (isIncome) "+ " else "− ") + Money.formatNpr(transaction.amountMinor),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isIncome) NeonGreen else DarkOnSurface
                )
                Text(
                    if (isIncome) "MONEY IN" else "MONEY OUT",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isIncome) NeonGreen else NeonPurple
                )
            }
        }
    }
}

@Composable
private fun AddTransactionDialog(
    state: TransactionsUiState,
    defaultDateText: String,
    errorMessage: String?,
    initialDirection: String? = null,
    onDismiss: () -> Unit,
    onSave: (
        direction: Int,
        amount: String,
        date: String,
        accountId: Long,
        categoryId: Long?,
        workItemId: Long?,
        note: String
    ) -> Unit
) {
    var direction by remember {
        mutableStateOf(
            if (initialDirection == "in") TRANSACTION_DIRECTION_INCOME
            else TRANSACTION_DIRECTION_OUTFLOW
        )
    }
    var amountText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(defaultDateText) }
    var accountId by remember { mutableStateOf(state.accounts.firstOrNull()?.id ?: 0L) }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var workItemId by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }

    var accountExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var workExpanded by remember { mutableStateOf(false) }

    val effectiveAccountId = state.accounts.firstOrNull { it.id == accountId }?.id
        ?: state.accounts.firstOrNull()?.id
    if (effectiveAccountId != null && effectiveAccountId != accountId) {
        accountId = effectiveAccountId
    }
    val accountName = state.accounts.firstOrNull { it.id == accountId }?.name
        ?: "Select account"
    val categoryOptions = state.categories.filter {
        it.direction == com.prasbin.shadowmoney.data.model.CATEGORY_DIRECTION_BOTH ||
            it.direction == direction
    }
    if (categoryId != null && categoryOptions.none { it.id == categoryId }) {
        categoryId = null
    }
    val categoryName = categoryOptions.firstOrNull { it.id == categoryId }?.name
        ?: "None (uncategorized)"
    val workName = state.workItems.firstOrNull { it.id == workItemId }?.title
        ?: "None"
    val noAccounts = state.accounts.isEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceVariant,
        title = { Text("RECORD MONEY", color = NeonCyan, letterSpacing = 1.5f.sp) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (noAccounts) {
                    Text(
                        "Create an account on the Money screen before recording a transaction.",
                        color = ErrorRed,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount") },
                    prefix = { Text("NPR ", color = DarkOnSurfaceVariant, style = MaterialTheme.typography.labelLarge) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    colors = dialogFieldColors()
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    "TYPE",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = direction == TRANSACTION_DIRECTION_INCOME,
                        onClick = { direction = TRANSACTION_DIRECTION_INCOME },
                        label = { Text("Money In") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonGreen,
                            selectedLabelColor = DarkPrimary
                        )
                    )
                    FilterChip(
                        selected = direction == TRANSACTION_DIRECTION_OUTFLOW,
                        onClick = { direction = TRANSACTION_DIRECTION_OUTFLOW },
                        label = { Text("Money Out") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonPurple,
                            selectedLabelColor = DarkPrimary
                        )
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = categoryName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        colors = dialogFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None (uncategorized)") },
                            onClick = {
                                categoryId = null
                                categoryExpanded = false
                            }
                        )
                        categoryOptions.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    categoryId = category.id
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                ExposedDropdownMenuBox(
                    expanded = accountExpanded,
                    onExpandedChange = { if (!noAccounts) accountExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = accountName,
                        onValueChange = {},
                        readOnly = true,
                        enabled = !noAccounts,
                        label = { Text("Account") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        colors = dialogFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = accountExpanded,
                        onDismissRequest = { accountExpanded = false }
                    ) {
                        state.accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = {
                                    accountId = account.id
                                    accountExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("Date") },
                    placeholder = { Text("YYYY-MM-DD", color = DarkOnSurfaceVariant) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = dialogFieldColors()
                )
                Spacer(modifier = Modifier.height(12.dp))
                ExposedDropdownMenuBox(
                    expanded = workExpanded,
                    onExpandedChange = { workExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = workName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Linked work item (optional)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = workExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        colors = dialogFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = workExpanded,
                        onDismissRequest = { workExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None") },
                            onClick = {
                                workItemId = null
                                workExpanded = false
                            }
                        )
                        state.workItems.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.title) },
                                onClick = {
                                    workItemId = item.id
                                    workExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    colors = dialogFieldColors()
                )
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(errorMessage, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !noAccounts,
                onClick = {
                    onSave(
                        direction,
                        amountText,
                        dateText,
                        accountId,
                        categoryId,
                        workItemId,
                        note
                    )
                },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (direction == TRANSACTION_DIRECTION_INCOME) NeonGreen else NeonPurple,
                    contentColor = DarkPrimary
                )
            ) {
                Text(
                    "SAVE",
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    letterSpacing = 1f.sp
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = DarkOnSurfaceVariant)
            }
        }
    )
}

@Composable
private fun dialogFieldColors(): androidx.compose.material3.TextFieldColors {
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = DarkOnSurface,
        unfocusedTextColor = DarkOnSurface,
        focusedLabelColor = NeonCyan,
        unfocusedLabelColor = DarkOnSurfaceVariant,
        focusedBorderColor = NeonCyan,
        unfocusedBorderColor = BorderColor,
        errorBorderColor = ErrorRed
    )
}
