package com.pitwall.bridge.ktor.embedded

/**
 * Parses SocketCAN **slcan** ASCII lines (e.g. from CANable / USB-serial adapters).
 *
 * Standard frame: `t` + 3 hex (11-bit ID) + 1 hex (DLC) + `2*DLC` hex payload digits.
 * Extended frame: `T` + 8 hex (29-bit ID) + 1 hex (DLC) + payload.
 *
 * @see [Linux slcan documentation](https://www.kernel.org/doc/html/latest/networking/can/slcan.html)
 */
object SlcanParser {

    data class Frame(
        /** 11-bit or 29-bit CAN identifier */
        val id: Long,
        val extended: Boolean,
        val dlc: Int,
        val data: ByteArray,
    )

    fun parseLine(line: String): Frame? {
        val s = line.trim().trimEnd('\r')
        if (s.length < 5) return null
        return when (s[0]) {
            't' -> parseStd(s)
            'T' -> parseExt(s)
            else -> null
        }
    }

    private fun parseStd(s: String): Frame? {
        if (s.length < 5) return null
        val id = s.substring(1, 4).toIntOrNull(16) ?: return null
        if (id > 0x7FF) return null
        val dlc = s[4].digitToIntOrNull(16) ?: return null
        if (dlc !in 0..8) return null
        val need = 5 + 2 * dlc
        if (s.length < need) return null
        val hexPayload = s.substring(5, need)
        val data = hexToBytes(hexPayload) ?: return null
        return Frame(id.toLong(), extended = false, dlc = dlc, data = data)
    }

    private fun parseExt(s: String): Frame? {
        if (s.length < 10) return null
        val id = s.substring(1, 9).toLongOrNull(16) ?: return null
        if (id > 0x1FFFFFFF) return null
        val dlc = s[9].digitToIntOrNull(16) ?: return null
        if (dlc !in 0..8) return null
        val need = 10 + 2 * dlc
        if (s.length < need) return null
        val hexPayload = s.substring(10, need)
        val data = hexToBytes(hexPayload) ?: return null
        return Frame(id, extended = true, dlc = dlc, data = data)
    }

    private fun hexToBytes(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        val out = ByteArray(hex.length / 2)
        var i = 0
        while (i < hex.length) {
            val b = hex.substring(i, i + 2).toIntOrNull(16) ?: return null
            out[i / 2] = b.toByte()
            i += 2
        }
        return out
    }
}
