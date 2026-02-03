package dev.jbang.jdkdb.util;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jbang.jdkdb.model.JdkMetadata;
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
		MetadataUtils.saveMetadata(tempDir.resolve("metadata"), List.of(metadata));

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

		JdkMetadata metadata1 = new JdkMetadata();
		metadata1.setFilename("test-jdk-1");
		metadata1.setVendor("test-vendor");
		metadata1.setVersion("17.0.1");
		metadata1.setJavaVersion("17");
		metadata1.setOs("linux");
		metadata1.setArchitecture("x86_64");
		metadata1.setFileType("tar.gz");
		metadata1.setImageType("jdk");
		metadata1.setReleaseType("ga");
		metadata1.setUrl("https://example.com/jdk-1.tar.gz");
		metadata1.setSize(100_000_000L);

		JdkMetadata metadata2 = new JdkMetadata();
		metadata2.setFilename("test-jdk-2");
		metadata2.setVendor("test-vendor");
		metadata2.setVersion("17.0.2");
		metadata2.setJavaVersion("17");
		metadata2.setOs("windows");
		metadata2.setArchitecture("x86_64");
		metadata2.setFileType("zip");
		metadata2.setImageType("jdk");
		metadata2.setReleaseType("ga");
		metadata2.setUrl("https://example.com/jdk-2.zip");
		metadata2.setSize(100_000_001L);

		// Save individual metadata files
		MetadataUtils.saveMetadataFile(vendorDir, metadata1);
		MetadataUtils.saveMetadataFile(vendorDir, metadata2);

		// Verify individual files exist
		assertThat(vendorDir.resolve("test-jdk-1.json")).exists();
		assertThat(vendorDir.resolve("test-jdk-2.json")).exists();

		// When - generate all.json from directory
		MetadataUtils.generateAllJsonFromDirectory(vendorDir);

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

		JdkMetadata metadata = new JdkMetadata();
		metadata.setFilename("test-jdk");
		metadata.setVendor("test-vendor");
		metadata.setVersion("17.0.1");
		metadata.setJavaVersion("17");
		metadata.setOs("linux");
		metadata.setArchitecture("x86_64");
		metadata.setFileType("tar.gz");
		metadata.setImageType("jdk");
		metadata.setReleaseType("ga");
		metadata.setUrl("https://example.com/jdk.tar.gz");
		metadata.setSize(100_000_000L);

		MetadataUtils.saveMetadataFile(vendorDir, metadata);

		// Create an old all.json with different content
		Files.writeString(vendorDir.resolve("all.json"), "[{\"old\": \"data\"}]");

		// When - regenerate all.json
		MetadataUtils.generateAllJsonFromDirectory(vendorDir);

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
		assertThatCode(() -> MetadataUtils.generateAllJsonFromDirectory(nonExistent))
				.doesNotThrowAnyException();
	}

	@Test
	void testSaveMetadataSortsByVersionThenFilename() throws Exception {
		// Given - metadata with various versions and filenames
		JdkMetadata metadata1 = new JdkMetadata();
		metadata1.setFilename("zulu-jdk-11.0.10");
		metadata1.setVendor("zulu");
		metadata1.setVersion("11.0.10");
		metadata1.setJavaVersion("11");
		metadata1.setOs("linux");
		metadata1.setArchitecture("x86_64");
		metadata1.setFileType("tar.gz");
		metadata1.setImageType("jdk");
		metadata1.setReleaseType("ga");
		metadata1.setUrl("https://example.com/zulu-11.0.10.tar.gz");
		metadata1.setSize(100_000_000L);

		JdkMetadata metadata2 = new JdkMetadata();
		metadata2.setFilename("abc-jdk-11.0.10");
		metadata2.setVendor("abc");
		metadata2.setVersion("11.0.10");
		metadata2.setJavaVersion("11");
		metadata2.setOs("linux");
		metadata2.setArchitecture("x86_64");
		metadata2.setFileType("tar.gz");
		metadata2.setImageType("jdk");
		metadata2.setReleaseType("ga");
		metadata2.setUrl("https://example.com/abc-11.0.10.tar.gz");
		metadata2.setSize(100_000_000L);

		JdkMetadata metadata3 = new JdkMetadata();
		metadata3.setFilename("temurin-jdk-17.0.5");
		metadata3.setVendor("temurin");
		metadata3.setVersion("17.0.5");
		metadata3.setJavaVersion("17");
		metadata3.setOs("linux");
		metadata3.setArchitecture("x86_64");
		metadata3.setFileType("tar.gz");
		metadata3.setImageType("jdk");
		metadata3.setReleaseType("ga");
		metadata3.setUrl("https://example.com/temurin-17.0.5.tar.gz");
		metadata3.setSize(100_000_000L);

		JdkMetadata metadata4 = new JdkMetadata();
		metadata4.setFilename("oracle-jdk-8.0.202");
		metadata4.setVendor("oracle");
		metadata4.setVersion("8.0.202");
		metadata4.setJavaVersion("8");
		metadata4.setOs("linux");
		metadata4.setArchitecture("x86_64");
		metadata4.setFileType("tar.gz");
		metadata4.setImageType("jdk");
		metadata4.setReleaseType("ga");
		metadata4.setUrl("https://example.com/oracle-8.0.202.tar.gz");
		metadata4.setSize(100_000_000L);

		JdkMetadata metadata5 = new JdkMetadata();
		metadata5.setFilename("oracle-jdk-11.0.2");
		metadata5.setVendor("oracle");
		metadata5.setVersion("11.0.2");
		metadata5.setJavaVersion("11");
		metadata5.setOs("linux");
		metadata5.setArchitecture("x86_64");
		metadata5.setFileType("tar.gz");
		metadata5.setImageType("jdk");
		metadata5.setReleaseType("ga");
		metadata5.setUrl("https://example.com/oracle-11.0.2.tar.gz");
		metadata5.setSize(100_000_000L);

		// When - save in random order
		Path metadataDir = tempDir.resolve("metadata");
		Files.createDirectories(metadataDir);
		MetadataUtils.saveMetadata(metadataDir, List.of(metadata3, metadata1, metadata5, metadata4, metadata2));

		// Then - all.json should be sorted by version first, then filename
		Path allJson = metadataDir.resolve("all.json");
		assertThat(allJson).exists();

		String content = Files.readString(allJson);
		ObjectMapper objectMapper = new ObjectMapper();
		JdkMetadata[] result = objectMapper.readValue(content, JdkMetadata[].class);

		// Verify sorted order: 8.0.202, 11.0.2, 11.0.10 (abc before zulu), 17.0.5
		assertThat(result).hasSize(5);
		assertThat(result[0].getVersion()).isEqualTo("8.0.202");
		assertThat(result[0].getFilename()).isEqualTo("oracle-jdk-8.0.202");

		assertThat(result[1].getVersion()).isEqualTo("11.0.2");
		assertThat(result[1].getFilename()).isEqualTo("oracle-jdk-11.0.2");

		assertThat(result[2].getVersion()).isEqualTo("11.0.10");
		assertThat(result[2].getFilename()).isEqualTo("abc-jdk-11.0.10"); // abc comes before zulu

		assertThat(result[3].getVersion()).isEqualTo("11.0.10");
		assertThat(result[3].getFilename()).isEqualTo("zulu-jdk-11.0.10");

		assertThat(result[4].getVersion()).isEqualTo("17.0.5");
		assertThat(result[4].getFilename()).isEqualTo("temurin-jdk-17.0.5");
	}
}
