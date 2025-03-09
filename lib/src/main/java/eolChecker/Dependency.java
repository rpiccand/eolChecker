package eolChecker;

import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public class Dependency {

	private String group;
	private String artifact;
	private String version;
	private String product;
	private LocalDate eolDate = null;
	private String repoName;
	private String latestVersion;
	private LocalDate latestReleaseDate ;
	private LocalDate releaseDate;

	public Dependency(String lib) {
		String[] dependency = lib.split(":");
		if (dependency.length == 3) {
			this.group = dependency[0];
			this.artifact = dependency[1];
			this.version = dependency[2];
		}
	}
	
	public Dependency(String lib, String repoPath) {
		this(lib);
		File fullPath = new File(repoPath);
		this.repoName = fullPath.getAbsolutePath();
	}

	public void setReleaseDateFromTimestamp(long unixTimestamp) {
		releaseDate = Instant.ofEpochMilli(unixTimestamp).atZone(ZoneId.of("UTC")).toLocalDate();
	}

	public LocalDate getReleaseDate() {
		return releaseDate;
	}

	public String getGroup() {
		return group;
	}

	public String getArtifact() {
		return artifact;
	}

	public String getVersion() {
		return version;
	}

	@Override
	public String toString() {
		return group + ":" + artifact + ":" + version;
	}

	public String getProduct() {
		return this.product;
	}

	public void setEOLDate(LocalDate eolDate) {
		this.eolDate = eolDate;
	}

	public Boolean getIsPastEOL() {
		if (eolDate != null) {
			return LocalDate.now().isAfter(eolDate);
		} else {
			return null;
		}
	}

	public LocalDate getEOLDate() {
		return eolDate;
	}

	public Object getRepoName() {
		return repoName;
	}

	public void setProduct(String product) {
		this.product = product == null ? this.group : product;
	}

	public void setLatestVersion(String latestVersion) {
		this.latestVersion = latestVersion;
	}

	public String getLatestVersion() {
		return this.latestVersion;
	}
	
	public void setLatestReleaseDateFromTimestamp(long timestamp) {
		latestReleaseDate = Instant.ofEpochMilli(timestamp).atZone(ZoneId.of("UTC")).toLocalDate();
	}
	
	public LocalDate getLatestReleaseDate() {
		return this.latestReleaseDate ;
	}
	
}