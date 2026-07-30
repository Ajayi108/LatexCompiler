package latexcompiler.ui;

import java.awt.Color;

public record UiTheme(
    String id,
    String label,
    boolean darkMode,
    Color windowBackground,
    Color panelBackground,
    Color raisedBackground,
    Color border,
    Color text,
    Color mutedText,
    Color disabledText,
    Color editorBackground,
    Color editorForeground,
    Color editorCaret,
    Color editorSelection,
    Color editorSelectedText,
    Color gutterBackground,
    Color gutterForeground,
    Color gutterCurrentLine,
    Color gutterCurrentText,
    Color listSelectionBackground,
    Color listSelectionForeground,
    Color missingText,
    Color pdfBackground,
    Color pageShadow,
    Color messageText,
    Color syntaxComment,
    Color syntaxCommand,
    Color syntaxKeyCommand,
    Color syntaxEnvironment,
    Color syntaxMath,
    Color syntaxBracket
) {
    public static UiTheme light() {
        return new UiTheme(
            "light",
            "Light",
            false,
            new Color(246, 248, 250),
            new Color(255, 255, 255),
            new Color(242, 244, 247),
            new Color(210, 215, 222),
            new Color(31, 35, 40),
            new Color(101, 109, 118),
            new Color(110, 119, 129),
            new Color(255, 255, 255),
            new Color(31, 35, 40),
            new Color(31, 35, 40),
            new Color(9, 105, 218, 70),
            Color.WHITE,
            new Color(246, 248, 250),
            new Color(101, 109, 118),
            new Color(234, 238, 242),
            new Color(31, 35, 40),
            new Color(9, 105, 218),
            Color.WHITE,
            new Color(207, 34, 46),
            new Color(232, 235, 239),
            new Color(0, 0, 0, 36),
            new Color(101, 109, 118),
            new Color(110, 119, 129),
            new Color(130, 80, 223),
            new Color(9, 105, 218),
            new Color(188, 76, 0),
            new Color(31, 136, 61),
            new Color(9, 105, 218)
        );
    }

    public static UiTheme dark() {
        return new UiTheme(
            "dark",
            "Dark",
            true,
            new Color(13, 17, 23),
            new Color(22, 27, 34),
            new Color(33, 38, 45),
            new Color(48, 54, 61),
            new Color(230, 237, 243),
            new Color(139, 148, 158),
            new Color(125, 133, 144),
            new Color(22, 27, 34),
            new Color(230, 237, 243),
            new Color(230, 237, 243),
            new Color(56, 139, 253, 100),
            Color.WHITE,
            new Color(13, 17, 23),
            new Color(125, 133, 144),
            new Color(33, 38, 45),
            new Color(230, 237, 243),
            new Color(38, 79, 120),
            Color.WHITE,
            new Color(255, 123, 114),
            new Color(13, 17, 23),
            new Color(0, 0, 0, 52),
            new Color(139, 148, 158),
            new Color(139, 148, 158),
            new Color(255, 123, 114),
            new Color(210, 168, 255),
            new Color(255, 166, 87),
            new Color(121, 192, 255),
            new Color(126, 231, 135)
        );
    }

    public static UiTheme byId(String id) {
        return "dark".equalsIgnoreCase(id) ? dark() : light();
    }
}
