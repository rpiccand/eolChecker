package eolChecker;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutputManager {

	private static final Logger logger = LoggerFactory.getLogger(OutputManager.class);

	public void saveToCSV(List<Dependency> dependencies, String filePath) {

		try (FileWriter writer = new FileWriter(filePath)) {
			writer.append("Repository,Group,Name,Version,Release Date,EOL Date,Past EOL,Latest Version,Release Date\n");

			for (Dependency dep : dependencies) {
				writer.append(toCSVRow(dep)).append("\n");
			}

			logger.info("Summary saved to: {}", filePath);
		} catch (IOException e) {
			logger.error("ERROR: Failed to write CSV file - {}", e.getMessage());
		}
	}

	private String toCSVRow(Dependency result) {
		return String.format("\"%s\",\"%s\",\"%s\",\"%s\", \"%s\", \"%s\" , \"%s\" ,\"%s\" ,\"%s\"", result.getRepoName(), result.getGroup(),
				result.getArtifact(), result.getVersion(), result.getReleaseDate(), result.getEOLDate(), result.getIsPastEOL(), result.getLatestVersion(), result.getLatestReleaseDate());
	}
}
