package com.appfeedback.core

import kotlin.test.Test
import kotlin.test.assertEquals

class DeterministicByteCountTest {
    @Test fun bytes() {
        assertEquals("0 B", DeterministicByteCount.string(0))
        assertEquals("0 B", DeterministicByteCount.string(-5))
        assertEquals("512 B", DeterministicByteCount.string(512))
        assertEquals("999 B", DeterministicByteCount.string(999))
    }

    @Test fun kilobytes_half_up() {
        assertEquals("1 KB", DeterministicByteCount.string(1000))
        assertEquals("1.2 KB", DeterministicByteCount.string(1234))
        assertEquals("1.1 KB", DeterministicByteCount.string(1050))
        assertEquals("1.5 KB", DeterministicByteCount.string(1500))
        assertEquals("4.1 KB", DeterministicByteCount.string(4096))
        assertEquals("319.5 KB", DeterministicByteCount.string(319_488))
    }

    @Test fun mb_gb_and_no_carry_and_overflow_guard() {
        assertEquals("2 MB", DeterministicByteCount.string(2_000_000))
        assertEquals("1.5 MB", DeterministicByteCount.string(1_500_000))
        assertEquals("1000 KB", DeterministicByteCount.string(999_999))   // no carry to MB
        assertEquals("1 GB", DeterministicByteCount.string(1_000_000_000)) // would overflow 32-bit *10
    }
}
