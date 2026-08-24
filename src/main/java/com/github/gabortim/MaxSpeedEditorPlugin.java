package com.github.gabortim;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.layer.MainLayerManager;
import org.openstreetmap.josm.gui.preferences.ToolbarPreferences;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.Logging;

import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.openstreetmap.josm.tools.I18n.tr;

/**
 * Maxspeed editor plugin for JOSM.
 * <p>
 * Facilitates fast, interactive tagging of maximum speed limits ({@code maxspeed},
 * {@code maxspeed:forward} and {@code maxspeed:backward}) on highways.
 * When enabled, clicking an editable selected highway opens an overlay popup menu directly
 * at the clicked position with contextual compass headings and speed presets.
 * <p>
 * <b>JOSM Integration:</b>
 * <ul>
 *   <li>Integrates into the Tools menu and JOSM's main toolbar with a toggle action.</li>
 *   <li>Dynamically binds selection and mouse listeners to active datasets and map frames.</li>
 *   <li>Leverages JOSM's command and undo/redo architecture ({@link org.openstreetmap.josm.data.UndoRedoHandler}).</li>
 *   <li>Supports configurable preferences via {@link Config#getPref()}.</li>
 * </ul>
 * </p>
 */
public class MaxSpeedEditorPlugin extends Plugin {

    /**
     * Preference key in JOSM preferences for enabling/disabling the plugin popup.
     */
    public static final String PREF_ENABLED = "maxspeed-editor.enabled";

    /**
     * Preference key for the list of highway types that trigger the editor UI.
     */
    public static final String PREF_HIGHWAYS = "maxspeed-editor.highways";

    /**
     * Action toolbar identifier for toolbar registration and persistence.
     */
    public static final String TOOLBAR_ID = MaxSpeedToggleAction.TOOLBAR_ID;

    /**
     * Default highway types eligible for speed limit editing.
     */
    public static final List<String> DEFAULT_HIGHWAYS = Arrays.asList(
            "motorway", "motorway_link", "trunk", "trunk_link", "primary", "primary_link",
            "secondary", "secondary_link", "tertiary", "tertiary_link", "unclassified",
            "residential", "living_street", "service", "track", "busway"
    );

    /**
     * Plugin activation state.
     */
    private boolean pluginEnabled;

    /**
     * Action that toggles the plugin state via menu and toolbar.
     */
    private final MaxSpeedToggleAction toggleAction;

    /**
     * Popup manager responsible for rendering the speed editor overlay.
     */
    private final MaxSpeedPopup popup;

    /**
     * Mouse listener on the map canvas to trigger the popup on click.
     */
    private final MouseAdapter mouseListener = new MapMouseAdapter();

    /**
     * Constructs and initializes the MaxSpeed Editor plugin.
     *
     * @param info JOSM plugin information provided by the plugin loader
     */
    public MaxSpeedEditorPlugin(PluginInformation info) {
        super(info);
        this.pluginEnabled = Config.getPref().getBoolean(PREF_ENABLED, true);
        this.toggleAction = new MaxSpeedToggleAction(pluginEnabled, this::onPluginToggled);
        this.popup = new MaxSpeedPopup(this::setStatusText);

        registerInMenu();
        registerInToolbar();
        updateMouseListener();

        Logging.debug("MaxSpeedEditor: plugin initialized (pluginEnabled=" + pluginEnabled + ")");
    }

    /**
     * Callback handler when the toggle state is updated via menu or toolbar.
     *
     * @param enabled new plugin enabled state
     */
    private void onPluginToggled(boolean enabled) {
        this.pluginEnabled = enabled;
        updateMouseListener();
        setStatusText(tr("MaxSpeed Editor {0}", enabled ? tr("enabled") : tr("disabled")));
    }

    /**
     * Registers the toggle action in JOSM's Tools menu.
     */
    private void registerInMenu() {
        if (MainApplication.getMenu() != null && MainApplication.getMenu().toolsMenu != null) {
            MainMenu.add(MainApplication.getMenu().toolsMenu, toggleAction);
            Logging.debug("MaxSpeedEditor: registered in Tools menu");
        } else {
            Logging.info("MaxSpeedEditor: Tools menu not found, menu registration skipped");
        }
    }

    /**
     * Registers the toggle action in JOSM's main toolbar and ensures it is included in the toolbar configuration.
     */
    private void registerInToolbar() {
        ToolbarPreferences toolbar = MainApplication.getToolbar();
        if (toolbar == null) {
            Logging.info("MaxSpeedEditor: Toolbar not found, toolbar registration skipped");
            return;
        }
        toolbar.register(toggleAction);

        // Ensure the button is present in the active toolbar tool string preceded by a separator
        List<String> currentToolbarItems = new ArrayList<>(ToolbarPreferences.getToolString());
        if (!currentToolbarItems.contains(TOOLBAR_ID)) {
            List<String> updatedToolbarItems = addToolbarItemWithSeparator(currentToolbarItems);
            Config.getPref().putList("toolbar", updatedToolbarItems);
            toolbar.refreshToolbarControl();
            Logging.debug("MaxSpeedEditor: toolbar button added (" + TOOLBAR_ID + ")");
        }
    }

    /**
     * Appends a toolbar item identifier to the toolbar item list, preceded by a separator if needed.
     *
     * @param currentItems existing list of toolbar item definitions
     * @return updated list containing the new item preceded by a separator if it was not already present
     */
    static List<String> addToolbarItemWithSeparator(List<String> currentItems) {
        List<String> items = new ArrayList<>(currentItems != null ? currentItems : Collections.emptyList());
        if (!items.contains(MaxSpeedEditorPlugin.TOOLBAR_ID)) {
            if (items.isEmpty() || !"|".equals(items.getLast())) {
                items.add("|");
            }
            items.add(MaxSpeedEditorPlugin.TOOLBAR_ID);
        }
        return items;
    }

    /**
     * Sets a feedback message in JOSM's bottom status line on the Swing Event Dispatch Thread (EDT).
     *
     * @param text message to display in the status bar
     */
    private void setStatusText(String text) {
        GuiHelper.runInEDT(() -> {
            MapFrame mapFrame = MainApplication.getMap();
            if (mapFrame != null && mapFrame.statusLine != null) {
                mapFrame.statusLine.setHelpText(text);
            }
        });
    }

    /**
     * Adds or removes the mouse listener on the current mapView based on plugin state.
     * AWT's addMouseListener/removeMouseListener are idempotent, so no tracking needed.
     */
    private void updateMouseListener() {
        MapView mapView = MainApplication.getMap() != null ? MainApplication.getMap().mapView : null;
        if (mapView == null) {
            return;
        }
        if (pluginEnabled) {
            mapView.addMouseListener(mouseListener);
        } else {
            mapView.removeMouseListener(mouseListener);
        }
    }

    /**
     * Mouse listener on the MapView canvas to trigger the popup on clicking a selected highway.
     * <p>
     * Listens to {@code mouseReleased} rather than {@code mouseClicked} because Swing's popup dismiss
     * mechanism consumes {@code mouseClicked} when clicking away from an active popup onto another highway.
     */
    class MapMouseAdapter extends MouseAdapter {
        private Point pressPoint;

        @Override
        public void mousePressed(MouseEvent e) {
            if (e.getButton() == MouseEvent.BUTTON1) {
                pressPoint = e.getPoint();
            } else {
                pressPoint = null;
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (!pluginEnabled
                    || e.getButton() != MouseEvent.BUTTON1
                    || e.isConsumed()
                    || e.isShiftDown()
                    || e.isControlDown()
                    || e.isAltDown()
                    || e.isMetaDown()
                    || MainApplication.getMap() == null) {
                pressPoint = null;
                return;
            }

            Point releasePoint = e.getPoint();
            if (pressPoint != null && pressPoint.distanceSq(releasePoint) > 100) {
                pressPoint = null;
                return;
            }
            pressPoint = null;

            Way clickedWay = getClickedHighwayNearPoint(releasePoint);
            if (clickedWay != null) {
                popup.show(clickedWay, e.getComponent(), releasePoint.x, releasePoint.y);
            }
        }
    }

    /**
     * Determines which allowed highway (if any) in the active selection was clicked within snap tolerance.
     *
     * @param clickPoint coordinates of the mouse click in screen pixel space
     * @return the selected {@link Way} if the click was on/near it, or {@code null} otherwise
     */
    Way getClickedHighwayNearPoint(Point clickPoint) {
        if (clickPoint == null) {
            return null;
        }
        MapFrame mapFrame = MainApplication.getMap();
        if (mapFrame == null || mapFrame.mapView == null) {
            return null;
        }
        MapView mapView = mapFrame.mapView;
        MainLayerManager layerManager = MainApplication.getLayerManager();
        DataSet activeDataSet = layerManager != null ? layerManager.getActiveDataSet() : null;
        if (activeDataSet == null) {
            return null;
        }
        Set<String> allowedHighways = new HashSet<>(Config.getPref().getList(PREF_HIGHWAYS, DEFAULT_HIGHWAYS));
        double maxDistance = Math.max(15.0, Config.getPref().getInt("mappaint.segment.snap-distance", 10));
        return MaxSpeedEditorModel.findAllowedHighwayNearPoint(
                activeDataSet.getSelected(),
                allowedHighways,
                clickPoint,
                node -> (node != null && node.getEastNorth() != null) ? mapView.getPoint(node.getEastNorth()) : null,
                maxDistance
        );
    }

    MouseAdapter getMouseListener() {
        return mouseListener;
    }

    /**
     * Handles MapFrame lifecycle events (e.g. when opening/closing datasets or changing map views).
     *
     * @param oldFrame previous MapFrame instance, or {@code null}
     * @param newFrame new MapFrame instance, or {@code null}
     */
    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        if (oldFrame != null && oldFrame.mapView != null) {
            oldFrame.mapView.removeMouseListener(mouseListener);
        }
        updateMouseListener();
    }

    /**
     * Cleans up all registered listeners and internal state.
     */
    public void destroy() {
        pluginEnabled = false;
        updateMouseListener();
    }
}