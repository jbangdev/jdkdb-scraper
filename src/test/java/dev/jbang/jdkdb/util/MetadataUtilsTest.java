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
		Path metadataFile = tempDir.resolve("metadata").resolve("all.json");
		MetadataUtils.saveMetadata(metadataFile, List.of(metadata));

		// Then
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
		MetadataUtils.generateAllJsonFromDirectory(vendorDir, true);

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
		MetadataUtils.generateAllJsonFromDirectory(vendorDir, true);

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
		assertThatCode(() -> MetadataUtils.generateAllJsonFromDirectory(nonExistent, true))
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
		Path allJson = metadataDir.resolve("all.json");
		MetadataUtils.saveMetadata(allJson, List.of(metadata3, metadata1, metadata5, metadata4, metadata2));

		// Then - all.json should be sorted by version first, then filename
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
	void testGenerateComprehensiveIndicesBasic() throws Exception {
		// Given - create vendor directories with metadata files
		Path metadataDir = tempDir.resolve("metadata");
		Path vendorDir = metadataDir.resolve("vendor");
		Path temurinDir = vendorDir.resolve("temurin");
		Files.createDirectories(temurinDir);

		DownloadResult download1 = new DownloadResult("md5-1", "sha1-1", "sha256-1", "sha512-1", 100_000_000L);
		JdkMetadata metadata1 = JdkMetadata.create()
				.vendor("temurin")
				.filename("temurin-jdk-17-linux-x64.tar.gz")
				.releaseType("ga")
				.version("17.0.5")
				.javaVersion("17")
				.jvmImpl("hotspot")
				.os("linux")
				.arch("x86_64")
				.fileType("tar.gz")
				.imageType("jdk")
				.url("https://example.com/temurin-17.tar.gz")
				.download(download1);

		DownloadResult download2 = new DownloadResult("md5-2", "sha1-2", "sha256-2", "sha512-2", 100_000_001L);
		JdkMetadata metadata2 = JdkMetadata.create()
				.vendor("temurin")
				.filename("temurin-jdk-17-windows-x64.zip")
				.releaseType("ga")
				.version("17.0.5")
				.javaVersion("17")
				.jvmImpl("hotspot")
				.os("windows")
				.arch("x86_64")
				.fileType("zip")
				.imageType("jdk")
				.url("https://example.com/temurin-17.zip")
				.download(download2);

		MetadataUtils.saveMetadataFile(temurinDir.resolve(metadata1.metadataFilename()), metadata1);
		MetadataUtils.saveMetadataFile(temurinDir.resolve(metadata2.metadataFilename()), metadata2);

		// When
		MetadataUtils.generateComprehensiveIndices(metadataDir, false);

		// Then - verify metadata/all.json exists
		Path allJson = metadataDir.resolve("all.json");
		assertThat(allJson).exists();
		String allJsonContent = Files.readString(allJson);
		assertThat(allJsonContent).contains("temurin-jdk-17-linux-x64.tar.gz");
		assertThat(allJsonContent).contains("temurin-jdk-17-windows-x64.zip");

		// Verify hierarchical structure: ga.json
		Path gaDir = metadataDir.resolve("ga");
		assertThat(gaDir).exists().isDirectory();
		Path gaJson = metadataDir.resolve("ga.json");
		assertThat(gaJson).exists();

		// Verify OS level: ga/linux/, ga/linux.json and ga/windows/, ga/windows.json
		Path linuxDir = gaDir.resolve("linux");
		assertThat(linuxDir).exists().isDirectory();
		assertThat(gaDir.resolve("linux.json")).exists();

		Path windowsDir = gaDir.resolve("windows");
		assertThat(windowsDir).exists().isDirectory();
		assertThat(gaDir.resolve("windows.json")).exists();

		// Verify architecture level: ga/linux/x86_64/x86_64.json
		Path archLinuxDir = linuxDir.resolve("x86_64");
		assertThat(archLinuxDir).exists().isDirectory();
		assertThat(linuxDir.resolve("x86_64.json")).exists();

		// Verify image_type level: ga/linux/x86_64/jdk/jdk.json
		Path imageTypeDir = archLinuxDir.resolve("jdk");
		assertThat(imageTypeDir).exists().isDirectory();
		assertThat(archLinuxDir.resolve("jdk.json")).exists();

		// Verify jvm_impl level: ga/linux/x86_64/jdk/hotspot.json
		Path jvmDir = imageTypeDir.resolve("hotspot");
		assertThat(jvmDir).exists().isDirectory();
		assertThat(imageTypeDir.resolve("hotspot.json")).exists();

		// Verify vendor level: ga/linux/x86_64/jdk/hotspot/temurin.json
		Path vendorJson = jvmDir.resolve("temurin.json");
		assertThat(vendorJson).exists();
		String vendorContent = Files.readString(vendorJson);
		assertThat(vendorContent).contains("temurin-jdk-17-linux-x64.tar.gz");
		assertThat(vendorContent).doesNotContain("windows"); // Should only contain Linux entry
	}

	@Test
	void testGenerateComprehensiveIndicesMultipleVendors() throws Exception {
		// Given - multiple vendors with overlapping properties
		Path metadataDir = tempDir.resolve("metadata");
		Path vendorDir = metadataDir.resolve("vendor");
		Path temurinDir = vendorDir.resolve("temurin");
		Path microsoftDir = vendorDir.resolve("microsoft");
		Files.createDirectories(temurinDir);
		Files.createDirectories(microsoftDir);

		DownloadResult download1 = new DownloadResult("md5-1", "sha1-1", "sha256-1", "sha512-1", 100_000_000L);
		JdkMetadata metadata1 = JdkMetadata.create()
				.vendor("temurin")
				.filename("temurin-jdk-17.tar.gz")
				.releaseType("ga")
				.version("17.0.5")
				.javaVersion("17")
				.jvmImpl("hotspot")
				.os("linux")
				.arch("x86_64")
				.fileType("tar.gz")
				.imageType("jdk")
				.url("https://example.com/temurin.tar.gz")
				.download(download1);

		DownloadResult download2 = new DownloadResult("md5-2", "sha1-2", "sha256-2", "sha512-2", 100_000_001L);
		JdkMetadata metadata2 = JdkMetadata.create()
				.vendor("microsoft")
				.filename("microsoft-jdk-17.tar.gz")
				.releaseType("ga")
				.version("17.0.5")
				.javaVersion("17")
				.jvmImpl("hotspot")
				.os("linux")
				.arch("x86_64")
				.fileType("tar.gz")
				.imageType("jdk")
				.url("https://example.com/microsoft.tar.gz")
				.download(download2);

		MetadataUtils.saveMetadataFile(temurinDir.resolve(metadata1.metadataFilename()), metadata1);
		MetadataUtils.saveMetadataFile(microsoftDir.resolve(metadata2.metadataFilename()), metadata2);

		// When
		MetadataUtils.generateComprehensiveIndices(metadataDir, false);

		// Then - verify both vendors appear in the same hierarchical path
		Path vendorPath = metadataDir.resolve("ga/linux/x86_64/jdk/hotspot");
		Path temurinJson = vendorPath.resolve("temurin.json");
		Path microsoftJson = vendorPath.resolve("microsoft.json");

		assertThat(temurinJson).exists();
		assertThat(microsoftJson).exists();

		String temurinContent = Files.readString(temurinJson);
		assertThat(temurinContent).contains("temurin-jdk-17.tar.gz");
		assertThat(temurinContent).doesNotContain("microsoft");

		String microsoftContent = Files.readString(microsoftJson);
		assertThat(microsoftContent).contains("microsoft-jdk-17.tar.gz");
		assertThat(microsoftContent).doesNotContain("temurin");
	}

	@Test
	void testGenerateComprehensiveIndicesWithDifferentReleaseTypes() throws Exception {
		// Given - metadata with different release types
		Path metadataDir = tempDir.resolve("metadata");
		Path vendorDir = metadataDir.resolve("vendor");
		Path temurinDir = vendorDir.resolve("temurin");
		Files.createDirectories(temurinDir);

		DownloadResult download1 = new DownloadResult("md5-1", "sha1-1", "sha256-1", "sha512-1", 100_000_000L);
		JdkMetadata metadata1 = JdkMetadata.create()
				.vendor("temurin")
				.filename("temurin-jdk-17-ga.tar.gz")
				.releaseType("ga")
				.version("17.0.5")
				.javaVersion("17")
				.jvmImpl("hotspot")
				.os("linux")
				.arch("x86_64")
				.fileType("tar.gz")
				.imageType("jdk")
				.url("https://example.com/ga.tar.gz")
				.download(download1);

		DownloadResult download2 = new DownloadResult("md5-2", "sha1-2", "sha256-2", "sha512-2", 100_000_001L);
		JdkMetadata metadata2 = JdkMetadata.create()
				.vendor("temurin")
				.filename("temurin-jdk-18-ea.tar.gz")
				.releaseType("ea")
				.version("18-ea")
				.javaVersion("18")
				.jvmImpl("hotspot")
				.os("linux")
				.arch("x86_64")
				.fileType("tar.gz")
				.imageType("jdk")
				.url("https://example.com/ea.tar.gz")
				.download(download2);

		MetadataUtils.saveMetadataFile(temurinDir.resolve(metadata1.metadataFilename()), metadata1);
		MetadataUtils.saveMetadataFile(temurinDir.resolve(metadata2.metadataFilename()), metadata2);

		// When
		MetadataUtils.generateComprehensiveIndices(metadataDir, false);

		// Then - verify separate release_type directories
		Path gaDir = metadataDir.resolve("ga");
		Path eaDir = metadataDir.resolve("ea");

		assertThat(gaDir).exists().isDirectory();
		assertThat(eaDir).exists().isDirectory();

		assertThat(metadataDir.resolve("ga.json")).exists();
		assertThat(metadataDir.resolve("ea.json")).exists();

		// Verify content is separated correctly
		String gaContent = Files.readString(metadataDir.resolve("ga.json"));
		assertThat(gaContent).contains("temurin-jdk-17-ga.tar.gz");
		assertThat(gaContent).doesNotContain("temurin-jdk-18-ea.tar.gz");

		String eaContent = Files.readString(metadataDir.resolve("ea.json"));
		assertThat(eaContent).contains("temurin-jdk-18-ea.tar.gz");
		assertThat(eaContent).doesNotContain("temurin-jdk-17-ga.tar.gz");
	}

	@Test
	void testGenerateComprehensiveIndicesEmptyVendorDir() throws Exception {
		// Given - empty vendor directory
		Path metadataDir = tempDir.resolve("metadata");
		Path vendorDir = metadataDir.resolve("vendor");
		Files.createDirectories(vendorDir);

		// When/Then - should not throw exception
		assertThatCode(() -> MetadataUtils.generateComprehensiveIndices(metadataDir, false))
				.doesNotThrowAnyException();

		// all.json should not be created
		assertThat(metadataDir.resolve("all.json")).doesNotExist();
	}

	@Test
	void testGenerateComprehensiveIndicesNonExistentVendorDir() throws Exception {
		// Given - non-existent metadata directory
		Path metadataDir = tempDir.resolve("does-not-exist");

		// When/Then - should not throw exception
		assertThatCode(() -> MetadataUtils.generateComprehensiveIndices(metadataDir, false))
				.doesNotThrowAnyException();
	}

	@Test
	void testGenerateComprehensiveIndicesFiltersIncompleteMetadata() throws Exception {
		// Given - metadata with and without checksums
		Path metadataDir = tempDir.resolve("metadata");
		Path vendorDir = metadataDir.resolve("vendor");
		Path temurinDir = vendorDir.resolve("temurin");
		Files.createDirectories(temurinDir);

		// Complete metadata
		DownloadResult download1 = new DownloadResult("md5-1", "sha1-1", "sha256-1", "sha512-1", 100_000_000L);
		JdkMetadata metadata1 = JdkMetadata.create()
				.vendor("temurin")
				.filename("complete-jdk.tar.gz")
				.releaseType("ga")
				.version("17.0.5")
				.javaVersion("17")
				.jvmImpl("hotspot")
				.os("linux")
				.arch("x86_64")
				.fileType("tar.gz")
				.imageType("jdk")
				.url("https://example.com/complete.tar.gz")
				.download(download1);

		// Incomplete metadata (missing checksums)
		DownloadResult download2 = new DownloadResult(null, null, null, null, 100_000_001L);
		JdkMetadata metadata2 = JdkMetadata.create()
				.vendor("temurin")
				.filename("incomplete-jdk.tar.gz")
				.releaseType("ga")
				.version("17.0.5")
				.javaVersion("17")
				.jvmImpl("hotspot")
				.os("linux")
				.arch("x86_64")
				.fileType("tar.gz")
				.imageType("jdk")
				.url("https://example.com/incomplete.tar.gz")
				.download(download2);

		MetadataUtils.saveMetadataFile(temurinDir.resolve(metadata1.metadataFilename()), metadata1);
		MetadataUtils.saveMetadataFile(temurinDir.resolve(metadata2.metadataFilename()), metadata2);

		// When - generate with allowIncomplete=false
		MetadataUtils.generateComprehensiveIndices(metadataDir, false);

		// Then - incomplete metadata should be filtered out
		Path allJson = metadataDir.resolve("all.json");
		assertThat(allJson).exists();
		String allContent = Files.readString(allJson);
		assertThat(allContent).contains("complete-jdk.tar.gz");
		assertThat(allContent).doesNotContain("incomplete-jdk.tar.gz");

		// When - generate with allowIncomplete=true
		MetadataUtils.generateComprehensiveIndices(metadataDir, true);

		// Then - both should be included
		allContent = Files.readString(allJson);
		assertThat(allContent).contains("complete-jdk.tar.gz");
		assertThat(allContent).contains("incomplete-jdk.tar.gz");
	}

	@Test
	void testGenerateComprehensiveIndicesNormalizesEmptyValues() throws Exception {
		// Given - metadata with null/empty values
		Path metadataDir = tempDir.resolve("metadata");
		Path vendorDir = metadataDir.resolve("vendor");
		Path testDir = vendorDir.resolve("test");
		Files.createDirectories(testDir);

		DownloadResult download = new DownloadResult("md5-1", "sha1-1", "sha256-1", "sha512-1", 100_000_000L);
		JdkMetadata metadata = JdkMetadata.create()
				.vendor(null) // null vendor
				.filename("test-jdk.tar.gz")
				.releaseType("") // empty release type
				.version("17.0.5")
				.javaVersion("17")
				.jvmImpl(null) // null jvm_impl
				.os("linux")
				.arch("x86_64")
				.fileType("tar.gz")
				.imageType("") // empty image type
				.url("https://example.com/test.tar.gz")
				.download(download);

		MetadataUtils.saveMetadataFile(testDir.resolve(metadata.metadataFilename()), metadata);

		// When
		MetadataUtils.generateComprehensiveIndices(metadataDir, false);

		// Then - verify normalized directories are created
		assertThat(metadataDir.resolve("all.json")).exists();
		assertThat(metadataDir.resolve("unknown-release-type-")).exists().isDirectory();
		Path unknownReleaseTypeDir = metadataDir.resolve("unknown-release-type-");
		assertThat(unknownReleaseTypeDir.resolve("linux")).exists().isDirectory();
		Path linuxDir = unknownReleaseTypeDir.resolve("linux");
		Path archDir = linuxDir.resolve("x86_64");
		assertThat(archDir).exists().isDirectory();
		assertThat(archDir.resolve("unknown-image-type")).exists().isDirectory();
		Path imageTypeDir = archDir.resolve("unknown-image-type");
		assertThat(imageTypeDir.resolve("unknown-jvm-impl")).exists().isDirectory();
		Path jvmDir = imageTypeDir.resolve("unknown-jvm-impl");
		assertThat(jvmDir.resolve("unknown-vendor.json")).exists();
	}

	@Test
	void testGenerateComprehensiveIndicesWithDifferentArchitectures() throws Exception {
		// Given - metadata with different architectures
		Path metadataDir = tempDir.resolve("metadata");
		Path vendorDir = metadataDir.resolve("vendor");
		Path temurinDir = vendorDir.resolve("temurin");
		Files.createDirectories(temurinDir);

		DownloadResult download1 = new DownloadResult("md5-1", "sha1-1", "sha256-1", "sha512-1", 100_000_000L);
		JdkMetadata metadata1 = JdkMetadata.create()
				.vendor("temurin")
				.filename("temurin-x64.tar.gz")
				.releaseType("ga")
				.version("17.0.5")
				.javaVersion("17")
				.jvmImpl("hotspot")
				.os("linux")
				.arch("x86_64")
				.fileType("tar.gz")
				.imageType("jdk")
				.url("https://example.com/x64.tar.gz")
				.download(download1);

		DownloadResult download2 = new DownloadResult("md5-2", "sha1-2", "sha256-2", "sha512-2", 100_000_001L);
		JdkMetadata metadata2 = JdkMetadata.create()
				.vendor("temurin")
				.filename("temurin-aarch64.tar.gz")
				.releaseType("ga")
				.version("17.0.5")
				.javaVersion("17")
				.jvmImpl("hotspot")
				.os("linux")
				.arch("aarch64")
				.fileType("tar.gz")
				.imageType("jdk")
				.url("https://example.com/aarch64.tar.gz")
				.download(download2);

		MetadataUtils.saveMetadataFile(temurinDir.resolve(metadata1.metadataFilename()), metadata1);
		MetadataUtils.saveMetadataFile(temurinDir.resolve(metadata2.metadataFilename()), metadata2);

		// When
		MetadataUtils.generateComprehensiveIndices(metadataDir, false);

		// Then - verify separate architecture directories
		Path gaLinuxDir = metadataDir.resolve("ga/linux");
		assertThat(gaLinuxDir.resolve("x86_64")).exists().isDirectory();
		assertThat(gaLinuxDir.resolve("aarch64")).exists().isDirectory();

		String x64Content = Files.readString(gaLinuxDir.resolve("x86_64/jdk/hotspot/temurin.json"));
		assertThat(x64Content).contains("temurin-x64.tar.gz");
		assertThat(x64Content).doesNotContain("aarch64");

		String aarch64Content = Files.readString(gaLinuxDir.resolve("aarch64/jdk/hotspot/temurin.json"));
		assertThat(aarch64Content).contains("temurin-aarch64.tar.gz");
		assertThat(aarch64Content).doesNotContain("x86_64");
	}

	@Test
	void testGenerateComprehensiveIndicesWithDifferentImageTypes() throws Exception {
		// Given - metadata with different image types (jdk, jre)
		Path metadataDir = tempDir.resolve("metadata");
		Path vendorDir = metadataDir.resolve("vendor");
		Path temurinDir = vendorDir.resolve("temurin");
		Files.createDirectories(temurinDir);

		DownloadResult download1 = new DownloadResult("md5-1", "sha1-1", "sha256-1", "sha512-1", 100_000_000L);
		JdkMetadata metadata1 = JdkMetadata.create()
				.vendor("temurin")
				.filename("temurin-jdk.tar.gz")
				.releaseType("ga")
				.version("17.0.5")
				.javaVersion("17")
				.jvmImpl("hotspot")
				.os("linux")
				.arch("x86_64")
				.fileType("tar.gz")
				.imageType("jdk")
				.url("https://example.com/jdk.tar.gz")
				.download(download1);

		DownloadResult download2 = new DownloadResult("md5-2", "sha1-2", "sha256-2", "sha512-2", 100_000_001L);
		JdkMetadata metadata2 = JdkMetadata.create()
				.vendor("temurin")
				.filename("temurin-jre.tar.gz")
				.releaseType("ga")
				.version("17.0.5")
				.javaVersion("17")
				.jvmImpl("hotspot")
				.os("linux")
				.arch("x86_64")
				.fileType("tar.gz")
				.imageType("jre")
				.url("https://example.com/jre.tar.gz")
				.download(download2);

		MetadataUtils.saveMetadataFile(temurinDir.resolve(metadata1.metadataFilename()), metadata1);
		MetadataUtils.saveMetadataFile(temurinDir.resolve(metadata2.metadataFilename()), metadata2);

		// When
		MetadataUtils.generateComprehensiveIndices(metadataDir, false);

		// Then - verify separate image_type directories
		Path gaLinuxArchDir = metadataDir.resolve("ga/linux/x86_64");
		assertThat(gaLinuxArchDir.resolve("jdk")).exists().isDirectory();
		assertThat(gaLinuxArchDir.resolve("jre")).exists().isDirectory();

		String jdkContent = Files.readString(gaLinuxArchDir.resolve("jdk/hotspot/temurin.json"));
		assertThat(jdkContent).contains("temurin-jdk.tar.gz");
		assertThat(jdkContent).doesNotContain("jre");

		String jreContent = Files.readString(gaLinuxArchDir.resolve("jre/hotspot/temurin.json"));
		assertThat(jreContent).contains("temurin-jre.tar.gz");
		assertThat(jreContent).doesNotContain("\"jdk\"");
	}

	@Test
	void testGenerateComprehensiveIndicesWithDifferentJvmImplementations() throws Exception {
		// Given - metadata with different JVM implementations
		Path metadataDir = tempDir.resolve("metadata");
		Path vendorDir = metadataDir.resolve("vendor");
		Path temurinDir = vendorDir.resolve("temurin");
		Files.createDirectories(temurinDir);

		DownloadResult download1 = new DownloadResult("md5-1", "sha1-1", "sha256-1", "sha512-1", 100_000_000L);
		JdkMetadata metadata1 = JdkMetadata.create()
				.vendor("temurin")
				.filename("temurin-hotspot.tar.gz")
				.releaseType("ga")
				.version("17.0.5")
				.javaVersion("17")
				.jvmImpl("hotspot")
				.os("linux")
				.arch("x86_64")
				.fileType("tar.gz")
				.imageType("jdk")
				.url("https://example.com/hotspot.tar.gz")
				.download(download1);

		DownloadResult download2 = new DownloadResult("md5-2", "sha1-2", "sha256-2", "sha512-2", 100_000_001L);
		JdkMetadata metadata2 = JdkMetadata.create()
				.vendor("temurin")
				.filename("temurin-openj9.tar.gz")
				.releaseType("ga")
				.version("17.0.5")
				.javaVersion("17")
				.jvmImpl("openj9")
				.os("linux")
				.arch("x86_64")
				.fileType("tar.gz")
				.imageType("jdk")
				.url("https://example.com/openj9.tar.gz")
				.download(download2);

		MetadataUtils.saveMetadataFile(temurinDir.resolve(metadata1.metadataFilename()), metadata1);
		MetadataUtils.saveMetadataFile(temurinDir.resolve(metadata2.metadataFilename()), metadata2);

		// When
		MetadataUtils.generateComprehensiveIndices(metadataDir, false);

		// Then - verify separate jvm_impl directories
		Path gaLinuxArchImageDir = metadataDir.resolve("ga/linux/x86_64/jdk");
		assertThat(gaLinuxArchImageDir.resolve("hotspot")).exists().isDirectory();
		assertThat(gaLinuxArchImageDir.resolve("openj9")).exists().isDirectory();

		String hotspotContent = Files.readString(gaLinuxArchImageDir.resolve("hotspot/temurin.json"));
		assertThat(hotspotContent).contains("temurin-hotspot.tar.gz");
		assertThat(hotspotContent).doesNotContain("openj9");

		String openj9Content = Files.readString(gaLinuxArchImageDir.resolve("openj9/temurin.json"));
		assertThat(openj9Content).contains("temurin-openj9.tar.gz");
		assertThat(openj9Content).doesNotContain("hotspot");
	}
}
