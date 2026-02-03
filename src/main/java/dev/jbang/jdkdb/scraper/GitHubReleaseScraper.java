package dev.jbang.jdkdb.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Base class for scrapers that fetch releases from GitHub */
public abstract class GitHubReleaseScraper extends BaseScraper {
	private static final String GITHUB_API_BASE = "https://api.github.com/repos";

	public GitHubReleaseScraper(ScraperConfig config) {
		super(config);
	}

	/** Get the GitHub organization name */
	protected abstract String getGitHubOrg();

	/** Get the GitHub repository names to scrape */
	protected abstract List<String> getGitHubRepos();

	/** Process a single release and extract metadata */
	protected abstract List<JdkMetadata> processRelease(JsonNode release) throws Exception;

	@Override
	protected List<JdkMetadata> scrape() throws Exception {
		List<JdkMetadata> allMetadata = new ArrayList<>();

		try {
			for (String repo : getGitHubRepos()) {
				processRepo(allMetadata, repo);
			}
		} catch (InterruptedProgressException e) {
			log("Reached progress limit, aborting");
		}

		return allMetadata;
	}

	protected void processRepo(List<JdkMetadata> allMetadata, String repo) throws IOException, InterruptedException {
		log("Processing repository: " + repo);

		String releasesUrl = String.format("%s/%s/%s/releases?per_page=100", GITHUB_API_BASE, getGitHubOrg(), repo);

		log("Fetching releases from " + releasesUrl);
		String json = httpUtils.downloadString(releasesUrl);

		JsonNode releases = readJson(json);

		if (releases.isArray()) {
			log("Found " + releases.size() + " releases");
			for (JsonNode release : releases) {
				try {
					List<JdkMetadata> metadata = processRelease(release);
					allMetadata.addAll(metadata);
				} catch (InterruptedProgressException | TooManyFailuresException e) {
					throw e;
				} catch (Exception e) {
					String tagName =
							release.has("tag_name") ? release.get("tag_name").asText() : "unknown";
					log("Failed to process release " + tagName + ": " + e.getMessage());
				}
			}
		}
	}

	/** Parse filename to extract metadata components */
	protected static class FilenameParser {
		public String version;
		public String os;
		public String arch;
		public String extension;
		public String imageType;

		public boolean isValid() {
			return version != null && os != null && arch != null && extension != null;
		}
	}
}
