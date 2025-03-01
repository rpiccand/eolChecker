package eolChecker;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GradleParser {
    private static final Logger logger = LoggerFactory.getLogger(GradleParser.class);

    private final HttpClient httpClient;

    // Regex to match dependencies using single/double quotes
    private static final Pattern DEPENDENCY_PATTERN = Pattern.compile("(implementation|api|compile|testImplementation)\\s+['\"]([^'\"]+)['\"]");

    // Regex to extract versions from `strictly` blocks
    private static final Pattern STRICTLY_VERSION_PATTERN = Pattern.compile("strictly\\s+['\"]([^'\"]+)['\"]");

    public GradleParser() {
        this.httpClient = HttpClient.newHttpClient();
    }

    public List<Dependency> extractDependencies(List<String> gradleFilePaths) {
        List<Dependency> dependencies = new ArrayList<>();

        for (String filePath : gradleFilePaths) {
            logger.info("Reading: {}", filePath);
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
            if (filePath.startsWith("http")) { // Fetch from GitHub if URL
                logger.info("Fetching remote file: {}", filePath);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI(filePath))
                        .header("Accept", "application/vnd.github.v3.raw")
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    lines = Arrays.asList(response.body().split("\n"));
                } else {
                    logger.error("ERROR: Failed to download {} (HTTP {})", filePath, response.statusCode());
                }
            } else { // Read local Gradle file
                logger.info("📂 Reading local file: {}", filePath);
                lines = Files.readAllLines(Paths.get(filePath));
            }
        } catch (Exception e) {
            logger.error("ERROR: Failed to read {} - {}", filePath, e.getMessage());
        }
        return lines;
    }

    private List<Dependency> parseGradleDependencies(List<String> lines) {
        List<Dependency> dependencies = new ArrayList<>();
        Map<String, String> dependencyVersionMap = new HashMap<>();

        String currentDependency = null; // To store dependency being processed

        for (String line : lines) {
            String trimmedLine = line.trim();

            // Ignore comment lines
            if (trimmedLine.startsWith("//")) {
                continue;
            }

            // Match dependency declarations
            Matcher matcher = DEPENDENCY_PATTERN.matcher(trimmedLine);
            if (matcher.find()) {
                currentDependency = matcher.group(2).trim(); 
                String[] depParts = currentDependency.split(":");

                if (depParts.length == 3) {
                    dependencies.add(new Dependency(depParts[0], depParts[1], depParts[2]));
                    logger.info("Extracted Dependency: {}:{}:{}", depParts[0], depParts[1], depParts[2]);
                } else {
                    dependencyVersionMap.put(depParts[0] + ":" + depParts[1], ""); // Store for future version lookup
                    logger.debug("Found dependency without version: {}:{}", depParts[0], depParts[1]);
                }
                continue;
            }

            // Match `strictly 'version'` inside dependency block
            Matcher versionMatcher = STRICTLY_VERSION_PATTERN.matcher(trimmedLine);
            if (versionMatcher.find() && currentDependency != null) {
                String strictlyVersion = versionMatcher.group(1).trim();
                String[] depParts = currentDependency.split(":");

                if (depParts.length >= 2) { // Ensure group and artifact exist
                    String group = depParts[0];
                    String artifact = depParts[1];
                    dependencyVersionMap.put(group + ":" + artifact, strictlyVersion);
                    logger.info("Found strictly version: {} for {}:{}", strictlyVersion, group, artifact);
                }
            }
        }

        // Add dependencies with versions found in `strictly` blocks
        for (Map.Entry<String, String> entry : dependencyVersionMap.entrySet()) {
            String key = entry.getKey();
            String version = entry.getValue();

            if (!version.isEmpty()) {
                String[] depParts = key.split(":");
                dependencies.add(new Dependency(depParts[0], depParts[1], version));
                logger.info("Extracted Dependency (strictly): {}:{}:{}", depParts[0], depParts[1], version);
            } else {
                logger.warn("WARNING: No version found for dependency: {}", key);
            }
        }

        return dependencies;
    }
}