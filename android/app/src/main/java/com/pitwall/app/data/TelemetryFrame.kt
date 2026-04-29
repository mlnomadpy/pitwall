package com.pitwall.app.data

/**
 * A signal value with confidence metadata.
 * Every signal from Racelogic and OBDLink carries this wrapper (ADR-001).
 */
data class SignalValue(
    val value: Float,
    val confidence: Float,          // 0.0–1.0
    val source: String,             // "racelogic:gps", "racelogic:imu", "obdlink:can", "fused:kalman"
    val hz: Float,                  // actual update rate
    val stale: Boolean = false,     // no update in >2× expected period
) {
    companion object {
        val UNKNOWN = SignalValue(0f, 0f, "unknown", 0f, stale = true)

        fun racelogicGps(value: Float, confidence: Float = 0.95f) =
            SignalValue(value, confidence, "racelogic:gps", 10f)

        fun racelogicImu(value: Float, confidence: Float = 0.95f) =
            SignalValue(value, confidence, "racelogic:imu", 10f)

        fun obdlinkCan(value: Float, confidence: Float = 0.95f) =
            SignalValue(value, confidence, "obdlink:can", 10f)

        fun fused(value: Float, source: String = "fused:kalman", confidence: Float = 0.95f) =
            SignalValue(value, confidence, source, 50f)
    }
}

data class TelemetryFrame(
    val timestamp: Double,
    val sources: List<String>,

    val latitude: SignalValue,
    val longitude: SignalValue,
    val speed: SignalValue,         // m/s
    val heading: SignalValue,

    val gLat: SignalValue,          // lateral G
    val gLong: SignalValue,         // longitudinal G
    val comboG: SignalValue,        // sqrt(gLat² + gLong²)

    val throttle: SignalValue,      // 0–100%
    val brake: SignalValue,         // bar (0–104)
    val rpm: SignalValue,
    val steering: SignalValue,
    val coolantTemp: SignalValue,
    val oilTemp: SignalValue,

    val distance: SignalValue,
    val cornerProximity: Float,
    val currentCorner: String?,
    val pastApex: Boolean,
    val sector: String?,
    val lap: Int,
    val lapTime: Float,
    val gear: Int,
) {
    val speedKmh get() = speed.value * 3.6f
    val speedMph get() = speed.value * 2.237f
    val inCorner get() = currentCorner != null
    val gripUsage get() = comboG.value / MAX_COMBO_G
    val isCoasting get() = throttle.value < 5f && brake.value < 2f

    /**
     * Serialise to a Map for the Flutter Platform Channel.
     * Flutter receives this as Map<String, Any?> and deserialises in Dart.
     */
    fun toChannelMap(): Map<String, Any?> = mapOf(
        "timestamp" to timestamp,
        "speedMs" to speed.value,
        "speedKmh" to speedKmh,
        "speedMph" to speedMph,
        "gLat" to gLat.value,
        "gLong" to gLong.value,
        "comboG" to comboG.value,
        "gripUsage" to gripUsage,
        "throttle" to throttle.value,
        "brake" to brake.value,
        "rpm" to rpm.value,
        "steering" to steering.value,
        "coolantTemp" to coolantTemp.value,
        "oilTemp" to oilTemp.value,
        "gear" to gear,
        "lap" to lap,
        "lapTime" to lapTime,
        "distance" to distance.value,
        "cornerProximity" to cornerProximity,
        "currentCorner" to currentCorner,
        "pastApex" to pastApex,
        "sector" to sector,
        "inCorner" to inCorner,
        "isCoasting" to isCoasting,
        "speedConf" to speed.confidence,
        "gLatConf" to gLat.confidence,
        "brakeConf" to brake.confidence,
    )

    companion object {
        const val MAX_COMBO_G = 2.29f
        const val MAX_BRAKE_BAR = 73.5f
        const val MAX_RPM = 8321f

        val GEAR_RATIOS = mapOf(1 to 13.17f, 2 to 8.09f, 3 to 5.77f, 4 to 4.52f, 5 to 3.68f, 6 to 3.09f)

        fun deriveGear(speedMs: Float, rpm: Float): Int {
            if (speedMs < 2f || rpm < 500f) return 0
            val wheelCircumference = 2 * Math.PI.toFloat() * 0.315f
            val ratio = rpm / (speedMs * 60f / wheelCircumference)
            return GEAR_RATIOS.minByOrNull { (_, r) -> Math.abs(r - ratio) }?.key ?: 0
        }
    }
}

data class RawFrame(
    val timestamp: Double,
    val source: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val speedMs: Float? = null,
    val heading: Float? = null,
    val gLat: Float? = null,
    val gLong: Float? = null,
    val comboG: Float? = null,
    val distance: Float? = null,
    val satellites: Int? = null,
    val throttle: Float? = null,
    val brakePressure: Float? = null,
    val brakePosition: Float? = null,
    val rpm: Float? = null,
    val steering: Float? = null,
    val coolantTemp: Float? = null,
    val oilTemp: Float? = null,
    val oilPressure: Float? = null,
    val fuelLevel: Float? = null,
    val batteryVoltage: Float? = null,
)
