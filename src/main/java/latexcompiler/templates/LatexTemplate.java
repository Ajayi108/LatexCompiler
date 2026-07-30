package latexcompiler.templates;

import java.nio.file.Path;

public record LatexTemplate(
    String name,
    String category,
    String description,
    String preamble,
    String body,
    boolean custom,
    Path sourcePath
) {
    public LatexTemplate(
        String name,
        String category,
        String description,
        String preamble,
        String body,
        boolean custom
    ) {
        this(name, category, description, preamble, body, custom, null);
    }
}
