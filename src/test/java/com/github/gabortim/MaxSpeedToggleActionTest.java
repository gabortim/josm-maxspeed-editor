package com.github.gabortim;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.spi.preferences.Config;

import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link MaxSpeedToggleAction}.
 */
class MaxSpeedToggleActionTest {

    @BeforeAll
    static void initializeJosmPreferences() {
        Preferences pref = new Preferences();
        pref.enableSaveOnPut(false);
        Config.setPreferencesInstance(pref);
        try {
            java.lang.reflect.Field field = org.openstreetmap.josm.gui.MainApplication.class.getDeclaredField("contentPanePrivate");
            field.setAccessible(true);
            if (field.get(null) == null) {
                field.set(null, new javax.swing.JPanel());
            }
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("Validates constructor preconditions")
    void validatesConstructorArguments() {
        assertThrows(NullPointerException.class, () -> new MaxSpeedToggleAction(true, null));
    }

    @Test
    @DisplayName("Toggling action updates selection, preference, and invokes callback")
    void togglingActionUpdatesStateAndCallback() {
        AtomicBoolean state = new AtomicBoolean(true);
        MaxSpeedToggleAction action = new MaxSpeedToggleAction(true, state::set);

        assertTrue(action.isSelected());
        assertEquals("maxspeed-editor-toggle", MaxSpeedToggleAction.TOOLBAR_ID);

        // Simulate user clicking toggle action
        action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "toggle"));

        assertFalse(action.isSelected());
        assertFalse(state.get());
        assertFalse(Config.getPref().getBoolean(MaxSpeedEditorPlugin.PREF_ENABLED, true));

        // Toggle back to true
        action.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "toggle"));

        assertTrue(action.isSelected());
        assertTrue(state.get());
        assertTrue(Config.getPref().getBoolean(MaxSpeedEditorPlugin.PREF_ENABLED, false));
    }

    @Test
    @DisplayName("Adds toolbar item with separator to existing toolbar list")
    void addsToolbarItemWithSeparator() {
        List<String> items = Arrays.asList("open", "save");
        List<String> updated = MaxSpeedEditorPlugin.addToolbarItemWithSeparator(items);
        assertEquals(Arrays.asList("open", "save", "|", "maxspeed-editor-toggle"), updated);
    }

    @Test
    @DisplayName("Does not duplicate separator if toolbar list already ends with one")
    void doesNotDuplicateSeparatorWhenAlreadyPresent() {
        List<String> items = Arrays.asList("open", "|");
        List<String> updated = MaxSpeedEditorPlugin.addToolbarItemWithSeparator(items);
        assertEquals(Arrays.asList("open", "|", "maxspeed-editor-toggle"), updated);
    }

    @Test
    @DisplayName("Adds separator and item when toolbar list is empty or null")
    void handlesEmptyAndNullToolbarLists() {
        List<String> empty = Collections.emptyList();
        List<String> updatedEmpty = MaxSpeedEditorPlugin.addToolbarItemWithSeparator(empty);
        assertEquals(Arrays.asList("|", "maxspeed-editor-toggle"), updatedEmpty);

        List<String> updatedNull = MaxSpeedEditorPlugin.addToolbarItemWithSeparator(null);
        assertEquals(Arrays.asList("|", "maxspeed-editor-toggle"), updatedNull);
    }

    @Test
    @DisplayName("Does not modify toolbar list if item is already present")
    void doesNotAddDuplicateIfAlreadyInToolbar() {
        List<String> items = Arrays.asList("open", "|", "maxspeed-editor-toggle");
        List<String> updated = MaxSpeedEditorPlugin.addToolbarItemWithSeparator(items);
        assertEquals(Arrays.asList("open", "|", "maxspeed-editor-toggle"), updated);
    }
}
