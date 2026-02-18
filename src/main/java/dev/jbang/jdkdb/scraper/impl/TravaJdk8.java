package dev.jbang.jdkdb.scraper.impl;

import dev.jbang.jdkdb.scraper.Scraper;
import dev.jbang.jdkdb.scraper.ScraperConfig;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scraper for Trava OpenJDK 8 releases with DCEVM */
public class TravaJdk8 extends TravaBaseScraper {
	private static final String REPO = "trava-jdk-8-dcevm";
	private static final String JAVA_VERSION = "8";
	private static final Pattern TAG_PATTERN = Pattern.compile("^dcevm8u(\\d+)b(\\d+)$");
	private static final Pattern FILENAME_PATTERN =
			Pattern.compile("^java8-openjdk-dcevm-(linux|osx|windows)\\.(tar\\.gz|zip)$");

	public TravaJdk8(ScraperConfig config) {
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
		String update = tagMatcher.group(1);
		String build = tagMatcher.group(2);
		return "8.0." + update + "+" + build;
	}

	@Override
	protected String extractArch(Matcher filenameMatcher) {
		// For Java 8, architecture is not in filename, default to x86_64
		return "x86_64";
	}

	@Override
	protected String extractExtension(Matcher filenameMatcher) {
		return filenameMatcher.group(2);
	}

	public static class Discovery implements Scraper.Discovery {
		@Override
		public String name() {
			return VENDOR + "-8";
		}

		@Override
		public String vendor() {
			return VENDOR;
		}

		@Override
		public When when() {
			// Trava is no longer maintained, last release was in 2023
			return When.IF_MISSING;
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new TravaJdk8(config);
		}
	}
}
