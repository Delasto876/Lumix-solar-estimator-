package com.lumix.estimator.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
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
import com.lumix.estimator.ui.simulation.SimulationScreen
import com.lumix.estimator.ui.simulation.SimulationViewModel
import com.lumix.estimator.ui.wizard.WizardScreen
import com.lumix.estimator.ui.wizard.WizardViewModel
import com.lumix.estimator.site.ManualSiteScreen
import com.lumix.estimator.site.SiteDetailScreen
import com.lumix.estimator.site.SolarSiteEntryScreen
import com.lumix.estimator.site.SolarSiteViewModel
import com.lumix.estimator.site.map.SolarSiteMapScreen

private const val ROUTE_HOME = "home"
private const val ROUTE_ESTIMATE = "estimate"
private const val ROUTE_WIZARD = "wizard"
private const val ROUTE_RESULTS = "results/{quoteId}"
private const val ROUTE_SIMULATION = "simulation/{quoteId}"
private const val ROUTE_SYSTEMS = "systems"
private const val ROUTE_SAVINGS = "savings"
private const val ROUTE_PROFILE = "profile"
private const val ROUTE_SITE = "site"
private const val ROUTE_SITE_MAP = "site/map"
private const val ROUTE_SITE_MANUAL = "site/manual"
private const val ROUTE_SITE_DETAIL = "site/detail/{siteId}"

private val tabs = listOf(
    NavTab(ROUTE_HOME, "Home", Icons.Default.Home),
    NavTab(ROUTE_ESTIMATE, "Estimate", Icons.Default.WbSunny),
    NavTab(ROUTE_SITE, "Site", Icons.Default.Map),
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
    val siteViewModel: SolarSiteViewModel = viewModel(
        factory = SolarSiteViewModel.factory(app.siteRepository)
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
                    onBackToHome = { navController.popBackStack(ROUTE_HOME, inclusive = false) },
                    onSimulate = { id -> navController.navigate("simulation/$id") }
                )
            }

            composable(
                ROUTE_SIMULATION,
                arguments = listOf(navArgument("quoteId") { type = NavType.LongType })
            ) { entry ->
                val quoteId = entry.arguments?.getLong("quoteId") ?: 0L
                val simulationViewModel: SimulationViewModel = viewModel(
                    factory = SimulationViewModel.factory(app.quoteRepository)
                )
                SimulationScreen(
                    quoteId = quoteId,
                    viewModel = simulationViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(ROUTE_SITE) {
                SolarSiteEntryScreen(
                    viewModel = siteViewModel,
                    onOpenMap = {
                        siteViewModel.startNewSite()
                        navController.navigate(ROUTE_SITE_MAP)
                    },
                    onOpenManual = {
                        siteViewModel.startNewSite()
                        navController.navigate(ROUTE_SITE_MANUAL)
                    },
                    onOpenSite = { id -> navController.navigate("site/detail/$id") }
                )
            }

            composable(ROUTE_SITE_MAP) {
                SolarSiteMapScreen(
                    viewModel = siteViewModel,
                    onSaved = { id -> navController.navigate("site/detail/$id") { popUpTo(ROUTE_SITE) } },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(ROUTE_SITE_MANUAL) {
                ManualSiteScreen(
                    viewModel = siteViewModel,
                    onSaved = { id -> navController.navigate("site/detail/$id") { popUpTo(ROUTE_SITE) } },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                ROUTE_SITE_DETAIL,
                arguments = listOf(navArgument("siteId") { type = NavType.StringType })
            ) { entry ->
                val siteId = entry.arguments?.getString("siteId") ?: ""
                SiteDetailScreen(
                    viewModel = siteViewModel,
                    siteId = siteId,
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
