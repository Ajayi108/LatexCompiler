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
    private static final Color BACKGROUND = new Color(22, 27, 34);
    private static final Color FOREGROUND = new Color(230, 237, 243);
    private static final Color COMMENT = new Color(139, 148, 158);
    private static final Color COMMAND = new Color(255, 123, 114);
    private static final Color KEY_COMMAND = new Color(210, 168, 255);
    private static final Color ENVIRONMENT = new Color(255, 166, 87);
    private static final Color MATH = new Color(121, 192, 255);
    private static final Color BRACKET = new Color(126, 231, 135);

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
    private final SimpleAttributeSet baseStyle;
    private final SimpleAttributeSet commentStyle;
    private final SimpleAttributeSet commandStyle;
    private final SimpleAttributeSet keyCommandStyle;
    private final SimpleAttributeSet environmentStyle;
    private final SimpleAttributeSet mathStyle;
    private final SimpleAttributeSet bracketStyle;

    private boolean highlighting;

    public LatexSyntaxHighlighter(JTextPane editor) {
        this.editor = editor;
        this.document = editor.getStyledDocument();
        this.baseStyle = style(FOREGROUND, false, false);
        this.commentStyle = style(COMMENT, false, true);
        this.commandStyle = style(COMMAND, false, false);
        this.keyCommandStyle = style(KEY_COMMAND, true, false);
        this.environmentStyle = style(ENVIRONMENT, true, false);
        this.mathStyle = style(MATH, false, false);
        this.bracketStyle = style(BRACKET, false, false);
        this.timer = new Timer(HIGHLIGHT_DELAY_MS, event -> refreshNow());
        this.timer.setRepeats(false);

        editor.setBackground(BACKGROUND);
        editor.setForeground(FOREGROUND);
        editor.setCaretColor(FOREGROUND);
        editor.setSelectionColor(new Color(56, 139, 253, 100));
        editor.setSelectedTextColor(Color.WHITE);
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

