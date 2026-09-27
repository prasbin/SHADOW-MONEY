@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.prasbin.shadowmoney.presentation.screen.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prasbin.shadowmoney.data.Money
import com.prasbin.shadowmoney.data.ShadowMoneyDatabase
import com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_BANK
import com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_CASH
import com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_DIGITAL_WALLET
import com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_WALLET
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_INCOME
import com.prasbin.shadowmoney.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TRUST_LABEL = "Local records only · not a bank balance."

private val dashboardDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
private val windowDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

private fun insightKindLabel(kind: com.prasbin.shadowmoney.intelligence.InsightKind): String = when (kind) {
    com.prasbin.shadowmoney.intelligence.InsightKind.FACT -> "FACT"
    com.prasbin.shadowmoney.intelligence.InsightKind.CALCULATION -> "CALCULATION"
    com.prasbin.shadowmoney.intelligence.InsightKind.ANALYSIS -> "ANALYSIS"
    com.prasbin.shadowmoney.intelligence.InsightKind.PROJECTION -> "PROJECTION"
}

private fun insightKindColor(kind: com.prasbin.shadowmoney.intelligence.InsightKind): Color = when (kind) {
    com.prasbin.shadowmoney.intelligence.InsightKind.FACT -> NeonCyan
    com.prasbin.shadowmoney.intelligence.InsightKind.CALCULATION -> NeonGreen
    com.prasbin.shadowmoney.intelligence.InsightKind.ANALYSIS -> NeonPurple
    com.prasbin.shadowmoney.intelligence.InsightKind.PROJECTION -> AccentGold
}

private fun accountTypeLabel(type: Int): String = when (type) {
    ACCOUNT_TYPE_WALLET -> "Wallet"
    ACCOUNT_TYPE_BANK -> "Bank"
    ACCOUNT_TYPE_CASH -> "Cash"
    ACCOUNT_TYPE_DIGITAL_WALLET -> "Digital Wallet"
    else -> "Account"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: androidx.navigation.NavHostController? = null) {
    val context = LocalContext.current
    val viewModel: DashboardViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = ShadowMoneyDatabase.getInstance(context.applicationContext)
                return DashboardViewModel(
                    com.prasbin.shadowmoney.data.DashboardRepository(
                        accountDao = database.accountDao(),
                        categoryDao = database.categoryDao(),
                        transactionDao = database.transactionDao(),
                        goalDao = database.goalDao(),
                        openHelper = database.openHelper
                    )
                ) as T
            }
        }
    )
    val state by viewModel.uiState.collectAsState()
    val intelligenceViewModel: IntelligenceViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = ShadowMoneyDatabase.getInstance(context.applicationContext)
                return IntelligenceViewModel(
                    com.prasbin.shadowmoney.data.IntelligenceRepository(
                        transactionDao = database.transactionDao(),
                        categoryDao = database.categoryDao()
                    )
                ) as T
            }
        }
    )
    val intelligenceState by intelligenceViewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "SHADOW MONEY",
                            style = MaterialTheme.typography.titleLarge,
                            color = NeonCyan
                        )
                        Text(
                            text = TRUST_LABEL,
                            style = MaterialTheme.typography.labelSmall,
                            color = DarkOnSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Dashboard") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController?.navigate(com.prasbin.shadowmoney.presentation.navigation.Screen.Goals.route) },
                    icon = { Text("G") },
                    label = { Text("Goals") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController?.navigate(com.prasbin.shadowmoney.presentation.navigation.Screen.Budgets.route) },
                    icon = { Text("B") },
                    label = { Text("Budgets") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController?.navigate(com.prasbin.shadowmoney.presentation.navigation.Screen.Work.route) },
                    icon = { Text("W") },
                    label = { Text("Work") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController?.navigate(com.prasbin.shadowmoney.presentation.navigation.Screen.Telecom.route) },
                    icon = { Text("T") },
                    label = { Text("Telecom") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { },
                    icon = { Text("$") },
                    label = { Text("Money") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { },
                    icon = { Text("S") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { innerPadding ->
        when (val currentState = state) {
            is DashboardUiState.Loading -> DashboardLoading(modifier = Modifier.padding(innerPadding))
            is DashboardUiState.Empty -> DashboardEmpty(modifier = Modifier.padding(innerPadding))
            is DashboardUiState.Error -> DashboardError(
                message = currentState.message,
                modifier = Modifier.padding(innerPadding)
            )
            is DashboardUiState.Content -> DashboardContent(
                state = currentState,
                intelligenceState = intelligenceState,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
private fun DashboardLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = NeonCyan)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Loading local records…",
            style = MaterialTheme.typography.bodyMedium,
            color = DarkOnSurfaceVariant
        )
    }
}

@Composable
private fun DashboardEmpty(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No financial records yet",
            style = MaterialTheme.typography.headlineMedium,
            color = NeonCyan
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Add an account and record transactions to see your dashboard.",
            style = MaterialTheme.typography.bodyMedium,
            color = DarkOnSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = TRUST_LABEL,
            style = MaterialTheme.typography.labelSmall,
            color = DarkOnSurfaceVariant
        )
    }
}

@Composable
private fun DashboardError(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Dashboard unavailable",
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
private fun DashboardContent(
    state: DashboardUiState.Content,
    intelligenceState: IntelligenceUiState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            SystemPanel(title = "Financial Summary") {
                SystemCard(
                    title = "Total Balance",
                    value = Money.formatNpr(state.totalBalanceMinor),
                    accentColor = if (state.totalBalanceMinor >= 0) NeonGreen else ErrorRed,
                    subtitle = "Active accounts · derived from local records"
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SystemCard(
                        title = "Income",
                        value = Money.formatNpr(state.totalIncomeMinor),
                        accentColor = NeonGreen,
                        modifier = Modifier.weight(1f)
                    )
                    SystemCard(
                        title = "Outflow",
                        value = Money.formatNpr(state.totalOutflowMinor),
                        accentColor = NeonPurple,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        item {
            SystemPanel(title = "Accounts") {
                if (state.accounts.isEmpty()) {
                    SectionEmptyRow("No accounts recorded yet")
                }
                state.accounts.forEach { view ->
                    AccountRow(view)
                }
            }
        }
        item {
            SystemPanel(title = "Outflow by Category") {
                if (state.categoryOutflow.isEmpty()) {
                    SectionEmptyRow("No outflow recorded")
                }
                state.categoryOutflow.forEach { view ->
                    CategorySpendRow(view)
                }
            }
        }
        item {
            SystemPanel(title = "Recent Transactions") {
                if (state.recentTransactions.isEmpty()) {
                    SectionEmptyRow("No transactions yet")
                }
                state.recentTransactions.forEach { view ->
                    TransactionRow(view)
                }
            }
        }
        item {
            SystemPanel(title = "Goals") {
                if (state.goals.isEmpty()) {
                    SectionEmptyRow("No goals yet")
                }
                state.goals.forEach { view ->
                    GoalRow(view)
                }
            }
        }
        item {
            IntelligenceSection(state = intelligenceState)
        }
    }
}

@Composable
private fun SectionEmptyRow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = DarkOnSurfaceVariant
    )
}

@Composable
private fun AccountRow(view: com.prasbin.shadowmoney.data.AccountBalanceView) {
    val account = view.account
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (account.isActive) DarkOnSurface else DarkOnSurfaceVariant
            )
            Text(
                text = if (account.isActive) accountTypeLabel(account.type) else "Archived",
                style = MaterialTheme.typography.labelSmall,
                color = DarkOnSurfaceVariant
            )
        }
        Text(
            text = Money.formatNpr(view.balanceMinor),
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace,
            color = if (account.isActive) NeonCyan else DarkOnSurfaceVariant
        )
    }
}

@Composable
private fun CategorySpendRow(view: com.prasbin.shadowmoney.data.CategorySpendView) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = view.name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (view.categoryId == null) WarningAmber else DarkOnSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = Money.formatNpr(view.totalMinor),
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace,
            color = DarkOnSurface
        )
    }
}

@Composable
private fun TransactionRow(view: com.prasbin.shadowmoney.data.RecentTransactionView) {
    val transaction = view.transaction
    val isIncome = transaction.direction == TRANSACTION_DIRECTION_INCOME
    val description = if (transaction.note.isBlank()) "Transaction" else transaction.note
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = DarkOnSurface
            )
            Text(
                text = "${view.accountName} · ${view.categoryName} · ${dashboardDateFormat.format(Date(transaction.transactionTimestamp))}",
                style = MaterialTheme.typography.labelSmall,
                color = DarkOnSurfaceVariant
            )
        }
        Text(
            text = (if (isIncome) "+ " else "− ") + Money.formatNpr(transaction.amountMinor),
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace,
            color = if (isIncome) NeonGreen else NeonPurple
        )
    }
}

@Composable
private fun GoalRow(view: com.prasbin.shadowmoney.data.GoalProgressView) {
    val goal = view.goal
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = goal.name,
                style = MaterialTheme.typography.bodyLarge,
                color = DarkOnSurface,
                modifier = Modifier.weight(1f)
            )
            if (view.progressPercent != null) {
                Text(
                    text = "${view.progressPercent}%",
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    color = NeonCyan
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        if (view.progressPercent != null) {
            LinearProgressIndicator(
                progress = { view.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = NeonCyan,
                trackColor = BorderColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${Money.formatNpr(view.savedMinor ?: 0L)} of ${Money.formatNpr(goal.targetAmountMinor)}",
                style = MaterialTheme.typography.labelSmall,
                color = DarkOnSurfaceVariant
            )
        } else {
            Text(
                text = "No account linked · target ${Money.formatNpr(goal.targetAmountMinor)}",
                style = MaterialTheme.typography.labelSmall,
                color = WarningAmber
            )
        }
    }
}

private const val INTELLIGENCE_DISPLAY_CAP = 3

@Composable
private fun IntelligenceSection(state: IntelligenceUiState) {
    SystemPanel(title = "Intelligence") {
        when (state) {
            is IntelligenceUiState.Loading -> {
                SectionEmptyRow("Analyzing local records…")
            }
            is IntelligenceUiState.Error -> {
                Text(
                    text = "Intelligence unavailable: ${state.message}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ErrorRed
                )
            }
            is IntelligenceUiState.Content -> {
                val report = state.report
                Text(
                    text = if (report.sufficientData) {
                        "Deterministic analysis of stored records · window since ${windowDateFormat.format(Date(report.windowStart))}"
                    } else {
                        "Insufficient data for meaningful intelligence insights — more recorded history is needed"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (report.sufficientData) DarkOnSurfaceVariant else WarningAmber
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (report.insights.isEmpty()) {
                    SectionEmptyRow("No insight found")
                }
                val analysisInsights = report.insights.filter {
                    it.kind == com.prasbin.shadowmoney.intelligence.InsightKind.ANALYSIS
                }
                val otherInsights = report.insights.filter {
                    it.kind != com.prasbin.shadowmoney.intelligence.InsightKind.ANALYSIS
                }
                otherInsights.forEach { insight -> InsightRow(insight) }
                analysisInsights.take(INTELLIGENCE_DISPLAY_CAP).forEach { insight -> InsightRow(insight) }
                if (analysisInsights.size > INTELLIGENCE_DISPLAY_CAP) {
                    SectionEmptyRow("+${analysisInsights.size - INTELLIGENCE_DISPLAY_CAP} more analysis insights")
                }
            }
        }
    }
}

@Composable
private fun InsightRow(insight: com.prasbin.shadowmoney.intelligence.Insight) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = insightKindLabel(insight.kind),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = insightKindColor(insight.kind),
                fontWeight = FontWeight.Bold
            )
            if (insight.amountMinor != null) {
                Text(
                    text = "  ${Money.formatNpr(insight.amountMinor)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = DarkOnSurface
                )
            }
        }
        Text(
            text = insight.title,
            style = MaterialTheme.typography.bodyLarge,
            color = DarkOnSurface
        )
        Text(
            text = insight.summary,
            style = MaterialTheme.typography.bodySmall,
            color = DarkOnSurfaceVariant
        )
        if (insight.evidence != null) {
            Text(
                text = insight.evidence,
                style = MaterialTheme.typography.labelSmall,
                color = DarkOnSurfaceVariant
            )
        }
        insight.assumptions.forEach { assumption ->
            Text(
                text = "· $assumption",
                style = MaterialTheme.typography.labelSmall,
                color = AccentGold
            )
        }
    }
}

@Composable
fun MoneyPlaceholderScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Money") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Money — Not Implemented Yet",
                style = MaterialTheme.typography.headlineMedium,
                color = DarkOnSurfaceVariant
            )
            Text(
                text = "This feature will be available in Phase 2+",
                style = MaterialTheme.typography.bodyMedium,
                color = DarkOnSurfaceVariant
            )
        }
    }
}
