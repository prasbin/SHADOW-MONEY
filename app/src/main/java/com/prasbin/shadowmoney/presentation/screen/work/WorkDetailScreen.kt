@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.prasbin.shadowmoney.presentation.screen.work

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.prasbin.shadowmoney.data.Money
import com.prasbin.shadowmoney.data.ShadowMoneyDatabase
import com.prasbin.shadowmoney.data.WorkMath
import com.prasbin.shadowmoney.data.WorkRepository
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_INCOME
import com.prasbin.shadowmoney.data.model.Transaction
import com.prasbin.shadowmoney.data.model.WORK_STATUS_ACTIVE
import com.prasbin.shadowmoney.data.model.WORK_STATUS_ARCHIVED
import com.prasbin.shadowmoney.data.model.WORK_STATUS_COMPLETED
import com.prasbin.shadowmoney.data.model.WORK_STATUS_PAUSED
import com.prasbin.shadowmoney.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val detailDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkDetailScreen(workItemId: Long, navController: NavHostController) {
    val context = LocalContext.current
    val viewModel: WorkDetailViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = ShadowMoneyDatabase.getInstance(context.applicationContext)
                return WorkDetailViewModel(
                    workItemId,
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Work Item") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NeonCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        }
    ) { innerPadding ->
        when (val currentState = state) {
            is WorkDetailUiState.Loading -> DetailLoading(modifier = Modifier.padding(innerPadding))
            is WorkDetailUiState.NotFound -> DetailNotFound(modifier = Modifier.padding(innerPadding))
            is WorkDetailUiState.Error -> DetailError(
                message = currentState.message,
                modifier = Modifier.padding(innerPadding)
            )
            is WorkDetailUiState.Content -> WorkDetailContent(
                state = currentState,
                onLink = { transactionId -> viewModel.linkTransaction(transactionId) },
                onUnlink = { transactionId -> viewModel.unlinkTransaction(transactionId) },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
private fun DetailLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = NeonCyan)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Loading work itemâ€¦",
            style = MaterialTheme.typography.bodyMedium,
            color = DarkOnSurfaceVariant
        )
    }
}

@Composable
private fun DetailNotFound(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Work item not found",
            style = MaterialTheme.typography.headlineMedium,
            color = ErrorRed
        )
    }
}

@Composable
private fun DetailError(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Work item unavailable",
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
private fun WorkDetailContent(
    state: WorkDetailUiState.Content,
    onLink: (Long) -> Unit,
    onUnlink: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val detail = state.detail
    val item = detail.workItem
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            SystemPanel(title = item.title) {
                StatusRow(item)
                Spacer(modifier = Modifier.height(8.dp))
                if (item.description.isNotBlank()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = DarkOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (item.client.isNotBlank()) {
                    Text(
                        text = "Client / source: ${item.client}",
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                AmountRow(
                    label = "Expected amount",
                    value = Money.formatNpr(item.expectedAmountMinor),
                    color = DarkOnSurface
                )
                AmountRow(
                    label = "Received (actual linked income)",
                    value = Money.formatNpr(detail.receivedMinor),
                    color = NeonGreen
                )
                AmountRow(
                    label = "Remaining expected",
                    value = Money.formatNpr(detail.remainingExpectedMinor),
                    color = if (detail.remainingExpectedMinor < 0) WarningAmber else DarkOnSurfaceVariant
                )
                if (item.deadlineTimestamp > 0L) {
                    Text(
                        text = "Deadline: ${detailDateFormat.format(Date(item.deadlineTimestamp))} (${WorkMath.deadlineLabel(detail.deadlineStatus)})",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (detail.deadlineStatus == com.prasbin.shadowmoney.data.DeadlineStatus.OVERDUE) ErrorRed else DarkOnSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Deadline: none",
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkOnSurfaceVariant
                    )
                }
            }
        }
        item {
            SystemPanel(title = "Linked Transactions") {
                if (detail.transactions.isEmpty()) {
                    Text(
                        text = "No linked transactions yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DarkOnSurfaceVariant
                    )
                }
                detail.transactions.forEach { transaction ->
                    LinkedTransactionRow(transaction, onUnlink = { onUnlink(transaction.id) })
                }
            }
        }
        item {
            SystemPanel(title = "Link a Transaction") {
                val linkable = detail.linkableTransactions.filter { it.id !in detail.transactions.map { linked -> linked.id } }
                if (linkable.isEmpty()) {
                    Text(
                        text = "No unlinked transactions available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DarkOnSurfaceVariant
                    )
                }
                linkable.take(20).forEach { transaction ->
                    LinkableTransactionRow(transaction, onLink = { onLink(transaction.id) })
                }
            }
        }
    }
}

@Composable
private fun StatusRow(item: com.prasbin.shadowmoney.data.model.WorkItem) {
    val statusColor = when (item.status) {
        WORK_STATUS_ACTIVE -> NeonGreen
        WORK_STATUS_PAUSED -> WarningAmber
        WORK_STATUS_COMPLETED -> NeonCyan
        WORK_STATUS_ARCHIVED -> DarkOnSurfaceVariant
        else -> DarkOnSurfaceVariant
    }
    Text(
        text = WorkMath.statusLabel(item.status),
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        color = statusColor,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun AmountRow(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = DarkOnSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = color
        )
    }
}

@Composable
private fun LinkedTransactionRow(transaction: Transaction, onUnlink: () -> Unit) {
    val isIncome = transaction.direction == TRANSACTION_DIRECTION_INCOME
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.note.ifBlank { "Transaction" },
                style = MaterialTheme.typography.bodyMedium,
                color = DarkOnSurface
            )
            Text(
                text = "${detailDateFormat.format(Date(transaction.transactionTimestamp))} Â· ${if (isIncome) "INCOME" else "OUTFLOW"}",
                style = MaterialTheme.typography.labelSmall,
                color = DarkOnSurfaceVariant
            )
        }
        Text(
            text = Money.formatNpr(transaction.amountMinor),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = if (isIncome) NeonGreen else NeonPurple
        )
        TextButton(onClick = onUnlink) {
            Text("Unlink", color = WarningAmber)
        }
    }
}

@Composable
private fun LinkableTransactionRow(transaction: Transaction, onLink: () -> Unit) {
    val isIncome = transaction.direction == TRANSACTION_DIRECTION_INCOME
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.note.ifBlank { "Transaction" },
                style = MaterialTheme.typography.bodyMedium,
                color = DarkOnSurface
            )
            Text(
                text = "${detailDateFormat.format(Date(transaction.transactionTimestamp))} Â· ${if (isIncome) "INCOME" else "OUTFLOW"}",
                style = MaterialTheme.typography.labelSmall,
                color = DarkOnSurfaceVariant
            )
        }
        Text(
            text = Money.formatNpr(transaction.amountMinor),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = if (isIncome) NeonGreen else NeonPurple
        )
        TextButton(onClick = onLink) {
            Text("Link", color = NeonCyan)
        }
    }
}
