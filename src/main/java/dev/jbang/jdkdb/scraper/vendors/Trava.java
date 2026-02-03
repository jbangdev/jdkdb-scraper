package dev.jbang.jdkdb.scraper.vendors;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.DownloadResult;
import dev.jbang.jdkdb.scraper.GitHubReleaseScraper;
import dev.jbang.jdkdb.scraper.InterruptedProgressException;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import dev.jbang.jdkdb.scraper.TooManyFailuresException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scraper for Trava OpenJDK releases with DCEVM across multiple Java versions */
public class Trava extends GitHubReleaseScraper {
	private static final String VENDOR = "trava";

	/** Configuration for each Trava Java version variant */
	private record ProjectConfig(
			String javaVersion,
			String repo,
			Pattern tagPattern,
			Pattern filenamePattern,
			Function<Matcher, String> versionExtractor) {}

	private static final List<ProjectConfig> PROJECTS = List.of(
			new ProjectConfig(
					"8",
					"trava-jdk-8-dcevm",
					Pattern.compile("^dcevm8u(\\d+)b(\\d+)$"),
					Pattern.compile("^java8-openjdk-dcevm-(linux|osx|windows)\\.(.*)$"),
					matcher -> {
						String update = matcher.group(1);
						String build = matcher.group(2);
						return "8.0." + update + "+" + build;
					}),
			new ProjectConfig(
					"11",
					"trava-jdk-11-dcevm",
					Pattern.compile("^dcevm-(11\\.[\\d.+]+)$"),
					Pattern.compile("^java11-openjdk-dcevm-(linux|osx|windows)-(amd64|arm64|x64)\\.(.*)$"),
					matcher -> matcher.group(1)));

	public Trava(ScraperConfig config) {
		super(config);
	}

	@Override
	protected String getGitHubOrg() {
		return "TravaOpenJDK";
	}

	@Override
	protected List<String> getGitHubRepos() {
		return PROJECTS.stream().map(ProjectConfig::repo).toList();
	}

	@Override
	protected List<JdkMetadata> processRelease(JsonNode release) throws Exception {
		List<JdkMetadata> metadataList = new ArrayList<>();

		String tagName = release.get("tag_name").asText();
		log("Processing release: " + tagName);

		// Find matching project config based on tag pattern
		ProjectConfig matchingProject = null;
		Matcher tagMatcher = null;
		for (ProjectConfig project : PROJECTS) {
			tagMatcher = project.tagPattern().matcher(tagName);
			if (tagMatcher.matches()) {
				matchingProject = project;
				break;
			}
		}

		if (matchingProject == null) {
			log("Skipping tag " + tagName + " (does not match any pattern)");
			return null;
		}

		String version = matchingProject.versionExtractor().apply(tagMatcher);

		JsonNode assets = release.get("assets");
		if (assets != null && assets.isArray()) {
			for (JsonNode asset : assets) {
				String contentType = asset.path("content_type").asText("");
				String assetName = asset.get("name").asText();

				// Skip source files and jar files
				if (assetName.contains("_source") || assetName.endsWith(".jar")) {
					continue;
				}

				// Only process application files
				if (contentType.startsWith("application")) {
					try {
						JdkMetadata metadata = processAsset(matchingProject, tagName, assetName, version);
						if (metadata != null) {
							saveMetadataFile(metadata);
							metadataList.add(metadata);
							success(assetName);
						}
					} catch (InterruptedProgressException | TooManyFailuresException e) {
						throw e;
					} catch (Exception e) {
						log("Failed to process " + assetName + ": " + e.getMessage());
					}
				}
			}
		}

		return metadataList;
	}

	private JdkMetadata processAsset(ProjectConfig project, String tagName, String assetName, String version)
			throws Exception {

		Matcher filenameMatcher = project.filenamePattern().matcher(assetName);
		if (!filenameMatcher.matches()) {
			log("Skipping " + assetName + " (does not match pattern)");
			return null;
		}

		String os = filenameMatcher.group(1);
		// For Java 8, architecture is not in filename, default to x86_64
		// For Java 11, architecture is in the filename
		String arch;
		String ext;
		if (project.javaVersion().equals("8")) {
			arch = "x86_64";
			ext = filenameMatcher.group(2);
		} else {
			arch = filenameMatcher.group(2);
			ext = filenameMatcher.group(3);
		}

		String metadataFilename = VENDOR + "-" + version + "-" + os + "-" + arch + "." + ext;

		if (metadataExists(metadataFilename)) {
			log("Skipping " + metadataFilename + " (already exists)");
			return null;
		}

		String url = String.format(
				"https://github.com/%s/%s/releases/download/%s/%s", getGitHubOrg(), project.repo(), tagName, assetName);

		// Download and compute hashes
		DownloadResult download = downloadFile(url, metadataFilename);

		// Create metadata using builder
		return JdkMetadata.builder()
				.vendor(VENDOR)
				.releaseType("ga")
				.version(version)
				.javaVersion(version)
				.jvmImpl("hotspot")
				.os(normalizeOs(os))
				.arch(normalizeArch(arch))
				.fileType(ext)
				.imageType("jdk")
				.url(url)
				.download(assetName, download)
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
			return new Trava(config);
		}
	}
}
