package dev.jbang.jdkdb.scraper.vendors;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.BaseScraper;
import dev.jbang.jdkdb.scraper.InterruptedProgressException;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import dev.jbang.jdkdb.scraper.TooManyFailuresException;
import java.util.ArrayList;
import java.util.List;

/** Base class for GraalVM scrapers */
public abstract class GraalVmBaseScraper extends BaseScraper {
	private static final String GITHUB_API_BASE = "https://api.github.com/repos";

	public GraalVmBaseScraper(ScraperConfig config) {
		super(config);
	}

	/** Get the GitHub organization name */
	protected abstract String getGithubOrg();

	/** Get the GitHub repository name */
	protected abstract String getGithubRepo();

	/** Check if a release tag should be processed */
	protected abstract boolean shouldProcessTag(String tagName);

	/** Check if an asset should be processed */
	protected abstract boolean shouldProcessAsset(String assetName);

	/** Process an asset and extract metadata */
	protected abstract void processAsset(String tagName, String assetName, List<JdkMetadata> allMetadata)
			throws Exception;

	@Override
	protected List<JdkMetadata> scrape() throws Exception {
		List<JdkMetadata> allMetadata = new ArrayList<>();

		log("Fetching releases from GitHub");
		String releasesUrl =
				String.format("%s/%s/%s/releases?per_page=100", GITHUB_API_BASE, getGithubOrg(), getGithubRepo());
		String json = httpUtils.downloadString(releasesUrl);
		JsonNode releases = readJson(json);

		if (!releases.isArray()) {
			log("No releases found");
			return allMetadata;
		}

		try {
			for (JsonNode release : releases) {
				String tagName = release.get("tag_name").asText();

				if (!shouldProcessTag(tagName)) {
					continue;
				}

				log("Processing release: " + tagName);

				JsonNode assets = release.get("assets");
				if (assets != null && assets.isArray()) {
					for (JsonNode asset : assets) {
						String assetName = asset.get("name").asText();

						if (!shouldProcessAsset(assetName)) {
							continue;
						}

						try {
							processAsset(tagName, assetName, allMetadata);
						} catch (InterruptedProgressException | TooManyFailuresException e) {
							throw e;
						} catch (Exception e) {
							log("Failed to process " + assetName + ": " + e.getMessage());
						}
					}
				}
			}
		} catch (InterruptedProgressException e) {
			log("Reached progress limit, aborting");
		}

		return allMetadata;
	}

	protected JdkMetadata createMetadata(
			String vendor,
			String assetName,
			String releaseType,
			String version,
			String javaVersion,
			String os,
			String arch,
			String ext,
			String url,
			DownloadResult download) {

		JdkMetadata metadata = new JdkMetadata();
		metadata.setVendor(vendor);
		metadata.setFilename(assetName);
		metadata.setReleaseType(releaseType);
		metadata.setVersion(version);
		metadata.setJavaVersion(javaVersion);
		metadata.setJvmImpl("graalvm");
		metadata.setOs(normalizeOs(os));
		metadata.setArchitecture(normalizeArch(arch));
		metadata.setFileType(ext);
		metadata.setImageType("jdk");
		metadata.setFeatures(new ArrayList<>());
		metadata.setUrl(url);
		metadata.setMd5(download.md5());
		metadata.setMd5File(assetName + ".md5");
		metadata.setSha1(download.sha1());
		metadata.setSha1File(assetName + ".sha1");
		metadata.setSha256(download.sha256());
		metadata.setSha256File(assetName + ".sha256");
		metadata.setSha512(download.sha512());
		metadata.setSha512File(assetName + ".sha512");
		metadata.setSize(download.size());

		return metadata;
	}
}
