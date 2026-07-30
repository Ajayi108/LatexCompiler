package latexcompiler.ui;

import javax.swing.JTextPane;
import javax.swing.Timer;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.awt.Font;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LatexSyntaxHighlighter {
    private static final int HIGHLIGHT_DELAY_MS = 120;

    private static final Pattern COMMENT_PATTERN = Pattern.compile("(?m)(?<!\\\\)%.*$");
    private static final Pattern COMMAND_PATTERN = Pattern.compile("\\\\(?:[a-zA-Z@]+\\*?|.)");
    private static final Pattern KEY_COMMAND_PATTERN = Pattern.compile(
        "\\\\(?:documentclass|usepackage|begin|end|input|include|title|author|date|maketitle|section|subsection|subsubsection|paragraph|textbf|textit|emph|href|url|item|label|ref|cite)\\*?"
    );
    private static final Pattern ENVIRONMENT_PATTERN = Pattern.compile("\\\\(?:begin|end)\\s*\\{([^}]+)}");
    private static final Pattern MATH_PATTERN = Pattern.compile("\\$[^$\\n]+\\$|\\\\\\([^\\n]*?\\\\\\)|\\\\\\[[\\s\\S]*?\\\\\\]");
    private static final Pattern BRACKET_PATTERN = Pattern.compile("[{}\\[\\]]");

    private final JTextPane editor;
    private final StyledDocument document;
    private final Timer timer;
    private SimpleAttributeSet baseStyle;
    private SimpleAttributeSet commentStyle;
    private SimpleAttributeSet commandStyle;
    private SimpleAttributeSet keyCommandStyle;
    private SimpleAttributeSet environmentStyle;
    private SimpleAttributeSet mathStyle;
    private SimpleAttributeSet bracketStyle;

    private boolean highlighting;

    public LatexSyntaxHighlighter(JTextPane editor, UiTheme theme) {
        this.editor = editor;
        this.document = editor.getStyledDocument();
        this.timer = new Timer(HIGHLIGHT_DELAY_MS, event -> refreshNow());
        this.timer.setRepeats(false);

        applyTheme(theme);
    }

    public void schedule() {
        timer.restart();
    }

    public void refreshNow() {
        timer.stop();
        highlighting = true;
        try {
            String text = document.getText(0, document.getLength());
            document.setCharacterAttributes(0, text.length(), baseStyle, true);

            applyPattern(text, MATH_PATTERN, mathStyle);
            applyPattern(text, COMMAND_PATTERN, commandStyle);
            applyPattern(text, KEY_COMMAND_PATTERN, keyCommandStyle);
            applyGroup(text, ENVIRONMENT_PATTERN, 1, environmentStyle);
            applyPattern(text, BRACKET_PATTERN, bracketStyle);
            applyPattern(text, COMMENT_PATTERN, commentStyle);
        } catch (BadLocationException ignored) {
            // If the document changes while highlighting, the next edit will schedule another pass.
        } finally {
            highlighting = false;
        }
    }

    public boolean isHighlighting() {
        return highlighting;
    }

    public void applyTheme(UiTheme theme) {
        baseStyle = style(theme.editorForeground(), false, false);
        commentStyle = style(theme.syntaxComment(), false, true);
        commandStyle = style(theme.syntaxCommand(), false, false);
        keyCommandStyle = style(theme.syntaxKeyCommand(), true, false);
        environmentStyle = style(theme.syntaxEnvironment(), true, false);
        mathStyle = style(theme.syntaxMath(), false, false);
        bracketStyle = style(theme.syntaxBracket(), false, false);

        editor.setBackground(theme.editorBackground());
        editor.setForeground(theme.editorForeground());
        editor.setCaretColor(theme.editorCaret());
        editor.setSelectionColor(theme.editorSelection());
        editor.setSelectedTextColor(theme.editorSelectedText());
        refreshNow();
    }

    private void applyPattern(String text, Pattern pattern, SimpleAttributeSet style) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            document.setCharacterAttributes(matcher.start(), matcher.end() - matcher.start(), style, true);
        }
    }

    private void applyGroup(String text, Pattern pattern, int group, SimpleAttributeSet style) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            document.setCharacterAttributes(matcher.start(group), matcher.end(group) - matcher.start(group), style, true);
        }
    }

    private SimpleAttributeSet style(Color foreground, boolean bold, boolean italic) {
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setForeground(attributes, foreground);
        StyleConstants.setBold(attributes, bold);
        StyleConstants.setItalic(attributes, italic);
        StyleConstants.setFontFamily(attributes, Font.MONOSPACED);
        StyleConstants.setFontSize(attributes, 15);
        return attributes;
    }
}
