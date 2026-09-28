@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.prasbin.shadowmoney.presentation.screen.transactions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.prasbin.shadowmoney.presentation.navigation.Screen
import com.prasbin.shadowmoney.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(navController: NavHostController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transactions") },
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
                text = "Transactions — Not Implemented Yet",
                style = MaterialTheme.typography.headlineMedium,
                color = DarkOnSurfaceVariant
            )
            Text(
                text = "This feature will be available in Phase 2+",
                style = MaterialTheme.typography.bodyMedium,
                color = DarkOnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedButton(
                onClick = { navController.navigate(Screen.Import.route) },
                border = BorderStroke(1.dp, NeonCyan)
            ) {
                Text("Import Transactions (CSV)", color = NeonCyan)
            }
        }
    }
}
