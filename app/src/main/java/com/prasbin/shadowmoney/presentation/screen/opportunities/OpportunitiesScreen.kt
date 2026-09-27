@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.prasbin.shadowmoney.presentation.screen.opportunities

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInNew
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
import com.prasbin.shadowmoney.data.OpportunityMath
import com.prasbin.shadowmoney.data.ShadowMoneyDatabase
import com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_ARCHIVED
import com.prasbin.shadowmoney.data.model.Opportunity
import com.prasbin.shadowmoney.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val opportunityDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpportunitiesScreen(navController: NavHostController) {
    val context = LocalContext.current
    val viewModel: OpportunitiesViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = ShadowMoneyDatabase.getInstance(context.applicationContext)
                return OpportunitiesViewModel(
                    com.prasbin.shadowmoney.data.OpportunityRepository(database.opportunityDao())
                ) as T
            }
        }
    )
    val state by viewModel.uiState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var showForm by remember { mutableStateOf(false) }
    var editingOpportunity by remember { mutableStateOf<Opportunity?>(null) }
    var detailOpportunity by remember { mutableStateOf<Opportunity?>(null) }
    var archiveTarget by remember { mutableStateOf<Opportunity?>(null) }
    var deleteTarget by remember { mutableStateOf<Opportunity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Opportunities") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        },
        floatingActionButton = {
            if (state is OpportunitiesUiState.Content || state is OpportunitiesUiState.Empty) {
                FloatingActionButton(
                    onClick = {
                        editingOpportunity = null
                        showForm = true
                    },
                    containerColor = NeonCyan,
                    contentColor = DarkPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add opportunity")
                }
            }
        }
    ) { innerPadding ->
        when (val currentState = state) {
            is OpportunitiesUiState.Loading -> OpportunitiesLoading(modifier = Modifier.padding(innerPadding))
            is OpportunitiesUiState.Error -> OpportunitiesError(
                message = currentState.message,
                modifier = Modifier.padding(innerPadding)
            )
            is OpportunitiesUiState.Empty -> OpportunitiesEmpty(
                onAdd = {
                    editingOpportunity = null
                    showForm = true
                },
                modifier = Modifier.padding(innerPadding)
            )
            is OpportunitiesUiState.Content -> OpportunitiesContent(
                state = currentState,
                viewModel = viewModel,
                onOpen = { opportunity -> detailOpportunity = opportunity },
                onEdit = { opportunity ->
                    editingOpportunity = opportunity
                    showForm = true
                },
                onArchive = { opportunity -> archiveTarget = opportunity },
                onDelete = { opportunity -> deleteTarget = opportunity },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }

    if (showForm) {
        OpportunityFormDialog(
            initialOpportunity = editingOpportunity,
            onDismiss = { showForm = false },
            onSave = { title, description, type, source, sourceUrl, expectedAmountMinor, status, deadlineTimestamp, client, existing ->
                if (existing == null) {
                    viewModel.create(
                        title, description, type, source, sourceUrl,
                        expectedAmountMinor, deadlineTimestamp, client
                    )
                } else {
                    viewModel.update(
                        existing, title, description, type, source, sourceUrl,
                        expectedAmountMinor, status, deadlineTimestamp, client
                    )
                }
                showForm = false
            }
        )
    }

    detailOpportunity?.let { opportunity ->
        OpportunityDetailDialog(
            opportunity = opportunity,
            onDismiss = { detailOpportunity = null },
            onOpenUrl = { url ->
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        )
    }

    archiveTarget?.let { opportunity ->
        AlertDialog(
            onDismissRequest = { archiveTarget = null },
            containerColor = DarkSurfaceVariant,
            title = { Text("Archive opportunity", color = NeonCyan) },
            text = { Text("Archive \"${opportunity.title}\"? It will be hidden from the active list.", color = DarkOnSurface) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.archive(opportunity.id)
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

    deleteTarget?.let { opportunity ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = DarkSurfaceVariant,
            title = { Text("Delete opportunity", color = ErrorRed) },
            text = {
                Text(
                    "Delete \"${opportunity.title}\"? This only removes the opportunity record - no financial transactions are affected.",
                    color = DarkOnSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.delete(opportunity)
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
private fun OpportunitiesLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = NeonCyan)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Loading opportunities...",
            style = MaterialTheme.typography.bodyMedium,
            color = DarkOnSurfaceVariant
        )
    }
}

@Composable
private fun OpportunitiesEmpty(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No opportunities found",
            style = MaterialTheme.typography.headlineMedium,
            color = NeonCyan
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Track freelance jobs, client work, and projects. Expected amounts are never income.",
            style = MaterialTheme.typography.bodyMedium,
            color = DarkOnSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onAdd,
            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkPrimary)
        ) {
            Text("Add opportunity")
        }
    }
}

@Composable
private fun OpportunitiesError(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Opportunities unavailable",
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
private fun OpportunitiesContent(
    state: OpportunitiesUiState.Content,
    viewModel: OpportunitiesViewModel,
    onOpen: (Opportunity) -> Unit,
    onEdit: (Opportunity) -> Unit,
    onArchive: (Opportunity) -> Unit,
    onDelete: (Opportunity) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<Int?>(null) }
    var typeFilter by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(searchQuery, statusFilter, typeFilter) {
        viewModel.setSearchQuery(searchQuery)
        viewModel.setStatusFilter(statusFilter)
        viewModel.setTypeFilter(typeFilter)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            SystemPanel(title = "Summary") {
                SummaryRow("Active opportunities", state.summary.activeCount.toString())
                SummaryRow(
                    "Total stored expected amount",
                    Money.formatNpr(state.summary.totalExpectedAmountMinor)
                )
                SummaryRow("Needs review", state.summary.needsReviewCount.toString())
                if (state.summary.upcomingDeadlines.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Upcoming deadlines (max 5)",
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkOnSurfaceVariant
                    )
                    state.summary.upcomingDeadlines.forEach { opportunity ->
                        Text(
                            text = "${opportunityDateFormat.format(Date(opportunity.deadlineTimestamp))} - ${opportunity.title}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = DarkOnSurfaceVariant
                        )
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search title, client, source, notes") },
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
        item {
            TypeFilterChips(selected = typeFilter, onSelect = { typeFilter = it })
        }
        items(state.opportunities, key = { it.opportunity.id }) { view ->
            OpportunityCard(
                view = view,
                onOpen = { onOpen(view.opportunity) },
                onEdit = { onEdit(view.opportunity) },
                onArchive = { onArchive(view.opportunity) },
                onDelete = { onDelete(view.opportunity) }
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
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
            color = DarkOnSurface
        )
    }
}

@Composable
private fun StatusFilterChips(selected: Int?, onSelect: (Int?) -> Unit) {
    val options = listOf(
        null to "All",
        com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_NEW to "New",
        com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_REVIEWING to "Reviewing",
        com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_APPLIED to "Applied",
        com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_IN_PROGRESS to "In Progress",
        com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_WON to "Won",
        com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_LOST to "Lost",
        OPPORTUNITY_STATUS_ARCHIVED to "Archived"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.take(5).forEach { (value, label) ->
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.drop(5).forEach { (value, label) ->
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
private fun TypeFilterChips(selected: Int?, onSelect: (Int?) -> Unit) {
    val options = listOf(
        null to "All",
        com.prasbin.shadowmoney.data.model.OPPORTUNITY_TYPE_FREELANCE to "Freelance",
        com.prasbin.shadowmoney.data.model.OPPORTUNITY_TYPE_CLIENT_WORK to "Client Work",
        com.prasbin.shadowmoney.data.model.OPPORTUNITY_TYPE_PART_TIME to "Part Time",
        com.prasbin.shadowmoney.data.model.OPPORTUNITY_TYPE_REMOTE_WORK to "Remote Work",
        com.prasbin.shadowmoney.data.model.OPPORTUNITY_TYPE_PROJECT to "Project",
        com.prasbin.shadowmoney.data.model.OPPORTUNITY_TYPE_REPOSITORY to "Repository",
        com.prasbin.shadowmoney.data.model.OPPORTUNITY_TYPE_OTHER to "Other"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.take(5).forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NeonPurple,
                    selectedLabelColor = DarkSurface
                )
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.drop(5).forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NeonPurple,
                    selectedLabelColor = DarkSurface
                )
            )
        }
    }
}

@Composable
private fun OpportunityCard(
    view: com.prasbin.shadowmoney.data.OpportunityView,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    val opportunity = view.opportunity
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = CardColor,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = opportunity.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = DarkOnSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = OpportunityMath.statusLabel(opportunity.status),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = statusColor(opportunity.status),
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = DarkOnSurfaceVariant, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onArchive, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Archive, contentDescription = "Archive", tint = WarningAmber, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed, modifier = Modifier.size(18.dp))
                }
            }
            Text(
                text = OpportunityMath.typeLabel(opportunity.type),
                style = MaterialTheme.typography.labelSmall,
                color = NeonPurple
            )
            if (opportunity.source.isNotBlank()) {
                Text(
                    text = "Source: ${opportunity.source}",
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkOnSurfaceVariant
                )
            }
            if (opportunity.expectedAmountMinor != null) {
                Text(
                    text = "Expected: ${Money.formatNpr(opportunity.expectedAmountMinor)} (not income)",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = WarningAmber
                )
            }
            if (opportunity.deadlineTimestamp > 0L) {
                Text(
                    text = "Deadline: ${opportunityDateFormat.format(Date(opportunity.deadlineTimestamp))} (${com.prasbin.shadowmoney.data.WorkMath.deadlineLabel(view.deadlineStatus)})",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (view.deadlineStatus == com.prasbin.shadowmoney.data.DeadlineStatus.OVERDUE) ErrorRed else DarkOnSurfaceVariant
                )
            }
        }
    }
}

private fun statusColor(status: Int): androidx.compose.ui.graphics.Color = when (status) {
    com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_NEW -> NeonCyan
    com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_REVIEWING -> WarningAmber
    com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_APPLIED -> NeonPurple
    com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_IN_PROGRESS -> NeonCyan
    com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_WON -> NeonGreen
    com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_LOST -> ErrorRed
    OPPORTUNITY_STATUS_ARCHIVED -> DarkOnSurfaceVariant
    else -> DarkOnSurfaceVariant
}

@Composable
private fun OpportunityDetailDialog(
    opportunity: Opportunity,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val view = com.prasbin.shadowmoney.data.OpportunityView(
        opportunity = opportunity,
        deadlineStatus = com.prasbin.shadowmoney.data.WorkMath.deadlineStatus(
            opportunity.deadlineTimestamp,
            System.currentTimeMillis()
        ),
        githubReference = com.prasbin.shadowmoney.data.UrlParser.parseGithubReference(opportunity.sourceUrl)
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceVariant,
        title = { Text(opportunity.title, color = NeonCyan) },
        text = {
            Column {
                DetailRow("Type", OpportunityMath.typeLabel(opportunity.type))
                DetailRow("Status", OpportunityMath.statusLabel(opportunity.status))
                DetailRow("Source", opportunity.source.ifBlank { "-" })
                if (opportunity.client.isNotBlank()) {
                    DetailRow("Client / company", opportunity.client)
                }
                DetailRow(
                    "Expected amount",
                    opportunity.expectedAmountMinor?.let { Money.formatNpr(it) } ?: "Not set"
                )
                Text(
                    text = "Expected opportunity amount - not financial income.",
                    style = MaterialTheme.typography.labelSmall,
                    color = WarningAmber
                )
                DetailRow(
                    "Deadline",
                    if (opportunity.deadlineTimestamp > 0L) {
                        "${opportunityDateFormat.format(Date(opportunity.deadlineTimestamp))} (${com.prasbin.shadowmoney.data.WorkMath.deadlineLabel(view.deadlineStatus)})"
                    } else {
                        "No deadline"
                    }
                )
                DetailRow(
                    "Created",
                    opportunityDateFormat.format(Date(opportunity.createdTimestamp))
                )
                if (opportunity.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = opportunity.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = DarkOnSurfaceVariant
                    )
                }
                if (opportunity.sourceUrl.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Reference: ${opportunity.sourceUrl}",
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkOnSurfaceVariant
                    )
                    view.githubReference?.let { ref ->
                        Text(
                            text = "GitHub: ${ref.owner}/${ref.repository}${ref.reference?.let { "/$it" } ?: ""}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = NeonCyan
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (opportunity.sourceUrl.isNotBlank()) {
                TextButton(onClick = { onOpenUrl(opportunity.sourceUrl) }) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, tint = NeonCyan)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Open reference", color = NeonCyan)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = DarkOnSurfaceVariant)
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
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
            color = DarkOnSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpportunityFormDialog(
    initialOpportunity: Opportunity?,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String, type: Int, source: String, sourceUrl: String, expectedAmountMinor: Long?, status: Int, deadlineTimestamp: Long, client: String, existing: Opportunity?) -> Unit
) {
    var title by remember { mutableStateOf(initialOpportunity?.title ?: "") }
    var description by remember { mutableStateOf(initialOpportunity?.description ?: "") }
    var type by remember { mutableStateOf(initialOpportunity?.type ?: com.prasbin.shadowmoney.data.model.OPPORTUNITY_TYPE_FREELANCE) }
    var source by remember { mutableStateOf(initialOpportunity?.source ?: "") }
    var sourceUrl by remember { mutableStateOf(initialOpportunity?.sourceUrl ?: "") }
    var amountText by remember {
        mutableStateOf(
            initialOpportunity?.expectedAmountMinor?.let { Money.formatNpr(it).removePrefix("NPR ") } ?: ""
        )
    }
    var status by remember { mutableStateOf(initialOpportunity?.status ?: com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_NEW) }
    var deadlineText by remember {
        mutableStateOf(
            initialOpportunity?.deadlineTimestamp?.let { opportunityDateFormat.format(Date(it)) } ?: ""
        )
    }
    var client by remember { mutableStateOf(initialOpportunity?.client ?: "") }
    var titleError by remember { mutableStateOf(false) }
    var urlError by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }
    var deadlineError by remember { mutableStateOf(false) }
    var typeDropdownExpanded by remember { mutableStateOf(false) }
    var statusDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceVariant,
        title = { Text(if (initialOpportunity == null) "Add Opportunity" else "Edit Opportunity", color = NeonCyan) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; titleError = false },
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
                    label = { Text("Description / notes") },
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
                ExposedDropdownMenuBox(
                    expanded = typeDropdownExpanded,
                    onExpandedChange = { typeDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = OpportunityMath.typeLabel(type),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeDropdownExpanded) },
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
                        expanded = typeDropdownExpanded,
                        onDismissRequest = { typeDropdownExpanded = false }
                    ) {
                        listOf(
                            com.prasbin.shadowmoney.data.model.OPPORTUNITY_TYPE_FREELANCE to "Freelance",
                            com.prasbin.shadowmoney.data.model.OPPORTUNITY_TYPE_CLIENT_WORK to "Client Work",
                            com.prasbin.shadowmoney.data.model.OPPORTUNITY_TYPE_PART_TIME to "Part Time",
                            com.prasbin.shadowmoney.data.model.OPPORTUNITY_TYPE_REMOTE_WORK to "Remote Work",
                            com.prasbin.shadowmoney.data.model.OPPORTUNITY_TYPE_PROJECT to "Project",
                            com.prasbin.shadowmoney.data.model.OPPORTUNITY_TYPE_REPOSITORY to "Repository",
                            com.prasbin.shadowmoney.data.model.OPPORTUNITY_TYPE_OTHER to "Other"
                        ).forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    type = value
                                    typeDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    label = { Text("Source (LinkedIn, Upwork, GitHub, ... )") },
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
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = sourceUrl,
                    onValueChange = { sourceUrl = it; urlError = false },
                    label = { Text("Source URL / reference (optional)") },
                    isError = urlError,
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
                    onValueChange = { amountText = it; amountError = false },
                    label = { Text("Expected amount (NPR, optional - not income)") },
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
                    expanded = statusDropdownExpanded,
                    onExpandedChange = { statusDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = OpportunityMath.statusLabel(status),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Status") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusDropdownExpanded) },
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
                        expanded = statusDropdownExpanded,
                        onDismissRequest = { statusDropdownExpanded = false }
                    ) {
                        listOf(
                            com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_NEW to "New",
                            com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_REVIEWING to "Reviewing",
                            com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_APPLIED to "Applied",
                            com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_IN_PROGRESS to "In Progress",
                            com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_WON to "Won",
                            com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_LOST to "Lost",
                            OPPORTUNITY_STATUS_ARCHIVED to "Archived"
                        ).forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    status = value
                                    statusDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = deadlineText,
                    onValueChange = { deadlineText = it; deadlineError = false },
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
                    label = { Text("Client / company (optional)") },
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
                if (urlError) {
                    Text("Enter a valid URL (https://...) or leave empty", style = MaterialTheme.typography.labelSmall, color = ErrorRed)
                }
                if (amountError) {
                    Text("Enter a valid non-negative amount or leave empty", style = MaterialTheme.typography.labelSmall, color = ErrorRed)
                }
                if (deadlineError) {
                    Text("Enter a valid date (yyyy-MM-dd) or leave empty", style = MaterialTheme.typography.labelSmall, color = ErrorRed)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amountMinor = com.prasbin.shadowmoney.presentation.screen.budgets.parseNprToMinor(amountText)
                    val deadlineMillis = parseOpportunityDate(deadlineText)
                    if (title.isBlank()) titleError = true
                    if (sourceUrl.isNotBlank() && !com.prasbin.shadowmoney.data.UrlParser.isValidUrl(sourceUrl)) urlError = true
                    if (amountText.isNotBlank() && (amountMinor == null || amountMinor < 0L)) amountError = true
                    if (deadlineText.isNotBlank() && deadlineMillis == null) deadlineError = true
                    if (title.isNotBlank() && !urlError && !amountError && !deadlineError) {
                        onSave(
                            title, description, type, source, sourceUrl,
                            amountMinor?.takeIf { amountText.isNotBlank() },
                            status, deadlineMillis ?: 0L, client, initialOpportunity
                        )
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

private fun parseOpportunityDate(input: String): Long? {
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
