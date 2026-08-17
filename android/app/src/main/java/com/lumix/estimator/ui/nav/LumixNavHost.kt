package com.lumix.estimator.ui.nav

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
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
import com.lumix.estimator.ui.results.SystemResultScreen
import com.lumix.estimator.ui.savings.SavingsScreen
import com.lumix.estimator.ui.settings.SettingsScreen
import com.lumix.estimator.ui.simulation.SimulationScreen
import com.lumix.estimator.ui.simulation.SimulationViewModel
import com.lumix.estimator.ui.wizard.WizardScreen
import com.lumix.estimator.ui.wizard.WizardViewModel

private const val ROUTE_HOME = "home"
private const val ROUTE_ESTIMATE = "estimate"
private const val ROUTE_WIZARD = "wizard"
private const val ROUTE_SYSTEM_RESULT = "system-result/{quoteId}"
private const val ROUTE_RESULTS = "results/{quoteId}"
private const val ROUTE_SIMULATION = "simulation/{quoteId}"
private const val ROUTE_SYSTEMS = "systems"
private const val ROUTE_SAVINGS = "savings"
private const val ROUTE_SETTINGS = "settings"

private val tabs = listOf(
    NavTab(ROUTE_HOME, "Home", Icons.Default.Home),
    NavTab(ROUTE_ESTIMATE, "Estimate", Icons.Default.WbSunny),
    NavTab(ROUTE_SYSTEMS, "Systems", Icons.Default.Layers),
    NavTab(ROUTE_SAVINGS, "Savings", Icons.Default.TrendingUp),
    NavTab(ROUTE_SETTINGS, "Settings", Icons.Default.Settings)
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

    // contentWindowInsets = WindowInsets(0): this outer, app-wide Scaffold must NOT consume any
    // system insets itself. Material3's Scaffold consumes contentWindowInsets on behalf of its
    // content regardless of whether bottomBar is populated for the current route — with the
    // default (WindowInsets.safeDrawing), that silently zeroed out the navigation-bar inset for
    // every screen further down the tree (including each pushed screen's own inner Scaffold),
    // which is why adding navigationBarsPadding() inside those inner screens alone didn't stop
    // buttons clipping under the nav bar on a real device. Insets now pass through untouched;
    // FloatingBottomNav below and each individual screen apply their own, for real.
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
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
                    settingsRepository = app.settingsRepository,
                    onBackToHome = { navController.popBackStack(ROUTE_HOME, inclusive = false) },
                    // A56: DESIGN flow finishing ("Calculate System") lands on the lightweight
                    // engineering-only SystemResultScreen — WIZARD stays on the back stack so
                    // "Edit System" there is just a pop, not a fresh navigate.
                    onSystemCalculated = { id -> navController.navigate("system-result/$id") },
                    // QUOTE_DETAILS flow finishing ("Save Quote") is the real end of this whole
                    // estimate — clears WIZARD/SYSTEM_RESULT off the stack same as before.
                    onQuoteSaved = { id ->
                        navController.navigate("results/$id") {
                            popUpTo(ROUTE_HOME)
                        }
                    }
                )
            }

            composable(
                ROUTE_SYSTEM_RESULT,
                arguments = listOf(navArgument("quoteId") { type = NavType.LongType })
            ) { entry ->
                val quoteId = entry.arguments?.getLong("quoteId") ?: 0L
                SystemResultScreen(
                    quoteId = quoteId,
                    quoteRepository = app.quoteRepository,
                    onSimulate = { id -> navController.navigate("simulation/$id") },
                    onEditSystem = { navController.popBackStack() },
                    onCreateQuote = {
                        wizardViewModel.startQuoteDetails()
                        navController.popBackStack()
                    },
                    onBackToHome = { navController.popBackStack(ROUTE_HOME, inclusive = false) }
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
                    onBackToHome = { navController.popBackStack(ROUTE_HOME, inclusive = false) },
                    onSimulate = { id -> navController.navigate("simulation/$id") },
                    onEditSystem = { saved ->
                        wizardViewModel.loadForEdit(saved)
                        navController.navigate(ROUTE_WIZARD)
                    }
                )
            }

            composable(
                ROUTE_SIMULATION,
                arguments = listOf(navArgument("quoteId") { type = NavType.LongType })
            ) { entry ->
                val quoteId = entry.arguments?.getLong("quoteId") ?: 0L
                val simulationViewModel: SimulationViewModel = viewModel(
                    factory = SimulationViewModel.factory(app.quoteRepository, app.settingsRepository)
                )
                SimulationScreen(
                    quoteId = quoteId,
                    viewModel = simulationViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(ROUTE_SYSTEMS) {
                HistoryScreen(
                    quoteRepository = app.quoteRepository,
                    onOpenQuote = { id -> navController.navigate("results/$id") },
                    onSimulate = { id -> navController.navigate("simulation/$id") },
                    onStartQuote = {
                        wizardViewModel.reset()
                        navController.navigate(ROUTE_WIZARD)
                    }
                )
            }

            composable(ROUTE_SAVINGS) {
                SavingsScreen(
                    quoteRepository = app.quoteRepository,
                    settingsRepository = app.settingsRepository,
                    onStartQuote = {
                        wizardViewModel.reset()
                        navController.navigate(ROUTE_WIZARD)
                    }
                )
            }

            composable(ROUTE_SETTINGS) {
                SettingsScreen(
                    priceRepository = app.priceRepository,
                    settingsRepository = app.settingsRepository,
                    quoteRepository = app.quoteRepository
                )
            }
        }
    }
}
