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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
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
fun TransactionsScreen(navController: NavHostController) {
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

    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transactions", color = NeonCyan) },
                actions = {
                    TextButton(onClick = { navController.navigate(Screen.Import.route) }) {
                        Text("Import CSV", color = NeonCyan)
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
                Text(
                    "No transactions yet",
                    style = MaterialTheme.typography.headlineSmall,
                    color = DarkOnSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (state.accounts.isEmpty()) {
                        "Create an account on the Money screen first, then record income or outflow here."
                    } else {
                        "Record income or outflow manually, or import a CSV file."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = DarkOnSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(
                    onClick = { showAdd = true },
                    border = BorderStroke(1.dp, NeonCyan)
                ) {
                    Text("Add transaction", color = NeonCyan)
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { navController.navigate(Screen.Import.route) },
                    border = BorderStroke(1.dp, NeonCyan)
                ) {
                    Text("Import Transactions (CSV)", color = NeonCyan)
                }
                if (state.accounts.isEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { navController.navigate(Screen.Money.route) },
                        border = BorderStroke(1.dp, NeonCyan)
                    ) {
                        Text("Go to Money (accounts)", color = NeonCyan)
                    }
                }
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
    val description = if (transaction.note.isBlank()) "Transaction" else transaction.note
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = DarkOnSurface
                )
                Text(
                    "${view.accountName} · ${view.categoryName} · " +
                        transactionDateFormat.format(Date(transaction.transactionTimestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkOnSurfaceVariant
                )
            }
            Text(
                (if (isIncome) "+ " else "- ") + Money.formatNpr(transaction.amountMinor),
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Monospace,
                color = if (isIncome) NeonGreen else NeonPurple
            )
        }
    }
}

@Composable
private fun AddTransactionDialog(
    state: TransactionsUiState,
    defaultDateText: String,
    errorMessage: String?,
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
    var direction by remember { mutableStateOf(TRANSACTION_DIRECTION_OUTFLOW) }
    var amountText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(defaultDateText) }
    var accountId by remember { mutableStateOf(state.accounts.firstOrNull()?.id ?: 0L) }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var workItemId by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }

    var accountExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var workExpanded by remember { mutableStateOf(false) }

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
        title = { Text("Add Transaction", color = NeonCyan) },
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = direction == TRANSACTION_DIRECTION_OUTFLOW,
                        onClick = { direction = TRANSACTION_DIRECTION_OUTFLOW },
                        label = { Text("Outflow") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonPurple,
                            selectedLabelColor = DarkPrimary
                        )
                    )
                    FilterChip(
                        selected = direction == TRANSACTION_DIRECTION_INCOME,
                        onClick = { direction = TRANSACTION_DIRECTION_INCOME },
                        label = { Text("Income") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonGreen,
                            selectedLabelColor = DarkPrimary
                        )
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (NPR)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = dialogFieldColors()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("Date (yyyy-MM-dd)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = dialogFieldColors()
                )
                Spacer(modifier = Modifier.height(8.dp))
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
                Spacer(modifier = Modifier.height(8.dp))
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
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = workExpanded,
                    onExpandedChange = { workExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = workName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Work item (optional)") },
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
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
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
            TextButton(
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
                }
            ) {
                Text("Save", color = if (noAccounts) DarkOnSurfaceVariant else NeonCyan)
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
