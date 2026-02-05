package dev.jbang.jdkdb.scraper.vendors;

import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scraper for Trava OpenJDK 11 releases with DCEVM */
public class TravaJdk11 extends TravaBaseScraper {
	private static final String REPO = "trava-jdk-11-dcevm";
	private static final String JAVA_VERSION = "11";
	private static final Pattern TAG_PATTERN = Pattern.compile("^dcevm-(11\\.[\\d.+]+)$");
	private static final Pattern FILENAME_PATTERN =
			Pattern.compile("^java11-openjdk-dcevm-(linux|osx|windows)-(amd64|arm64|x64)\\.(.*)$");

	public TravaJdk11(ScraperConfig config) {
		super(config);
	}

	@Override
	protected String getRepo() {
		return REPO;
	}

	@Override
	protected String getJavaVersion() {
		return JAVA_VERSION;
	}

	@Override
	protected Pattern getTagPattern() {
		return TAG_PATTERN;
	}

	@Override
	protected Pattern getFilenamePattern() {
		return FILENAME_PATTERN;
	}

	@Override
	protected String extractVersion(String tagName) {
		Matcher tagMatcher = getTagPattern().matcher(tagName);
		if (!tagMatcher.matches()) {
			return null;
		}
		return tagMatcher.group(1);
	}

	@Override
	protected String extractArch(Matcher filenameMatcher) {
		// For Java 11, architecture is in the filename
		return filenameMatcher.group(2);
	}

	@Override
	protected String extractExtension(Matcher filenameMatcher) {
		return filenameMatcher.group(3);
	}

	public static class Discovery implements Scraper.Discovery {
		@Override
		public String name() {
			return VENDOR + "-11";
		}

		@Override
		public String vendor() {
			return VENDOR;
		}

		@Override
		public When when() {
			// Trava is no longer maintained, last release was in 2023
			return When.NEVER;
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new TravaJdk11(config);
		}
	}
}
