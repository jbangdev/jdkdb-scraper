package dev.jbang.jdkdb.util;

import java.util.Comparator;
import java.util.regex.Pattern;

public class VersionComparator implements Comparator<String> {
	public static final Comparator<String> INSTANCE = new VersionComparator();
	private static final Pattern VERSION_PREFIX = Pattern.compile("^(\\d+(?:\\.\\d+)*)(.*)$");

	@Override
	public int compare(String v1, String v2) {
		if (v1 == null && v2 == null) return 0;
		if (v1 == null) return -1;
		if (v2 == null) return 1;
		var version1 = VERSION_PREFIX.matcher(v1);
		var version2 = VERSION_PREFIX.matcher(v2);
		if (version1.matches() && version2.matches()) {
			// Compare the Java version before considering build numbers or vendor suffixes.
			// In particular, 21+35 precedes 21.0.1, and 17.0.8.1b1080.1 precedes 17.0.14b1367.22.
			String[] numbers1 = version1.group(1).split("\\.");
			String[] numbers2 = version2.group(1).split("\\.");
			for (int i = 0; i < Math.max(numbers1.length, numbers2.length); i++) {
				int number1 = i < numbers1.length ? parseToInt(numbers1[i], 0) : 0;
				int number2 = i < numbers2.length ? parseToInt(numbers2[i], 0) : 0;
				int cmp = Integer.compare(number1, number2);
				if (cmp != 0) return cmp;
			}
			return compareParts(version1.group(2), version2.group(2));
		}
		return compareParts(v1, v2);
	}

	private int compareParts(String v1, String v2) {
		// Keep numeric parts of vendor builds numeric (b1000 must follow b999).
		String[] parts1 = v1.split("[.+-]|(?<=\\d)(?=\\D)|(?<=\\D)(?=\\d)");
		String[] parts2 = v2.split("[.+-]|(?<=\\d)(?=\\D)|(?<=\\D)(?=\\d)");

		int length = Math.max(parts1.length, parts2.length);
		for (int i = 0; i < length; i++) {
			int p1 = i < parts1.length ? parseToInt(parts1[i], -1) : -2;
			int p2 = i < parts2.length ? parseToInt(parts2[i], -1) : -2;
			if (p1 == -1 && p2 == -1) {
				// Both are non-integers, compare as strings
				int cmp = parts1[i].compareTo(parts2[i]);
				if (cmp != 0) {
					return cmp;
				}
			} else if (p1 != p2) {
				return Integer.compare(p1, p2);
			}
		}
		return parts1.length == parts2.length ? 0 : Integer.compare(parts1.length, parts2.length);
	}

	private static int parseToInt(String number, int defaultValue) {
		if (number != null) {
			try {
				return Integer.parseInt(number);
			} catch (NumberFormatException ex) {
				// Ignore
			}
		}
		return defaultValue;
	}
}
