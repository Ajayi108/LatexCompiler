package latexcompiler.synctex;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.zip.GZIPInputStream;

public final class SyncTexService {
    public static final String METADATA_EXTENSION = ".latex-compiler-sync.properties";

    public Optional<SourcePosition> findSourcePosition(
        Path pdfFile,
        Path currentSourceFile,
        int pageNumber,
        double pageX,
        double pageY
    ) throws IOException {
        Path syncTexFile = syncTexPathForPdf(pdfFile);
        if (!Files.isRegularFile(syncTexFile)) {
            return Optional.empty();
        }

        SyncMetadata metadata = readMetadata(pdfFile, currentSourceFile);
        ParsedSyncTex parsed = parse(syncTexFile);
        PageData page = parsed.pages().get(pageNumber);
        if (page == null || page.candidates().isEmpty() || !page.bounds().isUsable()) {
            return Optional.empty();
        }

        List<Candidate> sourceMatches = matchingCandidates(page.candidates(), parsed.inputs(), metadata);
        List<Candidate> candidates = sourceMatches.isEmpty()
            ? matchingCandidates(page.candidates(), parsed.inputs(), metadata.withoutCompiledSource())
            : sourceMatches;
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        double targetX = page.bounds().xAt(clamp(pageX));
        double targetY = page.bounds().yAt(clamp(pageY));
        Candidate best = null;
        double bestScore = Double.MAX_VALUE;
        for (Candidate candidate : candidates) {
            double score = score(candidate, targetX, targetY, page.bounds());
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
            }
        }

        if (best == null) {
            return Optional.empty();
        }

        Path sourceFile = sourceFor(best, parsed.inputs(), metadata);
        int line = translatedLine(best, parsed.inputs(), metadata);
        if (sourceFile == null || line < 1) {
            return Optional.empty();
        }

        return Optional.of(new SourcePosition(sourceFile, line, pageNumber, confidence(bestScore)));
    }

    public static Path syncTexPathForPdf(Path pdfFile) {
        return pdfFile.resolveSibling(baseName(pdfFile) + ".synctex.gz");
    }

    public static Path metadataPathForPdf(Path pdfFile) {
        return pdfFile.resolveSibling(baseName(pdfFile) + METADATA_EXTENSION);
    }

    private ParsedSyncTex parse(Path syncTexFile) throws IOException {
        Map<Integer, Path> inputs = new HashMap<>();
        Map<Integer, PageData> pages = new HashMap<>();
        PageData currentPage = null;

        try (
            GZIPInputStream gzip = new GZIPInputStream(Files.newInputStream(syncTexFile));
            BufferedReader reader = new BufferedReader(new InputStreamReader(gzip, StandardCharsets.UTF_8))
        ) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Input:")) {
                    parseInput(line).ifPresent(input -> inputs.put(input.tag(), input.path()));
                    continue;
                }

                if (line.startsWith("{")) {
                    int pageNumber = parsePositiveInt(line.substring(1));
                    if (pageNumber > 0) {
                        currentPage = pages.computeIfAbsent(pageNumber, ignored -> new PageData());
                    }
                    continue;
                }

                if (line.startsWith("}")) {
                    currentPage = null;
                    continue;
                }

                if (currentPage != null) {
                    parseCandidate(line).ifPresent(currentPage::add);
                }
            }
        }

        return new ParsedSyncTex(inputs, pages);
    }

    private Optional<InputRef> parseInput(String line) {
        int tagStart = "Input:".length();
        int pathSeparator = line.indexOf(':', tagStart);
        if (pathSeparator < 0 || pathSeparator == line.length() - 1) {
            return Optional.empty();
        }

        int tag = parsePositiveInt(line.substring(tagStart, pathSeparator));
        if (tag < 1) {
            return Optional.empty();
        }

        String rawPath = line.substring(pathSeparator + 1).trim();
        if (rawPath.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new InputRef(tag, normalize(Path.of(rawPath))));
    }

    private Optional<Candidate> parseCandidate(String line) {
        if (line.length() < 6) {
            return Optional.empty();
        }

        char type = line.charAt(0);
        int tagEnd = line.indexOf(',');
        int lineEnd = line.indexOf(':', tagEnd + 1);
        int xEnd = line.indexOf(',', lineEnd + 1);
        if (tagEnd < 1 || lineEnd < 0 || xEnd < 0) {
            return Optional.empty();
        }

        int yEnd = nextNumberEnd(line, xEnd + 1);
        if (yEnd <= xEnd + 1) {
            return Optional.empty();
        }

        try {
            int inputTag = Integer.parseInt(line.substring(1, tagEnd));
            int sourceLine = Integer.parseInt(line.substring(tagEnd + 1, lineEnd));
            long x = Long.parseLong(line.substring(lineEnd + 1, xEnd));
            long y = Long.parseLong(line.substring(xEnd + 1, yEnd));
            return Optional.of(new Candidate(type, inputTag, sourceLine, x, y));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private int nextNumberEnd(String line, int start) {
        int index = start;
        if (index < line.length() && line.charAt(index) == '-') {
            index++;
        }
        while (index < line.length() && Character.isDigit(line.charAt(index))) {
            index++;
        }
        return index;
    }

    private List<Candidate> matchingCandidates(
        List<Candidate> pageCandidates,
        Map<Integer, Path> inputs,
        SyncMetadata metadata
    ) {
        List<Candidate> matches = new ArrayList<>();
        for (Candidate candidate : pageCandidates) {
            if (!candidate.canJumpToSource()) {
                continue;
            }

            Path inputFile = inputs.get(candidate.inputTag());
            if (inputFile == null) {
                continue;
            }

            if (metadata.matchesSource(inputFile) || metadata.matchesCompiledSource(inputFile)) {
                matches.add(candidate);
            }
        }
        return matches;
    }

    private Path sourceFor(Candidate candidate, Map<Integer, Path> inputs, SyncMetadata metadata) {
        Path inputFile = inputs.get(candidate.inputTag());
        if (inputFile == null) {
            return null;
        }
        if (metadata.matchesCompiledSource(inputFile)) {
            return metadata.sourceFile();
        }
        return inputFile;
    }

    private int translatedLine(Candidate candidate, Map<Integer, Path> inputs, SyncMetadata metadata) {
        Path inputFile = inputs.get(candidate.inputTag());
        if (inputFile != null && metadata.matchesCompiledSource(inputFile)) {
            return candidate.sourceLine() - metadata.lineOffset();
        }
        return candidate.sourceLine();
    }

    private double score(Candidate candidate, double targetX, double targetY, Bounds bounds) {
        double width = Math.max(1d, bounds.maxX() - bounds.minX());
        double height = Math.max(1d, bounds.maxY() - bounds.minY());
        double vertical = Math.abs(candidate.y() - targetY) / height;
        double horizontal = Math.abs(candidate.x() - targetX) / width;
        // Vertical closeness decides the source line; horizontal closeness helps within dense rows.
        return vertical * 12d + horizontal * 0.8d;
    }

    private SyncMetadata readMetadata(Path pdfFile, Path currentSourceFile) throws IOException {
        Path metadataFile = metadataPathForPdf(pdfFile);
        Path sourceFile = normalize(currentSourceFile);
        Path compiledSourceFile = null;
        int lineOffset = 0;

        if (Files.isRegularFile(metadataFile)) {
            Properties properties = new Properties();
            try (BufferedReader reader = Files.newBufferedReader(metadataFile, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            sourceFile = readPath(properties.getProperty("sourceUri")).orElse(sourceFile);
            compiledSourceFile = readPath(properties.getProperty("compiledSourceUri")).orElse(null);
            lineOffset = parseNonNegativeInt(properties.getProperty("lineOffset"));
        }

        return new SyncMetadata(sourceFile, compiledSourceFile, lineOffset);
    }

    private Optional<Path> readPath(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(normalize(Path.of(URI.create(value))));
        } catch (IllegalArgumentException ignored) {
            return Optional.of(normalize(Path.of(value)));
        }
    }

    private int parsePositiveInt(String value) {
        try {
            int number = Integer.parseInt(value.trim());
            return number > 0 ? number : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private int parseNonNegativeInt(String value) {
        try {
            int number = Integer.parseInt(value == null ? "" : value.trim());
            return Math.max(0, number);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private double confidence(double score) {
        return Math.max(0d, Math.min(1d, 1d - score));
    }

    private double clamp(double value) {
        return Math.max(0d, Math.min(1d, value));
    }

    private static String baseName(Path file) {
        String fileName = file.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static Path normalize(Path path) {
        return path == null ? null : path.toAbsolutePath().normalize();
    }

    private record ParsedSyncTex(Map<Integer, Path> inputs, Map<Integer, PageData> pages) {
    }

    private record InputRef(int tag, Path path) {
    }

    private record Candidate(char type, int inputTag, int sourceLine, long x, long y) {
        private boolean canJumpToSource() {
            String jumpTypes = "xghvr";
            return sourceLine > 0 && jumpTypes.indexOf(Character.toLowerCase(type)) >= 0;
        }
    }

    private static final class PageData {
        private final List<Candidate> candidates = new ArrayList<>();
        private final Bounds bounds = new Bounds();

        private void add(Candidate candidate) {
            bounds.include(candidate.x(), candidate.y());
            candidates.add(candidate);
        }

        private List<Candidate> candidates() {
            return candidates;
        }

        private Bounds bounds() {
            return bounds;
        }
    }

    private static final class Bounds {
        private long minX = Long.MAX_VALUE;
        private long maxX = Long.MIN_VALUE;
        private long minY = Long.MAX_VALUE;
        private long maxY = Long.MIN_VALUE;

        private void include(long x, long y) {
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }

        private boolean isUsable() {
            return minX < maxX && minY < maxY;
        }

        private double xAt(double fraction) {
            return minX + fraction * (maxX - minX);
        }

        private double yAt(double fraction) {
            return minY + fraction * (maxY - minY);
        }

        private long minX() {
            return minX;
        }

        private long maxX() {
            return maxX;
        }

        private long minY() {
            return minY;
        }

        private long maxY() {
            return maxY;
        }
    }

    private record SyncMetadata(Path sourceFile, Path compiledSourceFile, int lineOffset) {
        private boolean matchesSource(Path inputFile) {
            return samePath(inputFile, sourceFile);
        }

        private boolean matchesCompiledSource(Path inputFile) {
            return samePath(inputFile, compiledSourceFile);
        }

        private SyncMetadata withoutCompiledSource() {
            return new SyncMetadata(sourceFile, null, 0);
        }

        private boolean samePath(Path left, Path right) {
            if (left == null || right == null) {
                return false;
            }
            String leftText = normalize(left).toString().toLowerCase(Locale.ROOT);
            String rightText = normalize(right).toString().toLowerCase(Locale.ROOT);
            return leftText.equals(rightText);
        }
    }
}
