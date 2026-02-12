package dev.jbang.jdkdb.scraper;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScraperFactoryTest {

	@TempDir
	Path tempDir;

	private Path metadataDir;
	private Path checksumDir;
	private DownloadManager downloadManager;

	@BeforeEach
	void setUp() {
		metadataDir = tempDir.resolve("metadata");
		checksumDir = tempDir.resolve("checksums");
		downloadManager = new DummyDownloadManager();
	}

	@Test
	void testCreateScraperFactory() {
		// When
		ScraperFactory factory = ScraperFactory.create(metadataDir, checksumDir, false, 10, 0, downloadManager);

		// Then
		assertThat(factory).isNotNull();
	}

	@Test
	void testGetAvailableScraperDiscoveries() {
		// When
		Map<String, Scraper.Discovery> discoveries = ScraperFactory.getAvailableScraperDiscoveries();

		// Then
		assertThat(discoveries).isNotNull();
		// Should at least contain our dummy scraper
		assertThat(discoveries).containsKey("dummy");
		assertThat(discoveries.get("dummy").vendor()).isEqualTo("test-vendor");
	}

	@Test
	void testCreateAllScrapers() {
		// Given
		ScraperFactory factory = ScraperFactory.create(metadataDir, checksumDir, false, 10, 0, downloadManager);

		// When
		List<Scraper> scrapers = factory.createAllScrapers();

		// Then
		assertThat(scrapers).isNotNull();
		assertThat(scrapers).isNotEmpty();
		// Should contain at least our dummy scraper
		assertThat(scrapers).hasSizeGreaterThanOrEqualTo(1);
	}

	@Test
	void testCreateSpecificScraper() {
		// Given
		ScraperFactory factory = ScraperFactory.create(metadataDir, checksumDir, false, 10, 0, downloadManager);

		// When
		Scraper scraper = factory.createScraper("dummy");

		// Then
		assertThat(scraper).isNotNull();
		assertThat(scraper).isInstanceOf(DummyScraper.class);
	}

	@Test
	void testCreateScraperWithInvalidName() {
		// Given
		ScraperFactory factory = ScraperFactory.create(metadataDir, checksumDir, false, 10, 0, downloadManager);

		// When/Then
		assertThatThrownBy(() -> factory.createScraper("non-existent-scraper"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Unknown scraper ID");
	}

	@Test
	void testFactoryCreatesScrapersWithCorrectConfig() throws Exception {
		// Given
		ScraperFactory factory = ScraperFactory.create(
				metadataDir,
				checksumDir,
				true, // fromStart
				5, // maxFailureCount
				100, // limitProgress
				downloadManager);

		// When
		Scraper scraper = factory.createScraper("dummy");
		ScraperResult result = scraper.call();

		// Then
		assertThat(result).isNotNull();
		// The scraper should have been created with the correct config
		// which we can verify by checking it runs successfully
		assertThat(result.success()).isTrue();
	}

	@Test
	void testFactoryCreatesScrapersWithVendorDirectories() throws Exception {
		// Given
		ScraperFactory factory = ScraperFactory.create(metadataDir, checksumDir, false, 10, 0, downloadManager);

		// When
		Scraper scraper = factory.createScraper("dummy");
		scraper.call();

		// Then
		// The metadata should be in vendor subdirectory
		Path vendorMetadataDir = metadataDir.resolve("vendor").resolve("test-vendor");
		assertThat(vendorMetadataDir).exists();
	}
}
