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
 * GitHubScanner fetches repositories and Gradle files from GitHub.
 */
public class GitHubScanner {
    private static final Logger logger = LoggerFactory.getLogger(GitHubScanner.class);
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String githubToken;

    public GitHubScanner(String githubToken) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.githubToken = githubToken;
    }

    public Map<String, List<String>> scanRepositories(String githubRepo) {
        GitHubRepository repository = GitHubRepository.fromUrl(githubRepo);
        if (repository == null) {
            logger.error("Invalid GitHub URL format. Must be 'https://github.com/user' or 'https://github.com/user/repo/'");
            return Collections.emptyMap();
        }

        return repository.isSingleRepo() ? scanSingleRepository(repository) : scanAllRepositories(repository);
    }

    private Map<String, List<String>> scanSingleRepository(GitHubRepository repo) {
        Map<String, List<String>> repoGradleFiles = new HashMap<>();
        logger.info("Scanning single repository: {}/{}", repo.getOwner(), repo.getName());

        String repoContentsUrl = repo.getApiContentsUrl();
        List<String> gradleFiles = findGradleFilesRecursively(repoContentsUrl);
        if (!gradleFiles.isEmpty()) {
            repoGradleFiles.put(repo.getName(), gradleFiles);
        } else {
            logger.warn("No Gradle files found in repository: {}", repo.getName());
        }
        return repoGradleFiles;
    }

    private Map<String, List<String>> scanAllRepositories(GitHubRepository repo) {
        Map<String, List<String>> repoGradleFiles = new HashMap<>();
        logger.info("Scanning all repositories for organization/user: {}", repo.getOwner());

        String apiUrl = repo.getApiReposUrl();
        JsonNode repositories = fetchJsonFromGitHub(apiUrl);
        if (repositories == null || !repositories.isArray()) {
            logger.error("Failed to fetch repositories.");
            return repoGradleFiles;
        }

        for (JsonNode repoNode : repositories) {
            String repoName = repoNode.get("name").asText();
            String repoContentsUrl = repo.getApiContentsUrl(repoName);

            List<String> gradleFiles = findGradleFilesRecursively(repoContentsUrl);
            if (!gradleFiles.isEmpty()) {
                repoGradleFiles.put(repoName, gradleFiles);
            }
        }
        return repoGradleFiles;
    }

    private List<String> findGradleFilesRecursively(String repoContentsUrl) {
        List<String> gradleFiles = new ArrayList<>();
        JsonNode directoryContents = fetchJsonFromGitHub(repoContentsUrl);

        if (directoryContents == null || !directoryContents.isArray()) {
            logger.error("Unexpected API response format for {}", repoContentsUrl);
            return gradleFiles;
        }

        for (JsonNode fileNode : directoryContents) {
            processFileNode(fileNode, gradleFiles);
        }
        return gradleFiles;
    }

    private void processFileNode(JsonNode fileNode, List<String> gradleFiles) {
        String fileType = fileNode.get("type").asText();
        String filePath = fileNode.get("path").asText();
        String downloadUrl = fileNode.get("download_url").asText(null);

        if ("file".equals(fileType) && (filePath.endsWith("build.gradle") || filePath.endsWith("libraries.gradle"))) {
            gradleFiles.add(downloadUrl);
            logger.info("Found Gradle file: {}", downloadUrl);
        } else if ("dir".equals(fileType)) {
            String subdirContentsUrl = fileNode.get("url").asText();
            gradleFiles.addAll(findGradleFilesRecursively(subdirContentsUrl));
        }
    }

    private JsonNode fetchJsonFromGitHub(String url) {
        HttpRequest request = buildGitHubRequest(url);
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                logger.error("GitHub API error: {} - {}", url, response.body());
                return null;
            }
            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            logger.error("Exception while fetching {}: {}", url, e.getMessage());
            return null;
        }
    }

    private HttpRequest buildGitHubRequest(String url) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Java-Gradle-Scanner");

        if (githubToken != null && !githubToken.isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + githubToken);
        }
        return requestBuilder.GET().build();
    }
}