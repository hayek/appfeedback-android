package com.appfeedback.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IssueBodyParserTest {
    @Test fun parses_device_block_and_email() {
        val raw = "Desc line.\n\n---\n**Device Information:**\nApp: Acme\nApp Version: 1.0 (1)\nDevice: Pixel 8\nAndroid Version: 14\n\n**Contact Email:**\nme@example.com\n\n---\n👍 Votes: 0"
        val p = IssueBodyParser.parse(raw)
        assertEquals("Desc line.", p.description)
        assertEquals("Acme", p.appName)
        assertEquals("1.0 (1)", p.appVersion)
        assertEquals("Pixel 8", p.device)
        assertEquals("14", p.osVersion)
        assertEquals("me@example.com", p.email)
    }

    @Test fun inline_email_and_no_email() {
        val inline = IssueBodyParser.parse("d\n\n---\n**Device Information:**\nApp: A\nApp Version: 1 (1)\nDevice: M\nWeb Version: Chrome 120\n**Contact Email:** x@y.com")
        assertEquals("x@y.com", inline.email)
        val none = IssueBodyParser.parse("d\n\n---\n**Device Information:**\nApp: A\nApp Version: 1 (1)\nDevice: M\niOS Version: 18.0\n\n---\n👍 Votes: 0")
        assertNull(none.email)
    }

    @Test fun parses_attachments() {
        val raw = "d\n\n---\n**Device Information:**\nApp: A\nApp Version: 1 (1)\nDevice: M\nmacOS Version: 15.1\n\n<!-- attachments-v1 -->\n## Attachments\n\n![shot.png](https://e.com/shot.png) — image/png, 312 KB\n\n[log.txt](https://e.com/log.txt) — text/plain, 4.1 KB\n\n<!-- /attachments-v1 -->\n\n---\n👍 Votes: 0"
        val p = IssueBodyParser.parse(raw)
        assertEquals(2, p.attachments.size)
        assertEquals("shot.png", p.attachments[0].filename)
        assertEquals("image/png", p.attachments[0].mimeType)
        assertEquals(312000, p.attachments[0].sizeBytes)
        assertEquals("text/plain", p.attachments[1].mimeType)
        assertEquals(4100, p.attachments[1].sizeBytes)
    }
}
