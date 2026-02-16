package dev.jbang.jdkdb.util;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jbang.jdkdb.model.JdkMetadata;
import dev.jbang.jdkdb.scraper.DownloadResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MetadataUtilsTest {

	@TempDir
	Path tempDir;

	@Test
	void testValidSaveMetadata() throws Exception {
		// Given
		String json = """
			{
				"vendor" : "microsoft",
				"filename" : "microsoft-jdk-25.0.2-macos-x64.tar.gz",
				"release_type" : "ga",
				"version" : "25.0.2",
				"java_version" : "25.0.2",
				"jvm_impl" : "hotspot",
				"os" : "macosx",
				"architecture" : "x86_64",
				"file_type" : "tar.gz",
				"image_type" : "jdk",
				"features" : [ ],
				"url" : "https://aka.ms/download-jdk/microsoft-jdk-25.0.2-macos-x64.tar.gz",
				"md5" : "37d1c8e9537cd4d75fd28147a5dd6a55",
				"md5_file" : "microsoft-jdk-25.0.2-macos-x64.tar.gz.md5",
				"sha1" : "741a6df853edecfadd81b4a389271eb937c2626a",
				"sha1_file" : "microsoft-jdk-25.0.2-macos-x64.tar.gz.sha1",
				"sha256" : "6bc02fd3182dee12510f253d08eeac342a1f0e03d7f4114763f83d8722e2915e",
				"sha256_file" : "microsoft-jdk-25.0.2-macos-x64.tar.gz.sha256",
				"sha512" : "4615da0674699a41cc62ed6ddf201efe1904091cdbf95d629178c2026b312f7bec2fb424ef0c10d2270b086b72b0ca006cdbbcbe1e7df4f54412b8ebb8c37ab5",
				"sha512_file" : "microsoft-jdk-25.0.2-macos-x64.tar.gz.sha512",
				"size" : 220223851
				}""";
		// spotless:off
		String expected = """
[
  {
    "architecture": "x86_64",
    "features": [],
    "file_type": "tar.gz",
    "filename": "microsoft-jdk-25.0.2-macos-x64.tar.gz",
    "image_type": "jdk",
    "java_version": "25.0.2",
    "jvm_impl": "hotspot",
    "md5": "37d1c8e9537cd4d75fd28147a5dd6a55",
    "md5_file": "microsoft-jdk-25.0.2-macos-x64.tar.gz.md5",
    "os": "macosx",
    "release_type": "ga",
    "sha1": "741a6df853edecfadd81b4a389271eb937c2626a",
    "sha1_file": "microsoft-jdk-25.0.2-macos-x64.tar.gz.sha1",
    "sha256": "6bc02fd3182dee12510f253d08eeac342a1f0e03d7f4114763f83d8722e2915e",
    "sha256_file": "microsoft-jdk-25.0.2-macos-x64.tar.gz.sha256",
    "sha512": "4615da0674699a41cc62ed6ddf201efe1904091cdbf95d629178c2026b312f7bec2fb424ef0c10d2270b086b72b0ca006cdbbcbe1e7df4f54412b8ebb8c37ab5",
    "sha512_file": "microsoft-jdk-25.0.2-macos-x64.tar.gz.sha512",
    "size": 220223851,
    "url": "https://aka.ms/download-jdk/microsoft-jdk-25.0.2-macos-x64.tar.gz",
    "vendor": "microsoft",
    "version": "25.0.2"
  }
]
""";
		// spotless:on

		ObjectMapper objectMapper = new ObjectMapper().configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
		JdkMetadata metadata = objectMapper.readValue(json, JdkMetadata.class); // Validate JSON
		Files.createDirectories(tempDir.resolve("metadata"));

		// When
		MetadataUtils.saveMetadata(tempDir.resolve("metadata"), List.of(metadata), false);

		// Then
		Path metadataFile = tempDir.resolve("metadata").resolve("all.json");
		assertThat(metadataFile).exists();
		String fileContent = Files.readString(metadataFile);
		assertThat(fileContent).isEqualTo(expected);
	}

	@Test
	void testGenerateAllJsonFromDirectory() throws Exception {
		// Given - create individual metadata files
		Path vendorDir = tempDir.resolve("vendor-dir");
		Files.createDirectories(vendorDir);

		DownloadResult download1 = new DownloadResult("md5-1", "sha1-1", "sha256-1", "sha512-1", 100_000_000L);
		JdkMetadata metadata1 = JdkMetadata.create()
				.vendor("test-vendor")
				.filename("test-jdk-1")
				.releaseType("ga")
				.version("17.0.1")
				.javaVersion("17")
				.os("linux")
				.arch("x86_64")
				.fileType("tar.gz")
				.imageType("jdk")
				.url("https://example.com/jdk-1.tar.gz")
				.download(download1);

		DownloadResult download2 = new DownloadResult("md5-2", "sha1-2", "sha256-2", "sha512-2", 100_000_001L);
		JdkMetadata metadata2 = JdkMetadata.create()
				.vendor("test-vendor")
				.filename("test-jdk-2")
				.releaseType("ga")
				.version("17.0.2")
				.javaVersion("17")
				.os("windows")
				.arch("x86_64")
				.fileType("zip")
				.imageType("jdk")
				.url("https://example.com/jdk-2.zip")
				.download(download2)
				.metadataFilename("custom-metadata-filename.json");

		// Save individual metadata files
		MetadataUtils.saveMetadataFile(vendorDir.resolve(metadata1.metadataFilename()), metadata1);
		MetadataUtils.saveMetadataFile(vendorDir.resolve(metadata2.metadataFilename()), metadata2);

		// Verify individual files exist
		assertThat(vendorDir.resolve("test-jdk-1.json")).exists();
		assertThat(vendorDir.resolve("test-jdk-2.json")).doesNotExist();
		assertThat(vendorDir.resolve("custom-metadata-filename.json")).exists();

		// When - generate all.json from directory
		MetadataUtils.generateAllJsonFromDirectory(vendorDir, false);

		// Then - all.json should be created
		Path allJson = vendorDir.resolve("all.json");
		assertThat(allJson).exists();

		String allJsonContent = Files.readString(allJson);
		assertThat(allJsonContent).contains("test-jdk-1");
		assertThat(allJsonContent).contains("test-jdk-2");
		assertThat(allJsonContent).contains("17.0.1");
		assertThat(allJsonContent).contains("17.0.2");
	}

	@Test
	void testGenerateAllJsonFromDirectoryIgnoresExistingAllJson() throws Exception {
		// Given - create metadata files including an existing all.json
		Path vendorDir = tempDir.resolve("vendor-dir");
		Files.createDirectories(vendorDir);

		DownloadResult download = new DownloadResult(null, null, null, null, 100_000_000L);
		JdkMetadata metadata = JdkMetadata.create()
				.filename("test-jdk")
				.vendor("test-vendor")
				.version("17.0.1")
				.javaVersion("17")
				.os("linux")
				.arch("x86_64")
				.fileType("tar.gz")
				.imageType("jdk")
				.releaseType("ga")
				.url("https://example.com/jdk.tar.gz")
				.download(download);

		MetadataUtils.saveMetadataFile(vendorDir.resolve(metadata.metadataFilename()), metadata);

		// Create an old all.json with different content
		Files.writeString(vendorDir.resolve("all.json"), "[{\"old\": \"data\"}]");

		// When - regenerate all.json
		MetadataUtils.generateAllJsonFromDirectory(vendorDir, false);

		// Then - new all.json should contain current metadata, not old data
		String allJsonContent = Files.readString(vendorDir.resolve("all.json"));
		assertThat(allJsonContent).contains("test-jdk");
		assertThat(allJsonContent).doesNotContain("old");
	}

	@Test
	void testGenerateAllJsonFromNonExistentDirectory() throws Exception {
		// Given - non-existent directory
		Path nonExistent = tempDir.resolve("does-not-exist");

		// When/Then - should not throw exception
		assertThatCode(() -> MetadataUtils.generateAllJsonFromDirectory(nonExistent, false))
				.doesNotThrowAnyException();
	}

	@Test
	void testSaveMetadataSortsByVersionThenFilename() throws Exception {
		// Given - metadata with various versions and filenames
		DownloadResult download1 = new DownloadResult(null, null, null, null, 100_000_000L);
		JdkMetadata metadata1 = JdkMetadata.create()
				.filename("zulu-jdk-11.0.10")
				.vendor("zulu")
				.version("11.0.10")
				.javaVersion("11")
				.os("linux")
				.arch("x86_64")
				.fileType("tar.gz")
				.imageType("jdk")
				.releaseType("ga")
				.url("https://example.com/zulu-11.0.10.tar.gz")
				.download(download1);

		DownloadResult download2 = new DownloadResult(null, null, null, null, 100_000_000L);
		JdkMetadata metadata2 = JdkMetadata.create()
				.filename("abc-jdk-11.0.10")
				.vendor("abc")
				.version("11.0.10")
				.javaVersion("11")
				.os("linux")
				.arch("x86_64")
				.fileType("tar.gz")
				.imageType("jdk")
				.releaseType("ga")
				.url("https://example.com/abc-11.0.10.tar.gz")
				.download(download2);

		DownloadResult download3 = new DownloadResult(null, null, null, null, 100_000_000L);
		JdkMetadata metadata3 = JdkMetadata.create()
				.filename("temurin-jdk-17.0.5")
				.vendor("temurin")
				.version("17.0.5")
				.javaVersion("17")
				.os("linux")
				.arch("x86_64")
				.fileType("tar.gz")
				.imageType("jdk")
				.releaseType("ga")
				.url("https://example.com/temurin-17.0.5.tar.gz")
				.download(download3);

		DownloadResult download4 = new DownloadResult(null, null, null, null, 100_000_000L);
		JdkMetadata metadata4 = JdkMetadata.create()
				.filename("oracle-jdk-8.0.202")
				.vendor("oracle")
				.version("8.0.202")
				.javaVersion("8")
				.os("linux")
				.arch("x86_64")
				.fileType("tar.gz")
				.imageType("jdk")
				.releaseType("ga")
				.url("https://example.com/oracle-8.0.202.tar.gz")
				.download(download4);

		DownloadResult download5 = new DownloadResult(null, null, null, null, 100_000_000L);
		JdkMetadata metadata5 = JdkMetadata.create()
				.filename("oracle-jdk-11.0.2")
				.vendor("oracle")
				.version("11.0.2")
				.javaVersion("11")
				.os("linux")
				.arch("x86_64")
				.fileType("tar.gz")
				.imageType("jdk")
				.releaseType("ga")
				.url("https://example.com/oracle-11.0.2.tar.gz")
				.download(download5);

		// When - save in random order
		Path metadataDir = tempDir.resolve("metadata");
		Files.createDirectories(metadataDir);
		MetadataUtils.saveMetadata(metadataDir, List.of(metadata3, metadata1, metadata5, metadata4, metadata2), false);

		// Then - all.json should be sorted by version first, then filename
		Path allJson = metadataDir.resolve("all.json");
		assertThat(allJson).exists();

		String content = Files.readString(allJson);
		ObjectMapper objectMapper = new ObjectMapper();
		JdkMetadata[] result = objectMapper.readValue(content, JdkMetadata[].class);

		// Verify sorted order: 8.0.202, 11.0.2, 11.0.10 (abc before zulu), 17.0.5
		assertThat(result).hasSize(5);
		assertThat(result[0].version()).isEqualTo("8.0.202");
		assertThat(result[0].filename()).isEqualTo("oracle-jdk-8.0.202");

		assertThat(result[1].version()).isEqualTo("11.0.2");
		assertThat(result[1].filename()).isEqualTo("oracle-jdk-11.0.2");

		assertThat(result[2].version()).isEqualTo("11.0.10");
		assertThat(result[2].filename()).isEqualTo("abc-jdk-11.0.10"); // abc comes before zulu

		assertThat(result[3].version()).isEqualTo("11.0.10");
		assertThat(result[3].filename()).isEqualTo("zulu-jdk-11.0.10");

		assertThat(result[4].version()).isEqualTo("17.0.5");
		assertThat(result[4].filename()).isEqualTo("temurin-jdk-17.0.5");
	}

	@Test
	void testSaveMetadataLexicalSort() throws Exception {
		// Lexical version sort: "11.0.10" < "11.0.2" (string compare). Order still version then filename.
		DownloadResult download = new DownloadResult(null, null, null, null, 100_000_000L);
		JdkMetadata metadata1 = JdkMetadata.create()
				.filename("jdk-11.0.2")
				.vendor("test")
				.version("11.0.2")
				.javaVersion("11")
				.os("linux")
				.arch("x86_64")
				.fileType("tar.gz")
				.imageType("jdk")
				.releaseType("ga")
				.url("https://example.com/jdk-11.0.2.tar.gz")
				.download(download);

		JdkMetadata metadata2 = JdkMetadata.create()
				.filename("jdk-11.0.10")
				.vendor("test")
				.version("11.0.10")
				.javaVersion("11")
				.os("linux")
				.arch("x86_64")
				.fileType("tar.gz")
				.imageType("jdk")
				.releaseType("ga")
				.url("https://example.com/jdk-11.0.10.tar.gz")
				.download(download);

		Path metadataDir = tempDir.resolve("metadata");
		Files.createDirectories(metadataDir);
		MetadataUtils.saveMetadata(metadataDir, List.of(metadata1, metadata2), true);

		Path allJson = metadataDir.resolve("all.json");
		assertThat(allJson).exists();

		String content = Files.readString(allJson);
		ObjectMapper objectMapper = new ObjectMapper();
		JdkMetadata[] result = objectMapper.readValue(content, JdkMetadata[].class);

		assertThat(result).hasSize(2);
		// Lexical: "11.0.10" before "11.0.2"
		assertThat(result[0].version()).isEqualTo("11.0.10");
		assertThat(result[1].version()).isEqualTo("11.0.2");
	}
}
