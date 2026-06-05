package com.appfeedback.core

/** Locale-invariant, deterministic human-readable byte-count formatter.
 *  Port of the Swift `DeterministicByteCount`. Uses `Long` arithmetic because
 *  Kotlin `Int` is 32-bit and `bytes * 10` overflows at GB scale. Decimal
 *  (1000-based) units; unit chosen by magnitude, then rounded half-up within
 *  it (never re-promoted), so 999_999 -> "1000 KB". Pinned by the wire spec. */
object DeterministicByteCount {
    fun string(bytes: Int): String {
        val b = maxOf(0, bytes).toLong()
        val units = listOf("GB" to 1_000_000_000L, "MB" to 1_000_000L, "KB" to 1_000L)
        for ((name, factor) in units) {
            if (b >= factor) {
                val tenths = (b * 10 + factor / 2) / factor   // half-up, Long
                val whole = tenths / 10
                val frac = tenths % 10
                return if (frac == 0L) "$whole $name" else "$whole.$frac $name"
            }
        }
        return "$b B"
    }
}
