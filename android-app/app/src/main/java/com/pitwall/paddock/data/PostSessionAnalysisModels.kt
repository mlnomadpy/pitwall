package com.pitwall.paddock.data

/**
 * Reference catalog for post-session / “feedback after race” stats — aligned to PITWALL-style
 * module families (F1 data tooling). Bodies are summaries for the mobile shell; full charts
 * wire to Taha’s APIs when available.
 */
data class PostSessionGroup(
    val id: String,
    val title: String,
    val blurb: String,
    val modules: List<PostSessionModule>,
)

data class PostSessionModule(
    val id: String,
    val code: String,
    val title: String,
    val body: String,
    /** Suggested “stat” chips for future API wiring (display only) */
    val statHints: List<String> = emptyList(),
)

object PostSessionAnalysisCatalog {
    val groups: List<PostSessionGroup> = listOf(
        groupHistorical(),
        groupTelemetry(),
        groupLapPerformance(),
        groupIdealLapAndSectors(),
        groupSpeedCorner(),
        groupAiAndMulti(),
        groupLive(),
    )
}

private fun groupHistorical() = PostSessionGroup(
    id = "1",
    title = "1 · Historical Analysis",
    blurb = "Deep post-race analysis: strategy, tires, incidents, track and environment. PITWALL-style family.",
    modules = listOf(
        module(
            "1.1",
            "1.1 Temperature Analysis",
            """
            Environmental data for the full race weekend. Track and air temp plus humidity, pressure, and rainfall on one shared timeline. Correlate environment to lap time or tire wear; switch year/event; auto-scaling time axis.
            """.trimIndent(),
            listOf("Track ºC", "Air ºC", "Humidity", "Pressure", "Rain"),
        ),
        module(
            "1.2",
            "1.2 Track Analysis",
            """
            High-precision circuit map with corner numbers and DRS detection/activation. Vector geometry from the API, overlays for driver trajectories or speed heatmap. GPS→screen coordinate mapping for line and track work.
            """.trimIndent(),
            listOf("DRS", "Trajectories", "Speed overlay"),
        ),
        module(
            "1.3",
            "1.3 Pitstop Analysis",
            """
            Team summary and detailed log: stop count, average stationary time, total pit loss (in+stationary+out). Filters for normal stops vs drive-through; compound before/after each visit.
            """.trimIndent(),
            listOf("Pit count", "Avg stop", "Pit loss", "Compounds"),
        ),
        module(
            "1.4",
            "1.4 Accident & Flags",
            """
            Yellow / Red / VSC / SC on a Gantt-style timeline (duration, lap). Links race rhythm, strategy windows, effective racing laps; correlate with DNF where available.
            """.trimIndent(),
            listOf("VSC/SC", "Lap of event"),
        ),
        module(
            "1.5",
            "1.5 Tire Strategy",
            """
            Horizontal stint bars per driver: segment = set, color = compound, length = stint. Avg lap and degradation per stint; new vs used. Compare one-stop vs multi-stop.
            """.trimIndent(),
            listOf("Stint bars", "Degradation", "Compound color"),
        ),
        module(
            "1.6",
            "1.6 Driver Run Position",
            """
            Position vs lap for the full race. DNF handling. Interactive legend to filter drivers (e.g. top-3 or midfield) from position-by-lap data.
            """.trimIndent(),
            listOf("Pos vs lap", "DNF-safe", "Filter"),
        ),
        module(
            "1.7",
            "1.7 Traffic Analysis",
            """
            Gaps to cars ahead/behind: clean air vs wake/dirty air. Often heatmap or interval chart — % of race in traffic. Informs true pace and pit-window choice.
            """.trimIndent(),
            listOf("Clean vs traffic", "% race"),
        ),
    ),
)

private fun groupTelemetry() = PostSessionGroup(
    id = "2",
    title = "2 · Telemetry Analysis",
    blurb = "Sample-rate (Hz) vehicle physics: driving style and car performance. PITWALL-style family.",
    modules = listOf(
        module(
            "2.1",
            "2.1 Main Telemetry",
            """
            Synchronized multi-channel: speed, RPM, gear, throttle, brake, DRS. Multi-driver overlay; cursor sync across charts.
            """.trimIndent(),
            listOf("Speed", "Brake", "T", "DRS", "Sync cursor"),
        ),
        module(
            "2.2a",
            "2.2 Speed / Brake / Throttle / Gears / Accel",
            """
            Deeper per-channel: speed (min corner / end straight), brake pressure and duration, throttle and traction, gear+RPM, longitudinal/lateral g (G-G diagram / grip).
            """.trimIndent(),
            listOf("Speed", "Brake", "T%", "G-G"),
        ),
        module(
            "2.3",
            "2.3 Delta Analysis",
            """
            Speed delta between two cars at same position; integrated gap vs distance; time-delta over lap vs a reference to see where time is won or lost.
            """.trimIndent(),
            listOf("Δv", "Δs", "Δt lap"),
        ),
    ),
)

private fun groupLapPerformance() = PostSessionGroup(
    id = "3",
    title = "3 · Lap Performance",
    blurb = "Lap time stats, consistency, and long runs. PITWALL-style family.",
    modules = listOf(
        module("3.1", "3.1 Detailed Lap Data", "Lap/sector table: lap time, S1–S3, trap, compound+age, abnormal laps, PB/OB color.", listOf("S1-S3", "Compound", "Flags")),
        module("3.2", "3.2 Lap Time Box Plot", "Distribution: median, IQR, outliers; filter pit/traffic for true race pace. Narrower box = more consistent.", listOf("Median", "IQR", "Outliers")),
        module("3.3", "3.3 Throttle–Corner", "Throttle metrics through named corners: avg opening or full-throttle ratio; box or line. Confidence, grip, exit T%.", listOf("Per corner", "T ratio")),
        module("3.4", "3.4 Pedal Behavior", "Stacked bar: Throttle only / Brake only / Trail (both) / Coast — lift-and-coast for fuel. Four-way classification across stint.", listOf("Trail %", "Coast %")),
        module("3.5", "3.5 Long Run (FP2-style)", "Stint detection, fuel-corrected laps to separate tire degradation from mass. Strategy input for race stops.", listOf("Fuel correct", "Deg rate")),
    ),
)

private fun groupIdealLapAndSectors() = PostSessionGroup(
    id = "4",
    title = "4 · Ideal Lap & Sectors",
    blurb = "Reconstruct from best sectors; theoretical limit vs actual.",
    modules = listOf(
        module("4.1", "4.1 Ideal Lap Ranking", "Theoretical best = best S1+S2+S3; vs pole, potential gap — mistake vs hidden pace in sectors.", listOf("Theoretical", "Potential gap")),
    ),
)

private fun groupSpeedCorner() = PostSessionGroup(
    id = "5",
    title = "5 · Speed & Corner",
    blurb = "Straights, braking, corner classification. PITWALL-style family.",
    modules = listOf(
        module("5.1", "5.1 Straight Line Speed", "Trap and top speed: drag and power. Filter DRS/tow for clean-air intrinsic pace.", listOf("Trap", "Top speed")),
        module("5.2", "5.2 Brake & Accel", "Decel in heavy zones; long-g on exit. Mechanical grip vs braking. Stop-and-go vs flowing balance.", listOf("Decel", "Long g")),
        module("5.3", "5.3 Corner Class", "Low / mid / high speed: min apex speed by class — aero vs mechanical trade.", listOf("3 bands", "Min apex v")),
    ),
)

private fun groupAiAndMulti() = PostSessionGroup(
    id = "6-7",
    title = "6–7 · AI & Multi-Season",
    blurb = "Models and year-over-year context.",
    modules = listOf(
        module("6.1", "6.x FP/Quali / Race models", "Practice→quali regression; quali→race overtake sim from history — EV-style references.", listOf("Predict", "Overtake coeff")),
        module("7.1", "7.1 Historical Track Map", "Map + elevation + year flag stats; API incident X/Y; 4y macro counts (Y/R/SC/VSC).", listOf("Incidents", "4 seasons")),
        module("7.2", "7.2 Season Start Reaction", "0–50 km/h launch: box by driver; team colors; filter bad starts from rain or incidents.", listOf("0–50", "IQR")),
    ),
)

private fun groupLive() = PostSessionGroup(
    id = "8",
    title = "8 · Live timing (reference)",
    blurb = "For parity with full PITWALL — ranked tower, map, circle, weather, strategy, real-time traces, history tables, race control.",
    modules = listOf(
        module("8.1a", "8.1 Core live", "Ranking tower, live map, circle (gap) map, track & weather bar.", listOf("Tower", "Map", "Weather")),
        module("8.1b", "8.1 Strategy", "Driver strategy vs model, battle pair, chase-to-DRS, pit rejoin, tyre life.", listOf("Strategy", "Battle")),
        module("8.1c", "8.1 Live telemetry", "Traces, sector Δ, SF% fuel save history, top speed by lap (purple=PB).", listOf("Traces", "SF%")),
        module("8.1d", "8.1 History & control", "Lap tables, distribution, traffic heatmap, race-control feed.", listOf("Tables", "RC feed")),
    ),
)

private fun module(
    id: String,
    title: String,
    body: String,
    statHints: List<String>,
) = PostSessionModule(
    id = id,
    code = title.substringBefore(" ").ifEmpty { id },
    title = title,
    body = body,
    statHints = statHints,
)
