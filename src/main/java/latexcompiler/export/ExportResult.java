package latexcompiler.export;

import java.nio.file.Path;

public record ExportResult(boolean success, Path outputFile, String log) {
}

