package com.lumix.estimator.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lumix.estimator.auth.GoogleIdentityConfig
import com.lumix.estimator.auth.GoogleSignInManager
import com.lumix.estimator.auth.GoogleSignInResult
import com.lumix.estimator.data.CodeStandardRepository
import com.lumix.estimator.data.PriceRepository
import com.lumix.estimator.data.QuoteRepository
import com.lumix.estimator.data.SettingsRepository
import com.lumix.estimator.data.ThemeMode
import com.lumix.estimator.domain.CodeRequirementReference
import com.lumix.estimator.domain.CodeStandard
import com.lumix.estimator.domain.ai.AiConfig
import com.lumix.estimator.domain.mcp.McpConfig
import com.lumix.estimator.domain.monitoring.MonitoringConfig
import com.lumix.estimator.domain.monitoring.MonitoringCredentials
import com.lumix.estimator.domain.monitoring.MonitoringManufacturer
import com.lumix.estimator.domain.monitoring.MonitoringProviderRegistry
import com.lumix.estimator.domain.monitoring.deye.DeyeAuthClient
import com.lumix.estimator.domain.monitoring.deye.DeyeAuthResult
import com.lumix.estimator.domain.PriceFields
import com.lumix.estimator.domain.PriceList
import com.lumix.estimator.domain.SavingsCalculator
import com.lumix.estimator.domain.SystemDiagnostics
import com.lumix.estimator.domain.simulation.SimulationEngine
import com.lumix.estimator.ui.components.CollapsibleGroup
import com.lumix.estimator.ui.components.CollapsibleSectionCard
import com.lumix.estimator.ui.components.LabeledDropdown
import com.lumix.estimator.ui.components.LargeTitleTopBar
import com.lumix.estimator.ui.components.LumixPrimaryButton
import com.lumix.estimator.ui.components.LumixSecondaryButton
import com.lumix.estimator.ui.components.NullableNumberField
import com.lumix.estimator.ui.components.NumberField
import com.lumix.estimator.ui.theme.LocalLumixPalette
import com.lumix.estimator.ui.theme.LumixRadius
import kotlinx.coroutines.launch

/**
 * The Settings tab: everything about the app itself rather than any one quote — appearance,
 * simulation defaults, the price list every quote is calculated from, and data management.
 * Replaces the old Profile tab, which was really just the price editor under a different name.
 *
 * A61 (spec §10/12 — "collapsible/tabbed Settings... separate Materials section"): every
 * top-level section is now a [CollapsibleSectionCard] instead of an always-open flat scroll, and
 * "Materials & Pricing" (formerly a bare "Price list" header directly above ~60 ungrouped price
 * fields) is now its own clearly separated section whose ~9 material categories
 * (`PriceFields.groups`) are each an independently collapsible [CollapsibleGroup]. Appearance
 * starts expanded (the one section most people actually open Settings for); everything else
 * starts collapsed so the tab reads as a short list of topics rather than one long form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    priceRepository: PriceRepository,
    settingsRepository: SettingsRepository,
    quoteRepository: QuoteRepository,
    codeStandardRepository: CodeStandardRepository,
    /** A149 (Deye integration round): opens the new Devices screen — null hides the "View live devices" entry point entirely (this screen stays usable without it). */
    onOpenDevices: (() -> Unit)? = null
) {
    val palette = LocalLumixPalette.current
    val scope = rememberCoroutineScope()

    val themeMode by settingsRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val defaultTechnicalMode by settingsRepository.defaultTechnicalMode.collectAsState(initial = false)
    val defaultGridServiceAmps by settingsRepository.defaultGridServiceAmps.collectAsState(initial = SimulationEngine.DEFAULT_GRID_SERVICE_AMPS)
    val billEscalationRate by settingsRepository.billEscalationRate.collectAsState(initial = SavingsCalculator.BILL_ESCALATION_RATE)
    val panelDegradationRate by settingsRepository.panelDegradationRate.collectAsState(initial = SavingsCalculator.PANEL_DEGRADATION_RATE)
    val billEscalationRatePercent = billEscalationRate * 100.0
    val panelDegradationRatePercent = panelDegradationRate * 100.0

    val currentPrices by priceRepository.prices.collectAsState(initial = PriceList.DEFAULT)

    val companyName by settingsRepository.companyName.collectAsState(initial = "")
    val companyAddress by settingsRepository.companyAddress.collectAsState(initial = "")
    val companyPhone by settingsRepository.companyPhone.collectAsState(initial = "")
    val companyEmail by settingsRepository.companyEmail.collectAsState(initial = "")
    val defaultWarranty by settingsRepository.defaultWarranty.collectAsState(initial = "")
    val paymentTerms by settingsRepository.paymentTerms.collectAsState(initial = "")

    // 2026-08-19 ("do this google sign in/OAuth" — confirmed scope: identity-capture only): see
    // GoogleSignInManager's own doc. context here is the Activity Compose hosts this screen in
    // (Credential Manager needs one to show the account picker) — NOT downgraded to
    // applicationContext the way e.g. DeviceLocationManager is, since that one needs no UI.
    val context = LocalContext.current
    val googleSignInManager = remember(context) { GoogleSignInManager(context) }

    // A149 (Deye integration round): "Connected" is defined by a persisted access token existing —
    // matches MonitoringProviderRegistry's own real/mock decision (see RealDeyeProvider's own doc).
    val deyeEmail by settingsRepository.deyeEmail.collectAsState(initial = "")
    val deyeAccessToken by settingsRepository.deyeAccessToken.collectAsState(initial = "")
    val deyeConnected = deyeAccessToken.isNotBlank()
    var deyeAppIdInput by remember { mutableStateOf("") }
    var deyeAppSecretInput by remember { mutableStateOf("") }
    var deyeEmailInput by remember { mutableStateOf("") }
    var deyeCompanyIdInput by remember { mutableStateOf("0") }
    var deyePasswordInput by remember { mutableStateOf("") }
    var deyeConnecting by remember { mutableStateOf(false) }
    var deyeConnectError by remember { mutableStateOf<String?>(null) }
    val googleSignedInName by settingsRepository.googleSignedInName.collectAsState(initial = "")
    val googleSignedInEmail by settingsRepository.googleSignedInEmail.collectAsState(initial = "")
    var googleSignInInProgress by remember { mutableStateOf(false) }
    var googleSignInError by remember { mutableStateOf<String?>(null) }

    val codeStandards by codeStandardRepository.standards.collectAsState(initial = emptyList())
    val codeReferences by codeStandardRepository.references.collectAsState(initial = emptyList())

    var showClearHistoryConfirm by remember { mutableStateOf(false) }

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(modifier = Modifier.fillMaxSize()) {
        LargeTitleTopBar(title = "Settings", subtitle = "Appearance, defaults, pricing, and data")

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp + navBarBottom),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                CollapsibleSectionCard(title = "Appearance", initiallyExpanded = true) {
                    SettingsRow(icon = Icons.Default.Contrast, title = "Theme", subtitle = "Follows the system unless overridden") {}
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = themeMode == mode,
                                onClick = { scope.launch { settingsRepository.setThemeMode(mode) } },
                                shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                                icon = {
                                    Icon(
                                        when (mode) {
                                            ThemeMode.SYSTEM -> Icons.Default.Contrast
                                            ThemeMode.LIGHT -> Icons.Default.LightMode
                                            ThemeMode.DARK -> Icons.Default.DarkMode
                                        },
                                        contentDescription = null
                                    )
                                }
                            ) {
                                Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                            }
                        }
                    }
                }
            }

            item {
                CollapsibleSectionCard(title = "Simulation defaults") {
                    SettingsRow(
                        icon = Icons.Default.Speed,
                        title = "Technical mode by default",
                        subtitle = "Open the digital twin with full technical readouts showing",
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Switch(
                            checked = defaultTechnicalMode,
                            onCheckedChange = { scope.launch { settingsRepository.setDefaultTechnicalMode(it) } },
                            colors = SwitchDefaults.colors(checkedTrackColor = palette.solarYellow)
                        )
                    }
                    NumberField(
                        label = "Default grid service",
                        value = defaultGridServiceAmps,
                        onValueChange = { v -> scope.launch { settingsRepository.setDefaultGridServiceAmps(v.coerceIn(10.0, 200.0)) } },
                        allowDecimal = false,
                        suffix = "A",
                        supportingText = "The main-breaker rating new simulations start with — matches a typical Jamaican residential service unless changed."
                    )
                }
            }

            item {
                CollapsibleSectionCard(title = "Financial assumptions") {
                    Text(
                        "ESTIMATE — these two rates drive the 20-year savings projection on the Savings tab. They're assumptions, not measured or guaranteed figures.",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary
                    )
                    NumberField(
                        label = "Annual bill escalation",
                        value = billEscalationRatePercent,
                        onValueChange = { v -> scope.launch { settingsRepository.setBillEscalationRate((v / 100.0).coerceIn(0.0, 0.30)) } },
                        suffix = "%/yr",
                        supportingText = "How much JPS-style electricity bills are assumed to climb each year."
                    )
                    NumberField(
                        label = "Annual panel degradation",
                        value = panelDegradationRatePercent,
                        onValueChange = { v -> scope.launch { settingsRepository.setPanelDegradationRate((v / 100.0).coerceIn(0.0, 5.0)) } },
                        suffix = "%/yr",
                        supportingText = "How much panel output is assumed to decline each year as they age."
                    )
                }
            }

            item {
                // 2026-08-19 ("do this google sign in/OAuth" — confirmed scope: identity-capture
                // only, nothing gated, no backend): a Sign in with Google button that fills the
                // Business Information section's Company name/Email below the FIRST time it
                // succeeds — never overwrites what the installer already typed by hand.
                CollapsibleSectionCard(title = "Google Account", subtitle = "Optional — prefills the business info below") {
                    if (!GoogleIdentityConfig.isConfigured) {
                        Text(
                            "Google Sign-In isn't set up yet. Add GOOGLE_WEB_CLIENT_ID to android/local.properties (a \"Web application\" type OAuth client ID from Google Cloud Console — see the README) to enable this.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                    } else if (googleSignedInEmail.isNotBlank()) {
                        SettingsRow(icon = Icons.Default.AccountCircle, title = googleSignedInName.ifBlank { "Signed in" }, subtitle = googleSignedInEmail) {}
                        LumixSecondaryButton(
                            text = "Sign out",
                            onClick = {
                                scope.launch {
                                    googleSignInManager.signOut()
                                    settingsRepository.clearGoogleSignedInIdentity()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LumixPrimaryButton(
                            text = if (googleSignInInProgress) "Signing in…" else "Sign in with Google",
                            onClick = {
                                googleSignInError = null
                                googleSignInInProgress = true
                                scope.launch {
                                    when (val result = googleSignInManager.signIn()) {
                                        is GoogleSignInResult.Success -> {
                                            val user = result.user
                                            settingsRepository.setGoogleSignedInIdentity(
                                                user.displayName.orEmpty(), user.email.orEmpty(), user.photoUrl.orEmpty()
                                            )
                                            if (companyName.isBlank() && !user.displayName.isNullOrBlank()) {
                                                settingsRepository.setCompanyName(user.displayName)
                                            }
                                            if (companyEmail.isBlank() && !user.email.isNullOrBlank()) {
                                                settingsRepository.setCompanyEmail(user.email)
                                            }
                                        }
                                        GoogleSignInResult.Cancelled -> {}
                                        is GoogleSignInResult.Failed -> googleSignInError = result.message
                                    }
                                    googleSignInInProgress = false
                                }
                            },
                            enabled = !googleSignInInProgress,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (googleSignInError != null) {
                            Text(
                                googleSignInError.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.warningRedText,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }

            item {
                // A79 (spec Phase 16, §40 "Company information / Address / Phone / Email /
                // Default warranty / Payment terms"): none of this existed anywhere before this
                // round — every field starts blank (see SettingsRepository's own doc for why
                // nothing is pre-filled). Once the installer enters their own real details here,
                // the quote PDF/HTML/CSV exports pick them up automatically (see ResultsScreen.kt).
                CollapsibleSectionCard(
                    title = "Business Information",
                    subtitle = "Shown on quote PDF/HTML/CSV exports once filled in"
                ) {
                    OutlinedTextField(
                        value = companyName,
                        onValueChange = { v -> scope.launch { settingsRepository.setCompanyName(v) } },
                        label = { Text("Company name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = companyAddress,
                        onValueChange = { v -> scope.launch { settingsRepository.setCompanyAddress(v) } },
                        label = { Text("Address") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = companyPhone,
                        onValueChange = { v -> scope.launch { settingsRepository.setCompanyPhone(v) } },
                        label = { Text("Phone") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = companyEmail,
                        onValueChange = { v -> scope.launch { settingsRepository.setCompanyEmail(v) } },
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = defaultWarranty,
                        onValueChange = { v -> scope.launch { settingsRepository.setDefaultWarranty(v) } },
                        label = { Text("Default warranty") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = paymentTerms,
                        onValueChange = { v -> scope.launch { settingsRepository.setPaymentTerms(v) } },
                        label = { Text("Payment terms") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                // A61 (spec §10/12): Materials & Pricing is now its own section, separate from
                // Appearance/Simulation/Financial rather than sharing the flat scroll they used to
                // all live in — and its ~60 fields are grouped into per-category collapsibles
                // (PriceFields.groups) instead of one long ungrouped list.
                CollapsibleSectionCard(
                    title = "Materials & Pricing",
                    subtitle = "${PriceFields.all.size} priced items across ${PriceFields.groups.size} categories"
                ) {
                    Text(
                        // A57 (spec §11): one price list — a discount is applied per-quote
                        // (percent or fixed) on top of these prices, not by swapping to a second set.
                        "These prices feed every quote across the app. Apply a discount per-quote from the Pricing step.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary
                    )
                    PriceFields.groups.forEach { group ->
                        CollapsibleGroup(title = group) {
                            PriceFields.all.filter { it.group == group }.forEach { field ->
                                NumberField(
                                    label = field.label,
                                    value = field.get(currentPrices),
                                    onValueChange = { v ->
                                        val updated = field.set(currentPrices, v)
                                        scope.launch { priceRepository.update(updated) }
                                    },
                                    suffix = field.suffix
                                )
                            }
                        }
                    }
                    LumixSecondaryButton(
                        text = "Reset prices to default",
                        onClick = { scope.launch { priceRepository.resetToDefault() } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                // 2026-08-18 ("for any inverter or component that doesn't have a price leave
                // blank with option to edit and enter price"): a separate section from Materials
                // & Pricing above — these fields genuinely have no real price yet (see PriceList's
                // own doc), so they render blank rather than a guessed placeholder number, and a
                // quote using one is flagged (QuoteResult.missingPriceItems) until entered here.
                CollapsibleSectionCard(
                    title = "Not Yet Priced",
                    subtitle = "${PriceFields.nullableAll.size} items with no price entered yet"
                ) {
                    Text(
                        "These have no real price yet — quotes using one of these are flagged as unable to finalize until you enter a price here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary
                    )
                    PriceFields.nullableGroups.forEach { group ->
                        CollapsibleGroup(title = group) {
                            PriceFields.nullableAll.filter { it.group == group }.forEach { field ->
                                NullableNumberField(
                                    label = field.label,
                                    value = field.get(currentPrices),
                                    onValueChange = { v ->
                                        val updated = field.set(currentPrices, v)
                                        scope.launch { priceRepository.update(updated) }
                                    },
                                    suffix = field.suffix
                                )
                            }
                        }
                    }
                }
            }

            item {
                // A82 (spec Phase 19 — "electrical-code lookup architecture"): every standard and
                // citation here is exactly what the administrator types in — this app never
                // fetches, generates, or pre-fills any electrical-code content. See
                // CodeStandard.kt's own doc for the full "do not invent code requirements"
                // reasoning; this UI is the "allow the administrator to upload/update applicable
                // standards later" half of that same spec section.
                CollapsibleSectionCard(
                    title = "Electrical Code Standards",
                    subtitle = if (codeStandards.isEmpty()) "None on file yet" else "${codeStandards.size} standard(s), ${codeReferences.size} citation(s)"
                ) {
                    CodeStandardsSection(
                        standards = codeStandards,
                        references = codeReferences,
                        onAddStandard = { name, edition, source ->
                            scope.launch { codeStandardRepository.addStandard(name, edition, source) }
                        },
                        onDeleteStandard = { id -> scope.launch { codeStandardRepository.deleteStandard(id) } },
                        onAddReference = { standardId, checkLabel, section, relevance ->
                            scope.launch { codeStandardRepository.addReference(standardId, checkLabel, section, relevance) }
                        },
                        onDeleteReference = { id -> scope.launch { codeStandardRepository.deleteReference(id) } }
                    )
                }
            }

            item {
                // A83 (spec Phase 22, original §63 "FUTURE MONITORING"), extended A85 (Phase 23 —
                // "BUILD NOW, ACTIVATE LATER"): this is a status list, not a live dashboard. The
                // manufacturer list comes from MonitoringManufacturer (the same enum
                // MonitoringProviderRegistry keys its providers by), so this can't silently drift
                // from the real registry. Status per manufacturer comes from
                // MonitoringProviderRegistry.statusFor, which is itself driven only by
                // MonitoringConfig — never a value invented in this UI layer.
                CollapsibleSectionCard(
                    title = "Device Monitoring",
                    subtitle = "Mock data — ready for future activation"
                ) {
                    Text(
                        "Every manufacturer below is wired to local mock telemetry so the monitoring UI can be built and tested without any paid API access. Drop real credentials into local.properties (see app/build.gradle.kts) to activate a real integration later — no other part of the app needs to change.",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    MonitoringManufacturer.entries.forEach { manufacturer ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(manufacturer.label, style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
                            Text(
                                MonitoringProviderRegistry.statusFor(manufacturer).label,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textSecondary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = palette.outline)

                    // A149 (Deye integration round): the only manufacturer above with a real
                    // client — see RealDeyeProvider's own doc for what's confirmed vs. inferred
                    // about DeyeCloud's wire format. Deliberately account-login-based, not a
                    // pasted API key (see MonitoringCredentials.Deye's own doc for why) — this is
                    // the one place that login actually happens.
                    Text("Deye — connect your account", style = MaterialTheme.typography.titleSmall, color = palette.textPrimary, modifier = Modifier.padding(bottom = 6.dp))
                    if (deyeConnected) {
                        SettingsRow(icon = Icons.Default.AccountCircle, title = "Connected", subtitle = deyeEmail.ifBlank { "DeyeCloud account" }) {}
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (onOpenDevices != null) {
                                LumixPrimaryButton(text = "View live devices", onClick = onOpenDevices, modifier = Modifier.weight(1f))
                            }
                            LumixSecondaryButton(
                                text = "Disconnect",
                                onClick = {
                                    scope.launch {
                                        settingsRepository.clearDeyeConnection()
                                        MonitoringConfig.updateDeye(MonitoringCredentials.Deye())
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Text(
                            "Requires a free DeyeCloud developer \"App\" (appId/appSecret from developer.deyecloud.com) plus your normal DeyeCloud account email and password. Your password is used once to sign in and is never stored by this app — only the resulting session token is kept.",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = deyeAppIdInput, onValueChange = { deyeAppIdInput = it },
                            label = { Text("App ID") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = deyeAppSecretInput, onValueChange = { deyeAppSecretInput = it },
                            label = { Text("App Secret") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = deyeEmailInput, onValueChange = { deyeEmailInput = it },
                            label = { Text("Account email") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = deyeCompanyIdInput, onValueChange = { deyeCompanyIdInput = it },
                            label = { Text("Company ID (leave \"0\" for a personal account)") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = deyePasswordInput, onValueChange = { deyePasswordInput = it },
                            label = { Text("Account password") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                        LumixPrimaryButton(
                            text = if (deyeConnecting) "Connecting…" else "Connect",
                            enabled = !deyeConnecting && deyeAppIdInput.isNotBlank() && deyeAppSecretInput.isNotBlank() && deyeEmailInput.isNotBlank() && deyePasswordInput.isNotBlank(),
                            onClick = {
                                deyeConnectError = null
                                deyeConnecting = true
                                val credentials = MonitoringCredentials.Deye(
                                    appId = deyeAppIdInput.trim(),
                                    appSecret = deyeAppSecretInput.trim(),
                                    email = deyeEmailInput.trim(),
                                    password = deyePasswordInput,
                                    companyId = deyeCompanyIdInput.trim().ifBlank { "0" }
                                )
                                scope.launch {
                                    when (val result = DeyeAuthClient().login(credentials)) {
                                        is DeyeAuthResult.Authenticated -> {
                                            settingsRepository.setDeyeConnection(
                                                appId = credentials.appId, appSecret = credentials.appSecret,
                                                email = credentials.email, companyId = credentials.companyId,
                                                accessToken = result.accessToken, expiresAtMillis = result.expiresAtMillis
                                            )
                                            MonitoringConfig.updateDeye(
                                                credentials.copy(accessToken = result.accessToken, tokenExpiresAtMillis = result.expiresAtMillis)
                                            )
                                            deyePasswordInput = ""
                                        }
                                        is DeyeAuthResult.Failed -> deyeConnectError = result.reason
                                    }
                                    deyeConnecting = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (deyeConnectError != null) {
                            Text(
                                deyeConnectError.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.warningRedText,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }

            item {
                // A85 (Phase 24 — "Build the AI layer as an optional service that can be
                // disabled... Build the architecture so an AI provider can be connected later"):
                // status only, matching the Device Monitoring section's own pattern. AiConfig
                // defaults to disabled (no API key) — see AiConfig.kt's own doc.
                CollapsibleSectionCard(
                    title = "AI Assistant",
                    subtitle = if (AiConfig.enabled) "Enabled" else "Disabled — ready for future activation"
                ) {
                    Text(
                        "Optional: explains results this app's own deterministic engine already computed. Never used to perform PV/battery/inverter sizing, MPPT, Voc/Vmp/Isc validation, SOC, or backup calculations — those stay deterministic regardless of this setting. Disabled until an AI provider and API key are configured.",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary
                    )
                }
            }

            item {
                // A85 (Phase 24 — "MCP... build it as an optional local/development interface...
                // expose read-only information only"): status only; the tool registry itself
                // (McpToolRegistry) has no UI of its own yet — it exists for a future MCP host/
                // client to call, in-process, since this sandbox has no MCP transport available.
                CollapsibleSectionCard(
                    title = "MCP Access",
                    subtitle = if (McpConfig.enabled) "Enabled (read-only)" else "Disabled — ready for future activation"
                ) {
                    Text(
                        "Optional: exposes read-only queries (system design, quote, battery/inverter status, simulation state, warnings, material takeoff) to a future MCP client. Cannot modify the engineering design — any change still goes through this app's own validation and calculation engine.",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary
                    )
                }
            }

            item {
                CollapsibleSectionCard(title = "Data") {
                    SettingsRow(
                        icon = Icons.Default.DeleteSweep,
                        title = "Clear quote history",
                        subtitle = "Permanently deletes every saved quote from this device"
                    ) {}
                    LumixSecondaryButton(
                        text = "Clear quote history",
                        onClick = { showClearHistoryConfirm = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Text(
                    "Lumix Solar Pro",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }

    if (showClearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirm = false },
            title = { Text("Clear quote history?") },
            text = { Text("This permanently deletes every saved quote from this device. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { quoteRepository.clearAll() }
                    showClearHistoryConfirm = false
                }) {
                    Text("Clear", color = palette.warningRedText)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

/**
 * A82 (spec Phase 19 — "electrical-code lookup architecture"): the administrator-facing half of
 * this feature — add/remove a [CodeStandard] the administrator attests is on file, then cite it
 * against one of [SystemDiagnostics.ALL_CHECK_LABELS] (a real engineering check this app already
 * computes, never a free-floating claim). Nothing here is pre-filled or suggested; every field
 * starts blank, matching this codebase's "don't invent business/regulatory content" discipline.
 */
@Composable
private fun CodeStandardsSection(
    standards: List<CodeStandard>,
    references: List<CodeRequirementReference>,
    onAddStandard: (name: String, edition: String, source: String) -> Unit,
    onDeleteStandard: (id: String) -> Unit,
    onAddReference: (standardId: String, checkLabel: String, section: String, relevance: String) -> Unit,
    onDeleteReference: (id: String) -> Unit
) {
    val palette = LocalLumixPalette.current

    Text(
        "Standards on file here are exactly what you enter — this app never fetches or invents electrical-code content. A citation only records that you've linked a check to a specific section of a standard you have; it is never a claim that this system is code-compliant.",
        style = MaterialTheme.typography.labelSmall,
        color = palette.textSecondary,
        modifier = Modifier.padding(bottom = 12.dp)
    )

    if (standards.isEmpty()) {
        Text(
            NO_STANDARDS_ON_FILE_MESSAGE,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textPrimary,
            modifier = Modifier.padding(bottom = 12.dp)
        )
    } else {
        standards.forEach { standard ->
            CodeStandardRow(
                standard = standard,
                references = references.filter { it.standardId == standard.id },
                onDelete = { onDeleteStandard(standard.id) },
                onDeleteReference = onDeleteReference
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
        }
    }

    CollapsibleGroup(title = "Add a standard") {
        var name by remember { mutableStateOf("") }
        var edition by remember { mutableStateOf("") }
        var source by remember { mutableStateOf("") }
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("Standard name (e.g. NEC, JS 316)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = edition, onValueChange = { edition = it },
            label = { Text("Edition/year (e.g. 2023)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = source, onValueChange = { source = it },
            label = { Text("Source / where this document is on file") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
        LumixSecondaryButton(
            text = "Add standard",
            onClick = {
                if (name.isNotBlank() && edition.isNotBlank()) {
                    onAddStandard(name.trim(), edition.trim(), source.trim())
                    name = ""; edition = ""; source = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (standards.isNotEmpty()) {
        CollapsibleGroup(title = "Cite a standard against a check") {
            var selectedStandard by remember(standards) { mutableStateOf(standards.first()) }
            var selectedCheckLabel by remember { mutableStateOf(SystemDiagnostics.ALL_CHECK_LABELS.first()) }
            var section by remember { mutableStateOf("") }
            var relevance by remember { mutableStateOf("") }

            LabeledDropdown(
                label = "Standard",
                options = standards,
                selected = selectedStandard,
                optionLabel = { "${it.name} ${it.edition}" },
                onSelected = { selectedStandard = it }
            )
            LabeledDropdown(
                label = "Check this citation applies to",
                options = SystemDiagnostics.ALL_CHECK_LABELS,
                selected = selectedCheckLabel,
                optionLabel = { it },
                onSelected = { selectedCheckLabel = it },
                modifier = Modifier.padding(top = 8.dp)
            )
            OutlinedTextField(
                value = section, onValueChange = { section = it },
                label = { Text("Section / article") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)
            )
            OutlinedTextField(
                value = relevance, onValueChange = { relevance = it },
                label = { Text("Why this section applies here") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
            LumixSecondaryButton(
                text = "Add citation",
                onClick = {
                    if (section.isNotBlank()) {
                        onAddReference(selectedStandard.id, selectedCheckLabel, section.trim(), relevance.trim())
                        section = ""; relevance = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private const val NO_STANDARDS_ON_FILE_MESSAGE =
    "No standards on file. Every check on the System Result screen will show \"Source document required for verification.\" until you add one below."

@Composable
private fun CodeStandardRow(
    standard: CodeStandard,
    references: List<CodeRequirementReference>,
    onDelete: () -> Unit,
    onDeleteReference: (id: String) -> Unit
) {
    val palette = LocalLumixPalette.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Description, contentDescription = null, tint = palette.solarYellowText)
                Column {
                    Text("${standard.name} ${standard.edition}", style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
                    if (standard.sourceNote.isNotBlank()) {
                        Text(standard.sourceNote, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                    }
                }
            }
            IconButton(onClick = onDelete) { Text("✕", color = palette.warningRedText) }
        }
        if (references.isEmpty()) {
            Text(
                "No citations linked to this standard yet.",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
                modifier = Modifier.padding(start = 34.dp, top = 4.dp)
            )
        } else {
            references.forEach { ref ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 34.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("§${ref.sectionArticle} — ${ref.checkLabel}", style = MaterialTheme.typography.labelSmall, color = palette.textPrimary)
                        if (ref.relevanceNote.isNotBlank()) {
                            Text(ref.relevanceNote, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                        }
                    }
                    IconButton(onClick = { onDeleteReference(ref.id) }) { Text("✕", color = palette.warningRedText) }
                }
            }
        }
    }
}

/** A One UI-style settings list row: a leading icon in a soft rounded backdrop, title/subtitle, trailing control. */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit
) {
    val palette = LocalLumixPalette.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(LumixRadius.sm))
                .background(palette.glass),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = palette.solarYellowText)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            }
        }
        trailing()
    }
}
