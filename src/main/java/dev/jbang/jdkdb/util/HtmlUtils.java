package dev.jbang.jdkdb.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utility class for parsing HTML content */
public class HtmlUtils {
	// Pattern to match href attributes in HTML links
	private static final Pattern HREF_PATTERN = Pattern.compile("href=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

	/** Extract all href links from HTML string */
	public static List<String> extractHrefs(String html) {
		List<String> hrefs = new ArrayList<>();
		Matcher matcher = HREF_PATTERN.matcher(html);

		while (matcher.find()) {
			String href = matcher.group(1);
			hrefs.add(href);
		}

		return hrefs;
	}

	/** Extract filename from a URL or path */
	public static String extractFilename(String path) {
		if (path == null || path.isEmpty()) {
			return "";
		}

		// Remove query parameters
		int queryIndex = path.indexOf('?');
		if (queryIndex != -1) {
			path = path.substring(0, queryIndex);
		}

		// Get the last part of the path
		int lastSlash = path.lastIndexOf('/');
		if (lastSlash != -1 && lastSlash < path.length() - 1) {
			return path.substring(lastSlash + 1);
		}

		return path;
	}
}
