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

	public static final String GITHUB_TOKEN_PROP = "github.token";

	public HttpUtils() {
		this.httpClient = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NORMAL)
				.connectTimeout(Duration.ofSeconds(30))
				.build();
	}

	/** Download a file from a URL to a local path */
	public void downloadFile(String url, Path destination) throws IOException, InterruptedException {
		HttpRequest request = request(url).build();
		HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IOException("Failed to download file: " + url + " - HTTP status: " + response.statusCode());
		}
		try (InputStream inputStream = response.body()) {
			Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/** Download content from a URL as a string */
	public String downloadString(String url) throws IOException, InterruptedException {
		HttpRequest request = request(url).build();
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IOException("Failed to download content: " + url + " - HTTP status: " + response.statusCode());
		}
		return response.body();
	}

	/** Check if a URL exists (returns 2xx status code) */
	public boolean urlExists(String url) {
		try {
			HttpRequest request = request(url)
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

	private HttpRequest.Builder request(String url) {
		URI uri = URI.create(url);
		HttpRequest.Builder builder = HttpRequest.newBuilder().uri(uri).GET();

		// If the URL is for GitHub API, add the Authorization header if a token is available
		if (uri.getHost().equalsIgnoreCase("api.github.com")) {
			String token = System.getProperty(GITHUB_TOKEN_PROP);
			if (token != null && !token.isEmpty()) {
				builder.header("Authorization", "Bearer " + token);
			}
		}

		return builder;
	}

	public void close() {
		// HttpClient doesn't require explicit closing in modern JDK
		// It manages its resources automatically
	}
}
