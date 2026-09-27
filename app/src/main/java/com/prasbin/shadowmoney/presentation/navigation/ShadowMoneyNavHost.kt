package com.prasbin.shadowmoney.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.prasbin.shadowmoney.presentation.screen.budgets.BudgetsScreen
import com.prasbin.shadowmoney.presentation.screen.dashboard.DashboardScreen
import com.prasbin.shadowmoney.presentation.screen.dashboard.MoneyPlaceholderScreen
import com.prasbin.shadowmoney.presentation.screen.jobs.JobsScreen
import com.prasbin.shadowmoney.presentation.screen.opportunities.OpportunitiesScreen
import com.prasbin.shadowmoney.presentation.screen.settings.SettingsScreen
import com.prasbin.shadowmoney.presentation.screen.transactions.TransactionsScreen
import com.prasbin.shadowmoney.presentation.screen.work.WorkScreen

@Composable
fun ShadowMoneyNavHost(
    modifier: Modifier = Modifier,
    navController: androidx.navigation.NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = modifier
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(navController = navController)
        }
        composable(Screen.Budgets.route) {
            BudgetsScreen()
        }
        composable(Screen.Transactions.route) {
            TransactionsScreen()
        }
        composable(Screen.Money.route) {
            MoneyPlaceholderScreen()
        }
        composable(Screen.Jobs.route) {
            JobsScreen()
        }
        composable(Screen.Work.route) {
            WorkScreen()
        }
        composable(Screen.Opportunities.route) {
            OpportunitiesScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}

object Screen {
    object Dashboard {
        const val route = "dashboard"
    }
    object Budgets {
        const val route = "budgets"
    }
    object Transactions {
        const val route = "transactions"
    }
    object Money {
        const val route = "money"
    }
    object Jobs {
        const val route = "jobs"
    }
    object Work {
        const val route = "work"
    }
    object Opportunities {
        const val route = "opportunities"
    }
    object Settings {
        const val route = "settings"
    }
}
