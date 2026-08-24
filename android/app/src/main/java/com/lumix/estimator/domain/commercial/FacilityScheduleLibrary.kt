package com.lumix.estimator.domain.commercial

/**
 * Phase 47 (spec §5 — "School operating schedule should default to daytime operation"; §6 — "Call
 * centres must support: 8-hour shift, 12-hour shift, 24-hour operation, Multiple shifts"; §21 —
 * Commercial facility schedule): a facility-type-driven SUGGESTION for [BusinessHours], surfaced by
 * the wizard's `BusinessHoursSection` as an offer the installer applies explicitly — never written
 * automatically. [CommercialIndustrialDesign.businessHours] keeps its own Phase 28 generic default
 * (M-F 7am-6pm, Sat 8am-1pm, Sun closed) regardless of facility; this library only exists to suggest
 * a better starting point once a facility is actually chosen, exactly the same "ordering/suggestion
 * hint, not a forced default" contract [FacilityLoadLibrary] already documents for loads.
 *
 * Only facility types where the spec explicitly discusses hours (School, Call Centre) or where a
 * suggestion is an obvious, uncontroversial improvement on the flat generic default (24-hour
 * operations whose own load list already includes round-the-clock loads — Hotel, Gas Station,
 * Data/IT Facility; a restaurant's later hours) get an entry here. Every other facility type returns
 * null — falling back to the plain Phase 28 default is entirely correct for them, not a gap.
 *
 * Industrial needs no equivalent: [IndustrialShiftSchedule] already ships with zero default shifts
 * per §21's own "DO NOT assume operating hours" (Phase 28 §1), so there is nothing to suggest.
 *
 * Deliberately NOT modeled here: §6's literal "8-hour shift / 12-hour shift / Multiple shifts"
 * granularity for Call Centre. [BusinessHours] is a single open/close window per day type — it can
 * represent 24-hour operation cleanly (open=0, close=24, used below), but not a named multi-shift
 * breakdown the way [IndustrialShiftSchedule] does for Industrial. Building a second, Commercial-only
 * multi-shift model was judged out of scope for this round (a real UI/domain addition, not a
 * defaults tweak) — the practical equivalent already available is per-load scheduling
 * ([LoadInstance]'s own multi-run editor, Phase 37), which lets shift-differentiated equipment usage
 * be modeled today even without a facility-level "Shift 1/2/3" construct for Commercial designs.
 */
object FacilityScheduleLibrary {

    fun suggestedBusinessHoursFor(type: CommercialFacilityType): BusinessHours? = when (type) {
        // §5: daytime, weekdays-only — no weekend operation assumed.
        CommercialFacilityType.SCHOOL, CommercialFacilityType.UNIVERSITY_COLLEGE -> BusinessHours(
            weekdayOpenHour = 7.5, weekdayCloseHour = 15.5,
            saturdayOpenHour = null, saturdayCloseHour = null,
            sundayOpenHour = null, sundayCloseHour = null
        )
        // §6: the closest a single open/close window can represent "24-hour operation" — see this
        // object's own doc for why the fuller 8hr/12hr/multi-shift breakdown isn't modeled here.
        CommercialFacilityType.CALL_CENTRE,
        CommercialFacilityType.HOTEL_GUEST_HOUSE,
        CommercialFacilityType.GAS_STATION,
        CommercialFacilityType.DATA_IT_FACILITY -> BusinessHours(
            weekdayOpenHour = 0.0, weekdayCloseHour = 24.0,
            saturdayOpenHour = 0.0, saturdayCloseHour = 24.0,
            sundayOpenHour = 0.0, sundayCloseHour = 24.0
        )
        CommercialFacilityType.RESTAURANT -> BusinessHours(
            weekdayOpenHour = 10.0, weekdayCloseHour = 22.0,
            saturdayOpenHour = 10.0, saturdayCloseHour = 23.0,
            sundayOpenHour = 11.0, sundayCloseHour = 21.0
        )
        CommercialFacilityType.CHURCH -> BusinessHours(
            weekdayOpenHour = null, weekdayCloseHour = null,
            saturdayOpenHour = null, saturdayCloseHour = null,
            sundayOpenHour = 8.0, sundayCloseHour = 13.0
        )
        else -> null
    }
}
