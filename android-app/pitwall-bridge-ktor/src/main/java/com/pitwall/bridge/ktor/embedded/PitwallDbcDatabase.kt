package com.pitwall.bridge.ktor.embedded

import java.nio.ByteBuffer
import java.nio.ByteOrder
/**
 * Minimal Vector DBC parser for **Intel** (`@1+` / `@1-`) signals — sufficient for
 * bundled `assets/pitwall.dbc` (AIM MXP SmartyCam).
 *
 * Full cantools parity (multiplex, Motorola, VAL_ tables) is intentionally out of scope.
 */
class PitwallDbcDatabase private constructor(
    private val messages: Map<Long, List<DbcSignal>>,
) {

    data class DbcSignal(
        val name: String,
        val startBit: Int,
        val bitLength: Int,
        /** Intel little-endian bit numbering (`@1`) */
        val intel: Boolean,
        val signed: Boolean,
        val factor: Double,
        val offset: Double,
    )

    fun decodeStandard(arbitrationId: Long, data: ByteArray): Map<String, Double>? {
        val defs = messages[arbitrationId] ?: return null
        val out = LinkedHashMap<String, Double>()
        for (sig in defs) {
            if (!sig.intel) continue // Motorola not used in pitwall.dbc
            val raw = extractIntelRaw(data, sig.startBit, sig.bitLength, sig.signed) ?: continue
            out[sig.name] = raw * sig.factor + sig.offset
        }
        return out
    }

    companion object {

        fun parse(dbcText: String): PitwallDbcDatabase {
            val byId = LinkedHashMap<Long, MutableList<DbcSignal>>()
            var currentId: Long? = null
            val boLine = Regex("""^BO_\s+(\d+)\s+\w+:\s+\d+\s+""")
            val sgLine =
                Regex(
                    """\s*SG_\s+(\w+)\s*:\s+(\d+)\|(\d+)@(\d)([+-])\s+\(([^,]+),([^)]+)\)""",
                )
            for (raw in dbcText.lines()) {
                val line = raw.trim()
                if (line.startsWith("BO_")) {
                    val m = boLine.find(line) ?: continue
                    currentId = m.groupValues[1].toLong()
                    continue
                }
                if (!line.startsWith("SG_")) continue
                val id = currentId ?: continue
                val m = sgLine.find(line) ?: continue
                val name = m.groupValues[1]
                val start = m.groupValues[2].toInt()
                val len = m.groupValues[3].toInt()
                val endianDigit = m.groupValues[4]
                val signChar = m.groupValues[5]
                val factor = m.groupValues[6].toDouble()
                val offset = m.groupValues[7].toDouble()
                val intel = endianDigit == "1"
                val signed = signChar == "-"
                byId.getOrPut(id) { mutableListOf() }
                    .add(DbcSignal(name, start, len, intel, signed, factor, offset))
            }
            return PitwallDbcDatabase(byId)
        }

        /**
         * Intel (little-endian) layout per Vector DBC: start_bit is the index of the
         * least-significant bit of the signal; consecutive bits increase toward MSB within
         * the signal. pitwall.dbc uses byte-aligned fields — fast path covers all AIM rows.
         */
        internal fun extractIntelRaw(data: ByteArray, startBit: Int, bitLength: Int, signed: Boolean): Double? {
            if (bitLength <= 0 || startBit < 0) return null
            // Aligned fast paths (pitwall.dbc uses only these)
            if (startBit % 8 == 0) {
                val byteIndex = startBit / 8
                when (bitLength) {
                    8 -> {
                        if (byteIndex >= data.size) return null
                        val v = data[byteIndex].toInt() and 0xFF
                        return if (signed) (v.toByte()).toDouble() else v.toDouble()
                    }
                    16 -> {
                        if (byteIndex + 1 >= data.size) return null
                        val bb = ByteBuffer.wrap(data, byteIndex, 2).order(ByteOrder.LITTLE_ENDIAN)
                        return if (signed) {
                            bb.short.toDouble()
                        } else {
                            (bb.short.toInt() and 0xFFFF).toDouble()
                        }
                    }
                    32 -> {
                        if (byteIndex + 3 >= data.size) return null
                        val bb = ByteBuffer.wrap(data, byteIndex, 4).order(ByteOrder.LITTLE_ENDIAN)
                        val i = bb.int
                        return i.toDouble()
                    }
                }
            }
            return extractIntelBitsSlow(data, startBit, bitLength, signed)
        }

        private fun extractIntelBitsSlow(data: ByteArray, startBit: Int, bitLength: Int, signed: Boolean): Double? {
            var value = 0L
            for (i in 0 until bitLength) {
                val bitPos = startBit + i
                val byteIndex = bitPos shr 3
                val bitInByte = bitPos and 7
                if (byteIndex >= data.size) return null
                val bit = ((data[byteIndex].toInt() ushr bitInByte) and 1).toLong()
                value = value or (bit shl i)
            }
            if (!signed) return value.toDouble()
            if (bitLength < 64) {
                val signBit = 1L shl (bitLength - 1)
                if ((value and signBit) != 0L) {
                    value = value or ((-1L) shl bitLength)
                }
            }
            return value.toDouble()
        }
    }
}
