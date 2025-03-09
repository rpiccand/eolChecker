package eolChecker;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MavenApiVersionFetcher implements VersionFetcher {

	private String latestVersion = "https://search.maven.org/solrsearch/select?q=g:%s+AND+a:%s&rows=1&wt=json";
	private String specificVersion = "https://search.maven.org/solrsearch/select?q=g:%s+AND+a:%s+v:%s&rows=1&wt=json";

	private static final Logger logger = LoggerFactory.getLogger(MavenApiVersionFetcher.class);

	@Override
	public String getLatestVersion(String group, String artifact) {

		String queryUrl = String.format(latestVersion,group, artifact);

		try {
			// Create HTTP request
			URL url = new URL(queryUrl);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setRequestProperty("Accept", "application/json");

			// Read response
			BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			StringBuilder response = new StringBuilder();
			String line;
			while ((line = br.readLine()) != null) {
				response.append(line);
			}
			br.close();
			conn.disconnect();

			// Extract latest version using regex
			Pattern pattern = Pattern.compile("\"latestVersion\":\"([^\"]+)\"");
			Matcher matcher = pattern.matcher(response.toString());

			if (matcher.find()) {
				return  matcher.group(1); // Extract latest version
			}

		} catch (Exception e) {
			logger.error("Error fetching latest version: " + e.getMessage());
		}
		return null;
	}

	@Override
	public long getReleaseDate(Dependency dependency) {
		return getReleaseDate(String.format(specificVersion, dependency.getGroup(), dependency.getArtifact(), dependency.getVersion()));
	}
	
	@Override
	public long getReleaseDate(Dependency dependency, String version) {
		return getReleaseDate(String.format(specificVersion, dependency.getGroup(), dependency.getArtifact(), version));
	}

	private long getReleaseDate(String queryUrl) {
		try {
			// Create HTTP request
			URL url = new URL(queryUrl);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setRequestProperty("Accept", "application/json");

			// Read response
			BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			StringBuilder response = new StringBuilder();
			String line;
			while ((line = br.readLine()) != null) {
				response.append(line);
			}
			br.close();
			conn.disconnect();

			// Extract latest version using regex
			Pattern pattern = Pattern.compile("\"timestamp\":\\s*(\\d+)");
			Matcher matcher = pattern.matcher(response.toString());

			if (matcher.find()) {
				return Long.parseLong(matcher.group(1));
			}
		} catch (Exception e) {
			logger.error("Error fetching latest version: " + e.getMessage());
		}
		return 0;
	}
	

}
