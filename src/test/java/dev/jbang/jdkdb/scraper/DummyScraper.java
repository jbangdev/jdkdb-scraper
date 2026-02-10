package dev.jbang.jdkdb.scraper;

import dev.jbang.jdkdb.model.JdkMetadata;
import java.util.ArrayList;
import java.util.List;

/** Dummy scraper implementation for testing purposes */
public class DummyScraper extends BaseScraper {
	protected final List<JdkMetadata> metadataToReturn;
	private final boolean shouldThrowException;
	private final String exceptionMessage;

	public DummyScraper(ScraperConfig config) {
		this(config, new ArrayList<>(), false, null);
	}

	public DummyScraper(ScraperConfig config, List<JdkMetadata> metadataToReturn) {
		this(config, metadataToReturn, false, null);
	}

	public DummyScraper(
			ScraperConfig config,
			List<JdkMetadata> metadataToReturn,
			boolean shouldThrowException,
			String exceptionMessage) {
		super(config);
		this.metadataToReturn = metadataToReturn;
		this.shouldThrowException = shouldThrowException;
		this.exceptionMessage = exceptionMessage;
	}

	@Override
	protected List<JdkMetadata> scrape() throws Exception {
		if (shouldThrowException) {
			throw new RuntimeException(exceptionMessage != null ? exceptionMessage : "Test exception");
		}
		// Save individual metadata files like real scrapers do
		for (JdkMetadata metadata : metadataToReturn) {
			saveMetadataFile(metadata);
			success(metadata.getMetadataFilename());
		}
		return metadataToReturn;
	}

	/** Discovery implementation for ServiceLoader testing */
	public static class Discovery implements Scraper.Discovery {
		@Override
		public String name() {
			return "dummy";
		}

		@Override
		public String vendor() {
			return "test-vendor";
		}

		@Override
		public Scraper create(ScraperConfig config) {
			return new DummyScraper(config);
		}
	}
}
