package eolChecker;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class GitHubScanner {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String githubApiBase;
    private final String githubToken;

    public GitHubScanner(Properties config) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.githubApiBase = "https://api.github.com/users/";
        this.githubToken = config.getProperty("github.token", "").trim();
    }

    public Map<String, List<String>> scanRepositoriesWithRepoNames(String githubUser) {
        Map<String, List<String>> repoGradleFiles = new HashMap<>();
        String apiUrl = githubApiBase + githubUser + "/repos";

        System.out.println("🔍 Fetching repositories from: " + apiUrl);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(apiUrl))
                    .header("Authorization", "token " + githubToken)
                    .header("Accept", "application/vnd.github.v3+json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("❌ ERROR: Failed to fetch repositories - " + response.body());
                return repoGradleFiles;
            }

            JsonNode jsonResponse = objectMapper.readTree(response.body());

            for (JsonNode repo : jsonResponse) {
                String repoName = repo.get("name").asText();
                String repoContentsUrl = repo.get("contents_url").asText().replace("{+path}", "");
                List<String> gradleFiles = findGradleFilesRecursively(repoContentsUrl, repoName);

                if (!gradleFiles.isEmpty()) {
                    repoGradleFiles.put(repoName, gradleFiles);
                    System.out.println("✅ Found " + gradleFiles.size() + " Gradle files in " + repoName);
                }
            }

        } catch (Exception e) {
            System.err.println("❌ ERROR: Failed to fetch repositories - " + e.getMessage());
        }

        return repoGradleFiles;
    }

    private List<String> findGradleFilesRecursively(String repoContentsUrl, String repoName) {
        List<String> gradleFiles = new ArrayList<>();
        scanDirectory(repoContentsUrl, repoName, gradleFiles);
        return gradleFiles;
    }

    private void scanDirectory(String directoryUrl, String repoName, List<String> gradleFiles) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(directoryUrl))
                    .header("Authorization", "token " + githubToken)
                    .header("Accept", "application/vnd.github.v3+json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("❌ ERROR: Failed to fetch directory contents for " + repoName);
                return;
            }

            JsonNode jsonResponse = objectMapper.readTree(response.body());

            for (JsonNode file : jsonResponse) {
                String fileName = file.get("name").asText();
                String fileType = file.get("type").asText();
                String fileUrl = file.get("download_url").asText();

                // Check for Gradle files
                if (fileName.equals("build.gradle") || fileName.equals("libraries.gradle")) {
                    gradleFiles.add(fileUrl);
                    System.out.println("📂 Found Gradle file: " + fileUrl);
                }

                // Recursively check subdirectories
                if (fileType.equals("dir")) {
                    String subDirUrl = file.get("url").asText(); // GitHub API provides a URL to fetch subdirectory contents
                    scanDirectory(subDirUrl, repoName, gradleFiles);
                }
            }

        } catch (Exception e) {
            System.err.println("❌ ERROR: Failed to scan directory - " + e.getMessage());
        }
    }
}