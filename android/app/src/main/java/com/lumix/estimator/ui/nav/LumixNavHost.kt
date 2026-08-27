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
import com.lumix.estimator.domain.RoofConstraint
import com.lumix.estimator.site.ManualSiteScreen
import com.lumix.estimator.site.SiteDetailScreen
import com.lumix.estimator.site.SiteSurveySummary
import com.lumix.estimator.site.SolarSiteEntryScreen
import com.lumix.estimator.site.SolarSiteViewModel
import com.lumix.estimator.site.map.SolarSiteMapScreen
import com.lumix.estimator.ui.components.FloatingBottomNav
import com.lumix.estimator.ui.components.NavTab
import com.lumix.estimator.ui.estimate.EstimateLandingScreen
import com.lumix.estimator.ui.history.HistoryScreen
import com.lumix.estimator.ui.home.HomeScreen
import com.lumix.estimator.ui.monitoring.DevicesScreen
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
// A149 (Deye integration round): pushed (not a bottom-nav tab), reached from Settings' "Device
// Monitoring" section — see DevicesScreen's own doc for why.
private const val ROUTE_DEVICES = "devices"
// A81 (Phase 18, restored): not bottom-nav tabs (see EstimateLandingScreen's own doc for why) —
// pushed routes reached from the Estimate tab's "Open Solar Site" entry instead.
private const val ROUTE_SITE = "site"
private const val ROUTE_SITE_MAP = "site/map"
private const val ROUTE_SITE_MANUAL = "site/manual"
private const val ROUTE_SITE_DETAIL = "site/detail/{siteId}"

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
    val siteViewModel: SolarSiteViewModel = viewModel(
        factory = SolarSiteViewModel.factory(app.siteRepository)
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
                    },
                    onOpenSite = { navController.navigate(ROUTE_SITE) }
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
                    onBack = { navController.popBackStack() },
                    onSwitchToManual = { navController.navigate(ROUTE_SITE_MANUAL) { popUpTo(ROUTE_SITE) } }
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
                    onUseRoof = { plane ->
                        val site = siteViewModel.getSite(siteId)
                        wizardViewModel.startWithRoofConstraint(
                            RoofConstraint(
                                sourceSiteId = siteId,
                                sourceRoofPlaneId = plane.id,
                                roofLabel = plane.label,
                                maxPanelCount = plane.panelLayout?.panelCount ?: 0,
                                panelWattage = plane.panelLayout?.panelWattage?.toInt() ?: 0,
                                azimuthDegrees = plane.azimuthDegrees ?: plane.suggestedAzimuthDegrees,
                                pitchDegrees = plane.pitchDegrees,
                                latitude = site?.latitude ?: 0.0,
                                longitude = site?.longitude ?: 0.0
                            ),
                            parish = site?.parish,
                            town = site?.town,
                            // Site Survey / Solar Mapping round (spec "produce a full site-survey
                            // summary for the final quote/report"): captured once here, from the
                            // real site as it stands right now — see SiteSurveySummary's own doc.
                            siteSurveySummary = site?.let { SiteSurveySummary.from(it) }
                        )
                        navController.navigate(ROUTE_WIZARD)
                    },
                    onBack = { navController.popBackStack() }
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
                    },
                    // A88 (spec Phase 26 §17): SITE_DETAILS flow's "Done" — return to the same
                    // quote's SystemResultScreen, mirroring how CREATE QUOTE re-enters the wizard
                    // then this same route handles the flow-mode switch.
                    onSiteDetailsDone = { id -> navController.navigate("system-result/$id") }
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
                    codeStandardRepository = app.codeStandardRepository,
                    onSimulate = { id -> navController.navigate("simulation/$id") },
                    onEditSystem = { navController.popBackStack() },
                    onCreateQuote = {
                        wizardViewModel.startQuoteDetails()
                        navController.popBackStack()
                    },
                    // A88: reveals the same still-live WIZARD (underneath on the backstack, per
                    // onSystemCalculated above) now showing Site Details — same pop-back-to-reveal
                    // pattern onEditSystem already uses.
                    onSiteDetails = {
                        wizardViewModel.openSiteDetails()
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
                    settingsRepository = app.settingsRepository,
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
                    quoteRepository = app.quoteRepository,
                    codeStandardRepository = app.codeStandardRepository,
                    onOpenDevices = { navController.navigate(ROUTE_DEVICES) }
                )
            }

            composable(ROUTE_DEVICES) {
                DevicesScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
