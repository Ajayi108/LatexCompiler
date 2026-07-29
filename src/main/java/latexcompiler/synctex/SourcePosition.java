package latexcompiler.synctex;

import java.nio.file.Path;

public record SourcePosition(Path sourceFile, int line, int page, double confidence) {
}
