package eolChecker;

import java.io.FileWriter;
import java.io.IOException;
import java.net.http.*;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EOLChecker {
    private static final Logger logger = LoggerFactory.getLogger(EOLChecker.class);

    private final String apiBaseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final List<EOLResult> results = new ArrayList<>();

    public EOLChecker(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    public void checkEOL(String repoName, String product, Dependency dependency) {
        String apiUrl = apiBaseUrl + product + ".json";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(apiUrl))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.warn("API Error for product '{}'. Response code: {}", product, response.statusCode());
                results.add(new EOLResult(repoName, dependency.toString(), "Unknown", "API Error"));
                return;
            }

            JsonNode jsonResponse = objectMapper.readTree(response.body());
            String majorVersion = extractMajorVersion(dependency.getVersion());

            JsonNode cycleEntry = findCycle(jsonResponse, majorVersion);

            if (cycleEntry == null) {
                logger.warn("No matching cycle found for dependency: {}", dependency);
                results.add(new EOLResult(repoName, dependency.toString(), "Unknown", "No matching cycle"));
                return;
            }

            String eolDate = cycleEntry.has("eol") ? cycleEntry.get("eol").asText() : null;

            if (eolDate == null || eolDate.equals("false")) {
                logger.info("{} is supported (No EOL).", dependency);
                results.add(new EOLResult(repoName, dependency.toString(), "No EOL", "Supported"));
                return;
            }

            LocalDate eol = LocalDate.parse(eolDate, DateTimeFormatter.ISO_LOCAL_DATE);
            LocalDate today = LocalDate.now();

            if (eol.isBefore(today)) {
                logger.warn("{} has reached End of Life (EOL: {}).", dependency, eolDate);
                results.add(new EOLResult(repoName, dependency.toString(), eolDate, "End of Life"));
            } else {
                logger.info("{} is approaching End of Life (EOL: {}).", dependency, eolDate);
                results.add(new EOLResult(repoName, dependency.toString(), eolDate, "Approaching EOL"));
            }

        } catch (Exception e) {
            logger.error("ERROR checking EOL for {}: {}", dependency, e.getMessage());
            results.add(new EOLResult(repoName, dependency.toString(), "Unknown", "Error: " + e.getMessage()));
        }
    }

    private JsonNode findCycle(JsonNode jsonResponse, String majorVersion) {
        for (JsonNode entry : jsonResponse) {
            String cycle = entry.has("cycle") ? entry.get("cycle").asText() : "";
            if (cycle.equals(majorVersion)) {
                return entry;
            }
        }
        return null;
    }

    private String extractMajorVersion(String version) {
        String[] parts = version.split("\\.");
        return parts.length > 0 ? parts[0] : version;
    }

    public void saveSummaryToCSV(String filePath) {
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.append("Repository,Dependency,EOL Date,Status\n");

            for (EOLResult result : results) {
                writer.append(result.toCSVRow()).append("\n");
            }

            logger.info("Summary saved to: {}", filePath);
        } catch (IOException e) {
            logger.error("ERROR: Failed to write CSV file - {}", e.getMessage());
        }
    }

    private static class EOLResult {
        String repoName;
        String dependency;
        String eolDate;
        String status;

        public EOLResult(String repoName, String dependency, String eolDate, String status) {
            this.repoName = repoName;
            this.dependency = dependency;
            this.eolDate = eolDate;
            this.status = status;
        }

        public String toCSVRow() {
            return String.format("\"%s\",\"%s\",\"%s\",\"%s\"", repoName, dependency, eolDate, status);
        }
    }
}