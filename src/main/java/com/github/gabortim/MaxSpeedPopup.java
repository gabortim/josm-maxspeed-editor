package com.github.gabortim;

import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.widgets.DisableShortcutsOnFocusGainedTextField;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.ImageProvider;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static org.openstreetmap.josm.tools.I18n.tr;

/**
 * Interactive Swing popup menu UI for viewing and modifying {@code maxspeed} tags on OSM ways.
 * <p>
 * Displays a compact, aligned grid with three directional rows:
 * <ul>
 *   <li><b>Forward</b> ({@code maxspeed:forward}) with directional compass arrow</li>
 *   <li><b>Both Directions</b> ({@code maxspeed}) with bidirectional compass axis</li>
 *   <li><b>Backward</b> ({@code maxspeed:backward}) with directional compass arrow</li>
 * </ul>
 * <p>
 * Respects OSM one-way restrictions by disabling invalid direction controls on one-way and
 * reverse one-way roads. Speed updates are dispatched through JOSM's {@link UndoRedoHandler}
 * for undo/redo integration.
 */
class MaxSpeedPopup {

    /**
     * Preference key for configurable speed limit presets list.
     */
    static final String PREF_PRESETS = "maxspeed-editor.presets";

    /**
     * Default speed presets in km/h.
     */
    static final List<String> DEFAULT_PRESETS = Arrays.asList("20", "30", "40", "60", "70");

    private static final Color ACTIVE_GREEN = new Color(0, 128, 0);
    private static final Color ACTIVE_BG_TINT = new Color(225, 245, 225);

    private final Consumer<String> statusConsumer;

    /**
     * Constructs a popup manager with a status notification callback.
     *
     * @param statusConsumer consumer receiving user feedback messages for JOSM's status bar
     */
    MaxSpeedPopup(Consumer<String> statusConsumer) {
        this.statusConsumer = Objects.requireNonNull(statusConsumer, "statusConsumer must not be null");
    }

    /**
     * Displays the interactive speed limit editor popup at the specified screen coordinates.
     *
     * @param way     the selected OSM highway way
     * @param invoker the component over which the popup will be displayed (typically {@link MapView})
     * @param x       x-coordinate in invoker coordinate space
     * @param y       y-coordinate in invoker coordinate space
     */
    void show(Way way, Component invoker, int x, int y) {
        if (way == null || invoker == null || !invoker.isShowing()) {
            return;
        }

        // Create the popup menu container
        JPopupMenu popup = new JPopupMenu(tr("MaxSpeed Editor"));

        // Temporarily disable global JOSM single-key shortcuts while popup is open
        // so typed numbers/letters go into text fields rather than triggering map tools
        PopupShortcutDisabler disabler = new PopupShortcutDisabler();
        popup.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                disabler.disableShortcuts();
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                disabler.restoreShortcuts();
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
                disabler.restoreShortcuts();
            }
        });

        // Main vertical container with outer margin padding
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        Point clickPoint = new Point(x, y);

        // Populate popup contents and show at clicked map location
        rebuild(container, way, clickPoint, popup);
        popup.add(container);
        popup.show(invoker, x, y);
    }

    /**
     * Constructs or rebuilds the popup contents dynamically.
     *
     * @param container  the parent container panel
     * @param way        the target OSM way
     * @param clickPoint the click point used to compute segment heading
     * @param popup      the parent popup menu
     */
    void rebuild(JPanel container, Way way, Point clickPoint, JPopupMenu popup) {
        container.removeAll();

        // 1. Calculate compass heading of the specific clicked way segment
        MaxSpeedEditorModel.WaySegmentHeading heading;
        if (MainApplication.getMap() != null && MainApplication.getMap().mapView != null) {
            MapView mapView = MainApplication.getMap().mapView;
            heading = MaxSpeedEditorModel.calculateHeading(
                    way, clickPoint, node -> (node != null && node.getEastNorth() != null)
                            ? mapView.getPoint(node.getEastNorth()) : null);
        } else {
            heading = MaxSpeedEditorModel.WaySegmentHeading.unknown();
        }

        // 2. Build title header with street name or highway type and way ID
        String name = way.get("name");
        String highway = way.get("highway");
        String titleText;
        if (name != null && !name.isEmpty()) {
            titleText = tr("MaxSpeed — {0} (#{1})", name, way.getUniqueId());
        } else if (highway != null && !highway.isEmpty()) {
            titleText = tr("MaxSpeed — Way #{0} ({1})", way.getUniqueId(), highway);
        } else {
            titleText = tr("MaxSpeed — Way #{0}", way.getUniqueId());
        }

        // Title label - explicitly left-aligned to prevent BoxLayout center-offset bug
        JLabel title = new JLabel(titleText);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        container.add(title);

        // Horizontal dividing line beneath the title
        JSeparator separator = new JSeparator();
        separator.setAlignmentX(Component.LEFT_ALIGNMENT);
        container.add(separator);

        // Small vertical spacing between separator and controls
        Component strut = Box.createVerticalStrut(4);
        ((JComponent) strut).setAlignmentX(Component.LEFT_ALIGNMENT);
        container.add(strut);

        // Speed presets loaded from JOSM preferences (default: 20, 30, 40, 60, 70)
        List<String> presets = Config.getPref().getList(PREF_PRESETS, DEFAULT_PRESETS);

        // Callback to dynamically refresh/re-render popup on value change
        Runnable refresh = () -> {
            rebuild(container, way, clickPoint, popup);
            container.revalidate();
            container.repaint();
            popup.pack();
        };

        // 3. Compact unified grid layout for aligned columns across all 3 direction rows
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Check oneway status: 1 = forward only, -1 = backward only, 0 = two-way
        int oneway = way.isOneway();

        // Row 0: Forward direction controls (disabled if reversed oneway)
        String forwardLabel = tr("Forward") + " [" + heading.forwardCardinal() + "]";
        if (oneway == -1) {
            forwardLabel += " (" + tr("Reversed Oneway") + ")";
        }
        addDirectionRow(grid, 0, way, forwardLabel, MaxSpeedEditorModel.MAXSPEED_FORWARD,
                presets, oneway != -1, popup, refresh);

        // Row 1: Both directions controls (always enabled)
        String bothLabel = tr("Both") + " [" + heading.bothCardinal() + "]";
        addDirectionRow(grid, 1, way, bothLabel, MaxSpeedEditorModel.MAXSPEED,
                presets, true, popup, refresh);

        // Row 2: Backward direction controls (disabled if normal oneway)
        String backwardLabel = tr("Backward") + " [" + heading.backwardCardinal() + "]";
        if (oneway == 1) {
            backwardLabel += " (" + tr("Oneway") + ")";
        }
        addDirectionRow(grid, 2, way, backwardLabel, MaxSpeedEditorModel.MAXSPEED_BACKWARD,
                presets, oneway != 1, popup, refresh);

        container.add(grid);
    }

    /**
     * Adds an aligned row of direction controls to the grid.
     *
     * @param grid      the grid container
     * @param row       the grid row index (0, 1, 2)
     * @param way       target OSM way
     * @param labelText directional label text
     * @param key       OSM tag key (e.g. {@code maxspeed:forward})
     * @param presets   list of speed preset values
     * @param enabled   whether editing is enabled for this direction
     * @param popup     parent popup menu
     * @param refresh   rebuild callback
     */
    private void addDirectionRow(
            JPanel grid,
            int row,
            Way way,
            String labelText,
            String key,
            List<String> presets,
            boolean enabled,
            JPopupMenu popup,
            Runnable refresh
    ) {
        String currentValue = way.get(key);

        // --- Column 0: Direction & Compass Heading Label (e.g. "Forward [North ↑]") ---
        JLabel label = new JLabel(labelText);
        label.setEnabled(enabled);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        grid.add(label, createGbc(0, row, GridBagConstraints.WEST, new Insets(2, 0, 2, 8)));

        // --- Column 1: Preset speed buttons (e.g. 20, 30, 40, 60, 70) ---
        JPanel presetsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        for (String preset : presets) {
            JButton button = new JButton(preset);
            configureButton(button, enabled);

            // Highlight button in green if it matches the current OSM tag value
            if (Objects.equals(currentValue, preset)) {
                button.setFont(button.getFont().deriveFont(Font.BOLD));
                button.setForeground(ACTIVE_GREEN);
                button.setBackground(ACTIVE_BG_TINT);
                button.setToolTipText(tr("Current speed limit is {0} ({1}={2})", preset, key, preset));
            } else {
                button.setToolTipText(tr("Set {0}={1}", key, preset));
            }
            // Clicking a preset immediately applies the tag
            button.addActionListener(event -> apply(way, key, preset, popup, refresh));
            presetsPanel.add(button);
        }
        grid.add(presetsPanel, createGbc(1, row, GridBagConstraints.CENTER, new Insets(2, 0, 2, 6)));

        // --- Column 2: Custom speed input text field ---
        JTextField customValue = new DisableShortcutsOnFocusGainedTextField(3) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(38, super.getPreferredSize().height);
            }

            @Override
            public Dimension getMinimumSize() {
                return getPreferredSize();
            }
        };
        customValue.setHorizontalAlignment(JTextField.CENTER);
        customValue.setMargin(new Insets(1, 2, 1, 2));
        customValue.setEnabled(enabled);

        // If the current tag value is non-standard (not among presets), display and highlight it in the text field
        boolean isCustomActive = currentValue != null && !presets.contains(currentValue);
        if (isCustomActive) {
            customValue.setText(currentValue);
            customValue.setFont(customValue.getFont().deriveFont(Font.BOLD));
            customValue.setForeground(ACTIVE_GREEN);
            customValue.setToolTipText(tr("Current custom speed: {0}. Enter new value to change.", currentValue));
        } else {
            customValue.setToolTipText(tr("Enter custom speed limit (e.g. 50, 80, walk)"));
        }
        grid.add(customValue, createGbc(2, row, GridBagConstraints.CENTER, new Insets(2, 2, 2, 2)));

        // --- Column 3: "Set" / Apply button for custom speed input ---
        JButton setButton = new JButton();
        ImageIcon okIcon = getSmallIcon("ok");
        if (okIcon != null) {
            setButton.setIcon(okIcon);
        } else {
            setButton.setText(tr("Set"));
        }
        configureIconButton(setButton, enabled);
        setButton.setToolTipText(tr("Apply custom speed limit for {0}", key));
        setButton.addActionListener(event -> {
            String value = customValue.getText().trim();
            if (!value.isEmpty()) {
                apply(way, key, value, popup, refresh);
            }
        });
        // Pressing Enter in the text field triggers the Set button
        customValue.addActionListener(event -> setButton.doClick());
        grid.add(setButton, createGbc(3, row, GridBagConstraints.CENTER, new Insets(2, 2, 2, 2)));

        // --- Column 4: "Clear" / Delete button to remove this direction's tag ---
        JButton clearButton = new JButton();
        ImageIcon clearIcon = getSmallIcon("dialogs/delete");
        if (clearIcon == null) {
            clearIcon = getSmallIcon("cancel");
        }
        if (clearIcon != null) {
            clearButton.setIcon(clearIcon);
        } else {
            clearButton.setText(tr("Clear"));
        }
        configureIconButton(clearButton, enabled && currentValue != null);
        clearButton.setToolTipText(tr("Remove tag {0}", key));
        clearButton.addActionListener(event -> {
            applySpeed(way, key, null);
            refresh.run();
        });
        grid.add(clearButton, createGbc(4, row, GridBagConstraints.CENTER, new Insets(2, 2, 2, 0)));
    }

    /**
     * Helper to construct a standardized {@link GridBagConstraints} for UI elements in the grid.
     */
    private GridBagConstraints createGbc(int x, int y, int anchor, Insets insets) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = anchor;
        gbc.insets = insets;
        return gbc;
    }

    /**
     * Safely loads a small icon from JOSM's {@link ImageProvider}.
     *
     * @param name the icon name or path
     * @return the loaded icon, or {@code null} if unavailable
     */
    static ImageIcon getSmallIcon(String name) {
        try {
            return new ImageProvider(name).setSize(ImageProvider.ImageSizes.SMALLICON).setOptional(true).get();
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Configures general button properties with compact margins.
     *
     * @param button  target button
     * @param enabled enabled state
     */
    private static void configureButton(JButton button, boolean enabled) {
        button.setEnabled(enabled);
        button.setMargin(new Insets(2, 5, 2, 5));
        button.setFocusable(false);
    }

    /**
     * Configures icon button properties with compact margins.
     *
     * @param button  target icon button
     * @param enabled enabled state
     */
    private static void configureIconButton(JButton button, boolean enabled) {
        button.setEnabled(enabled);
        button.setMargin(new Insets(2, 4, 2, 4));
        button.setFocusable(false);
    }

    /**
     * Applies a speed change, closing the popup for bidirectional edits or refreshing for directional edits.
     *
     * @param way     target OSM way
     * @param key     tag key
     * @param value   speed value
     * @param popup   parent popup menu
     * @param refresh callback to re-render popup
     */
    private void apply(Way way, String key, String value, JPopupMenu popup, Runnable refresh) {
        applySpeed(way, key, value);
        if (MaxSpeedEditorModel.MAXSPEED.equals(key)) {
            // Setting a broad bidirectional speed usually completes the edit, so auto-close popup
            popup.setVisible(false);
        } else {
            // Setting directional speed (e.g. forward) keeps popup open so user can edit backward speed
            refresh.run();
        }
    }

    /**
     * Adds the speed limit command to JOSM's {@link UndoRedoHandler} and updates the status feedback.
     *
     * @param way   target OSM way
     * @param key   tag key
     * @param value speed value, or {@code null} to clear
     */
    private void applySpeed(Way way, String key, String value) {
        UndoRedoHandler.getInstance().add(MaxSpeedEditorModel.createSpeedCommand(way, key, value));
        statusConsumer.accept(tr("Applied {0}={1}", key, value == null ? tr("<cleared>") : value));
    }
}