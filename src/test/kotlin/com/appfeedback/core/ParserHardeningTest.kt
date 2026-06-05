package com.appfeedback.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ParserHardeningTest {
    @Test fun non_finite_and_oversize_are_null() {
        assertNull(IssueBodyParser.parseHumanByteCount("Infinity KB"))
        assertNull(IssueBodyParser.parseHumanByteCount("NaN B"))
        assertNull(IssueBodyParser.parseHumanByteCount("10000000000 GB"))
        assertEquals(3_000_000_000L, IssueBodyParser.parseHumanByteCount("3 GB"))
    }
    @Test fun infer_mime_strips_query_string() {
        assertEquals("image/png", IssueBodyParser.inferMimeFromUrl("https://e.com/shot.png?token=abc"))
    }
}
