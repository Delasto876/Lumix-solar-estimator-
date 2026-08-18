package com.lumix.estimator.domain.pricing

import com.lumix.estimator.domain.Catalog
import com.lumix.estimator.domain.MaterialLine
import com.lumix.estimator.domain.MaterialOverride
import com.lumix.estimator.domain.ManualModeType
import com.lumix.estimator.domain.PriceList
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.QuoteMode
import com.lumix.estimator.domain.RoofType
import com.lumix.estimator.domain.SystemCalculator
import com.lumix.estimator.domain.SystemMode
import com.lumix.estimator.domain.TransferSwitchMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A89/Ph21 — regression coverage for the master prompt's own material-takeoff formulas
 * ([MaterialTakeoffEngine]) and the delivery formula ([DeliveryCalculator]). Written from the
 * master prompt's own explicit worked example ("3 panels 700w with mid clamp... 4 mid clamp and
 * 4 end clamp and 2 16ft rail") and the price-list spreadsheet's own Calculation Key rules — not a
 * literal transcription of every one of the master prompt's §52 scenario numbers/text, which this
 * session's context does not carry verbatim; see the README's own disclosure of this.
 */
class MaterialTakeoffEngineTest {

    private val deye6k = Catalog.hybridInverters.first { it.id == "deye6k" }
    private val growatt10k = Catalog.hybridInverters.first { it.id == "growatt10k" } // 10kW class, used only where exact string count isn't asserted

    private fun line(materials: List<MaterialLine>, calcKey: String) = materials.filter { it.calcKey == calcKey }

    // ---- 1. Panel pricing matches the spreadsheet exactly (P595/P615/P620/P700/P720) ----
    @Test
    fun `panel prices match the price-list spreadsheet exactly`() {
        assertEquals(18500.0, PriceList.DEFAULT.panelPrice(595), 0.0)
        assertEquals(19000.0, PriceList.DEFAULT.panelPrice(615), 0.0)
        assertEquals(21000.0, PriceList.DEFAULT.panelPrice(620), 0.0)
        assertEquals(24000.0, PriceList.DEFAULT.panelPrice(700), 0.0)
        assertEquals(26000.0, PriceList.DEFAULT.panelPrice(720), 0.0)
    }

    // ---- 2. The master prompt's own worked example: 3 x 700W panels -> 2 rails, 4 mid clamp, 4 end clamp ----
    @Test
    fun `3 x 700W panels needs exactly 2 rails, 4 mid clamps, 4 end clamps (master prompt's own worked example)`() {
        val result = MaterialTakeoffEngine.compute(
            MaterialTakeoffEngine.TakeoffInput(
                panelW = 700, panelCount = 3, effectiveSystemMode = SystemMode.HYBRID,
                roofType = RoofType.SLAB, inverter = deye6k, batteryModuleCount = 0,
                transferSwitchMode = TransferSwitchMode.AUTOMATIC, useVoltageRegulator = false,
                use8WayDistributionPanel = false
            ),
            PriceList.DEFAULT
        )
        assertEquals(2.0, line(result, "RAIL_16FT").sumOf { it.qty }, 0.0)
        assertEquals(4.0, line(result, "MID_CLAMP").sumOf { it.qty }, 0.0)
        assertEquals(4.0, line(result, "END_CLAMP").sumOf { it.qty }, 0.0)
    }

    // ---- 3. 16 x 620W full rail/clamp/leg/weeb takeoff ----
    @Test
    fun `16 x 620W on a slab roof needs exactly 4 sets of 2 rails, 24 mid clamps, 16 end clamps, 4 weeb clips`() {
        val result = MaterialTakeoffEngine.compute(
            MaterialTakeoffEngine.TakeoffInput(
                panelW = 620, panelCount = 16, effectiveSystemMode = SystemMode.HYBRID,
                roofType = RoofType.SLAB, inverter = growatt10k, batteryModuleCount = 0,
                transferSwitchMode = TransferSwitchMode.AUTOMATIC, useVoltageRegulator = false,
                use8WayDistributionPanel = false
            ),
            PriceList.DEFAULT
        )
        // 620W's real width (1134mm) fits 4 to a 16ft rail set (RailLayoutCalculator) -> 16/4 = 4 sets exactly.
        assertEquals(8.0, line(result, "RAIL_16FT").sumOf { it.qty }, 0.0) // 4 sets x 2 rails
        assertEquals(24.0, line(result, "MID_CLAMP").sumOf { it.qty }, 0.0) // 4 sets x 2x(4-1)
        assertEquals(16.0, line(result, "END_CLAMP").sumOf { it.qty }, 0.0) // 4 sets x 4
        assertEquals(4.0, line(result, "WEEB_CLIP").sumOf { it.qty }, 0.0) // 1 per set
        assertEquals(16.0, line(result, "FRONT_LEG").sumOf { it.qty }, 0.0) // 4 sets x 4
        assertEquals(16.0, line(result, "BACK_LEG").sumOf { it.qty }, 0.0)
    }

    // ---- 4. Slab roof gets front/back legs, never L-foot ----
    @Test
    fun `slab roof gets front and back legs, never L-foot`() {
        val result = MaterialTakeoffEngine.compute(
            MaterialTakeoffEngine.TakeoffInput(
                panelW = 620, panelCount = 4, effectiveSystemMode = SystemMode.HYBRID,
                roofType = RoofType.SLAB, inverter = deye6k, batteryModuleCount = 0,
                transferSwitchMode = TransferSwitchMode.AUTOMATIC, useVoltageRegulator = false,
                use8WayDistributionPanel = false
            ),
            PriceList.DEFAULT
        )
        assertTrue(line(result, "FRONT_LEG").isNotEmpty())
        assertTrue(line(result, "BACK_LEG").isNotEmpty())
        assertTrue(line(result, "L_FOOT").isEmpty())
    }

    // ---- 5. Zinc roof gets L-foot (8 per set), never front/back legs ----
    @Test
    fun `zinc roof gets 8 L-foot per rail set, never front or back legs`() {
        val result = MaterialTakeoffEngine.compute(
            MaterialTakeoffEngine.TakeoffInput(
                panelW = 620, panelCount = 4, effectiveSystemMode = SystemMode.HYBRID,
                roofType = RoofType.ZINC, inverter = deye6k, batteryModuleCount = 0,
                transferSwitchMode = TransferSwitchMode.AUTOMATIC, useVoltageRegulator = false,
                use8WayDistributionPanel = false
            ),
            PriceList.DEFAULT
        )
        assertEquals(8.0, line(result, "L_FOOT").sumOf { it.qty }, 0.0) // 1 set x 2 rails x 4
        assertTrue(line(result, "FRONT_LEG").isEmpty())
        assertTrue(line(result, "BACK_LEG").isEmpty())
    }

    // ---- 6. Single battery: just the 250A breaker, no busbars/box ----
    @Test
    fun `single battery gets only the 250A breaker, no busbars or box`() {
        val result = MaterialTakeoffEngine.compute(
            MaterialTakeoffEngine.TakeoffInput(
                panelW = 620, panelCount = 4, effectiveSystemMode = SystemMode.HYBRID,
                roofType = RoofType.SLAB, inverter = deye6k, batteryModuleCount = 1,
                transferSwitchMode = TransferSwitchMode.AUTOMATIC, useVoltageRegulator = false,
                use8WayDistributionPanel = false
            ),
            PriceList.DEFAULT
        )
        assertEquals(1, line(result, "BAT_BREAKER_250").size)
        assertTrue(line(result, "BAT_BUS_RED").isEmpty())
        assertTrue(line(result, "BAT_BUS_BLACK").isEmpty())
        assertTrue(line(result, "BAT_BOX_12X12X6").isEmpty())
    }

    // ---- 7. Multiple (parallel) batteries: breaker + busbars + box, regardless of inverter brand ----
    @Test
    fun `multiple batteries get busbars and box in addition to the breaker (no inverter-brand exception)`() {
        val luxpower12k = Catalog.hybridInverters.first { it.id == "luxLxpLb12k" }
        val result = MaterialTakeoffEngine.compute(
            MaterialTakeoffEngine.TakeoffInput(
                panelW = 620, panelCount = 8, effectiveSystemMode = SystemMode.HYBRID,
                roofType = RoofType.SLAB, inverter = luxpower12k, batteryModuleCount = 2,
                transferSwitchMode = TransferSwitchMode.AUTOMATIC, useVoltageRegulator = false,
                use8WayDistributionPanel = false
            ),
            PriceList.DEFAULT
        )
        assertEquals(1, line(result, "BAT_BREAKER_250").size)
        assertEquals(1, line(result, "BAT_BUS_RED").size)
        assertEquals(1, line(result, "BAT_BUS_BLACK").size)
        assertEquals(1, line(result, "BAT_BOX_12X12X6").size)
    }

    // ---- 8. No battery: no battery-connection lines at all ----
    @Test
    fun `no battery means no battery-connection lines at all`() {
        val result = MaterialTakeoffEngine.compute(
            MaterialTakeoffEngine.TakeoffInput(
                panelW = 620, panelCount = 4, effectiveSystemMode = SystemMode.GRIDTIE,
                roofType = RoofType.SLAB, inverter = deye6k, batteryModuleCount = 0,
                transferSwitchMode = TransferSwitchMode.AUTOMATIC, useVoltageRegulator = false,
                use8WayDistributionPanel = false
            ),
            PriceList.DEFAULT
        )
        assertTrue(line(result, "BAT_BREAKER_250").isEmpty())
    }

    // ---- 9. Changeover switch NONE -> 3in trunking; a real switch mode -> 4in trunking ----
    @Test
    fun `no transfer switch means 3in trunking; a real switch mode means 4in trunking`() {
        val none = MaterialTakeoffEngine.compute(
            MaterialTakeoffEngine.TakeoffInput(
                panelW = 620, panelCount = 4, effectiveSystemMode = SystemMode.HYBRID,
                roofType = RoofType.SLAB, inverter = deye6k, batteryModuleCount = 0,
                transferSwitchMode = TransferSwitchMode.NONE, useVoltageRegulator = false,
                use8WayDistributionPanel = false
            ),
            PriceList.DEFAULT
        )
        assertTrue(line(none, "TRUNK_3").isNotEmpty())
        assertTrue(line(none, "TRUNK_4").isEmpty())
        assertTrue(line(none, "CHANGEOVER_40").isEmpty() && line(none, "CHANGEOVER_50").isEmpty() &&
            line(none, "CHANGEOVER_100").isEmpty() && line(none, "CHANGEOVER_120").isEmpty())

        val automatic = MaterialTakeoffEngine.compute(
            MaterialTakeoffEngine.TakeoffInput(
                panelW = 620, panelCount = 4, effectiveSystemMode = SystemMode.HYBRID,
                roofType = RoofType.SLAB, inverter = deye6k, batteryModuleCount = 0,
                transferSwitchMode = TransferSwitchMode.AUTOMATIC, useVoltageRegulator = false,
                use8WayDistributionPanel = false
            ),
            PriceList.DEFAULT
        )
        assertTrue(line(automatic, "TRUNK_4").isNotEmpty())
        assertTrue(line(automatic, "TRUNK_3").isEmpty())
    }

    // ---- 10. Changeover switch is sized from the real inverter AC output, not a flat rating ----
    @Test
    fun `changeover switch tier scales with the real inverter's AC current`() {
        // Deye 6K's real acOutputA (EquipmentSpecs) is 25A; x1.25 continuous factor = 31.25A -> 40A tier.
        val result = MaterialTakeoffEngine.compute(
            MaterialTakeoffEngine.TakeoffInput(
                panelW = 620, panelCount = 4, effectiveSystemMode = SystemMode.HYBRID,
                roofType = RoofType.SLAB, inverter = deye6k, batteryModuleCount = 0,
                transferSwitchMode = TransferSwitchMode.AUTOMATIC, useVoltageRegulator = false,
                use8WayDistributionPanel = false
            ),
            PriceList.DEFAULT
        )
        assertEquals(1, line(result, "CHANGEOVER_40").size)
        assertTrue(line(result, "CHANGEOVER_50").isEmpty() && line(result, "CHANGEOVER_100").isEmpty() && line(result, "CHANGEOVER_120").isEmpty())
    }

    // ---- 11. Distribution panel: 4-way default, 8-way when selected ----
    @Test
    fun `distribution panel defaults to 4-way, switches to 8-way when selected`() {
        val default = MaterialTakeoffEngine.compute(
            MaterialTakeoffEngine.TakeoffInput(
                panelW = 620, panelCount = 4, effectiveSystemMode = SystemMode.HYBRID,
                roofType = RoofType.SLAB, inverter = deye6k, batteryModuleCount = 0,
                transferSwitchMode = TransferSwitchMode.AUTOMATIC, useVoltageRegulator = false,
                use8WayDistributionPanel = false
            ),
            PriceList.DEFAULT
        )
        assertTrue(line(default, "DIST_4WAY").isNotEmpty())
        assertTrue(line(default, "DIST_8WAY").isEmpty())

        val eightWay = MaterialTakeoffEngine.compute(
            MaterialTakeoffEngine.TakeoffInput(
                panelW = 620, panelCount = 4, effectiveSystemMode = SystemMode.HYBRID,
                roofType = RoofType.SLAB, inverter = deye6k, batteryModuleCount = 0,
                transferSwitchMode = TransferSwitchMode.AUTOMATIC, useVoltageRegulator = false,
                use8WayDistributionPanel = true
            ),
            PriceList.DEFAULT
        )
        assertTrue(line(eightWay, "DIST_8WAY").isNotEmpty())
        assertTrue(line(eightWay, "DIST_4WAY").isEmpty())
    }

    // ---- 12. Voltage regulator only appears when explicitly requested ----
    @Test
    fun `voltage regulator only appears when explicitly requested`() {
        val without = MaterialTakeoffEngine.compute(
            MaterialTakeoffEngine.TakeoffInput(
                panelW = 620, panelCount = 4, effectiveSystemMode = SystemMode.HYBRID,
                roofType = RoofType.SLAB, inverter = deye6k, batteryModuleCount = 0,
                transferSwitchMode = TransferSwitchMode.AUTOMATIC, useVoltageRegulator = false,
                use8WayDistributionPanel = false
            ),
            PriceList.DEFAULT
        )
        assertTrue(line(without, "VOLT_REG").isEmpty())

        val with = MaterialTakeoffEngine.compute(
            MaterialTakeoffEngine.TakeoffInput(
                panelW = 620, panelCount = 4, effectiveSystemMode = SystemMode.HYBRID,
                roofType = RoofType.SLAB, inverter = deye6k, batteryModuleCount = 0,
                transferSwitchMode = TransferSwitchMode.AUTOMATIC, useVoltageRegulator = true,
                use8WayDistributionPanel = false
            ),
            PriceList.DEFAULT
        )
        assertEquals(1, line(with, "VOLT_REG").size)
        assertEquals(18000.0, line(with, "VOLT_REG").first().unitPrice)
    }

    // ---- 13. Flat defaults: PV wire bundle (20ft red + 20ft black) ----
    @Test
    fun `PV wire bundle defaults to 20ft red plus 20ft black`() {
        val result = MaterialTakeoffEngine.compute(
            MaterialTakeoffEngine.TakeoffInput(
                panelW = 620, panelCount = 4, effectiveSystemMode = SystemMode.HYBRID,
                roofType = RoofType.SLAB, inverter = deye6k, batteryModuleCount = 0,
                transferSwitchMode = TransferSwitchMode.AUTOMATIC, useVoltageRegulator = false,
                use8WayDistributionPanel = false
            ),
            PriceList.DEFAULT
        )
        assertEquals(20.0, line(result, "PV_RED").sumOf { it.qty }, 0.0)
        assertEquals(20.0, line(result, "PV_BLACK").sumOf { it.qty }, 0.0)
    }

    // ---- 14. AC wire bundle: 80ft when a transfer switch is selected, 30ft when none; off-grid excluded entirely ----
    @Test
    fun `AC wire bundle is 80ft with a transfer switch, 30ft with none, and never appears for off-grid`() {
        val withSwitch = MaterialTakeoffEngine.compute(
            MaterialTakeoffEngine.TakeoffInput(
                panelW = 620, panelCount = 4, effectiveSystemMode = SystemMode.HYBRID,
                roofType = RoofType.SLAB, inverter = deye6k, batteryModuleCount = 0,
                transferSwitchMode = TransferSwitchMode.AUTOMATIC, useVoltageRegulator = false,
                use8WayDistributionPanel = false
            ),
            PriceList.DEFAULT
        )
        assertEquals(80.0, line(withSwitch, "AC_RED").sumOf { it.qty }, 0.0)
        assertEquals(80.0, line(withSwitch, "AC_BLACK").sumOf { it.qty }, 0.0)
        assertEquals(80.0, line(withSwitch, "AC_GROUND").sumOf { it.qty }, 0.0)

        val noSwitch = MaterialTakeoffEngine.compute(
            MaterialTakeoffEngine.TakeoffInput(
                panelW = 620, panelCount = 4, effectiveSystemMode = SystemMode.HYBRID,
                roofType = RoofType.SLAB, inverter = deye6k, batteryModuleCount = 0,
                transferSwitchMode = TransferSwitchMode.NONE, useVoltageRegulator = false,
                use8WayDistributionPanel = false
            ),
            PriceList.DEFAULT
        )
        assertEquals(30.0, line(noSwitch, "AC_RED").sumOf { it.qty }, 0.0)
        assertEquals(30.0, line(noSwitch, "AC_BLACK").sumOf { it.qty }, 0.0)
        assertEquals(30.0, line(noSwitch, "AC_GROUND").sumOf { it.qty }, 0.0)

        val offgridInv = Catalog.offgridInverters.first()
        val offgrid = MaterialTakeoffEngine.compute(
            MaterialTakeoffEngine.TakeoffInput(
                panelW = 620, panelCount = 4, effectiveSystemMode = SystemMode.OFFGRID,
                roofType = RoofType.SLAB, inverter = offgridInv, batteryModuleCount = 0,
                transferSwitchMode = TransferSwitchMode.AUTOMATIC, useVoltageRegulator = false,
                use8WayDistributionPanel = false
            ),
            PriceList.DEFAULT
        )
        assertTrue(line(offgrid, "AC_RED").isEmpty())
    }

    // ---- 14b. Shingle roof carries the same L-foot rule as zinc ----
    @Test
    fun `shingle roof gets the same 8-L-foot-per-set rule as zinc`() {
        val shingle = MaterialTakeoffEngine.compute(
            MaterialTakeoffEngine.TakeoffInput(
                panelW = 620, panelCount = 4, effectiveSystemMode = SystemMode.HYBRID,
                roofType = RoofType.SHINGLE, inverter = deye6k, batteryModuleCount = 0,
                transferSwitchMode = TransferSwitchMode.AUTOMATIC, useVoltageRegulator = false,
                use8WayDistributionPanel = false
            ),
            PriceList.DEFAULT
        )
        assertEquals(8.0, line(shingle, "L_FOOT").sumOf { it.qty }, 0.0)
        assertTrue(line(shingle, "FRONT_LEG").isEmpty())
        assertTrue(line(shingle, "BACK_LEG").isEmpty())
    }

    // ---- 14c. MC4 connectors: flat 4 pairs, all systems, at J$450/pair ----
    @Test
    fun `MC4 connectors are always 4 pairs at J450 per pair, regardless of string count`() {
        val inputs = QuoteInputs(
            quoteMode = QuoteMode.MANUAL, systemMode = SystemMode.HYBRID,
            manualModeType = ManualModeType.PANEL_LED,
            manualPanelWatts = 620, manualPanelCount = 4
        )
        val result = SystemCalculator.calculate(inputs, PriceList.DEFAULT)
        val mc4 = result.materials.first { it.name == "MC4 connector pair" }
        assertEquals(4.0, mc4.qty, 0.0)
        assertEquals(450.0, mc4.unitPrice)
    }

    // ---- 14d. Battery interconnect cable/lugs are never quoted (batteries ship with their own) ----
    @Test
    fun `battery cable and lugs are never quoted as separate materials`() {
        val inputs = QuoteInputs(
            quoteMode = QuoteMode.MANUAL, systemMode = SystemMode.HYBRID,
            manualModeType = ManualModeType.BATTERY_LED,
            manualBatt10k = 1
        )
        val result = SystemCalculator.calculate(inputs, PriceList.DEFAULT)
        assertTrue(result.materials.none { it.name.contains("battery cable", ignoreCase = true) })
        assertTrue(result.materials.none { it.name.contains("battery lug", ignoreCase = true) })
    }

    // ---- 14e. Delivery is manual-entry only; never auto-computed from a distance formula ----
    @Test
    fun `delivery charge is always the installer's manual entry, never auto-computed`() {
        val inputs = QuoteInputs(
            quoteMode = QuoteMode.MANUAL, systemMode = SystemMode.HYBRID,
            manualModeType = ManualModeType.PANEL_LED,
            manualPanelWatts = 620, manualPanelCount = 4,
            deliveryCharge = 12345.0
        )
        val result = SystemCalculator.calculate(inputs, PriceList.DEFAULT)
        assertEquals(12345.0, result.deliveryCharge, 0.0)
    }

    // ---- 15. Quantity/price overrides at the quote level don't mutate the catalog price ----
    @Test
    fun `a quote-level material override changes only that quote, never the catalog default`() {
        val inputs = QuoteInputs(
            quoteMode = QuoteMode.MANUAL, systemMode = SystemMode.HYBRID,
            manualModeType = ManualModeType.PANEL_LED,
            manualPanelWatts = 620, manualPanelCount = 4,
            materialOverrides = mapOf("RAIL_16FT" to MaterialOverride(priceOverride = 9999.0))
        )
        val overridden = SystemCalculator.calculate(inputs, PriceList.DEFAULT)
        val railLine = overridden.materials.first { it.calcKey == "RAIL_16FT" }
        assertEquals(9999.0, railLine.unitPrice)
        // Catalog default itself is untouched — a second quote with no override still sees the real price.
        assertEquals(4500.0, PriceList.DEFAULT.mountingRail16ft, 0.0)
    }

    // ---- 16. A blank toll price on a toll route blocks finalization; a non-toll route never needs it ----
    @Test
    fun `a toll route with no toll price entered blocks finalization; a non-toll route does not`() {
        val tollInputs = QuoteInputs(
            quoteMode = QuoteMode.MANUAL, systemMode = SystemMode.HYBRID,
            manualPanelWatts = 620, manualPanelCount = 4,
            deliveryIsTollRoute = true
        )
        val tollResult = SystemCalculator.calculate(tollInputs, PriceList.DEFAULT)
        assertFalse(tollResult.canFinalize)
        assertTrue(tollResult.missingPriceItems.any { it.contains("Toll", ignoreCase = true) })

        val noTollInputs = tollInputs.copy(deliveryIsTollRoute = false)
        val noTollResult = SystemCalculator.calculate(noTollInputs, PriceList.DEFAULT)
        assertTrue(noTollResult.canFinalize)
    }

    // ---- 17. Junction -> Santa Cruz baseline: exactly 28km prices at exactly JMD 18,000 ----
    @Test
    fun `delivery at exactly the 28km baseline prices at exactly JMD 18,000`() {
        val result = DeliveryCalculator.calculate(28.0, isTollRoute = false, prices = PriceList.DEFAULT)
        assertEquals(18000.0, result.baseCharge, 0.01)
        assertEquals(0.0, result.tollCharge)
        assertEquals(18000.0, result.totalCharge)
    }

    // ---- 18. Delivery scales proportionally with distance; toll is a separate add-on, not folded in ----
    @Test
    fun `delivery scales proportionally with distance and toll is added separately`() {
        val doubleDistance = DeliveryCalculator.calculate(56.0, isTollRoute = false, prices = PriceList.DEFAULT)
        assertEquals(36000.0, doubleDistance.baseCharge, 0.01) // 2x the baseline distance -> 2x the baseline charge

        val prices = PriceList.DEFAULT.copy(deliveryTollJmd = 2500.0)
        val withToll = DeliveryCalculator.calculate(28.0, isTollRoute = true, prices = prices)
        assertEquals(18000.0, withToll.baseCharge, 0.01)
        assertEquals(2500.0, withToll.tollCharge)
        assertEquals(20500.0, withToll.totalCharge)

        val missingToll = DeliveryCalculator.calculate(28.0, isTollRoute = true, prices = PriceList.DEFAULT)
        assertTrue(missingToll.tollMissing)
        assertNull(missingToll.totalCharge)
    }
}
