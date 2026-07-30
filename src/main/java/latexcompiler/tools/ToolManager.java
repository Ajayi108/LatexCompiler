package latexcompiler.tools;

import latexcompiler.files.AppPaths;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class ToolManager {
    private final GithubReleaseClient releaseClient;
    private final ToolDownloader downloader;

    public ToolManager(GithubReleaseClient releaseClient, ToolDownloader downloader) {
        this.releaseClient = releaseClient;
        this.downloader = downloader;
    }

    public Path ensureTool(JFrame owner, ToolType toolType, Consumer<String> logger)
        throws IOException, InterruptedException {
        Optional<Path> existing = findTool(toolType);
        if (existing.isPresent()) {
            logger.accept("Using " + toolType.displayName() + ": " + existing.get());
            return existing.get();
        }

        int choice = confirmInstall(owner, toolType);

        if (choice != JOptionPane.YES_OPTION) {
            return null;
        }

        return installTool(toolType, logger)
            .orElseThrow(() -> new IOException("Could not install " + toolType.displayName()));
    }

    public Optional<Path> findTool(ToolType toolType) throws IOException {
        // Prefer bundled tools, then app-managed downloads, then developer tools on PATH.
        Path bundled = AppPaths.bundledToolsDirectory().resolve(toolType.executableName());
        if (Files.isRegularFile(bundled)) {
            return Optional.of(bundled);
        }

        Path managedRoot = AppPaths.managedToolsDirectory().resolve(toolType.folderName());
        if (Files.isDirectory(managedRoot)) {
            try (var stream = Files.walk(managedRoot)) {
                Optional<Path> managed = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase(toolType.executableName()))
                    .findFirst();
                if (managed.isPresent()) {
                    return managed;
                }
            }
        }

        return findOnPath(toolType.executableName());
    }

    private Optional<Path> installTool(ToolType toolType, Consumer<String> logger)
        throws IOException, InterruptedException {
        logger.accept("Looking up the latest " + toolType.displayName() + " release...");
        Optional<ReleaseAsset> asset = releaseClient.findLatestAsset(toolType.githubRepository(), toolType.assetNamePattern());
        if (asset.isEmpty()) {
            return Optional.empty();
        }

        logger.accept("Found " + asset.get().name());
        Path destination = AppPaths.managedToolsDirectory().resolve(toolType.folderName());
        Path extracted = downloader.download(asset.get().downloadUrl(), destination, logger);
        try (var stream = Files.walk(extracted)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().equalsIgnoreCase(toolType.executableName()))
                .findFirst();
        }
    }

    private int confirmInstall(JFrame owner, ToolType toolType) throws IOException, InterruptedException {
        AtomicInteger choice = new AtomicInteger(JOptionPane.CANCEL_OPTION);
        Runnable dialog = () -> choice.set(JOptionPane.showConfirmDialog(
            owner,
            toolType.displayName() + " is required for this export.\n\nDownload and install it inside LaTeX Compiler now?",
            "Install " + toolType.displayName(),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        ));

        if (SwingUtilities.isEventDispatchThread()) {
            dialog.run();
            return choice.get();
        }

        try {
            // Export runs off the UI thread, but Swing dialogs must be created on the UI thread.
            SwingUtilities.invokeAndWait(dialog);
            return choice.get();
        } catch (InvocationTargetException error) {
            throw new IOException("Could not show install prompt", error.getCause());
        }
    }

    private Optional<Path> findOnPath(String executableName) {
        String pathValue = System.getenv("PATH");
        if (pathValue == null || pathValue.isBlank()) {
            return Optional.empty();
        }

        String[] entries = pathValue.split(java.io.File.pathSeparator);
        for (String entry : entries) {
            Path candidate = Path.of(entry, executableName);
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
        }

        return Optional.empty();
    }
}
