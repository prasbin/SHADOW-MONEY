@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.prasbin.shadowmoney.presentation.screen.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prasbin.shadowmoney.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SHADOW MONEY") },
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
                    onClick = { },
                    icon = { Text("\$") },
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
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "SHADOW MONEY",
                style = MaterialTheme.typography.displayLarge,
                color = NeonCyan
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Personal Financial Management",
                style = MaterialTheme.typography.bodyLarge,
                color = DarkOnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            SystemCard(
                title = "Balance",
                value = "0",
                subtitle = "Local records only — not a bank balance"
            )
            Spacer(modifier = Modifier.height(8.dp))
            SystemPanel(title = "Phase 1 Foundation") {
                Text(
                    text = "Dashboard is a placeholder. Financial data will appear after Phase 2.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DarkOnSurfaceVariant
                )
            }
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
