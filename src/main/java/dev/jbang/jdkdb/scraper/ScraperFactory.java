package dev.jbang.jdkdb.scraper;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Factory for creating scraper instances using ServiceLoader */
public class ScraperFactory {
	private final Path metadataDir;
	private final Path checksumDir;
	private final boolean fromStart;
	private final int maxFailureCount;
	private final int limitProgress;
	private final Duration skipEaDuration;
	private final DownloadManager downloadManager;

	public static ScraperFactory create(
			Path metadataDir,
			Path checksumDir,
			boolean fromStart,
			int maxFailureCount,
			int limitProgress,
			Duration skipEaDuration,
			DownloadManager downloadManager) {
		return new ScraperFactory(
				metadataDir, checksumDir, fromStart, maxFailureCount, limitProgress, skipEaDuration, downloadManager);
	}

	private ScraperFactory(
			Path metadataDir,
			Path checksumDir,
			boolean fromStart,
			int maxFailureCount,
			int limitProgress,
			Duration skipEaDuration,
			DownloadManager downloadManager) {
		this.metadataDir = metadataDir;
		this.checksumDir = checksumDir;
		this.fromStart = fromStart;
		this.maxFailureCount = maxFailureCount;
		this.limitProgress = limitProgress;
		this.skipEaDuration = skipEaDuration;
		this.downloadManager = downloadManager;
	}

	/** Create all available scrapers using ServiceLoader */
	public List<Scraper> createAllScrapers() {
		List<Scraper> scrapers = new ArrayList<>();
		Path metadataVendorDir = metadataDir.resolve("vendor");

		ServiceLoader<Scraper.Discovery> loader = ServiceLoader.load(Scraper.Discovery.class);

		for (Scraper.Discovery discovery : loader) {
			String vendor = discovery.vendor();
			Logger dl = LoggerFactory.getLogger("vendors." + vendor);
			ScraperConfig config = new ScraperConfig(
					metadataVendorDir.resolve(vendor),
					checksumDir.resolve(vendor),
					dl,
					fromStart,
					maxFailureCount,
					limitProgress,
					skipEaDuration,
					md -> downloadManager.submit(md, vendor, dl));

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
			Logger dl = LoggerFactory.getLogger("vendors." + vendor);
			ScraperConfig config = new ScraperConfig(
					metadataDir.resolve("vendor").resolve(vendor),
					checksumDir.resolve(vendor),
					dl,
					fromStart,
					maxFailureCount,
					limitProgress,
					skipEaDuration,
					md -> downloadManager.submit(md, vendor, dl));
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
