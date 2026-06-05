# P1a — Android Core (Kotlin) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Port the AppFeedback wire-format logic to an idiomatic, pure-Kotlin/JVM core that passes the same language-neutral golden-fixture conformance suite as the Swift reference — proving byte-exact cross-platform output.

**Architecture:** A single Kotlin/JVM Gradle library (`appfeedback-android`, package `com.appfeedback.core`) holding the data models, `DeterministicByteCount`, `IssueBodyFormatter` (+ code-point ordering), `IssueBodyParser`, `BodyMarker`, and a `FeedbackTransport` interface. No Android-platform APIs yet (DeviceInfo collection via `Build.*`, the concrete HTTP transport, attachment upload, and the Compose UI are P1b; Maven publishing is P1c). Correctness is gated by a Kotlin `ConformanceTest` that runs the canonical `appfeedback-spec` fixtures.

**Tech Stack:** Kotlin 2.2.20 (JVM), Gradle 9.5.1, JDK 26 compiling to **JVM 17 bytecode** (Java + Kotlin targets MUST be aligned or the build fails — JDK 26 is the only JDK installed), JUnit Platform via `kotlin("test")`, Gson (test-only) for fixture decoding.

**Reference:** Swift source at `/Users/amir/Developer/AppFeedbackSDK/Sources/AppFeedbackCore/`; canonical spec + fixtures at `/Users/amir/Developer/appfeedback-spec/`.

---

## Conventions

- Repo root: `/Users/amir/Developer/appfeedback-android` (NEW repo).
- Shell cwd resets between commands — prefix every command with `cd /Users/amir/Developer/appfeedback-android &&`.
- Build/test: `JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home ./gradlew test --console=plain` (the wrapper, created in Task 0, pins Gradle 9.5.1). The `gradlew` wrapper bootstraps via `java`, which is NOT on PATH, so `JAVA_HOME` MUST be set on every gradle command (use the brew OpenJDK 26 path shown). All gradle commands in the steps below assume this prefix.
- All source files are UTF-8. The em-dash `—` (U+2014) and `👍` emoji are part of the wire format — preserve them byte-for-byte.

## File structure

```
appfeedback-android/
├── settings.gradle.kts
├── build.gradle.kts
├── gradlew, gradlew.bat, gradle/wrapper/…        (Task 0)
├── .gitignore
├── src/main/kotlin/com/appfeedback/core/
│   ├── FeedbackType.kt          enum bug | feature-request (+ fromRawValue)
│   ├── FeedbackReport.kt        data: type/title/description/contactEmail/extraFields
│   ├── DeviceInfo.kt            data + renderForIssueBody()
│   ├── UploadedAttachment.kt    data: filename/mimeType/sizeBytes/url
│   ├── ParsedModels.kt          ParsedFeedbackBody + ParsedAttachment
│   ├── BodyMarker.kt            wire constants + recognisedOSNames + osVersionRegex
│   ├── DeterministicByteCount.kt
│   ├── IssueBodyFormatter.kt    format() + labels() + codePointOrder()
│   ├── IssueBodyParser.kt       parse() + helpers
│   └── FeedbackTransport.kt     interface (suspend submit)
└── src/test/
    ├── resources/conformance/format-cases.json, parse-cases.json   (vendored)
    └── kotlin/com/appfeedback/core/
        ├── DeterministicByteCountTest.kt
        ├── IssueBodyFormatterTest.kt
        ├── IssueBodyParserTest.kt
        └── ConformanceTest.kt    ← the gate
```

---

### Task 0: Scaffold the Gradle project

**Files:** `settings.gradle.kts`, `build.gradle.kts`, `.gitignore`, the Gradle wrapper, a temporary smoke test, vendored fixtures.

- [ ] **Step 1: Create `settings.gradle.kts`**

```kotlin
rootProject.name = "appfeedback-android"
```

- [ ] **Step 2: Create `build.gradle.kts`**

```kotlin
plugins {
    kotlin("jvm") version "2.2.20"
}

group = "io.github.hayek"          // finalized in P1c (publishing)
version = "0.1.0-SNAPSHOT"

repositories { mavenCentral() }

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("com.google.code.gson:gson:2.11.0")
}

// Only JDK 26 is installed; compile on it but emit broadly-compatible JVM 17
// bytecode. The Java and Kotlin target levels MUST match or Gradle fails with
// "Inconsistent JVM Target Compatibility".
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.test { useJUnitPlatform() }
```

- [ ] **Step 3: Create `.gitignore`**

```gitignore
.gradle/
build/
*.iml
.idea/
.DS_Store
```

- [ ] **Step 4: Generate the Gradle wrapper (pins Gradle 9.5.1)**

Run:
```bash
cd /Users/amir/Developer/appfeedback-android && gradle wrapper --gradle-version 9.5.1 2>&1 | tail -5
```
Expected: creates `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`.

- [ ] **Step 5: Vendor the canonical conformance fixtures**

```bash
mkdir -p /Users/amir/Developer/appfeedback-android/src/test/resources/conformance
cp /Users/amir/Developer/appfeedback-spec/fixtures/format-cases.json /Users/amir/Developer/appfeedback-android/src/test/resources/conformance/format-cases.json
cp /Users/amir/Developer/appfeedback-spec/fixtures/parse-cases.json /Users/amir/Developer/appfeedback-android/src/test/resources/conformance/parse-cases.json
```

- [ ] **Step 6: Add a temporary smoke test**

Create `src/test/kotlin/com/appfeedback/core/SmokeTest.kt`:

```kotlin
package com.appfeedback.core

import kotlin.test.Test
import kotlin.test.assertTrue

class SmokeTest {
    @Test fun toolchain_runs() { assertTrue(true) }
}
```

- [ ] **Step 7: Verify the toolchain builds and tests**

Run:
```bash
cd /Users/amir/Developer/appfeedback-android && ./gradlew test --console=plain 2>&1 | tail -12
```
Expected: `BUILD SUCCESSFUL`, smoke test passes. If it fails on JVM-target mismatch, confirm the `java {}` block from Step 2 is present.

- [ ] **Step 8: Initialise git and commit**

```bash
cd /Users/amir/Developer/appfeedback-android && git init -q && git add -A && git commit -q -m "chore: scaffold Kotlin/JVM appfeedback core project" && git log --oneline -1
```

---

### Task 1: Data models + BodyMarker

**Files:** Create `FeedbackType.kt`, `FeedbackReport.kt`, `DeviceInfo.kt`, `UploadedAttachment.kt`, `ParsedModels.kt`, `BodyMarker.kt`. Test: `src/test/kotlin/com/appfeedback/core/ModelsTest.kt`.

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/appfeedback/core/ModelsTest.kt`:

```kotlin
package com.appfeedback.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModelsTest {
    @Test fun feedback_type_raw_values() {
        assertEquals("bug", FeedbackType.BUG.rawValue)
        assertEquals("feature-request", FeedbackType.FEATURE_REQUEST.rawValue)
        assertEquals(FeedbackType.FEATURE_REQUEST, FeedbackType.fromRawValue("feature-request"))
        assertNull(FeedbackType.fromRawValue("nope"))
    }

    @Test fun device_info_renders_block() {
        val d = DeviceInfo("Acme", "1.2.3", "456", "iPhone15,2", "iOS", "Version 18.2 (Build 22C150)")
        assertEquals(
            "App: Acme\nApp Version: 1.2.3 (456)\nDevice: iPhone15,2\niOS Version: Version 18.2 (Build 22C150)",
            d.renderForIssueBody()
        )
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
cd /Users/amir/Developer/appfeedback-android && ./gradlew test --tests "com.appfeedback.core.ModelsTest" --console=plain 2>&1 | tail -12
```
Expected: compile failure (unresolved references).

- [ ] **Step 3: Create the model files**

`src/main/kotlin/com/appfeedback/core/FeedbackType.kt`:
```kotlin
package com.appfeedback.core

/** Bug report or feature request. The raw value is also the GitHub label string. */
enum class FeedbackType(val rawValue: String) {
    BUG("bug"),
    FEATURE_REQUEST("feature-request");

    companion object {
        fun fromRawValue(value: String): FeedbackType? = entries.firstOrNull { it.rawValue == value }
    }
}
```

`src/main/kotlin/com/appfeedback/core/FeedbackReport.kt`:
```kotlin
package com.appfeedback.core

/** A single user-supplied feedback submission. Attachments (raw files) and the
 *  device-info collector are platform concerns handled in later phases. */
data class FeedbackReport(
    val type: FeedbackType,
    val title: String,
    val description: String,
    val contactEmail: String? = null,
    val extraFields: Map<String, String> = emptyMap(),
)
```

`src/main/kotlin/com/appfeedback/core/DeviceInfo.kt`:
```kotlin
package com.appfeedback.core

/** Per-submission device/app metadata. `current()` (via android.os.Build) is added in P1b. */
data class DeviceInfo(
    val appName: String,
    val appVersion: String,
    val buildNumber: String,
    val model: String,
    val osName: String,
    val osVersion: String,
) {
    fun renderForIssueBody(): String =
        "${BodyMarker.APP_LABEL} $appName\n" +
        "${BodyMarker.APP_VERSION_LABEL} $appVersion ($buildNumber)\n" +
        "${BodyMarker.DEVICE_LABEL} $model\n" +
        "$osName${BodyMarker.OS_VERSION_SUFFIX} $osVersion"
}
```

`src/main/kotlin/com/appfeedback/core/UploadedAttachment.kt`:
```kotlin
package com.appfeedback.core

/** An attachment already uploaded; drives the body's attachment block. */
data class UploadedAttachment(
    val filename: String,
    val mimeType: String,
    val sizeBytes: Int,
    val url: String,
)
```

`src/main/kotlin/com/appfeedback/core/ParsedModels.kt`:
```kotlin
package com.appfeedback.core

data class ParsedAttachment(
    val filename: String,
    val mimeType: String,
    val url: String,
    val sizeBytes: Int?,
)

data class ParsedFeedbackBody(
    val description: String = "",
    val appName: String? = null,
    val appVersion: String? = null,
    val device: String? = null,
    val osVersion: String? = null,
    val email: String? = null,
    val attachments: List<ParsedAttachment> = emptyList(),
)
```

`src/main/kotlin/com/appfeedback/core/BodyMarker.kt`:
```kotlin
package com.appfeedback.core

/** The string literals that make up the wire contract. Mirrors the Swift
 *  `BodyMarker`; both ends read from one definition so they can't drift. */
internal object BodyMarker {
    const val DEVICE_HEADER = "Device Information:"
    const val APP_LABEL = "App:"
    const val APP_VERSION_LABEL = "App Version:"
    const val DEVICE_LABEL = "Device:"
    const val OS_VERSION_SUFFIX = " Version:"
    const val CONTACT_EMAIL_LABEL = "Contact Email:"
    const val HORIZONTAL_RULE = "---"
    const val VOTES_FOOTER = "👍 Votes: 0"
    const val ATTACHMENTS_OPEN = "<!-- attachments-v1 -->"
    const val ATTACHMENTS_CLOSE = "<!-- /attachments-v1 -->"
    const val ATTACHMENTS_HEADER = "## Attachments"

    val recognisedOSNames = listOf(
        "OS", "macOS", "iOS", "iPadOS", "watchOS", "tvOS", "visionOS",
        "Android", "Windows", "Linux", "Web", "ChromeOS",
    )

    val osVersionRegex = Regex(
        "^(${recognisedOSNames.joinToString("|")}) Version:",
        RegexOption.IGNORE_CASE,
    )
}
```

- [ ] **Step 4: Run to verify it passes**

```bash
cd /Users/amir/Developer/appfeedback-android && ./gradlew test --tests "com.appfeedback.core.ModelsTest" --console=plain 2>&1 | tail -8
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
cd /Users/amir/Developer/appfeedback-android && git add -A && git commit -q -m "feat(core): data models + BodyMarker wire constants"
```

---

### Task 2: DeterministicByteCount

**Files:** Create `DeterministicByteCount.kt`. Test: `DeterministicByteCountTest.kt`.

> **Critical gotcha:** Kotlin `Int` is 32-bit (Swift `Int` is 64-bit). `bytes * 10` overflows at GB scale, so the arithmetic MUST use `Long`. The test below includes `1_000_000_000` precisely to catch this — it is NOT in the JSON conformance corpus.

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/appfeedback/core/DeterministicByteCountTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run to verify it fails**

```bash
cd /Users/amir/Developer/appfeedback-android && ./gradlew test --tests "com.appfeedback.core.DeterministicByteCountTest" --console=plain 2>&1 | tail -10
```
Expected: compile failure (unresolved `DeterministicByteCount`).

- [ ] **Step 3: Implement**

Create `src/main/kotlin/com/appfeedback/core/DeterministicByteCount.kt`:

```kotlin
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
```

- [ ] **Step 4: Run to verify it passes**

```bash
cd /Users/amir/Developer/appfeedback-android && ./gradlew test --tests "com.appfeedback.core.DeterministicByteCountTest" --console=plain 2>&1 | tail -8
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
cd /Users/amir/Developer/appfeedback-android && git add -A && git commit -q -m "feat(core): deterministic byte-count formatter (Long arithmetic)"
```

---

### Task 3: IssueBodyFormatter + code-point ordering

**Files:** Create `IssueBodyFormatter.kt`. Test: `IssueBodyFormatterTest.kt`.

> **Gotcha:** `extraFields` ordering must be by Unicode **code point**, iterating `codePoints()` (not UTF-16 chars), to match the spec for non-BMP/non-ASCII keys.

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/appfeedback/core/IssueBodyFormatterTest.kt`:

```kotlin
package com.appfeedback.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IssueBodyFormatterTest {
    private val device = DeviceInfo("A", "1", "1", "M", "macOS", "Version 15.1")

    @Test fun labels() {
        assertEquals(listOf("bug", "user-submitted"), IssueBodyFormatter.labels(FeedbackType.BUG))
        assertEquals(listOf("feature-request", "user-submitted"), IssueBodyFormatter.labels(FeedbackType.FEATURE_REQUEST))
    }

    @Test fun minimal_body() {
        val report = FeedbackReport(FeedbackType.BUG, "t", "Desc")
        assertEquals(
            "Desc\n\n---\n**Device Information:**\nApp: A\nApp Version: 1 (1)\nDevice: M\nmacOS Version: Version 15.1\n\n---\n👍 Votes: 0",
            IssueBodyFormatter.format(report, device)
        )
    }

    @Test fun extra_fields_codepoint_order() {
        val report = FeedbackReport(FeedbackType.BUG, "t", "Desc",
            extraFields = mapOf("Zeta" to "z", "alpha" to "a", "Beta" to "b"))
        val body = IssueBodyFormatter.format(report, device)
        assertTrue(body.indexOf("**Beta:**") < body.indexOf("**Zeta:**"))
        assertTrue(body.indexOf("**Zeta:**") < body.indexOf("**alpha:**"))
    }

    @Test fun prefix_key_orders_first() {
        val report = FeedbackReport(FeedbackType.BUG, "t", "Desc",
            extraFields = mapOf("ab" to "2", "a" to "1"))
        val body = IssueBodyFormatter.format(report, device)
        assertTrue(body.indexOf("**a:**") < body.indexOf("**ab:**"))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
cd /Users/amir/Developer/appfeedback-android && ./gradlew test --tests "com.appfeedback.core.IssueBodyFormatterTest" --console=plain 2>&1 | tail -10
```
Expected: compile failure (unresolved `IssueBodyFormatter`).

- [ ] **Step 3: Implement**

Create `src/main/kotlin/com/appfeedback/core/IssueBodyFormatter.kt` (note the literal em-dash `—` U+2014 in the attachment line):

```kotlin
package com.appfeedback.core

/** Renders a report + device info (+ uploaded attachments) into the exact issue
 *  body the parser understands. Port of the Swift `IssueBodyFormatter`. */
object IssueBodyFormatter {
    const val USER_SUBMITTED_LABEL = "user-submitted"

    fun format(
        report: FeedbackReport,
        deviceInfo: DeviceInfo,
        uploaded: List<UploadedAttachment> = emptyList(),
    ): String {
        val sb = StringBuilder(report.description)
        sb.append("\n\n${BodyMarker.HORIZONTAL_RULE}\n**${BodyMarker.DEVICE_HEADER}**\n")
        sb.append(deviceInfo.renderForIssueBody())

        report.contactEmail?.takeIf { it.isNotEmpty() }?.let {
            sb.append("\n\n**${BodyMarker.CONTACT_EMAIL_LABEL}**\n$it")
        }

        for (key in report.extraFields.keys.sortedWith { a, b -> codePointOrder(a, b) }) {
            sb.append("\n\n**$key:**\n${report.extraFields[key]}")
        }

        if (uploaded.isNotEmpty()) {
            sb.append("\n\n${BodyMarker.ATTACHMENTS_OPEN}\n${BodyMarker.ATTACHMENTS_HEADER}\n")
            for (a in uploaded) {
                val prefix = if (a.mimeType.startsWith("image/")) "!" else ""
                val size = DeterministicByteCount.string(a.sizeBytes)
                sb.append("\n$prefix[${a.filename}](${a.url}) — ${a.mimeType}, $size\n")
            }
            sb.append("\n${BodyMarker.ATTACHMENTS_CLOSE}")
        }

        sb.append("\n\n${BodyMarker.HORIZONTAL_RULE}\n${BodyMarker.VOTES_FOOTER}")
        return sb.toString()
    }

    fun labels(type: FeedbackType): List<String> = listOf(type.rawValue, USER_SUBMITTED_LABEL)

    /** Ascending Unicode code-point order. Iterates code points (not UTF-16
     *  chars) so non-BMP keys order identically across platforms. */
    internal fun codePointOrder(a: String, b: String): Int {
        val ai = a.codePoints().iterator()
        val bi = b.codePoints().iterator()
        while (ai.hasNext() && bi.hasNext()) {
            val cmp = ai.nextInt().compareTo(bi.nextInt())
            if (cmp != 0) return cmp
        }
        return ai.hasNext().compareTo(bi.hasNext())  // shorter (prefix) sorts first
    }
}
```

- [ ] **Step 4: Run to verify it passes**

```bash
cd /Users/amir/Developer/appfeedback-android && ./gradlew test --tests "com.appfeedback.core.IssueBodyFormatterTest" --console=plain 2>&1 | tail -8
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
cd /Users/amir/Developer/appfeedback-android && git add -A && git commit -q -m "feat(core): issue body formatter + code-point extraFields ordering"
```

---

### Task 4: IssueBodyParser

**Files:** Create `IssueBodyParser.kt`. Test: `IssueBodyParserTest.kt`.

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/appfeedback/core/IssueBodyParserTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run to verify it fails**

```bash
cd /Users/amir/Developer/appfeedback-android && ./gradlew test --tests "com.appfeedback.core.IssueBodyParserTest" --console=plain 2>&1 | tail -10
```
Expected: compile failure (unresolved `IssueBodyParser`).

- [ ] **Step 3: Implement**

Create `src/main/kotlin/com/appfeedback/core/IssueBodyParser.kt` (note the literal em-dash `—` in `startsWith("—")`):

```kotlin
package com.appfeedback.core

/** Inverse of `IssueBodyFormatter`: pulls structured fields out of an issue body.
 *  Tolerant of hand-written / legacy bodies. Port of the Swift `IssueBodyParser`. */
object IssueBodyParser {

    fun parse(raw: String): ParsedFeedbackBody {
        val descLines = mutableListOf<String>()
        var inDevice = false
        var expectEmail = false
        var appName: String? = null
        var appVersion: String? = null
        var device: String? = null
        var osVersion: String? = null
        var email: String? = null

        for (line in raw.split("\n")) {
            val trimmed = line.trim().replace("**", "").trim()

            if (trimmed == BodyMarker.DEVICE_HEADER) { inDevice = true; continue }
            if (!inDevice) { descLines.add(line); continue }

            if (expectEmail) {
                if (trimmed.isNotEmpty() && trimmed.contains("@")) email = trimmed
                expectEmail = false
                continue
            }

            val appVer = valueAfter(trimmed, BodyMarker.APP_VERSION_LABEL)
            val app = valueAfter(trimmed, BodyMarker.APP_LABEL)
            val dev = valueAfter(trimmed, BodyMarker.DEVICE_LABEL)
            when {
                appVer != null -> appVersion = appVer
                app != null -> appName = app
                dev != null -> device = dev
                BodyMarker.osVersionRegex.containsMatchIn(trimmed) ->
                    osVersion = trimmed.split(":").drop(1).joinToString(":").trim()
                trimmed == BodyMarker.CONTACT_EMAIL_LABEL -> expectEmail = true
                else -> {
                    val v = valueAfter(trimmed, BodyMarker.CONTACT_EMAIL_LABEL)
                    if (v != null) { if (v.contains("@")) email = v else expectEmail = true }
                }
            }
        }

        val description = descLines
            .filter { it.trim() != BodyMarker.HORIZONTAL_RULE }
            .joinToString("\n")
            .trim()

        return ParsedFeedbackBody(
            description = description,
            appName = appName,
            appVersion = appVersion,
            device = device,
            osVersion = osVersion,
            email = email,
            attachments = parseAttachments(raw),
        )
    }

    private fun valueAfter(s: String, marker: String): String? =
        if (s.startsWith(marker)) s.removePrefix(marker).trim() else null

    private fun parseAttachments(raw: String): List<ParsedAttachment> {
        val openIdx = raw.indexOf(BodyMarker.ATTACHMENTS_OPEN)
        if (openIdx < 0) return emptyList()
        val afterOpen = openIdx + BodyMarker.ATTACHMENTS_OPEN.length
        val closeIdx = raw.indexOf(BodyMarker.ATTACHMENTS_CLOSE, afterOpen)
        val block = if (closeIdx < 0) raw.substring(afterOpen) else raw.substring(afterOpen, closeIdx)

        val results = mutableListOf<ParsedAttachment>()
        for (rawLine in block.split("\n")) {
            parseAttachmentLine(rawLine.trim())?.let { results.add(it) }
        }
        return results
    }

    private fun parseAttachmentLine(line: String): ParsedAttachment? {
        val working = when {
            line.startsWith("![") -> line.removePrefix("![")
            line.startsWith("[") -> line.removePrefix("[")
            else -> return null
        }
        val nameEnd = working.indexOf("](")
        if (nameEnd < 0) return null
        val filename = working.substring(0, nameEnd)
        val afterName = working.substring(nameEnd + 2)
        val urlEnd = afterName.indexOf(')')
        if (urlEnd < 0) return null
        val url = afterName.substring(0, urlEnd)
        if (url.isEmpty()) return null
        val rest = afterName.substring(urlEnd + 1).trim()

        var mime: String? = null
        var size: Int? = null
        if (rest.startsWith("—")) {
            val suffix = rest.removePrefix("—").trim()
            val parts = suffix.split(",", limit = 2).map { it.trim() }
            mime = parts[0]
            if (parts.size > 1) size = parseHumanByteCount(parts[1])
        }
        val resolvedMime = mime ?: inferMimeFromUrl(url)
        return ParsedAttachment(filename, resolvedMime, url, size)
    }

    internal fun parseHumanByteCount(s: String): Int? {
        val parts = s.split(" ", limit = 2)
        val num = parts.getOrNull(0)?.toDoubleOrNull() ?: return null
        val unit = if (parts.size > 1) parts[1].uppercase() else "B"
        return when (unit) {
            "BYTES", "B" -> num.toInt()
            "KB" -> (num * 1_000).toInt()
            "MB" -> (num * 1_000_000).toInt()
            "GB" -> (num * 1_000_000_000).toInt()
            else -> num.toInt()
        }
    }

    /** Best-effort MIME from a URL's file extension. Only used when the body line
     *  omits the MIME (not exercised by the conformance corpus). */
    internal fun inferMimeFromUrl(url: String): String {
        val ext = url.substringAfterLast('/').substringAfterLast('.', "").lowercase()
        return when (ext) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "heic" -> "image/heic"
            "webp" -> "image/webp"
            "pdf" -> "application/pdf"
            "log", "txt", "text" -> "text/plain"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "csv" -> "text/csv"
            else -> "application/octet-stream"
        }
    }
}
```

- [ ] **Step 4: Run to verify it passes**

```bash
cd /Users/amir/Developer/appfeedback-android && ./gradlew test --tests "com.appfeedback.core.IssueBodyParserTest" --console=plain 2>&1 | tail -8
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
cd /Users/amir/Developer/appfeedback-android && git add -A && git commit -q -m "feat(core): issue body parser"
```

---

### Task 5: Conformance gate

**Files:** Create `ConformanceTest.kt`. Delete the temporary `SmokeTest.kt`.

- [ ] **Step 1: Write the conformance test**

Create `src/test/kotlin/com/appfeedback/core/ConformanceTest.kt`:

```kotlin
package com.appfeedback.core

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Runs the canonical appfeedback-spec golden fixtures (vendored under
 *  resources/conformance) through the Kotlin formatter and parser. This is the
 *  cross-language gate: identical corpus to the Swift (and future TS) SDKs. */
class ConformanceTest {
    private val gson = Gson()

    private data class Corpus<T>(val version: Int, val cases: List<T>)
    private data class ReportFx(val type: String, val title: String, val description: String,
                                val contactEmail: String?, val extraFields: Map<String, String>?)
    private data class DeviceFx(val appName: String, val appVersion: String, val buildNumber: String,
                                val model: String, val osName: String, val osVersion: String)
    private data class UploadedFx(val filename: String, val mimeType: String, val sizeBytes: Int, val url: String)
    private data class FormatCase(val name: String, val report: ReportFx, val deviceInfo: DeviceFx,
                                  val uploaded: List<UploadedFx>?, val expectedBody: String, val expectedLabels: List<String>?)
    private data class AttachmentExp(val filename: String, val mimeType: String, val url: String, val sizeBytes: Int?)
    private data class ParsedExp(val description: String, val appName: String?, val appVersion: String?,
                                 val device: String?, val osVersion: String?, val email: String?, val attachments: List<AttachmentExp>?)
    private data class ParseCase(val name: String, val body: String, val expected: ParsedExp)

    private fun load(resource: String): String =
        (javaClass.getResourceAsStream("/conformance/$resource.json") ?: fail("missing $resource.json"))
            .bufferedReader().use { it.readText() }

    @Test fun format_golden_fixtures() {
        val type = object : TypeToken<Corpus<FormatCase>>() {}.type
        val corpus: Corpus<FormatCase> = gson.fromJson(load("format-cases"), type)
        assertTrue(corpus.cases.isNotEmpty(), "no format fixtures")
        for (c in corpus.cases) {
            val fType = FeedbackType.fromRawValue(c.report.type) ?: fail("unknown type in ${c.name}")
            val report = FeedbackReport(fType, c.report.title, c.report.description,
                c.report.contactEmail, c.report.extraFields ?: emptyMap())
            val device = DeviceInfo(c.deviceInfo.appName, c.deviceInfo.appVersion, c.deviceInfo.buildNumber,
                c.deviceInfo.model, c.deviceInfo.osName, c.deviceInfo.osVersion)
            val uploaded = (c.uploaded ?: emptyList()).map {
                UploadedAttachment(it.filename, it.mimeType, it.sizeBytes, it.url)
            }
            assertEquals(c.expectedBody, IssueBodyFormatter.format(report, device, uploaded), "format mismatch in '${c.name}'")
            c.expectedLabels?.let { assertEquals(it, IssueBodyFormatter.labels(fType), "labels mismatch in '${c.name}'") }
        }
    }

    @Test fun parse_golden_fixtures() {
        val type = object : TypeToken<Corpus<ParseCase>>() {}.type
        val corpus: Corpus<ParseCase> = gson.fromJson(load("parse-cases"), type)
        assertTrue(corpus.cases.isNotEmpty(), "no parse fixtures")
        for (c in corpus.cases) {
            val p = IssueBodyParser.parse(c.body)
            assertEquals(c.expected.description, p.description, "description in '${c.name}'")
            assertEquals(c.expected.appName, p.appName, "appName in '${c.name}'")
            assertEquals(c.expected.appVersion, p.appVersion, "appVersion in '${c.name}'")
            assertEquals(c.expected.device, p.device, "device in '${c.name}'")
            assertEquals(c.expected.osVersion, p.osVersion, "osVersion in '${c.name}'")
            assertEquals(c.expected.email, p.email, "email in '${c.name}'")
            val exp = c.expected.attachments ?: emptyList()
            assertEquals(exp.size, p.attachments.size, "attachment count in '${c.name}'")
            exp.forEachIndexed { i, ea ->
                assertEquals(ea.filename, p.attachments[i].filename, "att filename in '${c.name}'[$i]")
                assertEquals(ea.mimeType, p.attachments[i].mimeType, "att mime in '${c.name}'[$i]")
                assertEquals(ea.url, p.attachments[i].url, "att url in '${c.name}'[$i]")
                assertEquals(ea.sizeBytes, p.attachments[i].sizeBytes, "att size in '${c.name}'[$i]")
            }
        }
    }
}
```

- [ ] **Step 2: Remove the temporary smoke test**

```bash
cd /Users/amir/Developer/appfeedback-android && rm src/test/kotlin/com/appfeedback/core/SmokeTest.kt
```

- [ ] **Step 3: Run the conformance test, then the full suite**

```bash
cd /Users/amir/Developer/appfeedback-android && ./gradlew test --tests "com.appfeedback.core.ConformanceTest" --console=plain 2>&1 | tail -15
cd /Users/amir/Developer/appfeedback-android && ./gradlew test --console=plain 2>&1 | tail -8
```
Expected: PASS. **If a format case mismatches, STOP and report the exact expected-vs-actual — do NOT edit the vendored fixtures** (they are canonical; a mismatch means the Kotlin port diverges and must be fixed, not the fixture).

- [ ] **Step 4: Commit**

```bash
cd /Users/amir/Developer/appfeedback-android && git add -A && git commit -q -m "test(core): golden-fixture conformance gate (format + parse)"
```

---

### Task 6: FeedbackTransport interface

**Files:** Create `FeedbackTransport.kt`. Test: `FeedbackTransportTest.kt`.

> A `suspend` interface only (no concrete GitHub/relay impl — that's P1b). The test uses a fake to prove the contract shape compiles and is callable.

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/appfeedback/core/FeedbackTransportTest.kt`:

```kotlin
package com.appfeedback.core

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class FeedbackTransportTest {
    @Test fun fake_transport_returns_identifier() = runBlocking {
        val fake = object : FeedbackTransport {
            override suspend fun submit(report: FeedbackReport, deviceInfo: DeviceInfo): Int = 42
        }
        val id = fake.submit(
            FeedbackReport(FeedbackType.BUG, "t", "d"),
            DeviceInfo("A", "1", "1", "M", "Android", "14"),
        )
        assertEquals(42, id)
    }
}
```

- [ ] **Step 2: Add the coroutines test dependency**

In `build.gradle.kts`, add to `dependencies { }`:
```kotlin
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
```
(`kotlinx-coroutines-core` is brought transitively; `runBlocking` is available. If `runBlocking` is unresolved, also add `testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")`.)

- [ ] **Step 3: Run to verify it fails**

```bash
cd /Users/amir/Developer/appfeedback-android && ./gradlew test --tests "com.appfeedback.core.FeedbackTransportTest" --console=plain 2>&1 | tail -10
```
Expected: compile failure (unresolved `FeedbackTransport`).

- [ ] **Step 4: Implement**

Create `src/main/kotlin/com/appfeedback/core/FeedbackTransport.kt`:

```kotlin
package com.appfeedback.core

/** Where a report gets delivered. Concrete implementations (GitHub direct,
 *  relay) are added in P1b. Returns the backend-assigned identifier
 *  (the GitHub issue number for the GitHub transport). */
interface FeedbackTransport {
    suspend fun submit(report: FeedbackReport, deviceInfo: DeviceInfo): Int
}
```

- [ ] **Step 5: Run to verify it passes**

```bash
cd /Users/amir/Developer/appfeedback-android && ./gradlew test --tests "com.appfeedback.core.FeedbackTransportTest" --console=plain 2>&1 | tail -8
```
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
cd /Users/amir/Developer/appfeedback-android && git add -A && git commit -q -m "feat(core): FeedbackTransport interface"
```

---

### Task 7: Fixture sync + final verification

**Files:** Create `/Users/amir/Developer/appfeedback-spec/scripts/sync-to-android.sh`. Verification only otherwise.

- [ ] **Step 1: Add an Android sync script to the spec repo**

Create `/Users/amir/Developer/appfeedback-spec/scripts/sync-to-android.sh`:

```bash
#!/usr/bin/env bash
# Copy the canonical conformance fixtures into the Android SDK's test resources.
# Override the Android location with APPFEEDBACK_ANDROID_DIR if not the sibling.
set -euo pipefail
SPEC_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID_DIR="${APPFEEDBACK_ANDROID_DIR:-$SPEC_DIR/../appfeedback-android}"
DEST="$ANDROID_DIR/src/test/resources/conformance"
mkdir -p "$DEST"
cp "$SPEC_DIR/fixtures/format-cases.json" "$DEST/format-cases.json"
cp "$SPEC_DIR/fixtures/parse-cases.json" "$DEST/parse-cases.json"
echo "Synced fixtures → $DEST"
```

Make it executable and confirm it produces no drift:
```bash
chmod +x /Users/amir/Developer/appfeedback-spec/scripts/sync-to-android.sh
/Users/amir/Developer/appfeedback-spec/scripts/sync-to-android.sh
cd /Users/amir/Developer/appfeedback-android && git status --short src/test/resources/conformance
```
Expected: "Synced fixtures → …" and NO git changes (vendored copies already identical).

- [ ] **Step 2: Commit the sync script in the spec repo**

```bash
cd /Users/amir/Developer/appfeedback-spec && git add scripts/sync-to-android.sh && git commit -q -m "chore: add sync-to-android fixture script"
```

- [ ] **Step 3: Full suite + tree check on the Android repo**

```bash
cd /Users/amir/Developer/appfeedback-android && ./gradlew test --console=plain 2>&1 | tail -8
cd /Users/amir/Developer/appfeedback-android && git log --oneline && git status --short
```
Expected: BUILD SUCCESSFUL; clean tree; commits for Tasks 0–6.

---

## Self-Review (plan author)

**Spec coverage (design §5, P1a slice):** ✅ Kotlin core modules (Tasks 1–4,6); ✅ formatter/parser ported against fixtures (Tasks 3–5); ✅ DeviceInfo data + render (Task 1; `current()` deferred to P1b as designed); ✅ transport interface (Task 6); ✅ conformance gate (Task 5); ✅ single-source fixtures via sync (Task 7). Deferred to P1b/P1c (correct): `DeviceInfo.current()` via `Build.*`, concrete Relay/GitHub transports, attachment upload/preprocess, Compose UI, Maven publishing.

**Cross-language gotchas pinned:** `Long` arithmetic in `DeterministicByteCount` (Task 2 test includes 1e9), code-point comparator via `codePoints()` (Task 3), JDK-26 target alignment (Task 0 build file), em-dash/emoji UTF-8 (formatter/parser).

**Placeholder scan:** none; every step has complete code/commands.

**Type/name consistency:** `FeedbackType.fromRawValue`, `DeviceInfo.renderForIssueBody`, `IssueBodyFormatter.format/labels/codePointOrder`, `IssueBodyParser.parse/parseHumanByteCount/inferMimeFromUrl`, `BodyMarker.*`, `ParsedFeedbackBody/ParsedAttachment` are used consistently across tasks and match the fixture JSON field names decoded in Task 5.
