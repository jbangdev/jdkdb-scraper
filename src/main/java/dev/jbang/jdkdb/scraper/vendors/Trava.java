package dev.jbang.jdkdb.scraper.vendors;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jbang.jdkdb.model.JdkMetadata;
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
		List<JdkMetadata> metadata = new ArrayList<>();

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
			return metadata;
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
						processAsset(matchingProject, tagName, assetName, version, metadata);
					} catch (InterruptedProgressException | TooManyFailuresException e) {
						throw e;
					} catch (Exception e) {
						log("Failed to process " + assetName + ": " + e.getMessage());
					}
				}
			}
		}

		return metadata;
	}

	private void processAsset(
			ProjectConfig project, String tagName, String assetName, String version, List<JdkMetadata> allMetadata)
			throws Exception {

		Matcher filenameMatcher = project.filenamePattern().matcher(assetName);
		if (!filenameMatcher.matches()) {
			log("Skipping " + assetName + " (does not match pattern)");
			return;
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
			return;
		}

		String url = String.format(
				"https://github.com/%s/%s/releases/download/%s/%s", getGitHubOrg(), project.repo(), tagName, assetName);

		// Download and compute hashes
		DownloadResult download = downloadFile(url, metadataFilename);

		// Create metadata
		JdkMetadata metadata = new JdkMetadata();
		metadata.setVendor(VENDOR);
		metadata.setFilename(assetName);
		metadata.setReleaseType("ga");
		metadata.setVersion(version);
		metadata.setJavaVersion(version);
		metadata.setJvmImpl("hotspot");
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

		allMetadata.add(metadata);
		saveMetadataFile(metadata);
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
