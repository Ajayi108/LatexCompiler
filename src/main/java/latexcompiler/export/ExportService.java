package latexcompiler.export;

import latexcompiler.process.ProcessResult;
import latexcompiler.process.ProcessRunner;
import latexcompiler.synctex.SyncTexService;
import latexcompiler.tools.ToolManager;
import latexcompiler.tools.ToolType;

import javax.swing.JFrame;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

public final class ExportService {
    private static final String TECTONIC_PDFTEX_COMPATIBILITY = """
        % LaTeX Compiler compatibility layer for Tectonic's XeTeX engine.
        \\ifx\\pdfglyphtounicode\\undefined
          \\def\\pdfglyphtounicode#1#2{}%
        \\fi
        \\ifx\\pdfgentounicode\\undefined
          \\newcount\\pdfgentounicode
        \\fi
        
        """;

    private final ToolManager toolManager;
    private final ProcessRunner processRunner;

    public ExportService(ToolManager toolManager, ProcessRunner processRunner) {
        this.toolManager = toolManager;
        this.processRunner = processRunner;
    }

    public ExportResult export(JFrame owner, Path sourceFile, Path targetFile, ExportFormat format, Consumer<String> logger)
        throws IOException, InterruptedException {
        Files.createDirectories(targetFile.toAbsolutePath().getParent());

        return switch (format) {
            case PDF -> exportPdf(owner, sourceFile, targetFile, logger);
            case WORD, POWERPOINT -> exportWithPandoc(owner, sourceFile, targetFile, format, logger);
        };
    }

    private ExportResult exportPdf(JFrame owner, Path sourceFile, Path targetFile, Consumer<String> logger)
        throws IOException, InterruptedException {
        // Tectonic handles LaTeX-to-PDF without requiring users to install a full TeX distribution.
        Path tectonic = toolManager.ensureTool(owner, ToolType.TECTONIC, logger);
        if (tectonic == null) {
            return new ExportResult(false, targetFile, "PDF export cancelled because Tectonic is not available.");
        }

        // Tectonic writes several build artifacts, so compile in a temp folder and copy out the PDF.
        Path sourceDirectory = sourceFile.toAbsolutePath().getParent();
        Path workDir = Files.createTempDirectory("latex-compiler-pdf-");
        PreparedSource preparedSource = prepareTectonicSource(sourceFile, workDir, logger);
        Path compileSource = preparedSource.compileSource();
        List<String> command = new ArrayList<>();
        command.add(tectonic.toString());
        command.add("--keep-logs");
        command.add("--synctex");
        // A patched temp source still needs to find files beside the original document.
        command.add("-Z");
        command.add("search-path=" + sourceDirectory);
        command.add("--outdir");
        command.add(workDir.toString());
        command.add(compileSource.toAbsolutePath().toString());

        logger.accept("Running Tectonic...");
        ProcessResult result = processRunner.run(command, sourceDirectory);
        String baseName = stripExtension(compileSource.getFileName().toString());
        Path generatedPdf = workDir.resolve(baseName + ".pdf");

        if (result.exitCode() == 0 && Files.exists(generatedPdf)) {
            Files.copy(generatedPdf, targetFile, StandardCopyOption.REPLACE_EXISTING);
            copySyncTexArtifacts(sourceFile, targetFile, workDir.resolve(baseName + ".synctex.gz"), preparedSource, logger);
            return new ExportResult(true, targetFile, result.output());
        }

        return new ExportResult(false, targetFile, result.output());
    }

    private PreparedSource prepareTectonicSource(Path sourceFile, Path workDir, Consumer<String> logger) throws IOException {
        String source = Files.readString(sourceFile, StandardCharsets.UTF_8);
        if (!needsPdfTexCompatibility(source)) {
            return new PreparedSource(sourceFile, 0);
        }

        logger.accept("Applying pdfLaTeX compatibility for Tectonic...");
        Path compileSource = workDir.resolve(sourceFile.getFileName().toString());
        Files.writeString(compileSource, TECTONIC_PDFTEX_COMPATIBILITY + source, StandardCharsets.UTF_8);
        return new PreparedSource(compileSource, lineOffset(TECTONIC_PDFTEX_COMPATIBILITY));
    }

    private boolean needsPdfTexCompatibility(String source) {
        return source.contains("\\input{glyphtounicode}")
            || source.contains("\\pdfgentounicode")
            || source.contains("\\pdfglyphtounicode");
    }

    private void copySyncTexArtifacts(
        Path sourceFile,
        Path targetFile,
        Path generatedSyncTex,
        PreparedSource preparedSource,
        Consumer<String> logger
    ) throws IOException {
        Path targetSyncTex = SyncTexService.syncTexPathForPdf(targetFile);
        Path targetMetadata = SyncTexService.metadataPathForPdf(targetFile);

        if (!Files.isRegularFile(generatedSyncTex)) {
            Files.deleteIfExists(targetSyncTex);
            Files.deleteIfExists(targetMetadata);
            logger.accept("SyncTeX data was not generated for this PDF.");
            return;
        }

        Files.copy(generatedSyncTex, targetSyncTex, StandardCopyOption.REPLACE_EXISTING);
        writeSyncMetadata(sourceFile, preparedSource, targetMetadata);
    }

    private void writeSyncMetadata(Path sourceFile, PreparedSource preparedSource, Path targetMetadata) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("sourceUri", sourceFile.toAbsolutePath().normalize().toUri().toString());
        properties.setProperty("compiledSourceUri", preparedSource.compileSource().toAbsolutePath().normalize().toUri().toString());
        properties.setProperty("lineOffset", Integer.toString(preparedSource.lineOffset()));

        try (var writer = Files.newBufferedWriter(targetMetadata, StandardCharsets.UTF_8)) {
            properties.store(writer, "LaTeX Compiler SyncTeX source mapping");
        }
    }

    private int lineOffset(String prefix) {
        int count = 0;
        for (int index = 0; index < prefix.length(); index++) {
            if (prefix.charAt(index) == '\n') {
                count++;
            }
        }
        return count;
    }

    private ExportResult exportWithPandoc(JFrame owner, Path sourceFile, Path targetFile, ExportFormat format, Consumer<String> logger)
        throws IOException, InterruptedException {
        // Pandoc is used for document-style conversions like .tex to .docx and .pptx.
        Path pandoc = toolManager.ensureTool(owner, ToolType.PANDOC, logger);
        if (pandoc == null) {
            return new ExportResult(false, targetFile, format.label() + " export cancelled because Pandoc is not available.");
        }

        List<String> command = new ArrayList<>();
        command.add(pandoc.toString());
        command.add(sourceFile.toAbsolutePath().toString());
        command.add("-o");
        command.add(targetFile.toAbsolutePath().toString());

        logger.accept("Running Pandoc...");
        ProcessResult result = processRunner.run(command, sourceFile.toAbsolutePath().getParent());
        boolean success = result.exitCode() == 0 && Files.exists(targetFile);
        return new ExportResult(success, targetFile, result.output());
    }

    private String stripExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index > 0 ? fileName.substring(0, index) : fileName;
    }

    private record PreparedSource(Path compileSource, int lineOffset) {
    }
}
