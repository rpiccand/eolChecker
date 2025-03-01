package eolChecker;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class Application {
	public static void main(String[] args) {
        Properties config = new Properties();
        String githubUser = "";
        String mappingFile = "mapping.conf";
        String apiBaseUrl = "https://endoflife.date/api/";

        // Load config from classpath
        try (InputStream input = Application.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                throw new Exception("config.properties not found in classpath.");
            }
            config.load(input);
            githubUser = config.getProperty("github.user", "").trim();

            if (githubUser.isEmpty()) {
                throw new Exception("No GitHub user specified in config.properties.");
            }

            System.out.println("✅ Using GitHub User: " + githubUser);
        } catch (Exception e) {
            System.err.println("❌ ERROR: " + e.getMessage());
            return;
        }

        // Step 1: Fetch Gradle files from GitHub (WITH REPO NAMES)
        GitHubScanner githubScanner = new GitHubScanner(config);
        Map<String, List<String>> repoGradleFiles = githubScanner.scanRepositoriesWithRepoNames(githubUser);

        if (repoGradleFiles.isEmpty()) {
            System.err.println("⚠️ No Gradle files found in the repositories.");
            return;
        }

        // Step 2: Extract dependencies
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

        // Step 5: Save Summary to CSV
        eolChecker.saveSummaryToCSV("eol_summary.csv");
    }
	
}