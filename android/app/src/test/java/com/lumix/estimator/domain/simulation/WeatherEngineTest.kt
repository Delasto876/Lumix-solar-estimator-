package com.lumix.estimator.domain.simulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A80 (spec Phase 17 §"WEATHER / CLOUD MODEL" — "Use a seeded random generator so that the
 * simulation is reproducible. The same simulation scenario should produce the same result when
 * reopened"): [WeatherEngine.generate]'s core reproducibility contract, plus the value-range and
 * smoothness guarantees the spec's own §"WEATHER/CLOUD MODEL" and §"DAILY SOLAR CURVE" describe.
 */
class WeatherEngineTest {

    @Test
    fun `same inputs produce an identical curve`() {
        val a = WeatherEngine.generate(WeatherScenario.TYPICAL, month = 10)
        val b = WeatherEngine.generate(WeatherScenario.TYPICAL, month = 10)
        for (h in 0..240) {
            val hour = h / 10.0
            assertEquals(a.factorAt(hour), b.factorAt(hour), 0.0)
        }
    }

    @Test
    fun `a different scenario produces a different curve`() {
        val typical = WeatherEngine.generate(WeatherScenario.TYPICAL, month = 10)
        val rainy = WeatherEngine.generate(WeatherScenario.RAINY, month = 10)
        val differsSomewhere = (0..240).any { h ->
            val hour = h / 10.0
            typical.factorAt(hour) != rainy.factorAt(hour)
        }
        assertTrue("RAINY should differ from TYPICAL somewhere across the day", differsSomewhere)
    }

    @Test
    fun `an explicit seed overrides the default derived seed`() {
        // A single fixed hour (noon) isn't a reliable place to check for a seed-driven difference:
        // the seed only drives which/where transient CloudEvents land (WeatherCurve.factorAt), not
        // the baseline itself — a real JVM run confirms noon lands outside every event's window for
        // BOTH seed 42 and seed 99 here, so both correctly return the same unattenuated baseline
        // (0.7025) at that one point despite the seeds genuinely differing (40 of 241 samples across
        // the day disagree). Sampling across the day, as the sibling "different scenario" test
        // already does, is what actually verifies the seed's effect rather than assuming any one
        // hour must show it.
        val a = WeatherEngine.generate(WeatherScenario.TYPICAL, month = 6, seed = 42L)
        val b = WeatherEngine.generate(WeatherScenario.TYPICAL, month = 6, seed = 42L)
        val c = WeatherEngine.generate(WeatherScenario.TYPICAL, month = 6, seed = 99L)
        for (h in 0..240) {
            val hour = h / 10.0
            assertEquals(a.factorAt(hour), b.factorAt(hour), 0.0)
        }
        val differsSomewhere = (0..240).any { h ->
            val hour = h / 10.0
            a.factorAt(hour) != c.factorAt(hour)
        }
        assertTrue("seed 99 should differ from seed 42 somewhere across the day", differsSomewhere)
    }

    @Test
    fun `factorAt always stays within the documented 0_02 to 1_0 range`() {
        for (scenario in WeatherScenario.entries) {
            for (month in listOf(null, 1, 5, 10)) {
                val curve = WeatherEngine.generate(scenario, month)
                for (h in 0..480) { // covers a 48h span, same as RechargeFeasibility's extended timeline
                    val hour = h / 10.0
                    val factor = curve.factorAt(hour)
                    assertTrue("$scenario/$month@$hour = $factor out of range", factor in 0.02..1.0)
                }
            }
        }
    }

    @Test
    fun `CLEAR is a flat 1_0 curve for callers that opt out of the weather model`() {
        for (h in 0..240) {
            assertEquals(1.0, WeatherCurve.CLEAR.factorAt(h / 10.0), 0.0)
        }
    }

    @Test
    fun `the positive solar-conditions slider never fully cancels a cloud event`() {
        // The deviation multiplier is a scalar over the whole curve (spec's own "preserving...
        // cloud events" requirement) — it should shift availability, not erase transient dips
        // outright. Sampling across the day should still show some variation for a cloudier
        // scenario even at the slider's brightest setting.
        val curve = WeatherEngine.generate(WeatherScenario.CLOUDIER, month = 10, solarConditionsDeviation = 0.2)
        val samples = (60..170).map { curve.factorAt(it / 10.0) }
        assertTrue("expected some variation across the day", (samples.max() - samples.min()) > 0.01)
    }
}
