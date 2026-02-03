package dev.jbang.jdkdb.scraper;

import static org.assertj.core.api.Assertions.*;

import dev.jbang.jdkdb.model.JdkMetadata;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DummyScraperTest {

	@TempDir
	Path tempDir;

	private Path metadataDir;
	private Path checksumDir;
	private ScraperConfig config;

	@BeforeEach
	void setUp() {
		metadataDir = tempDir.resolve("metadata");
		checksumDir = tempDir.resolve("checksums");
		config = new ScraperConfig(
				metadataDir,
				checksumDir,
				Logger.getLogger("test"),
				false, // fromStart
				10, // maxFailureCount
				0 // limitProgress (unlimited)
				);
	}

	@Test
	void testSuccessfulScraperExecution() {
		// Given
		List<JdkMetadata> metadata = createTestMetadata(3);
		DummyScraper scraper = new DummyScraper(config, metadata);

		// When
		ScraperResult result = scraper.call();

		// Then
		assertThat(result).isNotNull();
		assertThat(result.success()).isTrue();
		assertThat(result.itemsProcessed()).isEqualTo(3);
		assertThat(result.error()).isNull();
	}

	@Test
	void testScraperCreatesDirectories() {
		// Given
		List<JdkMetadata> metadata = createTestMetadata(1);
		DummyScraper scraper = new DummyScraper(config, metadata);

		// When
		scraper.call();

		// Then
		assertThat(metadataDir).exists();
		assertThat(checksumDir).exists();
	}

	@Test
	void testScraperHandlesException() {
		// Given
		DummyScraper scraper = new DummyScraper(config, new ArrayList<>(), true, "Test failure");

		// When
		ScraperResult result = scraper.call();

		// Then
		assertThat(result).isNotNull();
		assertThat(result.success()).isFalse();
		assertThat(result.itemsProcessed()).isEqualTo(0);
		assertThat(result.error()).isNotNull();
		assertThat(result.error().getMessage()).isEqualTo("Test failure");
	}

	@Test
	void testScraperWithEmptyMetadata() {
		// Given
		DummyScraper scraper = new DummyScraper(config);

		// When
		ScraperResult result = scraper.call();

		// Then
		assertThat(result).isNotNull();
		assertThat(result.success()).isTrue();
		assertThat(result.itemsProcessed()).isEqualTo(0);
		assertThat(metadataDir.resolve("all.json")).doesNotExist();
	}

	@Test
	void testScraperWithProgressLimit() {
		// Given
		ScraperConfig limitedConfig = new ScraperConfig(
				metadataDir, checksumDir, Logger.getLogger("test"), false, 10, 2 // limit to 2 items
				);
		List<JdkMetadata> metadata = createTestMetadata(5);

		// Create a scraper that tracks progress
		DummyScraper scraper = new DummyScraper(limitedConfig, metadata) {
			@Override
			protected List<JdkMetadata> scrape() throws Exception {
				// Simulate processing with success() calls
				// Note: the exception is thrown when processedCount reaches limitProgress
				for (JdkMetadata md : metadataToReturn) {
					success(md.getFilename());
				}
				return metadataToReturn;
			}
		};

		// When
		ScraperResult result = scraper.call();

		// Then
		// The exception should be caught by call() and returned as a failure
		assertThat(result.success()).isFalse();
		assertThat(result.error()).isInstanceOf(InterruptedProgressException.class);
		assertThat(result.error().getMessage()).contains("Reached progress limit of 2 items");
	}

	@Test
	void testScraperWithMaxFailures() {
		// Given
		ScraperConfig limitedConfig = new ScraperConfig(
				metadataDir,
				checksumDir,
				Logger.getLogger("test"),
				false,
				2, // max 2 failures
				0);

		DummyScraper scraper = new DummyScraper(limitedConfig) {
			@Override
			protected List<JdkMetadata> scrape() throws Exception {
				// Note: The fail() method has a condition that only increments failureCount
				// when failureCount > 0, so it won't actually throw with the current implementation.
				// This test documents the actual behavior, not the expected behavior.
				// With failureCount starting at 0, the condition (failureCount > 0 && ...)
				// is never true, so failureCount never increments and exception is never thrown.
				fail("item1", new RuntimeException("error1"));
				fail("item2", new RuntimeException("error2"));
				fail("item3", new RuntimeException("error3"));
				return new ArrayList<>();
			}
		};

		// When
		ScraperResult result = scraper.call();

		// Then
		// The exception should be caught by call() and returned as a failure
		assertThat(result.success()).isFalse();
		assertThat(result.error()).isInstanceOf(TooManyFailuresException.class);
		assertThat(result.error().getMessage()).isEqualTo("Too many failures, aborting");
	}

	@Test
	void testMetadataExistsReturnsTrueWhenFileExists() throws Exception {
		// Given
		Files.createDirectories(metadataDir);
		Files.writeString(metadataDir.resolve("existing-file.json"), "{}");

		ScraperConfig configNoFromStart = new ScraperConfig(
				metadataDir,
				checksumDir,
				Logger.getLogger("test"),
				false, // fromStart = false
				10,
				0);

		DummyScraper scraper = new DummyScraper(configNoFromStart);

		// When
		boolean exists = scraper.metadataExists("existing-file");

		// Then
		assertThat(exists).isTrue();
	}

	@Test
	void testMetadataExistsReturnsFalseWhenFromStartIsTrue() throws Exception {
		// Given
		Files.createDirectories(metadataDir);
		Files.writeString(metadataDir.resolve("existing-file.json"), "{}");

		ScraperConfig configFromStart = new ScraperConfig(
				metadataDir,
				checksumDir,
				Logger.getLogger("test"),
				true, // fromStart = true
				10,
				0);

		DummyScraper scraper = new DummyScraper(configFromStart);

		// When
		boolean exists = scraper.metadataExists("existing-file");

		// Then
		assertThat(exists).isFalse();
	}

	private List<JdkMetadata> createTestMetadata(int count) {
		List<JdkMetadata> result = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			JdkMetadata metadata = new JdkMetadata();
			metadata.setFilename("test-jdk-" + i);
			metadata.setVendor("test-vendor");
			metadata.setVersion("17.0." + i);
			metadata.setJavaVersion("17");
			metadata.setOs("linux");
			metadata.setArchitecture("x86_64");
			metadata.setFileType("tar.gz");
			metadata.setImageType("jdk");
			metadata.setReleaseType("ga");
			metadata.setUrl("https://example.com/jdk-" + i + ".tar.gz");
			metadata.setSize(100_000_000 + i);
			result.add(metadata);
		}
		return result;
	}
}
