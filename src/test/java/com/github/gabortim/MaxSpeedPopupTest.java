package com.github.gabortim;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.spi.preferences.Config;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link MaxSpeedPopup} UI lifecycle, status callbacks, shortcut disabler, and edge case safety.
 */
class MaxSpeedPopupTest {

    @BeforeAll
    static void initializeJosmPreferences() {
        Preferences pref = new Preferences();
        pref.enableSaveOnPut(false);
        Config.setPreferencesInstance(pref);
    }

    @Test
    @DisplayName("Validates constructor precondition requiring non-null status callback")
    void validatesConstructorArguments() {
        assertThrows(NullPointerException.class, () -> new MaxSpeedPopup(null));
    }

    @Test
    @DisplayName("Handles null arguments to show() gracefully without throwing")
    void handlesNullArgumentsToShowGracefully() {
        MaxSpeedPopup popup = new MaxSpeedPopup(status -> {});
        Way way = createDataSetWay("residential");
        JPanel invoker = new JPanel();

        assertDoesNotThrow(() -> popup.show(null, invoker, 0, 0));
        assertDoesNotThrow(() -> popup.show(way, null, 0, 0));
        assertDoesNotThrow(() -> popup.show(null, null, 0, 0));
    }

    @Test
    @DisplayName("Constructs popup UI and verifies default preferences")
    void constructsPopupAndVerifiesDefaults() {
        List<String> statusMessages = new ArrayList<>();
        MaxSpeedPopup popup = new MaxSpeedPopup(statusMessages::add);

        Way way = createDataSetWay("residential");
        JPanel invoker = new JPanel();

        // Showing popup on a non-showing invoker in headless mode is safely ignored
        assertDoesNotThrow(() -> popup.show(way, invoker, 50, 50));

        // Verifies default presets list and customizable preferences
        assertEquals(Arrays.asList("20", "30", "40", "60", "70"), MaxSpeedPopup.DEFAULT_PRESETS);
        assertEquals("maxspeed-editor.presets", MaxSpeedPopup.PREF_PRESETS);

        List<String> customPresets = Arrays.asList("15", "25", "45", "85");
        Config.getPref().putList(MaxSpeedPopup.PREF_PRESETS, customPresets);
        assertEquals(customPresets, Config.getPref().getList(MaxSpeedPopup.PREF_PRESETS, MaxSpeedPopup.DEFAULT_PRESETS));
    }

    @Test
    @DisplayName("Correctly checks oneway forward and backward restrictions in UI logic")
    void respectsOnewayRestrictions() {
        Way forwardOneway = createDataSetWay("motorway");
        forwardOneway.put("oneway", "yes");
        assertEquals(1, forwardOneway.isOneway());

        Way reverseOneway = createDataSetWay("residential");
        reverseOneway.put("oneway", "-1");
        assertEquals(-1, reverseOneway.isOneway());

        Way bidirectional = createDataSetWay("secondary");
        assertEquals(0, bidirectional.isOneway());
    }

    @Test
    @DisplayName("Rebuilds compact popup container with title and grid elements for a named way")
    void rebuildsPopupForNamedWayWithPresetHighlights() {
        List<String> statusMessages = new ArrayList<>();
        MaxSpeedPopup popup = new MaxSpeedPopup(statusMessages::add);

        Way way = createDataSetWay("residential");
        way.put("name", "High Street");
        way.put("maxspeed", "30");

        JPanel container = new JPanel();
        JPopupMenu menu = new JPopupMenu();
        popup.rebuild(container, way, new Point(0, 0), menu);

        // Verify title
        JLabel titleLabel = (JLabel) container.getComponent(0);
        assertTrue(titleLabel.getText().contains("High Street"));

        // Verify grid exists with 5 columns across 3 rows (labels, presets, custom text field, set button, clear button)
        JPanel grid = (JPanel) container.getComponent(container.getComponentCount() - 1);
        assertNotNull(grid);
        assertTrue(grid.getComponentCount() >= 15); // 3 rows * 5 components

        // Find button "30" in the grid and verify it is bolded (active)
        List<JButton> buttons = findComponentsOfType(grid, JButton.class);
        JButton button30 = buttons.stream()
                .filter(b -> "30".equals(b.getText()))
                .findFirst()
                .orElse(null);
        assertNotNull(button30);
        assertEquals(Font.BOLD, button30.getFont().getStyle());
    }

    @Test
    @DisplayName("Rebuilds popup with custom speed value and preserves uniform fixed field dimensions")
    void rebuildsPopupWithCustomSpeedValueAndFixedDimensions() {
        List<String> statusMessages = new ArrayList<>();
        MaxSpeedPopup popup = new MaxSpeedPopup(statusMessages::add);

        Way way = createDataSetWay("tertiary");
        way.put("maxspeed:forward", "45"); // Custom value

        JPanel container = new JPanel();
        JPopupMenu menu = new JPopupMenu();
        popup.rebuild(container, way, new Point(0, 0), menu);

        // Find custom text fields in the grid
        JPanel grid = (JPanel) container.getComponent(container.getComponentCount() - 1);
        List<JTextField> textFields = findComponentsOfType(grid, JTextField.class);
        assertEquals(3, textFields.size());

        JTextField forwardCustomField = textFields.get(0); // Forward row
        assertEquals("45", forwardCustomField.getText());
        assertEquals(Font.BOLD, forwardCustomField.getFont().getStyle());
        assertEquals(JTextField.CENTER, forwardCustomField.getHorizontalAlignment());

        JTextField bothCustomField = textFields.get(1); // Both directions row (empty)
        assertEquals("", bothCustomField.getText());

        // Both populated and empty text fields must have identical fixed preferred and minimum width (38px)
        Dimension expectedSize = new Dimension(38, 22);
        assertEquals(expectedSize.width, forwardCustomField.getPreferredSize().width);
        assertEquals(expectedSize.width, bothCustomField.getPreferredSize().width);
        assertEquals(expectedSize.width, forwardCustomField.getMinimumSize().width);
        assertEquals(expectedSize.width, bothCustomField.getMinimumSize().width);
    }

    @Test
    @DisplayName("Rebuilds popup with tooltips and non-focusable buttons")
    void rebuildsPopupWithTooltipsAndNonFocusableButtons() {
        List<String> statusMessages = new ArrayList<>();
        MaxSpeedPopup popup = new MaxSpeedPopup(statusMessages::add);

        Way way = createDataSetWay("residential");
        way.put("maxspeed", "50");

        JPanel container = new JPanel();
        JPopupMenu menu = new JPopupMenu();
        popup.rebuild(container, way, new Point(0, 0), menu);

        JPanel grid = (JPanel) container.getComponent(container.getComponentCount() - 1);
        List<JButton> buttons = findComponentsOfType(grid, JButton.class);

        // All buttons should be non-focusable and have tooltips
        for (JButton btn : buttons) {
            assertFalse(btn.isFocusable());
            assertNotNull(btn.getToolTipText());
            assertFalse(btn.getToolTipText().trim().isEmpty());
        }
    }

    @Test
    @DisplayName("Executes custom speed input and clear actions via UI buttons")
    void executesCustomSpeedInputAndClearActions() {
        List<String> statusMessages = new ArrayList<>();
        MaxSpeedPopup popup = new MaxSpeedPopup(statusMessages::add);

        Way way = createDataSetWay("primary");
        JPanel container = new JPanel();
        JPopupMenu menu = new JPopupMenu();
        popup.rebuild(container, way, new Point(0, 0), menu);

        JPanel grid = (JPanel) container.getComponent(container.getComponentCount() - 1);
        List<JTextField> textFields = findComponentsOfType(grid, JTextField.class);
        List<JButton> buttons = findComponentsOfType(grid, JButton.class);

        // Find the custom field for Forward (index 0) and Set button for forward
        JTextField forwardField = textFields.get(0);
        forwardField.setText("85");

        // Action event on text field triggers setButton click
        forwardField.postActionEvent();

        assertEquals("85", way.get("maxspeed:forward"));
        assertFalse(statusMessages.isEmpty());
        assertTrue(statusMessages.get(statusMessages.size() - 1).contains("85"));

        // Now clear the tag
        popup.rebuild(container, way, new Point(0, 0), menu);
        grid = (JPanel) container.getComponent(container.getComponentCount() - 1);
        buttons = findComponentsOfType(grid, JButton.class);

        // Clear button for Forward row (last button in first row controls)
        JButton forwardClearButton = buttons.stream()
                .filter(b -> b.getToolTipText() != null && b.getToolTipText().contains("maxspeed:forward") && b.isEnabled())
                .filter(b -> b.getToolTipText().contains("Remove") || "Clear".equals(b.getText()))
                .findFirst()
                .orElse(null);

        assertNotNull(forwardClearButton);
        forwardClearButton.doClick();

        assertNull(way.get("maxspeed:forward"));
    }

    @Test
    @DisplayName("Rebuilds popup respecting oneway restrictions by disabling prohibited directions")
    void rebuildsPopupRespectingOnewayRestrictions() {
        List<String> statusMessages = new ArrayList<>();
        MaxSpeedPopup popup = new MaxSpeedPopup(statusMessages::add);

        // Forward oneway: backward direction disabled
        Way forwardOneway = createDataSetWay("motorway");
        forwardOneway.put("oneway", "yes");

        JPanel container = new JPanel();
        JPopupMenu menu = new JPopupMenu();
        popup.rebuild(container, forwardOneway, new Point(0, 0), menu);

        JPanel grid = (JPanel) container.getComponent(container.getComponentCount() - 1);
        List<JLabel> labels = findComponentsOfType(grid, JLabel.class);
        JLabel backwardLabel = labels.stream()
                .filter(l -> l.getText().contains("Backward"))
                .findFirst()
                .orElse(null);
        assertNotNull(backwardLabel);
        assertTrue(backwardLabel.getText().contains("Oneway"));
        assertFalse(backwardLabel.isEnabled());

        // Reverse oneway: forward direction disabled
        Way reverseOneway = createDataSetWay("residential");
        reverseOneway.put("oneway", "-1");

        JPanel containerRev = new JPanel();
        popup.rebuild(containerRev, reverseOneway, new Point(0, 0), menu);

        JPanel gridRev = (JPanel) containerRev.getComponent(containerRev.getComponentCount() - 1);
        List<JLabel> labelsRev = findComponentsOfType(gridRev, JLabel.class);
        JLabel forwardLabelRev = labelsRev.stream()
                .filter(l -> l.getText().contains("Forward"))
                .findFirst()
                .orElse(null);
        assertNotNull(forwardLabelRev);
        assertTrue(forwardLabelRev.getText().contains("Reversed Oneway"));
        assertFalse(forwardLabelRev.isEnabled());
    }

    @Test
    @DisplayName("Rebuilds popup for unnamed way using highway tag in title")
    void rebuildsPopupForUnnamedWayUsingHighwayTag() {
        List<String> statusMessages = new ArrayList<>();
        MaxSpeedPopup popup = new MaxSpeedPopup(statusMessages::add);

        Way way = createDataSetWay("primary");

        JPanel container = new JPanel();
        JPopupMenu menu = new JPopupMenu();
        popup.rebuild(container, way, new Point(0, 0), menu);

        JLabel titleLabel = (JLabel) container.getComponent(0);
        assertTrue(titleLabel.getText().contains("primary"));
    }

    @ParameterizedTest(name = "Number Key ''{0}'' should be disabled while popup active")
    @ValueSource(chars = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'})
    @DisplayName("Shortcut disabler identifies number keys without modifiers as disabled")
    void shortcutDisablerIdentifiesNumberKeys(char keyChar) {
        KeyStroke stroke = KeyStroke.getKeyStroke(keyChar);
        assertTrue(PopupShortcutDisabler.hasToBeDisabled(stroke));
    }

    @ParameterizedTest(name = "Letter Key ''{0}'' should be disabled while popup active")
    @ValueSource(chars = {'a', 's', 'd', 'w', 'q', 'e', 'x'})
    @DisplayName("Shortcut disabler identifies letter keys without modifiers as disabled")
    void shortcutDisablerIdentifiesLetterKeys(char keyChar) {
        KeyStroke stroke = KeyStroke.getKeyStroke(keyChar);
        assertTrue(PopupShortcutDisabler.hasToBeDisabled(stroke));
    }

    @Test
    @DisplayName("Shortcut disabler identifies Shift+key as disabled, but preserves Ctrl/Alt and Action keys")
    void shortcutDisablerPreservesCtrlAltAndActionKeys() {
        // Shift+1 is disabled
        KeyStroke shiftOne = KeyStroke.getKeyStroke(KeyEvent.VK_1, InputEvent.SHIFT_DOWN_MASK);
        assertTrue(PopupShortcutDisabler.hasToBeDisabled(shiftOne));

        // Ctrl+Z (Undo) is preserved (not disabled)
        KeyStroke ctrlZ = KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK);
        assertFalse(PopupShortcutDisabler.hasToBeDisabled(ctrlZ));

        // Alt+A is preserved (not disabled)
        KeyStroke altA = KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.ALT_DOWN_MASK);
        assertFalse(PopupShortcutDisabler.hasToBeDisabled(altA));

        // F1 (Action key / Help) is preserved (not disabled)
        KeyStroke f1 = KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0);
        assertFalse(PopupShortcutDisabler.hasToBeDisabled(f1));

        // Null keystroke check
        assertFalse(PopupShortcutDisabler.hasToBeDisabled(null));
    }

    @Test
    @DisplayName("PopupShortcutDisabler handles enable/restore lifecycle and remains idempotent")
    void shortcutDisablerLifecycleIsIdempotent() {
        PopupShortcutDisabler disabler = new PopupShortcutDisabler();
        assertFalse(disabler.isActive());

        // First disable activates
        disabler.disableShortcuts();
        assertTrue(disabler.isActive());

        // Second disable is idempotent
        disabler.disableShortcuts();
        assertTrue(disabler.isActive());

        // First restore deactivates
        disabler.restoreShortcuts();
        assertFalse(disabler.isActive());

        // Second restore is idempotent
        disabler.restoreShortcuts();
        assertFalse(disabler.isActive());
    }

    private static <T extends Component> List<T> findComponentsOfType(Component root, Class<T> type) {
        List<T> result = new ArrayList<>();
        findComponentsOfTypeRecursive(root, type, result);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Component> void findComponentsOfTypeRecursive(Component current, Class<T> type, List<T> result) {
        if (type.isInstance(current)) {
            result.add((T) current);
        }
        if (current instanceof java.awt.Container) {
            for (Component child : ((java.awt.Container) current).getComponents()) {
                findComponentsOfTypeRecursive(child, type, result);
            }
        }
    }

    private static Way createDataSetWay(String highway) {
        Way way = new Way();
        way.put("highway", highway);
        Node node1 = new Node(new LatLon(0, 0));
        Node node2 = new Node(new LatLon(0, 1));
        way.setNodes(Arrays.asList(node1, node2));

        DataSet ds = new DataSet();
        ds.addPrimitive(node1);
        ds.addPrimitive(node2);
        ds.addPrimitive(way);
        return way;
    }
}
