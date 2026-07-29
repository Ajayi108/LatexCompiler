package latexcompiler.ui;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.Rectangle2D;

public final class LineNumberView extends JComponent implements DocumentListener, CaretListener, ChangeListener {
    private static final int HORIZONTAL_PADDING = 10;

    private final JTextComponent editor;
    private int currentLine;

    public LineNumberView(JTextComponent editor) {
        this.editor = editor;
        this.currentLine = 0;
        this.editor.getDocument().addDocumentListener(this);
        this.editor.addCaretListener(this);
        setFont(gutterFont(editor));
        setForeground(new Color(125, 133, 144));
        setBackground(new Color(13, 17, 23));
    }

    @Override
    public void addNotify() {
        super.addNotify();
        JScrollPane scrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
        if (scrollPane != null) {
            scrollPane.getViewport().addChangeListener(this);
        }
    }

    @Override
    public Dimension getPreferredSize() {
        int lineCount = editor.getDocument().getDefaultRootElement().getElementCount();
        int digits = Math.max(2, String.valueOf(lineCount).length());
        FontMetrics metrics = getFontMetrics(getFont());
        int height = Math.max(editor.getPreferredSize().height, editor.getHeight());
        return new Dimension(HORIZONTAL_PADDING * 2 + metrics.charWidth('0') * digits, height);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D graphics2D = (Graphics2D) graphics.create();
        try {
            graphics2D.setColor(getBackground());
            graphics2D.fillRect(0, graphics.getClipBounds().y, getWidth(), graphics.getClipBounds().height);
            graphics2D.setFont(getFont());

            Element root = editor.getDocument().getDefaultRootElement();
            int startOffset = editor.viewToModel2D(new Point(0, graphics.getClipBounds().y));
            int endOffset = editor.viewToModel2D(new Point(0, graphics.getClipBounds().y + graphics.getClipBounds().height));
            int startLine = root.getElementIndex(startOffset);
            int endLine = root.getElementIndex(endOffset);
            FontMetrics metrics = graphics2D.getFontMetrics();

            for (int line = startLine; line <= endLine; line++) {
                paintLineNumber(graphics2D, root, metrics, line);
            }
        } finally {
            graphics2D.dispose();
        }
    }

    private void paintLineNumber(Graphics2D graphics, Element root, FontMetrics metrics, int line) {
        try {
            Rectangle2D lineBounds = editor.modelToView2D(root.getElement(line).getStartOffset());
            if (lineBounds == null) {
                return;
            }

            if (line == currentLine) {
                graphics.setColor(new Color(33, 38, 45));
                graphics.fillRect(0, (int) lineBounds.getY(), getWidth(), (int) Math.ceil(lineBounds.getHeight()));
            }

            String number = String.valueOf(line + 1);
            int x = getWidth() - HORIZONTAL_PADDING - metrics.stringWidth(number);
            int y = (int) Math.round(lineBounds.getY()
                + (lineBounds.getHeight() - metrics.getHeight()) / 2.0
                + metrics.getAscent());
            graphics.setColor(line == currentLine ? new Color(230, 237, 243) : getForeground());
            graphics.drawString(number, x, y);
        } catch (BadLocationException ignored) {
            // The editor can change during painting; the next repaint will correct the gutter.
        }
    }

    @Override
    public void insertUpdate(DocumentEvent event) {
        refresh();
    }

    @Override
    public void removeUpdate(DocumentEvent event) {
        refresh();
    }

    @Override
    public void changedUpdate(DocumentEvent event) {
        refresh();
    }

    @Override
    public void caretUpdate(CaretEvent event) {
        currentLine = editor.getDocument().getDefaultRootElement().getElementIndex(event.getDot());
        repaint();
    }

    @Override
    public void stateChanged(ChangeEvent event) {
        repaint();
    }

    private void refresh() {
        revalidate();
        repaint();
    }

    private Font gutterFont(JTextComponent editor) {
        Font font = editor.getFont();
        if (font == null) {
            font = UIManager.getFont("TextPane.font");
        }
        if (font == null) {
            font = new Font(Font.MONOSPACED, Font.PLAIN, 13);
        }
        return font.deriveFont(Font.PLAIN, 13f);
    }
}
