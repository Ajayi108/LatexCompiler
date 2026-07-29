package latexcompiler.tools;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ToolDownloader {
    private final HttpClient client;

    public ToolDownloader() {
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    public Path download(URI uri, Path destinationDirectory, Consumer<String> logger)
        throws IOException, InterruptedException {
        Files.createDirectories(destinationDirectory);
        String fileName = Path.of(uri.getPath()).getFileName().toString();
        Path archive = destinationDirectory.resolve(fileName);

        logger.accept("Downloading " + fileName + "...");
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofMinutes(10))
            .header("User-Agent", "LatexCompiler")
            .GET()
            .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Download failed with HTTP " + response.statusCode());
        }

        try (InputStream body = response.body()) {
            Files.copy(body, archive, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        if (fileName.endsWith(".zip")) {
            Path extracted = destinationDirectory.resolve(stripZipSuffix(fileName));
            unzip(archive, extracted);
            return extracted;
        }

        return archive;
    }

    private void unzip(Path archive, Path destinationDirectory) throws IOException {
        Files.createDirectories(destinationDirectory);
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path target = destinationDirectory.resolve(entry.getName()).normalize();
                if (!target.startsWith(destinationDirectory)) {
                    // Prevent malicious zip entries from writing outside the managed tools folder.
                    throw new IOException("Blocked unsafe zip entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zip, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private String stripZipSuffix(String fileName) {
        return fileName.substring(0, fileName.length() - ".zip".length());
    }
}
