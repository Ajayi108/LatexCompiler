package latexcompiler.ui;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicToggleButtonUI;
import java.awt.Color;
import java.awt.Insets;

final class UiButtons {
    private static final String CHANGE_LISTENER_KEY = "latex-compiler.buttonChangeListener";

    private UiButtons() {
    }

    static void style(AbstractButton button, UiTheme theme, Insets padding) {
        if (button instanceof JToggleButton) {
            button.setUI(new BasicToggleButtonUI());
        } else {
            button.setUI(new BasicButtonUI());
        }

        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(true);
        button.setFocusPainted(false);
        button.setRolloverEnabled(true);
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));

        Object oldListener = button.getClientProperty(CHANGE_LISTENER_KEY);
        if (oldListener instanceof ChangeListener listener) {
            button.getModel().removeChangeListener(listener);
        }

        ChangeListener listener = event -> applyState(button, theme, padding);
        button.getModel().addChangeListener(listener);
        button.putClientProperty(CHANGE_LISTENER_KEY, listener);
        applyState(button, theme, padding);
    }

    private static void applyState(AbstractButton button, UiTheme theme, Insets padding) {
        boolean selected = button instanceof JToggleButton toggleButton && toggleButton.isSelected();
        boolean pressed = button.getModel().isPressed() && button.getModel().isArmed();
        boolean rollover = button.getModel().isRollover();

        Color background = selected ? theme.listSelectionBackground() : theme.raisedBackground();
        Color foreground = selected ? theme.listSelectionForeground() : theme.text();
        Color border = theme.border();

        if (!button.isEnabled()) {
            background = blend(theme.raisedBackground(), theme.panelBackground(), 0.55);
            foreground = theme.disabledText();
        } else if (pressed) {
            background = blend(background, theme.text(), theme.darkMode() ? 0.22 : 0.13);
            border = theme.listSelectionBackground();
        } else if (rollover) {
            background = blend(background, theme.text(), theme.darkMode() ? 0.14 : 0.08);
            border = theme.listSelectionBackground();
        }

        button.setBackground(background);
        button.setForeground(foreground);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(border),
            BorderFactory.createEmptyBorder(padding.top, padding.left, padding.bottom, padding.right)
        ));

        SwingUtilities.invokeLater(button::repaint);
    }

    private static Color blend(Color base, Color overlay, double amount) {
        double keep = 1.0 - amount;
        return new Color(
            clamp(base.getRed() * keep + overlay.getRed() * amount),
            clamp(base.getGreen() * keep + overlay.getGreen() * amount),
            clamp(base.getBlue() * keep + overlay.getBlue() * amount)
        );
    }

    private static int clamp(double value) {
        return Math.max(0, Math.min(255, (int) Math.round(value)));
    }
}
