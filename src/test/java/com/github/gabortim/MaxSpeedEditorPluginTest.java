package com.github.gabortim;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.preferences.ToolbarPreferences;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.spi.preferences.Config;

import javax.swing.JPanel;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link MaxSpeedEditorPlugin} lifecycle, toolbar formatting, and mouse event handling.
 */
class MaxSpeedEditorPluginTest {

    @BeforeAll
    static void setupJosm() {
        Preferences pref = new Preferences();
        pref.enableSaveOnPut(false);
        Config.setPreferencesInstance(pref);
        Config.setBaseDirectoriesProvider(org.openstreetmap.josm.data.preferences.JosmBaseDirectories.getInstance());
        org.openstreetmap.josm.data.projection.ProjectionRegistry.setProjection(
                org.openstreetmap.josm.data.projection.Projections.getProjectionByCode("EPSG:3857")
        );
        try {
            Field cpField = MainApplication.class.getDeclaredField("contentPanePrivate");
            cpField.setAccessible(true);
            if (cpField.get(null) == null) {
                cpField.set(null, new JPanel());
            }
            Field tbField = MainApplication.class.getDeclaredField("toolbar");
            tbField.setAccessible(true);
            if (tbField.get(null) == null) {
                tbField.set(null, new ToolbarPreferences());
            }
            Field menuField = MainApplication.class.getDeclaredField("menu");
            menuField.setAccessible(true);
            if (menuField.get(null) == null) {
                menuField.set(null, new MainMenu());
            }
        } catch (Throwable t) {
            System.err.println("setupJosm error: " + t);
            t.printStackTrace();
        }
    }

    @Test
    @DisplayName("Toolbar helper adds separator before plugin icon when not already present")
    void toolbarHelperAddsSeparator() {
        List<String> items = Arrays.asList("open", "save");
        List<String> updated = MaxSpeedEditorPlugin.addToolbarItemWithSeparator(items);
        assertEquals(Arrays.asList("open", "save", "|", "maxspeed-editor-toggle"), updated);
    }

    @Test
    @DisplayName("Toolbar helper avoids duplicate separator if list already ends with one")
    void toolbarHelperAvoidsDuplicateSeparator() {
        List<String> items = Arrays.asList("open", "|");
        List<String> updated = MaxSpeedEditorPlugin.addToolbarItemWithSeparator(items);
        assertEquals(Arrays.asList("open", "|", "maxspeed-editor-toggle"), updated);
    }

    @Test
    @DisplayName("Toolbar helper handles empty and null lists safely")
    void toolbarHelperHandlesEmptyAndNull() {
        assertEquals(
                Arrays.asList("|", "maxspeed-editor-toggle"),
                MaxSpeedEditorPlugin.addToolbarItemWithSeparator(Collections.emptyList())
        );
        assertEquals(
                Arrays.asList("|", "maxspeed-editor-toggle"),
                MaxSpeedEditorPlugin.addToolbarItemWithSeparator(null)
        );
    }

    @Test
    @DisplayName("Toolbar helper does not add duplicate when toolbar ID already exists")
    void toolbarHelperDoesNotDuplicateExisting() {
        List<String> items = Arrays.asList("open", "|", "maxspeed-editor-toggle");
        List<String> updated = MaxSpeedEditorPlugin.addToolbarItemWithSeparator(items);
        assertEquals(items, updated);
    }

    @Test
    @DisplayName("Mouse adapter handles consecutive clicks and distinguishes clicks from drags")
    void mouseAdapterHandlesConsecutiveClicksAndDrags() throws Exception {
        AtomicInteger popupShownCount = new AtomicInteger(0);

        PluginInformation info = new PluginInformation(new java.util.jar.Attributes(), "MaxSpeedEditorPlugin", null);
        MaxSpeedEditorPlugin plugin = new MaxSpeedEditorPlugin(info);

        // Inject a test popup to count show invocations
        Field popupField = MaxSpeedEditorPlugin.class.getDeclaredField("popup");
        popupField.setAccessible(true);
        popupField.set(plugin, new MaxSpeedPopup(status -> {
        }) {
            @Override
            void show(Way way, java.awt.Component invoker, int x, int y) {
                if (way != null) {
                    popupShownCount.incrementAndGet();
                }
            }
        });

        // Setup mock MapFrame and DataSet
        DataSet ds = new DataSet();
        Way way1 = createWay(ds, "primary");
        Way way2 = createWay(ds, "secondary");

        // Set active layer in JOSM layer manager
        org.openstreetmap.josm.gui.layer.OsmDataLayer layer = new org.openstreetmap.josm.gui.layer.OsmDataLayer(ds, "test", null);
        MainApplication.getLayerManager().addLayer(layer);

        // Setup MapFrame
        MapFrame mapFrame = new MapFrame(null);

        Field mapField = MainApplication.class.getDeclaredField("map");
        mapField.setAccessible(true);
        mapField.set(null, mapFrame);

        java.awt.Component mockComponent = mapFrame.mapView;

        // 1. Select way1 and simulate mouse click directly on way1 at (100, 0)
        ds.setSelected(way1);
        MouseEvent press1 = new MouseEvent(mockComponent, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                0, 100, 0, 1, false, MouseEvent.BUTTON1);
        MouseEvent release1 = new MouseEvent(mockComponent, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(),
                0, 100, 0, 1, false, MouseEvent.BUTTON1);

        plugin.getMouseListener().mousePressed(press1);
        plugin.getMouseListener().mouseReleased(release1);

        // 2. Immediately click way2 directly on way2 at (150, 0) (simulating consecutive click)
        ds.setSelected(way2);
        MouseEvent press2 = new MouseEvent(mockComponent, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                0, 150, 0, 1, false, MouseEvent.BUTTON1);
        MouseEvent release2 = new MouseEvent(mockComponent, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(),
                0, 150, 0, 1, false, MouseEvent.BUTTON1);

        plugin.getMouseListener().mousePressed(press2);
        plugin.getMouseListener().mouseReleased(release2);

        // 3. Simulate click away in empty space (at (100, 100), 100px away from way on y=0) while way1 is selected
        ds.setSelected(way1);
        MouseEvent pressClickAway = new MouseEvent(mockComponent, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                0, 100, 100, 1, false, MouseEvent.BUTTON1);
        MouseEvent releaseClickAway = new MouseEvent(mockComponent, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(),
                0, 100, 100, 1, false, MouseEvent.BUTTON1);

        plugin.getMouseListener().mousePressed(pressClickAway);
        plugin.getMouseListener().mouseReleased(releaseClickAway);

        // 4. Simulate click away with empty selection
        ds.setSelected(Collections.emptyList());
        MouseEvent pressEmpty = new MouseEvent(mockComponent, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                0, 100, 0, 1, false, MouseEvent.BUTTON1);
        MouseEvent releaseEmpty = new MouseEvent(mockComponent, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(),
                0, 100, 0, 1, false, MouseEvent.BUTTON1);

        plugin.getMouseListener().mousePressed(pressEmpty);
        plugin.getMouseListener().mouseReleased(releaseEmpty);

        // 5. Simulate a drag (press at (200, 0), release at (250, 0) -> distance > 10px)
        ds.setSelected(way1);
        MouseEvent pressDrag = new MouseEvent(mockComponent, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                0, 200, 0, 1, false, MouseEvent.BUTTON1);
        MouseEvent releaseDrag = new MouseEvent(mockComponent, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(),
                0, 250, 0, 1, false, MouseEvent.BUTTON1);

        plugin.getMouseListener().mousePressed(pressDrag);
        plugin.getMouseListener().mouseReleased(releaseDrag);

        // 6. Simulate a right click (BUTTON3)
        MouseEvent pressRight = new MouseEvent(mockComponent, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                0, 100, 0, 1, false, MouseEvent.BUTTON3);
        MouseEvent releaseRight = new MouseEvent(mockComponent, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(),
                0, 100, 0, 1, false, MouseEvent.BUTTON3);

        plugin.getMouseListener().mousePressed(pressRight);
        plugin.getMouseListener().mouseReleased(releaseRight);

        // 7. Simulate a Shift-click
        MouseEvent pressShift = new MouseEvent(mockComponent, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                InputEvent.SHIFT_DOWN_MASK, 100, 0, 1, false, MouseEvent.BUTTON1);
        MouseEvent releaseShift = new MouseEvent(mockComponent, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(),
                InputEvent.SHIFT_DOWN_MASK, 100, 0, 1, false, MouseEvent.BUTTON1);

        plugin.getMouseListener().mousePressed(pressShift);
        plugin.getMouseListener().mouseReleased(releaseShift);

        // Only the 2 direct left-clicks on way1 and way2 should have triggered the popup
        assertEquals(2, popupShownCount.get());

        // Cleanup
        mapField.set(null, null);
        plugin.destroy();
    }

    private static Way createWay(DataSet ds, String highway) {
        Node node1 = new Node(new LatLon(0, 0));
        Node node2 = new Node(new LatLon(0, 1));
        ds.addPrimitive(node1);
        ds.addPrimitive(node2);

        Way way = new Way();
        way.put("highway", highway);
        way.setNodes(Arrays.asList(node1, node2));
        ds.addPrimitive(way);
        return way;
    }
}
