package dev.jbang.jdkdb.util;

import java.util.Comparator;

public class VersionComparator implements Comparator<String> {
	public static final Comparator<String> INSTANCE = new VersionComparator();

	@Override
	public int compare(String v1, String v2) {
		String[] parts1 = v1.split("[.+-]");
		String[] parts2 = v2.split("[.+-]");

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
