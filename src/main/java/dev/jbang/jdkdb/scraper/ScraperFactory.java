package dev.jbang.jdkdb.scraper;

import dev.jbang.jdkdb.reporting.ProgressReporter;
import dev.jbang.jdkdb.reporting.ProgressReporterLogger;
import java.nio.file.Path;
import java.util.*;

/** Factory for creating scraper instances using ServiceLoader */
public class ScraperFactory {
	private final Path metadataDir;
	private final Path checksumDir;
	private final ProgressReporter reporter;
	private final boolean fromStart;

	public static ScraperFactory create(
			Path metadataDir, Path checksumDir, ProgressReporter reporter, boolean fromStart) {
		return new ScraperFactory(metadataDir, checksumDir, reporter, fromStart);
	}

	private ScraperFactory(Path metadataDir, Path checksumDir, ProgressReporter reporter, boolean fromStart) {
		this.metadataDir = metadataDir;
		this.checksumDir = checksumDir;
		this.reporter = reporter;
		this.fromStart = fromStart;
	}

	/** Create all available scrapers using ServiceLoader */
	public List<Scraper> createAllScrapers() {
		List<Scraper> scrapers = new ArrayList<>();
		Path metadataVendorDir = metadataDir.resolve("vendor");

		ServiceLoader<Scraper.Discovery> loader = ServiceLoader.load(Scraper.Discovery.class);

		for (Scraper.Discovery discovery : loader) {
			String vendor = discovery.vendor();
			String name = discovery.name();

			ScraperConfig config = new ScraperConfig(
					metadataVendorDir.resolve(vendor),
					checksumDir.resolve(vendor),
					ProgressReporterLogger.forScraper(name, reporter),
					fromStart);

			Scraper scraper = discovery.create(config);
			scrapers.add(scraper);
		}

		return scrapers;
	}

	/** Create specific scraper by name */
	public Scraper createScraper(String scraperName) {
		Map<String, Scraper.Discovery> allDiscoveries = getAvailableScraperDiscoveries();

		Scraper.Discovery discovery = allDiscoveries.get(scraperName);
		if (discovery != null) {
			String vendor = discovery.vendor();
			ScraperConfig config = new ScraperConfig(
					metadataDir.resolve("vendor").resolve(vendor),
					checksumDir.resolve(vendor),
					ProgressReporterLogger.forScraper(scraperName, reporter),
					fromStart);
			return discovery.create(config);
		} else {
			throw new IllegalArgumentException("Unknown scraper ID: " + scraperName);
		}
	}

	/** Get all available scraper discoveries */
	public static Map<String, Scraper.Discovery> getAvailableScraperDiscoveries() {
		Map<String, Scraper.Discovery> discs = new HashMap<>();

		ServiceLoader<Scraper.Discovery> loader = ServiceLoader.load(Scraper.Discovery.class);
		for (Scraper.Discovery discovery : loader) {
			discs.put(discovery.name(), discovery);
		}

		return discs;
	}
}
