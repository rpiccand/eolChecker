package eolChecker;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        Properties config = new Properties();
        String githubRepo = "";
        String githubToken = "";
        String githubBranch = "main"; // Default branch to `main`
        String mappingFile = "mapping.conf";
        String apiBaseUrl = "https://endoflife.date/api/";
        String csvFilePath = "eol_summary.csv";

        // Step 1: Load Configuration from properties file or CLI arguments
        try (InputStream input = Application.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input != null) {
                config.load(input);
            }

            // Allow CLI arguments to override config properties
            githubRepo = getConfigValue(args, config, "github.repo");
            githubToken = getConfigValue(args, config, "github.token");
            githubBranch = getConfigValue(args, config, "github.branch", "main"); // Default to main

            if (githubRepo.isEmpty() || githubToken.isEmpty()) {
                throw new Exception("Missing 'github.repo' or 'github.token'. Provide via config.properties or CLI.");
            }

            logger.info("Using GitHub Repository/Organization: {}", githubRepo);
            logger.info("Using Target Branch: {}", githubBranch);
        } catch (Exception e) {
            logger.error("Error: {}", e.getMessage());
            return;
        }

        // Step 2: Fetch Gradle files from GitHub (Single repo or all repos)
        GitHubScanner githubScanner = new GitHubScanner(githubToken, githubBranch);
        Map<String, List<String>> repoGradleFiles = githubScanner.scanRepositories(githubRepo);

        if (repoGradleFiles.isEmpty()) {
            logger.warn("No Gradle files found in the repositories.");
            return;
        }

        // Step 3: Extract dependencies
        GradleParser parser = new GradleParser();
        EOLChecker eolChecker = new EOLChecker(apiBaseUrl);
        MappingManager mappingManager = new MappingManager();
        mappingManager.loadMappings(mappingFile);

        for (Map.Entry<String, List<String>> entry : repoGradleFiles.entrySet()) {
            String repoName = entry.getKey();
            List<String> gradleFiles = entry.getValue();

            List<Dependency> dependencies = parser.extractDependencies(gradleFiles);

            for (Dependency dep : dependencies) {
                String mappedProduct = mappingManager.getMappedProduct(dep.getGroup());

                if (mappedProduct != null) {
                    eolChecker.checkEOL(repoName, mappedProduct, dep);
                } else {
                    eolChecker.checkEOL(repoName, dep.getGroup(), dep);
                }
            }
        }

        // Step 4: Save Summary to CSV
        eolChecker.saveSummaryToCSV(csvFilePath);

        // Step 5: Print CSV content
        printCSV(csvFilePath);
    }

    // Helper method to read configuration from CLI arguments or config file
    private static String getConfigValue(String[] args, Properties config, String key) {
        return getConfigValue(args, config, key, "");
    }

    private static String getConfigValue(String[] args, Properties config, String key, String defaultValue) {
        for (String arg : args) {
            if (arg.startsWith("--" + key + "=")) {
                return arg.split("=", 2)[1].trim();
            }
        }
        return config.getProperty(key, defaultValue).trim();
    }

    // Method to print CSV content at the end
    private static void printCSV(String filePath) {
        logger.info("End-of-Life Summary Report (CSV Output):");

        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                logger.info(line);
            }
        } catch (java.io.IOException e) {
            logger.error("Unable to read CSV file - {}", e.getMessage());
        }

        logger.info("CSV Summary saved to: {}", filePath);
    }
}