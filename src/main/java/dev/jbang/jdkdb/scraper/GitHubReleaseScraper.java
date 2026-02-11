package dev.jbang.jdkdb.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

/** Base class for scrapers that fetch releases from GitHub */
public abstract class GitHubReleaseScraper extends BaseScraper {
	private static final String GITHUB_API_BASE = "https://api.github.com/repos";
	private static final String GITHUB_ORGS_API_BASE = "https://api.github.com/orgs";

	public GitHubReleaseScraper(ScraperConfig config) {
		super(config);
	}

	/** Get the GitHub organization name */
	protected abstract String getGitHubOrg();

	/** Get the GitHub repository names to scrape */
	protected abstract Iterable<String> getGitHubRepos() throws Exception;

	/** Process a single release and extract metadata */
	protected abstract void processRelease(List<JdkMetadata> allMetadata, JsonNode release) throws Exception;

	/** Return metadata filename based on asset name */
	protected String toMetadataFilename(JsonNode release, JsonNode asset) {
		return asset.get("name").asText();
	}

	/**
	 * Process release assets with common iteration and error handling logic.
	 * This method reduces boilerplate by handling the standard asset processing pattern.
	 * @param allMetadata List to collect metadata results
	 * @param release The GitHub release JSON node
	 * @param assetProcessor Function to process each asset, returns null to skip the asset
	 */
	protected void processReleaseAssets(
			List<JdkMetadata> allMetadata, JsonNode release, AssetProcessor assetProcessor) {
		JsonNode assets = release.get("assets");
		if (assets == null || !assets.isArray()) {
			return;
		}

		for (JsonNode asset : assets) {
			JdkMetadata metadata = assetProcessor.process(release, asset);
			if (metadata != null) {
				allMetadata.add(metadata);
			}
		}
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
		JdkMetadata process(JsonNode release, JsonNode asset);
	}

	@Override
	protected List<JdkMetadata> scrape() throws Exception {
		List<JdkMetadata> allMetadata = new ArrayList<>();

		Iterable<String> repos = getGitHubRepos();
		for (String repo : repos) {
			processRepo(allMetadata, repo);
		}

		return allMetadata;
	}

	protected void processRepo(List<JdkMetadata> allMetadata, String repo) {
		log("Processing repository: " + repo);

		Iterable<JsonNode> releases = getReleasesFromRepos(getGitHubOrg(), repo);
		for (JsonNode release : releases) {
			try {
				processRelease(allMetadata, release);
			} catch (Exception e) {
				String tagName =
						release.has("tag_name") ? release.get("tag_name").asText() : "unknown";
				warn("Failed to process release " + tagName + ": " + e.getMessage());
			}
		}
	}

	/**
	 * Fetch repository names from a GitHub organization that match a given pattern.
	 * Returns a lazy Iterable that fetches pages on-demand as the iterator is consumed.
	 *
	 * @param orgName The GitHub organization name
	 * @param searchString A string to search for in repository names
	 * @param repoNamePattern A regex pattern to match repository names against
	 * @return Iterable of repository names that match the pattern
	 */
	protected Iterable<String> getReposFromOrg(String orgName, String searchString, String repoNamePattern) {
		Pattern pattern = Pattern.compile(repoNamePattern);

		return () -> new PaginatedIterator<String>() {
			@Override
			protected List<String> fetchPage(int pageNumber) throws Exception {
				String url = String.format(
						"%s/%s/repos?type=public&per_page=100&page=%d", GITHUB_ORGS_API_BASE, orgName, pageNumber);
				if (searchString != null && !searchString.isEmpty()) {
					url += "&q=" + searchString;
				}
				log("Fetching repositories from " + url);

				String json = httpUtils.downloadString(url);
				JsonNode reposArray = readJson(json);

				if (reposArray == null || !reposArray.isArray() || reposArray.size() == 0) {
					return null;
				}

				List<String> matched = new ArrayList<>();
				for (JsonNode repo : reposArray) {
					String repoName = repo.get("name").asText();
					if (pattern.matcher(repoName).matches()) {
						matched.add(repoName);
						log("Matched repository: " + repoName);
					}
				}
				return matched;
			}

			@Override
			protected void handleFetchError(Exception e) {
				fail("Could not download list of repositories", e);
			}
		};
	}

	/**
	 * Fetch releases from a GitHub repository. Returns an Iterable of release JSON nodes.
	 *
	 * @param orgName The GitHub organization name
	 * @param repoName The GitHub repository name
	 * @return Iterable of release JSON nodes
	 */
	protected Iterable<JsonNode> getReleasesFromRepos(String orgName, String repoName) {
		return () -> new PaginatedIterator<JsonNode>() {
			@Override
			protected List<JsonNode> fetchPage(int pageNumber) throws Exception {
				String url = String.format(
						"%s/%s/%s/releases?per_page=100&page=%d", GITHUB_API_BASE, orgName, repoName, pageNumber);
				log("Fetching releases from " + url);

				String json = httpUtils.downloadString(url);
				JsonNode releasesArray = readJson(json);

				if (releasesArray == null || !releasesArray.isArray() || releasesArray.size() == 0) {
					return null;
				}

				List<JsonNode> releases = new ArrayList<>();
				for (JsonNode release : releasesArray) {
					releases.add(release);
				}
				return releases;
			}

			@Override
			protected void handleFetchError(Exception e) {
				fail("Could not download list of releases for repository " + orgName + "/" + repoName, e);
			}
		};
	}

	/**
	 * Abstract base class for paginated iteration over GitHub API results.
	 * Handles lazy fetching of pages as the iterator is consumed.
	 */
	protected abstract class PaginatedIterator<T> implements Iterator<T> {
		private int currentPage = 1;
		private Iterator<T> currentBatch = null;
		private boolean hasMore = true;

		@Override
		public boolean hasNext() {
			if (currentBatch != null && currentBatch.hasNext()) {
				return true;
			}
			if (!hasMore) {
				return false;
			}
			fetchNextBatch();
			return currentBatch != null && currentBatch.hasNext();
		}

		@Override
		public T next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			return currentBatch.next();
		}

		private void fetchNextBatch() {
			try {
				List<T> items = fetchPage(currentPage);

				if (items == null || items.isEmpty()) {
					hasMore = false;
					currentBatch = null;
					return;
				}

				currentBatch = items.iterator();
				currentPage++;
			} catch (Exception e) {
				handleFetchError(e);
				hasMore = false;
				currentBatch = null;
			}
		}

		/**
		 * Fetch a page of results from the GitHub API.
		 * @param pageNumber The page number to fetch (1-based)
		 * @return List of items for this page, or null/empty if no more pages
		 */
		protected abstract List<T> fetchPage(int pageNumber) throws Exception;

		/**
		 * Handle errors that occur during page fetching.
		 * @param e The exception that occurred
		 */
		protected abstract void handleFetchError(Exception e);
	}
}
