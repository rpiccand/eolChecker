package eolChecker;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);
    
    private static String githubRepo = "";
    private static String githubToken = "";

    public static void main(String[] args) {
        Properties config = new Properties();

        String mappingFile = "mapping.conf";
        String apiBaseUrl = "https://endoflife.date/api/";
        String csvFilePath = "eol_summary.csv"; // CSV File Path

        // Load config from classpath
        loadConfig(config);

        // Fetch Gradle files from GitHub (Single repo or all repos)
        GitHubScanner githubScanner = new GitHubScanner(githubToken);
        Map<String, List<String>> repoGradleFiles = githubScanner.scanRepositories(githubRepo);

        if (repoGradleFiles.isEmpty()) {
            logger.warn("No Gradle files found in the repositories.");
            return;
        }

        // Extract dependencies
        GradleParser parser = new GradleParser();
        EOLChecker eolChecker = new EOLChecker(apiBaseUrl);
        MappingManager mappingManager = new MappingManager();
        mappingManager.loadMappings(mappingFile);

        int totalDependenciesChecked = 0;

        for (Map.Entry<String, List<String>> entry : repoGradleFiles.entrySet()) {
            String repoName = entry.getKey();
            List<String> gradleFiles = entry.getValue();

            List<Dependency> dependencies = parser.extractDependencies(gradleFiles);
            totalDependenciesChecked += dependencies.size();

            for (Dependency dep : dependencies) {
                String mappedProduct = mappingManager.getMappedProduct(dep.getGroup());

                if (mappedProduct != null) {
                    eolChecker.checkEOL(repoName, mappedProduct, dep);
                } else {
                    eolChecker.checkEOL(repoName, dep.getGroup(), dep);
                }
            }
        }

        // Save Summary to CSV
        eolChecker.saveSummaryToCSV(csvFilePath);

        // Print CSV content as a table
        printCSV(csvFilePath, totalDependenciesChecked);
    }

	private static void loadConfig(Properties config) {
		try (InputStream input = Application.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                throw new Exception("config.properties not found in classpath.");
            }
            config.load(input);

            githubRepo = config.getProperty("github.repo", "").trim();
            githubToken = config.getProperty("github.token", "").trim();

            if (githubRepo.isEmpty() || githubToken.isEmpty()) {
                throw new Exception("Missing 'github.repo' or 'github.token' in config.properties.");
            }

            logger.info("Using GitHub Repository/Organization: {}", githubRepo);
        } catch (Exception e) {
            logger.error("{}", e.getMessage());
            return;
        }
	}

    // Method to print CSV content in a formatted table
    private static void printCSV(String filePath, int totalDependenciesChecked) {
        logger.info("\nEnd-of-Life Summary Report (CSV Output)\n");

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            boolean isHeader = true;

            while ((line = br.readLine()) != null) {
                if (isHeader) {
                    logger.info("————————————————————————————————————————————————————————————————————————");
                    logger.info("| Repository       | Group                  | Artifact     | Version  | Cycle | EOL Date  | Status    |");
                    logger.info("————————————————————————————————————————————————————————————————————————");
                    isHeader = false;
                }

                String[] columns = line.split(",");
                if (columns.length >= 7) {
                    logger.info("| {:<15} | {:<22} | {:<12} | {:<8} | {:<5} | {:<10} | {:<9} |",
                            columns[0], columns[1], columns[2], columns[3], columns[4], columns[5], columns[6]);
                }
            }
            logger.info("————————————————————————————————————————————————————————————————————————");

        } catch (IOException e) {
            logger.error("Unable to read CSV file - {}", e.getMessage());
        }

        logger.info("Total Dependencies Checked: {}", totalDependenciesChecked);
        logger.info("CSV Summary saved to: {}", filePath);
    }
}