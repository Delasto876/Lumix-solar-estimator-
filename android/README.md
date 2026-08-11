# Lumix Solar Estimator (Android)

Native Android rewrite of the original single-file `index.html` prototype, built with
Kotlin + Jetpack Compose (Material 3).

## What's here

- **Wizard** — the same 7-step quote flow (mode & site info, roof & mounting, loads,
  JPS usage, backup, manual builder, pricing) as native Compose screens with inline
  validation instead of raw HTML `<select>`/`<input>` elements.
- **Sizing engine** — `domain/SystemCalculator.kt` is a line-for-line Kotlin port of the
  original `calculateSystem`/`calculateLoadsKwhAndPeak` logic (same PSH, DOD, tariff
  constants and panel/inverter/battery selection rules).
- **Quote history** — every calculated quote is saved to a local Room database
  (`data/QuoteEntity.kt`, `data/AppDatabase.kt`) with its full input/result snapshot, so
  past quotes stay reproducible even if prices later change.
- **Editable price list** — `ui/settings/PriceSettingsScreen.kt` lets you edit every
  material/inverter/battery price (regular and discount lists separately), persisted via
  DataStore Preferences, with a "reset to default" action.
- **PDF export & share** — `pdf/QuotePdfGenerator.kt` renders a shareable PDF quote from
  any saved quote via Android's `PdfDocument` API and the system share sheet.

## Fixed vs. the original prototype

The original web app always priced panels, batteries, and mounting/wiring hardware from
`regularPrices`, even when "use discount price list" was toggled on — only the inverter
line actually respected the toggle. `SystemCalculator.calculate` now applies the selected
price list (`regular` or `discount`) consistently across every material line.

## Building

This sandbox could not verify a full build: the Android SDK and AndroidX/Compose/Room
artifacts are hosted on Google's Maven repo (`dl.google.com`), which this environment's
network proxy blocks (Maven Central is reachable, Google's Maven is not). What *was*
verified here:

- The pure-Kotlin `domain` package (no Android dependencies) was compiled and run
  standalone against `kotlinx-serialization` from Maven Central across several guided /
  manual / load-based scenarios (hybrid, off-grid, grid-tie) with no exceptions and
  sane, correctly-clamped output.
- Every Gradle/Compose file was manually checked for import completeness and API usage
  against the pinned library versions (a couple of real issues — missing `Modifier.weight`
  imports and a color-initialization-order bug in `Theme.kt` — were found this way and
  fixed).

To build for real, open the `android/` folder in **Android Studio (Koala or newer)** with
network access to Google's Maven repo, or from the CLI:

```bash
cd android
./gradlew assembleDebug
```

Requires JDK 17+, Android SDK platform 34, and Kotlin 2.0.21 (installed automatically by
Android Studio / the Gradle wrapper).
