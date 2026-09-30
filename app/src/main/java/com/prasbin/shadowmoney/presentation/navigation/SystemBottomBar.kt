package com.prasbin.shadowmoney.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.prasbin.shadowmoney.presentation.theme.DarkOnSurfaceVariant
import com.prasbin.shadowmoney.presentation.theme.DarkSurface
import com.prasbin.shadowmoney.presentation.theme.NeonCyan

private data class PrimaryTab(
    val label: String,
    val route: String,
    val icon: ImageVector
)

private val primaryTabs = listOf(
    PrimaryTab("Home", Screen.Dashboard.route, Icons.Filled.Dashboard),
    PrimaryTab("Money", Screen.Money.route, Icons.Filled.AccountBalanceWallet),
    PrimaryTab("Activity", Screen.Transactions.route, Icons.Filled.Receipt),
    PrimaryTab("Work", Screen.Work.route, Icons.Filled.Work),
    PrimaryTab("System", Screen.Settings.route, Icons.Filled.Settings)
)

/**
 * Five primary destinations: HOME, MONEY, ACTIVITY, WORK, SYSTEM.
 * Secondary screens (import, work detail) highlight their parent tab.
 */
@Composable
fun SystemBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationBar(
        containerColor = DarkSurface,
        tonalElevation = 0.dp
    ) {
        primaryTabs.forEach { tab ->
            val selected = when (tab.route) {
                Screen.Transactions.route ->
                    currentRoute == Screen.Transactions.route ||
                        currentRoute == Screen.Transactions.directionRoute ||
                        currentRoute == Screen.Import.route
                Screen.Work.route ->
                    currentRoute?.startsWith(Screen.Work.route) == true
                else -> currentRoute == tab.route
            }
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (currentRoute != tab.route) {
                        navController.navigate(tab.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                        }
                    }
                },
                icon = {
                    Icon(tab.icon, contentDescription = tab.label)
                },
                label = {
                    Text(
                        text = tab.label,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = NeonCyan,
                    selectedTextColor = NeonCyan,
                    unselectedIconColor = DarkOnSurfaceVariant,
                    unselectedTextColor = DarkOnSurfaceVariant,
                    indicatorColor = NeonCyan.copy(alpha = 0.10f)
                )
            )
        }
    }
}
