package eolChecker;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GradleParser {
    private static final Logger logger = LoggerFactory.getLogger(GradleParser.class);
    private final HttpClient httpClient;

    // Regex to match dependencies in `build.gradle`
    private static final Pattern DEPENDENCY_PATTERN = Pattern.compile("(implementation|api|compile|testImplementation)\\s+['\"]([^'\"]+)['\"]");

    // Regex to match key-value pairs inside `libraries.gradle`
    private static final Pattern LIBRARIES_PATTERN = Pattern.compile("\\s*([a-zA-Z0-9_-]+)\\s*:\\s*['\"]([^'\"]+)['\"]");

    // Regex to capture version variables (e.g., `ext.springVersion = '5.3.9'`)
    private static final Pattern VERSION_PATTERN = Pattern.compile("ext\\.([a-zA-Z0-9_-]+)\\s*=\\s*['\"]([^'\"]+)['\"]");

    // Regex to extract `strictly` versions inside dependency blocks
    private static final Pattern STRICTLY_VERSION_PATTERN = Pattern.compile("strictly\\s+['\"]([^'\"]+)['\"]");

    // Stores extracted version variables (e.g., `springVersion` → `5.3.9`)
    private final Map<String, String> versionVariables = new HashMap<>();

    public GradleParser() {
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Extracts dependencies from a list of Gradle file paths.
     */
    public List<Dependency> extractDependencies(List<String> gradleFilePaths) {
        List<Dependency> dependencies = new ArrayList<>();

        for (String filePath : gradleFilePaths) {
            logger.info("Reading: {}", filePath);
            List<String> lines = fetchGradleFileLines(filePath);

            if (lines != null) {
                if (filePath.contains("libraries.gradle")) {
                    extractVersionVariables(lines); // First, extract all version variables
                    dependencies.addAll(parseLibrariesGradle(lines));
                } else {
                    dependencies.addAll(parseGradleDependencies(lines));
                }
            }
        }
        return dependencies;
    }

    /**
     * Fetches the content of a Gradle file from either a local or remote location.
     */
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
                    logger.error("Failed to download {} (HTTP {})", filePath, response.statusCode());
                }
            } else { // Read local Gradle file
                logger.info("Reading local file: {}", filePath);
                lines = Files.readAllLines(Paths.get(filePath));
            }
        } catch (Exception e) {
            logger.error("Failed to read {} - {}", filePath, e.getMessage());
        }
        return lines;
    }

    /**
     * Extracts version variables like `ext.springVersion = '5.3.9'` and stores them.
     */
    private void extractVersionVariables(List<String> lines) {
        for (String line : lines) {
            Matcher matcher = VERSION_PATTERN.matcher(line.trim());
            if (matcher.find()) {
                String variableName = matcher.group(1);
                String variableValue = matcher.group(2);
                versionVariables.put(variableName, variableValue);
                logger.info("Extracted version variable: {} = {}", variableName, variableValue);
            }
        }
    }

    /**
     * Parses dependencies from `build.gradle`.
     */
    private List<Dependency> parseGradleDependencies(List<String> lines) {
        List<Dependency> dependencies = new ArrayList<>();
        Map<String, String> dependencyVersionMap = new HashMap<>();
        String currentDependency = null;

        for (String line : lines) {
            String trimmedLine = line.trim();

            if (trimmedLine.startsWith("//")) continue; // Ignore comments

            Matcher matcher = DEPENDENCY_PATTERN.matcher(trimmedLine);
            if (matcher.find()) {
                currentDependency = matcher.group(2).trim();
                String[] depParts = currentDependency.split(":");

                if (depParts.length == 3) {
                    dependencies.add(new Dependency(depParts[0], depParts[1], depParts[2]));
                    logger.info("Extracted Dependency: {}:{}:{}", depParts[0], depParts[1], depParts[2]);
                } else {
                    dependencyVersionMap.put(depParts[0] + ":" + depParts[1], "");
                    logger.debug("Found dependency without version: {}:{}", depParts[0], depParts[1]);
                }
                continue;
            }

            Matcher versionMatcher = STRICTLY_VERSION_PATTERN.matcher(trimmedLine);
            if (versionMatcher.find() && currentDependency != null) {
                String strictlyVersion = versionMatcher.group(1).trim();
                String[] depParts = currentDependency.split(":");

                if (depParts.length >= 2) {
                    dependencyVersionMap.put(depParts[0] + ":" + depParts[1], strictlyVersion);
                    logger.info("Found strictly version: {} for {}:{}", strictlyVersion, depParts[0], depParts[1]);
                }
            }
        }

        dependencies.addAll(applyStrictlyVersions(dependencyVersionMap));
        return dependencies;
    }

    /**
     * Parses dependencies from `libraries.gradle`, resolving version variables.
     */
    private List<Dependency> parseLibrariesGradle(List<String> lines) {
        List<Dependency> dependencies = new ArrayList<>();

        for (String line : lines) {
            Matcher matcher = LIBRARIES_PATTERN.matcher(line.trim());
            if (matcher.find()) {
                String key = matcher.group(1);
                String dependencyString = matcher.group(2);

                // Resolve variable references like `${springVersion}`
                dependencyString = resolveVersionVariables(dependencyString);

                String[] depParts = dependencyString.split(":");
                if (depParts.length == 3) {
                    dependencies.add(new Dependency(depParts[0], depParts[1], depParts[2].replace("@jar", "")));
                    logger.info("Extracted from libraries.gradle: {} -> {}:{}:{}", key, depParts[0], depParts[1], depParts[2]);
                } else {
                    logger.warn("Invalid dependency format in libraries.gradle: {}", dependencyString);
                }
            }
        }
        return dependencies;
    }

    /**
     * Resolves variable references like `${springVersion}`.
     */
    private String resolveVersionVariables(String dependencyString) {
        for (Map.Entry<String, String> entry : versionVariables.entrySet()) {
            dependencyString = dependencyString.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return dependencyString;
    }

    /**
     * Applies strictly versions found in dependency blocks.
     */
    private List<Dependency> applyStrictlyVersions(Map<String, String> dependencyVersionMap) {
        List<Dependency> dependencies = new ArrayList<>();
        for (Map.Entry<String, String> entry : dependencyVersionMap.entrySet()) {
            String key = entry.getKey();
            String version = entry.getValue();

            if (!version.isEmpty()) {
                String[] depParts = key.split(":");
                dependencies.add(new Dependency(depParts[0], depParts[1], version));
                logger.info("Extracted Dependency (strictly): {}:{}:{}", depParts[0], depParts[1], version);
            } else {
                logger.warn("No version found for dependency: {}", key);
            }
        }
        return dependencies;
    }
}