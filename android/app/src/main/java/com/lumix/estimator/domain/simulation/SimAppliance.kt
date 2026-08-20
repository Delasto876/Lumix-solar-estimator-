package com.lumix.estimator.domain.simulation

import com.lumix.estimator.domain.ApplianceType
import com.lumix.estimator.domain.QuoteInputs
import com.lumix.estimator.domain.SystemCalculator
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
    val runs: List<ApplianceRun> = listOf(ApplianceRun()),
    /**
     * A68: overrides [SimApplianceType.watts] for this specific household's selection, when the
     * catalog's flat per-type wattage isn't what was actually sized. [SimApplianceType.AIR_CONDITIONER]'s
     * own `watts` (1500) only exists to give AC a duty-cycle/schedule *shape* — the installer's real
     * AC sizing is per-BTU-tier ([com.lumix.estimator.domain.AcLoad.counts]), and
     * [com.lumix.estimator.domain.SystemCalculator]'s own wizard sizing already uses that real
     * figure. Before this field existed, the simulation silently discarded it and ran every
     * selected AC unit at the flat 1500W placeholder regardless of its real BTU/wattage — violating
     * "whatever system the installer designs is EXACTLY the system the simulation models" for the
     * single highest-power appliance in the catalog. Null for every other appliance, where the
     * catalog wattage already *is* the real figure.
     */
    val wattsOverride: Double? = null
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
 * used.
 *
 * 2026-08-18 solar-user behavior: this app models solar/battery homes, and real solar households
 * run their DEFERRABLE high-power chores during the day, on free PV, specifically to keep the
 * battery full for the night when backup matters most — they iron and do laundry in the morning,
 * not at 7pm. So the discretionary, shiftable big loads (clothes iron, washer, dryer, EV charging;
 * pool pump was already daytime) default to the mid-morning/midday solar window rather than the
 * evening peak a generic grid-tied household would fall into. Time-FIXED loads are left where human
 * need puts them regardless of solar: cooking at meal times, lighting after dark, fans/AC for
 * evening/overnight comfort, the fridge/security/router running 24h, bathing-linked water heating.
 * Shifting a load's start hour changes only WHEN it draws, not its daily kWh (energy = duration ×
 * duty factor × watts), so this doesn't change system sizing — but it does move discretionary load
 * off the battery-critical night and onto direct solar, which is the whole point.
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
    // Phase 28 ("BLENDER: Default primarily weekend usage... Allow user to select Saturday,
    // Sunday, or both."): weekend-only by default now (was every day) — a short burst either way.
    SimApplianceType.BLENDER -> listOf(ApplianceRun(startHour = 18.0, durationHours = 6.0 / 60.0, dayTypes = setOf(DayType.SATURDAY, DayType.SUNDAY)))
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

    // Phase 28 ("CEILING FAN: Default approximately 6:00–10:30 PM"): the primary evening block
    // now matches that window exactly (was 5:00-10:00pm). Weekend adds a daytime run on top of
    // the weekday morning/evening pair — people are actually home to feel the heat at 11am on a
    // Saturday, unlike a weekday.
    SimApplianceType.CEILING_FAN -> listOf(
        ApplianceRun(startHour = 6.0, durationHours = 2.0),
        ApplianceRun(startHour = 18.0, durationHours = 4.5),
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

    // Phase 28 ("BEDROOM LIGHT: 7:00–8:00 PM, 9:00–10:00 PM. Allow multiple time blocks."):
    // matches that exact two-block example — was a single continuous 6-10pm block plus a morning
    // block; a bedroom light realistically isn't on continuously through a 4-hour evening the way
    // a living-room light is.
    SimApplianceType.LED_BEDROOM -> listOf(
        ApplianceRun(startHour = 19.0, durationHours = 1.0),
        ApplianceRun(startHour = 21.0, durationHours = 1.0)
    )
    // Phase 28 ("GENERAL HOUSE LIGHTING: Morning approximately 6:00–7:00 AM. Evening approximately
    // 6:00–10:00 PM. Do not assume every light is on simultaneously."): kitchen/living room
    // together stand in for "general house lighting" — each keeps its own distinct window (kitchen
    // active earlier for breakfast/dinner prep, living room the main evening block) so the two
    // never claim identical simultaneous runtime, while both now anchor to the 6-7am/6-10pm shape.
    SimApplianceType.LED_KITCHEN -> listOf(
        ApplianceRun(startHour = 6.0, durationHours = 1.0),
        ApplianceRun(startHour = 12.0, durationHours = 1.0),
        ApplianceRun(startHour = 18.0, durationHours = 3.0)
    )
    SimApplianceType.LED_LIVING -> listOf(
        ApplianceRun(startHour = 6.0, durationHours = 0.5),
        ApplianceRun(startHour = 18.0, durationHours = 4.0)
    )
    // Dusk to dawn, wraps past midnight.
    SimApplianceType.LED_EXTERIOR -> listOf(ApplianceRun(startHour = 18.0, durationHours = 12.0))
    SimApplianceType.LED_BATHROOM -> listOf(
        ApplianceRun(startHour = 6.0, durationHours = 0.75),
        ApplianceRun(startHour = 18.0, durationHours = 0.75)
    )
    SimApplianceType.OUTDOOR_FLOODLIGHT -> listOf(ApplianceRun(startHour = 18.0, durationHours = 12.0))

    // Phase 28 ("TV: Default approximately 3–4 hours at night. Use approximately 6:30–10:00 PM as
    // the default window."): matches that window exactly (was 5:30-10pm/4.5h). Weekend adds a
    // daytime block — the working-household "unoccupied 8-5" assumption (section 18) only holds
    // on a weekday.
    SimApplianceType.TELEVISION -> listOf(
        ApplianceRun(startHour = 18.5, durationHours = 3.5),
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

    // Phase 28 ("IRON: Default only 15 minutes in the morning: 7:00–7:15 AM."): an explicit,
    // specific real-world default this round — supersedes the prior "shift ironing to a
    // mid-morning solar window" reasoning (9:00-9:30am) with the literal 7:00-7:15am figure
    // actually asked for. Still fully editable via "Allow an additional user-defined period."
    SimApplianceType.IRON -> listOf(ApplianceRun(startHour = 7.0, durationHours = 0.25))
    // Phase 28 ("WASHING MACHINE: Default primarily weekend. Use realistic cycle durations rather
    // than several continuous hours. Allow Saturday/Sunday selection."): weekend-only by default
    // now (was also running every weekday at 9am) — one realistic ~45-minute cycle, Saturday and
    // Sunday both selected by default (either can be turned off, per "allow Saturday/Sunday
    // selection", through the existing day-type toggle chips).
    SimApplianceType.WASHING_MACHINE -> listOf(
        ApplianceRun(startHour = 9.0, durationHours = 0.75, dayTypes = setOf(DayType.SATURDAY, DayType.SUNDAY))
    )
    // Solar-user behavior: a 5kW dryer is the single most punishing deferrable load — running it
    // at midday on solar instead of the evening is exactly what keeps it off the battery. Follows
    // the morning wash.
    SimApplianceType.CLOTHES_DRYER -> listOf(ApplianceRun(startHour = 11.0, durationHours = 0.75))

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

    // Solar-user behavior: EV charging is the largest fully-deferrable load a home has. A solar
    // owner charges midday on the sun, not overnight off the battery/grid — so this defaults to a
    // midday solar window. Still entirely user-configurable (a night-shift worker who charges
    // overnight just drags it there).
    SimApplianceType.EV_CHARGER_L1 -> listOf(ApplianceRun(startHour = 10.0, durationHours = 3.0))
    SimApplianceType.EV_CHARGER_L2 -> listOf(ApplianceRun(startHour = 10.0, durationHours = 3.0))
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
 * Starting configuration for each appliance — every [SimApplianceType] (Air Conditioner via
 * [com.lumix.estimator.domain.AcLoad], everything else via [ApplianceType]) now has a real wizard counterpart (A54,
 * 2026-08-14: "the section to choose appliances ... that list only show some appliances" — the
 * wizard's picker was previously a 19-item subset of this catalog; [ApplianceType] now mirrors it
 * in full), so what's enabled and at what quantity is always exactly what the installer selected
 * in [com.lumix.estimator.ui.wizard.steps.StepHouseholdAppliances] — never a hidden separate
 * default the estimator never showed. [defaultAppliances] seeds every type at qty 0 except
 * [ApplianceType.FRIDGE] (qty 1) — nearly every household already has one — so a fresh quote
 * starts close to empty rather than a "generic demo house" the installer has to pare back.
 */
fun defaultApplianceStates(inputs: QuoteInputs): Map<SimApplianceType, ApplianceState> {
    fun qty(type: ApplianceType) = inputs.appliances[type]?.qty ?: 0
    fun stateFor(type: SimApplianceType, quantity: Int = defaultQuantityFor(type), enabled: Boolean = true, wattsOverride: Double? = null): ApplianceState {
        val q = quantity.coerceAtLeast(1)
        return ApplianceState(enabled = enabled, runs = defaultScheduleFor(type).map { it.copy(quantity = q) }, wattsOverride = wattsOverride)
    }
    // Every appliance follows exactly what the customer actually reported having, including being
    // off entirely when they reported zero or never touched that row at all.
    fun stateFromWizard(type: SimApplianceType, wizardType: ApplianceType): ApplianceState {
        val q = qty(wizardType)
        return stateFor(type, quantity = q.coerceAtLeast(1), enabled = q > 0)
    }

    return linkedMapOf(
        // Kitchen
        SimApplianceType.REFRIGERATOR to stateFromWizard(SimApplianceType.REFRIGERATOR, ApplianceType.FRIDGE),
        SimApplianceType.CHEST_FREEZER to stateFromWizard(SimApplianceType.CHEST_FREEZER, ApplianceType.FREEZER),
        SimApplianceType.ELECTRIC_KETTLE to stateFromWizard(SimApplianceType.ELECTRIC_KETTLE, ApplianceType.ELECTRIC_KETTLE),
        SimApplianceType.MICROWAVE to stateFromWizard(SimApplianceType.MICROWAVE, ApplianceType.MICROWAVE),
        SimApplianceType.TOASTER to stateFromWizard(SimApplianceType.TOASTER, ApplianceType.TOASTER),
        SimApplianceType.BLENDER to stateFromWizard(SimApplianceType.BLENDER, ApplianceType.BLENDER),
        SimApplianceType.STOVE to stateFromWizard(SimApplianceType.STOVE, ApplianceType.STOVE),
        SimApplianceType.OVEN to stateFromWizard(SimApplianceType.OVEN, ApplianceType.OVEN),
        SimApplianceType.AIR_FRYER to stateFromWizard(SimApplianceType.AIR_FRYER, ApplianceType.AIR_FRYER),
        SimApplianceType.RICE_COOKER to stateFromWizard(SimApplianceType.RICE_COOKER, ApplianceType.RICE_COOKER),
        SimApplianceType.PRESSURE_COOKER to stateFromWizard(SimApplianceType.PRESSURE_COOKER, ApplianceType.PRESSURE_COOKER),

        // Cooling & comfort — wizard's single "Fans" quantity maps onto the primary (ceiling)
        // fan type since the wizard never asked which kind.
        SimApplianceType.CEILING_FAN to stateFromWizard(SimApplianceType.CEILING_FAN, ApplianceType.FAN),
        SimApplianceType.STANDING_FAN to stateFromWizard(SimApplianceType.STANDING_FAN, ApplianceType.STANDING_FAN),
        SimApplianceType.BEDROOM_FAN to stateFromWizard(SimApplianceType.BEDROOM_FAN, ApplianceType.BEDROOM_FAN),
        // A68/Phase 28: AIR_CONDITIONER's own catalog watts (1500) only exists to give AC a duty-
        // cycle/schedule shape — the installer's real AC sizing is per-BTU-tier (inputs.ac.counts)
        // at the real per-tier BTU/W ratio for the selected inverter/non-inverter type
        // (SystemCalculator.acBtuPerWatt — one source of truth, see that function's own doc).
        // Blending it into one real average watts-per-unit (rather than the flat placeholder)
        // keeps the TOTAL AC load correct even across a mixed BTU selection, since the total is a
        // linear sum either way — see ApplianceState.wattsOverride's own doc for the bug this fixes.
        // Phase 28: deliberately the full rated (peak) per-unit watts, NOT reduced by
        // SystemCalculator's own INVERTER_PART_LOAD_AVERAGE_FACTOR — wattsOverride also feeds
        // worstCaseStartupSurgeKw's surge-current estimate, which needs the real rated draw, not a
        // modulated average. This means an inverter-type AC's live simulation dial runs its energy
        // total slightly warm (closer to rated than its true average) versus the sizing
        // calculation's own reduced daily-kWh figure — a known, disclosed scope boundary (the
        // conservative direction, not an understatement) rather than a second wattsOverride-shaped
        // field threading the part-load factor through the dial's separate energy integration.
        SimApplianceType.AIR_CONDITIONER to run {
            val totalAcUnits = inputs.ac.counts.values.sum()
            val btuPerWatt = SystemCalculator.acBtuPerWatt(inputs.ac.acType)
            val totalAcWatts = inputs.ac.counts.entries.sumOf { (btu, count) -> (btu / btuPerWatt) * count }
            val avgAcWattsPerUnit = if (totalAcUnits > 0) totalAcWatts / totalAcUnits else null
            stateFor(
                SimApplianceType.AIR_CONDITIONER,
                quantity = totalAcUnits.coerceAtLeast(1),
                enabled = inputs.ac.hasAc,
                wattsOverride = avgAcWattsPerUnit
            )
        },

        // Lighting — the wizard's generic "Lights"/"Outdoor Lights" map onto one representative
        // indoor/outdoor fixture type each rather than fanning one quantity across every room.
        SimApplianceType.LED_LIVING to stateFromWizard(SimApplianceType.LED_LIVING, ApplianceType.LIGHTS),
        SimApplianceType.LED_EXTERIOR to stateFromWizard(SimApplianceType.LED_EXTERIOR, ApplianceType.OUTDOOR_LIGHTS),
        SimApplianceType.LED_BEDROOM to stateFromWizard(SimApplianceType.LED_BEDROOM, ApplianceType.LED_BEDROOM),
        SimApplianceType.LED_KITCHEN to stateFromWizard(SimApplianceType.LED_KITCHEN, ApplianceType.LED_KITCHEN),
        SimApplianceType.LED_BATHROOM to stateFromWizard(SimApplianceType.LED_BATHROOM, ApplianceType.LED_BATHROOM),
        SimApplianceType.OUTDOOR_FLOODLIGHT to stateFromWizard(SimApplianceType.OUTDOOR_FLOODLIGHT, ApplianceType.OUTDOOR_FLOODLIGHT),

        // Electronics & networking
        SimApplianceType.TELEVISION to stateFromWizard(SimApplianceType.TELEVISION, ApplianceType.TV),
        SimApplianceType.DESKTOP_COMPUTER to stateFromWizard(SimApplianceType.DESKTOP_COMPUTER, ApplianceType.COMPUTER),
        SimApplianceType.GAME_CONSOLE to stateFromWizard(SimApplianceType.GAME_CONSOLE, ApplianceType.GAMING_CONSOLE),
        SimApplianceType.SET_TOP_BOX to stateFromWizard(SimApplianceType.SET_TOP_BOX, ApplianceType.SET_TOP_BOX),
        SimApplianceType.WIFI_ROUTER to stateFromWizard(SimApplianceType.WIFI_ROUTER, ApplianceType.WIFI_ROUTER),
        SimApplianceType.MODEM to stateFromWizard(SimApplianceType.MODEM, ApplianceType.MODEM),
        SimApplianceType.PHONE_CHARGERS to stateFromWizard(SimApplianceType.PHONE_CHARGERS, ApplianceType.PHONE_CHARGERS),
        SimApplianceType.LAPTOP to stateFromWizard(SimApplianceType.LAPTOP, ApplianceType.LAPTOP),
        SimApplianceType.PRINTER to stateFromWizard(SimApplianceType.PRINTER, ApplianceType.PRINTER),
        SimApplianceType.SOUND_SYSTEM to stateFromWizard(SimApplianceType.SOUND_SYSTEM, ApplianceType.SOUND_SYSTEM),

        // Water & heating
        SimApplianceType.WATER_HEATER to stateFromWizard(SimApplianceType.WATER_HEATER, ApplianceType.WATER_HEATER),
        SimApplianceType.WATER_PUMP to stateFromWizard(SimApplianceType.WATER_PUMP, ApplianceType.WATER_PUMP),
        SimApplianceType.INSTANT_SHOWER to stateFromWizard(SimApplianceType.INSTANT_SHOWER, ApplianceType.INSTANT_SHOWER),

        // Personal care
        SimApplianceType.HAIR_DRYER to stateFromWizard(SimApplianceType.HAIR_DRYER, ApplianceType.HAIR_DRYER),
        SimApplianceType.CURLING_IRON to stateFromWizard(SimApplianceType.CURLING_IRON, ApplianceType.CURLING_IRON),

        // Laundry
        SimApplianceType.IRON to stateFromWizard(SimApplianceType.IRON, ApplianceType.IRON),
        SimApplianceType.WASHING_MACHINE to stateFromWizard(SimApplianceType.WASHING_MACHINE, ApplianceType.WASHER),
        SimApplianceType.CLOTHES_DRYER to stateFromWizard(SimApplianceType.CLOTHES_DRYER, ApplianceType.DRYER),

        // Cleaning & misc
        SimApplianceType.VACUUM_CLEANER to stateFromWizard(SimApplianceType.VACUUM_CLEANER, ApplianceType.VACUUM_CLEANER),
        SimApplianceType.SEWING_MACHINE to stateFromWizard(SimApplianceType.SEWING_MACHINE, ApplianceType.SEWING_MACHINE),

        // Security & access
        SimApplianceType.SECURITY_SYSTEM to stateFromWizard(SimApplianceType.SECURITY_SYSTEM, ApplianceType.SECURITY_SYSTEM),
        SimApplianceType.GATE_OPENER to stateFromWizard(SimApplianceType.GATE_OPENER, ApplianceType.GATE_OPENER),

        // EV & outdoor
        SimApplianceType.EV_CHARGER_L1 to stateFromWizard(SimApplianceType.EV_CHARGER_L1, ApplianceType.EV_CHARGER_L1),
        SimApplianceType.EV_CHARGER_L2 to stateFromWizard(SimApplianceType.EV_CHARGER_L2, ApplianceType.EV_CHARGER_L2),
        SimApplianceType.POOL_PUMP to stateFromWizard(SimApplianceType.POOL_PUMP, ApplianceType.POOL_PUMP)
    )
}

/**
 * The real default schedule's total "effective" daily hours for [type] — each run's own
 * duration, tapered by [SimApplianceType.dutyFactor] — on [dayType]. Multiplying this by a
 * per-unit wattage gives real daily energy without assuming the schedule's own catalog wattage;
 * this is what lets a caller combine "this type's real timing/duty-cycle shape" with a different,
 * more specific wattage figure (e.g. a wizard's own per-BTU-tier AC wattage).
 */
fun defaultEffectiveDailyHours(type: SimApplianceType, dayType: DayType = DayType.WEEKDAY): Double =
    defaultScheduleFor(type).filter { dayType in it.dayTypes }.sumOf { it.durationHours * type.dutyFactor }

/**
 * Real default daily energy for [quantity] units of [type] on [dayType] — the exact same
 * schedule/duty-cycle model [totalApplianceLoadKwAt] uses for the simulation, so a caller sizing
 * a system from this (e.g. the estimator wizard) can never disagree with what the simulation
 * itself will actually show for the same appliance selection.
 */
fun defaultDailyEnergyKwh(type: SimApplianceType, quantity: Int, dayType: DayType = DayType.WEEKDAY): Double {
    if (quantity <= 0) return 0.0
    return quantity * type.watts / 1000.0 * defaultEffectiveDailyHours(type, dayType)
}

/**
 * Phase 28 (§5 "Estimated operating hours/week"): [type]'s own schedule's total ON-duration
 * across a real week (5x [DayType.WEEKDAY] + [DayType.SATURDAY] + [DayType.SUNDAY]) — the RAW
 * scheduled runtime, deliberately NOT tapered by [SimApplianceType.dutyFactor] (a distinct
 * question from [defaultDailyEnergyKwh]'s energy total — "how many hours is this on" versus "how
 * much energy did it use while on") and NOT quantity-scaled (the schedule's own shape doesn't
 * change just because more units share it).
 */
fun defaultWeeklyOperatingHours(type: SimApplianceType): Double {
    val schedule = defaultScheduleFor(type)
    fun hoursOn(dayType: DayType) = schedule.filter { dayType in it.dayTypes }.sumOf { it.durationHours }
    return hoursOn(DayType.WEEKDAY) * 5 + hoursOn(DayType.SATURDAY) + hoursOn(DayType.SUNDAY)
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
        activeQty * (state.wattsOverride ?: type.watts.toDouble()) * type.dutyFactor / 1000.0
    }

/** Splits the appliance load active at [hour] on [dayType] by [ElectricalTier], for per-circuit current readings. */
fun applianceLoadKwByTierAt(states: Map<SimApplianceType, ApplianceState>, hour: Double, dayType: DayType = DayType.WEEKDAY): Map<ElectricalTier, Double> =
    states.entries.filter { it.value.enabled }
        .associate { (type, state) -> type to state.runs.filter { it.isActiveAt(hour, dayType) }.sumOf { it.quantity } }
        .filterValues { it > 0 }
        .entries.groupBy({ it.key.tier }, { it.key to it.value })
        .mapValues { (_, list) ->
            list.sumOf { (type, qty) -> qty * (states[type]?.wattsOverride ?: type.watts.toDouble()) * type.dutyFactor } / 1000.0
        }

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
        val kw = qty * (state.wattsOverride ?: type.watts.toDouble()) * type.dutyFactor / 1000.0
        if (legFor(type) == 0) l1 += kw else l2 += kw
    }
    return l1 to l2
}

/**
 * Phase 28 (§6 "Do not size the inverter simply by adding every appliance's maximum nameplate
 * wattage. Calculate realistic simultaneous usage based on overlapping schedules."): the coincident
 * load at [hour] if every appliance ACTIVE per its own schedule at that instant drew its full rated
 * watts. Deliberately NOT tapered by [SimApplianceType.dutyFactor], unlike [totalApplianceLoadKwAt]
 * (the right figure for AVERAGE energy) — a cycling load (fridge compressor, water heater element,
 * stove burner) genuinely draws close to full power whenever its own schedule says it's "on," even
 * though its average-over-the-hour is lower; using the duty-tapered figure here would understate
 * true instantaneous draw. This is what answers "how much could realistically be drawing at once,"
 * the question inverter sizing actually needs — see [worstCaseCoincidentPeakKw].
 */
fun coincidentPeakLoadKwAt(states: Map<SimApplianceType, ApplianceState>, hour: Double, dayType: DayType = DayType.WEEKDAY): Double =
    states.entries.filter { it.value.enabled }.sumOf { (type, state) ->
        val activeQty = state.runs.filter { it.isActiveAt(hour, dayType) }.sumOf { it.quantity }
        activeQty * (state.wattsOverride ?: type.watts.toDouble()) / 1000.0
    }

/**
 * The worst realistic coincident peak across a full week — every day type swept (weekend-only
 * schedule additions, e.g. the Saturday midday ceiling-fan/TV/stove blocks, can coincide into a
 * higher peak than any weekday moment), sampled every 15 minutes. This is the SIZING-relevant
 * figure [com.lumix.estimator.domain.SystemCalculator] reads for [com.lumix.estimator.domain
 * .QuoteResult.peakWatts] — distinct from [ApplianceLoadShape.peakKw] (duty-cycle-scaled, for the
 * wizard's load-audit preview's average-shape view, not for sizing).
 */
fun worstCaseCoincidentPeakKw(states: Map<SimApplianceType, ApplianceState>): Double =
    DayType.entries.maxOf { dayType ->
        generateSequence(0.0) { it + 0.25 }.takeWhile { it < 24.0 }
            .maxOf { hour -> coincidentPeakLoadKwAt(states, hour, dayType) }
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
        activeQty * (state.wattsOverride ?: type.watts.toDouble()) * type.startupSurgeMultiplier / 1000.0
    }

/**
 * "HOW WAS THIS CALCULATED?" (spec §46): each category's real contribution to daily energy, in
 * kWh — an exact analytic sum over every enabled run's own quantity/watts/dutyFactor/duration
 * (each run genuinely is "on" for its declared window, so summing directly is exact; no timestep
 * discretization to introduce error), scoped to the runs that actually apply on [dayType].
 * Categories with zero enabled load are omitted rather than shown as a zero row.
 */
/**
 * The appliance-only load curve's real shape — peak instant, when it happens, the evening-window
 * peak specifically, and the overnight/base floor — computed directly from the household's
 * appliance selection via [defaultApplianceStates] and [totalApplianceLoadKwAt], before any
 * PV/inverter/battery sizing exists yet. Lets a design flow show "here's what your load actually
 * looks like" (spec: LOAD-BASED mode's automatic load audit) without a second calculation engine
 * — this is the exact same per-appliance schedule data [applianceDailyEnergyByCategoryKwh] and the
 * simulation itself use, just sampled across the day instead of summed for one instant or one total.
 */
data class ApplianceLoadShape(
    val peakKw: Double,
    val peakHour: Double,
    val eveningPeakKw: Double,
    val baseLoadKw: Double
)

fun previewLoadShape(inputs: QuoteInputs, dayType: DayType = DayType.WEEKDAY): ApplianceLoadShape {
    val states = defaultApplianceStates(inputs)
    val samples = generateSequence(0.0) { it + 0.25 }.takeWhile { it < 24.0 }
        .map { hour -> hour to totalApplianceLoadKwAt(states, hour, dayType) }
        .toList()
    if (samples.isEmpty()) return ApplianceLoadShape(0.0, 0.0, 0.0, 0.0)
    val (peakHour, peakKw) = samples.maxBy { it.second }
    val eveningPeakKw = samples.filter { it.first in 17.0..22.0 }.maxOfOrNull { it.second } ?: 0.0
    val baseLoadKw = samples.minOf { it.second }
    return ApplianceLoadShape(peakKw = peakKw, peakHour = peakHour, eveningPeakKw = eveningPeakKw, baseLoadKw = baseLoadKw)
}

fun applianceDailyEnergyByCategoryKwh(states: Map<SimApplianceType, ApplianceState>, dayType: DayType = DayType.WEEKDAY): Map<String, Double> =
    states.entries.filter { it.value.enabled }
        .flatMap { (type, state) ->
            val watts = state.wattsOverride ?: type.watts.toDouble()
            state.runs.filter { dayType in it.dayTypes }
                .map { run -> type.category to (run.quantity * watts * type.dutyFactor * run.durationHours / 1000.0) }
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, contributions) -> contributions.sum() }
        .filterValues { it > 0.0 }
