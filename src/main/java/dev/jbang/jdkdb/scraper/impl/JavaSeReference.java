package dev.jbang.jdkdb.scraper.impl;

import dev.jbang.jdkdb.scraper.JavaNetBaseScraper;
import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Scraper for Java SE Reference Implementation releases */
public class JavaSeReference extends JavaNetBaseScraper {
	private static final String VENDOR = "java-se-ri";
	private static final String BASE_URL = "https://jdk.java.net/java-se-ri/";

	private static final String[] URL_VERSIONS = {
		"7", "8-MR3", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24",
		"25"
	};

	protected static final Pattern URL_PATTERN =
			Pattern.compile("href=\"(https://download\\.java\\.net/.*/openjdk-[^/]*\\.(tar\\.gz|zip))\"");
	protected static final Pattern FILENAME_PATTERN = Pattern.compile(
			"^openjdk-([0-9ub-]{1,}[^_]*)[-_](linux|osx|windows)-(aarch64|x64-musl|x64|i586).*\\.(tar\\.gz|zip)$");

	public JavaSeReference(ScraperConfig config) {
		super(config);
	}

	@Override
	protected List<String> getIndexUrls() {
		List<String> urls = new ArrayList<>();
		for (String urlVersion : URL_VERSIONS) {
			urls.add(BASE_URL + urlVersion);
		}
		return urls;
	}

	@Override
	protected String getVendor() {
		return VENDOR;
	}

	@Override
	protected Pattern getUrlPattern() {
		return URL_PATTERN;
	}

	@Override
	protected Pattern getFilenamePattern() {
		return FILENAME_PATTERN;
	}

	@Override
	protected boolean shouldProcessUrl(String url) {
		// Skip source files
		return !url.contains("-src") && !url.contains("_src");
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
			return new JavaSeReference(config);
		}
	}
}
