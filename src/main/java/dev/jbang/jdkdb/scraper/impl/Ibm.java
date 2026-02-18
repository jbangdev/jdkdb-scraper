package dev.jbang.jdkdb.scraper.impl;

import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.BaseScraper;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scraper for IBM JDK releases */
public class Ibm extends BaseScraper {
	private static final String VENDOR = "ibm";
	private static final String BASE_URL = "https://public.dhe.ibm.com/ibmdl/export/pub/systems/cloud/runtimes/java/";

	private static final Pattern VERSION_PATTERN = Pattern.compile("<a href=\"([8]\\.[01]\\.[0-9]+\\.[0-9]+)/\">");
	private static final Pattern ARCH_PATTERN = Pattern.compile("<a href=\"([a-z0-9_]+)/\">");
	private static final Pattern FILE_PATTERN = Pattern.compile("<a href=\"(.*\\.(tgz|rpm))\">");

	public Ibm(ScraperConfig config) {
		super(config);
	}

	@Override
	protected void scrape() throws Exception {
		String indexHtml;
		try {
			log("Fetching index");
			indexHtml = httpUtils.downloadString(BASE_URL);
		} catch (Exception e) {
			fail("Failed to fetch index page", e);
			throw e;
		}

		// Extract JDK versions
		Matcher versionMatcher = VERSION_PATTERN.matcher(indexHtml);
		List<String> jdkVersions = new ArrayList<>();
		while (versionMatcher.find()) {
			jdkVersions.add(versionMatcher.group(1));
		}

		log("Found " + jdkVersions.size() + " JDK versions");

		for (String jdkVersion : jdkVersions) {
			log("Processing JDK version: " + jdkVersion);

			// Fetch architecture list
			String archUrl = BASE_URL + jdkVersion + "/linux/";
			String archHtml;
			try {
				archHtml = httpUtils.downloadString(archUrl);
			} catch (Exception e) {
				warn("Failed to fetch architecture list for " + jdkVersion + ": " + e.getMessage());
				continue;
			}

			Matcher archMatcher = ARCH_PATTERN.matcher(archHtml);
			List<String> architectures = new ArrayList<>();
			while (archMatcher.find()) {
				architectures.add(archMatcher.group(1));
			}

			for (String architecture : architectures) {
				log("Processing architecture: " + architecture);

				// Fetch file list
				String filesUrl = BASE_URL + jdkVersion + "/linux/" + architecture + "/";
				String filesHtml;
				try {
					filesHtml = httpUtils.downloadString(filesUrl);
				} catch (Exception e) {
					warn("Failed to fetch file list for " + jdkVersion + " " + architecture + ": " + e.getMessage());
					continue;
				}

				Matcher fileMatcher = FILE_PATTERN.matcher(filesHtml);
				while (fileMatcher.find()) {
					String ibmFile = fileMatcher.group(1);

					JdkMetadata metadata = processAsset(ibmFile, jdkVersion, architecture);
					if (metadata != null) {
						process(metadata);
					}
				}
			}
		}
	}

	private JdkMetadata processAsset(String ibmFile, String jdkVersion, String architecture) {
		// Skip SFJ files
		if (ibmFile.contains("sfj")) {
			fine("Skipping " + ibmFile + " (sfj)");
			return null;
		}
		if (!ibmFile.endsWith(".tgz") && !ibmFile.endsWith(".rpm")) {
			fine("Skipping " + ibmFile + " (unsupported file type)");
			return null;
		}

		if (metadataExists(ibmFile)) {
			return skipped(ibmFile);
		}

		String fileType;
		if (ibmFile.endsWith(".tgz")) {
			fileType = "tar.gz";
		} else {
			fileType = "rpm";
		}

		String imageType = ibmFile.contains("sdk") ? "jdk" : "jre";
		String url = BASE_URL + jdkVersion + "/linux/" + architecture + "/" + ibmFile;

		// Create metadata using builder
		return JdkMetadata.create()
				.vendor(VENDOR)
				.releaseType("ga")
				.version(jdkVersion)
				.javaVersion(jdkVersion)
				.jvmImpl("openj9")
				.os("linux")
				.arch(normalizeArch(architecture))
				.fileType(fileType)
				.imageType(imageType)
				.url(url)
				.filename(ibmFile);
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
			return new Ibm(config);
		}
	}
}
