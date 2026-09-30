@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.prasbin.shadowmoney.presentation.screen.dashboard

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.prasbin.shadowmoney.presentation.navigation.Screen
import com.prasbin.shadowmoney.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TRUST_LABEL = "LOCAL RECORDS ONLY · NOT A BANK BALANCE"

private val dashboardDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
private val windowDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

private fun insightKindLabel(kind: com.prasbin.shadowmoney.intelligence.InsightKind): String = when (kind) {
    com.prasbin.shadowmoney.intelligence.InsightKind.FACT -> "FACT"
    com.prasbin.shadowmoney.intelligence.InsightKind.CALCULATION -> "CALC"
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
                            color = NeonCyan,
                            letterSpacing = 3.sp
                        )
                        Text(
                            text = "PERSONAL FINANCIAL SYSTEM",
                            style = MaterialTheme.typography.labelSmall,
                            color = DarkOnSurfaceVariant
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            navController?.navigate(Screen.Assistant.route)
                        }
                    ) {
                        Text("ASK", color = NeonCyan, style = MaterialTheme.typography.labelLarge)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        }
    ) { innerPadding ->
        when (val currentState = state) {
            is DashboardUiState.Loading -> DashboardLoading(modifier = Modifier.padding(innerPadding))
            is DashboardUiState.Empty -> DashboardEmpty(
                navController = navController,
                intelligenceState = intelligenceState,
                modifier = Modifier.padding(innerPadding)
            )
            is DashboardUiState.Error -> DashboardError(
                message = currentState.message,
                modifier = Modifier.padding(innerPadding)
            )
            is DashboardUiState.Content -> DashboardContent(
                state = currentState,
                intelligenceState = intelligenceState,
                navController = navController,
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
            text = "Reading local records…",
            style = MaterialTheme.typography.bodyMedium,
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
            text = "DASHBOARD UNAVAILABLE",
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

// ---- PRIMARY STATUS ----------------------------------------------------------

@Composable
private fun PrimaryStatusCard(
    balanceMinor: Long,
    incomeMinor: Long,
    outflowMinor: Long
) {
    val net = incomeMinor - outflowMinor
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderColor, MaterialTheme.shapes.medium),
        shape = MaterialTheme.shapes.medium,
        color = CardColor
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "TOTAL BALANCE",
                style = MaterialTheme.typography.labelSmall,
                color = NeonCyan,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = Money.formatNpr(balanceMinor),
                style = MaterialTheme.typography.displayLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (balanceMinor >= 0) DarkOnSurface else ErrorRed,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = TRUST_LABEL,
                style = MaterialTheme.typography.labelSmall,
                color = DarkOnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = BorderColor)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SystemMetric(
                    label = "Money in",
                    value = Money.formatNpr(incomeMinor),
                    valueColor = NeonGreen,
                    modifier = Modifier.weight(1f)
                )
                SystemMetric(
                    label = "Money out",
                    value = Money.formatNpr(outflowMinor),
                    valueColor = NeonPurple,
                    modifier = Modifier.weight(1f)
                )
                SystemMetric(
                    label = "Net",
                    value = Money.formatNpr(net),
                    valueColor = if (net >= 0) NeonGreen else ErrorRed,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ---- QUICK ACTIONS -----------------------------------------------------------

@Composable
private fun QuickActions(
    hasAccounts: Boolean,
    navController: androidx.navigation.NavHostController?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (hasAccounts) {
            SystemAction(
                label = "Money In",
                onClick = { navController?.navigate(Screen.Transactions.inDirection()) },
                accent = NeonGreen,
                modifier = Modifier.weight(1f)
            )
            SystemAction(
                label = "Money Out",
                onClick = { navController?.navigate(Screen.Transactions.outDirection()) },
                accent = NeonPurple,
                modifier = Modifier.weight(1f)
            )
            SystemAction(
                label = "Import",
                onClick = { navController?.navigate(Screen.Import.route) },
                accent = NeonCyan,
                modifier = Modifier.weight(1f)
            )
        } else {
            SystemAction(
                label = "Add Account",
                onClick = { navController?.navigate(Screen.Money.route) },
                accent = NeonCyan,
                modifier = Modifier.weight(1f)
            )
            SystemAction(
                label = "Import CSV",
                onClick = { navController?.navigate(Screen.Import.route) },
                accent = NeonCyan,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ---- EMPTY / CONTENT ---------------------------------------------------------

@Composable
private fun DashboardEmpty(
    navController: androidx.navigation.NavHostController?,
    intelligenceState: IntelligenceUiState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { PrimaryStatusCard(balanceMinor = 0L, incomeMinor = 0L, outflowMinor = 0L) }
        item { QuickActions(hasAccounts = false, navController = navController) }
        item {
            SystemEmptyState(
                title = "No records yet",
                message = "Add an account to start tracking money in and out. Everything stays on this device.",
                actionLabel = "Add first account",
                onAction = { navController?.navigate(Screen.Money.route) }
            )
        }
        item { IntelligenceSection(state = intelligenceState) }
    }
}

@Composable
private fun DashboardContent(
    state: DashboardUiState.Content,
    intelligenceState: IntelligenceUiState,
    navController: androidx.navigation.NavHostController?,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PrimaryStatusCard(
                balanceMinor = state.totalBalanceMinor,
                incomeMinor = state.totalIncomeMinor,
                outflowMinor = state.totalOutflowMinor
            )
        }
        item { QuickActions(hasAccounts = state.accounts.isNotEmpty(), navController = navController) }

        item {
            SystemPanel(
                title = "Recent activity",
                actionLabel = "VIEW ALL >",
                onAction = { navController?.navigate(Screen.Transactions.route) }
            ) {
                if (state.recentTransactions.isEmpty()) {
                    SectionEmptyRow("No transactions yet")
                }
                state.recentTransactions.take(5).forEach { view ->
                    TransactionRow(view)
                }
            }
        }

        if (state.categoryOutflow.isNotEmpty()) {
            item {
                SystemPanel(title = "Spending by category") {
                    state.categoryOutflow.take(5).forEach { view ->
                        CategorySpendRow(view)
                    }
                }
            }
        }

        if (state.goals.isNotEmpty()) {
            item {
                SystemPanel(
                    title = "Goals",
                    actionLabel = "ALL >",
                    onAction = { navController?.navigate(Screen.Goals.route) }
                ) {
                    state.goals.take(3).forEach { view ->
                        GoalRow(view)
                    }
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
private fun CategorySpendRow(view: com.prasbin.shadowmoney.data.CategorySpendView) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (view.categoryId == null) "${view.name} · uncategorized" else view.name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (view.categoryId == null) WarningAmber else DarkOnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = Money.formatNpr(view.totalMinor),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = DarkOnSurface
        )
    }
}

@Composable
private fun TransactionRow(view: com.prasbin.shadowmoney.data.RecentTransactionView) {
    val transaction = view.transaction
    val isIncome = transaction.direction == TRANSACTION_DIRECTION_INCOME
    val description = if (transaction.note.isBlank()) view.categoryName else transaction.note
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SystemChip(
            text = if (isIncome) "IN" else "OUT",
            color = if (isIncome) NeonGreen else NeonPurple
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = DarkOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${view.categoryName} · ${view.accountName} · ${dashboardDateFormat.format(Date(transaction.transactionTimestamp))}",
                style = MaterialTheme.typography.labelSmall,
                color = DarkOnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = (if (isIncome) "+ " else "− ") + Money.formatNpr(transaction.amountMinor),
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = if (isIncome) NeonGreen else DarkOnSurface,
            maxLines = 1
        )
    }
}

@Composable
private fun GoalRow(view: com.prasbin.shadowmoney.data.GoalProgressView) {
    val goal = view.goal
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = goal.name,
                style = MaterialTheme.typography.bodyLarge,
                color = DarkOnSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (view.progressPercent != null) {
                Text(
                    text = "${view.progressPercent}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = NeonCyan
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        if (view.progressPercent != null) {
            LinearProgressIndicator(
                progress = { view.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = NeonCyan,
                trackColor = BorderColor,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            Spacer(modifier = Modifier.height(5.dp))
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
                        "DETERMINISTIC ANALYSIS OF LOCAL RECORDS · SINCE ${windowDateFormat.format(Date(report.windowStart))}"
                    } else {
                        "NEEDS MORE RECORDED HISTORY FOR MEANINGFUL INSIGHTS"
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
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SystemChip(
                text = insightKindLabel(insight.kind),
                color = insightKindColor(insight.kind)
            )
            if (insight.amountMinor != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = Money.formatNpr(insight.amountMinor),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = DarkOnSurface
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
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
