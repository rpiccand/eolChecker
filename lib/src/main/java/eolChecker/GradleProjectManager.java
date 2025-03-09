package eolChecker;

import org.gradle.tooling.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.*;

public class GradleProjectManager {

	private static final Logger logger = LoggerFactory.getLogger(GradleProjectManager.class);

	/**
	 * Parses the Gradle `dependencies` output and maps declared dependencies to
	 * resolved artifacts.
	 */
	public Set<String> getFirstLevelDependencies(File projectDir) {

		String dependencies = extractDependencies(projectDir);

		Set<String> firstLevelDeps = new HashSet<>();
		Pattern pattern = Pattern.compile("^[+\\\\]---\\s([^\\s:]+:[^\\s:]+:[^\\s]+)");

		// Read output line by line
		if (dependencies != null) {
			for (String line : dependencies.split("\n")) {
				Matcher matcher = pattern.matcher(line);
				if (matcher.find()) {
					String dependency = matcher.group(1);
					firstLevelDeps.add(dependency);
				}
			}
			return firstLevelDeps;
		}
		return null ;
	}

	public List<File> getGradleProjects(String repoPath) {
		List<File> gradleProjects = new ArrayList<>();
		try {
			Files.walk(new File(repoPath).toPath())
					.filter(path -> path.getFileName().toString().matches("build\\.gradle?"))
					.forEach(path -> gradleProjects.add(path.getParent().toFile()));
		} catch (IOException e) {
			logger.error("Error scanning directory: " + e.getMessage());
		}
		return gradleProjects;
	}

	/**
	 * Runs the Gradle `dependencies` task
	 * 
	 * @return
	 */
	private String extractDependencies(File projectDir) {

		try (ProjectConnection connection = GradleConnector.newConnector().forProjectDirectory(projectDir).connect()) {

			logger.info("Fetching Dependencies from Gradle Build file at {}", projectDir);

			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			connection.newBuild().forTasks("dependencies").setStandardOutput(outputStream).run();

			// Convert output stream to String
			return outputStream.toString(StandardCharsets.UTF_8);

		} catch (Exception ex) {
			logger.warn("Unable to extract dependencies from {} - {}", projectDir, ex.getMessage());
			return null;
		}
	}

}