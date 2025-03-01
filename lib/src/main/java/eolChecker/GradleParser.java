package eolChecker;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.*;

public class GradleParser {
    private final HttpClient httpClient;

    public GradleParser() {
        this.httpClient = HttpClient.newHttpClient();
    }

    public List<Dependency> extractDependencies(List<String> gradleFilePaths) {
        List<Dependency> dependencies = new ArrayList<>();

        for (String filePath : gradleFilePaths) {
            System.out.println("📥 Reading: " + filePath);
            List<String> lines = fetchGradleFileLines(filePath);

            if (lines != null) {
                dependencies.addAll(parseGradleDependencies(lines));
            }
        }
        return dependencies;
    }

    private List<String> fetchGradleFileLines(String filePath) {
        List<String> lines = new ArrayList<>();

        try {
            if (filePath.startsWith("http")) { // ✅ Fetch from GitHub if URL
                System.out.println("🌐 Fetching remote file: " + filePath);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI(filePath))
                        .header("Accept", "application/vnd.github.v3.raw")
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    lines = Arrays.asList(response.body().split("\n"));
                } else {
                    System.err.println("❌ ERROR: Failed to download " + filePath + " (HTTP " + response.statusCode() + ")");
                }
            } else { // ✅ Read local Gradle file
                System.out.println("📂 Reading local file: " + filePath);
                lines = Files.readAllLines(Paths.get(filePath));
            }
        } catch (Exception e) {
            System.err.println("❌ ERROR: Failed to read " + filePath + " - " + e.getMessage());
        }
        return lines;
    }

    private List<Dependency> parseGradleDependencies(List<String> lines) {
        List<Dependency> dependencies = new ArrayList<>();

        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.startsWith("implementation") || trimmedLine.startsWith("api") || trimmedLine.startsWith("compile")) {
                String[] parts = trimmedLine.split("'");
                if (parts.length > 1) {
                    String dependency = parts[1].trim();
                    String[] depParts = dependency.split(":");

                    if (depParts.length == 3) {
                        dependencies.add(new Dependency(depParts[0], depParts[1], depParts[2]));
                        System.out.println("✅ Extracted Dependency: " + depParts[0] + ":" + depParts[1] + ":" + depParts[2]);
                    } else {
                        System.err.println("⚠️ WARNING: Skipping invalid dependency: " + dependency);
                    }
                }
            }
        }
        return dependencies;
    }
}