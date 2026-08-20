package latexcompiler.export;

import java.nio.file.Path;

public record ExportResult(boolean success, Path outputFile, String log, int lineOffset) {
    public ExportResult(boolean success, Path outputFile, String log) {
        this(success, outputFile, log, 0);
    }
}
