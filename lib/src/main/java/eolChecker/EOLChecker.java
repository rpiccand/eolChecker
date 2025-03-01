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
	    
	    logger.debug("Calling API: {}", apiUrl);

	    try {
	        HttpRequest request = HttpRequest.newBuilder()
	                .uri(new URI(apiUrl))
	                .header("Accept", "application/json")
	                .GET()
	                .build();

	        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

	        if (response.statusCode() == 404) {
	            logger.error("Product not found in API: {}", product);
	            results.add(new EOLResult(repoName, dependency.toString(), "Unknown", "Product Not Found"));
	            return;
	        }

	        if (response.statusCode() != 200) {
	            results.add(new EOLResult(repoName, dependency.toString(), "Unknown", "API Error"));
	            logger.error("Failed to fetch EOL data for {}: HTTP {} - {}", product, response.statusCode(), response.body());
	            return;
	        }

	        JsonNode jsonResponse = objectMapper.readTree(response.body());
	        String majorVersion = extractMajorVersion(dependency.getVersion());

	        JsonNode cycleEntry = findCycle(jsonResponse, majorVersion);

	        if (cycleEntry == null) {
	            results.add(new EOLResult(repoName, dependency.toString(), "Unknown", "No matching cycle"));
	            logger.warn("No matching cycle found for dependency: {}", dependency);
	            return;
	        }

	        String eolDate = cycleEntry.has("eol") ? cycleEntry.get("eol").asText() : null;

	        if (eolDate == null || eolDate.equals("false")) {
	            results.add(new EOLResult(repoName, dependency.toString(), "No EOL", "Supported"));
	            return;
	        }

	        LocalDate eol = LocalDate.parse(eolDate, DateTimeFormatter.ISO_LOCAL_DATE);
	        LocalDate today = LocalDate.now();

	        if (eol.isBefore(today)) {
	            results.add(new EOLResult(repoName, dependency.toString(), eolDate, "End of Life"));
	        } else {
	            results.add(new EOLResult(repoName, dependency.toString(), eolDate, "Approaching EOL"));
	        }

	    } catch (Exception e) {
	        results.add(new EOLResult(repoName, dependency.toString(), "Unknown", "Error: " + e.getMessage()));
	        logger.error("Error fetching EOL data for {}: {}", product, e.getMessage());
	    }
	}

	private JsonNode findCycle(JsonNode jsonResponse, String majorMinorVersion) {
		logger.debug("Searching for cycle matching: {}", majorMinorVersion);

		for (JsonNode entry : jsonResponse) {
			String cycle = entry.has("cycle") ? entry.get("cycle").asText() : "";

			logger.debug("Checking API cycle: {}", cycle);

			if (cycle.equals(majorMinorVersion)) {
				logger.info("Matched cycle: {} for version {}", cycle, majorMinorVersion);
				return entry;
			}
		}

		logger.warn("No matching cycle found for version: {}", majorMinorVersion);
		return null;
	}

	private String extractMajorVersion(String version) {
	    String[] parts = version.split("\\.");
	    if (parts.length >= 2) {
	        return parts[0] + "." + parts[1]; // Extracts "5.3" from "5.3.9"
	    }
	    return version;
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