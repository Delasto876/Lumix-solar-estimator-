# Lumix Solar Estimator (Android)

Native Android app built with Kotlin + Jetpack Compose (Material 3), redesigned as a
premium, dark-first energy-tech product on top of the original quoting logic.

## Design system

- **Palette** (`ui/theme/Color.kt`, `Palette.kt`) — midnight background, graphite
  surfaces, solar-yellow primary accent, with energy-green, technical-cyan, and
  solar-amber used sparingly as functional accents. Full dark and light themes share the
  same accent hues; `LocalLumixPalette` exposes tokens beyond Material3's default roles.
- **Motion** (`ui/theme/Motion.kt`) — shared spring specs (snappy/gentle/responsive) used
  for button press feedback, counters, ring gauges, and the bottom-nav indicator. Looping
  decorative animation is skipped when the system's "Remove animations" accessibility
  setting is on (`ui/theme/Accessibility.kt`).
- **Components** (`ui/components/`) — `LumixPrimaryButton`/`LumixSecondaryButton` (spring
  press-scale), `SurfaceCard`/`GlassSurface`, `AnimatedCounterText` (numbers ease toward
  new values instead of snapping), `RingGauge`, `SavingsGraph` (custom Canvas, drag-to-
  scrub 20-year projection), `EnergyFlowDiagram` (tappable Sun→Panels→Inverter→Battery→
  Home nodes with an animated connector pulse), `RoofPanelVisualization` (panels fill in
  one by one), `SolarHeroVisual` (ambient Home-screen illustration), `FloatingBottomNav`.

## Navigation

A tabbed shell (`ui/nav/LumixNavHost.kt`) with a floating pill bottom nav — **Home**,
**Estimate**, **Systems**, **Savings**, **Profile**. Starting a quote pushes the wizard
and results as a focused full-screen flow with the nav bar hidden, then returns to the
tab shell.

## What's here

- **Home** — a greeting, an ambient interactive solar illustration reacting to your last
  quote's coverage %, and a quick energy snapshot (bill / solar size / savings).
- **Estimate → Wizard** — the same 7-step quote flow (mode & site info, roof & mounting,
  loads, JPS usage, backup, manual builder, pricing) as native Compose screens with
  inline validation, restyled with the design system. Tapping *Calculate* plays a short
  staged sequence ("Analyzing your energy usage… → Finding your best system…") before
  landing on Results.
- **Results** — an animated kW hero counter, a performance stat grid (production,
  coverage ring, savings, payback), the tappable energy-flow diagram, an animated roof/
  panel visualization, a drag-scrub 20-year savings graph, and the restyled material
  breakdown.
- **Systems** — saved quote history (Room), each with its full input/result snapshot so
  past quotes stay reproducible even after prices change.
- **Savings** — the latest quote's coverage, monthly savings, payback, and 20-year
  projection, reachable independent of a fresh calculation.
- **Profile** — the editable regular/discount price lists (DataStore Preferences) with
  reset-to-default.
- **Sizing engine** — `domain/SystemCalculator.kt` is a line-for-line Kotlin port of the
  original `calculateSystem`/`calculateLoadsKwhAndPeak` logic (same PSH, DOD, tariff
  constants and panel/inverter/battery selection rules). `domain/SavingsCalculator.kt` is
  new: it derives solar coverage %, monthly savings, payback, and a 20-year cost
  projection from the existing sizing output (documented escalation/degradation
  assumptions — these are new, honestly-labeled estimates, not part of the original app).
- **PDF export & share** — `pdf/QuotePdfGenerator.kt` renders a shareable PDF quote via
  Android's `PdfDocument` API and the system share sheet.

## Digital twin simulator (Phases 1–4 of 4)

Reachable via **⚡ Explore Your Energy** on the Results screen, or the bolt icon on any
saved quote in **Systems** — never a separate fake demo, always driven by that quote's
real calculated PV/inverter/battery/load numbers (`domain/simulation/SimSystemConfig.kt`).

- **`domain/simulation/SimulationEngine.kt`** — a pure, UI-independent engine. It
  precomputes a full 24-hour timeline (5-minute resolution) once per quote: a deterministic
  bell-curve solar model (clipped to the real inverter's kW rating), a shaped daily house-
  load curve (scaled to the quote's real average daily kWh, capped at its real peak watts),
  and battery SOC physics (efficiency, min/max SOC, a C/2 charge/discharge rate cap) — then
  applies the solar→house→battery→grid priority routing from the spec at every step,
  including a `POWER_LIMITED` state when demand exceeds solar+battery and the grid is
  unavailable. Dragging the time dial is then a cheap lookup + linear interpolation into
  that timeline — no live re-simulation needed, which keeps scrubbing instant.
- **`ui/simulation/`** — `TimeDial` (drag-anywhere 24h dial with a highlighted daylight
  arc), `HouseSimulationVisual` (an isometric house/roof/panels/inverter/battery/grid-pole
  illustration with animated particle flow along every active connector, intensity scaled
  to real flow magnitude), live Solar/Grid/Home/Battery readouts, a status pill, and a
  floating play/pause/speed transport bar (1x plays a simulated day in real-time proportion
  per the spec: 1 sim-minute per real-second).
- Verified the same way as the other calculators: `SimulationEngine` was run standalone
  against real calculated quotes (hybrid+grid, hybrid+cloudy, hybrid+outage, off-grid,
  grid-tie-no-battery, and a deliberately undersized battery) asserting energy balance,
  SOC bounds, zero unmet load whenever grid-connected, and `POWER_LIMITED` correctly
  appearing when battery + grid can't cover demand — see the scenario output in this
  session's history for the numbers.

**Phase 2 additions:**

- **Appliances** (`domain/simulation/SimAppliance.kt`) — a 14-item checklist (lights, TV,
  fridge, fans, AC, microwave, washer, dryer, iron, water heater, oven, pump, computer, EV
  charger) with realistic wattages. Default on/off state is derived from what the user
  actually told the estimator they have (`inputs.appliances`, `inputs.ac.hasAc`) — not
  arbitrary — so the simulator starts matching the quote. Appliances the wizard never asks
  about default off, and turning them on is an explicit "what if." The engine's day-shaped
  curve now represents a reduced ambient/background load, with the checklist's live total
  added on top flat, so toggling something has an immediate, visible effect on load and on
  which flows (solar/battery/grid) cover it — including showing `POWER_LIMITED` if you turn
  on enough to outrun solar + battery with the grid off. Reachable via the "Appliances" row
  under the live power readouts, which opens a bottom sheet with a live "CURRENT LOAD"
  counter and checkboxes.
- **Grid toggle** — a live JPS Connected/Disconnected switch (hidden for pure off-grid
  systems, which have no grid to toggle). Disconnecting rebuilds the timeline with
  `gridConnected = false` and shows a "⚡ JPS OUTAGE — HOME RUNNING ON BACKUP" banner.
- **Tap-to-inspect** — a row of Panels/Inverter/Battery/Grid/Home chips under the house
  visualization; each opens a bottom sheet with real numbers pulled from the live frame
  (current output, load breakdown by source, battery charge/discharge + estimated backup
  runtime, grid import/export) rather than a fixed pixel-region tap on the illustration
  itself, which would need hit-test geometry kept in lockstep with the drawing code —
  chips are simpler and more reliable without on-device testing.
- Verified the same way as Phase 1: the engine was run standalone with the exact appliance
  scenario from the spec (AC on/off swinging the load by exactly its rated 1.5 kW) and an
  "everything on including an EV charger, grid disconnected" case, confirming `POWER_LIMITED`
  correctly appears when a small system's battery can't cover a deliberately oversized load.

**Phase 3 additions:**

- **Weather/cloud control** (`domain/simulation/WeatherState.kt`) — Clear/Partly Cloudy/
  Cloudy/Heavy Cloud/Storm at the spec's own 100/70/40/15/5% multipliers, wired straight into
  the engine's existing `cloudMultiplier` parameter. Selecting one rebuilds the timeline —
  confirmed standalone that Storm produces far less noon output than Clear, and that partial
  cloud still correctly respects inverter clipping (a hybrid system's 3 kW inverter caps
  output identically at Clear and Partly Cloudy once raw PV exceeds it either way).
- **What-If row** — one-tap experiments under the weather selector: **Cloud Event** (a
  temporary ~3.5s dip to Storm that self-reverts to whatever weather was selected, driven by
  a cancellable coroutine so re-tapping mid-event is a no-op), **Simulate Outage** (disconnects
  the grid, same effect as the toggle but framed as a suggestion), and **Low Battery (20%)**
  (rebuilds the timeline from a 20% starting SOC instead of the default 60%). Outage and
  battery actions only appear when relevant to the system (hidden for off-grid or
  batteryless configs).
- **24h energy graph** (`ui/simulation/EnergyGraph.kt`) — a collapsible Canvas chart plotting
  Solar and Consumption across the full precomputed timeline, plus a dashed Battery-% line,
  with a TIME/SOLAR/LOAD/BATTERY/GRID text readout for whatever instant is scrubbed. Dragging
  the graph calls the same `scrubTo` the time dial uses, so both stay in sync.
- **Battery full-time estimate** — `SimulationEngine.nextBatteryFullHour` scans the
  precomputed timeline forward from the current hour for the next ~100% SOC frame, returning
  null if the battery isn't currently charging or is already full (so the estimate doesn't
  show a stale marker). Surfaced as text ("🔋 Battery full at 9:00 AM") and a small marker on
  the time dial's ring. Verified standalone: starting a charge cycle at 30% SOC correctly
  finds a same-day full time, and querying from an hour when the battery is already full
  correctly returns no marker.

**Phase 4 additions:**

- **Basic/Technical mode** — a small pill toggle (`ModeToggle` in `SimulationScreen.kt`)
  next to the status pill. Technical mode adds a "Technical" section
  (`ui/simulation/TechnicalDetailsCard.kt`) with PV/battery/grid voltage and current,
  inverter output, grid frequency, and energy-today/energy-this-month readouts, computed by
  `domain/simulation/TechnicalReadout.kt`'s `TechnicalModel.compute()`. Voltage/current are
  derived from the real live frame's kW values against plausible nominal buses (380V PV,
  48V LiFePO4 battery scaled 46–53V by SOC, 110V/60Hz JPS grid) — explicitly labeled in the
  UI as "modeled, not live telemetry" rather than presented as measured data, consistent
  with how the rest of the app treats estimates. `energyTodayKwh` is a real Riemann-sum
  integral of the precomputed timeline's solar output up to the scrubbed hour (not a fixed
  number); `energyMonthEstKwh` is that day total × 30, labeled as an estimate. Verified
  standalone: voltage/current/frequency stayed within their physical bounds at four times of
  day (midnight, morning, noon, evening), and energy-today accumulated monotonically from 0
  at midnight to a full day's total by 11:55 PM.
- **Visual polish** — `SectionCard`/`SurfaceCard` gained an optional `accentColor` (a faint
  5%-alpha tint) now applied to the house-visualization card, tinted by the live system
  status color, so the card itself reflects Normal/Power-Limited/Outage state at a glance.
  Weather chips now spring-scale up slightly (via the existing `LumixMotion` spec) when
  selected, matching the micro-interaction language used elsewhere in the app.

**House visual, superseded**: the simulator originally used a hand-drawn Compose `Canvas`
illustration (described above) because this sandbox has no image-generation capability. Once
the user supplied an actual photoreal reference photo, the visual was rebuilt on top of that
real asset instead — see "Photoreal house overlay" below. `HouseSimulationVisual.kt` is no
longer wired into `SimulationScreen` but stays in the tree unused (its `statusColor()` helper
is still shared), in case the earlier illustration is wanted back.

## Photoreal house overlay

Follow-up work replacing the hand-drawn house illustration with the user-supplied
photorealistic reference photo (`res/drawable-nodpi/bg_house_energy_routes.png`) plus a fully
dynamic overlay on top — the image itself is never redrawn, regenerated, or recolored; only
transparent layers above it change. Built in four phases:

- **`domain/simulation/EnergyFlow.kt`** — `EnergyNode`/`FlowDirection`/`EnergyFlow`, and
  `EnergyFlowResolver.resolve(frame)`, which relabels a `SimFrame`'s already-computed granular
  sub-flows (`solarToHouseKw`, `solarToBatteryKw`, `batteryToHouseKw`, etc.) onto the four
  visual routes baked into the photo (solar→inverter, inverter↔battery, grid↔inverter,
  inverter→house). This is a pure relabeling, not a re-simulation, so it inherits the engine's
  priority routing and `POWER_LIMITED` handling for free — grid flows are already zeroed by
  the engine when disconnected.
- **`ui/simulation/SolarSimulationPaths.kt`** — normalized (0f..1f) anchor points for the four
  routes, hand-picked against the 1536×1024 reference photo, plus bounding boxes for the
  battery and panel array. Approximate by construction; a `DEBUG_SHOW_PATHS` flag (off by
  default) draws the raw polylines for recalibrating them visually in Android Studio.
- **`ui/simulation/EnergyFlowPathManager.kt`** — pure geometry/particle math (the spec's
  particle-count breakpoint table, power-dependent speed, arc-length-based position-along-
  path), kept free of Compose types so it's independently verifiable and renderer-agnostic.
- **`ui/simulation/EnergyFlowCanvas.kt`** — the umbrella composable: the fixed background
  `Image`, then (in order) the cloud layer, sun marker, battery fill wash, and animated
  particles, all sharing one aspect-ratio-locked `Box` so every layer stays aligned with the
  artwork regardless of screen width. Particles are small glow+core dots, power-dependent in
  count and speed, colored per the spec's semantic mapping (solar yellow; green/cyan for
  battery charge/discharge; amber/green for grid import/export; warm white into the house).
  Reversed flows (battery discharging, grid exporting) travel backward along the same printed
  line via a signed animation phase, so a bidirectional route only needs one polyline. Honors
  the app's existing reduced-motion accessibility setting by freezing particle motion.
- **`ui/simulation/EnvironmentOverlays.kt`** — `CloudOverlay` (soft gradient blobs over the
  sky, opacity from `1 - weather.multiplier`), `SunIndicator` (a glowing marker on a low arc,
  positioned by `SimulationEngine.daylightProgress()` and sized by `irradianceFactor() ×
  cloudMultiplier` — so it's physically impossible for the marker to show a bright sun while
  the panels report near-zero output), and `BatteryFillOverlay` (a translucent liquid-level
  wash rising within the battery's printed footprint, tracking live SOC).
- Wired into `SimulationScreen`: `EnergyFlowResolver.resolve(frame)` for the particle flows,
  `SimulationEngine.daylightProgress()`/`irradianceFactor()` for the sun marker, `1 -
  weather.multiplier` for cloud coverage, and `frame.batterySocPercent` for the fill overlay
  (only shown when `config.hasBattery`).
- Verified standalone (this sandbox can't compile the Android/Compose module): all 6 of the
  spec's critical test cases (solar+battery charging, battery discharge, grid import, export
  with full battery, simultaneous solar+battery+house, grid disconnected) run against
  hand-built `SimFrame`s through `EnergyFlowResolver`; the particle-count breakpoint table;
  path start/end/length sanity for all four paths; and `daylightProgress()` bounds at
  midnight/noon/sunrise. Plus a full import-completeness sweep across the module.

## Solar Site (Phases 1–3, in progress)

A new major module (a **Site** tab alongside Home/Estimate/Systems/Savings/Profile) letting a
homeowner or installer map a customer's actual roof and turn it into a real system estimate,
rather than a generic sizing based on electricity usage alone. Building toward 8 phases total;
phases 4–8 (compass sensor, live location service, the polished roof-analysis UI, wiring the
roof-constrained system into the estimator, and connecting site data into the digital-twin
simulation) are not yet built.

**Phase 1 — data model + geometry engine (`site/`, `site/geometry/`):**

- `SolarSite`, `RoofPlane`, `GeoPoint`, `PanelLayout` — the one shared model every later
  piece (map, manual entry, solar position, simulation) reads from and writes to.
- `RoofGeometryEngine` — turns a polygon of real WGS84 vertices into horizontal area (shoelace
  formula on a local equirectangular projection), pitch-adjusted true roof area (`horizontalArea
  / cos(pitch)`), perimeter, centroid, and an azimuth *suggestion*. Azimuth is deliberately
  exposed as two candidates 180° apart (perpendicular to the polygon's longest edge) rather than
  one confident answer — a flat traced shape genuinely can't say which side slopes down, and the
  UI always asks the user to confirm rather than presenting a guess as fact.
- `PanelLayoutOptimizer` — the direct answer to "if I draw an area and give panel dimensions and
  a count, can it tell me if that fits?": places real panel rectangles into the polygon
  (point-in-polygon + edge-setback distance checks, not `roofArea / panelArea`), tries portrait
  and landscape, and keeps whichever seats more panels. `fitsPanelCount(desiredCount, layout)` is
  the direct yes/no check.
- Verified standalone: a known 10m × 8m rectangle's area/perimeter/centroid/azimuth all matched
  hand computation; 2.278m × 1.134m 600W panels packed 21 landscape (cross-checked against a
  by-hand grid count and a footprint-never-exceeds-roof-area invariant); a degenerate 1m × 1m
  roof correctly fits zero panels without crashing. One real bug was caught this way — the
  packing grid originally started scanning from the polygon's raw bounding box instead of the
  setback-inset boundary, wasting the first row/column (18 vs. the correct 21 panels) — and
  fixed before this ever reached a screen.

**Phase 2 — solar position + resource provider (`solar/`):**

- `SolarPositionCalculator` — sun azimuth/elevation/zenith/sunrise/solar-noon/sunset from
  lat/lon/date-time, using the NOAA Solar Calculator's published equations (from Meeus'
  *Astronomical Algorithms*) — a recognized method, not a fixed animation. Cheap enough to call
  every frame as a time dial moves.
- `SolarResourceProvider` — an interface plus `UnavailableSolarResourceProvider`, the only
  implementation today. It returns nulls for PSH/irradiance rather than inventing a
  plausible-looking number; the UI shows "Solar resource data unavailable" and accepts manual
  entry. A real dataset (NASA POWER, PVGIS, Solcast, ...) can implement the same interface later
  without touching any caller.
- `SolarIrradianceModel` — the angle-of-incidence formula (Duffie & Beckman) connecting sun
  position to a roof's own azimuth/pitch, the geometric link a later phase will use to make PV
  output actually respond to roof orientation.
- Verified standalone against real astronomical facts, not a fabricated table: at Jamaica's 18°N,
  June-solstice noon elevation came out ≈84.1° with the sun north of zenith, December-solstice
  ≈48.5° with the sun south — both match the `90 - |latitude - declination|` identity for the
  known solstice declinations (±23.44°). Sunrise/sunset symmetry, the refraction-corrected
  horizon elevation (≈-0.83°) at the calculator's own reported sunrise, and ~12h equinox day
  length all checked out too.

**Phase 3 — map screen + manual entry (`site/map/`, `site/ManualSiteScreen.kt`):**

Per explicit instruction, **the map is optional, not a gate** — manual entry is a first-class
peer path, not a fallback bolted onto the map screen:

- `SolarSiteMapScreen` — Google Maps Compose satellite/hybrid/normal view, address search
  (`Geocoder`, off the main thread, never throws), a one-shot "My Location" button (permission
  is requested and gracefully skipped if denied — the screen stays usable either way), tap-to-
  place a pin, tap-to-trace a roof polygon anchored to real map coordinates (not a screen
  overlay), undo/redo/clear, and a bottom "Site Analysis" panel. Closing a traced polygon opens
  a confirm sheet (`RoofConfirmForm`) showing the geometry engine's suggested facing direction
  and letting the installer confirm azimuth/pitch/panel spec before it's added.
- `ManualSiteScreen` — the same result with zero map/GPS/network dependency: typed
  latitude/longitude, then roof length/width/azimuth/pitch/panel spec. `SolarSiteViewModel`
  turns those typed dimensions into a synthetic rectangular polygon and runs it through the
  *exact same* `RoofGeometryEngine`/`PanelLayoutOptimizer` pipeline a traced roof uses — manual
  entry isn't a lesser approximation, it's the same math with a different input method.
- `SolarSiteEntryScreen` (the new **Site** tab) presents "Use Satellite Map" and "Enter
  Manually" as two equal-weight cards, plus a list of previously saved sites (in-memory for
  now — `SiteRepository` isn't Room-backed yet; see Scope notes).
- The Maps SDK requires a `MAPS_API_KEY` in `android/local.properties` (git-ignored, never
  committed) — see `app/build.gradle.kts` for the manifest-placeholder wiring. Without a key the
  map screen's tiles won't render, but the rest of the app, including the entire manual entry
  path, works regardless.
- **Caught while wiring the fields**: `NumberField` (used throughout the app for kWh/currency
  entry) had no way to type a minus sign at all, which silently made negative longitude
  unenterable — Jamaica sits at roughly -77°. Fixed with a backward-compatible `allowNegative`
  flag (default `false`, so every existing call site is unaffected).
- **Verification gap, explicitly**: this is the one part of the whole project that couldn't be
  checked even the way the pure-math packages were — Maps Compose's exact API surface
  (`GoogleMap`, `Polygon`, `CameraPositionState`, `MapUiSettings`, ...) was written from
  training-knowledge confidence, not compiled or cross-referenced against live documentation,
  since this sandbox has no Android SDK and can't reach `dl.google.com`. Everything else
  (import completeness, geocoding's off-main-thread + try/catch safety, permission-check
  guards) was reviewed as rigorously as every other screen in this project. Test this screen
  in Android Studio before relying on it.

## Fixed vs. the original prototype

The original web app always priced panels, batteries, and mounting/wiring hardware from
`regularPrices`, even when "use discount price list" was toggled on — only the inverter
line actually respected the toggle. `SystemCalculator.calculate` now applies the selected
price list (`regular` or `discount`) consistently across every material line.

## Scope notes

- The wizard keeps its original 3-mode structure (Guided / Manual / Load-based) rather
  than being replaced by a simplified consumer-only flow — the detailed appliance-level
  and manual-builder capability is preserved, just restyled.
- The "circular coverage dial" is implemented as an animated read-only ring gauge (a
  derived output, not a draggable input) since there's no corresponding input field in
  the kept flow to drag against.
- Charts are hand-drawn on Compose `Canvas` rather than a charting library, to avoid an
  unverified new dependency in an environment that can't reach Google's Maven repo.
- `SiteRepository` (Solar Site module) is in-memory only — saved sites don't survive a process
  restart yet. A follow-up can persist it the same way `QuoteRepository` stores quotes (one
  JSON blob per row via kotlinx.serialization) without changing its public API.

## Building

This sandbox could not verify a full build: the Android SDK and AndroidX/Compose/Room
artifacts are hosted on Google's Maven repo (`dl.google.com`), which this environment's
network proxy blocks (Maven Central is reachable, Google's Maven is not). What *was*
verified here:

- The pure-Kotlin `domain` package (no Android dependencies) was compiled and run
  standalone against `kotlinx-serialization` from Maven Central across several guided /
  manual / load-based scenarios (hybrid, off-grid, grid-tie), including the new savings
  projection, with no exceptions and sane, correctly-clamped output.
- Every Gradle/Compose file was manually audited for import completeness and API usage
  against the pinned library versions. This pass caught and fixed several real issues:
  missing `Modifier.weight`/`getValue` imports, a color-initialization-order bug in
  `Theme.kt`, and a nullable-`String` `in Set<String>` type error in the nav host.

To build for real, open the `android/` folder in **Android Studio (Koala or newer)** with
network access to Google's Maven repo, or from the CLI:

```bash
cd android
./gradlew assembleDebug
```

Requires JDK 17+, Android SDK platform 34, and Kotlin 2.0.21 (installed automatically by
Android Studio / the Gradle wrapper).

To render the Solar Site map screen's satellite tiles, add a Google Maps API key: create
`android/local.properties` (if it doesn't already exist — Android Studio usually generates it
with `sdk.dir` on first open) and add a line `MAPS_API_KEY=your_key_here`. The file is
git-ignored, so the key never gets committed. Everything else in the app, including the entire
manual site-entry flow, works with no key at all.
