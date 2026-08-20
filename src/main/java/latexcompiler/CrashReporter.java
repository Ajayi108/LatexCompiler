package latexcompiler;

import latexcompiler.files.AppPaths;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Stores unexpected crashes locally and prepares optional report text for the user.
 * Reports are not sent automatically because logs can contain local paths or document names.
 */
public final class CrashReporter {
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
    private static final int MAX_REPORT_LOG_CHARS = 6_000;

    private CrashReporter() {
    }

    public static void install() {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> writeCrashLog(thread.getName(), error));
    }

    public static Optional<Path> writeCrashLog(String threadName, Throwable error) {
        try {
            Files.createDirectories(AppPaths.logsDirectory());
            Path logFile = AppPaths.logsDirectory().resolve("crash-" + FILE_TIMESTAMP.format(LocalDateTime.now()) + ".log");
            Files.writeString(logFile, crashText(threadName, error), StandardCharsets.UTF_8);
            return Optional.of(logFile);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public static Optional<Path> latestCrashLog() {
        Path logsDirectory = AppPaths.logsDirectory();
        if (!Files.isDirectory(logsDirectory)) {
            return Optional.empty();
        }

        try (Stream<Path> logs = Files.list(logsDirectory)) {
            return logs
                .filter(path -> path.getFileName().toString().startsWith("crash-"))
                .filter(path -> path.getFileName().toString().endsWith(".log"))
                .filter(Files::isRegularFile)
                .max(Comparator.comparing(CrashReporter::lastModifiedTime));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public static String latestCrashLogText() {
        Optional<Path> latest = latestCrashLog();
        if (latest.isEmpty()) {
            return "";
        }

        try {
            String text = Files.readString(latest.get(), StandardCharsets.UTF_8);
            if (text.length() <= MAX_REPORT_LOG_CHARS) {
                return text;
            }
            // GitHub issue URLs have practical size limits, so keep copied crash logs compact.
            return text.substring(0, MAX_REPORT_LOG_CHARS) + "\n\n[Crash log truncated for the GitHub issue body.]";
        } catch (Exception ignored) {
            return "";
        }
    }

    public static String appVersion() {
        Package appPackage = CrashReporter.class.getPackage();
        String version = appPackage == null ? null : appPackage.getImplementationVersion();
        return version == null || version.isBlank() ? "dev" : version;
    }

    private static String crashText(String threadName, Throwable error) {
        StringWriter stackTrace = new StringWriter();
        error.printStackTrace(new PrintWriter(stackTrace));

        return ""
            + "LaTeX Compiler crash report\n"
            + "Time: " + LocalDateTime.now() + "\n"
            + "Version: " + appVersion() + "\n"
            + "Thread: " + threadName + "\n"
            + "Java: " + System.getProperty("java.version") + "\n"
            + "Java home: " + System.getProperty("java.home") + "\n"
            + "OS: " + System.getProperty("os.name") + " "
            + System.getProperty("os.version") + " "
            + System.getProperty("os.arch") + "\n\n"
            + stackTrace;
    }

    private static long lastModifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception ignored) {
            return 0L;
        }
    }
}
