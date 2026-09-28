package com.prasbin.shadowmoney.presentation.screen.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.prasbin.shadowmoney.assistant.AssistantSection
import com.prasbin.shadowmoney.data.AssistantRepository
import com.prasbin.shadowmoney.data.BudgetRepository
import com.prasbin.shadowmoney.data.DashboardRepository
import com.prasbin.shadowmoney.data.GoalRepository
import com.prasbin.shadowmoney.data.IntelligenceRepository
import com.prasbin.shadowmoney.data.ShadowMoneyDatabase
import com.prasbin.shadowmoney.data.TelecomRepository
import com.prasbin.shadowmoney.data.WorkRepository
import com.prasbin.shadowmoney.data.OpportunityRepository
import com.prasbin.shadowmoney.intelligence.InsightKind
import com.prasbin.shadowmoney.presentation.theme.*

private fun kindLabel(kind: InsightKind): String = when (kind) {
    InsightKind.FACT -> "FACT"
    InsightKind.CALCULATION -> "CALCULATION"
    InsightKind.ANALYSIS -> "ANALYSIS"
    InsightKind.PROJECTION -> "PROJECTION"
}

private fun kindColor(kind: InsightKind): Color = when (kind) {
    InsightKind.FACT -> NeonCyan
    InsightKind.CALCULATION -> NeonGreen
    InsightKind.ANALYSIS -> NeonPurple
    InsightKind.PROJECTION -> AccentGold
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(navController: NavHostController) {
    val context = LocalContext.current
    val viewModel: AssistantViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = ShadowMoneyDatabase.getInstance(context.applicationContext)
                return AssistantViewModel(
                    repository = AssistantRepository(
                        dashboardRepository = DashboardRepository(
                            accountDao = database.accountDao(),
                            categoryDao = database.categoryDao(),
                            transactionDao = database.transactionDao(),
                            goalDao = database.goalDao(),
                            openHelper = database.openHelper
                        ),
                        budgetRepository = BudgetRepository(
                            budgetDao = database.budgetDao(),
                            transactionDao = database.transactionDao(),
                            categoryDao = database.categoryDao(),
                            openHelper = database.openHelper
                        ),
                        goalRepository = GoalRepository(
                            goalDao = database.goalDao(),
                            accountDao = database.accountDao(),
                            transactionDao = database.transactionDao()
                        ),
                        workRepository = WorkRepository(
                            workItemDao = database.workItemDao(),
                            transactionDao = database.transactionDao(),
                            openHelper = database.openHelper
                        ),
                        telecomRepository = TelecomRepository(database.telecomDao()),
                        opportunityRepository = OpportunityRepository(database.opportunityDao()),
                        intelligenceRepository = IntelligenceRepository(
                            transactionDao = database.transactionDao(),
                            categoryDao = database.categoryDao()
                        ),
                        transactionDao = database.transactionDao(),
                        workItemDao = database.workItemDao(),
                        opportunityDao = database.opportunityDao(),
                        telecomDao = database.telecomDao(),
                        openHelper = database.openHelper
                    )
                ) as T
            }
        }
    )
    val state by viewModel.state.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current

    fun send() {
        val text = input
        if (text.isBlank() || state.isBusy) return
        viewModel.submit(text)
        input = ""
        keyboard?.hide()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Financial Assistant",
                            style = MaterialTheme.typography.titleLarge,
                            color = NeonCyan
                        )
                        Text(
                            text = "Read-only · deterministic · local",
                            style = MaterialTheme.typography.labelSmall,
                            color = DarkOnSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = NeonCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                if (state.messages.isEmpty()) {
                    item { AssistantEmptyState(onSample = { viewModel.submit(it) }) }
                }
                items(state.messages) { message ->
                    AssistantMessageRow(message)
                }
            }
            if (state.isBusy) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = NeonCyan
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Ask about your local records") },
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = BorderColor,
                        focusedLabelColor = NeonCyan,
                        cursorColor = NeonCyan
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { send() },
                    enabled = input.isNotBlank() && !state.isBusy
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = NeonCyan)
                }
            }
            Text(
                text = "Session only — nothing you ask is saved to your records.",
                style = MaterialTheme.typography.labelSmall,
                color = DarkOnSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AssistantEmptyState(onSample: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Ask about your money",
            style = MaterialTheme.typography.headlineSmall,
            color = NeonCyan
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Supported topics: balances, income, spending, budgets, goals, work, " +
                "telecom, opportunities, recent transactions, and imports.",
            style = MaterialTheme.typography.bodyMedium,
            color = DarkOnSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Read-only — the assistant never changes your records. " +
                "Local records only · not a bank balance.",
            style = MaterialTheme.typography.labelSmall,
            color = DarkOnSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        OutlinedButton(
            onClick = { onSample("What is my balance?") },
            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan)
        ) {
            Text("What is my balance?", color = NeonCyan)
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { onSample("What did I spend this month?") },
            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan)
        ) {
            Text("What did I spend this month?", color = NeonCyan)
        }
    }
}

@Composable
private fun AssistantMessageRow(message: AssistantChatMessage) {
    if (message.isUser) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DarkSurfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            message.sections.forEach { section ->
                AssistantSectionRow(section)
                Spacer(modifier = Modifier.height(6.dp))
            }
            message.source?.let { source ->
                Text(
                    text = "SOURCE: $source",
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkOnSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun AssistantSectionRow(section: AssistantSection) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (section.kind != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = kindColor(section.kind).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = kindLabel(section.kind),
                        style = MaterialTheme.typography.labelSmall,
                        color = kindColor(section.kind),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(
            text = section.text,
            style = MaterialTheme.typography.bodyMedium,
            color = DarkOnSurface,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
