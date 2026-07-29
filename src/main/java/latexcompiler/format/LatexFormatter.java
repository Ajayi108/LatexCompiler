package latexcompiler.format;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LatexFormatter {
    private static final String INDENT = "  ";
    private static final Pattern BEGIN_ENVIRONMENT = Pattern.compile("\\\\begin\\s*\\{([^}]+)}");
    private static final Pattern END_ENVIRONMENT = Pattern.compile("\\\\end\\s*\\{([^}]+)}");
    private static final Pattern LEADING_END_ENVIRONMENT = Pattern.compile("^\\\\end\\s*\\{");
    private static final Pattern CONDITIONAL_START = Pattern.compile("\\\\if[a-zA-Z@]*\\b");
    private static final Pattern CONDITIONAL_END = Pattern.compile("\\\\fi\\b");
    private static final Pattern LEADING_CONDITIONAL_END = Pattern.compile("^\\\\fi\\b");
    private static final Pattern LEADING_CONDITIONAL_MIDDLE = Pattern.compile("^\\\\else\\b|^\\\\or\\b");
    private static final Set<String> RAW_ENVIRONMENTS = Set.of(
        "verbatim",
        "Verbatim",
        "lstlisting",
        "minted",
        "filecontents",
        "filecontents*"
    );

    public String format(String source) {
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        boolean hadTrailingNewline = normalized.endsWith("\n");
        String[] lines = normalized.split("\n", -1);
        StringBuilder formatted = new StringBuilder(normalized.length() + 64);
        int indentLevel = 0;
        boolean inRawEnvironment = false;

        for (int index = 0; index < lines.length; index++) {
            if (index == lines.length - 1 && lines[index].isEmpty() && hadTrailingNewline) {
                break;
            }

            String originalLine = trimTrailingWhitespace(lines[index]);
            String trimmedLine = originalLine.stripLeading();

            if (trimmedLine.isEmpty()) {
                appendLine(formatted, "");
                continue;
            }

            if (inRawEnvironment) {
                appendLine(formatted, originalLine);
                if (endsRawEnvironment(trimmedLine)) {
                    indentLevel = Math.max(0, indentLevel - 1);
                    inRawEnvironment = false;
                }
                continue;
            }

            String structuralLine = stripInlineComment(trimmedLine);
            int lineIndent = lineStartsBlockClose(structuralLine)
                ? Math.max(0, indentLevel - 1)
                : indentLevel;

            appendLine(formatted, INDENT.repeat(lineIndent) + trimmedLine);

            indentLevel = Math.max(0, indentLevel + countBegins(structuralLine) - countEnds(structuralLine)
                + countConditionals(structuralLine) - countConditionalEnds(structuralLine));

            if (startsRawEnvironment(structuralLine) && !endsRawEnvironment(structuralLine)) {
                inRawEnvironment = true;
            }
        }

        return formatted.toString();
    }

    private boolean lineStartsBlockClose(String line) {
        return LEADING_END_ENVIRONMENT.matcher(line).find()
            || LEADING_CONDITIONAL_END.matcher(line).find()
            || LEADING_CONDITIONAL_MIDDLE.matcher(line).find();
    }

    private int countBegins(String line) {
        return countEnvironmentMatches(BEGIN_ENVIRONMENT, line);
    }

    private int countEnds(String line) {
        return countEnvironmentMatches(END_ENVIRONMENT, line);
    }

    private int countEnvironmentMatches(Pattern pattern, String line) {
        int count = 0;
        Matcher matcher = pattern.matcher(line);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private int countConditionals(String line) {
        int count = 0;
        Matcher matcher = CONDITIONAL_START.matcher(line);
        while (matcher.find()) {
            String match = matcher.group();
            if (!"\\fi".equals(match)) {
                count++;
            }
        }
        return count;
    }

    private int countConditionalEnds(String line) {
        int count = 0;
        Matcher matcher = CONDITIONAL_END.matcher(line);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private boolean startsRawEnvironment(String line) {
        Matcher matcher = BEGIN_ENVIRONMENT.matcher(line);
        return matcher.find() && RAW_ENVIRONMENTS.contains(matcher.group(1));
    }

    private boolean endsRawEnvironment(String line) {
        Matcher matcher = END_ENVIRONMENT.matcher(line);
        return matcher.find() && RAW_ENVIRONMENTS.contains(matcher.group(1));
    }

    private String stripInlineComment(String line) {
        for (int index = 0; index < line.length(); index++) {
            if (line.charAt(index) == '%' && !isEscaped(line, index)) {
                return line.substring(0, index);
            }
        }
        return line;
    }

    private boolean isEscaped(String line, int index) {
        int slashCount = 0;
        for (int cursor = index - 1; cursor >= 0 && line.charAt(cursor) == '\\'; cursor--) {
            slashCount++;
        }
        return slashCount % 2 == 1;
    }

    private String trimTrailingWhitespace(String line) {
        int end = line.length();
        while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) {
            end--;
        }
        return line.substring(0, end);
    }

    private void appendLine(StringBuilder builder, String line) {
        builder.append(line).append(System.lineSeparator());
    }
}
