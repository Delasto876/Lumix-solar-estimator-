package com.lumix.estimator.domain.simulation

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A80 (spec Phase 17 §"MONTHLY WEATHER MODEL"): sanity checks that [JamaicaClimatology]'s table
 * covers every month, stays within its own documented 0..1/relative ranges, and actually encodes
 * the directional pattern the installer's own spec described (October cloudier/less sunny than
 * January, tropical-storm risk near zero outside hurricane season) — not exact figures, since none
 * are claimed; see the object's own [JamaicaClimatology.SOURCE_NOTE].
 */
class JamaicaClimatologyTest {

    @Test
    fun `every calendar month has a profile`() {
        for (month in 1..12) {
            assertNotNull("month $month should have a profile", JamaicaClimatology.profileFor(month))
        }
    }

    @Test
    fun `null or out-of-range month falls back to the annual average, not a crash`() {
        assertTrue(JamaicaClimatology.profileFor(null) == JamaicaClimatology.ANNUAL_AVERAGE)
        assertTrue(JamaicaClimatology.profileFor(13) == JamaicaClimatology.ANNUAL_AVERAGE)
        assertTrue(JamaicaClimatology.profileFor(0) == JamaicaClimatology.ANNUAL_AVERAGE)
    }

    @Test
    fun `every factor stays within its own documented range`() {
        for (month in 1..12) {
            val p = JamaicaClimatology.profileFor(month)
            assertTrue("month $month solarResourceFactor ${p.solarResourceFactor}", p.solarResourceFactor in 0.5..1.5)
            assertTrue("month $month cloudinessBaseline ${p.cloudinessBaseline}", p.cloudinessBaseline in 0.0..1.0)
            assertTrue("month $month variabilityFactor ${p.variabilityFactor}", p.variabilityFactor in 0.0..1.0)
            assertTrue("month $month tropicalStormRisk ${p.tropicalStormRisk}", p.tropicalStormRisk in 0.0..1.0)
        }
    }

    @Test
    fun `October (primary rainfall peak) is cloudier and less sunny than January (dry season)`() {
        val october = JamaicaClimatology.profileFor(10)
        val january = JamaicaClimatology.profileFor(1)
        assertTrue(october.cloudinessBaseline > january.cloudinessBaseline)
        assertTrue(october.solarResourceFactor < january.solarResourceFactor)
    }

    @Test
    fun `tropical storm risk is near zero outside hurricane season`() {
        assertTrue(JamaicaClimatology.profileFor(1).tropicalStormRisk < 0.05)
        assertTrue(JamaicaClimatology.profileFor(2).tropicalStormRisk < 0.05)
        assertTrue(JamaicaClimatology.profileFor(9).tropicalStormRisk > 0.2)
    }
}
