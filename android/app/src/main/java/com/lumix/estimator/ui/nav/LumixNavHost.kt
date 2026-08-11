package com.lumix.estimator.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lumix.estimator.LumixApp
import com.lumix.estimator.ui.components.FloatingBottomNav
import com.lumix.estimator.ui.components.NavTab
import com.lumix.estimator.ui.estimate.EstimateLandingScreen
import com.lumix.estimator.ui.history.HistoryScreen
import com.lumix.estimator.ui.home.HomeScreen
import com.lumix.estimator.ui.results.ResultsScreen
import com.lumix.estimator.ui.savings.SavingsScreen
import com.lumix.estimator.ui.settings.PriceSettingsScreen
import com.lumix.estimator.ui.wizard.WizardScreen
import com.lumix.estimator.ui.wizard.WizardViewModel

private const val ROUTE_HOME = "home"
private const val ROUTE_ESTIMATE = "estimate"
private const val ROUTE_WIZARD = "wizard"
private const val ROUTE_RESULTS = "results/{quoteId}"
private const val ROUTE_SYSTEMS = "systems"
private const val ROUTE_SAVINGS = "savings"
private const val ROUTE_PROFILE = "profile"

private val tabs = listOf(
    NavTab(ROUTE_HOME, "Home", Icons.Default.Home),
    NavTab(ROUTE_ESTIMATE, "Estimate", Icons.Default.WbSunny),
    NavTab(ROUTE_SYSTEMS, "Systems", Icons.Default.Layers),
    NavTab(ROUTE_SAVINGS, "Savings", Icons.Default.TrendingUp),
    NavTab(ROUTE_PROFILE, "Profile", Icons.Default.Person)
)
private val tabRoutes = tabs.map { it.route }.toSet()

@Composable
fun LumixNavHost(app: LumixApp) {
    val navController = rememberNavController()
    val wizardViewModel: WizardViewModel = viewModel(
        factory = WizardViewModel.factory(app.quoteRepository, app.priceRepository)
    )

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTabRoute = currentRoute != null && tabRoutes.contains(currentRoute)

    Scaffold(
        bottomBar = {
            if (isTabRoute) {
                FloatingBottomNav(
                    tabs = tabs,
                    selectedRoute = currentRoute ?: ROUTE_HOME,
                    onSelect = { tab ->
                        if (tab.route != currentRoute) {
                            navController.navigate(tab.route) {
                                popUpTo(ROUTE_HOME) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
        }
    ) { scaffoldPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_HOME,
            modifier = Modifier.padding(bottom = if (isTabRoute) scaffoldPadding.calculateBottomPadding() else 0.dp)
        ) {
            composable(ROUTE_HOME) {
                HomeScreen(
                    quoteRepository = app.quoteRepository,
                    onStartQuote = {
                        wizardViewModel.reset()
                        navController.navigate(ROUTE_WIZARD)
                    },
                    onOpenQuote = { id -> navController.navigate("results/$id") }
                )
            }

            composable(ROUTE_ESTIMATE) {
                EstimateLandingScreen(
                    onStartQuote = {
                        wizardViewModel.reset()
                        navController.navigate(ROUTE_WIZARD)
                    }
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
            ) { entry ->
                val quoteId = entry.arguments?.getLong("quoteId") ?: 0L
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

            composable(ROUTE_SYSTEMS) {
                HistoryScreen(
                    quoteRepository = app.quoteRepository,
                    onOpenQuote = { id -> navController.navigate("results/$id") },
                    onStartQuote = {
                        wizardViewModel.reset()
                        navController.navigate(ROUTE_WIZARD)
                    }
                )
            }

            composable(ROUTE_SAVINGS) {
                SavingsScreen(
                    quoteRepository = app.quoteRepository,
                    onStartQuote = {
                        wizardViewModel.reset()
                        navController.navigate(ROUTE_WIZARD)
                    }
                )
            }

            composable(ROUTE_PROFILE) {
                PriceSettingsScreen(priceRepository = app.priceRepository)
            }
        }
    }
}
