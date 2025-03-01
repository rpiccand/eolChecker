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

		logger.debug("Fetching EOL data from API: {}", apiUrl); // Log actual API call

		try {
			HttpRequest request = HttpRequest.newBuilder().uri(new URI(apiUrl)).header("Accept", "application/json")
					.GET().build();

			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() != 200) {
				results.add(new EOLResult(repoName, dependency.toString(), "Unknown", "API Error"));
				return;
			}

			JsonNode jsonResponse = objectMapper.readTree(response.body());

			// Extract major/minor version based on API response format
			String extractedVersion = extractMajorVersion(dependency.getVersion(), jsonResponse);
			logger.debug("Extracted Version: {} for dependency {}", extractedVersion, dependency);

			// Find matching cycle in the API response
			JsonNode cycleEntry = findCycle(jsonResponse, extractedVersion);

			if (cycleEntry == null) {
				results.add(new EOLResult(repoName, dependency.toString(), "Unknown", "No matching cycle"));
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
		}
	}

	private JsonNode findCycle(JsonNode jsonResponse, String extractedVersion) {
		for (JsonNode entry : jsonResponse) {
			String cycle = entry.has("cycle") ? entry.get("cycle").asText() : "";

			// Compare extracted major.minor or major version against API cycles
			if (cycle.equals(extractedVersion)) {
				return entry;
			}
		}
		return null;
	}

	private String extractMajorVersion(String version, JsonNode jsonResponse) {
		String[] parts = version.split("\\.");

		// Identify if the API contains major.minor or just major
		boolean containsMinorVersions = jsonResponse.has(0) && jsonResponse.get(0).get("cycle").asText().contains(".");

		// If API contains minor versions (e.g., Spring "5.3"), return "major.minor",
		// otherwise just "major"
		if (containsMinorVersions && parts.length >= 2) {
			return parts[0] + "." + parts[1];
		} else {
			return parts[0]; // Default to major version if API cycles don't use minor versions
		}
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