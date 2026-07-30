package latexcompiler.files;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class AppPaths {
    private static final String APP_NAME = "LaTeX Compiler";
    private static final String APP_SLUG = "latex-compiler";

    private AppPaths() {
    }

    public static Path appDataDirectory() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            return Paths.get(appData, APP_NAME);
        }

        return Paths.get(System.getProperty("user.home"), "." + APP_SLUG);
    }

    public static Path managedToolsDirectory() {
        return appDataDirectory().resolve("tools");
    }

    public static Path templatesDirectory() {
        return appDataDirectory().resolve("templates");
    }

    public static Path bundledToolsDirectory() {
        Path applicationDirectory = applicationDirectory();
        Path direct = applicationDirectory.resolve("tools").resolve(Platform.current().folderName());
        if (java.nio.file.Files.exists(direct)) {
            return direct;
        }

        Path parent = applicationDirectory.getParent();
        if (parent != null) {
            Path sibling = parent.resolve("tools").resolve(Platform.current().folderName());
            if (java.nio.file.Files.exists(sibling)) {
                return sibling;
            }
        }

        return direct;
    }

    public static Path applicationDirectory() {
        try {
            URI uri = AppPaths.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path location = Paths.get(uri);
            if (location.toString().endsWith(".jar")) {
                Path parent = location.getParent();
                return parent == null ? Paths.get(".").toAbsolutePath() : parent;
            }
            return Paths.get("").toAbsolutePath();
        } catch (URISyntaxException | SecurityException ignored) {
            return Paths.get("").toAbsolutePath();
        }
    }
}
