package com.prasbin.shadowmoney

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.prasbin.shadowmoney.presentation.navigation.ShadowMoneyNavHost
import com.prasbin.shadowmoney.presentation.navigation.SystemBottomBar
import com.prasbin.shadowmoney.presentation.theme.ShadowMoneyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShadowMoneyTheme {
                AppShell()
            }
        }
    }
}

@Composable
private fun AppShell() {
    val snackbarHostState = remember { SnackbarHostState() }
    androidx.navigation.compose.rememberNavController().let { navController ->
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = { SystemBottomBar(navController = navController) }
        ) { innerPadding ->
            ShadowMoneyNavHost(
                navController = navController,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
