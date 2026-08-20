package latexcompiler.ui;

import javax.swing.JTextPane;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BoxView;
import javax.swing.text.ComponentView;
import javax.swing.text.Element;
import javax.swing.text.IconView;
import javax.swing.text.LabelView;
import javax.swing.text.ParagraphView;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;

public final class CodeEditorPane extends JTextPane {
    public CodeEditorPane() {
        setEditorKit(new WordWrapEditorKit());
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        // Keep source paragraphs readable by soft-wrapping them to the editor width.
        return true;
    }

    private static final class WordWrapEditorKit extends StyledEditorKit {
        private final ViewFactory viewFactory = new WordWrapViewFactory();

        @Override
        public ViewFactory getViewFactory() {
            return viewFactory;
        }
    }

    private static final class WordWrapViewFactory implements ViewFactory {
        @Override
        public View create(Element element) {
            String kind = element.getName();
            if (kind != null) {
                return switch (kind) {
                    case AbstractDocument.ContentElementName -> new WordWrapLabelView(element);
                    case AbstractDocument.ParagraphElementName -> new ParagraphView(element);
                    case AbstractDocument.SectionElementName -> new BoxView(element, View.Y_AXIS);
                    case StyleConstants.ComponentElementName -> new ComponentView(element);
                    case StyleConstants.IconElementName -> new IconView(element);
                    default -> new LabelView(element);
                };
            }
            return new LabelView(element);
        }
    }

    private static final class WordWrapLabelView extends LabelView {
        private WordWrapLabelView(Element element) {
            super(element);
        }

        @Override
        public float getMinimumSpan(int axis) {
            if (axis == View.X_AXIS) {
                // Let ParagraphView wrap at natural word breaks instead of clipping at the right edge.
                return 0;
            }
            return super.getMinimumSpan(axis);
        }
    }
}
