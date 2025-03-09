package eolChecker;

public interface VersionFetcher {
	

	long getReleaseDate(Dependency dependency);

	long getReleaseDate(Dependency dependency, String version);

	String getLatestVersion(String group, String artifact);

}
