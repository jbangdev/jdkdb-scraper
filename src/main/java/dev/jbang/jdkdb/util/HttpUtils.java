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

/** Utility class for HTTP operations */
public class HttpUtils {
	private final HttpClient httpClient;

	public HttpUtils() {
		this.httpClient = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NORMAL)
				.connectTimeout(Duration.ofSeconds(30))
				.build();
	}

	/** Download a file from a URL to a local path */
	public void downloadFile(String url, Path destination) throws IOException, InterruptedException {
		HttpRequest request =
				HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

		HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

		try (InputStream inputStream = response.body()) {
			Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/** Download content from a URL as a string */
	public String downloadString(String url) throws IOException, InterruptedException {
		HttpRequest request =
				HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		return response.body();
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

	public void close() {
		// HttpClient doesn't require explicit closing in modern JDK
		// It manages its resources automatically
	}
}
