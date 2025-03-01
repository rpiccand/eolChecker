package eolChecker;

/**
 * Represents a GitHub repository or organization.
 */
public class GitHubRepository {
    private final String owner;
    private final String name;
    private final boolean singleRepo;

    private GitHubRepository(String owner, String name, boolean singleRepo) {
        this.owner = owner;
        this.name = name;
        this.singleRepo = singleRepo;
    }

    public static GitHubRepository fromUrl(String githubRepo) {
        if (!githubRepo.startsWith("https://github.com/")) {
            return null;
        }

        String[] parts = githubRepo.replace("https://github.com/", "").split("/");
        return (parts.length == 1) ? new GitHubRepository(parts[0], null, false) :
                new GitHubRepository(parts[0], parts[1], true);
    }

    public boolean isSingleRepo() {
        return singleRepo;
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getApiReposUrl() {
        return "https://api.github.com/users/" + owner + "/repos";
    }

    public String getApiContentsUrl() {
        return "https://api.github.com/repos/" + owner + "/" + name + "/contents";
    }

    public String getApiContentsUrl(String repoName) {
        return "https://api.github.com/repos/" + owner + "/" + repoName + "/contents";
    }
}