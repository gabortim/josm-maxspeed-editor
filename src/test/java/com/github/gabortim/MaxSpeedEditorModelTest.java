package com.github.gabortim;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.spi.preferences.Config;

import java.awt.Point;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprehensive test suite verifying business logic, bearing geometry, command generation,
 * tag conflict resolution, and preference defaults for MaxSpeed Editor.
 */
class MaxSpeedEditorModelTest {

    private static final Set<String> ALLOWED_HIGHWAYS = new HashSet<>(Arrays.asList(
            "residential", "primary", "secondary", "tertiary", "trunk", "motorway"
    ));

    @BeforeAll
    static void initializeJosmPreferences() {
        Preferences pref = new Preferences();
        pref.enableSaveOnPut(false);
        Config.setPreferencesInstance(pref);
    }

    @Test
    @DisplayName("Detects whether a click point is near a way segment within threshold")
    void detectsWhetherClickPointIsNearWay() {
        Node n1 = new Node(new LatLon(0, 0));
        Node n2 = new Node(new LatLon(0, 1));
        Way way = new Way();
        way.addNode(n1);
        way.addNode(n2);

        // Projector: n1 -> (0, 0), n2 -> (100, 0) - horizontal line segment from (0, 0) to (100, 0)
        java.util.function.Function<Node, Point> projector = node -> {
            if (node == n1) return new Point(0, 0);
            if (node == n2) return new Point(100, 0);
            return null;
        };

        // Click right on the segment
        assertTrue(MaxSpeedEditorModel.isClickNearWay(way, new Point(50, 0), projector, 15.0));

        // Click 10 pixels away from the segment (within 15px threshold)
        assertTrue(MaxSpeedEditorModel.isClickNearWay(way, new Point(50, 10), projector, 15.0));

        // Click exactly 15 pixels away (within threshold)
        assertTrue(MaxSpeedEditorModel.isClickNearWay(way, new Point(50, 15), projector, 15.0));

        // Click 16 pixels away (exceeds 15px threshold)
        assertFalse(MaxSpeedEditorModel.isClickNearWay(way, new Point(50, 16), projector, 15.0));

        // Click 100 pixels away (far away, click-away scenario)
        assertFalse(MaxSpeedEditorModel.isClickNearWay(way, new Point(50, 100), projector, 15.0));

        // Edge cases and null checks
        assertFalse(MaxSpeedEditorModel.isClickNearWay(null, new Point(50, 0), projector, 15.0));
        assertFalse(MaxSpeedEditorModel.isClickNearWay(way, null, projector, 15.0));
        assertFalse(MaxSpeedEditorModel.isClickNearWay(way, new Point(50, 0), null, 15.0));
        assertFalse(MaxSpeedEditorModel.isClickNearWay(way, new Point(50, 0), projector, -1.0));

        Way singleNodeWay = new Way();
        singleNodeWay.addNode(n1);
        assertFalse(MaxSpeedEditorModel.isClickNearWay(singleNodeWay, new Point(0, 0), projector, 15.0));
    }

    @Test
    @DisplayName("Finds allowed highway near click point and ignores far ways or disallowed ways")
    void findsAllowedHighwayNearPoint() {
        Node n1 = new Node(new LatLon(0, 0));
        Node n2 = new Node(new LatLon(0, 1));
        Way residential = new Way();
        residential.put("highway", "residential");
        residential.addNode(n1);
        residential.addNode(n2);

        Node n3 = new Node(new LatLon(1, 0));
        Node n4 = new Node(new LatLon(1, 1));
        Way primary = new Way();
        primary.put("highway", "primary");
        primary.addNode(n3);
        primary.addNode(n4);

        Node n5 = new Node(new LatLon(2, 0));
        Node n6 = new Node(new LatLon(2, 1));
        Way footway = new Way();
        footway.put("highway", "footway");
        footway.addNode(n5);
        footway.addNode(n6);

        java.util.function.Function<Node, Point> projector = node -> {
            if (node == n1) return new Point(0, 0);
            if (node == n2) return new Point(100, 0);
            if (node == n3) return new Point(0, 100);
            if (node == n4) return new Point(100, 100);
            if (node == n5) return new Point(0, 200);
            if (node == n6) return new Point(100, 200);
            return null;
        };

        List<OsmPrimitive> selection = Arrays.asList(residential, primary, footway);

        // Click near residential at (50, 2)
        assertSame(residential, MaxSpeedEditorModel.findAllowedHighwayNearPoint(
                selection, ALLOWED_HIGHWAYS, new Point(50, 2), projector, 15.0));

        // Click near primary at (50, 98)
        assertSame(primary, MaxSpeedEditorModel.findAllowedHighwayNearPoint(
                selection, ALLOWED_HIGHWAYS, new Point(50, 98), projector, 15.0));

        // Click near footway at (50, 202) -> footway is disallowed, returns null
        assertNull(MaxSpeedEditorModel.findAllowedHighwayNearPoint(
                selection, ALLOWED_HIGHWAYS, new Point(50, 202), projector, 15.0));

        // Click away in empty space at (50, 50) -> returns null
        assertNull(MaxSpeedEditorModel.findAllowedHighwayNearPoint(
                selection, ALLOWED_HIGHWAYS, new Point(50, 50), projector, 15.0));

        // Null and empty safety checks
        assertNull(MaxSpeedEditorModel.findAllowedHighwayNearPoint(
                null, ALLOWED_HIGHWAYS, new Point(50, 2), projector, 15.0));
        assertNull(MaxSpeedEditorModel.findAllowedHighwayNearPoint(
                selection, null, new Point(50, 2), projector, 15.0));
        assertNull(MaxSpeedEditorModel.findAllowedHighwayNearPoint(
                selection, ALLOWED_HIGHWAYS, null, projector, 15.0));
        assertNull(MaxSpeedEditorModel.findAllowedHighwayNearPoint(
                selection, ALLOWED_HIGHWAYS, new Point(50, 2), null, 15.0));
        assertNull(MaxSpeedEditorModel.findAllowedHighwayNearPoint(
                selection, ALLOWED_HIGHWAYS, new Point(50, 2), projector, -5.0));
        assertNull(MaxSpeedEditorModel.findAllowedHighwayNearPoint(
                Collections.emptyList(), ALLOWED_HIGHWAYS, new Point(50, 2), projector, 15.0));
    }

    @Test
    @DisplayName("Finds first allowed highway and ignores other primitives and disallowed highways")
    void findsFirstAllowedHighwayAndIgnoresOtherPrimitives() {
        Way disallowed = way("footway", new LatLon(0, 0), new LatLon(0, 1));
        Way allowed1 = way("Residential", new LatLon(0, 0), new LatLon(1, 0));
        Way allowed2 = way("primary", new LatLon(1, 0), new LatLon(2, 0));
        Relation relation = new Relation();
        relation.put("highway", "residential");

        List<OsmPrimitive> selection = Arrays.asList(
                new Node(new LatLon(0, 0)),
                relation,
                disallowed,
                allowed1,
                allowed2
        );

        Way result = MaxSpeedEditorModel.findAllowedHighway(selection, ALLOWED_HIGHWAYS);
        assertSame(allowed1, result, "Should return the first matching allowed way");
    }

    @Test
    @DisplayName("Handles case-insensitivity and whitespace when matching highway types")
    void handlesCaseInsensitiveAndTrimmedHighwayTags() {
        Way wayWithWhitespace = way("  SECONDARY  ", new LatLon(0, 0), new LatLon(0, 1));
        List<OsmPrimitive> selection = Collections.singletonList(wayWithWhitespace);

        Way result = MaxSpeedEditorModel.findAllowedHighway(selection, ALLOWED_HIGHWAYS);
        assertSame(wayWithWhitespace, result);
    }

    @Test
    @DisplayName("Returns null for null, empty, or disallowed selections")
    void returnsNullForMissingEmptyOrDisallowedSelection() {
        Way disallowed = way("cycleway", new LatLon(0, 0), new LatLon(0, 1));
        Way noHighwayTag = way(null, new LatLon(0, 0), new LatLon(0, 1));

        assertNull(MaxSpeedEditorModel.findAllowedHighway(null, ALLOWED_HIGHWAYS));
        assertNull(MaxSpeedEditorModel.findAllowedHighway(Collections.emptyList(), ALLOWED_HIGHWAYS));
        assertNull(MaxSpeedEditorModel.findAllowedHighway(Collections.singleton(disallowed), ALLOWED_HIGHWAYS));
        assertNull(MaxSpeedEditorModel.findAllowedHighway(Collections.singleton(noHighwayTag), ALLOWED_HIGHWAYS));
        assertNull(MaxSpeedEditorModel.findAllowedHighway(Collections.singleton(disallowed), null));
    }

    @ParameterizedTest(name = "Bearing {0}° -> Cardinal \"{1}\"")
    @CsvSource({
            "0.0, North ↑",
            "22.0, North ↑",
            "23.0, Northeast ↗",
            "45.0, Northeast ↗",
            "67.0, Northeast ↗",
            "68.0, East →",
            "90.0, East →",
            "112.0, East →",
            "113.0, Southeast ↘",
            "135.0, Southeast ↘",
            "157.0, Southeast ↘",
            "158.0, South ↓",
            "180.0, South ↓",
            "202.0, South ↓",
            "203.0, Southwest ↙",
            "225.0, Southwest ↙",
            "247.0, Southwest ↙",
            "248.0, West ←",
            "270.0, West ←",
            "292.0, West ←",
            "293.0, Northwest ↖",
            "315.0, Northwest ↖",
            "337.0, Northwest ↖",
            "338.0, North ↑",
            "360.0, North ↑",
            "-45.0, Northwest ↖",
            "-90.0, West ←",
            "-180.0, South ↓",
            "720.0, North ↑"
    })
    @DisplayName("Formats 8-point cardinal directions across full angular range and wraps correctly")
    void formatsAllCardinalDirectionsAndWrapsBearings(double bearing, String expectedCardinal) {
        assertEquals(expectedCardinal, MaxSpeedEditorModel.formatCardinal(bearing));
    }

    @ParameterizedTest(name = "Bearing {0}° -> Axis \"{1}\"")
    @CsvSource({
            "0.0, North–South ↕",
            "45.0, Northeast–Southwest ⤢",
            "90.0, East–West ↔",
            "135.0, Northwest–Southeast ⤡",
            "180.0, North–South ↕",
            "225.0, Northeast–Southwest ⤢",
            "270.0, East–West ↔",
            "315.0, Northwest–Southeast ⤡",
            "360.0, North–South ↕",
            "-90.0, East–West ↔"
    })
    @DisplayName("Formats 4-point bidirectional compass axes symmetrically across all quadrants")
    void formatsBidirectionalAxesSymmetrically(double bearing, String expectedAxis) {
        assertEquals(expectedAxis, MaxSpeedEditorModel.formatBothCardinal(bearing));
    }

    @Test
    @DisplayName("Calculates heading from the way segment nearest to the click point")
    void calculatesHeadingFromSegmentNearestClick() {
        // Create 3 connected nodes:
        // Segment 1 (southWest -> northWest): heading North (0 deg)
        // Segment 2 (northWest -> northEast): heading East (90 deg)
        Node southWest = new Node(new LatLon(0, 0));
        Node northWest = new Node(new LatLon(1, 0));
        Node northEast = new Node(new LatLon(1, 1));
        Way way = new Way();
        way.setNodes(Arrays.asList(southWest, northWest, northEast));

        // Click near segment 2 (North segment going East)
        MaxSpeedEditorModel.WaySegmentHeading headingEast = MaxSpeedEditorModel.calculateHeading(
                way,
                new Point(90, 10),
                node -> node == southWest ? new Point(0, 100)
                        : node == northWest ? new Point(0, 0) : new Point(100, 0)
        );

        assertEquals("East →", headingEast.forwardCardinal());
        assertEquals("West ←", headingEast.backwardCardinal());
        assertEquals("East–West ↔", headingEast.bothCardinal());
        assertEquals(90.0, headingEast.forwardBearing(), 0.1);

        // Click near segment 1 (West segment going North)
        MaxSpeedEditorModel.WaySegmentHeading headingNorth = MaxSpeedEditorModel.calculateHeading(
                way,
                new Point(10, 80),
                node -> node == southWest ? new Point(0, 100)
                        : node == northWest ? new Point(0, 0) : new Point(100, 0)
        );

        assertEquals("North ↑", headingNorth.forwardCardinal());
        assertEquals("South ↓", headingNorth.backwardCardinal());
        assertEquals("North–South ↕", headingNorth.bothCardinal());
        assertEquals(0.0, headingNorth.forwardBearing(), 0.1);
    }

    @Test
    @DisplayName("Reports unknown heading for degenerate ways, null arguments, or missing coordinates")
    void reportsUnknownForDegenerateWays() {
        Way singleNodeWay = new Way();
        singleNodeWay.addNode(new Node(new LatLon(0, 0)));

        Way missingCoordWay = new Way();
        missingCoordWay.addNode(new Node());
        missingCoordWay.addNode(new Node());

        Way emptyWay = new Way();

        MaxSpeedEditorModel.WaySegmentHeading h1 = MaxSpeedEditorModel.calculateHeading(
                singleNodeWay, new Point(0, 0), node -> new Point(0, 0));
        MaxSpeedEditorModel.WaySegmentHeading h2 = MaxSpeedEditorModel.calculateHeading(
                missingCoordWay, new Point(0, 0), node -> new Point(0, 0));
        MaxSpeedEditorModel.WaySegmentHeading h3 = MaxSpeedEditorModel.calculateHeading(
                emptyWay, new Point(0, 0), node -> new Point(0, 0));
        MaxSpeedEditorModel.WaySegmentHeading h4 = MaxSpeedEditorModel.calculateHeading(
                null, new Point(0, 0), node -> new Point(0, 0));
        MaxSpeedEditorModel.WaySegmentHeading h5 = MaxSpeedEditorModel.calculateHeading(
                singleNodeWay, null, node -> new Point(0, 0));
        MaxSpeedEditorModel.WaySegmentHeading h6 = MaxSpeedEditorModel.calculateHeading(
                singleNodeWay, new Point(0, 0), null);

        for (MaxSpeedEditorModel.WaySegmentHeading h : Arrays.asList(h1, h2, h3, h4, h5, h6)) {
            assertTrue(Double.isNaN(h.forwardBearing()));
            assertEquals("Unknown", h.forwardCardinal());
            assertEquals("Unknown", h.backwardCardinal());
            assertEquals("Unknown", h.bothCardinal());
        }
    }

    @Test
    @DisplayName("Setting broad maxspeed clears existing directional maxspeed tags atomically")
    void broadSpeedReplacesDirectionalSpeedsInOneCommand() {
        Way way = dataSetWay("residential", new LatLon(0, 0), new LatLon(0, 1));
        way.put("maxspeed:forward", "30");
        way.put("maxspeed:backward", "40");

        SequenceCommand command = MaxSpeedEditorModel.createSpeedCommand(way, "maxspeed", "50");
        command.executeCommand();

        assertEquals("50", way.get("maxspeed"));
        assertFalse(way.hasKey("maxspeed:forward"), "maxspeed:forward should be cleared");
        assertFalse(way.hasKey("maxspeed:backward"), "maxspeed:backward should be cleared");

        // Test undo restores original directional tags
        command.undoCommand();
        assertNull(way.get("maxspeed"));
        assertEquals("30", way.get("maxspeed:forward"));
        assertEquals("40", way.get("maxspeed:backward"));

        // Test redo reapplies broad speed
        command.executeCommand();
        assertEquals("50", way.get("maxspeed"));
        assertFalse(way.hasKey("maxspeed:forward"));
        assertFalse(way.hasKey("maxspeed:backward"));
    }

    @Test
    @DisplayName("Setting directional speed clears broad maxspeed, but clearing directional speed does not")
    void directionalSpeedReplacesBroadSpeedButClearingDoesNot() {
        Way way = dataSetWay("residential", new LatLon(0, 0), new LatLon(0, 1));
        way.put("maxspeed", "50");

        // Setting forward speed clears broad speed
        SequenceCommand setForward = MaxSpeedEditorModel.createSpeedCommand(way, "maxspeed:forward", "30");
        setForward.executeCommand();
        assertEquals("30", way.get("maxspeed:forward"));
        assertFalse(way.hasKey("maxspeed"), "Broad maxspeed must be cleared when setting forward speed");

        // Re-set broad speed, then clear forward speed (value = null)
        way.put("maxspeed", "50");
        SequenceCommand clearForward = MaxSpeedEditorModel.createSpeedCommand(way, "maxspeed:forward", null);
        clearForward.executeCommand();
        assertFalse(way.hasKey("maxspeed:forward"));
        assertEquals("50", way.get("maxspeed"), "Broad maxspeed must remain when clearing directional speed");
    }

    @Test
    @DisplayName("Setting backward speed clears broad maxspeed and leaves forward speed intact")
    void backwardSpeedReplacesBroadSpeedAndPreservesForwardSpeed() {
        Way way = dataSetWay("residential", new LatLon(0, 0), new LatLon(0, 1));
        way.put("maxspeed", "50");
        way.put("maxspeed:forward", "30");

        SequenceCommand setBackward = MaxSpeedEditorModel.createSpeedCommand(way, "maxspeed:backward", "40");
        setBackward.executeCommand();

        assertEquals("40", way.get("maxspeed:backward"));
        assertEquals("30", way.get("maxspeed:forward"));
        assertFalse(way.hasKey("maxspeed"), "Broad maxspeed must be removed when setting backward speed");
    }

    @Test
    @DisplayName("Clearing broad maxspeed removes tag and does not remove directional tags")
    void clearingBroadSpeedRemovesTag() {
        Way way = dataSetWay("residential", new LatLon(0, 0), new LatLon(0, 1));
        way.put("maxspeed", "60");

        SequenceCommand clearBroad = MaxSpeedEditorModel.createSpeedCommand(way, "maxspeed", null);
        clearBroad.executeCommand();

        assertFalse(way.hasKey("maxspeed"));
    }

    @Test
    @DisplayName("Delegates one-way and roundabout detection to JOSM core")
    void delegatesOnewayAndRoundaboutDetectionToJosm() {
        Way forward = way("residential", new LatLon(0, 0), new LatLon(0, 1));
        forward.put("oneway", "yes");

        Way reverse = way("residential", new LatLon(0, 0), new LatLon(0, 1));
        reverse.put("oneway", "-1");

        Node first = new Node(new LatLon(0, 0));
        Way roundabout = new Way();
        roundabout.setNodes(Arrays.asList(
                first, new Node(new LatLon(0, 1)), new Node(new LatLon(1, 1)), first));
        roundabout.put("highway", "residential");
        roundabout.put("junction", "roundabout");

        Way regular = way("residential", new LatLon(0, 0), new LatLon(0, 1));

        assertEquals(1, forward.isOneway());
        assertEquals(-1, reverse.isOneway());
        assertEquals(0, roundabout.isOneway());
        assertEquals(0, regular.isOneway());
    }

    @Test
    @DisplayName("Validates null argument checks on command creation")
    void validatesNullPreconditionsOnCommandCreation() {
        Way way = way("residential", new LatLon(0, 0), new LatLon(0, 1));

        assertThrows(NullPointerException.class, () -> MaxSpeedEditorModel.createSpeedCommand(null, "maxspeed", "50"));
        assertThrows(NullPointerException.class, () -> MaxSpeedEditorModel.createSpeedCommand(way, null, "50"));
    }

    @Test
    @DisplayName("Validates plugin and popup default constants and preference keys")
    void validatesPluginConstantsAndDefaults() {
        assertEquals("maxspeed-editor.enabled", MaxSpeedEditorPlugin.PREF_ENABLED);
        assertEquals("maxspeed-editor.highways", MaxSpeedEditorPlugin.PREF_HIGHWAYS);
        assertEquals("maxspeed-editor-toggle", MaxSpeedEditorPlugin.TOOLBAR_ID);
        assertEquals("maxspeed-editor.presets", MaxSpeedPopup.PREF_PRESETS);

        assertNotNull(MaxSpeedEditorPlugin.DEFAULT_HIGHWAYS);
        assertTrue(MaxSpeedEditorPlugin.DEFAULT_HIGHWAYS.contains("primary"));
        assertTrue(MaxSpeedEditorPlugin.DEFAULT_HIGHWAYS.contains("residential"));
        assertTrue(MaxSpeedEditorPlugin.DEFAULT_HIGHWAYS.contains("motorway"));

        assertNotNull(MaxSpeedPopup.DEFAULT_PRESETS);
        assertEquals(Arrays.asList("20", "30", "40", "60", "70"), MaxSpeedPopup.DEFAULT_PRESETS);
    }

    private static Way way(String highway, LatLon... coordinates) {
        Way way = new Way();
        if (highway != null) {
            way.put("highway", highway);
        }
        Arrays.stream(coordinates).map(Node::new).forEach(way::addNode);
        return way;
    }

    private static Way dataSetWay(String highway, LatLon... coordinates) {
        Way way = way(highway, coordinates);
        DataSet dataSet = new DataSet();
        way.getNodes().forEach(dataSet::addPrimitive);
        dataSet.addPrimitive(way);
        return way;
    }
}