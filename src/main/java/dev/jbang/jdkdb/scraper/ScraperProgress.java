package dev.jbang.jdkdb.scraper;

import dev.jbang.jdkdb.reporting.ProgressEvent;
import dev.jbang.jdkdb.reporting.ProgressReporter;

public interface ScraperProgress {
	void success(String filename);

	void skipped(String filename);

	void fail(String message, Exception error);

	class Default implements ScraperProgress {
		private final ProgressReporter reporter;
		private final String scraperId;

		public Default(String scraperId, ProgressReporter reporter) {
			this.scraperId = scraperId;
			this.reporter = reporter;
		}

		@Override
		public void success(String filename) {
			reporter.report(ProgressEvent.progress(scraperId, "Success: " + filename));
		}

		@Override
		public void skipped(String filename) {
			reporter.report(ProgressEvent.progress(scraperId, "Skipped: " + filename));
		}

		@Override
		public void fail(String message, Exception error) {
			reporter.report(ProgressEvent.progress(scraperId, "Failed: " + message + " - " + error.getMessage()));
		}
	}
}
