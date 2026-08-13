package com.lumix.estimator.domain.simulation

import com.lumix.estimator.domain.ApplianceType
import com.lumix.estimator.domain.QuoteInputs
import kotlinx.serialization.Serializable

/**
 * Which residential circuit an appliance is modeled as living on. Jamaican homes (like the
 * US-style split-phase service this app assumes) wire general lighting/outlets at 110V and
 * heavy draws (AC, water heater, ovens, dryers, EV chargers) at 220V across both legs.
 */
enum class ElectricalTier(val nominalVoltage: Double) {
    LOW(110.0),
    HIGH(220.0)
}

/**
 * Which kind of day the simulation is modeling — the same household behaves differently on a
 * working weekday than on a Saturday or Sunday (more daytime occupancy, different laundry/
 * cleaning/cooking timing). Affects both the background occupancy curve ([SimulationEngine]'s
 * load shape) and a handful of appliances whose [ApplianceRun.dayTypes] scope them to specific
 * days rather than running the same way every day.
 */
@Serializable
enum class DayType(val label: String) {
    WEEKDAY("Weekday"),
    SATURDAY("Saturday"),
    SUNDAY("Sunday")
}

/**
 * The full appliance database — expected running watts, electrical tier, duty factor, and
 * grouping — sourced from the Jamaica Residential Energy Audit Load Profile supplied for this
 * app (A33). [watts] is "expected running watts": the power drawn *while actually running*, not
 * a flat all-day average — that's what [dutyFactor] is for, matching the source spreadsheet's
 * own engineering model (`Pavg = Pnameplate × duty factor`) for thermostatic/cycling loads like
 * a refrigerator or water heater, where the appliance is only genuinely drawing that wattage a
 * fraction of its scheduled window (compressor/element cycling on and off), not continuously.
 * Appliances that don't cycle (lighting, electronics, most short "event" loads) carry
 * `dutyFactor = 1.0` — their scheduled window already *is* their actual running time.
 * [category] groups the appliance picker into sections instead of one 45-row flat list.
 */
@Serializable
enum class SimApplianceType(
    val label: String,
    val watts: Int,
    val tier: ElectricalTier,
    val dutyFactor: Double,
    val category: String,
    /**
     * Locked-rotor inrush as a multiple of [watts] (e.g. 3.0 = a compressor briefly drawing 3x
     * its running watts at startup). 1.0 (the default) means no meaningful motor inrush — true
     * for every resistive/electronic load in this catalog. Typical figures for the appliance
     * *class*, not a manufacturer nameplate — see [worstCaseStartupSurgeKw]'s own doc for why
     * this is surfaced as a separate informational figure rather than folded into the 5-minute
     * timestep simulation.
     */
    val startupSurgeMultiplier: Double = 1.0,
    /** How long the inrush actually lasts — real motor starts settle in well under a second. */
    val startupDurationSeconds: Double = 0.0
) {
    // Kitchen
    REFRIGERATOR("Refrigerator/Freezer", 150, ElectricalTier.LOW, 0.35, "Kitchen", startupSurgeMultiplier = 3.0, startupDurationSeconds = 0.5),
    CHEST_FREEZER("Chest/Deep Freezer", 180, ElectricalTier.LOW, 0.35, "Kitchen", startupSurgeMultiplier = 3.0, startupDurationSeconds = 0.5),
    ELECTRIC_KETTLE("Electric Kettle", 1500, ElectricalTier.LOW, 1.0, "Kitchen"),
    MICROWAVE("Microwave", 1200, ElectricalTier.LOW, 1.0, "Kitchen"),
    TOASTER("Toaster", 1200, ElectricalTier.LOW, 1.0, "Kitchen"),
    BLENDER("Blender", 400, ElectricalTier.LOW, 1.0, "Kitchen"),
    STOVE("Electric Stove/Cooktop", 3000, ElectricalTier.HIGH, 0.55, "Kitchen"),
    OVEN("Electric Oven", 3000, ElectricalTier.HIGH, 0.50, "Kitchen"),
    AIR_FRYER("Air Fryer", 1500, ElectricalTier.LOW, 0.65, "Kitchen"),
    RICE_COOKER("Rice Cooker", 700, ElectricalTier.LOW, 0.55, "Kitchen"),
    PRESSURE_COOKER("Pressure Cooker", 1000, ElectricalTier.LOW, 0.65, "Kitchen"),

    // Cooling & comfort
    CEILING_FAN("Ceiling Fan", 60, ElectricalTier.LOW, 1.0, "Cooling & Comfort"),
    STANDING_FAN("Pedestal/Standing Fan", 50, ElectricalTier.LOW, 1.0, "Cooling & Comfort"),
    BEDROOM_FAN("Bedroom Fan", 50, ElectricalTier.LOW, 1.0, "Cooling & Comfort"),
    // dutyFactor 0.60: a thermostat-cycling compressor doesn't hold nameplate watts for its
    // whole scheduled window — it satisfies the room and cycles off, then back on. 60% is a
    // typical residential figure, not a measured one; the average draw during an active window
    // becomes 1500W x 0.60 = 900W, matching the exact worked example this app's own AC spec used.
    AIR_CONDITIONER("Air Conditioner", 1500, ElectricalTier.HIGH, 0.60, "Cooling & Comfort", startupSurgeMultiplier = 3.0, startupDurationSeconds = 1.0),

    // Lighting
    LED_BEDROOM("LED Lighting — Bedroom", 10, ElectricalTier.LOW, 1.0, "Lighting"),
    LED_KITCHEN("LED Lighting — Kitchen", 10, ElectricalTier.LOW, 1.0, "Lighting"),
    LED_LIVING("LED Lighting — Living/Common", 10, ElectricalTier.LOW, 1.0, "Lighting"),
    LED_EXTERIOR("LED Lighting — Exterior/Security", 10, ElectricalTier.LOW, 1.0, "Lighting"),
    LED_BATHROOM("LED Lighting — Bathroom", 10, ElectricalTier.LOW, 1.0, "Lighting"),
    OUTDOOR_FLOODLIGHT("Outdoor Security Floodlight", 30, ElectricalTier.LOW, 1.0, "Lighting"),

    // Electronics & networking
    TELEVISION("Television", 80, ElectricalTier.LOW, 1.0, "Electronics & Networking"),
    SET_TOP_BOX("Set-Top/Cable Box", 15, ElectricalTier.LOW, 0.8, "Electronics & Networking"),
    WIFI_ROUTER("Wi-Fi Router", 10, ElectricalTier.LOW, 1.0, "Electronics & Networking"),
    MODEM("Modem/ONT", 12, ElectricalTier.LOW, 1.0, "Electronics & Networking"),
    PHONE_CHARGERS("Phone Chargers", 10, ElectricalTier.LOW, 0.5, "Electronics & Networking"),
    LAPTOP("Laptop", 65, ElectricalTier.LOW, 0.6, "Electronics & Networking"),
    DESKTOP_COMPUTER("Desktop Computer + Monitor", 150, ElectricalTier.LOW, 0.6, "Electronics & Networking"),
    PRINTER("Computer Printer", 35, ElectricalTier.LOW, 0.3, "Electronics & Networking"),
    GAME_CONSOLE("Game Console", 120, ElectricalTier.LOW, 0.7, "Electronics & Networking"),
    SOUND_SYSTEM("Sound System", 100, ElectricalTier.LOW, 0.5, "Electronics & Networking"),

    // Water & heating
    WATER_HEATER("Electric Water Heater", 3800, ElectricalTier.HIGH, 0.50, "Water & Heating"),
    INSTANT_SHOWER("Instant Electric Shower", 4500, ElectricalTier.HIGH, 1.0, "Water & Heating"),
    WATER_PUMP("Water Pump", 750, ElectricalTier.HIGH, 0.50, "Water & Heating", startupSurgeMultiplier = 3.0, startupDurationSeconds = 1.0),

    // Personal care
    HAIR_DRYER("Hair Dryer", 1500, ElectricalTier.LOW, 1.0, "Personal Care"),
    CURLING_IRON("Curling/Flat Iron", 60, ElectricalTier.LOW, 0.60, "Personal Care"),

    // Laundry
    IRON("Clothes Iron", 1200, ElectricalTier.LOW, 0.60, "Laundry"),
    WASHING_MACHINE("Washing Machine", 500, ElectricalTier.LOW, 0.60, "Laundry", startupSurgeMultiplier = 2.0, startupDurationSeconds = 0.5),
    CLOTHES_DRYER("Clothes Dryer", 5000, ElectricalTier.HIGH, 0.75, "Laundry", startupSurgeMultiplier = 2.0, startupDurationSeconds = 0.5),

    // Cleaning & misc
    VACUUM_CLEANER("Vacuum Cleaner", 900, ElectricalTier.LOW, 1.0, "Cleaning & Misc"),
    SEWING_MACHINE("Sewing Machine", 100, ElectricalTier.LOW, 0.50, "Cleaning & Misc"),

    // Security & access
    SECURITY_SYSTEM("Security/CCTV System", 40, ElectricalTier.LOW, 1.0, "Security & Access"),
    GATE_OPENER("Electric Gate/Garage Opener", 500, ElectricalTier.LOW, 1.0, "Security & Access", startupSurgeMultiplier = 2.5, startupDurationSeconds = 0.5),

    // EV & outdoor
    EV_CHARGER_L1("EV Charger — 110V (L1)", 1400, ElectricalTier.LOW, 0.80, "EV & Outdoor"),
    EV_CHARGER_L2("EV Charger — 220V (L2)", 5000, ElectricalTier.HIGH, 0.80, "EV & Outdoor"),
    POOL_PUMP("Pool Pump", 1000, ElectricalTier.HIGH, 1.0, "EV & Outdoor", startupSurgeMultiplier = 3.0, startupDurationSeconds = 1.0)
}

/**
 * One scheduled window a quantity of an appliance runs — e.g. "3 units, from 6am for 3h".
 * An appliance can have several of these (a "+" control adds more), so a household's 6 fans
 * can be modeled as 3 running a daytime window and 3 running a separate nighttime window,
 * each contributing load only while its own window is active.
 */
data class ApplianceRun(
    val quantity: Int = 1,
    val startHour: Double = 0.0,
    val durationHours: Double = 24.0,
    /** Which day(s) this run applies on — defaults to every day, matching prior behavior. */
    val dayTypes: Set<DayType> = DayType.entries.toSet()
) {
    /** Whether this run is contributing load at [hour] (0..24) on [dayType], handling wraparound past midnight. */
    fun isActiveAt(hour: Double, dayType: DayType = DayType.WEEKDAY): Boolean {
        if (dayType !in dayTypes) return false
        val h = hour.mod(24.0)
        val endHour = startHour + durationHours
        return if (endHour <= 24.0) {
            h >= startHour && h < endHour
        } else {
            h >= startHour || h < (endHour - 24.0)
        }
    }
}

/**
 * An appliance's full runtime configuration: whether it's in play at all, and the one or more
 * scheduled [runs] making it up. A fresh, un-scheduled appliance defaults to a single run
 * covering the whole day, so simply toggling one on behaves exactly like the old flat on/off
 * model until someone actually customizes the schedule.
 */
data class ApplianceState(
    val enabled: Boolean = false,
    val runs: List<ApplianceRun> = listOf(ApplianceRun())
) {
    val totalQuantity: Int get() = runs.sumOf { it.quantity }
}

/**
 * A reasonable starting run schedule for a typical Jamaican household, per appliance type — a
 * DEFAULT ASSUMPTION grounded in the Jamaica Residential Energy Audit Load Profile (A33), not a
 * claim about how any specific household actually behaves. Always fully editable afterward
 * through the appliance picker's own quantity/hours/period controls. Quantities are filled in
 * separately by the caller; only the timing shape lives here. Short "event" appliances (kettle,
 * microwave, iron, gate opener) default to minutes, not hours, since that's how they're actually
 * used. Where the source data gave alternative "or" windows (e.g. laundry morning-or-evening),
 * the evening window was picked as the more universally common one for a working household.
 */
fun defaultScheduleFor(type: SimApplianceType): List<ApplianceRun> = when (type) {
    // A compressor cycles on/off all day rather than truly running continuously — dutyFactor
    // carries that, not the schedule shape — so a single all-day run is the right shape here.
    SimApplianceType.REFRIGERATOR -> listOf(ApplianceRun(startHour = 0.0, durationHours = 24.0))
    SimApplianceType.CHEST_FREEZER -> listOf(ApplianceRun(startHour = 0.0, durationHours = 24.0))
    // Two short events, breakfast and dinner.
    SimApplianceType.ELECTRIC_KETTLE -> listOf(
        ApplianceRun(startHour = 6.0, durationHours = 8.0 / 60.0),
        ApplianceRun(startHour = 18.0, durationHours = 8.0 / 60.0)
    )
    SimApplianceType.MICROWAVE -> listOf(
        ApplianceRun(startHour = 7.0, durationHours = 10.0 / 60.0),
        ApplianceRun(startHour = 18.5, durationHours = 10.0 / 60.0)
    )
    SimApplianceType.TOASTER -> listOf(ApplianceRun(startHour = 6.5, durationHours = 7.0 / 60.0))
    SimApplianceType.BLENDER -> listOf(ApplianceRun(startHour = 18.0, durationHours = 6.0 / 60.0))
    // Weekend adds a real midday cooking session (section 19/27's "higher weekend daytime
    // occupancy" pattern) — a weekday's empty-house midday never gets one.
    SimApplianceType.STOVE -> listOf(
        ApplianceRun(startHour = 6.0, durationHours = 0.75),
        ApplianceRun(startHour = 18.0, durationHours = 1.0),
        ApplianceRun(startHour = 12.0, durationHours = 1.0, dayTypes = setOf(DayType.SATURDAY, DayType.SUNDAY))
    )
    SimApplianceType.OVEN -> listOf(ApplianceRun(startHour = 18.0, durationHours = 1.5))
    SimApplianceType.AIR_FRYER -> listOf(ApplianceRun(startHour = 18.0, durationHours = 0.5))
    SimApplianceType.RICE_COOKER -> listOf(ApplianceRun(startHour = 18.0, durationHours = 1.0))
    SimApplianceType.PRESSURE_COOKER -> listOf(ApplianceRun(startHour = 18.0, durationHours = 0.5))

    // Weekend adds a daytime run on top of the weekday morning/evening pair — people are
    // actually home to feel the heat at 11am on a Saturday, unlike a weekday.
    SimApplianceType.CEILING_FAN -> listOf(
        ApplianceRun(startHour = 6.0, durationHours = 2.0),
        ApplianceRun(startHour = 17.0, durationHours = 5.0),
        ApplianceRun(startHour = 10.0, durationHours = 6.0, dayTypes = setOf(DayType.SATURDAY, DayType.SUNDAY))
    )
    SimApplianceType.STANDING_FAN -> listOf(
        ApplianceRun(startHour = 6.0, durationHours = 2.0),
        ApplianceRun(startHour = 17.0, durationHours = 6.0),
        ApplianceRun(startHour = 10.0, durationHours = 6.0, dayTypes = setOf(DayType.SATURDAY, DayType.SUNDAY))
    )
    // Overnight, wraps past midnight — isActiveAt handles the wraparound.
    SimApplianceType.BEDROOM_FAN -> listOf(ApplianceRun(startHour = 20.0, durationHours = 10.5))
    SimApplianceType.AIR_CONDITIONER -> listOf(ApplianceRun(startHour = 19.0, durationHours = 8.0))

    SimApplianceType.LED_BEDROOM -> listOf(
        ApplianceRun(startHour = 6.0, durationHours = 1.5),
        ApplianceRun(startHour = 18.0, durationHours = 4.0)
    )
    SimApplianceType.LED_KITCHEN -> listOf(
        ApplianceRun(startHour = 6.0, durationHours = 2.0),
        ApplianceRun(startHour = 12.0, durationHours = 1.0),
        ApplianceRun(startHour = 17.5, durationHours = 3.5)
    )
    SimApplianceType.LED_LIVING -> listOf(ApplianceRun(startHour = 17.5, durationHours = 4.5))
    // Dusk to dawn, wraps past midnight.
    SimApplianceType.LED_EXTERIOR -> listOf(ApplianceRun(startHour = 18.0, durationHours = 12.0))
    SimApplianceType.LED_BATHROOM -> listOf(
        ApplianceRun(startHour = 6.0, durationHours = 0.75),
        ApplianceRun(startHour = 18.0, durationHours = 0.75)
    )
    SimApplianceType.OUTDOOR_FLOODLIGHT -> listOf(ApplianceRun(startHour = 18.0, durationHours = 12.0))

    // Weekend adds a daytime block — the working-household "unoccupied 8-5" assumption
    // (section 18) only holds on a weekday.
    SimApplianceType.TELEVISION -> listOf(
        ApplianceRun(startHour = 17.5, durationHours = 4.5),
        ApplianceRun(startHour = 10.0, durationHours = 6.0, dayTypes = setOf(DayType.SATURDAY, DayType.SUNDAY))
    )
    SimApplianceType.SET_TOP_BOX -> listOf(ApplianceRun(startHour = 17.5, durationHours = 4.5))
    SimApplianceType.WIFI_ROUTER -> listOf(ApplianceRun(startHour = 0.0, durationHours = 24.0))
    SimApplianceType.MODEM -> listOf(ApplianceRun(startHour = 0.0, durationHours = 24.0))
    SimApplianceType.PHONE_CHARGERS -> listOf(
        ApplianceRun(startHour = 6.0, durationHours = 1.5),
        ApplianceRun(startHour = 18.0, durationHours = 4.0)
    )
    SimApplianceType.LAPTOP -> listOf(
        ApplianceRun(startHour = 6.5, durationHours = 1.0),
        ApplianceRun(startHour = 18.0, durationHours = 4.0)
    )
    SimApplianceType.DESKTOP_COMPUTER -> listOf(ApplianceRun(startHour = 18.0, durationHours = 4.0))
    SimApplianceType.PRINTER -> listOf(ApplianceRun(startHour = 19.0, durationHours = 6.0 / 60.0))
    SimApplianceType.GAME_CONSOLE -> listOf(ApplianceRun(startHour = 18.0, durationHours = 2.5))
    SimApplianceType.SOUND_SYSTEM -> listOf(ApplianceRun(startHour = 18.0, durationHours = 2.5))

    // JPS recommends switching the water heater on shortly before bathing, not leaving it live
    // all day — modeled as two short pre-bathing windows.
    SimApplianceType.WATER_HEATER -> listOf(
        ApplianceRun(startHour = 5.5, durationHours = 1.0),
        ApplianceRun(startHour = 18.0, durationHours = 1.0)
    )
    SimApplianceType.INSTANT_SHOWER -> listOf(
        ApplianceRun(startHour = 6.0, durationHours = 0.15),
        ApplianceRun(startHour = 18.0, durationHours = 0.15)
    )
    SimApplianceType.WATER_PUMP -> listOf(
        ApplianceRun(startHour = 6.0, durationHours = 0.5),
        ApplianceRun(startHour = 18.0, durationHours = 0.5)
    )

    SimApplianceType.HAIR_DRYER -> listOf(ApplianceRun(startHour = 6.5, durationHours = 9.0 / 60.0))
    SimApplianceType.CURLING_IRON -> listOf(ApplianceRun(startHour = 6.5, durationHours = 18.0 / 60.0))

    SimApplianceType.IRON -> listOf(ApplianceRun(startHour = 19.0, durationHours = 0.5))
    // Saturday adds the household's real laundry batch (section 27) on top of an occasional
    // weeknight load — Jamaican working households typically do the bulk of laundry on the
    // weekend, not spread flat across every weekday.
    SimApplianceType.WASHING_MACHINE -> listOf(
        ApplianceRun(startHour = 18.0, durationHours = 0.75),
        ApplianceRun(startHour = 10.0, durationHours = 1.0, dayTypes = setOf(DayType.SATURDAY))
    )
    SimApplianceType.CLOTHES_DRYER -> listOf(ApplianceRun(startHour = 18.5, durationHours = 0.75))

    // Scoped to the weekend "cleaning day," not every single day (section 27) — a 9am vacuum
    // run on a weekday when the house is meant to be empty (section 18) doesn't reflect real use.
    SimApplianceType.VACUUM_CLEANER -> listOf(
        ApplianceRun(startHour = 9.0, durationHours = 0.5, dayTypes = setOf(DayType.SATURDAY, DayType.SUNDAY))
    )
    SimApplianceType.SEWING_MACHINE -> listOf(ApplianceRun(startHour = 14.0, durationHours = 1.0))

    SimApplianceType.SECURITY_SYSTEM -> listOf(ApplianceRun(startHour = 0.0, durationHours = 24.0))
    SimApplianceType.GATE_OPENER -> listOf(
        ApplianceRun(startHour = 7.0, durationHours = 1.0 / 60.0),
        ApplianceRun(startHour = 18.0, durationHours = 1.0 / 60.0)
    )

    // No typical pattern exists for these — entirely user-configurable — so this default
    // (evening, off-peak-ish) is just a starting point, not a behavioral claim.
    SimApplianceType.EV_CHARGER_L1 -> listOf(ApplianceRun(startHour = 18.0, durationHours = 3.0))
    SimApplianceType.EV_CHARGER_L2 -> listOf(ApplianceRun(startHour = 18.0, durationHours = 3.0))
    SimApplianceType.POOL_PUMP -> listOf(ApplianceRun(startHour = 10.0, durationHours = 4.0))
}

/**
 * Default quantity for a fresh appliance, from the Jamaica load profile's own "Qty" column —
 * things like lighting and phone chargers realistically come in more than one, everything else
 * defaults to a single unit.
 */
private fun defaultQuantityFor(type: SimApplianceType): Int = when (type) {
    SimApplianceType.CEILING_FAN -> 2
    SimApplianceType.LED_BEDROOM -> 2
    SimApplianceType.LED_KITCHEN -> 2
    SimApplianceType.LED_LIVING -> 4
    SimApplianceType.LED_EXTERIOR -> 4
    SimApplianceType.PHONE_CHARGERS -> 2
    SimApplianceType.OUTDOOR_FLOODLIGHT -> 2
    else -> 1
}

/**
 * Starting configuration for each appliance. Where the wizard actually asked the customer about
 * a matching appliance (fridge, fans, TV, microwave, washer, dryer, iron, AC), that reported
 * quantity drives it — not an arbitrary default. Everything else in the expanded A33 database
 * defaults ON with the load profile's own realistic schedule and quantity, since the ask is for
 * these to be genuine defaults the user only has to turn off or retime, not assemble from
 * scratch. A handful default OFF instead, each for a specific reason (never "just because"):
 * [SimApplianceType.CHEST_FREEZER] and [SimApplianceType.DESKTOP_COMPUTER] are usually a second
 * unit alongside something already on ([SimApplianceType.REFRIGERATOR], [SimApplianceType.LAPTOP])
 * rather than universal; [SimApplianceType.INSTANT_SHOWER] is a genuine alternative to
 * [SimApplianceType.WATER_HEATER], not an addition to it — both defaulting on would double-count
 * the same hot-water need; [SimApplianceType.EV_CHARGER_L1], [SimApplianceType.EV_CHARGER_L2],
 * and [SimApplianceType.POOL_PUMP] are marked "Optional" in the source load profile itself.
 */
fun defaultApplianceStates(inputs: QuoteInputs): Map<SimApplianceType, ApplianceState> {
    fun qty(type: ApplianceType) = inputs.appliances[type]?.qty ?: 0
    fun stateFor(type: SimApplianceType, quantity: Int = defaultQuantityFor(type), enabled: Boolean = true): ApplianceState {
        val q = quantity.coerceAtLeast(1)
        return ApplianceState(enabled = enabled, runs = defaultScheduleFor(type).map { it.copy(quantity = q) })
    }
    // Wizard-linked appliances follow what the customer actually reported having, including
    // being off entirely when they reported zero — same precedent as before A33.
    fun stateFromWizard(type: SimApplianceType, wizardType: ApplianceType): ApplianceState {
        val q = qty(wizardType)
        return stateFor(type, quantity = q.coerceAtLeast(1), enabled = q > 0)
    }

    return linkedMapOf(
        // Kitchen
        SimApplianceType.REFRIGERATOR to stateFromWizard(SimApplianceType.REFRIGERATOR, ApplianceType.FRIDGE),
        SimApplianceType.CHEST_FREEZER to stateFor(SimApplianceType.CHEST_FREEZER, quantity = qty(ApplianceType.FREEZER).coerceAtLeast(1), enabled = false),
        SimApplianceType.ELECTRIC_KETTLE to stateFor(SimApplianceType.ELECTRIC_KETTLE),
        SimApplianceType.MICROWAVE to stateFromWizard(SimApplianceType.MICROWAVE, ApplianceType.MICROWAVE),
        SimApplianceType.TOASTER to stateFor(SimApplianceType.TOASTER),
        SimApplianceType.BLENDER to stateFor(SimApplianceType.BLENDER),
        SimApplianceType.STOVE to stateFor(SimApplianceType.STOVE),
        SimApplianceType.OVEN to stateFor(SimApplianceType.OVEN, enabled = false),
        SimApplianceType.AIR_FRYER to stateFor(SimApplianceType.AIR_FRYER),
        SimApplianceType.RICE_COOKER to stateFor(SimApplianceType.RICE_COOKER),
        SimApplianceType.PRESSURE_COOKER to stateFor(SimApplianceType.PRESSURE_COOKER, enabled = false),

        // Cooling & comfort — wizard's single "Fans" quantity maps onto the primary (ceiling)
        // fan type since the wizard never asked which kind; standing/bedroom fans get their own
        // load-profile default quantity as supplementary comfort appliances.
        SimApplianceType.CEILING_FAN to stateFromWizard(SimApplianceType.CEILING_FAN, ApplianceType.FAN),
        SimApplianceType.STANDING_FAN to stateFor(SimApplianceType.STANDING_FAN),
        SimApplianceType.BEDROOM_FAN to stateFor(SimApplianceType.BEDROOM_FAN),
        SimApplianceType.AIR_CONDITIONER to stateFor(SimApplianceType.AIR_CONDITIONER, quantity = inputs.ac.counts.values.sum().coerceAtLeast(1), enabled = inputs.ac.hasAc),

        // Lighting
        SimApplianceType.LED_BEDROOM to stateFor(SimApplianceType.LED_BEDROOM),
        SimApplianceType.LED_KITCHEN to stateFor(SimApplianceType.LED_KITCHEN),
        SimApplianceType.LED_LIVING to stateFor(SimApplianceType.LED_LIVING),
        SimApplianceType.LED_EXTERIOR to stateFor(SimApplianceType.LED_EXTERIOR),
        SimApplianceType.LED_BATHROOM to stateFor(SimApplianceType.LED_BATHROOM),
        SimApplianceType.OUTDOOR_FLOODLIGHT to stateFor(SimApplianceType.OUTDOOR_FLOODLIGHT),

        // Electronics & networking
        SimApplianceType.TELEVISION to stateFromWizard(SimApplianceType.TELEVISION, ApplianceType.TV),
        SimApplianceType.SET_TOP_BOX to stateFor(SimApplianceType.SET_TOP_BOX),
        SimApplianceType.WIFI_ROUTER to stateFor(SimApplianceType.WIFI_ROUTER),
        SimApplianceType.MODEM to stateFor(SimApplianceType.MODEM),
        SimApplianceType.PHONE_CHARGERS to stateFor(SimApplianceType.PHONE_CHARGERS),
        SimApplianceType.LAPTOP to stateFor(SimApplianceType.LAPTOP),
        SimApplianceType.DESKTOP_COMPUTER to stateFor(SimApplianceType.DESKTOP_COMPUTER, enabled = false),
        SimApplianceType.PRINTER to stateFor(SimApplianceType.PRINTER),
        SimApplianceType.GAME_CONSOLE to stateFor(SimApplianceType.GAME_CONSOLE),
        SimApplianceType.SOUND_SYSTEM to stateFor(SimApplianceType.SOUND_SYSTEM),

        // Water & heating
        SimApplianceType.WATER_HEATER to stateFor(SimApplianceType.WATER_HEATER),
        SimApplianceType.INSTANT_SHOWER to stateFor(SimApplianceType.INSTANT_SHOWER, enabled = false),
        SimApplianceType.WATER_PUMP to stateFor(SimApplianceType.WATER_PUMP),

        // Personal care
        SimApplianceType.HAIR_DRYER to stateFor(SimApplianceType.HAIR_DRYER),
        SimApplianceType.CURLING_IRON to stateFor(SimApplianceType.CURLING_IRON),

        // Laundry
        SimApplianceType.IRON to stateFromWizard(SimApplianceType.IRON, ApplianceType.IRON),
        SimApplianceType.WASHING_MACHINE to stateFromWizard(SimApplianceType.WASHING_MACHINE, ApplianceType.WASHER),
        SimApplianceType.CLOTHES_DRYER to stateFromWizard(SimApplianceType.CLOTHES_DRYER, ApplianceType.DRYER),

        // Cleaning & misc
        SimApplianceType.VACUUM_CLEANER to stateFor(SimApplianceType.VACUUM_CLEANER),
        SimApplianceType.SEWING_MACHINE to stateFor(SimApplianceType.SEWING_MACHINE, enabled = false),

        // Security & access
        SimApplianceType.SECURITY_SYSTEM to stateFor(SimApplianceType.SECURITY_SYSTEM),
        SimApplianceType.GATE_OPENER to stateFor(SimApplianceType.GATE_OPENER),

        // EV & outdoor — explicitly "Optional" in the source load profile.
        SimApplianceType.EV_CHARGER_L1 to stateFor(SimApplianceType.EV_CHARGER_L1, enabled = false),
        SimApplianceType.EV_CHARGER_L2 to stateFor(SimApplianceType.EV_CHARGER_L2, enabled = false),
        SimApplianceType.POOL_PUMP to stateFor(SimApplianceType.POOL_PUMP, enabled = false)
    )
}

/**
 * Total appliance load at a given hour (0..24) — only runs whose window is active contribute,
 * scaled by each type's [SimApplianceType.dutyFactor] so cycling/thermostatic loads (fridge,
 * water heater, stove) contribute their real average draw rather than their full nameplate
 * watts for the whole scheduled window.
 */
fun totalApplianceLoadKwAt(states: Map<SimApplianceType, ApplianceState>, hour: Double, dayType: DayType = DayType.WEEKDAY): Double =
    states.entries.filter { it.value.enabled }.sumOf { (type, state) ->
        val activeQty = state.runs.filter { it.isActiveAt(hour, dayType) }.sumOf { it.quantity }
        activeQty * type.watts * type.dutyFactor / 1000.0
    }

/** Splits the appliance load active at [hour] on [dayType] by [ElectricalTier], for per-circuit current readings. */
fun applianceLoadKwByTierAt(states: Map<SimApplianceType, ApplianceState>, hour: Double, dayType: DayType = DayType.WEEKDAY): Map<ElectricalTier, Double> =
    states.entries.filter { it.value.enabled }
        .associate { (type, state) -> type to state.runs.filter { it.isActiveAt(hour, dayType) }.sumOf { it.quantity } }
        .filterValues { it > 0 }
        .entries.groupBy({ it.key.tier }, { it.key to it.value })
        .mapValues { (_, list) -> list.sumOf { (type, qty) -> qty * type.watts * type.dutyFactor } / 1000.0 }

/**
 * Which 110V leg (L1 or L2) a LOW-tier appliance is modeled as wired to. There's no real panel
 * schedule to read this from (a genuine one would come from the customer's actual wiring), so
 * this alternates by the appliance type's own catalog position — the same thing an electrician
 * does by default: spread general-lighting/outlet circuits evenly across both legs rather than
 * dumping them all on one. HIGH-tier (220V, line-to-line) appliances don't have a leg at all.
 */
private fun legFor(type: SimApplianceType): Int = type.ordinal % 2

/**
 * Splits the LOW-tier (110V) load active at [hour] on [dayType] across the two split-phase legs.
 * Returns (L1 kW, L2 kW). HIGH-tier appliances are line-to-line and don't appear on either leg.
 */
fun applianceLoadKwByLegAt(states: Map<SimApplianceType, ApplianceState>, hour: Double, dayType: DayType = DayType.WEEKDAY): Pair<Double, Double> {
    var l1 = 0.0
    var l2 = 0.0
    states.entries.filter { it.value.enabled }.forEach { (type, state) ->
        if (type.tier != ElectricalTier.LOW) return@forEach
        val qty = state.runs.filter { it.isActiveAt(hour, dayType) }.sumOf { it.quantity }
        if (qty <= 0) return@forEach
        val kw = qty * type.watts * type.dutyFactor / 1000.0
        if (legFor(type) == 0) l1 += kw else l2 += kw
    }
    return l1 to l2
}

/**
 * Worst-case instantaneous inrush if every currently-active motor/compressor appliance ([type]s
 * with [SimApplianceType.startupSurgeMultiplier] above 1.0) happened to start at the exact same
 * moment — refrigerator, AC, pumps, washer/dryer motors, gate opener. This is deliberately kept
 * as a separate informational figure rather than folded into [SimulationEngine]'s 5-minute
 * timestep timeline: a real motor start settles in well under a second (see
 * [SimApplianceType.startupDurationSeconds]), so representing it as a sustained load for an
 * entire timestep would overstate it by orders of magnitude. What this *does* answer honestly is
 * "if the worst coincidence happened, would the inverter's short-term surge rating cover it" —
 * most hybrid inverters tolerate roughly 2x their continuous rating for a few seconds, which is
 * the comparison the Technical panel draws this figure against.
 */
fun worstCaseStartupSurgeKw(states: Map<SimApplianceType, ApplianceState>, hour: Double, dayType: DayType = DayType.WEEKDAY): Double =
    states.entries.filter { it.value.enabled }.sumOf { (type, state) ->
        val activeQty = state.runs.filter { it.isActiveAt(hour, dayType) }.sumOf { it.quantity }
        activeQty * type.watts * type.startupSurgeMultiplier / 1000.0
    }

/**
 * "HOW WAS THIS CALCULATED?" (spec §46): each category's real contribution to daily energy, in
 * kWh — an exact analytic sum over every enabled run's own quantity/watts/dutyFactor/duration
 * (each run genuinely is "on" for its declared window, so summing directly is exact; no timestep
 * discretization to introduce error), scoped to the runs that actually apply on [dayType].
 * Categories with zero enabled load are omitted rather than shown as a zero row.
 */
fun applianceDailyEnergyByCategoryKwh(states: Map<SimApplianceType, ApplianceState>, dayType: DayType = DayType.WEEKDAY): Map<String, Double> =
    states.entries.filter { it.value.enabled }
        .flatMap { (type, state) ->
            state.runs.filter { dayType in it.dayTypes }
                .map { run -> type.category to (run.quantity * type.watts * type.dutyFactor * run.durationHours / 1000.0) }
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, contributions) -> contributions.sum() }
        .filterValues { it > 0.0 }
