package dev.jbang.jdkdb.util;

import java.util.Optional;

/**
 * Result of an HTTP request. Carries status code and response body for both success and failure so
 * error bodies (API messages, HTML, JSON) can be used in logging and exception messages.
 *
 * @param <T> type of body (String for downloadString/downloadFile)
 */
public record HttpResult<T>(int statusCode, T body) {

	public boolean isSuccess() {
		return statusCode >= 200 && statusCode < 300;
	}

	/**
	 * Returns a single string for logging or exception messages when {@code !isSuccess()}. Includes
	 * status code and first line of body (trimmed, length-capped) when body is present.
	 */
	public String errorMessage() {
		String base = "HTTP " + statusCode;
		if (body == null) {
			return base;
		}
		String trimmed = body.toString().trim();
		if (trimmed.isEmpty()) {
			return base;
		}
		String firstLine = trimmed.lines().findFirst().orElse(trimmed);
		int maxLen = 200;
		String snippet = firstLine.length() > maxLen ? firstLine.substring(0, maxLen) + "..." : firstLine;
		return base + ": " + snippet;
	}

	/** Returns Optional.of(body) when success else Optional.empty(). */
	public Optional<T> bodyIfSuccess() {
		return isSuccess() ? Optional.ofNullable(body) : Optional.empty();
	}
}
