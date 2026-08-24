package com.github.gabortim;

import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;

import java.awt.Point;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.openstreetmap.josm.tools.I18n.tr;

/**
 * Domain model and helper functions for the Maxspeed editor plugin.
 * <p>
 * Encapsulates non-GUI business logic including:
 * <ul>
 *   <li>Filtering selections for editable highway ways</li>
 *   <li>Calculating geometric bearings and compass headings relative to clicked road segments</li>
 *   <li>Building atomic undo/redo {@link Command} sequences that manage tag conflicts
 *       between directional ({@code maxspeed:forward}/{@code maxspeed:backward}) and
 *       bidirectional ({@code maxspeed}) tags</li>
 * </ul>
 */
final class MaxSpeedEditorModel {

    /**
     * OSM tag key for bidirectional maximum speed limit.
     */
    static final String MAXSPEED = "maxspeed";

    /**
     * OSM tag key for maximum speed limit in the forward way direction.
     */
    static final String MAXSPEED_FORWARD = "maxspeed:forward";

    /**
     * OSM tag key for maximum speed limit in the backward way direction.
     */
    static final String MAXSPEED_BACKWARD = "maxspeed:backward";

    private MaxSpeedEditorModel() {
        // Utility class; prevent instantiation.
    }

    /**
     * Finds the first {@link Way} in the selection matching the allowed highway types.
     *
     * @param selection       a collection of currently selected OSM primitives may be {@code null}
     * @param allowedHighways set of allowed {@code highway} tag values in lowercase, may be {@code null}
     * @return the first matching {@link Way}, or {@code null} if no match is found
     */
    static Way findAllowedHighway(Collection<? extends OsmPrimitive> selection, Set<String> allowedHighways) {
        return findMatchingHighway(selection, allowedHighways, way -> true);
    }

    /**
     * Finds the first {@link Way} in the selection matching the allowed highway types that is within
     * the specified maximum screen pixel distance to the click point.
     *
     * @param selection       a collection of currently selected OSM primitives may be {@code null}
     * @param allowedHighways set of allowed {@code highway} tag values in lowercase, may be {@code null}
     * @param clickPoint      the coordinates of the mouse click in screen pixel space, may be {@code null}
     * @param projector       function mapping a {@link Node} to screen pixel {@link Point}, may be {@code null}
     * @param maxDistance     maximum allowed distance in screen pixels
     * @return the matching {@link Way} within {@code maxDistance} of the click point, or {@code null} if no match is near the click
     */
    static Way findAllowedHighwayNearPoint(
            Collection<? extends OsmPrimitive> selection,
            Set<String> allowedHighways,
            Point clickPoint,
            Function<Node, Point> projector,
            double maxDistance) {
        if (clickPoint == null || projector == null || maxDistance < 0) {
            return null;
        }
        return findMatchingHighway(selection, allowedHighways,
                way -> isClickNearWay(way, clickPoint, projector, maxDistance));
    }

    private static Way findMatchingHighway(
            Collection<? extends OsmPrimitive> selection,
            Set<String> allowedHighways,
            Predicate<Way> filter) {
        if (selection == null || allowedHighways == null) {
            return null;
        }
        return selection.stream()
                .filter(p -> p instanceof Way)
                .map(p -> (Way) p)
                .filter(way -> {
                    String highway = way.get("highway");
                    return highway != null && allowedHighways.contains(highway.trim().toLowerCase(Locale.ROOT));
                })
                .filter(filter)
                .findFirst()
                .orElse(null);
    }

    /**
     * Checks if a click point is within a maximum screen pixel distance to any segment of the given way.
     *
     * @param way         the OSM highway way, may be {@code null}
     * @param clickPoint  the coordinates of the mouse click in screen pixel space, may be {@code null}
     * @param projector   function mapping a {@link Node} to screen pixel {@link Point}, may be {@code null}
     * @param maxDistance maximum allowed distance in screen pixels
     * @return {@code true} if the click point is within {@code maxDistance} of any way segment, {@code false} otherwise
     */
    static boolean isClickNearWay(
            Way way,
            Point clickPoint,
            Function<Node, Point> projector,
            double maxDistance) {
        if (maxDistance < 0) {
            return false;
        }
        SegmentMatch closest = findClosestSegment(way, clickPoint, projector);
        return closest != null && closest.distanceSquared <= maxDistance * maxDistance;
    }

    /**
     * Calculates the directional heading of the way segment that is closest to the given click point.
     *
     * @param way        the OSM highway way
     * @param clickPoint the coordinates of the mouse click in screen pixel space
     * @param projector  function mapping a {@link Node} to screen pixel {@link Point}
     * @return a {@link WaySegmentHeading} representing the compass heading and cardinals of the closest segment
     */
    static WaySegmentHeading calculateHeading(Way way, Point clickPoint, Function<Node, Point> projector) {
        SegmentMatch closest = findClosestSegment(way, clickPoint, projector);
        if (closest == null) {
            return WaySegmentHeading.unknown();
        }
        double bearing = Math.toDegrees(closest.start.getCoor().bearing(closest.end.getCoor()));
        return new WaySegmentHeading(bearing);
    }

    private static SegmentMatch findClosestSegment(Way way, Point clickPoint, Function<Node, Point> projector) {
        if (way == null || clickPoint == null || projector == null || way.getNodesCount() < 2) {
            return null;
        }

        List<Node> nodes = way.getNodes();
        SegmentMatch bestMatch = null;
        double minDistanceSquared = Double.MAX_VALUE;

        for (int i = 0; i < nodes.size() - 1; i++) {
            Node start = nodes.get(i);
            Node end = nodes.get(i + 1);
            if (start == null || end == null || start.getCoor() == null || end.getCoor() == null) {
                continue;
            }
            Point startPoint = projector.apply(start);
            Point endPoint = projector.apply(end);
            if (startPoint == null || endPoint == null) {
                continue;
            }
            double distanceSquared = Line2D.ptSegDistSq(
                    startPoint.x, startPoint.y, endPoint.x, endPoint.y, clickPoint.x, clickPoint.y);
            if (distanceSquared < minDistanceSquared) {
                minDistanceSquared = distanceSquared;
                bestMatch = new SegmentMatch(start, end, minDistanceSquared);
            }
        }

        return bestMatch;
    }

    private record SegmentMatch(Node start, Node end, double distanceSquared) {}

    /**
     * Formats an azimuth bearing in degrees into an 8-point cardinal direction with arrow.
     *
     * @param bearing azimuth bearing in degrees (0 = North, 90 = East, 180 = South, 270 = West)
     * @return localized cardinal direction string with arrow
     */
    static String formatCardinal(double bearing) {
        return formatSector(bearing, new String[]{
                tr("North ↑"), tr("Northeast ↗"), tr("East →"), tr("Southeast ↘"),
                tr("South ↓"), tr("Southwest ↙"), tr("West ←"), tr("Northwest ↖")
        });
    }

    /**
     * Formats an azimuth bearing in degrees into a 4-point bidirectional compass axis.
     *
     * @param bearing azimuth bearing in degrees
     * @return localized bidirectional axis string
     */
    static String formatBothCardinal(double bearing) {
        return formatSector(bearing, new String[]{
                tr("North–South ↕"), tr("Northeast–Southwest ⤢"),
                tr("East–West ↔"), tr("Northwest–Southeast ⤡")
        });
    }

    private static String formatSector(double bearing, String[] labels) {
        int index = Math.floorMod((int) Math.round(bearing / 45.0), 8) % labels.length;
        return labels[index];
    }

    /**
     * Creates an atomic JOSM {@link SequenceCommand} to set or clear a speed limit tag on a way.
     * <p>
     * Automatically resolves tag conflicts:
     * <ul>
     *   <li>Setting {@code maxspeed} clears existing {@code maxspeed:forward} and {@code maxspeed:backward} tags.</li>
     *   <li>Setting {@code maxspeed:forward} or {@code maxspeed:backward} with a non-null value clears
     *       any existing {@code maxspeed} tag.</li>
     * </ul>
     *
     * @param way   the target OSM way
     * @param key   the speed tag key (e.g. {@code maxspeed}, {@code maxspeed:forward}, {@code maxspeed:backward})
     * @param value the speed limit value to set, or {@code null} to remove the tag
     * @return an atomic {@link SequenceCommand} ready for execution and undo/redo registration
     */
    static SequenceCommand createSpeedCommand(Way way, String key, String value) {
        Objects.requireNonNull(way, "way must not be null");
        Objects.requireNonNull(key, "key must not be null");

        List<Command> commands = new ArrayList<>();
        commands.add(new ChangePropertyCommand(way, key, value));

        if (MAXSPEED.equals(key)) {
            removeIfPresent(commands, way, MAXSPEED_FORWARD, MAXSPEED_BACKWARD);
        } else if (value != null) {
            removeIfPresent(commands, way, MAXSPEED);
        }

        String summary = tr("Set {0}={1}", key, value == null ? tr("<cleared>") : value);
        return new SequenceCommand(summary, commands);
    }

    private static void removeIfPresent(List<Command> commands, Way way, String... keys) {
        java.util.Arrays.stream(keys)
                .filter(way::hasKey)
                .map(k -> new ChangePropertyCommand(way, k, null))
                .forEach(commands::add);
    }

    /**
     * Immutable value object representing the compass heading and cardinal directions
     * of a highway segment relative to the way's node sequence.
     */
    public record WaySegmentHeading(double forwardBearing, String forwardCardinal, String backwardCardinal, String bothCardinal) {
        public WaySegmentHeading(double forwardBearing) {
            this(
                    forwardBearing,
                    formatCardinal(forwardBearing),
                    formatCardinal(forwardBearing + 180.0),
                    formatBothCardinal(forwardBearing)
            );
        }

        public static WaySegmentHeading unknown() {
            String unknownStr = tr("Unknown");
            return new WaySegmentHeading(Double.NaN, unknownStr, unknownStr, unknownStr);
        }

        public boolean isKnown() {
            return !Double.isNaN(forwardBearing);
        }
    }
}