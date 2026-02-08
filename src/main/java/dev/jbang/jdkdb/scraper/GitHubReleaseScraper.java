package dev.jbang.jdkdb.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.util.HttpResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/** Base class for scrapers that fetch releases from GitHub */
public abstract class GitHubReleaseScraper extends BaseScraper {
	private static final String GITHUB_API_BASE = "https://api.github.com/repos";
	private static final String GITHUB_ORGS_API_BASE = "https://api.github.com/orgs";

	private static final String GITHUB_TOKEN_ENV = "GITHUB_TOKEN";
	private static final Logger logger = Logger.getLogger(GitHubReleaseScraper.class.getName());

	/** Cached GitHub token from env or gh CLI; resolved once. */
	private static volatile String githubToken;
	/** Whether we have already attempted to resolve the token. */
	private static volatile boolean tokenResolved;

	/**
	 * Return a GitHub token for API requests, or null. Uses GITHUB_TOKEN env if set; otherwise runs
	 * {@code gh auth token} once and caches the result.
	 */
	protected static String getGitHubToken() {
		if (tokenResolved) {
			return githubToken;
		}
		synchronized (GitHubReleaseScraper.class) {
			if (tokenResolved) {
				return githubToken;
			}
			String fromEnv = System.getenv(GITHUB_TOKEN_ENV);
			if (fromEnv != null && !fromEnv.isBlank()) {
				githubToken = fromEnv.trim();
				logger.info("Using GitHub token from " + GITHUB_TOKEN_ENV);
			} else {
				githubToken = runGhAuthToken();
				if (githubToken != null) {
					logger.info("Using GitHub token from gh auth token");
				} else {
					logger.warning("No GitHub token found (set " + GITHUB_TOKEN_ENV
							+ " or run 'gh auth login'); API rate limits may apply");
				}
			}
			tokenResolved = true;
		}
		return githubToken;
	}

	private static String runGhAuthToken() {
		try {
			Process process = new ProcessBuilder("gh", "auth", "token")
					.redirectErrorStream(false)
					.start();
			String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
			int exit = process.waitFor();
			if (exit == 0 && !output.isBlank()) {
				return output;
			}
		} catch (IOException | InterruptedException e) {
			// ignore: no token available
		}
		return null;
	}

	/** Download from a GitHub API URL, using bearer token if available. */
	protected HttpResult<String> downloadFromGitHub(String url) throws IOException, InterruptedException {
		return httpUtils.downloadString(url, getGitHubToken());
	}

	public GitHubReleaseScraper(ScraperConfig config) {
		super(config);
	}

	/** Get the GitHub organization name */
	protected abstract String getGitHubOrg();

	/** Get the GitHub repository names to scrape */
	protected abstract List<String> getGitHubRepos() throws Exception;

	/** Process a single release and extract metadata */
	protected abstract List<JdkMetadata> processRelease(JsonNode release) throws Exception;

	protected boolean shouldProcessAsset(JsonNode release, JsonNode asset) {
		// By default, process all assets
		return true;
	}

	/** Return metadata filename based on asset name */
	protected String toMetadataFilename(JsonNode release, JsonNode asset) {
		return asset.get("name").asText();
	}

	/**
	 * Process release assets with common iteration and error handling logic.
	 * This method reduces boilerplate by handling the standard asset processing pattern.
	 *
	 * @param release The GitHub release JSON node
	 * @param assetProcessor Function to process each asset, returns null to skip the asset
	 * @return List of successfully processed metadata
	 */
	protected List<JdkMetadata> processReleaseAssets(JsonNode release, AssetProcessor assetProcessor) throws Exception {
		List<JdkMetadata> metadataList = new ArrayList<>();

		JsonNode assets = release.get("assets");
		if (assets == null || !assets.isArray()) {
			return null;
		}

		for (JsonNode asset : assets) {
			if (!shouldProcessAsset(release, asset)) {
				continue;
			}

			String assetName = asset.get("name").asText();
			String metadataFilename = toMetadataFilename(release, asset);
			if (metadataFilename == null) { // trava returns null so to avoid non-informative fail logging explicitly
				log("Skipping " + assetName + " (does not match pattern)");
				metadataList.add(skipped(assetName));
				continue;
			} else if (metadataExists(metadataFilename)) {
				log("Skipping " + assetName + " (already exists)");
				metadataList.add(skipped(assetName));
				continue;
			}

			try {
				JdkMetadata metadata = assetProcessor.process(release, asset);
				if (metadata != null) {
					saveMetadataFile(metadata);
					metadataList.add(metadata);
					success(assetName);
				}
			} catch (InterruptedProgressException | TooManyFailuresException e) {
				throw e;
			} catch (Exception e) {
				fail("Failed to process asset " + assetName + ": " + e.getMessage(), e);
			}
		}

		return metadataList;
	}

	/**
	 * Determine release type from version string or prerelease flag.
	 * Common logic extracted from multiple scrapers.
	 */
	protected String determineReleaseType(String version, boolean isPrerelease) {
		if (isPrerelease) {
			return "ea";
		}
		if (version == null) {
			return "ga";
		}
		String lower = version.toLowerCase();
		if (lower.contains("ea") || lower.contains("alpha") || lower.contains("beta") || lower.contains("-dev")) {
			return "ea";
		}
		return "ga";
	}

	/**
	 * Overload for determining release type from version string only
	 */
	protected String determineReleaseType(String version) {
		return determineReleaseType(version, false);
	}

	/**
	 * Functional interface for processing assets
	 */
	@FunctionalInterface
	protected interface AssetProcessor {
		JdkMetadata process(JsonNode release, JsonNode asset) throws Exception;
	}

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
		var res = downloadFromGitHub(releasesUrl);
		if (!res.isSuccess()) {
			log("Failed to fetch releases: " + res.errorMessage());
			return;
		}
		JsonNode releases = readJson(res.body());

		if (releases.isArray()) {
			log("Found " + releases.size() + " releases");
			for (JsonNode release : releases) {
				try {
					List<JdkMetadata> metadata = processRelease(release);
					if (metadata != null) { // some implementations return null to filter out
						allMetadata.addAll(metadata);
					}
				} catch (InterruptedProgressException | TooManyFailuresException e) {
					throw e;
				} catch (Exception e) {
					String tagName =
							release.has("tag_name") ? release.get("tag_name").asText() : "unknown";
					fail("Failed to process release " + tagName + ": " + e.getMessage(), e);
				}
			}
		}
	}

	/**
	 * Fetch repository names from a GitHub organization that match a given pattern.
	 * This is useful for organizations with multiple repositories following a naming convention.
	 *
	 * @param orgName The GitHub organization name
	 * @param searchString A string to search for in repository names
	 * @param repoNamePattern A regex pattern to match repository names against
	 * @return List of repository names that match the pattern
	 * @throws IOException If the API call fails
	 * @throws InterruptedException If the download is interrupted
	 */
	protected List<String> getGitHubReposFromOrg(String orgName, String searchString, String repoNamePattern)
			throws IOException, InterruptedException {
		List<String> repos = new ArrayList<>();
		Pattern pattern = Pattern.compile(repoNamePattern);

		// GitHub API pagination
		int page = 1;
		boolean hasMore = true;

		while (hasMore) {
			String url =
					String.format("%s/%s/repos?type=public&per_page=100&page=%d", GITHUB_ORGS_API_BASE, orgName, page);
			if (searchString != null && !searchString.isEmpty()) {
				url += "&q=" + searchString;
			}
			log("Fetching repositories from " + url);

			var pageRes = downloadFromGitHub(url);
			if (!pageRes.isSuccess()) {
				fail(url, new java.io.IOException(pageRes.errorMessage()));
				hasMore = false;
				continue;
			}
			JsonNode reposArray = readJson(pageRes.body());

			if (!reposArray.isArray() || reposArray.size() == 0) {
				hasMore = false;
			} else {
				for (JsonNode repo : reposArray) {
					String repoName = repo.get("name").asText();
					if (pattern.matcher(repoName).matches()) {
						repos.add(repoName);
						log("Matched repository: " + repoName);
					}
				}
				page++;
			}
		}

		log("Found " + repos.size() + " matching repositories");
		return repos;
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
