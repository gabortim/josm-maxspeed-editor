package com.github.gabortim;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.tools.Pair;
import org.openstreetmap.josm.tools.Shortcut;

import javax.swing.Action;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Disables single-key and Shift+key JOSM action shortcuts and menu items while a popup is visible,
 * restoring them upon popup dismissal. This is a workaround for JOSM intercepting keystrokes inside popups.
 */
class PopupShortcutDisabler {
    private final List<Pair<Action, Shortcut>> unregisteredActionShortcuts = new ArrayList<>();
    private final Set<JosmAction> disabledMenuActions = new HashSet<>();
    private boolean active = false;

    void disableShortcuts() {
        if (active) return;
        active = true;
        try {
            disabledMenuActions.clear();
            if (MainApplication.getMenu() != null) {
                for (int i = 0; i < MainApplication.getMenu().getMenuCount(); i++) {
                    JMenu menu = MainApplication.getMenu().getMenu(i);
                    if (menu != null) {
                        for (int j = 0; j < menu.getItemCount(); j++) {
                            JMenuItem item = menu.getItem(j);
                            if (item != null) {
                                Action action = item.getAction();
                                if (action instanceof JosmAction && action.isEnabled()) {
                                    Shortcut shortcut = ((JosmAction) action).getShortcut();
                                    if (shortcut != null && hasToBeDisabled(shortcut.getKeyStroke())) {
                                        action.setEnabled(false);
                                        disabledMenuActions.add((JosmAction) action);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            unregisteredActionShortcuts.clear();
            for (Shortcut shortcut : Shortcut.listAll()) {
                KeyStroke ks = shortcut.getKeyStroke();
                if (hasToBeDisabled(ks)) {
                    Action action = MainApplication.getRegisteredActionShortcut(shortcut);
                    if (action != null) {
                        MainApplication.unregisterActionShortcut(action, shortcut);
                        unregisteredActionShortcuts.add(new Pair<>(action, shortcut));
                    }
                }
            }
        } catch (Exception ignored) {
            // Ignore in headless test setups where full MainApplication UI is absent
        }
    }

    void restoreShortcuts() {
        if (!active) return;
        active = false;
        try {
            for (Pair<Action, Shortcut> p : unregisteredActionShortcuts) {
                MainApplication.registerActionShortcut(p.a, p.b);
            }
            unregisteredActionShortcuts.clear();

            for (JosmAction a : disabledMenuActions) {
                a.setEnabled(true);
            }
            disabledMenuActions.clear();
        } catch (Exception ignored) {
            // Ignore
        }
    }

    boolean isActive() {
        return active;
    }

    List<Pair<Action, Shortcut>> getUnregisteredActionShortcuts() {
        return unregisteredActionShortcuts;
    }

    Set<JosmAction> getDisabledMenuActions() {
        return disabledMenuActions;
    }

    static boolean hasToBeDisabled(KeyStroke ks) {
        return ks != null && (ks.getModifiers() == 0 || isOnlyShift(ks.getModifiers()))
                && !new KeyEvent(new JLabel(), KeyEvent.KEY_PRESSED, 0, ks.getModifiers(), ks.getKeyCode(), ks.getKeyChar()).isActionKey();
    }

    static boolean isOnlyShift(int modifiers) {
        return (modifiers & InputEvent.SHIFT_DOWN_MASK) != 0
                && (modifiers & InputEvent.CTRL_DOWN_MASK) == 0
                && (modifiers & InputEvent.ALT_DOWN_MASK) == 0
                && (modifiers & InputEvent.ALT_GRAPH_DOWN_MASK) == 0
                && (modifiers & InputEvent.META_DOWN_MASK) == 0;
    }
}
