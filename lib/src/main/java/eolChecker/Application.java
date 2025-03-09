package eolChecker;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Application {
	private static final Logger logger = LoggerFactory.getLogger(Application.class);

	private static GradleProjectManager depExtractor = new GradleProjectManager();
	private static OutputManager outputManager = new OutputManager();

	private static EOLDataFetcher apiEOLFetcher = new EndOfLifeApiDataFetcher();
	private static EOLProcessor eolProcessor = new EOLProcessor(apiEOLFetcher);
	private static VersionFetcher apiVersionFetcher = new MavenApiVersionFetcher();
	private static List<Dependency> dependencies = new ArrayList<Dependency>();

	private static Properties config = ConfigLoader.loadProperties("config.properties");
	private static Properties productMappings = ConfigLoader.loadProperties("mapping.conf");

	public static void main(String[] args) {
		String repoPath = config.getProperty("repo.path", "").trim();
		logger.info("Using Repository Path: {}", repoPath);

		Optional.ofNullable(depExtractor.getGradleProjects(repoPath)).orElse(List.of()).stream()
				.filter(Objects::nonNull).forEach(gradleProject -> {
					Optional.ofNullable(depExtractor.getFirstLevelDependencies(gradleProject)).orElse(Set.of()).stream()
							.filter(Objects::nonNull).forEach(lib -> {
								Dependency dependency = new Dependency(lib, gradleProject.getAbsolutePath());
								dependency.setProduct((String) productMappings.get(dependency.getGroup()));

								long releaseDateTimeStamp = apiVersionFetcher.getReleaseDate(dependency);
								String latestVersion = apiVersionFetcher.getLatestVersion(dependency.getGroup(),
										dependency.getArtifact());
								long latestReleaseDateTimeStamp = apiVersionFetcher.getReleaseDate(dependency,
										latestVersion);

								dependency.setEOLDate(eolProcessor.getEOLDate(dependency));
								dependency.setReleaseDateFromTimestamp(releaseDateTimeStamp);
								dependency.setLatestVersion(latestVersion);
								dependency.setLatestReleaseDateFromTimestamp(latestReleaseDateTimeStamp);
								dependencies.add(dependency);
							});

				});
		outputManager.saveToCSV(dependencies, "eol_summary.csv");
	}

}