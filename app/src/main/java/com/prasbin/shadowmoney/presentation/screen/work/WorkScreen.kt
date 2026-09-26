package com.prasbin.shadowmoney.presentation.screen.work

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.prasbin.shadowmoney.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Work") },
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
                text = "Work — Not Implemented Yet",
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
