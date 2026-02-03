package dev.jbang.jdkdb.scraper.vendors;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.DownloadResult;
import dev.jbang.jdkdb.scraper.GitHubReleaseScraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.List;

/** Base class for GraalVM scrapers */
public abstract class GraalVmBaseScraper extends GitHubReleaseScraper {

	public GraalVmBaseScraper(ScraperConfig config) {
		super(config);
	}

	@Override
	protected List<String> getGitHubRepos() {
		return List.of(getGithubRepo());
	}

	/** Get the GitHub organization name */
	protected abstract String getGithubOrg();

	/** Get the GitHub repository name */
	protected abstract String getGithubRepo();

	@Override
	protected String getGitHubOrg() {
		return getGithubOrg();
	}

	/** Check if a release tag should be processed */
	protected abstract boolean shouldProcessTag(String tagName);

	/** Check if an asset should be processed */
	protected abstract boolean shouldProcessAsset(String assetName);

	/** Process an asset and extract metadata - returns metadata or null to skip */
	protected abstract JdkMetadata processAsset(String tagName, String assetName) throws Exception;

	@Override
	protected List<JdkMetadata> processRelease(JsonNode release) throws Exception {
		String tagName = release.get("tag_name").asText();

		if (!shouldProcessTag(tagName)) {
			return List.of();
		}

		return processReleaseAssets(release, asset -> {
			String assetName = asset.get("name").asText();

			if (!shouldProcessAsset(assetName)) {
				return null;
			}

			return processAsset(tagName, assetName);
		});
	}

	/**
	 * @deprecated Use the createMetadata method from GitHubReleaseScraper with MetadataBuilder instead
	 */
	@Deprecated
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

		return JdkMetadata.builder()
				.vendor(vendor)
				.releaseType(releaseType)
				.version(version)
				.javaVersion(javaVersion)
				.jvmImpl("graalvm")
				.os(normalizeOs(os))
				.arch(normalizeArch(arch))
				.fileType(ext)
				.imageType("jdk")
				.url(url)
				.download(assetName, download)
				.build();
	}
}
