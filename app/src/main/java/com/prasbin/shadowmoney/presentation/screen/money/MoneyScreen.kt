@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.prasbin.shadowmoney.presentation.screen.money

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
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
import com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_BANK
import com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_CASH
import com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_DIGITAL_WALLET
import com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_WALLET
import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.presentation.navigation.Screen
import com.prasbin.shadowmoney.presentation.theme.BorderColor
import com.prasbin.shadowmoney.presentation.theme.DarkOnSurface
import com.prasbin.shadowmoney.presentation.theme.DarkOnSurfaceVariant
import com.prasbin.shadowmoney.presentation.theme.DarkPrimary
import com.prasbin.shadowmoney.presentation.theme.DarkSurface
import com.prasbin.shadowmoney.presentation.theme.DarkSurfaceVariant
import com.prasbin.shadowmoney.presentation.theme.ErrorRed
import com.prasbin.shadowmoney.presentation.theme.NeonCyan
import com.prasbin.shadowmoney.presentation.theme.WarningAmber

fun accountTypeLabel(type: Int): String = when (type) {
    ACCOUNT_TYPE_BANK -> "Bank"
    ACCOUNT_TYPE_CASH -> "Cash"
    ACCOUNT_TYPE_DIGITAL_WALLET -> "Digital wallet"
    else -> "Wallet"
}

@Composable
fun MoneyScreen(navController: NavHostController) {
    val context = LocalContext.current
    val viewModel: MoneyViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = ShadowMoneyDatabase.getInstance(context.applicationContext)
                return MoneyViewModel(
                    accountDao = database.accountDao(),
                    transactionDao = database.transactionDao()
                ) as T
            }
        }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var showForm by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<Account?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Money", color = NeonCyan) },
                actions = {
                    TextButton(onClick = { navController.navigate(Screen.Transactions.route) }) {
                        Text("Transactions", color = NeonCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        floatingActionButton = {
            if (!state.isLoading) {
                FloatingActionButton(
                    onClick = {
                        editingAccount = null
                        showForm = true
                    },
                    containerColor = NeonCyan
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add account", tint = DarkPrimary)
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
                Text("Loading accounts…", color = DarkOnSurfaceVariant)
            }

            state.accounts.isEmpty() -> Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "No accounts yet",
                    style = MaterialTheme.typography.headlineSmall,
                    color = DarkOnSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Add your wallet, bank, cash, or digital wallet account to start recording transactions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DarkOnSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(
                    onClick = {
                        editingAccount = null
                        showForm = true
                    }
                ) {
                    Text("Add account", color = NeonCyan)
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = { navController.navigate(Screen.Transactions.route) }) {
                    Text("View Transactions", color = NeonCyan)
                }
            }

            else -> LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.accounts, key = { it.account.id }) { view ->
                    AccountCard(
                        view = view,
                        onClick = {
                            editingAccount = view.account
                            showForm = true
                        }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }

        if (showForm) {
            AccountFormDialog(
                initial = editingAccount,
                errorMessage = errorMessage,
                onDismiss = {
                    showForm = false
                    viewModel.clearError()
                },
                onSave = { name, type, opening ->
                    val editing = editingAccount
                    val saved = if (editing == null) {
                        viewModel.createAccount(name, type, opening)
                    } else {
                        viewModel.updateAccount(editing.id, name = name, type = type, openingText = opening)
                    }
                    if (saved) {
                        showForm = false
                        viewModel.clearError()
                    }
                },
                onArchiveToggle = { archived ->
                    editingAccount?.let { viewModel.setArchived(it.id, archived) }
                    showForm = false
                    viewModel.clearError()
                }
            )
        }
    }
}

@Composable
private fun AccountCard(view: MoneyAccountView, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        view.account.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = DarkOnSurface
                    )
                    if (!view.account.isActive) {
                        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        Text(
                            "ARCHIVED",
                            style = MaterialTheme.typography.labelSmall,
                            color = WarningAmber
                        )
                    }
                }
                Text(
                    accountTypeLabel(view.account.type),
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkOnSurfaceVariant
                )
            }
            Text(
                Money.formatNpr(view.balanceMinor),
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Monospace,
                color = NeonCyan
            )
        }
    }
}

@Composable
private fun AccountFormDialog(
    initial: Account?,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, type: Int, opening: String) -> Unit,
    onArchiveToggle: (archived: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: ACCOUNT_TYPE_WALLET) }
    var openingText by remember {
        mutableStateOf(
            initial?.let { Money.formatNpr(it.openingBalanceMinor).removePrefix("NPR ") } ?: ""
        )
    }
    var typeExpanded by remember { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceVariant,
        title = {
            Text(
                if (initial == null) "Add Account" else "Edit Account",
                color = NeonCyan
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Account name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = moneyFieldColors()
                )
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = accountTypeLabel(type),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Account type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        colors = moneyFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        listOf(
                            ACCOUNT_TYPE_WALLET,
                            ACCOUNT_TYPE_BANK,
                            ACCOUNT_TYPE_CASH,
                            ACCOUNT_TYPE_DIGITAL_WALLET
                        ).forEach { option ->
                            DropdownMenuItem(
                                text = { Text(accountTypeLabel(option)) },
                                onClick = {
                                    type = option
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = openingText,
                    onValueChange = { openingText = it },
                    label = { Text("Opening balance (NPR)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = moneyFieldColors()
                )
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(errorMessage, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                }
                if (initial != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { onArchiveToggle(initial.isActive) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (initial.isActive) "Archive account" else "Restore account",
                            color = WarningAmber
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, type, openingText) }) {
                Text("Save", color = NeonCyan)
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
private fun moneyFieldColors(): androidx.compose.material3.TextFieldColors {
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
