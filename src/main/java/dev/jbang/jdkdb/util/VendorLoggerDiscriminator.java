package dev.jbang.jdkdb.util;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.sift.Discriminator;

/**
 * Logback discriminator that extracts vendor name from logger names.
 * Expects logger names in the format "vendors.{vendorName}" and extracts the vendor name portion.
 */
public class VendorLoggerDiscriminator implements Discriminator<ILoggingEvent> {
	private static final String KEY = "vendorName";
	private boolean started = false;

	@Override
	public String getDiscriminatingValue(ILoggingEvent event) {
		String loggerName = event.getLoggerName();

		// Extract vendor name from logger name (e.g., "vendors.Temurin" -> "Temurin")
		if (loggerName != null && loggerName.startsWith("vendors.")) {
			return loggerName.substring("vendors.".length());
		}

		return "unknown";
	}

	@Override
	public String getKey() {
		return KEY;
	}

	@Override
	public void start() {
		started = true;
	}

	@Override
	public void stop() {
		started = false;
	}

	@Override
	public boolean isStarted() {
		return started;
	}
}
