package eolChecker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

/**
 * GitHubScanner fetches Gradle files from a GitHub repository or organization.
 */
public class GitHubScanner {
    private static final Logger logger = LoggerFactory.getLogger(GitHubScanner.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String githubToken;
    private final String githubBranch;

    public GitHubScanner(String githubToken, String githubBranch) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.githubToken = githubToken;
        this.githubBranch = githubBranch;
    }

    public Map<String, List<String>> scanRepositories(String githubRepo) {
        Map<String, List<String>> repoGradleFiles = new HashMap<>();
        boolean isSingleRepo = false;
        githubRepo = githubRepo.trim();

        if (githubRepo.startsWith("https://github.com/")) {
            String[] parts = githubRepo.replace("https://github.com/", "").split("/");
            if (parts.length == 1) {
                logger.info("Scanning all repositories for org/user: {}", parts[0]);
            } else if (parts.length >= 2) {
                logger.info("Scanning only repository: {}/{}", parts[0], parts[1]);
                isSingleRepo = true;
            }
        } else {
            logger.error("Invalid GitHub URL format in github.repo.");
            return repoGradleFiles;
        }

        try {
            if (isSingleRepo) {
                String repoOwner = githubRepo.split("/")[3];
                String repoName = githubRepo.split("/")[4];
                String branch = fetchDefaultBranch(repoOwner, repoName);

                Repository repo = new Repository(repoOwner, repoName, branch);
                logger.info("Using branch: {}", branch);
                
                List<String> gradleFiles = findGradleFilesRecursively(repo.getContentsUrl());
                if (!gradleFiles.isEmpty()) {
                    repoGradleFiles.put(repoName, gradleFiles);
                } else {
                    logger.warn("No Gradle files found in repository: {}", repoName);
                }
            } else {
                String githubApiUrl = "https://api.github.com/users/" + githubRepo.split("/")[3] + "/repos";
                logger.info("Fetching repositories from: {}", githubApiUrl);

                HttpRequest request = buildGitHubRequest(githubApiUrl);
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    logger.error("Failed to fetch repositories - {}", response.body());
                    return repoGradleFiles;
                }

                JsonNode jsonResponse = objectMapper.readTree(response.body());

                for (JsonNode repoNode : jsonResponse) {
                    String repoName = repoNode.get("name").asText();
                    String repoOwner = githubRepo.split("/")[3];
                    String branch = fetchDefaultBranch(repoOwner, repoName);

                    Repository repo = new Repository(repoOwner, repoName, branch);
                    List<String> gradleFiles = findGradleFilesRecursively(repo.getContentsUrl());

                    if (!gradleFiles.isEmpty()) {
                        repoGradleFiles.put(repoName, gradleFiles);
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Failed to fetch repositories - {}", e.getMessage());
        }

        return repoGradleFiles;
    }
    
    private String fetchDefaultBranch(String owner, String repoName) {
        try {
            String apiUrl = "https://api.github.com/repos/" + owner + "/" + repoName;
            HttpRequest request = buildGitHubRequest(apiUrl);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode jsonResponse = objectMapper.readTree(response.body());
                return jsonResponse.get("default_branch").asText();
            } else {
                logger.warn("Failed to fetch default branch for {}/{}. Using 'main' as fallback.", owner, repoName);
            }
        } catch (Exception e) {
            logger.error("Error fetching default branch for {}/{}: {}", owner, repoName, e.getMessage());
        }
        return "main"; // Default fallback branch
    }

    private boolean isSingleRepository(String githubRepo) {
        return githubRepo.split("/").length >= 5;
    }

    private Repository extractRepositoryDetails(String githubRepo) {
        String[] parts = githubRepo.replace("https://github.com/", "").split("/");
        return new Repository(parts[0], parts[1], githubBranch);
    }

    private String extractOwnerFromUrl(String githubRepo) {
        return githubRepo.replace("https://github.com/", "").split("/")[0];
    }

    private void fetchGradleFilesFromRepository(Repository repo, Map<String, List<String>> repoGradleFiles) {
        String repoContentsUrl = repo.getContentsUrl();
        logger.info("Fetching Gradle files from: {}", repoContentsUrl);
        List<String> gradleFiles = findGradleFilesRecursively(repoContentsUrl);

        if (!gradleFiles.isEmpty()) {
            repoGradleFiles.put(repo.getName(), gradleFiles);
        } else {
            logger.warn("No Gradle files found in repository: {}", repo.getName());
        }
    }

    private void fetchRepositoriesFromOrganization(String owner, Map<String, List<String>> repoGradleFiles) {
        String apiUrl = "https://api.github.com/users/" + owner + "/repos";
        logger.info("Fetching repositories from: {}", apiUrl);

        try {
            HttpResponse<String> response = sendGitHubRequest(apiUrl);
            if (response.statusCode() != 200) {
                logger.error("Failed to fetch repositories - {}", response.body());
                return;
            }

            JsonNode jsonResponse = objectMapper.readTree(response.body());
            for (JsonNode repoNode : jsonResponse) {
                String repoName = repoNode.get("name").asText();
                Repository repo = new Repository(owner, repoName, githubBranch);
                fetchGradleFilesFromRepository(repo, repoGradleFiles);
            }
        } catch (Exception e) {
            logger.error("Error fetching repositories: {}", e.getMessage());
        }
    }

    private List<String> findGradleFilesRecursively(String repoContentsUrl) {
        List<String> gradleFiles = new ArrayList<>();
        int maxRetries = 5;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {
                HttpResponse<String> response = sendGitHubRequest(repoContentsUrl);
                if (response.statusCode() != 200) {
                    handleGitHubError(repoContentsUrl, response, retryCount, maxRetries);
                    return gradleFiles;
                }

                JsonNode jsonResponse = objectMapper.readTree(response.body());
                if (!jsonResponse.isArray()) {
                    logger.error("Unexpected API response format for {}", repoContentsUrl);
                    return gradleFiles;
                }

                processGitHubDirectory(jsonResponse, gradleFiles);
                return gradleFiles;
            } catch (Exception e) {
                logger.error("Exception while scanning {} - {}", repoContentsUrl, e.getMessage());
                retryCount = handleStreamError(retryCount, maxRetries);
            }
        }

        return gradleFiles;
    }

    private void processGitHubDirectory(JsonNode jsonResponse, List<String> gradleFiles) {
        for (JsonNode fileNode : jsonResponse) {
            String fileType = fileNode.get("type").asText();
            String filePath = fileNode.get("path").asText();
            String downloadUrl = fileNode.get("download_url").asText(null);
            String subdirUrl = fileNode.get("url").asText();

            if ("file".equals(fileType) && isGradleFile(filePath)) {
                gradleFiles.add(downloadUrl);
                logger.info("Found Gradle file: {}", downloadUrl);
            }

            if ("dir".equals(fileType)) {
                gradleFiles.addAll(findGradleFilesRecursively(subdirUrl));
            }
        }
    }

    private boolean isGradleFile(String filePath) {
        return filePath.endsWith("build.gradle") || filePath.endsWith("libraries.gradle");
    }

    private void handleGitHubError(String url, HttpResponse<String> response, int retryCount, int maxRetries) {
        logger.error("Failed to fetch directory contents for {} - Response: {}", url, response.body());
        if (response.statusCode() == 429 || response.statusCode() == 503) {
            retryCount = handleStreamError(retryCount, maxRetries);
        }
    }

    private int handleStreamError(int retryCount, int maxRetries) {
        if (retryCount < maxRetries) {
            retryCount++;
            logger.warn("Retrying due to stream error... ({}/{})", retryCount, maxRetries);
            try {
                Thread.sleep(2000 * retryCount);
            } catch (InterruptedException ignored) {}
        }
        return retryCount;
    }

    private HttpResponse<String> sendGitHubRequest(String url) throws Exception {
        HttpRequest request = buildGitHubRequest(url);
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest buildGitHubRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Java-Gradle-Scanner")
                .header("Authorization", "Bearer " + githubToken)
                .GET()
                .build();
    }

    private static class Repository {
        private final String owner;
        private final String name;
        private final String branch;

        public Repository(String owner, String name, String branch) {
            this.owner = owner;
            this.name = name;
            this.branch = branch;
        }

        public String getName() {
            return name;
        }

        public String getContentsUrl() {
            return "https://api.github.com/repos/" + owner + "/" + name + "/contents?ref=" + branch;
        }
    }
}