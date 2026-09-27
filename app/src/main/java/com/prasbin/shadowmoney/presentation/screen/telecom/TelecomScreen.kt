@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.prasbin.shadowmoney.presentation.screen.telecom

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
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
import com.prasbin.shadowmoney.data.ShadowMoneyDatabase
import com.prasbin.shadowmoney.data.TelecomRepository
import com.prasbin.shadowmoney.data.model.BILLING_PERIOD_MONTHLY
import com.prasbin.shadowmoney.data.model.BILLING_PERIOD_QUARTERLY
import com.prasbin.shadowmoney.data.model.BILLING_PERIOD_WEEKLY
import com.prasbin.shadowmoney.data.model.BILLING_PERIOD_YEARLY
import com.prasbin.shadowmoney.data.model.SIM_STATUS_ACTIVE
import com.prasbin.shadowmoney.data.model.TelecomPackage
import com.prasbin.shadowmoney.data.model.TelecomSim
import com.prasbin.shadowmoney.data.model.TelecomSubscription
import com.prasbin.shadowmoney.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val telecomDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

private fun periodLabel(period: Int): String = when (period) {
    BILLING_PERIOD_WEEKLY -> "Weekly"
    BILLING_PERIOD_MONTHLY -> "Monthly"
    BILLING_PERIOD_QUARTERLY -> "Quarterly"
    BILLING_PERIOD_YEARLY -> "Yearly"
    else -> "Unknown"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelecomScreen(navController: NavHostController) {
    val context = LocalContext.current
    val viewModel: TelecomViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = ShadowMoneyDatabase.getInstance(context.applicationContext)
                return TelecomViewModel(
                    TelecomRepository(database.telecomDao())
                ) as T
            }
        }
    )
    val state by viewModel.uiState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var showSimForm by remember { mutableStateOf(false) }
    var editingSim by remember { mutableStateOf<TelecomSim?>(null) }
    var showPackageForm by remember { mutableStateOf(false) }
    var editingPackage by remember { mutableStateOf<TelecomPackage?>(null) }
    var showSubscriptionForm by remember { mutableStateOf(false) }
    var editingSubscription by remember { mutableStateOf<TelecomSubscription?>(null) }
    var deleteSubscriptionTarget by remember { mutableStateOf<TelecomSubscription?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Telecom Tracker") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        }
    ) { innerPadding ->
        when (val currentState = state) {
            is TelecomUiState.Loading -> TelecomLoading(modifier = Modifier.padding(innerPadding))
            is TelecomUiState.Error -> TelecomError(
                message = currentState.message,
                modifier = Modifier.padding(innerPadding)
            )
            is TelecomUiState.Empty -> TelecomEmpty(
                onAddSim = {
                    editingSim = null
                    showSimForm = true
                },
                modifier = Modifier.padding(innerPadding)
            )
            is TelecomUiState.Content -> TelecomContent(
                state = currentState,
                viewModel = viewModel,
                onAddSim = {
                    editingSim = null
                    showSimForm = true
                },
                onEditSim = { sim ->
                    editingSim = sim
                    showSimForm = true
                },
                onAddPackage = {
                    editingPackage = null
                    showPackageForm = true
                },
                onEditPackage = { pkg ->
                    editingPackage = pkg
                    showPackageForm = true
                },
                onAddSubscription = {
                    editingSubscription = null
                    showSubscriptionForm = true
                },
                onEditSubscription = { subscription ->
                    editingSubscription = subscription
                    showSubscriptionForm = true
                },
                onDeleteSubscription = { subscription -> deleteSubscriptionTarget = subscription },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }

    if (showSimForm) {
        SimFormDialog(
            initialSim = editingSim,
            onDismiss = { showSimForm = false },
            onSave = { label, carrier, phoneNumber, notes, existing ->
                if (existing == null) {
                    viewModel.createSim(label, carrier, phoneNumber, notes)
                } else {
                    viewModel.updateSim(existing, label, carrier, phoneNumber, notes)
                }
                showSimForm = false
            }
        )
    }

    if (showPackageForm) {
        PackageFormDialog(
            initialPackage = editingPackage,
            onDismiss = { showPackageForm = false },
            onSave = { name, carrier, category, priceMinor, period, notes, existing ->
                if (existing == null) {
                    viewModel.createPackage(name, carrier, category, priceMinor, period, notes)
                } else {
                    viewModel.updatePackage(existing, name, carrier, category, priceMinor, period, notes)
                }
                showPackageForm = false
            }
        )
    }

    if (showSubscriptionForm) {
        SubscriptionFormDialog(
            initialSubscription = editingSubscription,
            sims = (state as? TelecomUiState.Content)?.sims ?: emptyList(),
            packages = (state as? TelecomUiState.Content)?.packages ?: emptyList(),
            onDismiss = { showSubscriptionForm = false },
            onSave = { simId, packageId, startTimestamp, renewalTimestamp, monthlyCostMinor, existing ->
                if (existing == null) {
                    viewModel.createSubscription(simId, packageId, startTimestamp, renewalTimestamp, monthlyCostMinor)
                } else {
                    viewModel.updateSubscription(
                        existing, simId, packageId, startTimestamp, renewalTimestamp, monthlyCostMinor
                    )
                }
                showSubscriptionForm = false
            }
        )
    }

    deleteSubscriptionTarget?.let { subscription ->
        AlertDialog(
            onDismissRequest = { deleteSubscriptionTarget = null },
            containerColor = DarkSurfaceVariant,
            title = { Text("Delete subscription", color = ErrorRed) },
            text = {
                Text(
                    "Delete this subscription record? No financial transactions are affected.",
                    color = DarkOnSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSubscription(subscription)
                        deleteSubscriptionTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = DarkOnSurface)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteSubscriptionTarget = null }) {
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
private fun TelecomLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = NeonCyan)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Loading telecom data...",
            style = MaterialTheme.typography.bodyMedium,
            color = DarkOnSurfaceVariant
        )
    }
}

@Composable
private fun TelecomEmpty(onAddSim: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No telecom records yet",
            style = MaterialTheme.typography.headlineMedium,
            color = NeonCyan
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Track SIMs, packages, and subscriptions manually. Expected costs never become financial records.",
            style = MaterialTheme.typography.bodyMedium,
            color = DarkOnSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onAddSim,
            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkPrimary)
        ) {
            Text("Add SIM")
        }
    }
}

@Composable
private fun TelecomError(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Telecom tracker unavailable",
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
private fun TelecomContent(
    state: TelecomUiState.Content,
    viewModel: TelecomViewModel,
    onAddSim: () -> Unit,
    onEditSim: (TelecomSim) -> Unit,
    onAddPackage: () -> Unit,
    onEditPackage: (TelecomPackage) -> Unit,
    onAddSubscription: () -> Unit,
    onEditSubscription: (TelecomSubscription) -> Unit,
    onDeleteSubscription: (TelecomSubscription) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            SystemPanel(title = "Summary") {
                Text(
                    text = "Expected values - not actual bills. Local/manual tracking only.",
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkOnSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                SummaryRow("Active SIMs", state.summary.activeSimCount.toString())
                SummaryRow("Active subscriptions", state.summary.activeSubscriptionCount.toString())
                SummaryRow(
                    "Expected monthly telecom cost",
                    Money.formatNpr(state.summary.expectedMonthlyCostMinor)
                )
                if (state.summary.nextRenewal != null) {
                    SummaryRow(
                        "Next renewal",
                        "${telecomDateFormat.format(Date(state.summary.nextRenewal!!.renewalTimestamp))} - ${state.summary.nextRenewal!!.simLabel}"
                    )
                } else {
                    SummaryRow("Next renewal", "None upcoming")
                }
                if (state.summary.upcomingRenewals.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Upcoming renewals (next 60 days, max 5)",
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkOnSurfaceVariant
                    )
                    state.summary.upcomingRenewals.forEach { renewal ->
                        Text(
                            text = "${telecomDateFormat.format(Date(renewal.renewalTimestamp))} - ${renewal.simLabel} - ${renewal.packageName} - expected ${Money.formatNpr(renewal.expectedMonthlyCostMinor)}/mo",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = DarkOnSurfaceVariant
                        )
                    }
                }
            }
        }
        item {
            SystemPanel(title = "SIMs") {
                Text(
                    text = "Manually tracked - no device/SIM access.",
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkOnSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (state.sims.isEmpty()) {
                    SectionEmptyRow("No SIMs tracked yet")
                }
                state.sims.forEach { sim ->
                    SimRow(
                        sim = sim,
                        onEdit = { onEditSim(sim) },
                        onArchive = { viewModel.archiveSim(sim) },
                        onActivate = { viewModel.activateSim(sim) }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onAddSim,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add SIM")
                }
            }
        }
        item {
            SystemPanel(title = "Packages / Plans") {
                Text(
                    text = "User-entered prices - not carrier bills.",
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkOnSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (state.packages.isEmpty()) {
                    SectionEmptyRow("No packages yet")
                }
                state.packages.forEach { pkg ->
                    PackageRow(
                        pkg = pkg,
                        onEdit = { onEditPackage(pkg) },
                        onArchive = { viewModel.archivePackage(pkg) },
                        onActivate = { viewModel.activatePackage(pkg) }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onAddPackage,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add package")
                }
            }
        }
        item {
            SystemPanel(title = "Subscriptions") {
                Text(
                    text = "Never creates financial transactions.",
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkOnSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (state.subscriptions.isEmpty()) {
                    SectionEmptyRow("No subscriptions yet")
                }
                state.subscriptions.forEach { subscription ->
                    SubscriptionRow(
                        subscription = subscription,
                        simLabel = state.sims.firstOrNull { it.id == subscription.simId }?.label ?: "Unknown SIM",
                        packageName = state.packages.firstOrNull { it.id == subscription.packageId }?.name ?: "Unknown package",
                        onEdit = { onEditSubscription(subscription) },
                        onDeactivate = { viewModel.deactivateSubscription(subscription) },
                        onActivate = { viewModel.activateSubscription(subscription) },
                        onDelete = { onDeleteSubscription(subscription) }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onAddSubscription,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add subscription")
                }
            }
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
private fun SectionEmptyRow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = DarkOnSurfaceVariant
    )
}

@Composable
private fun SimRow(
    sim: TelecomSim,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onActivate: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = sim.label, style = MaterialTheme.typography.bodyLarge, color = DarkOnSurface)
            Text(
                text = buildString {
                    append(sim.carrier)
                    if (sim.phoneNumber.isNotBlank()) append(" - ${sim.phoneNumber}")
                    append(if (sim.status == SIM_STATUS_ACTIVE) " - Active" else " - Archived")
                },
                style = MaterialTheme.typography.labelSmall,
                color = DarkOnSurfaceVariant
            )
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Edit, contentDescription = "Edit SIM", tint = DarkOnSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        if (sim.status == SIM_STATUS_ACTIVE) {
            IconButton(onClick = onArchive, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Archive, contentDescription = "Archive SIM", tint = WarningAmber, modifier = Modifier.size(18.dp))
            }
        } else {
            IconButton(onClick = onActivate, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Activate SIM", tint = NeonGreen, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun PackageRow(
    pkg: TelecomPackage,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onActivate: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = pkg.name, style = MaterialTheme.typography.bodyLarge, color = DarkOnSurface)
            Text(
                text = "${pkg.carrier} - ${pkg.category} - ${periodLabel(pkg.period)} - ${Money.formatNpr(pkg.priceMinor)}",
                style = MaterialTheme.typography.labelSmall,
                color = DarkOnSurfaceVariant
            )
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Edit, contentDescription = "Edit package", tint = DarkOnSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        if (pkg.isActive) {
            IconButton(onClick = onArchive, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Archive, contentDescription = "Archive package", tint = WarningAmber, modifier = Modifier.size(18.dp))
            }
        } else {
            IconButton(onClick = onActivate, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Activate package", tint = NeonGreen, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SubscriptionRow(
    subscription: TelecomSubscription,
    simLabel: String,
    packageName: String,
    onEdit: () -> Unit,
    onDeactivate: () -> Unit,
    onActivate: () -> Unit,
    onDelete: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = packageName, style = MaterialTheme.typography.bodyLarge, color = DarkOnSurface)
                Text(
                    text = "$simLabel - from ${telecomDateFormat.format(Date(subscription.startTimestamp))} - renews ${telecomDateFormat.format(Date(subscription.renewalTimestamp))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkOnSurfaceVariant
                )
                Text(
                    text = if (subscription.isActive) "Active" else "Inactive",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = if (subscription.isActive) NeonGreen else DarkOnSurfaceVariant
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Edit subscription", tint = DarkOnSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            if (subscription.isActive) {
                IconButton(onClick = onDeactivate, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Archive, contentDescription = "Deactivate", tint = WarningAmber, modifier = Modifier.size(18.dp))
                }
            } else {
                IconButton(onClick = onActivate, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Activate", tint = NeonGreen, modifier = Modifier.size(18.dp))
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete subscription", tint = ErrorRed, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimFormDialog(
    initialSim: TelecomSim?,
    onDismiss: () -> Unit,
    onSave: (label: String, carrier: String, phoneNumber: String, notes: String, existing: TelecomSim?) -> Unit
) {
    var label by remember { mutableStateOf(initialSim?.label ?: "") }
    var carrier by remember { mutableStateOf(initialSim?.carrier ?: "") }
    var phoneNumber by remember { mutableStateOf(initialSim?.phoneNumber ?: "") }
    var notes by remember { mutableStateOf(initialSim?.notes ?: "") }
    var labelError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceVariant,
        title = { Text(if (initialSim == null) "Add SIM" else "Edit SIM", color = NeonCyan) },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it; labelError = false },
                    label = { Text("Label") },
                    isError = labelError,
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
                    value = carrier,
                    onValueChange = { carrier = it },
                    label = { Text("Carrier / provider") },
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
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Phone number (optional, you choose to record)") },
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
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
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
                if (labelError) {
                    Text("Label is required", style = MaterialTheme.typography.labelSmall, color = ErrorRed)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (label.isBlank()) {
                        labelError = true
                    } else {
                        onSave(label, carrier, phoneNumber, notes, initialSim)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PackageFormDialog(
    initialPackage: TelecomPackage?,
    onDismiss: () -> Unit,
    onSave: (name: String, carrier: String, category: String, priceMinor: Long, period: Int, notes: String, existing: TelecomPackage?) -> Unit
) {
    var name by remember { mutableStateOf(initialPackage?.name ?: "") }
    var carrier by remember { mutableStateOf(initialPackage?.carrier ?: "") }
    var category by remember { mutableStateOf(initialPackage?.category ?: "") }
    var priceText by remember {
        mutableStateOf(
            initialPackage?.let { Money.formatNpr(it.priceMinor).removePrefix("NPR ") } ?: ""
        )
    }
    var period by remember { mutableStateOf(initialPackage?.period ?: BILLING_PERIOD_MONTHLY) }
    var notes by remember { mutableStateOf(initialPackage?.notes ?: "") }
    var nameError by remember { mutableStateOf(false) }
    var priceError by remember { mutableStateOf(false) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceVariant,
        title = { Text(if (initialPackage == null) "Add Package" else "Edit Package", color = NeonCyan) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
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
                    value = carrier,
                    onValueChange = { carrier = it },
                    label = { Text("Carrier / provider") },
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
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category (data/voice/bundle)") },
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
                    value = priceText,
                    onValueChange = { priceText = it; priceError = false },
                    label = { Text("Price (NPR)") },
                    isError = priceError,
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
                        value = periodLabel(period),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Billing period") },
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
                        listOf(
                            BILLING_PERIOD_WEEKLY to "Weekly",
                            BILLING_PERIOD_MONTHLY to "Monthly",
                            BILLING_PERIOD_QUARTERLY to "Quarterly",
                            BILLING_PERIOD_YEARLY to "Yearly"
                        ).forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    period = value
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
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
                if (nameError) {
                    Text("Name is required", style = MaterialTheme.typography.labelSmall, color = ErrorRed)
                }
                if (priceError) {
                    Text("Enter a valid positive price", style = MaterialTheme.typography.labelSmall, color = ErrorRed)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val priceMinor = com.prasbin.shadowmoney.presentation.screen.budgets.parseNprToMinor(priceText) ?: 0L
                    if (name.isBlank()) nameError = true
                    if (priceText.isBlank() || priceMinor <= 0L) priceError = true
                    if (name.isNotBlank() && !priceError) {
                        onSave(name, carrier, category, priceMinor, period, notes, initialPackage)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubscriptionFormDialog(
    initialSubscription: TelecomSubscription?,
    sims: List<TelecomSim>,
    packages: List<TelecomPackage>,
    onDismiss: () -> Unit,
    onSave: (simId: Long, packageId: Long, startTimestamp: Long, renewalTimestamp: Long, monthlyCostMinor: Long, existing: TelecomSubscription?) -> Unit
) {
    var selectedSimId by remember { mutableStateOf(initialSubscription?.simId) }
    var selectedPackageId by remember { mutableStateOf(initialSubscription?.packageId) }
    var startDate by remember {
        mutableStateOf(
            initialSubscription?.startTimestamp?.let { telecomDateFormat.format(Date(it)) } ?: ""
        )
    }
    var renewalDate by remember {
        mutableStateOf(
            initialSubscription?.renewalTimestamp?.let { telecomDateFormat.format(Date(it)) } ?: ""
        )
    }
    var costText by remember {
        mutableStateOf(
            initialSubscription?.monthlyCostMinor?.let { Money.formatNpr(it).removePrefix("NPR ") } ?: ""
        )
    }
    var simError by remember { mutableStateOf(false) }
    var packageError by remember { mutableStateOf(false) }
    var dateError by remember { mutableStateOf(false) }
    var costError by remember { mutableStateOf(false) }
    var simDropdownExpanded by remember { mutableStateOf(false) }
    var packageDropdownExpanded by remember { mutableStateOf(false) }

    val selectedSimLabel = sims.firstOrNull { it.id == selectedSimId }?.label ?: "Select SIM"
    val selectedPackageName = packages.firstOrNull { it.id == selectedPackageId }?.name ?: "Select package"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceVariant,
        title = { Text(if (initialSubscription == null) "Add Subscription" else "Edit Subscription", color = NeonCyan) },
        text = {
            Column {
                ExposedDropdownMenuBox(
                    expanded = simDropdownExpanded,
                    onExpandedChange = { simDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedSimLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("SIM") },
                        isError = simError,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = simDropdownExpanded) },
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
                        expanded = simDropdownExpanded,
                        onDismissRequest = { simDropdownExpanded = false }
                    ) {
                        sims.forEach { sim ->
                            DropdownMenuItem(
                                text = { Text("${sim.label} (${sim.carrier})") },
                                onClick = {
                                    selectedSimId = sim.id
                                    simError = false
                                    simDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = packageDropdownExpanded,
                    onExpandedChange = { packageDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedPackageName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Package") },
                        isError = packageError,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = packageDropdownExpanded) },
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
                        expanded = packageDropdownExpanded,
                        onDismissRequest = { packageDropdownExpanded = false }
                    ) {
                        packages.filter { it.isActive }.forEach { pkg ->
                            DropdownMenuItem(
                                text = { Text("${pkg.name} (${Money.formatNpr(pkg.priceMinor)}/${periodLabel(pkg.period)})") },
                                onClick = {
                                    selectedPackageId = pkg.id
                                    packageError = false
                                    packageDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = startDate,
                    onValueChange = { startDate = it; dateError = false },
                    label = { Text("Start date (yyyy-MM-dd)") },
                    isError = dateError,
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
                    value = renewalDate,
                    onValueChange = { renewalDate = it; dateError = false },
                    label = { Text("Renewal date (yyyy-MM-dd)") },
                    isError = dateError,
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
                    value = costText,
                    onValueChange = { costText = it; costError = false },
                    label = { Text("Custom monthly cost (NPR, optional)") },
                    isError = costError,
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
                if (simError) {
                    Text("Select a SIM", style = MaterialTheme.typography.labelSmall, color = ErrorRed)
                }
                if (packageError) {
                    Text("Select a package", style = MaterialTheme.typography.labelSmall, color = ErrorRed)
                }
                if (dateError) {
                    Text("Enter valid dates (yyyy-MM-dd)", style = MaterialTheme.typography.labelSmall, color = ErrorRed)
                }
                if (costError) {
                    Text("Enter a valid non-negative cost or leave empty", style = MaterialTheme.typography.labelSmall, color = ErrorRed)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val startMillis = parseTelecomDate(startDate)
                    val renewalMillis = parseTelecomDate(renewalDate)
                    val costMinor = com.prasbin.shadowmoney.presentation.screen.budgets.parseNprToMinor(costText) ?: 0L
                    if (selectedSimId == null) simError = true
                    if (selectedPackageId == null) packageError = true
                    if (startDate.isBlank() || startMillis == null) dateError = true
                    if (renewalDate.isBlank() || renewalMillis == null) dateError = true
                    if (costText.isNotBlank() && costMinor < 0L) costError = true
                    if (selectedSimId != null && selectedPackageId != null && startMillis != null &&
                        renewalMillis != null && !costError
                    ) {
                        onSave(selectedSimId!!, selectedPackageId!!, startMillis, renewalMillis, costMinor, initialSubscription)
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

private fun parseTelecomDate(input: String): Long? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    return try {
        val date = java.time.LocalDate.parse(trimmed)
        date.atStartOfDay(com.prasbin.shadowmoney.data.BudgetCalendar.KATHMANDU_ZONE)
            .toInstant()
            .toEpochMilli()
    } catch (error: java.time.format.DateTimeParseException) {
        null
    }
}
