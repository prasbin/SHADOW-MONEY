package com.prasbin.shadowmoney.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.prasbin.shadowmoney.presentation.screen.assistant.AssistantScreen
import com.prasbin.shadowmoney.presentation.screen.budgets.BudgetsScreen
import com.prasbin.shadowmoney.presentation.screen.connections.ConnectionsScreen
import com.prasbin.shadowmoney.presentation.screen.csvimport.ImportTransactionsScreen
import com.prasbin.shadowmoney.presentation.screen.dashboard.DashboardScreen
import com.prasbin.shadowmoney.presentation.screen.goals.GoalsScreen
import com.prasbin.shadowmoney.presentation.screen.money.MoneyScreen
import com.prasbin.shadowmoney.presentation.screen.opportunities.OpportunitiesScreen
import com.prasbin.shadowmoney.presentation.screen.settings.SettingsScreen
import com.prasbin.shadowmoney.presentation.screen.telecom.TelecomScreen
import com.prasbin.shadowmoney.presentation.screen.transactions.TransactionsScreen
import com.prasbin.shadowmoney.presentation.screen.work.WorkDetailScreen
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
        composable(Screen.Connections.route) {
            ConnectionsScreen()
        }
        composable(Screen.Goals.route) {
            GoalsScreen(navController = navController)
        }
        composable(Screen.Telecom.route) {
            TelecomScreen(navController = navController)
        }
        composable(Screen.Opportunities.route) {
            OpportunitiesScreen(navController = navController)
        }
        composable(
            route = Screen.Transactions.directionRoute,
            arguments = listOf(
                androidx.navigation.navArgument("direction") {
                    type = androidx.navigation.NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            TransactionsScreen(
                navController = navController,
                initialDirection = Screen.Transactions.normalizeDirection(
                    backStackEntry.arguments?.getString("direction")
                )
            )
        }
        composable(Screen.Money.route) {
            MoneyScreen(navController = navController)
        }
        composable(Screen.Import.route) {
            ImportTransactionsScreen(navController = navController)
        }
        composable(Screen.Work.route) {
            WorkScreen(navController = navController)
        }
        composable(Screen.WorkDetail.route) { backStackEntry ->
            val workItemId = backStackEntry.arguments?.getString("id")?.toLongOrNull() ?: 0L
            WorkDetailScreen(workItemId = workItemId, navController = navController)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
        composable(Screen.Assistant.route) {
            AssistantScreen(navController = navController)
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
    object Connections {
        const val route = "connections"
    }
    object Goals {
        const val route = "goals"
    }
    object Telecom {
        const val route = "telecom"
    }
    object Opportunities {
        const val route = "opportunities"
    }
    object Transactions {
        const val route = "transactions"
        const val directionRoute = "transactions?direction={direction}"
        fun inDirection() = "transactions?direction=in"
        fun outDirection() = "transactions?direction=out"
        fun normalizeDirection(raw: String?): String? =
            raw?.takeIf { it == "in" || it == "out" }
    }
    object Import {
        const val route = "import"
    }
    object Money {
        const val route = "money"
    }
    object Work {
        const val route = "work"
    }
    object WorkDetail {
        const val route = "work/{id}"
    }
    object Settings {
        const val route = "settings"
    }
    object Assistant {
        const val route = "assistant"
    }
}
