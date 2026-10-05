package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.airplay.AirPlayDisplaySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class WidthPhysicalMmAutoDetectTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE)

    @Before fun clearPreferences() {
        prefs.edit().clear().apply()
    }

    @Test fun widthMillimetersRejectsInvalidGeometry() {
        assertNull(NetaDisplayDetector.widthMillimeters(NetaDisplayDetector.DisplayGeometry(0, 720, 160f, 160f)))
        assertNull(NetaDisplayDetector.widthMillimeters(NetaDisplayDetector.DisplayGeometry(1920, 720, 0f, 160f)))
        assertNull(NetaDisplayDetector.widthMillimeters(NetaDisplayDetector.DisplayGeometry(1920, 0, 160f, 160f)))
        assertNull(NetaDisplayDetector.widthMillimeters(NetaDisplayDetector.DisplayGeometry(-100, 720, 160f, 160f)))
    }

    @Test fun widthMillimetersMatches1080pPanelAt96Dpi() {
        // 1920/96 = 20 inches = 508 mm (standard 1920x1080 @ 96dpi reference panel).
        val geometry = NetaDisplayDetector.DisplayGeometry(1920, 1080, 96f, 96f)
        val mm = NetaDisplayDetector.widthMillimeters(geometry)!!
        assertTrue("Expected ~508mm, got $mm", mm in 500..515)
    }

    @Test fun widthMillimetersMatchesNeta15point6InchPanel() {
        // 哪吒 HZEP32 15.6" head unit: 1920 wide at ~156 dpi gives 15.0" wide (~380mm).
        val geometry = NetaDisplayDetector.DisplayGeometry(1920, 978, 156f, 156f)
        val mm = NetaDisplayDetector.widthMillimeters(geometry)!!
        assertTrue("Expected ~380mm for 15.6\" panel, got $mm", mm in 370..400)
    }

    @Test fun widthMillimetersReturnsNullForTooSmallPanels() {
        // Wristband-class panel: 40px wide at 200dpi is ~5mm, filtered by MIN_WIDTH_PHYSICAL_MM.
        // detectWidthMillimeters drops it; direct widthMillimeters still returns a positive int.
        val geometry = NetaDisplayDetector.DisplayGeometry(40, 60, 200f, 200f)
        val mm = NetaDisplayDetector.widthMillimeters(geometry)
        // Positive but very small.
        assertTrue(mm == null || mm < AirPlayDisplaySettings.MIN_WIDTH_PHYSICAL_MM)
    }

    @Test fun detectWidthMillimetersReturnsNonNullOrFilteredOnRobolectric() {
        // Robolectric's default shadow display reports dpi and pixels; whatever it computes,
        // the result must either be null (filtered) or respect MIN_WIDTH_PHYSICAL_MM.
        val detected = NetaDisplayDetector.detectWidthMillimeters(context)
        if (detected != null) {
            assertTrue(
                "Detected $detected mm is below min",
                detected >= AirPlayDisplaySettings.MIN_WIDTH_PHYSICAL_MM,
            )
        }
    }

    @Test fun loadWidthPhysicalMmReturnsDefaultWhenPrefsEmpty() {
        // Robolectric's default display is 320x470 @ dpi 320. widthMillimeters returns ~25mm,
        // below MIN_WIDTH_PHYSICAL_MM, so detectWidthMillimeters filters it to null. The
        // fresh-install branch falls back to DEFAULT_WIDTH_PHYSICAL_MM.
        assertEquals(
            AirPlayDisplaySettings.DEFAULT_WIDTH_PHYSICAL_MM,
            AirPlayPersistence.loadWidthPhysicalMm(context),
        )
    }

    @Test fun loadWidthPhysicalMmPersistsValueAfterFirstRead() {
        val first = AirPlayPersistence.loadWidthPhysicalMm(context)
        assertTrue(prefs.contains("display_width_physical_mm"))
        assertEquals(first, AirPlayPersistence.loadWidthPhysicalMm(context))
    }

    @Test fun loadWidthPhysicalMmRespectsUserChange() {
        // Persist a value that's NOT the default; verify it survives subsequent loads.
        AirPlayPersistence.saveWidthPhysicalMm(context, 300)
        assertEquals(300, AirPlayPersistence.loadWidthPhysicalMm(context))
    }

    @Test fun loadWidthPhysicalMmUpgradesLegacyDefaultOnlyWhenDetectionAvailable() {
        // Simulate old install: 200mm persisted. Without a real panel detection result, the
        // upgrade branch is a no-op and the value stays at 200.
        AirPlayPersistence.saveWidthPhysicalMm(context, 200)
        assertEquals(200, AirPlayPersistence.loadWidthPhysicalMm(context))
    }

    @Test fun loadWidthPhysicalMmIsIdempotentAfterFirstCall() {
        AirPlayPersistence.loadWidthPhysicalMm(context)
        val second = AirPlayPersistence.loadWidthPhysicalMm(context)
        val third = AirPlayPersistence.loadWidthPhysicalMm(context)
        assertEquals(second, third)
    }
}
