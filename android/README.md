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

## Solar Site (Phases 1–8, complete)

A new major module (a **Site** tab alongside Home/Estimate/Systems/Savings/Profile) letting a
homeowner or installer map a customer's actual roof and turn it into a real system estimate,
rather than a generic sizing based on electricity usage alone. Building toward 8 phases total;
phases 7–8 (connecting site data into the digital-twin simulation, and final polish) are not
yet built.

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

**Phase 4 — compass sensor + location service (`sensors/`, `location/`):**

- `sensors/CompassMath.kt` — the pure heading math split out from the Android sensor plumbing
  specifically so it's independently verifiable: `normalizeDegrees`/`shortestAngleDelta` (0°/360°
  wraparound arithmetic), `smooth` (exponential moving average against jittery raw readings —
  crossing the wraparound boundary the short way, not backward through 180°), and
  `compassLabel`. `sensors/CompassManager.kt` is the actual `TYPE_ROTATION_VECTOR` sensor
  wrapper — registers/unregisters a listener, converts the rotation vector to a heading via
  `SensorManager.getOrientation`, and corrects magnetic heading to **true north** using
  `GeomagneticField` (declination depends on location, so `updateLocation()` should be called
  whenever the working site coordinates are known). Solar orientation is measured against true
  north, never magnetic, per spec — the two differ by several degrees depending on where you
  are, enough to matter for panel-facing accuracy.
- `location/DeviceLocationManager.kt` — wraps `FusedLocationProviderClient` with a permission
  check baked into every entry point (`hasPermission()`, a one-shot `lastKnownLocation()`, and a
  continuous `locationUpdates()` `Flow`), so a caller can never crash from a missing-permission
  call. Every caller still has manual entry as a fully independent path — permission being
  denied never blocks the app, per spec.
- `site/SolarCompassBadge.kt` — a compact "fixed dial, rotating needle" compass widget (N/E/S/W
  stay upright, the needle rotates to point toward true north) wired into
  `SolarSiteMapScreen`'s top-left corner, live off `CompassManager.state`. The map screen's ad
  hoc Phase-3 location fetch was also replaced with `DeviceLocationManager` for consistency.
- Verified standalone: `CompassMath`'s wraparound arithmetic (`shortestAngleDelta(350°, 10°) =
  +20°`, the short way through 0°/360°, not −340°) and the smoothing function crossing that same
  boundary correctly (smoothing from 350° toward 10° lands near 0°, not doubling back through
  180°) — the sensor plumbing itself (`CompassManager`, `DeviceLocationManager`) needs a real
  device and could not be tested here at all, unlike everything else in this project.

**Phase 5 — roof analysis UI (`site/geometry/ShadeEstimator.kt`, `RoofScoreCalculator.kt`,
`site/RoofPlaneDiagram.kt`, `SolarPotentialCard.kt`, `SiteDetailScreen.kt`):**

- `ShadeEstimator` — nearby-obstruction checkboxes (trees, nearby building, utility pole, other)
  suggest a starting shading estimate via documented flat percentages per type, always shown as
  "estimated" and always directly overridable — satellite imagery has no elevation data, so real
  ray-traced shading is out of reach, and pretending otherwise would violate the spec's "do not
  fake accuracy" requirement.
- `RoofScoreCalculator` — a 100-point preliminary roof-quality estimate, explicitly not a
  certified assessment. Five independent 0–20 factors: **Area** (absolute usable m²),
  **Orientation** (how close the confirmed or suggested azimuth is to the ideal equator-facing
  direction for the site's hemisphere), **Pitch** (how close to the rule-of-thumb optimal tilt ≈
  site latitude), **Usable space** (usable ÷ total roof area — how much survives setback and
  exclusions), and **Shading** (from the exposure estimate above). This deliberately differs from
  the spec's own illustrative breakdown, which lists "Solar exposure" and "Shading" as two
  separate categories measuring roughly the same thing — Pitch replaces the duplicate here as an
  independently meaningful factor. Unconfirmed azimuth/pitch get neutral half-credit (10/20)
  rather than a false zero or a false full score.
- `RoofGeometryEngine.usableAreaM2` gained an `additionalExclusionAreaM2` parameter (defaulted,
  backward compatible) — a lump-sum obstruction area (chimney, tank, vents) entered directly
  rather than traced as a polygon, since the manual-entry flow has no map to trace on. Full
  exclusion-*zone* polygon tracing on the live map (per spec section 7's more elaborate ask) is
  deferred; the lump-sum field covers the same underlying need (usable area accounts for
  obstructions) without the added Maps-SDK surface area.
- `RoofPlaneDiagram` — an actual top-down diagram of the roof outline with every packed panel
  drawn as a rotated rectangle (not just a placement count), reusing
  `RoofGeometryEngine.toLocalMeters` so it renders identically whether the roof came from the
  map or from typed dimensions.
- `SolarPotentialCard` — the polished "here's what your roof can do" summary: the diagram, area/
  usable/panels/capacity/orientation/pitch/exposure stats, the Roof Score breakdown, an accuracy
  disclaimer, and a "Use This Roof" button (present but not yet wired to the estimator — that's
  Phase 6).
- `SiteDetailScreen` — a new read-only screen (wired into `SolarSiteEntryScreen`'s previously
  stubbed "open saved site" action, and reached automatically after saving a new site) listing
  every roof plane's full `SolarPotentialCard`.
- `ShadeAndExclusionSection` is shared by both the map's roof-confirm sheet and the manual-entry
  form, so obstruction marking and the excluded-area field work identically regardless of which
  path built the roof plane.
- Verified standalone: `ShadeEstimator`'s per-type loss percentages sum correctly (trees + nearby
  building + utility pole + other = 27% loss → 73% exposure); `RoofScoreCalculator` scores a
  "perfect" roof (south-facing at 18°N, pitch matching latitude, no shading, usable area equal to
  a 50m² reference) at exactly 100/100, a north-facing roof at 18°N (worst possible orientation)
  at 0/20 on that factor, and unconfirmed azimuth/pitch at the neutral 10/20 each.

**Phase 6 — connect roof analysis to the estimator (`domain/QuoteInputs.kt`'s `RoofConstraint`,
`SystemCalculator.kt`, `WizardViewModel.kt`, `ResultsScreen.kt`):**

This is where the Solar Site module stops being a separate tool and actually changes what the
estimator recommends.

- `RoofConstraint` (in the `domain` package, not `site` — the estimator's domain layer doesn't
  depend on the Site module, only on this small data-transfer shape) carries a roof plane's
  `PanelLayoutOptimizer`-verified panel count into `QuoteInputs.roofConstraint`.
- `SystemCalculator` now determines the panel count exactly as before (from electricity usage —
  the "energy-optimal" figure), then, in one place right before that count starts driving
  inverter/rail/wiring/material calculations, caps it at the roof's real limit if one is set and
  binding. The cap rounds **down** to the nearest even panel count rather than reusing
  `enforceEvenPanels` (which rounds up for normal sizing) — rounding up here would let the
  even-row convention silently exceed what the roof was actually verified to hold, breaking the
  panel-packing engine's own "never overclaim" guarantee from Phase 1.
- `QuoteResult` gained `energyOptimalPanelCount` (defaults to `panelCount`, so quotes saved
  before this field existed still decode without a roof constraint on record — the accurate
  reading for old data) plus derived `energyOptimalPvKw` and `isRoofConstrained`.
- `SolarPotentialCard`'s "Use This Roof" button (present since Phase 5, unwired until now) calls
  `WizardViewModel.startWithRoofConstraint()`, which resets the wizard and pre-loads the
  constraint, then opens the wizard fresh.
- `ResultsScreen` shows a "Your roof limits the recommended system" banner whenever
  `result.isRoofConstrained` is true, stating both figures ("Your electricity usage calls for
  about 7.2 kW, but Roof A can physically fit about 5.4 kW") and suggesting tracing more roof
  area, a second roof plane, or ground mounting — matching the spec's energy-optimal-vs-roof-
  physical-limit distinction.
- Verified standalone: a large-usage guided quote (avg bill J$200,000) that would normally
  recommend 38 panels, capped by a 7-panel roof (odd — confirming it rounds *down* to 6, never
  up to 8), correctly reports `panelCount=6`, `energyOptimalPanelCount=38`,
  `isRoofConstrained=true`, and the capped `panelCount` never exceeds the roof's real limit; the
  same quote against a 200-panel roof (not actually binding) correctly reports
  `isRoofConstrained=false` and leaves the panel count completely unchanged.

**Phase 7 — wire site data into the digital-twin simulation
(`domain/simulation/SimSystemConfig.kt`, `domain/simulation/SimulationEngine.kt`,
`ui/simulation/SimulationScreen.kt`, `ui/nav/LumixNavHost.kt`):**

The digital-twin simulator (the "Interactive Solar Home Simulation" from earlier phases) gets a
real, physically-grounded solar model when a quote came from Solar Site's "Use This Roof" flow,
instead of always falling back to the generic time-of-day bell curve.

- `RoofConstraint` gained `latitude`/`longitude` (default `0.0`, for backward-compatible decoding
  of quotes saved before these fields existed — harmless, since the site-aware path is gated on
  azimuth/pitch being non-null, not on the coordinates alone), threaded through from
  `LumixNavHost`'s "Use This Roof" wiring, which now reads the source site's stored location.
- `SimSystemConfig` gained optional `siteLatitude`/`siteLongitude`/`roofAzimuthDegrees`/
  `roofPitchDegrees` (all default `null`) and a computed `isSiteAware` flag, true only when every
  one of the four is present. `SimSystemConfig.from` now takes the full `QuoteInputs` (not just
  the `QuoteResult`) so it can read `roofConstraint`.
- `SimulationEngine.sitePlaneOfArrayFactor` computes the real sun position for **today's actual
  date** (a live digital twin should reflect the current day, not a fixed reference date) at the
  simulated hour, via the Phase 2 `SolarPositionCalculator`, then runs it through the Phase 2
  `SolarIrradianceModel.planeOfArrayFactor` against the roof's real azimuth/pitch. `buildDayTimeline`
  multiplies this factor into the existing PV calculation only when `config.isSiteAware`; site-
  unaware quotes are completely unaffected (factor implicitly 1.0), so the original simulator
  behavior is preserved byte-for-byte for every quote that didn't come through Solar Site.
- A well-oriented roof (south-facing, pitch matching latitude) was deliberately designed to *not*
  be penalized relative to the old generic model: at solar noon its plane-of-array factor peaks
  near 1.0, matching the generic bell curve's own peak, so "Use This Roof" never makes a good
  roof look worse than a hand-entered quote would have.
- `SimulationScreen` shows a new "Sun & Roof" card (only when `config.isSiteAware`) with the live
  sun azimuth/elevation for the currently-scrubbed hour (using `CompassMath.compassLabel` for the
  8-point label, reusing the Phase 4 compass math) next to the roof's fixed azimuth/pitch — this
  is the "live compass" idea from the original spec, brought into the digital twin.
- Verified standalone: `isSiteAware` is correctly `false` with no roof constraint, `false` when
  azimuth/pitch are unconfirmed even with lat/lon present, and `true` only when fully specified;
  `sitePlaneOfArrayFactor` matches a by-hand computation through `sitePosition` +
  `SolarIrradianceModel.planeOfArrayFactor` exactly; a south-facing, latitude-tilt roof's factor
  is `>0.95` at today's solar noon; at a fixed December-solstice morning (a date-independent,
  unambiguous case — chosen because at 18°N in mid-August the sun's declination sits close enough
  to the latitude that the sunrise azimuth actually swings north of due east, letting a
  north-facing roof legitimately out-produce south-facing during some morning hours on *today's*
  real date, which is correct low-latitude solar geometry rather than a bug) a north-facing roof's
  factor (0.279) is meaningfully below a south-facing roof's (0.634); and full day-timeline
  simulations for both a south-facing and a north-facing site-aware config still respect every
  existing PV/SOC/load-flow bound.

**Phase 8 — polish: sun path visualization, monthly PSH estimate, 3D map view, offline handling
(`solar/SolarPathSampler.kt`, `solar/ClearSkyPshEstimator.kt`, `site/SunPathDiagram.kt`,
`site/MonthlyPshChart.kt`, `site/map/MapController.kt`, `site/map/SolarSiteMapScreen.kt`,
`network/NetworkConnectivityObserver.kt`):**

The last item on the original 8-phase plan — four smaller, independent additions rather than one
big feature.

- **Sun path diagram.** `SolarPathSampler` samples `SolarPositionCalculator` across a full day for
  an explicit date (unlike the simulation engine's deliberate `LocalDate.now()` usage, this takes
  the date as a parameter so it's independently testable and reusable for any reference day).
  `SunPathDiagram` plots today's daylight arc — azimuth (x) vs. elevation (y) — as a `Canvas`
  curve, with sunrise/sunset endpoints, the solar-noon peak, and the roof's own facing direction
  overlaid as a dashed vertical line, so it's visually obvious whether the sun's arc actually
  crosses the roof's orientation. Added to `SolarPotentialCard`, so every roof plane's summary
  (map-traced or manually entered — both paths converge on the same card) now shows it.
- **Monthly Peak Sun Hours.** `ClearSkyPshEstimator` integrates `sin(elevation)` — the standard
  clear-sky horizontal-irradiance proxy — over daylight hours for a representative day of each
  month, scaled by a fixed 0.75 clearness index so the numbers land in a believable range rather
  than the un-attenuated geometric maximum. This is **not** real irradiance/weather data — that
  stays `SolarResourceProvider`'s honestly-`null` job until a live dataset is wired in — so
  `MonthlyPshChart`'s 12-bar chart carries an explicit "clear-sky geometry only... not measured
  weather data" disclaimer rather than ever being mistaken for a real PSH dataset.
- **3D map view.** `MapController` gained an `is3D` toggle; the map screen's new 3D button
  animates the `GoogleMap` camera's tilt between 0° and 55° (`CameraPosition.Builder` copying the
  existing target/zoom/bearing), giving a closer, more roof-eye view when zoomed in to trace —
  using the Maps SDK's own tilt support already included in the existing dependency, not a new
  rendering path.
- **Offline handling.** `NetworkConnectivityObserver` wraps `ConnectivityManager` (new
  `ACCESS_NETWORK_STATE` manifest permission — a normal permission, no runtime prompt) as a
  `Flow<Boolean>`. The map screen — the one part of Solar Site that genuinely needs a live
  connection, since satellite tiles and address search both require it, unlike the rest of the
  module's pure local math — now shows an explicit "You're offline" banner with a one-tap "Manual
  Entry" button (wired through `LumixNavHost`) the moment connectivity drops, instead of leaving
  the user staring at a silently blank map with no explanation.
- Verified standalone: `SolarPathSampler.sampleDay` returns the exact expected sample count for
  its step size; `sampleDaylightPath` never includes a below-horizon sample; June 21 at 18°N shows
  a near-overhead max elevation (>80°, matching the ~84.5° found via Phase 2's own hourly
  sampling) with a longer daylight sample count than December 21 (~48.5° max elevation) — correct
  northern-hemisphere seasonal shape; `ClearSkyPshEstimator.estimateMonthlyPsh` returns exactly 12
  values, all within a believable 2–9 hour range, with June's estimate higher than December's at
  this latitude (Jamaica: roughly 4.0–6.2 hours across the year in this geometry-only model).

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

## Post-build field fixes (Samsung Galaxy A15 + simulation logic, in progress)

The app was first successfully built and run on a physical device (Samsung Galaxy A15) after
the sandbox-only development above. Real-device testing surfaced issues this environment's
lack of an Android SDK/emulator couldn't have caught. Fixes land in phases; each is verified
by code review + import-completeness sweep here (this sandbox still can't compile/run the app),
then confirmed on-device by the user before the next phase starts.

**Phase 1 — window-insets / safe-area pass:**

Root cause of "buttons cut off at the bottom of screens": several screens are reached as
*pushed* routes (Wizard, Results, the Solar Site map, manual site entry) rather than tab
routes. `LumixNavHost`'s outer `Scaffold` only reserves bottom space for tab routes
(`Modifier.padding(bottom = if (isTabRoute) scaffoldPadding.calculateBottomPadding() else
0.dp)`), which is correct — pushed screens don't have the floating bottom nav to clear — but
none of those screens then applied their *own* navigation-bar inset protection. Fixed:

- `WizardScreen.kt` — the Back/Next row was a plain last child of the body `Column` with a flat
  `16.dp` padding, not routed through Scaffold's `bottomBar` slot at all. Moved it into
  `bottomBar` with `.navigationBarsPadding()`, matching the pattern `SimulationScreen` already
  used correctly for its transport bar.
- `ResultsScreen.kt` — same anti-pattern for the "New quote" / "Share PDF" row; same fix.
- `SolarSiteMapScreen.kt` — this screen has no `Scaffold` at all (full-bleed map with overlaid
  controls), so its bottom analysis/drawing panel and top search bar, both positioned via plain
  `Box.align(...)`, had *zero* automatic inset protection — `navigationBarsPadding()` /
  `statusBarsPadding()` here are load-bearing, not defensive.
- `SimulationScreen.kt` — already used `bottomBar` correctly; added `navigationBarsPadding()`
  defensively for consistency.
- `ManualSiteScreen.kt` — its buttons already scroll with content (lower severity, never fully
  hidden), but as another pushed route its `LazyColumn`'s `contentPadding` didn't account for
  the nav bar either; added `WindowInsets.navigationBars` to the bottom content padding.

**Phase 1 correction — the real root cause was one level up.** The fixes above (moving
Back/Next into `bottomBar`, adding `navigationBarsPadding()`) still clipped on-device. Cause:
`LumixNavHost`'s own outer, app-wide `Scaffold` — the one hosting the tab bar — used the
Material3 default `contentWindowInsets` (`WindowInsets.safeDrawing`). Scaffold consumes that
inset on behalf of its content **regardless of whether `bottomBar` is populated for the current
route**, which silently zeroed the navigation-bar inset for every screen further down the tree,
pushed or tabbed alike — so the `navigationBarsPadding()` calls added inside each individual
screen's own inner Scaffold had nothing left to consume; they were correct code operating on an
already-exhausted inset. Fixed in `LumixNavHost.kt`: the outer Scaffold now sets
`contentWindowInsets = WindowInsets(0, 0, 0, 0)` so it never reserves anything itself, and
`FloatingBottomNav` (the tab bar) gained its own explicit `navigationBarsPadding()` so it still
protects itself now that nothing upstream does it automatically. Insets now propagate fully
intact to every leaf screen, tab or pushed, each handling its own edges — no more double
consumption anywhere in the tree.

Not yet done: splitting any wizard step whose *content* (not just the button row) is too tall
for a small phone — that's bundled into Phase 2 below, since it's the same step-list refactor.

**Phase 2 — customer-first wizard reorder + new defaults
(`QuoteInputs.kt`, `SystemCalculator.kt`, `QuoteResult.kt`, `SavingsCalculator.kt`,
`ui/wizard/`, `Validation.kt`):**

- New wizard step order: **Customer** (name/phone/email/address, plus parish/nearest-town moved
  here from the old Site Info step) is now step 1, before any electricity-usage or sizing
  question. Full order: 1 Customer, 2 Mode & Site Info, 3 Roof & Mounting, 4 Loads, 5 JPS
  Bill/Usage, 6 Backup Requirements, 7 Manual System Builder (Manual mode only), 8 Pricing &
  Discount. `WizardViewModel`/`WizardScreen`/`Validation` all renumbered together;
  `Validation`'s functions are now named for what they validate (`customerErrors`,
  `usageErrors`, `manualErrors`, `pricingErrors`) instead of by step number, so a future
  reorder doesn't silently re-wire the wrong check to the wrong step again.
- New defaults: monthly bill **J$16,000** (was 50,000), backup duration **12h** (was 4h,
  16h added as a preset option alongside 4/8/24/Custom).
- New `QuoteInputs.peakSunHours` field (default **5.5**, editable in the Usage step) replaces
  `SystemCalculator`'s previously-hardcoded `PSH = 6.0` constant everywhere it mattered
  (panel-count sizing *and* `SavingsCalculator`'s production projection) — every quote now
  carries its own PSH assumption instead of the whole app sharing one baked-in number.
- `BackupCoverage` gained `CRITICAL_LOADS`/`MOST_LOAD`/`CUSTOM` (`MOST_LOAD` is the new
  default) alongside the original `ESSENTIALS`/`FULL`, which are kept — not renamed — purely
  so quotes saved before this change still decode (kotlinx.serialization encodes enum
  constants by name). `CRITICAL_LOADS` and `ESSENTIALS` compute identically; so do
  `MOST_LOAD` and `FULL`. `CUSTOM` adds a user-chosen 10–100% slider
  (`customBackupCoverageFraction`) instead of a fixed haircut.
- New capacity-check warning: `QuoteResult.backupCapacityWarningKw` is set when the requested
  backup coverage (Most Load/Custom — never Critical Loads/Essentials, which never asked for
  the full peak) implies more load than the actually-selected inverter can deliver (its rating
  got capped at the catalog's largest option). `ResultsScreen` shows an amber warning banner
  in that case, naming the shortfall and suggesting Critical Loads coverage or a larger
  inverter — the same "never silently pretend it fits" pattern as the roof-constraint banner.
- Verified standalone: every existing scenario still passes unchanged (confirms
  `ESSENTIALS`/`FULL` decode and calculate identically to before); a lower PSH now correctly
  requires more PV kW for the same energy target; `SavingsCalculator` production now moves
  with `peakSunHours` instead of a fixed constant; `CRITICAL_LOADS`/`MOST_LOAD` match legacy
  `ESSENTIALS`/`FULL` battery sizing exactly; `CUSTOM` at 50%/80% falls strictly between
  `CRITICAL_LOADS`(60%) and `MOST_LOAD`(100%) as expected; a deliberately oversized off-grid
  appliance load triggers the capacity warning under `MOST_LOAD` but never under
  `CRITICAL_LOADS`; a modest, well-within-capacity hybrid load triggers no warning.

**Phase 3 — hybrid inverter operating modes: SOL/SBU/UTI, and the hard no-export rule**
(`domain/simulation/SimFrame.kt`, `SimulationEngine.kt`, `EnergyFlow.kt`, `TechnicalReadout.kt`,
`ui/simulation/SolarSimulationPaths.kt`, `EnergyFlowCanvas.kt`, `HouseSimulationVisual.kt`,
`InspectPanel.kt`, `SimulationViewModel.kt`, `SimulationScreen.kt`):**

The grid connection this app models is **strictly import-only** — a correction from the user
that overrides the original spec text's own SOL-mode example (which mentioned conditional grid
export). Every mode, in every state, never sends power to JPS. Concretely: `SimFrame` lost its
`solarToGridKw` field and `SystemStatus.EXPORTING_TO_GRID` entirely; there is no code path left
that can produce a negative `gridPowerKw`. Solar surplus that can't be used or stored (battery
full or absent, no export outlet) is simply **curtailed** — a new `SimFrame.curtailedSolarKw`
tracks it for transparency (surfaced in the Panels inspect sheet as "Curtailed (unused)"),
distinct from "exported."

New `InverterMode` enum (`SOL`, `SBU`, `UTI`), selectable per-simulation from a new "Inverter
Mode" card on `SimulationScreen` (only shown for grid-capable systems), wired through
`SimulationViewModel`/`SimulationUiState` and `SimulationEngine.buildDayTimeline` the same way
`gridConnected`/`weather` already were — a runtime what-if toggle, not baked into the saved
quote:

- **SOL and SBU** are functionally identical priority chains, per the spec's own description of
  them: solar → house first, solar surplus → battery (up to its rate/room cap; anything left
  over is curtailed, never exported), and only once the battery is drawn down to its reserve
  floor does JPS import kick in. Verified standalone that SOL and SBU produce byte-identical
  timelines for the same config. Neither ever charges the battery from the grid.
- **UTI** makes JPS the primary house supply whenever it's connected — the battery is pure
  outage backup in this mode and never discharges to the house while grid power is available,
  falling back to the SBU-style solar→battery→nothing-else logic only during an outage. A new
  `gridToBatteryKw` flow (and a new `gridChargeEnabled` toggle, shown alongside the mode
  selector when UTI is active) lets JPS simultaneously top off the battery in the same frame it's
  serving the house — the flow the spec calls out explicitly and that didn't exist in the engine
  before this phase. Solar is still prioritized over grid for battery charging even in UTI mode
  (free power first).
- The battery's reserve floor (previously a generic 10% `BATTERY_MIN_SOC_FRACTION`) is now 20%,
  matching the spec's SOL/SBU cutoff.
- `SystemStatus` gained `GRID_CHARGING_BATTERY` (JPS topping off the battery with no house
  import happening in that same instant) and dropped `EXPORTING_TO_GRID`.

Visualization: the existing 4 fixed energy-flow paths (`solar_inverter`, `grid_inverter`,
`inverter_battery`, `inverter_house`) needed no new geometry — `grid_inverter` (now marked
`bidirectional = false`, always GRID→INVERTER) and `inverter_battery` simply render active
simultaneously in a UTI grid-charging frame, which is exactly the "simultaneous grid-to-house
and grid-to-battery" visual the spec asked for. `EnergyFlowResolver` combines
`solarToBatteryKw + gridToBatteryKw` into the one physical inverter↔battery path's magnitude,
and `gridToHouseKw + gridToBatteryKw` into the one physical grid↔inverter path's magnitude.
`HouseSimulationVisual.kt` (the earlier hand-drawn Canvas visualization, superseded by the
photoreal overlay and confirmed unreferenced anywhere in the nav graph) was kept compiling
rather than deleted, on the same "don't rebuild, make targeted fixes" instruction as the rest of
this pass.

Verified standalone (scratch JVM project): the no-export invariant (`gridPowerKw >= 0` and
neither grid sub-flow ever negative) holds across full-day timelines in all three modes; SOL/SBU
timelines are identical; every SBU grid-import frame is explained by either the reserve floor or
the battery's discharge-rate cap (not a logic gap); UTI never discharges the battery to the house
while grid-connected; UTI charges the battery from JPS when enabled and never does when disabled;
a real engine-produced frame shows simultaneous `gridToHouseKw` + `gridToBatteryKw`; a
battery-starts-full/near-zero-load scenario curtails solar rather than exporting it; and
`EnergyFlowResolver`'s grid path stays `FORWARD`-only with correct combined magnitudes across a
full UTI day.

**Phase 4 — Jamaica electrical config: 110V/220V split-phase, 50Hz, P=V×I, configurable utility
service amps** (`domain/simulation/SimAppliance.kt`, `TechnicalReadout.kt`, `SimulationEngine.kt`,
`ui/simulation/TechnicalDetailsCard.kt`, `InspectPanel.kt`, `SimulationViewModel.kt`,
`SimulationScreen.kt`):

Scoped to the simulation only, per an earlier explicit choice — the estimator's core appliance
catalog (pricing/sizing) is untouched; only the simulation-only `SimApplianceType` enum and the
Technical readout/engine gained electrical awareness.

- **50Hz, not 60Hz.** `TechnicalModel`'s grid frequency assumption was a real bug — Jamaica's
  mains frequency is 50Hz. Fixed.
- **Real 110V/220V split, not one flat fake voltage.** Every `SimApplianceType` now carries an
  `ElectricalTier` (`LOW`=110V for lighting/outlets/fridge/fans/etc., `HIGH`=220V for AC, water
  heater, oven, dryer, pump, EV charger — matching how Jamaican homes are actually wired). A
  new `applianceLoadKwByTier()` helper splits the currently-on appliances' load by tier, and
  `TechnicalModel.compute()` apportions the frame's actual grid draw across those two tiers by
  the live appliance mix, reporting genuine `gridLowCurrent`/`gridHighCurrent` via real P=V×I
  on each tier instead of one blended, physically-meaningless number. Verified standalone: an
  all-AC load draws its current entirely on the 220V leg, an all-lights load draws entirely on
  110V, and `(gridLowVoltage×gridLowCurrent + gridHighVoltage×gridHighCurrent)/1000` exactly
  reconstructs the frame's total grid draw — P=V×I round-trips correctly.
- **A real, configurable utility service current limit — enforced in the engine, not just
  displayed.** `SimulationEngine.buildDayTimeline` gained a `gridServiceAmps` parameter
  (default 30A, matching the spec) that hard-caps total grid import (`gridToHouseKw +
  gridToBatteryKw`) at `amps × 220V`, the same both-legs-capacity convention used for the
  Technical readout's own service-utilization figure so the two stay consistent. When the cap
  binds, battery charging backs off first (it's the lower-priority use of grid import), then
  house import is throttled as a last resort, converting the remainder into `unmetLoadKw` —
  exactly like a real breaker trip would behave, and surfacing as the existing
  `POWER_LIMITED` status rather than a new one. Selectable at runtime from the Inverter Mode
  card (15A/30A/60A/100A presets), the same what-if pattern as `inverterMode`/`gridConnected`.
  Verified standalone: a 25kW load against a 30A service caps grid draw at exactly 6.6kW and
  produces `POWER_LIMITED` with real unmet load; raising the service to 100A permits
  proportionally more draw; the cap never causes a Phase 3 no-export violation; and in UTI mode
  the cap always exhausts battery-charging headroom before ever leaving house load unmet.
- `InspectPanel`'s Grid detail sheet now shows the configured service limit (amps + kW) and its
  "demand exceeds supply" note distinguishes an actual outage from hitting the utility service
  cap while still connected — two different real-world causes that look identical in the raw
  numbers but aren't the same problem.

Remaining phases (further energy-flow animation polish, final validation against the spec's 20
test scenarios) are tracked but not started as of this commit.
