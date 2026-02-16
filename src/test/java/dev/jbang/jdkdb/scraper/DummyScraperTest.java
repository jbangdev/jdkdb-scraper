package dev.jbang.jdkdb.scraper;

import static org.assertj.core.api.Assertions.*;

import dev.jbang.jdkdb.model.JdkMetadata;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DummyScraperTest {

	@TempDir
	Path tempDir;

	private Path metadataDir;
	private Path checksumDir;
	private ScraperConfig config;

	private DummyDownloadManager downloadManager;

	@BeforeEach
	void setUp() {
		metadataDir = tempDir.resolve("metadata");
		checksumDir = tempDir.resolve("checksums");
		downloadManager = new DummyDownloadManager();
		Logger dl = LoggerFactory.getLogger("test");
		config = new ScraperConfig(
				metadataDir,
				checksumDir,
				dl,
				false, // fromStart
				10, // maxFailureCount
				0, // limitProgress (unlimited)
				md -> downloadManager.submit(md, "test-vendor", dl));
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
	void testScraperSubmitsDownloadsToDownloadManager() {
		List<JdkMetadata> metadata = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			JdkMetadata meta = JdkMetadata.create()
					.vendor("test-vendor")
					.releaseType("ga")
					.version("17.0." + i)
					.javaVersion("17")
					.os("linux")
					.arch("x86_64")
					.fileType("tar.gz")
					.imageType("jdk")
					.url("https://example.com/jdk-" + i + ".tar.gz")
					.filename("test-jdk-" + i + ".tar.gz");
			// Don't set download() - these should be submitted to download manager
			metadata.add(meta);
		}

		DummyScraper scraper = new DummyScraper(config, metadata);

		// When
		scraper.call();

		// Then
		assertThat(downloadManager.getSubmittedCount()).isEqualTo(3);
		assertThat(downloadManager.getSubmittedDownloads())
				.hasSize(3)
				.allMatch(d -> d.metadata().url() != null)
				.allMatch(d -> d.metadata().filename() != null);
	}

	@Test
	void testScraperSkipsAlreadyDownloadedFiles() throws IOException {
		// Given - create metadata WITH download results (md5 already set)
		List<JdkMetadata> metadata = createSkippedMetadata(3); // These have download() already set
		Files.createDirectories(metadataDir);
		for (JdkMetadata meta : metadata) {
			// Create empty metadata files to simulate already processed items
			Files.writeString(metadataDir.resolve(meta.metadataFilename()), "{}");
		}

		DummyScraper scraper = new DummyScraper(config, metadata);

		// When
		scraper.call();

		// Then - should NOT submit to download manager since their metadata files already exist (simulating already
		// processed)
		assertThat(downloadManager.getSubmittedCount()).isEqualTo(0);
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
		Logger dl = LoggerFactory.getLogger("test");
		ScraperConfig limitedConfig = new ScraperConfig(
				metadataDir,
				checksumDir,
				dl,
				false,
				10,
				2, // limit to 2 items
				md -> downloadManager.submit(md, "test-vendor", dl));
		List<JdkMetadata> metadata = createTestMetadata(5);

		// Create a scraper that tracks progress
		DummyScraper scraper = new DummyScraper(limitedConfig, metadata);

		// When
		ScraperResult result = scraper.call();

		// Then
		// The exception should be caught by call() and returned as a failure
		assertThat(result.success()).isTrue();
		assertThat(result.itemsProcessed()).isEqualTo(2);
	}

	@Test
	void testScraperWithMaxFailures() {
		// Given
		Logger dl = LoggerFactory.getLogger("test");
		ScraperConfig limitedConfig = new ScraperConfig(
				metadataDir,
				checksumDir,
				dl,
				false,
				2, // max 2 failures
				0,
				md -> downloadManager.submit(md, "test-vendor", dl));

		DummyScraper scraper = new DummyScraper(limitedConfig) {
			@Override
			protected void scrape() throws Exception {
				// Note: The fail() method has a condition that only increments failureCount
				// when failureCount > 0, so it won't actually throw with the current implementation.
				// This test documents the actual behavior, not the expected behavior.
				// With failureCount starting at 0, the condition (failureCount > 0 && ...)
				// is never true, so failureCount never increments and exception is never thrown.
				fail("item1", new RuntimeException("error1"));
				fail("item2", new RuntimeException("error2"));
				fail("item3", new RuntimeException("error3"));
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

		Logger dl = LoggerFactory.getLogger("test");
		ScraperConfig configNoFromStart = new ScraperConfig(
				metadataDir,
				checksumDir,
				dl,
				false, // fromStart = false
				10,
				0,
				md -> downloadManager.submit(md, "test-vendor", dl));

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

		Logger dl = LoggerFactory.getLogger("test");
		ScraperConfig configFromStart = new ScraperConfig(
				metadataDir,
				checksumDir,
				dl,
				true, // fromStart = true
				10,
				0,
				md -> downloadManager.submit(md, "test-vendor", dl));

		DummyScraper scraper = new DummyScraper(configFromStart);

		// When
		boolean exists = scraper.metadataExists("existing-file");

		// Then
		assertThat(exists).isFalse();
	}

	private List<JdkMetadata> createTestMetadata(int count) {
		List<JdkMetadata> result = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			// Create a mock DownloadResult for testing
			// DownloadResult download =
			//		new DownloadResult("md5-" + i, "sha1-" + i, "sha256-" + i, "sha512-" + i, 100_000_000L + i);
			JdkMetadata metadata = JdkMetadata.create()
					.vendor("test-vendor")
					.releaseType("ga")
					.version("17.0." + i)
					.javaVersion("17")
					.os("linux")
					.arch("x86_64")
					.fileType("tar.gz")
					.imageType("jdk")
					.url("https://example.com/jdk-" + i + ".tar.gz")
					.filename("test-jdk-" + i + ".tar.gz");
			// .download(download);
			result.add(metadata);
		}
		return result;
	}

	private List<JdkMetadata> createSkippedMetadata(int count) {
		List<JdkMetadata> result = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			JdkMetadata metadata = BaseScraper.skipped("skipped-file-" + i + ".json");
			result.add(metadata);
		}
		return result;
	}
}
