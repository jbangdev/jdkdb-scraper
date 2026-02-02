package dev.jbang.jdkdb.reporting;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * A Logger that wraps a ProgressReporter, forwarding log messages as progress events. This allows
 * scrapers to use standard Java logging while still integrating with the progress reporting system.
 */
public class ProgressReporterLogger extends Logger {

	private ProgressReporterLogger(String scraperId, ProgressReporter reporter) {
		super(scraperId, null);
		setLevel(Level.ALL);
		setUseParentHandlers(false);

		// Add custom handler that forwards to progress reporter
		addHandler(new Handler() {
			@Override
			public void publish(LogRecord record) {
				if (isLoggable(record)) {
					String message = record.getLevel() + ": " + record.getMessage();
					if (message != null && !message.isBlank()) {
						reporter.report(ProgressEvent.progress(scraperId, message));
					}
				}
			}

			@Override
			public void flush() {}

			@Override
			public void close() {}
		});
	}

	/**
	 * Create a new logger for a scraper.
	 *
	 * @param scraperId the scraper ID
	 * @param reporter the progress reporter
	 * @return a new logger instance
	 */
	public static Logger forScraper(String scraperId, ProgressReporter reporter) {
		return new ProgressReporterLogger(scraperId, reporter);
	}
}
