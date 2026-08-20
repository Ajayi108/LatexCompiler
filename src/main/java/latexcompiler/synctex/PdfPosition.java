package latexcompiler.synctex;

import java.nio.file.Path;

public record PdfPosition(Path sourceFile, int line, int page, double normalizedX, double normalizedY, double confidence) {
}
