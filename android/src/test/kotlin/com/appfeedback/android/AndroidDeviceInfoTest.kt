package com.appfeedback.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidDeviceInfoTest {
  @Test fun fills_android_platform_fields() {
    val d = currentDeviceInfo("Acme", "1.2.3", "45")
    assertEquals("Acme", d.appName)
    assertEquals("1.2.3", d.appVersion)
    assertEquals("45", d.buildNumber)
    assertEquals("Android", d.osName)
    assertTrue(d.model.isNotBlank())
    assertTrue(d.osVersion.isNotBlank())
  }
}
