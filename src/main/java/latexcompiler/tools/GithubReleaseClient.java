package latexcompiler.tools;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GithubReleaseClient {
    private static final Pattern NAME_PATTERN = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern URL_PATTERN = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"");

    private final HttpClient client;

    public GithubReleaseClient() {
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    public Optional<ReleaseAsset> findLatestAsset(String repository, Pattern assetNamePattern)
        throws IOException, InterruptedException {
        URI uri = URI.create("https://api.github.com/repos/" + repository + "/releases/latest");
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(60))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "LaTeX-Compiler")
            .GET()
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub returned HTTP " + response.statusCode());
        }

        String body = response.body();
        Matcher names = NAME_PATTERN.matcher(body);
        while (names.find()) {
            String name = names.group(1);
            if (assetNamePattern.matcher(name).matches()) {
                Matcher url = URL_PATTERN.matcher(body);
                url.region(names.end(), Math.min(body.length(), names.end() + 8000));
                if (url.find()) {
                    return Optional.of(new ReleaseAsset(name, URI.create(url.group(1))));
                }
            }
        }

        return Optional.empty();
    }
}
