package latexcompiler.ui;

import javax.swing.JTextPane;
import java.awt.Dimension;

public final class CodeEditorPane extends JTextPane {
    @Override
    public boolean getScrollableTracksViewportWidth() {
        Dimension preferred = getUI().getPreferredSize(this);
        return preferred.width <= getParent().getSize().width;
    }
}

