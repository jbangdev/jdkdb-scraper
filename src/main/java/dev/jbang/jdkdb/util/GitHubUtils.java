package dev.jbang.jdkdb.util;

import dev.jbang.jdkdb.MainCommand;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class GitHubUtils {

	public static void setupGitHubToken() {
		String githubToken;
		String fromEnv = System.getenv(MainCommand.GITHUB_TOKEN_ENV);
		if (fromEnv != null && !fromEnv.isBlank()) {
			githubToken = fromEnv.trim();
			MainCommand.logger.info("Using GitHub token from {}", MainCommand.GITHUB_TOKEN_ENV);
		} else {
			githubToken = runGhAuthToken();
			if (githubToken != null) {
				MainCommand.logger.info("Using GitHub token from gh auth token");
			} else {
				MainCommand.logger.info(
						"No GitHub token found (set {} or run 'gh auth login'); API rate limits may apply",
						MainCommand.GITHUB_TOKEN_ENV);
			}
		}
		if (githubToken != null) {
			System.setProperty(HttpUtils.GITHUB_TOKEN_PROP, githubToken);
		}
	}

	private static String runGhAuthToken() {
		try {
			Process process = new ProcessBuilder("gh", "auth", "token")
					.redirectErrorStream(false)
					.start();
			String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
			int exit = process.waitFor();
			if (exit == 0 && !output.isBlank()) {
				return output;
			}
		} catch (IOException | InterruptedException e) {
			// ignore: no token available
		}
		return null;
	}
}
