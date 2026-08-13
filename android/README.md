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

## Post-build field fixes (Samsung Galaxy A15 + simulation logic)

The app was first successfully built and run on a physical device (Samsung Galaxy A15) after
the sandbox-only development above. Real-device testing surfaced issues this environment's
lack of an Android SDK/emulator couldn't have caught. Fixes landed in six phases, each verified
by code review + a standalone scratch JVM project exercising the pure-Kotlin domain logic (this
sandbox still can't compile/run the actual Android app). Phase 6 is the final validation pass
against the original spec's own TEST 1-20 — see below. Real-device re-confirmation on the A15,
especially of TEST 6, is the one thing this sandbox genuinely cannot do itself.

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

**Phase 5 — energy-flow animation correctness** (`ui/simulation/EnergyFlowCanvas.kt`,
`EnergyGraph.kt`):

Most of "correct energy-flow animation" was already delivered as a side effect of Phase 3's
domain-layer rewrite — the 4 fixed paths needed no new geometry, `EnergyFlowResolver` already
combines solar+grid battery-charging onto the one physical path and gridToHouse+gridToBattery
onto the one physical grid path, and `grid_inverter` was already marked import-only
(`bidirectional = false`). Phase 5 closes the two gaps that were left:

- **The `bidirectional` flag was metadata nobody read.** `EnergyFlowCanvas`'s particle renderer
  now actually enforces it: a flow reporting `REVERSE` on a path not marked `bidirectional`
  (which should never happen, but previously had nothing stopping it from rendering if it did)
  is now simply not drawn, rather than animating a physically-impossible direction — e.g. an
  export-looking glow on the strictly-import-only grid path. This turns the flag from
  documentation into an enforced invariant.
- **The 24h Energy Graph had no grid line.** It plotted Solar/Consumption/Battery% but nothing
  for JPS activity, even though grid import (including the new battery-charging sub-flow) is now
  a first-class, sometimes-load-bearing part of the day. Added a dashed amber grid trace (using
  `SimFrame.gridPowerKw`, always ≥0 per the no-export rule) plus a legend entry, and folded it
  into the graph's Y-axis scaling so a service-capped day doesn't clip off-chart.

Verified standalone: extended the Phase 3 EnergyFlowResolver checks from hand-picked single
frames to every frame of six full-day timelines (SOL, SBU, UTI, UTI-no-grid-charge, and both
service-cap scenarios) — every active flow maps to a real fixed path, `REVERSE` never appears on
`grid_inverter` in a single frame across any of them, and the resolver/paths system stays
internally consistent for a whole simulated day, not just the moments already covered by
targeted test cases.

**Phase 6 — final validation against the spec's own TEST 1-20** (`domain/simulation/SimulationEngine.kt`,
`ui/simulation/InspectPanel.kt`):

The original 34-section spec ended with 20 explicit test scenarios. Went through each against
the current implementation (code review, not on-device — this sandbox still can't run the app):

| # | Scenario | Result |
|---|---|---|
| 1 | New estimate → Customer details appear first | ✅ Phase 2 |
| 2 | Bill field defaults to $16,000 | ✅ Phase 2 |
| 3 | Backup defaults to 12 hours | ✅ Phase 2 |
| 4 | Backup coverage defaults to MOST LOAD | ✅ Phase 2 |
| 5 | PSH defaults to 5.5 hours | ✅ Phase 2 |
| 6 | Samsung A15: all buttons visible | ⚠️ Fix applied (Phase 1's root-cause `Scaffold` inset fix) but never re-confirmed on-device after that specific fix — please re-check |
| 7 | SBU + daytime: solar supplies load first | ✅ Phase 3 |
| 8 | SBU + no solar + battery 80%: battery supplies load | ✅ Phase 3 |
| 9 | SBU + battery reaches 20%: battery stops, utility takes over | ✅ Phase 3 (20% DOD cutoff) |
| 10 | Utility active: supplies house AND charges battery simultaneously | ✅ Phase 3 (`gridToBatteryKw`) |
| 11 | Utility current limit: 30A | ✅ Phase 4 (configurable, enforced) |
| 12 | UTI mode: utility has priority | ✅ Phase 3 |
| 13 | SOL mode: solar has priority | ✅ Phase 3 |
| 14 | Battery charging → particles INVERTER→BATTERY | ✅ Phase 3/5 |
| 15 | Battery discharging → particles BATTERY→INVERTER | ✅ (pre-existing, unaffected) |
| 16 | Utility importing → particles JPS→INVERTER | ✅ Phase 3/5 |
| 17 | Utility exporting → particles INVERTER→JPS | ❌ **Deliberately not implemented** — see below |
| 18 | House supply → particles INVERTER→HOUSE | ✅ (pre-existing, unaffected) |
| 19 | No power → no particles, routes stay visible | ✅ (pre-existing: routes are baked into the background image, not drawn by the particle layer) |
| 20 | High-load appliance ON → animation changes immediately | ✅ (pre-existing reactive `StateFlow` pipeline, unaffected) |

**TEST 17 is an intentional deviation, not a bug.** The spec's own SOL-mode example (§24) and
this test both describe conditional grid export ("`INVERTER → JPS` if grid export is enabled").
Partway through this work the user gave an explicit, direct correction — *"we do not send power
to the grid when only take power from grid"* — which overrides that part of the original spec.
Phase 3 removed export capability from the domain model entirely (no `solarToGridKw` field, no
`EXPORTING_TO_GRID` status, no code path that can produce a negative `gridPowerKw`), so TEST 17
cannot pass and was never intended to after that correction. Documenting it here rather than
silently dropping it, since it's a real, deliberate divergence from the written spec.

Two small gaps found and fixed during this review pass, both real correctness issues rather than
missing features:
- The Battery inspect sheet's "Estimated runtime" figure was still computing against a
  hardcoded 10% reserve floor — a leftover from before Phase 3 raised the actual engine cutoff
  to 20% (spec §12/§27). It was quietly overestimating remaining runtime ever since. Fixed by
  making `SimulationEngine.BATTERY_MIN_SOC_FRACTION` public and having the UI read it directly,
  so the two can't drift apart again.
- Added an explicit "Reserve cutoff: 20%" row to the same sheet — spec §27 asks for the cutoff
  to be shown alongside SOC, and it wasn't displayed anywhere.

Everything else in the original 34-section spec not covered by the numbered tests above
(mobile-first sizing, progressive disclosure over scrolling, the `InverterMode` enum shape,
named fixed paths, bidirectional particle handling, cloud/appliance reactivity) was cross-checked
against the corresponding phase's own verification and is covered.

This closes out the A15 field-fix pass. Everything above was verified through code review and
the standalone scratch JVM project (this sandbox has no Android SDK); real-device confirmation
on the Samsung Galaxy A15 — especially TEST 6 — is the one remaining step.

## Second round of field feedback (A16)

After the A15 pass above, further on-device testing surfaced two more real bugs and three
feature requests. Verified the same way as the whole post-build arc: code review + the
standalone scratch JVM project for anything with testable domain logic (still no Android SDK
in this sandbox).

**Bottom nav label clipping** (`ui/components/FloatingBottomNav.kt`): six tabs (Home / Estimate
/ Site / Systems / Savings / Profile) in a fixed-height pill left roughly 50dp per tab, and the
label `Text` had no `maxLines`/overflow guard — "Systems", "Savings", and "Profile" could wrap
to a second line and blow out the pill's fixed height, clipping. Fixed with a deliberately small
fixed label size, `maxLines = 1` / `softWrap = false` / ellipsis as a hard guarantee it can never
wrap, and tighter internal paddings.

**Sun dial direction** (`ui/simulation/TimeDial.kt`): `hourToAngleDeg` mapped midnight to the
top of the dial and noon to the bottom — backwards from the "sun is highest at the top" mental
model, and inconsistent with the separate `SunIndicator` overlay's own sunrise-left/sunset-right
convention. Shifted the mapping so noon sits at the top, midnight at the bottom, sunrise on the
left, sunset on the right; verified numerically against the dial's own coordinate math
(`angleToOffset`) rather than just eyeballing it.

**Itemized simulation efficiency/loss model** (new `domain/simulation/SystemLosses.kt`): solar
production previously had no real-world losses applied at all — nameplate capacity times
irradiance, full stop. Added, as separate named factors (per the "itemized, not blended"
choice): panel temperature derating (an NOCT-based cell-temperature estimate against a modeled
Jamaica ambient-temperature curve, ~-0.4%/°C above the 25°C STC reference — genuinely meaningful
in a tropical climate) plus fixed inverter conversion, DC wiring, and soiling/availability
efficiency factors. `SimFrame` gained `potentialPvKw`/`cellTempC`/`temperatureDerateFraction` so
the loss-free "ideal" output stays visible alongside the realized figure, surfaced in both the
Technical readout and the Panels inspect sheet. Verified standalone that actual output never
exceeds the ideal, cell temperature is realistically hotter at noon than midnight, and the
actual/potential ratio exactly equals `fixedSystemEfficiency × temperatureDerateFraction`
whenever the inverter's own rated-power ceiling isn't independently clipping both sides (and
separately confirmed that when it does clip — an intentionally oversized array — that's the
expected behavior, not a bug).

**Appliance quantity + multi-block time scheduling** (`domain/simulation/SimAppliance.kt`,
`ui/simulation/AppliancesSheet.kt`): replaced the flat per-appliance on/off toggle with a real
schedule model per the user's own example — "3 units running in the day, 3 more at night, each
with its own set time." Every appliance now has a quantity and one or more `ApplianceRun` blocks
(quantity + start time + duration); a "+ Add time block" control in the Appliances sheet adds
more. `SimulationEngine.buildDayTimeline` computes appliance load per-hour from these schedules
rather than one constant added to the whole day. A fresh appliance defaults to a single all-day
run, so nothing regresses until someone actually customizes a schedule. Verified standalone with
the literal 3-day/3-night example: each block contributes only its own quantity's load during
its own window, zero load between windows, correct wraparound past midnight, and the two blocks'
quantities never double up.

**Wizard step splitting** (`ui/wizard/`): per the "pages, not scrolling" preference, split the
three step groups the user pointed at into nine focused, one-concept-per-screen steps: "Mode &
Site Info" → **Quote Mode** + **Property & System**; "Roof & Mounting" → **Roof Type** +
**Roof Mounting** (now its own step, only shown for zinc/metal roofs, instead of a conditional
section buried in a longer screen); "Loads" → **Air Conditioning** + **Appliances**; "Manual
System Builder" → **Manual Mode** + **Inverter & Panels** + **Battery Bank**. The wizard is now
13 steps (up from 8): 1 Customer, 2 Quote Mode, 3 Property & System, 4 Roof Type, 5 Roof
Mounting (ZINC only), 6 Air Conditioning, 7 Appliances, 8 JPS Bill/Usage (GUIDED only),
9 Backup Requirements, 10 Manual Mode (MANUAL only), 11 Inverter & Panels (MANUAL only),
12 Battery Bank (MANUAL only), 13 Pricing & Discount. `WizardViewModel.visibleSteps()` gates the
three newly-conditional steps (5, and the 10-12 trio) the same way it already gated JPS Bill/
Usage; validation stayed attached to its semantic function (`manualErrors` now fires on step 11,
Inverter & Panels, where the panel-count field it actually checks lives) rather than being
re-derived from scratch, so the existing step-reorder discipline from the A15 pass held. The
four old combined step files (`Step1SiteInfo.kt`, `Step2Roof.kt`, `Step3Loads.kt`,
`Step6Manual.kt`) were deleted outright rather than left as dead code, since every line of their
content now lives in one of the nine new files.

## A17: replaced Google Maps with OpenStreetMap (no API key required)

The Solar Site map screen (`site/map/SolarSiteMapScreen.kt`) was built on the Google Maps SDK
back in the original build, which needs a `MAPS_API_KEY` set in `android/local.properties` to
render any tiles at all — left blank (the out-of-the-box state for anyone who hasn't set one
up), the map area was always going to be blank/error tiles. The user asked for it to just work,
no API key, via OpenStreetMap or similar. Replaced entirely:

- **Street tiles**: OSM's own Mapnik tile servers (`TileSourceFactory.MAPNIK`), osmdroid's
  built-in default — no key, no setup.
- **Satellite tiles**: Esri's free public World Imagery REST endpoint
  (`server.arcgisonline.com/.../World_Imagery/MapServer/tile/{z}/{y}/{x}`), wired up as a custom
  osmdroid tile source (`esriWorldImagery` in `SolarSiteMapScreen.kt`) — also no key. Roof
  tracing fundamentally needs to see the actual building outline, which a street map alone can't
  show, so keeping a working satellite view was the priority, not just any keyless map.
- The map-type toggle is now two states (`SiteMapType.STREET` / `SATELLITE`) instead of three —
  OSM has no built-in "hybrid" (satellite + labels) tile source the way Google Maps does, and
  compositing one from two tile layers wasn't worth the complexity for what this screen needs.

osmdroid has no first-class Jetpack Compose bindings (unlike `com.google.maps.android:maps-compose`,
which is gone). `SolarSiteMapScreen` now hosts osmdroid's classic `MapView` via `AndroidView`,
managed imperatively: overlays (the selected-location marker, saved roof-plane polygons, the
roof currently being traced) are rebuilt from Compose state inside `AndroidView`'s `update`
lambda; one-shot camera commands (zoom in/out, pan-to-search-result, pan-to-my-location) go
straight to the held `MapView` reference via a `MapEventsOverlay` for tap handling and
`mapView.controller` for camera movement. `MapController` now tracks `selectedLocation` as the
app's own domain `GeoPoint` type rather than an SDK-specific one, decoupling it from whichever
map engine is underneath.

**Dropped, not replicable**: the 3D tilt toggle (`MapController.is3D`, the `ThreeDRotation`
button) is gone. Google Maps' camera could tilt to a near-oblique angle over 3D building/terrain
data; osmdroid is a pure 2D tile renderer with no equivalent camera capability, so faking this
would mean lying about what the map can do rather than a genuine feature parity gap to close.

**Setup got simpler**: no more `local.properties` API key step for the map to work at all —
`android/local.properties` is no longer referenced anywhere in the Gradle config. osmdroid's own
usage policy requires a real user-agent string and a sane tile cache location (both set once, in
`LumixApp.onCreate()`), which needs no manual setup either.

This is real Android-SDK/platform-library code this sandbox has no way to compile against (no
Android SDK here, and no network access to fetch/inspect the actual osmdroid artifact) — the
osmdroid API surface used (`MapView`, `MapEventsOverlay`, `Marker`, `Polygon`,
`OnlineTileSourceBase`, `MapTileIndex`) was written from a well-established, stable, long-standing
library API rather than verified by a build. Please pull and report back the exact Gradle/compile
error if anything doesn't build — that's the fastest way to pin down any API mismatch precisely.

## A18: map removed entirely (deferred to a future upgrade)

Immediately after A17, the user asked to remove the map altogether rather than keep debugging
it — it'll come back in a future upgrade rather than staying half-working in the meantime.
Removed cleanly:

- `site/map/SolarSiteMapScreen.kt`, `site/map/MapController.kt`, and
  `site/map/RoofDrawingController.kt` (the whole `site/map/` package) — deleted outright, not
  disabled/hidden behind a flag.
- The `site/map` nav route and its `SolarSiteMapScreen` wiring in `LumixNavHost.kt`.
- The "Use Satellite Map" entry option on the Site tab's landing screen
  (`SolarSiteEntryScreen.kt`) — manual entry is now the only way in, shown as a single full-width
  card rather than two side-by-side options, with copy updated to match ("Record a customer's
  roof..." instead of "Map a customer's roof...").
- The `org.osmdroid:osmdroid-android` Gradle dependency and its one-time `Configuration` setup
  in `LumixApp.onCreate()`.

**Kept, deliberately, as reusable infrastructure**: `DeviceLocationManager.kt`,
`sensors/CompassManager.kt`, `network/NetworkConnectivityObserver.kt`, and
`site/SolarCompassBadge.kt`. These are small, generic, already-correct utility classes (device
GPS location, magnetic compass heading, connectivity observation) that only ever had the map
screen as a caller — with no live caller now, they're inert, not broken or confusing, and
rewriting them from scratch when the map returns would just be wasted duplicate work. The
`play-services-location` dependency stays for the same reason (`DeviceLocationManager` still
compiles against it). `sensors/CompassMath.kt` was never map-specific to begin with — it's still
actively used by the digital-twin simulation's Sun & Roof card.

`ManualSiteScreen.kt`'s own doc comment (previously describing itself as "a peer path to
SolarSiteMapScreen, not a fallback") was updated to reflect that it's now the sole path into
Solar Site, not a fallback with nothing to be a peer to.

## A19: particle/wire alignment fix + full-scene weather & time-of-day atmosphere

Two fixes to the digital-twin Simulation screen (`EnergyFlowCanvas.kt` and friends):

**1. Electrons no longer drift off the printed wires.** `SolarSimulationPaths.kt`'s four
`EnergyPath` point lists (solar→inverter, grid→inverter, inverter→battery, inverter→house) were
hand-eyeballed against the `bg_house_energy_routes.png` artwork and had drifted up to ~90px off
the actual printed neon wires in spots — worst on the grid→inverter (red) path, which runs the
longest, most bent route across the image. Recalibrated by directly sampling the PNG's own pixel
data: thresholded each wire's color (yellow/red/green/blue) against its background, traced the
resulting pixel clusters into a polyline per path, then verified by rendering the new normalized
points back onto the image as connected dots and visually confirming every dot lands on the
printed wire. The grid→inverter path in particular grew from 4 points to 14 to actually follow
its zig-zagging real route along the wall, instead of cutting a straight line through it.

**2. The house scene itself now visibly reacts to weather and time of day**, not just the small
sun-icon glow and cloud puffs it did before — "sunny" at midday, dimmer/greyed-out under cloud,
a rain wash + falling streaks during Storm weather, and a dark, cool-toned night once the sun
sets, matching whatever hour the time dial or What-If actions land on. New
`SceneAtmosphereOverlay` composable (`EnvironmentOverlays.kt`) draws three independent washes
over the *entire* photo (walls and ground, not just the sky the existing `CloudOverlay` blobs
sit in):
- An overcast grey-down scaled by `cloudCoverage` (same value already driving the sky's cloud
  puffs), so a cloudy midday visibly dims the whole scene, not just the sky.
- A cool, dark gradient scaled by a new `daylightFactor` param — the engine's own undamped
  `SimulationEngine.irradianceFactor(hour)` curve (0f outside daylight hours, rising to ~1f at
  midday) — so the scene's darkness always tracks real solar output hitting zero, and fades in
  smoothly around sunrise/sunset rather than snapping.
- A semi-transparent rain wash plus ~46 animated falling streaks, shown only when
  `state.weather == WeatherState.STORM` (the same state the "Simulate Cloud Event" What-If
  action already sets) — off by default, respects reduced-motion the same way the particle
  overlay does.

`EnergyFlowCanvas` gained two new params (`daylightFactor`, `isStorm`, both defaulted so the one
call site in `SimulationScreen.kt` is the only thing that needed updating) and now draws
`SceneAtmosphereOverlay` immediately after the background image, before the existing cloud/sun/
battery/particle layers — so those stay legible and un-dimmed on top of it rather than getting
washed out themselves.

## A20: One UI restyle, Solar Site removed entirely, new Settings tab, appliance picker rework, battery math verified

A large, multi-part round: a One UI-inspired shell restyle, full removal of the Solar Site
feature (not just the map — everything, per explicit request), a real Settings tab in its place,
a fix + redesign of the appliance scheduling sheet, and a verification pass on the battery's
load-response physics.

**1. Solar Site removed entirely — tab, screens, domain, and every integration point.**
Unlike A18 (which removed just the map UI and deliberately kept some infrastructure "for when
the map returns"), this is a full removal with nothing kept dormant, since the plan changed to
not bringing Solar Site back. Deleted outright: the whole `site/` package (20 files — entry
screen, manual entry, detail screen, view model, repository, roof-plane/panel-layout/shading UI,
`geometry/` roof-geometry engine + panel-packing optimizer + roof-score calculator), plus
`solar/` (`SolarPositionCalculator`, `SolarIrradianceModel`, `ClearSkyPshEstimator`,
`SolarPathSampler`, `SolarResourceProvider`, `SolarPosition`) and `sensors/`
(`CompassManager`, `CompassMath`) and `location/DeviceLocationManager.kt` and
`network/NetworkConnectivityObserver.kt` — all of these had zero remaining callers once Solar
Site itself was gone, so A18's "keep it inert for later" reasoning no longer applied. Also
removed: the `ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION`/`INTERNET`/`ACCESS_NETWORK_STATE`
manifest permissions and the `play-services-location` Gradle dependency, since nothing in the
app talks to a network or reads device location anymore.

Surgical edits (keep the file, remove only the site-specific parts) rather than deletions:
- `domain/QuoteInputs.kt`: removed the `RoofConstraint` data class and its `roofConstraint`
  field on `QuoteInputs`.
- `domain/QuoteResult.kt`: removed `energyOptimalPanelCount`, `energyOptimalPvKw`,
  `isRoofConstrained`.
- `domain/SystemCalculator.kt`: removed the roof-constrained panel-count cap block.
- `domain/simulation/SimSystemConfig.kt`: removed `siteLatitude`/`siteLongitude`/
  `roofAzimuthDegrees`/`roofPitchDegrees`/`isSiteAware` — confirmed via full-codebase search that
  these were populated *exclusively* from the site "Use This Roof" flow and the plain wizard has
  no roof-facing/pitch entry at all, so there was no general-purpose use to preserve.
  `SimSystemConfig.from()` also dropped its now-unused `inputs: QuoteInputs` parameter.
- `domain/simulation/SimulationEngine.kt`: removed `sitePosition()`/`sitePlaneOfArrayFactor()`
  and the `siteFactor` conditional in `buildDayTimeline` — the generic, location-agnostic
  irradiance model is now the only model, same as it always was for non-site-aware quotes.
- `ui/simulation/SimulationScreen.kt`: removed the "Sun & Roof" card and its `config.isSiteAware`
  gate.
- `ui/results/ResultsScreen.kt` and `pdf/QuotePdfGenerator.kt`: removed the roof-constrained
  warning banner/PDF block (and PDF's now-unused `warningPaint`/`wrapText` helper).
- `ui/wizard/WizardViewModel.kt` and `ui/nav/LumixNavHost.kt`: removed
  `startWithRoofConstraint()`, the `site/*` routes, the Site tab, and the `SolarSiteViewModel`
  wiring.

**2. One UI-inspired shell restyle** (design tokens + shared shell components, not a full
per-screen rewrite — the existing corner-radius/spacing scale was already fairly generous, so
the main gap was structural). New `LargeTitleTopBar` component
(`ui/components/LargeTitleTopBar.kt`): a big, bold, left-aligned title sitting directly on the
screen background — no bar fill, no shadow — replacing Material's small centered `TopAppBar`
across every top-level tab screen (Home, Estimate, Systems, Savings, Settings). Supports an
optional `onBack` for the rare screen (`HistoryScreen`) also reachable as a pushed route. Home's
existing time-of-day greeting text now lives in the header's subtitle instead of a separate body
item.

**3. New Settings tab, replacing Profile** (which was really just the price editor under a
different name). `ui/settings/SettingsScreen.kt` consolidates:
- **Price list** — the same regular/discount editor from the old `PriceSettingsScreen.kt`,
  folded in as one section.
- **Appearance** — Light/Dark/System theme, a real in-app override on top of the existing
  light+dark color system (previously it only ever followed the OS setting). New
  `SettingsRepository` (DataStore-backed, same pattern as `PriceRepository`) persists it;
  `MainActivity` reads it and passes the resolved `darkTheme` boolean into `LumixTheme`.
- **Simulation defaults** — technical-mode-by-default and default grid service amps, both also
  in `SettingsRepository`, read once by `SimulationViewModel.load()` so every new simulation
  starts from the saved preference instead of a hardcoded value.
- **Data management** — "Clear quote history," behind a confirmation dialog, backed by a new
  `QuoteDao.deleteAll()` / `QuoteRepository.clearAll()`.

The bottom nav drops from 5 tabs (Home/Estimate/Site/Systems/Savings/Profile was 6, already
fixed for clipping in A16) to 5 (Home/Estimate/Systems/Savings/Settings) — Site removed, Profile
renamed and expanded in place, so no new clipping risk.

**4. Appliance sheet: fixed the scroll bug and reworked the picker.** The bug: the sheet's outer
`Column` had no scroll modifier at all — with 14 appliances (some expandable), content taller
than the sheet's viewport simply couldn't be reached past whatever fit on screen. Fixed by adding
`.verticalScroll(rememberScrollState())`.

The picker itself: replaced the old free-form "+ Add time block" editor (arbitrary start
hour/duration blocks) with a fixed 3-chip Morning/Noon/Night control per appliance, plus a shared
quantity and hours-per-period. Any combination can be selected — one, two, all three (which,
since each period's own hours field clamps to that period's real span, approximates "runs all
day"), or none, which is now *the* off state — there's no separate on/off switch to fall out of
sync with it anymore. New `DayPeriod` enum (Morning 6am–12pm, Noon 12pm–5pm, Night 5pm–6am,
wrapping past midnight) and `buildApplianceSchedule()` translate the picker's
quantity/hours/periods into the domain's existing `ApplianceRun`/`ApplianceState` shape — that
underlying model didn't need to change at all, since it already supported arbitrary scheduled
windows; only the UI construction changed.  `SimulationViewModel` gained
`setApplianceSchedule()` replacing the old `toggleAppliance()`/`setApplianceRuns()` pair.

**5. Battery charge/discharge response to load: verified, not changed.** Built a focused
standalone verification (pure-Kotlin copy of `SimulationEngine`/`SimFrame`/`SimSystemConfig`/
`SimAppliance` in a scratch JVM project) with five scenarios: more load at midday visibly slows
`solarToBatteryKw`; a big enough load flips `batteryPowerKw` from positive (charging) to negative
(discharging) with `batteryToHouseKw` turning on; less load reaches a higher SOC by the same
hour (faster charging); more nighttime load discharges faster (lower SOC by the same hour); and
scheduled appliance runs (the new picker's mechanism) drive the same response as the legacy flat
load parameter. All five passed against the existing, unmodified energy-balance code in
`buildDayTimeline` — the physics described in the request (more load → slower charge → eventual
discharge; less load → faster charge) was already fully implemented via the per-timestep
solar-then-battery-then-grid priority order and rate/capacity-limited charge/discharge math; nothing
in the engine needed to change. One real pitfall surfaced during verification and is worth
recording: a naive test comparing SOC at a fixed hour across two load scenarios can silently pass
or fail for the wrong reason if either run has already saturated at the battery's 100% ceiling or
20% reserve floor by that hour — several early scenario attempts hit exactly this before
adjusting starting SOC/battery size to leave headroom on both ends.

## A21: battery power curve, SOL/SBU split, time slider, appliance minutes, wizard review step, results/savings rebuild

A large round implementing a detailed spec covering the simulation's battery physics, its time
controls, appliance scheduling, and the estimate wizard's late steps. Before touching anything,
verified what the spec assumed was missing was often already true — the engine already
integrates energy as power×dt at 5-minute resolution (not "SOC += fixed percentage"), already
clamps at the 20%/100% floor/ceiling, and `SimSystemConfig.from(result)` was already the sole,
demo-value-free bridge from the calculated quote into the simulation. What genuinely didn't
exist is documented below.

**1. Battery charge/discharge power curve.** `SimSystemConfig` gained
`batteryMaxChargeKw`/`batteryMaxDischargeKw`/`batteryChargeEfficiency`/
`batteryDepthOfDischargeFraction` (derived from the quoted battery+inverter — 0.5C typical
LiFePO4 rate, capped by the inverter — this catalog has no per-model rate spec to read a real
one from instead) so the engine reads these instead of hardcoding its own copies. New
`BatteryPowerCurve` object tapers both rates by current SOC: full rate below 80%, stepping down
through 90%/95%, to 10% of rated power right at the ceiling — and mirrored on the discharge side
relative to the 20% reserve floor rather than absolute zero. Verified via the standalone
harness: the curve strictly decreases in both directions, and a battery starting at 99% SOC
genuinely charges at a fraction of its rated 5kW, not the flat max.

**2. SOL and SBU inverter modes now actually differ.** The engine's own prior doc comment said
so explicitly: "SBU: functionally the same priority as SOL." Now SOL is solar+battery only —
JPS is never touched even when connected, so once the battery hits its floor any remaining
deficit goes unmet, like a genuine off-grid system — while SBU keeps the same solar→battery
priority but falls back to JPS once the battery is drawn down. Verified: at the reserve floor
with no solar, SBU imports from JPS and reports zero unmet load; SOL imports nothing and reports
the full deficit as unmet.

**3. Time dial replaced with a horizontal slider + corner clock.** `TimeDial.kt` (the circular
drag dial) is deleted; new `TimeSlider.kt` sits directly under the simulation scene showing the
current time, a "battery full at…" marker, a 0–24h slider, and tick labels at
00/06/09/12/15/18/21/24:00. `formatSimTime` moved into this new file (still used by
`AppliancesSheet`/`EnergyGraph`). `EnergyFlowCanvas` gained a `simTimeText` param rendering a
subtle time chip in the scene's top-right corner (sky, never over equipment). `TransportBar`'s
speed options changed from 0.5/1/2/4× to 1/2/5/10× — the play loop already only ever accelerates
the simulated-hour clock (`simHours = speed × elapsedRealSeconds / 60`), never touches the
energy math directly, so 10× needed no engine change at all.

**4. Appliance runtime supports minutes, and defaults are realistic per-appliance schedules.**
The picker's "hours" stepper now steps by 5 minutes below an hour and 30 minutes above it, down
to a 5-minute floor (`MIN_RUN_HOURS`), displayed as "10min"/"1h 30m" rather than always "%.1fh".
New `defaultScheduleFor(SimApplianceType)` in the domain layer replaces the old "every appliance
defaults to one all-day run" behavior with real per-type timing — lights get a brief pre-dawn
window plus dusk-through-bedtime, the fridge stays all-day (a cycling compressor's average draw
really is close to constant, unlike the others), TV/AC get evening windows, microwave/iron/pump
get genuine ~10–15 minute events, water heater gets a morning+evening pair, and so on — all
labeled as defaults and fully editable. `defaultApplianceStates()` now seeds every appliance's
`runs` with this schedule (scaled to the wizard's quantity) even while off, so turning on a
previously-off appliance (water heater, oven, etc.) via a period chip starts from its real
duration instead of a generic 13-hour block. Verified: a 1200W microwave run for 10 minutes
computes to ~0.20kWh, not rounded up to a 30-minute block.

**5. Wizard: "3 Rail" step removed, new System Review step added, a real bug fixed.** Deleted
`StepRoofMounting.kt` (the ZINC-only "use center rail" toggle) — zinc roofs now always use 3
rails/row (its own prior default), matching the guidance that most roofs need it, without asking.
All 13 step slots renumbered; `WizardViewModel.visibleSteps()`/`errorsForStep()` and
`WizardScreen`'s title map/dispatch updated to match. While renumbering, fixed a real bug the
same code surfaced: JPS Bill/Usage was only excluded from the step count for LOAD mode, but only
ever *rendered* for GUIDED mode — MANUAL mode showed it in the step count and let you navigate to
a blank page. Now excluded for both LOAD and MANUAL.

A new step 12, **System Review** (`StepSystemReview.kt`), was inserted right before Pricing &
Discount — a real engineering review, not a summary. It recomputes a live preview via
`SystemCalculator.calculate()` (pricing isn't needed for this, so `PriceList.DEFAULT` stands in)
and shows Load/Solar/Inverter/Battery/Grid sections with real figures, four pass/warn engineering
checks (inverter capacity, battery capacity vs. requested backup, battery discharge power vs.
peak load, PV array vs. inverter input limit), and a design-confidence percentage — 5 binary
signals (customer details present, location present, usage grounded in a real kWh reading rather
than the bill-mode placeholder, PSH confirmed away from the generic 5.5h default, exact hardware
model chosen) — explicitly labeled as a data-completeness indicator, not a certification. Grid
voltage/frequency/current-limit are shown as Jamaica's fixed residential-service reference values
(220V/110V split-phase, 50Hz, 30A) rather than fake-editable fields, since nothing in the domain
currently varies them per quote.

**6. Results screen rebuilt into clean separated sections.** The old hero card mixed the PV
number, panel count, inverter name and battery capacity into one run-on paragraph. Now: a
**System** card (PV/Inverter/Battery as clean rows), a **Performance** card (daily/annual solar,
monthly savings, payback, coverage ring), a new **Backup** card (target vs. estimated backup
hours, with a pass/warn status line — this data didn't exist on the results screen at all
before), the existing energy-flow diagram/roof visualization/20-year graph, and a new **Cost**
summary (Equipment/Installation/Discount/TOTAL) ahead of the existing detailed material
breakdown table.

**7. Savings screen: year-15 slider + editable financial assumptions.** New slider (default:
year 15) scrubs through `SavingsProjection.yearly` (already a real 20-year, degradation- and
escalation-compounded series — this data already existed, it just weren't exposed year-by-year
in the UI) showing that year's savings/cost figures prominently, above the existing full 20-year
graph. `SavingsCalculator.project()` gained optional `billEscalationRate`/`panelDegradationRate`
params (defaulting to the existing 6%/0.5% constants, so both other call sites needed no changes)
now sourced from two new `SettingsRepository` fields, editable in a new "Financial assumptions"
Settings section, labeled ESTIMATE.

## A22 — Premium UI overhaul

A visual/interaction-layer pass toward a "premium energy operating system" feel — near-black
graphite surfaces, large dominant numbers, almost no borders, one elegant status statement
instead of competing badges. No domain/simulation/pricing logic changed in this round; every
edit is in `ui/theme` or a `ui/*` screen file.

**1. Design tokens.** `LumixColors` retuned to the requested near-black palette
(`#080A0D`/`#101419`/`#151A20` background/surface/elevated, `#F5F5F5`/`#8E969F` text) and the
five accent hues desaturated toward "restrained instrument-panel" rather than neon (solar amber,
battery green, grid cyan, warning amber, error red — unchanged semantic roles, just quieter).
`SurfaceCard`/`GlassSurface` (`ui/components/Cards.kt`) dropped their default 1dp border — depth
now comes from the surface/elevated tonal step plus a very faint shadow (visible in light mode
only; dark mode relies on tone alone, since shadows barely read on near-black anyway) — a
`bordered` param stays available for the rare control that genuinely needs one. Added
`heroValueStyle()` (`ui/theme/Type.kt`) for the one dominant number per screen, and named
motion-duration tiers (`LumixMotion.DURATION_MICRO/SCREEN/MAJOR` = 180/300/420ms) so future
animation work has a shared vocabulary instead of ad hoc tween values. Font stays the platform
system sans (Roboto) rather than bundling Inter — wiring the AndroidX downloadable-Google-Fonts
provider needs an on-device network fetch plus certificate-hash constants that can't be verified
from this sandbox, and a wrong hash silently breaks font loading at runtime with no build-time
signal, so it was judged too risky to add unverified.

**2. Home dashboard rebuilt as one hero.** Replaced the multi-card layout (potential-card +
snapshot-card) with a single flow: small greeting label + name, then one hero (PV size in
`heroValueStyle`, a status dot, the existing ambient sun/roof visual), then a plain three-stat
row (coverage/battery/monthly savings — no card wrapper), then one CTA. No quote yet shows a
"NO SYSTEM YET" empty state instead of a small inline prompt.

**3. Simulation screen decluttered.** The digital-twin scene is no longer boxed in a
`SectionCard` — it, the time slider, and the inspect chips sit directly on the screen background
so the scene reads as the centerpiece rather than one box among many. The status pill + mode
toggle row was replaced with a single status statement (a colored dot + the existing
`SystemStatus.label`, e.g. "SOLAR POWERING HOME") sized and weighted to read at a glance. The
live Solar/Grid/Home/Battery stat row lost its card wrapper too.

**4. Wizard: selection cards + minimal step indicator.** `StepQuoteMode`'s three-way segmented
button became three large `SelectionCard`s (new reusable component) with a title, a one-line
explanation, and a quiet tint + accent-colored title on selection — no checkmark badge. The
"Step X of Y" progress text was replaced with a `01 / 09`-style numeric indicator next to a
thinned-down (2dp) progress line.

**5. Recommendations + Design Confidence.** New `RecommendationCard` component (cyan-tinted,
"RECOMMENDED" eyebrow, always carries a real action — never a non-interactive warning in
disguise, per the "don't make non-actionable things look like buttons" rule). Wired one real
instance into the Simulation screen: when only lighting is scheduled, it recommends adding
appliance schedules with a working button that opens the existing Appliances sheet. The System
Review step's Design Confidence card now leads with a `heroValueStyle` percentage and uses quiet
dot indicators (green/muted, not ✓/⚠ glyphs) for its five confidence signals, since those are
soft "provided vs. default" cues rather than pass/fail judgments; the genuine engineering
warnings below it now use muted amber instead of red, reserving red for real errors.

**6. Calculation sequence + empty states.** `CalculationSequenceOverlay` (shown while a quote
calculates) replaced its generic spinner with a five-stage checklist (PV / LOAD / BATTERY /
INVERTER / SYSTEM, each pulsing then checking off) ending on a brief "SYSTEM READY" beat before
navigating to Results. The Systems (history) and Savings tabs' empty states were reworded to the
eyebrow-label + headline + button pattern used everywhere else ("NO SYSTEMS YET" / "NO SAVINGS
YET") instead of a soft sentence.

**Out of scope, and why.** The spec's Map/Roof-Trace screens (satellite view, corner-tap
tracing) and a standalone Invoice screen with payment-progress don't exist in this app — both
were deliberately removed in earlier rounds (A18 removed the map, A20 removed Solar Site
entirely) at the user's explicit prior request. Building either from scratch would be new
functionality, not a visual reskin, so this round left them out rather than silently resurrecting
removed features; flagged back to the user instead. A full one-question-per-screen restructuring
of every wizard step (Customer, Property, Roof, AC, Appliances, etc.) and animated horizontal
step-to-step page transitions were also out of scope for this pass — the highest-visibility
screens (Home, Simulation, Quote Mode selection, System Review, calculation sequence) were
prioritized within the round instead of a shallower pass across all ~13 steps.

## A23 — New house diagram artwork + recalibrated energy paths

`bg_house_energy_routes.png` was swapped for a new reference photo (a dusk aerial view with
four white routed lines — pole/meter → inverter, panels → inverter, inverter → battery, plus
dashed pointer lines from four baked "0 W"/"100%" placeholder labels) at a different resolution
and aspect ratio (1173×1341 vs. the old 1536×1024) and, critically, completely different line
geometry — the particle system's normalized coordinates only mean anything relative to one
specific image, so swapping the artwork without recalibrating them would have sent particles
drifting off into empty sky/lawn instead of riding the printed line.

**Extraction method, and two rounds of correction.** Three attempts, each fixing what the last
one got wrong:
1. A per-column/row centroid scan got the *pixels each path belongs to* right but over-fit
   noise (arrowheads, anti-aliasing) into a slightly wavy polyline instead of the artwork's
   actual straight segments, and invented `inverterToHousePath` as a stylized stub toward a
   window that doesn't exist as a line in the artwork at all.
2. Hand-reading each corner's exact pixel coordinates off gridded crops fixed the straightness
   and replaced the invented stub with `gridToInverterPath.points.reversed()` — correct in
   principle (this artwork draws only *one* physical line between the inverter and the street
   side, and in a real installation that conductor genuinely is shared between JPS's incoming
   supply and the house's outgoing load) — but still missed some of the grid route's own bends,
   because that route loops back on itself near the door in a purely decorative "cable slack"
   flourish that's easy to misread by eye (it looks like two crossing lines, not one folded one).
3. Redone with a graph-shortest-path trace: threshold the printed line to a pixel mask,
   skeletonize each isolated line to a 1px centerline, then take the shortest path between the
   line's two known endpoints through that skeleton's own pixel-adjacency graph (Ramer–Douglas–
   Peucker-simplified afterward). Shortest-path naturally resolves straight through the door
   loop's crossing point instead of tracing its decorative detour, without needing to manually
   disambiguate which branch to follow — solving exactly the case eyeballing got wrong. Every
   point in every path is now literal skeleton pixels from the artwork.

All three versions were rendered back onto the source image as colored overlays and visually
compared before the final one was committed.

**What changed:**
- `IMAGE_ASPECT_RATIO` (`EnergyFlowCanvas.kt`) updated to `1173/1341`.
- `solarToInverterPath`, `gridToInverterPath`, `inverterToBatteryPath` rebuilt from precise
  corner coordinates read off the new artwork — particles now ride its actual printed lines.
- `inverterToHousePath` reuses `gridToInverterPath`'s own points, reversed, rather than any
  separately traced or invented line (see above).
- `panelArrayBounds`/`batteryBounds` (used for the sun-glow and battery-fill overlays) re-derived
  from the new artwork's panel and battery-unit footprints.
- The four dashed "pointer" lines from the artwork's own baked labels down to the components
  were deliberately **not** turned into animated `EnergyPath`s — they're annotation lines to a
  number, not real electrical routes, and animating "flow toward a label" would mean inventing a
  flow that doesn't correspond to any real [EnergyFlow]. They stay as static, unanimated parts of
  the image.

**Live wattage + battery percentage.** New `WattageOverlays` composable in `EnergyFlowCanvas.kt`
draws a small opaque chip directly over each of the artwork's four baked "0 W" (and, for
Battery, "100%") placeholders — Grid/Solar/Consumption read straight from the same `EnergyFlow`
list already driving the particles (`grid_inverter`/`solar_inverter`/`inverter_house` power, in
real watts, comma-grouped), so the number and the animation can never disagree; Battery shows a
signed wattage (charging "+", discharging "−") plus the live state-of-charge percentage already
passed into the canvas for the fill overlay. Four new `NormalizedRect`s
(`gridLabelBounds`/`solarLabelBounds`/`consumptionLabelBounds`/`batteryLabelBounds`) mark where
each placeholder sits in the artwork; the chip is positioned at that box's left edge (matching
where the artwork's own icon+text started) and vertically centered on it, rather than sized to
match the box exactly — that box is print-scale (a few dp once fitted to a phone screen), far
too tight for a legible touch-target-adjacent chip.

## A24 — Wattage overlay: erase properly, anchor at the top

Two follow-up fixes to the A23 wattage overlay, plus confirmation the shared-line grid/house
routing already covers what was asked.

**1. Grid-in and house-out already share one line.** `SolarSimulationPaths.kt`'s
`inverterToHousePath.points = gridToInverterPath.points.reversed()` (landed in the same A23
round the artwork was recalibrated) already puts both flows on the exact same printed line:
`grid_inverter` particles (amber) travel pole → inverter, `inverter_house` particles (warm
white) travel inverter → pole/street-side, in opposite directions on that identical polyline.
Whatever mix of solar, battery, and grid is actually feeding the house, `inverter_house`'s
power is their combined total (`solarToHouseKw + batteryToHouseKw + gridToHouseKw`, resolved in
`EnergyFlowResolver`) — one aggregate flow, one shared line, regardless of source mix. No
code change was needed here; included for the record since it's easy to miss this was already
in place from the prior round.

**2. Wattage chips: erase, don't dim — and anchor at the top, not the center.** The chip's
backing went from 55% to 92% opacity — at 55%, the artwork's own bold white digits were still
visible underneath, reading as "written over" rather than replaced. Positioning changed from
"vertically centered on the label box, minus an arbitrary 10dp" to anchored directly at the
box's own top-left corner (`bounds.left`/`bounds.top`, with a few dp of padding) — the same spot
the artwork's icon+"0 W" row occupies, sitting above its unmodified "Grid"/"Solar"/"Consumption"/
"Battery" word underneath. Only the numeric readout is ever touched; the static word labels are
untouched pixels.

**3. Weather and day/night — unaffected, confirmed.** Neither this round nor A23 touched
`WeatherState.kt`, `SceneAtmosphereOverlay`, `CloudOverlay`, or `SunIndicator` — Clear/Partly
Cloudy/Cloudy/Heavy Cloud/Storm and the sunrise-to-sunset daylight curve work exactly as before;
`WattageOverlays` and the recalibrated particle paths are additive layers on top of that
existing pipeline, not a replacement for it.

## A25 — Grid route's self-crossing peak: the real reason it kept looking wrong

`gridToInverterPath` needed a *fourth* correction pass. The ground route isn't a simple V dip —
it's a genuine "W": pole down to a first dip, up to a peak, down into a second (deeper) dip,
then a long gentle rise to the inverter. Critically, that peak sits directly under the
artwork's "Consumption" dashed pointer line, which meets the ground exactly there — the artist
clearly routed it that way on purpose, so a viewer's eye tracing up from "Consumption ⚡0 W"
lands right on the cable. The up-stroke and down-stroke either side of that peak visually cross
each other before reaching it (a decorative cable-slack flourish), which is exactly why every
previous extraction attempt got this specific stretch wrong in a different way:
- The original per-column centroid scan (A23) sampled noise into a wavy line here worse than
  anywhere else on the artwork, since the crossing meant a single column could pick up either
  stroke.
- A hand-read set of corners (A23's first fix) simplified the crossing into a plain V — visually
  clean, but it deleted the peak entirely, leaving the Consumption label's pointer meeting empty
  ground with no cable at that spot.
- A graph-shortest-path trace (A23's second fix — skeletonize the line, then find the shortest
  path between its two endpoints through the pixel graph) fixed the waviness everywhere else,
  but *at a self-crossing curve, shortest-path is structurally the wrong tool*: the crossing
  point is a real shared pixel between both strokes, so the algorithm "cuts through" there
  rather than following the true route up to the peak and back down — the shorter path, but not
  the actual line. This is the version the user caught: the route visually skipped past the
  Consumption pointer's meeting point.

Fixed by reading each of the W's five vertices directly off pixel-gridded crops and confirming
each against raw column/row pixel data at that specific x or y (not a shortest-path search) —
`(0.087, 0.486)` pole → `(0.087, 0.666)` ground → `(0.202, 0.726)` first dip → `(0.315, 0.696)`
peak, under Consumption's pointer → `(0.322, 0.772)` second dip → `(0.516, 0.727)` rise corner →
`(0.518, 0.582)` inverter. Verified by rendering the full path back onto the source photo and
confirming the peak lines up with where the Consumption dashed line actually meets the ground.

## A26 — Consumption ends at the door, not the pole

Follow-up correction to A25, from feedback that the shape is really "a V with a T" — the door
junction (the W's peak, where the Consumption dashed pointer meets the ground) isn't just a
bend the *grid* line passes through on its way to the pole; the artwork treats it as the
meter/consumption point itself, and `inverterToHousePath` reversing the *entire* grid line
(all the way to the pole) meant a "house" particle would visually travel past the door and
continue up toward the utility pole — never actually stopping "at the house."

Confirmed via connected-component pixel analysis that the door junction, the grid route's
second dip, and the inverter are all one continuous printed line (so this is a real T-junction
on a single physical conductor, not two separate lines that happen to cross) — matching a
real service-entrance layout, where the meter/panel sits near the door and grid continues on
to the pole from there. `inverterToHousePath` now uses only the second half of
`gridToInverterPath.points` — `gridToInverterPath.points.drop(3).reversed()`, i.e. from the
inverter back to the door junction only — so a "house" particle starts at the inverter and
ends its trip right at the door, matching the door threshold visible in the artwork, instead
of continuing past it toward the pole. Grid's own particles still run the full pole-to-inverter
span in the other direction. Both flows still share the same physical conductor between the
door and the inverter — they just no longer share the *pole-to-door* stretch, which only ever
carries grid supply, never house consumption headed the other way.

## A27 — Simulation HUD: stat cards, legend, glowing flow lines, battery/system cards

Rebuilt the simulation screen's overlay layer to match a full UI mockup the user provided
(shown inline in chat, not as a file, so this was implemented from visual reading rather than
pixel measurement — see the file-access limitation noted below). The mockup's bottom-nav still
showed a "Site" tab; asked the user directly since Solar Site was deliberately fully removed in
A18/A20 at their own prior request, and a mockup screenshot alone isn't authorization to bring
a removed feature back. Answer: **keep the current 5 tabs, skip Site** — no nav changes made.

**Legend-driven color remap.** `colorFor()` in `EnergyFlowCanvas.kt` now maps each route to the
exact color the user's hand-drawn reference and the mockup's legend both specified: PV→Inverter
yellow (`SolarYellow`), Inverter→Battery blue (`TechnicalCyan`), Inverter→Load green
(`EnergyGreen`), Grid→Inverter pink/magenta — a new `LumixColors.GridMagenta`, since nothing in
the existing palette was actually pink. The battery path previously changed color by flow
direction (green forward / cyan reverse); the legend only lists one blue entry for it, so that
distinction was dropped in favor of matching the legend exactly.

**Full-length glowing dashed lines.** `ParticleOverlay`'s Canvas now draws each active path as a
real line for its whole length — a soft wide low-alpha glow stroke plus a crisper dashed stroke
whose phase animates with the same signed speed driving the particles — instead of relying
solely on the baked artwork's printed white line as the only visible "conduit." Particles still
ride on top of it exactly as before. An active flow below the particle-count threshold (very
low but nonzero power) now still shows the glowing line even with zero visible particles, which
reads correctly — there genuinely is current flowing, just not enough to warrant particles.

**Flow label chips.** The old `WattageOverlays`/`WattageChip` (which erased the artwork's own
baked "0 W" placeholder text with a plain number) is now `FlowLabelChips`/`FlowChip`: same exact
pixel-measured bounds (`gridLabelBounds`/`solarLabelBounds`/`consumptionLabelBounds`/
`batteryLabelBounds`), but each chip now also shows an icon, a "Grid → Inverter"-style route
label, and a small color dot matching that route's legend color — matching the mockup's
"floating labeled chips on each line" without inventing new, unverified chip positions.

**HUD cards.** Added `HudOverlay`, corner-anchored over the scene so nothing sits on the house
or equipment itself: `TopStatRow` (Solar/Grid/Home Load, top-left), `TimeModeCard` + `LegendCard`
(top-right — simulated clock, active inverter mode as "SBU Mode" etc., and the 4-entry color
legend), `BatteryCard` (bottom-left — percent + kWh + charging state, only when the system has a
battery), and `SystemSummaryCard` (bottom-right — compact Solar/Home Load/Battery/Grid table).
Every number comes from the same `flows`/`SimFrame` state already driving the particles and the
existing below-canvas `LivePowerRow` — nothing here is fabricated. `EnergyFlowCanvas` gained two
new parameters to support this: `batterySocKwh` (for the battery card's kWh line) and
`inverterModeLabel` (for the time/mode card; passed only when `config.gridConnectable`, since
SOL/SBU/UTI mode is meaningless for a system with no grid connection). The existing below-canvas
`LivePowerRow` in `SimulationScreen.kt` was left in place rather than removed — the mockup shows
its own equivalents floating on the image, but nothing indicated the existing row should be
deleted, and keeping it is the non-destructive choice.

**Known limitation, stated plainly:** images pasted inline in chat (as opposed to genuinely
uploaded files) are visible to the assistant but are not saved anywhere a script can read them —
checked again this round, same result as A24-A26. That means this mockup's exact pixel
positions, spacing, and typography could not be measured or verified the way the base artwork
(a real upload) was; the layout above is a faithful best-effort reading of the mockup's
structure and content, not a pixel-matched reproduction. If further precision corrections are
needed, the mockup would need to be attached as an actual file upload.

## A28 — Split the shared grid/house conductor into two independent lines

Follow-up to A27, prompted by a detailed reference spec listing four *fixed, independent* paths
(pink Grid→Inverter, yellow Solar→Inverter, green Inverter→Load, blue Inverter→Battery) with an
explicit "no shortcuts, no shared/alternate routes" requirement. Rendered the app's actual A26
path coordinates onto the real background photo (this sandbox has no Android SDK/emulator, so a
literal running-app screenshot isn't possible — this Python/PIL render onto the real artwork is
the closest available proxy) and the A26 design's flaw was visible immediately: since
`inverterToHousePath` was literally `gridToInverterPath.points.drop(3).reversed()`, the two
paths shared every pixel of the door-to-inverter stretch. That looked fine as a static diagram,
but the app's own routing rule composes "grid powers house" as grid_inverter → inverter_house
particles in sequence — with both flows active at once, whichever path's `Canvas.drawPath` ran
second (inverter_house, later in `SolarSimulationPaths.allPaths`) painted directly over the
other's color for that whole shared span, so the grid's pink line visually stopped short of the
inverter instead of running unbroken pole-to-inverter.

Confirmed the fix direction with the user ("split") before touching code, then gave
`inverterToHousePath` its own independent point list in `SolarSimulationPaths.kt`: leaves the
inverter on a distinct attachment point (`0.4950, 0.5850`, offset from grid's `0.5175, 0.5817`
so the two lines don't start pixel-on-pixel), drops straight down the wall, then cuts across the
lawn to the door/consumption point at `0.3300, 0.7900`. The two lines now cross at one point near
the ground rather than tracing over each other — `gridToInverterPath` runs pole→inverter
unbroken in pink with nothing drawn over any of it, and `inverterToHousePath` runs its own
inverter→door route in green. Verified by re-rendering both paths together onto the real
artwork before committing, per the established render-and-check workflow.

Since this is now a fully custom overlay design rather than a trace of a single printed line
(the base artwork only ever printed one wire per run, not four independent ones), the new
`inverterToHousePath` coordinates are an original, plausible routing choice — down the wall,
across the lawn, into the door — rather than something pixel-measured off a printed line, since
no such second line exists in the photo to measure.

## A29 — Reverted to the original Phase A artwork; it already had 4 real colored lines

After several more rounds (see A24-A28 above) spent trying to reconstruct a 4-colored routing
scheme onto the A23 photo — which only ever had one printed white line — from descriptions of
images pasted inline in chat (never file-accessible, a recurring blocker documented since A24),
the user asked to revert to the artwork used before A23. Checking git history for what that was
(`git log --oneline -- .../bg_house_energy_routes.png`) turned up something worth calling out:
the *original* Phase A image (commit `b7cf7b2`, 1536x1024) already had four genuinely, distinctly
colored glow lines baked in — the exact kind of artwork this whole multi-round effort had been
trying to hand-build on top of the wrong photo. A23's swap to a plain-white-line image was the
wrong call in hindsight; A29 undoes it.

Critically, this image came back out of **git history** via `git show`, not a chat paste — genuine
file access, the same kind the A23 artwork always had. That meant the four lines could finally be
traced the reliable way instead of guessed: render a fine coordinate grid onto the real photo,
crop tightly around each line, and read vertices directly off the gridded crops (the same method
offered to the user as "Option B" a few turns earlier, just usable here because the photo was
actually reachable). Colors were sampled too, then matched to the closest existing restrained
`LumixColors` tokens rather than adding new saturated ones: `WarningRed` for the grid line
(genuinely a warm red in the artwork, not the pink/magenta assumed in A27-A28 for a different
photo), `SolarYellow` for solar, `EnergyGreen` and `TechnicalCyan` for the two inverter output
lines.

That last pairing needed a real look rather than an assumption. Tracing the inverter's two
bottom terminals close up (crop at the battery, gridded to 0.01 resolution) showed: the *green*
line is short and terminates in a clean glowing dot flush against the battery casing — an actual
component connection, styled identically to how the grid/solar lines terminate at the inverter.
The *blue* line is long — it continues past the battery, along the wall, past a window, to a
small junction/breaker box, and stops there (not at the parked car visible further right, which
turned out to be unconnected background scenery). So in this specific artwork: **green =
inverter→battery, blue = inverter→house/load** — the reverse of the green=load/blue=battery
mapping stated earlier for a different hand-drawn reference. The artwork's own printed endpoints
were treated as ground truth over a remembered verbal description from a different image, per
this round's "no hallucinating, use the grids" instruction. `SolarSimulationPaths.kt`'s class doc
flags this explicitly as a one-line swap if it's actually backwards.

Also removed as part of the revert: `LumixColors.GridMagenta` (A27's invented pink, unused now
that grid is genuinely red in this artwork), and the four `*LabelBounds` rects in
`SolarSimulationPaths.kt` (A23-specific — this artwork has no baked "0 W" placeholder text to
erase). `FlowLabelChips` now anchors each chip to its own path's midpoint
(`EnergyFlowPathManager.pointAt(path, 0.5f)`) instead of a fixed label position, which also makes
it correct automatically if the artwork is ever swapped again. `IMAGE_ASPECT_RATIO` reverted to
`1536f/1024f`. The A27 HUD layer (stat cards, time/mode card, legend, battery/system cards) needed
no changes at all — it was already built against `flows`/`SimFrame` state, not image-specific
positions, so it carried over cleanly onto the restored artwork.

Verified the same way as every round since A23: rendered all four traced paths, in their real
app colors, onto the actual restored background photo via Python/PIL, and read the result back
before touching any Kotlin.
