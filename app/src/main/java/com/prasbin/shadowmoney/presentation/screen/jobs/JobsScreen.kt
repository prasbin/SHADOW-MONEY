@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.prasbin.shadowmoney.presentation.screen.jobs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prasbin.shadowmoney.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobsScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Jobs") },
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
                text = "Jobs — Not Implemented Yet",
                style = MaterialTheme.typography.headlineMedium,
                color = DarkOnSurfaceVariant
            )
            Text(
                text = "This feature will be available in Phase 6+",
                style = MaterialTheme.typography.bodyMedium,
                color = DarkOnSurfaceVariant
            )
        }
    }
}
