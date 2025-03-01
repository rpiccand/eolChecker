package eolChecker;

public class Dependency {
    private final String group;
    private final String artifact;
    private final String version;

    public Dependency(String group, String artifact, String version) {
        this.group = group;
        this.artifact = artifact;
        this.version = version;
    }

    public String getGroup() { return group; }
    public String getArtifact() { return artifact; }
    public String getVersion() { return version; }

    @Override
    public String toString() {
        return group + ":" + artifact + ":" + version;
    }
}