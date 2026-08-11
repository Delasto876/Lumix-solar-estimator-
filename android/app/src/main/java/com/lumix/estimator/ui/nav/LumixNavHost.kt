package com.lumix.estimator.ui.nav

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lumix.estimator.LumixApp
import com.lumix.estimator.ui.history.HistoryScreen
import com.lumix.estimator.ui.home.HomeScreen
import com.lumix.estimator.ui.results.ResultsScreen
import com.lumix.estimator.ui.settings.PriceSettingsScreen
import com.lumix.estimator.ui.wizard.WizardScreen
import com.lumix.estimator.ui.wizard.WizardViewModel

private const val ROUTE_HOME = "home"
private const val ROUTE_WIZARD = "wizard"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_RESULTS = "results/{quoteId}"

@Composable
fun LumixNavHost(app: LumixApp) {
    val navController = rememberNavController()
    val wizardViewModel: WizardViewModel = viewModel(
        factory = WizardViewModel.factory(app.quoteRepository, app.priceRepository)
    )

    NavHost(navController = navController, startDestination = ROUTE_HOME) {
        composable(ROUTE_HOME) {
            HomeScreen(
                onStartQuote = {
                    wizardViewModel.reset()
                    navController.navigate(ROUTE_WIZARD)
                },
                onHistory = { navController.navigate(ROUTE_HISTORY) },
                onSettings = { navController.navigate(ROUTE_SETTINGS) }
            )
        }

        composable(ROUTE_WIZARD) {
            WizardScreen(
                viewModel = wizardViewModel,
                onBackToHome = { navController.popBackStack(ROUTE_HOME, inclusive = false) },
                onResults = { id ->
                    navController.navigate("results/$id") {
                        popUpTo(ROUTE_HOME)
                    }
                }
            )
        }

        composable(
            ROUTE_RESULTS,
            arguments = listOf(navArgument("quoteId") { type = NavType.LongType })
        ) { backStackEntry ->
            val quoteId = backStackEntry.arguments?.getLong("quoteId") ?: 0L
            ResultsScreen(
                quoteId = quoteId,
                quoteRepository = app.quoteRepository,
                onNewQuote = {
                    wizardViewModel.reset()
                    navController.navigate(ROUTE_WIZARD) {
                        popUpTo(ROUTE_HOME)
                    }
                },
                onBackToHome = { navController.popBackStack(ROUTE_HOME, inclusive = false) }
            )
        }

        composable(ROUTE_HISTORY) {
            HistoryScreen(
                quoteRepository = app.quoteRepository,
                onOpenQuote = { id ->
                    navController.navigate("results/$id")
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_SETTINGS) {
            PriceSettingsScreen(
                priceRepository = app.priceRepository,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
