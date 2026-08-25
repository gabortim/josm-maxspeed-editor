package com.github.gabortim;

import org.openstreetmap.josm.actions.ToggleAction;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Shortcut;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Objects;
import java.util.function.Consumer;

import static org.openstreetmap.josm.tools.I18n.tr;

/**
 * Toggle action for enabling and disabling the MaxSpeed Editor popup.
 * <p>
 * Extends JOSM's {@link ToggleAction} to provide synchronized checkmark rendering
 * in the Tools menu and toolbar toggle button state.
 */
public class MaxSpeedToggleAction extends ToggleAction {

    /**
     * Action toolbar identifier for toolbar registration and persistence.
     */
    public static final String TOOLBAR_ID = "maxspeed-editor-toggle";

    private final Consumer<Boolean> onToggleCallback;

    /**
     * Constructs the toggle action with the given initial state and toggle callback.
     *
     * @param initialState     initial enabled/selected state
     * @param onToggleCallback callback invoked when the toggle state changes
     */
    public MaxSpeedToggleAction(boolean initialState, Consumer<Boolean> onToggleCallback) {
        super(
                tr("MaxSpeed Editor"),
                new ImageProvider("maxspeed_editor.svg"),
                tr("Toggle MaxSpeed Editor popup on highway click"),
                Shortcut.registerShortcut(
                        "menu:maxspeed:toggle",
                        tr("Toggle MaxSpeed Editor"),
                        KeyEvent.VK_M,
                        Shortcut.ALT_SHIFT
                ),
                true,
                TOOLBAR_ID,
                false
        );
        this.onToggleCallback = Objects.requireNonNull(onToggleCallback, "onToggleCallback must not be null");
        putValue(SMALL_ICON, ImageProvider.getIfAvailable("dialogs", "search"));
        setSelected(initialState);
        notifySelectedState();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        toggleSelectedState(e);
        boolean enabled = isSelected();
        Config.getPref().putBoolean(MaxSpeedEditorPlugin.PREF_ENABLED, enabled);
        notifySelectedState();

        Logging.debug("MaxSpeedEditor: toggle state changed, pluginEnabled=" + enabled);
        onToggleCallback.accept(enabled);
    }
}
