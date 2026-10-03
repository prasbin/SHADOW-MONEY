@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.prasbin.shadowmoney.presentation.screen.connections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prasbin.shadowmoney.data.Money
import com.prasbin.shadowmoney.data.ShadowMoneyDatabase
import com.prasbin.shadowmoney.data.connections.ConnectionRepository
import com.prasbin.shadowmoney.data.connections.ConnectionStatus
import com.prasbin.shadowmoney.data.connections.ConnectionSyncCoordinator
import com.prasbin.shadowmoney.data.connections.DiscrepancyState
import com.prasbin.shadowmoney.data.connections.Provenance
import com.prasbin.shadowmoney.data.connections.ProviderAvailability
import com.prasbin.shadowmoney.data.connections.label
import com.prasbin.shadowmoney.presentation.theme.DarkOnSurface
import com.prasbin.shadowmoney.presentation.theme.DarkOnSurfaceVariant
import com.prasbin.shadowmoney.presentation.theme.DarkSurface
import com.prasbin.shadowmoney.presentation.theme.ErrorRed
import com.prasbin.shadowmoney.presentation.theme.NeonCyan
import com.prasbin.shadowmoney.presentation.theme.NeonGreen
import com.prasbin.shadowmoney.presentation.theme.SystemChip
import com.prasbin.shadowmoney.presentation.theme.SystemPanel
import com.prasbin.shadowmoney.presentation.theme.SystemPrimaryButton
import com.prasbin.shadowmoney.presentation.theme.SystemSectionHeader
import com.prasbin.shadowmoney.presentation.theme.WarningAmber

/**
 * Real-money connections hub. Every provider card states the honest, research-backed
 * availability of official consumer interfaces — no fake "Connect" actions, no
 * fabricated balances. The actual-money view, baseline action, and discrepancy state
 * all come from the connection architecture and refuse honestly while no official
 * consumer data connection exists.
 */
@Composable
fun ConnectionsScreen() {
    val context = LocalContext.current
    val viewModel: ConnectionsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = ShadowMoneyDatabase.getInstance(context.applicationContext)
                val repository = ConnectionRepository(database.connectionDao())
                return ConnectionsViewModel(
                    repository = repository,
                    coordinator = ConnectionSyncCoordinator(repository = repository)
                ) as T
            }
        }
    )
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "CONNECTIONS",
                            color = NeonCyan,
                            style = MaterialTheme.typography.titleLarge,
                            letterSpacing = 2f.sp
                        )
                        Text(
                            "REAL-MONEY LINKS · HONEST STATUS",
                            color = DarkOnSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SystemSectionHeader("Providers")
            state.providers.forEach { availability ->
                ProviderCard(
                    availability = availability,
                    storedStatus = state.connections
                        .firstOrNull { it.provider == availability.provider }
                        ?.status
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            SystemSectionHeader("Source of truth")
            ActualMoneyPanel(state)
            Spacer(modifier = Modifier.height(8.dp))

            BaselinePanel(
                state = state,
                onSetBaseline = { viewModel.setBaselineFromConnectedSources() }
            )
            Spacer(modifier = Modifier.height(8.dp))

            DiscrepancyPanel(state)
            Spacer(modifier = Modifier.height(8.dp))

            SystemSectionHeader("Data provenance")
            ProvenancePanel()

            Text(
                text = "No credentials stored · no network permission · official integrations only. " +
                    "Manual and imported records keep their own provenance and are never merged " +
                    "into a connected total.",
                style = MaterialTheme.typography.labelSmall,
                color = DarkOnSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ProviderCard(
    availability: ProviderAvailability,
    storedStatus: ConnectionStatus?
) {
    val status = storedStatus ?: availability.status
    SystemPanel(title = availability.displayName) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SystemChip(
                text = statusLabel(status),
                color = statusColor(status)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        AvailabilityRow("Interface", availability.officialInterface)
        AvailabilityRow("Balance read", availability.balanceRead)
        AvailabilityRow("Transaction read", availability.transactionRead)
        AvailabilityRow("Payment initiate", availability.paymentInitiate)
        AvailabilityRow("Auth model", availability.authModel)
        AvailabilityRow("Approval required", availability.approvalRequired)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = availability.note,
            style = MaterialTheme.typography.labelSmall,
            color = WarningAmber,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AvailabilityRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = DarkOnSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = DarkOnSurface
        )
    }
}

@Composable
private fun ActualMoneyPanel(state: ConnectionsUiState) {
    SystemPanel(title = "Actual money") {
        val view = state.actualMoney
        if (view == null || view.connectedVerifiedTotalMinor == null) {
            Text(
                text = "NOT AVAILABLE",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = WarningAmber
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = view?.unavailableReason
                    ?: "No connected sources. Manual and imported records stay under their own provenance.",
                style = MaterialTheme.typography.bodySmall,
                color = DarkOnSurfaceVariant
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = Money.formatNpr(view.connectedVerifiedTotalMinor),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = NeonGreen
                )
                Spacer(modifier = Modifier.width(8.dp))
                SystemChip(
                    text = if (view.isFullyVerified) "VERIFIED" else "PARTIALLY VERIFIED",
                    color = if (view.isFullyVerified) NeonGreen else WarningAmber
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${view.freshSourceCount} fresh · ${view.staleSourceCount} stale · " +
                    "${view.unverifiedSourceCount} unverified of ${view.connectedSourceCount} connected",
                style = MaterialTheme.typography.labelSmall,
                color = DarkOnSurfaceVariant
            )
            view.unavailableReason?.let { reason ->
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = WarningAmber
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Connected verified data outranks everything else. Manual entries and " +
                "imported rows remain separately provenanced and are never folded into " +
                "this figure.",
            style = MaterialTheme.typography.labelSmall,
            color = DarkOnSurfaceVariant
        )
    }
}

@Composable
private fun BaselinePanel(
    state: ConnectionsUiState,
    onSetBaseline: () -> Unit
) {
    SystemPanel(title = "Baseline") {
        if (state.baselines.isEmpty()) {
            Text(
                text = "NO BASELINE",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = WarningAmber
            )
        } else {
            state.baselines.forEach { baseline ->
                Text(
                    text = "${baseline.provider.name} · ${Money.formatNpr(baseline.baselineMinor)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = NeonGreen
                )
                Text(
                    text = "${baseline.provenance.label()} · set ${baseline.setAtMs}",
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkOnSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        SystemPrimaryButton(
            text = "Set baseline from connected sources",
            onClick = onSetBaseline
        )
        state.baselineMessage?.let { message ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (state.baselines.isEmpty()) WarningAmber else NeonGreen
            )
        }
    }
}

@Composable
private fun DiscrepancyPanel(state: ConnectionsUiState) {
    SystemPanel(title = "Discrepancy") {
        val result = state.discrepancy
        if (result == null) {
            Text(
                text = "…",
                style = MaterialTheme.typography.titleMedium,
                color = DarkOnSurfaceVariant
            )
            return@SystemPanel
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SystemChip(
                text = result.state.name.replace('_', ' '),
                color = discrepancyColor(result.state)
            )
            result.deltaMinor?.let { delta ->
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Δ ${Money.formatNpr(delta)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = discrepancyColor(result.state)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = result.explanation,
            style = MaterialTheme.typography.bodySmall,
            color = DarkOnSurfaceVariant
        )
    }
}

@Composable
private fun ProvenancePanel() {
    SystemPanel(title = "Provenance tags") {
        ProvenanceRow(Provenance.CONNECTED_VERIFIED, "Read from an official connected source and verified.")
        ProvenanceRow(Provenance.IMPORTED, "Provided by you (CSV import or other user-provided file).")
        ProvenanceRow(Provenance.MANUAL_ENTRY, "Recorded by hand in the app.")
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Provenance is preserved per record. There is no merged pseudo-source: " +
                "manual plus connected never becomes a third stored \"actual\" figure.",
            style = MaterialTheme.typography.labelSmall,
            color = DarkOnSurfaceVariant
        )
    }
}

@Composable
private fun ProvenanceRow(provenance: Provenance, definition: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SystemChip(
            text = provenance.label(),
            color = when (provenance) {
                Provenance.CONNECTED_VERIFIED -> NeonGreen
                Provenance.IMPORTED -> NeonCyan
                Provenance.MANUAL_ENTRY -> DarkOnSurfaceVariant
            }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = definition,
            style = MaterialTheme.typography.bodySmall,
            color = DarkOnSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun statusLabel(status: ConnectionStatus): String = when (status) {
    ConnectionStatus.UNAVAILABLE -> "UNAVAILABLE"
    ConnectionStatus.DISCONNECTED -> "DISCONNECTED"
    ConnectionStatus.CONNECTING -> "CONNECTING"
    ConnectionStatus.CONNECTED -> "CONNECTED"
    ConnectionStatus.STALE -> "STALE"
    ConnectionStatus.ERROR -> "ERROR"
    ConnectionStatus.REAUTH_REQUIRED -> "REAUTH REQUIRED"
}

private fun statusColor(status: ConnectionStatus): androidx.compose.ui.graphics.Color = when (status) {
    ConnectionStatus.CONNECTED -> NeonGreen
    ConnectionStatus.STALE -> WarningAmber
    ConnectionStatus.ERROR -> ErrorRed
    ConnectionStatus.REAUTH_REQUIRED -> WarningAmber
    ConnectionStatus.UNAVAILABLE -> WarningAmber
    ConnectionStatus.CONNECTING -> NeonCyan
    ConnectionStatus.DISCONNECTED -> DarkOnSurfaceVariant
}

private fun discrepancyColor(state: DiscrepancyState): androidx.compose.ui.graphics.Color =
    when (state) {
        DiscrepancyState.EXPECTED_CHANGE -> NeonGreen
        DiscrepancyState.UNEXPLAINED_REDUCTION -> ErrorRed
        DiscrepancyState.ACTUAL_DISCREPANCY -> WarningAmber
        DiscrepancyState.CONNECTION_ERROR -> ErrorRed
        DiscrepancyState.REAUTH_REQUIRED -> WarningAmber
        DiscrepancyState.CONNECTION_STALE -> WarningAmber
        DiscrepancyState.NO_BASELINE -> NeonCyan
        DiscrepancyState.NO_CONNECTED_SOURCES -> DarkOnSurfaceVariant
    }
