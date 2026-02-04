package dev.jbang.jdkdb.scraper.vendors;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.DownloadResult;
import dev.jbang.jdkdb.scraper.GitHubReleaseScraper;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scraper for Gluon GraalVM releases */
public class GluonGraalVm extends GitHubReleaseScraper {
	private static final String VENDOR = "gluon-graalvm";
	private static final Pattern FILENAME_PATTERN = Pattern.compile(
			"^graalvm-svm-java([0-9]+)-(linux|darwin|windows)-(aarch64|x86_64|amd64)-([0-9.]+(?:-dev)?)\\.(zip|tar\\.gz)$");

	public GluonGraalVm(ScraperConfig config) {
		super(config);
	}

	@Override
	protected String getGitHubOrg() {
		return "gluonhq";
	}

	@Override
	protected List<String> getGitHubRepos() {
		return List.of("graal");
	}

	@Override
	protected List<JdkMetadata> processRelease(JsonNode release) throws Exception {
		String tagName = release.get("tag_name").asText();
		boolean isPrerelease = release.get("prerelease").asBoolean();

		if (!shouldProcessTag(tagName)) {
			return null;
		}

		return processReleaseAssets(release, asset -> {
			String assetName = asset.get("name").asText();

			if (!shouldProcessAsset(assetName)) {
				return null;
			}

			return processAsset(tagName, assetName, isPrerelease);
		});
	}

	@Override
	protected boolean shouldProcessAsset(String assetName) {
		// Skip non-matching files
		return assetName.startsWith("graalvm-svm-") && !assetName.endsWith(".sha256");
	}

	private JdkMetadata processAsset(String tagName, String filename, boolean isPrerelease) throws Exception {
		Matcher matcher = FILENAME_PATTERN.matcher(filename);
		if (!matcher.matches()) {
			log("Filename doesn't match pattern: " + filename);
			return null;
		}

		String javaVersion = matcher.group(1);
		String os = matcher.group(2);
		String arch = matcher.group(3);
		String version = matcher.group(4);
		String ext = matcher.group(5);

		String url = String.format("https://github.com/gluonhq/graal/releases/download/%s/%s", tagName, filename);

		// Download and compute hashes
		DownloadResult download = downloadFile(url, filename);

		// Create metadata using builder
		return JdkMetadata.builder()
				.vendor(VENDOR)
				.releaseType(determineReleaseType(version, isPrerelease))
				.version(version)
				.javaVersion(javaVersion)
				.jvmImpl("graalvm")
				.os(normalizeOs(os))
				.arch(normalizeArch(arch))
				.fileType(ext)
				.imageType("jdk")
				.features(List.of("native-image", "substrate-vm"))
				.url(url)
				.download(filename, download)
				.build();
	}

	public static class Discovery implements Scraper.Discovery {
		@Override
		public String name() {
			return VENDOR;
		}

		@Override
		public String vendor() {
			return VENDOR;
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new GluonGraalVm(config);
		}
	}
}
