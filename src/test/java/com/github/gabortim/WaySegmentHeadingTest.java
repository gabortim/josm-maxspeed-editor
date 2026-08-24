package com.github.gabortim;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.spi.preferences.Config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.gabortim.MaxSpeedEditorModel.WaySegmentHeading;

/**
 * Unit tests for {@link WaySegmentHeading} value object.
 */
class WaySegmentHeadingTest {

    @BeforeAll
    static void initializeJosmPreferences() {
        Config.setPreferencesInstance(new Preferences());
    }

    @Test
    @DisplayName("Creates known heading instance with accurate forward, backward, and axis directions")
    void createsKnownHeading() {
        WaySegmentHeading heading = new WaySegmentHeading(90.0);

        assertTrue(heading.isKnown());
        assertEquals(90.0, heading.forwardBearing(), 0.001);
        assertEquals("East →", heading.forwardCardinal());
        assertEquals("West ←", heading.backwardCardinal());
        assertEquals("East–West ↔", heading.bothCardinal());
        assertNotNull(heading.toString());
        assertTrue(heading.toString().contains("East →"));
    }

    @Test
    @DisplayName("Creates unknown heading instance with NaN bearing and localized fallback strings")
    void createsUnknownHeading() {
        WaySegmentHeading heading = WaySegmentHeading.unknown();

        assertFalse(heading.isKnown());
        assertTrue(Double.isNaN(heading.forwardBearing()));
        assertEquals("Unknown", heading.forwardCardinal());
        assertEquals("Unknown", heading.backwardCardinal());
        assertEquals("Unknown", heading.bothCardinal());
        assertNotNull(heading.toString());
    }
}
