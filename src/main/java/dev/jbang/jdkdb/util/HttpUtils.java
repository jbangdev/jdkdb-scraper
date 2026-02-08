package dev.jbang.jdkdb.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.logging.Logger;

/** Utility class for HTTP operations */
public class HttpUtils {
	private static final Logger logger = Logger.getLogger(HttpUtils.class.getName());
	private final HttpClient httpClient;

	public HttpUtils() {
		this.httpClient = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NORMAL)
				.connectTimeout(Duration.ofSeconds(30))
				.build();
	}

	/** Download a file from a URL to a local path. Returns result with body null on success, error body on non-2xx. */
	public HttpResult<String> downloadFile(String url, Path destination) throws IOException, InterruptedException {
		HttpRequest request =
				HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

		HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
		int statusCode = response.statusCode();
		if (statusCode >= 200 && statusCode < 300) {
			try (InputStream inputStream = response.body()) {
				Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
			}
			return new HttpResult<>(statusCode, null);
		}
		// Read error body for errorMessage(); do not write to destination
		String errorBody;
		try (InputStream inputStream = response.body()) {
			errorBody = new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		}
		logger.severe(
				"HTTP " + statusCode + " for " + url + ": " + new HttpResult<>(statusCode, errorBody).errorMessage());
		return new HttpResult<>(statusCode, errorBody);
	}

	/** Download content from a URL as a string. Returns result with body for any status; do not throw for non-2xx. */
	public HttpResult<String> downloadString(String url) throws IOException, InterruptedException {
		return downloadString(url, null);
	}

	/**
	 * Download content from a URL as a string, optionally with a Bearer token (e.g. for GitHub API).
	 * If bearerToken is non-null and non-empty, adds "Authorization: Bearer &lt;token&gt;" header.
	 */
	public HttpResult<String> downloadString(String url, String bearerToken) throws IOException, InterruptedException {
		HttpRequest.Builder builder =
				HttpRequest.newBuilder().uri(URI.create(url)).GET();
		if (bearerToken != null && !bearerToken.isBlank()) {
			builder.header("Authorization", "Bearer " + bearerToken.trim());
		}
		HttpRequest request = builder.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		int statusCode = response.statusCode();
		HttpResult<String> result = new HttpResult<>(statusCode, response.body());
		if (!result.isSuccess()) {
			logger.severe("HTTP " + statusCode + " for " + url + ": " + result.errorMessage());
		}
		return result;
	}

	/** Check if a URL exists (returns 2xx status code) */
	public boolean urlExists(String url) {
		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(url))
					.method("HEAD", HttpRequest.BodyPublishers.noBody())
					.build();

			HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
			int statusCode = response.statusCode();
			return statusCode >= 200 && statusCode < 300;
		} catch (IOException | InterruptedException e) {
			return false;
		}
	}

	/** Alias for urlExists - check if a URL exists (returns 2xx status code) */
	public boolean checkUrlExists(String url) {
		return urlExists(url);
	}
}
