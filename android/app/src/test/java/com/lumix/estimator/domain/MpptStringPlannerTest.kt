package com.lumix.estimator.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A62 (spec §24-28 — flexible MPPT string allocation): regression tests for
 * [MpptStringPlanner.planStrings], hand-traced first with an equivalent Python port (this
 * project's standard practice) before being encoded here. Covers the spec message's own two
 * worked examples verbatim (13 panels/2 MPPT -> 6+7; 10 panels/2 MPPT -> single string when the
 * 5+5 split would undervolt) plus the boundary/edge cases the algorithm itself depends on.
 */
class MpptStringPlannerTest {

    @Test
    fun `13 panels across 2 trackers splits 7 and 6, the spec's own worked example`() {
        val counts = MpptStringPlanner.planStrings(panelCount = 13, maxTrackers = 2, vmpPerPanel = 40.0, minVmpPerString = 90.0)
        assertEquals(listOf(7, 6), counts)
    }

    @Test
    fun `10 panels across 2 trackers splits evenly 5 and 5 when that split is not undervolted`() {
        val counts = MpptStringPlanner.planStrings(panelCount = 10, maxTrackers = 2, vmpPerPanel = 40.0, minVmpPerString = 90.0)
        assertEquals(listOf(5, 5), counts)
    }

    @Test
    fun `10 panels consolidate onto a single tracker when a 5 and 5 split would undervolt`() {
        // A low panel Vmp (12V) makes 5 x 12 = 60V, under the 90V floor; 10 x 12 = 120V clears it.
        val counts = MpptStringPlanner.planStrings(panelCount = 10, maxTrackers = 2, vmpPerPanel = 12.0, minVmpPerString = 90.0)
        assertEquals(listOf(10), counts)
    }

    @Test
    fun `an even panel count divides evenly across every tracker when valid`() {
        val counts = MpptStringPlanner.planStrings(panelCount = 12, maxTrackers = 2, vmpPerPanel = 40.0, minVmpPerString = 90.0)
        assertEquals(listOf(6, 6), counts)
    }

    @Test
    fun `17 panels across 2 trackers splits 9 and 8`() {
        val counts = MpptStringPlanner.planStrings(panelCount = 17, maxTrackers = 2, vmpPerPanel = 40.0, minVmpPerString = 90.0)
        assertEquals(listOf(9, 8), counts)
    }

    @Test
    fun `progressively falls back through 3, then 2, then 1 tracker when each larger split still undervolts`() {
        // 9 panels, 3 trackers, 10V/panel: 3-way split (3 each, 30V) and 2-way split (5+4, 40V
        // shortest) both undervolt a 90V floor; only fully consolidating onto 1 tracker (9 x 10 =
        // 90V) reaches it.
        val counts = MpptStringPlanner.planStrings(panelCount = 9, maxTrackers = 3, vmpPerPanel = 10.0, minVmpPerString = 90.0)
        assertEquals(listOf(9), counts)
    }

    @Test
    fun `a single panel always returns a single string regardless of how many trackers are available`() {
        val counts = MpptStringPlanner.planStrings(panelCount = 1, maxTrackers = 4, vmpPerPanel = 40.0, minVmpPerString = 90.0)
        assertEquals(listOf(1), counts)
    }

    @Test
    fun `zero panels returns an empty plan`() {
        assertEquals(emptyList<Int>(), MpptStringPlanner.planStrings(panelCount = 0, maxTrackers = 2, vmpPerPanel = 40.0, minVmpPerString = 90.0))
    }

    @Test
    fun `zero available trackers returns an empty plan`() {
        assertEquals(emptyList<Int>(), MpptStringPlanner.planStrings(panelCount = 10, maxTrackers = 0, vmpPerPanel = 40.0, minVmpPerString = 90.0))
    }

    @Test
    fun `single-tracker inverters always return one string with every panel`() {
        val counts = MpptStringPlanner.planStrings(panelCount = 14, maxTrackers = 1, vmpPerPanel = 40.0, minVmpPerString = 90.0)
        assertEquals(listOf(14), counts)
    }
}
